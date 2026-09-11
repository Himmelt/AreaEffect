package org.soraworld.areaeffect.common.server;

import net.minecraft.entity.player.EntityPlayerMP;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.soraworld.areaeffect.common.area.AreaTable;
import org.soraworld.areaeffect.common.effect.AreaEffect;
import org.soraworld.areaeffect.common.net.AreaSync;
import org.soraworld.areaeffect.common.network.Area;
import org.soraworld.areaeffect.common.network.MessageConflictAreas;
import org.soraworld.areaeffect.common.network.MessageDeleteRequest;
import org.soraworld.areaeffect.common.network.MessageListReply;
import org.soraworld.areaeffect.common.network.MessageSelectShape;
import org.soraworld.areaeffect.common.network.MessageSetProps;
import org.soraworld.areaeffect.common.network.MessageTpRequest;
import org.soraworld.areaeffect.common.network.PacketChannel;
import org.soraworld.areaeffect.common.selection.SelectionManager;
import org.soraworld.areaeffect.common.shape.AreaShape;
import org.soraworld.areaeffect.common.shape.Selection;
import org.soraworld.areaeffect.common.shape.ShapeTypes;
import org.soraworld.areaeffect.common.storage.AreaStore;
import org.soraworld.areaeffect.common.util.Players;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 服务端入站请求的业务编排：<b>鉴权 → 改数据 → 标记落盘 → 广播</b>。
 *
 * <p>从代理中独立出来的原因：这部分是"业务规则"，与"代理要装配哪些组件"是两件事。
 * 本类只依赖三个组件（数据 / 选区 / 存档）与两个静态工具（权限消息 / 广播），不反向依赖代理。
 *
 * <p>线程模型：所有方法都在服务端主线程执行——入站数据包由 {@code PacketChannel} 投递、
 * 在 {@code ServerTickEvent} 排空（见 {@code PacketChannel#route}），因此这里不需要额外同步。
 *
 * <p>安全前提：客户端"已经鉴权"的说法一律不可信，每个入口都独立再判一次 OP 权限。
 * 客户端提交的数据（形状类型、效果参数、备注）也都在这里做边界处理。
 *
 * <p>权限反馈规则（各入口保持一致，避免"有的刷屏、有的毫无反应"）：
 * <ul>
 *   <li><b>显式请求</b>——面板开启、指令型操作：回一条 {@code chat.perm.denied}，让玩家知道是权限问题；</li>
 *   <li><b>工具驱动的动作</b>——选点、撤回顶点、轮切形状：一律静默忽略，因为非 OP 手里的木斧是
 *       普通工具，这类点击高度频繁且多半是误触，逐次回拒绝只会刷屏。</li>
 * </ul>
 */
public class AreaRequests {

    private static final Logger LOGGER = LogManager.getLogger("AreaEffect");

    private final AreaTable table;
    private final SelectionManager selections;
    private final AreaStore store;

    public AreaRequests(AreaTable table, SelectionManager selections, AreaStore store) {
        this.table = table;
        this.selections = selections;
        this.store = store;
    }

    /**
     * 列表请求：校验 OP 权限后回复当前维度的区域列表（按 id 升序）。
     * 无权限时回一条拒绝消息——静默返回会让玩家以为面板/按键坏了。
     */
    public void onListRequest(EntityPlayerMP player) {
        if (!Players.canManage(player)) {
            Players.chat(player, "chat.perm.denied");
            return;
        }
        // 只回一个「授权 / 刷新」信号，不带数据：面板读的是客户端本地镜像（见 MessageListReply 说明）
        PacketChannel.sendTo(new MessageListReply(), player);
    }

    /**
     * 效果写回：以客户端回写的整组效果替换，逐条参数边界处理并按类型去重
     * （与 {@code Area#getEffects} "每种效果类型至多一个实例" 的约定一致，重复取首个）。
     */
    public void onSetProps(EntityPlayerMP player, MessageSetProps packet) {
        if (!Players.canManage(player)) {
            Players.chat(player, "chat.perm.denied");
            return;
        }
        // 该维度尚无任何区域：无从写回，静默丢弃（区域存在但 id 不匹配才提示 notfound）
        if (!table.hasDim(packet.dim)) {
            return;
        }
        Area area = table.get(packet.dim, packet.id);
        if (area == null) {
            Players.chat(player, "chat.area.notfound");
            return;
        }
        List<AreaEffect> incoming = packet.effects;
        List<AreaEffect> deduped = new ArrayList<>(incoming.size());
        Set<String> seenTypes = new HashSet<>();
        for (AreaEffect effect : incoming) {
            effect.sanitize();
            if (seenTypes.add(effect.typeId())) {
                deduped.add(effect);
            }
        }
        // 效果集已允许为空（区域可以不带任何效果，见 Area#getEffects 的约定）。
        // 这里复制一份"待生效"的区域评估它与其它重叠区域是否产生同种效果权重平局：
        // 区域允许重叠，但同种效果的权重在重叠集合内必须唯一（平局即禁重叠），
        // 否则渲染端取权重最高者时结果不确定（见 Area#weightConflict）。
        Area pending = new Area(area.shape());
        pending.id = area.id;
        pending.setEffects(deduped);
        for (Area other : table.inDim(packet.dim).values()) {
            if (other.id != area.id && pending.weightConflict(other)) {
                Players.chat(player, "chat.setprops.conflict");
                return;
            }
        }
        area.setEffects(deduped);
        area.setRemark(packet.remark);
        store.markDirty();
        AreaSync.toAll(packet.dim, area.id, area);
    }

    /**
     * 按 id 删除：成功则标记落盘并广播（含单机，客户端依赖广播统一刷新本地数据，
     * 本地不提前移除以免竞态）。
     */
    public void onDeleteRequest(EntityPlayerMP player, MessageDeleteRequest packet) {
        if (!Players.canManage(player)) {
            Players.chat(player, "chat.perm.denied");
            return;
        }
        if (table.remove(packet.dim, packet.id) == null) {
            Players.chat(player, "chat.area.notfound");
            return;
        }
        store.markDirty();
        AreaSync.deleteToAll(packet.dim, packet.id);
    }

    /** 按 id 传送：校验 OP 权限后跳转到区域中心。 */
    public void onTpRequest(EntityPlayerMP player, MessageTpRequest packet) {
        if (!Players.canManage(player)) {
            Players.chat(player, "chat.perm.denied");
            return;
        }
        teleportTo(player, packet.id);
    }

    /**
     * 设置选区形状：除权限外还要按 {@link ShapeTypes#ALL} 做白名单校验。
     * 形状类型来自客户端，不校验就会让任意字符串进入选区状态并随 {@code MessageSelection}
     * 回显到所有客户端。
     */
    public void onSelectShape(EntityPlayerMP player, MessageSelectShape packet) {
        if (!Players.canManage(player)) {
            // 工具驱动的动作统一静默，见类注释的权限反馈规则（与 onClickAir 一致）
            return;
        }
        if (ShapeTypes.isValid(packet.type)) {
            selections.reset(player, packet.type);
        } else {
            LOGGER.warn("Rejected unknown select shape '{}' from {}", packet.type, player.getCommandSenderName());
        }
    }

    /**
     * 右键空气：与右键方块共用 {@link SelectionManager#clickRight} 入口
     * （{@code Selection#onClickRight}），作为多边形选区的撤回触发；二点形状无坐标故忽略。
     */
    public void onClickAir(EntityPlayerMP player) {
        if (!Players.canManage(player)) {
            return;
        }
        selections.clickRight(player, null);
    }

    /**
     * 按当前选区创建区域（指令入口）：只创建区域本体，<b>不携带任何效果</b>。
     * 空区域的 weightConflict 恒为 false（无同种效果可比），故重叠本身总是放行；
     * 效果在面板里添加保存时由 {@link #onSetProps} 再做权重平局校验。
     * 冲突时不创建，并把冲突区域 id 发回客户端以便自动显示它们的线框。
     */
    public void create(EntityPlayerMP player) {
        Selection sel = selections.existing(player);
        if (sel == null || !sel.isBuildable()) {
            Players.chat(player, "chat.create.noselect");
            return;
        }
        AreaShape intent = sel.build();
        // 平局规则预检：构造候选空区域。空区域无同种效果可比，正常情况下不会冲突，
        // 保留这一层判定仅作防御（与 table.add 内部的判定一致、双保险）。
        Area candidate = new Area(intent);
        List<Integer> conflicts = table.conflictsIn(player.dimension, candidate);
        if (!conflicts.isEmpty()) {
            Players.chat(player, "chat.create.conflict");
            // 把冲突区域的框线都显示出来
            PacketChannel.sendTo(new MessageConflictAreas(player.dimension, conflicts), player);
            return;
        }
        Area area = table.add(player.dimension, intent);
        if (area == null) {
            Players.chat(player, "chat.create.conflict");
            return;
        }
        Players.chat(player, "chat.create.done");
        if (Players.isDedicated()) {
            AreaSync.toAll(player.dimension, area.id, area);
        }
        // 创建成功后重置选区（保留形状类型，锚点清空），可直接开始下一个选区
        sel.anchors.clear();
        selections.sync(player);
        store.markDirty();
    }

    /** 传送到指定 id 区域的中心（跨维度会先切维度）；不存在则提示。 */
    public void teleportTo(EntityPlayerMP player, int id) {
        if (player == null) {
            return;
        }
        AreaTable.Located located = table.locate(id);
        if (located == null) {
            Players.chat(player, "chat.area.notfound");
            return;
        }
        if (player.dimension != located.dim) {
            player.travelToDimension(located.dim);
        }
        located.area.center(player);
    }
}
