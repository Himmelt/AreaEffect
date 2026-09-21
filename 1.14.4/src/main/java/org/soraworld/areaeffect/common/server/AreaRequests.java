package org.soraworld.areaeffect.common.server;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.play.server.SPlayEntityEffectPacket;
import net.minecraft.network.play.server.SPlaySoundEventPacket;
import net.minecraft.network.play.server.SPlayerAbilitiesPacket;
import net.minecraft.network.play.server.SRespawnPacket;
import net.minecraft.network.play.server.SServerDifficultyPacket;
import net.minecraft.potion.EffectInstance;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.fml.hooks.BasicEventHooks;
import net.minecraftforge.fml.network.NetworkHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.soraworld.areaeffect.common.area.AreaTable;
import org.soraworld.areaeffect.common.effect.AreaEffect;
import org.soraworld.areaeffect.common.net.AreaSync;
import org.soraworld.areaeffect.common.network.Area;
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
import org.soraworld.areaeffect.common.util.Vec3d;

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
 * <p>线程模型：所有方法都在服务端主线程执行——入站数据包由 {@code PacketChannel} 经 SimpleChannel
 * 的 {@code enqueueWork} 投递（见 {@code PacketChannel#handle}），因此这里不需要额外同步。
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
 *
 * <p>1.13 的维度由 {@code int} 换成 {@link DimensionType}（见 {@code Entity#dimension}），
 * 但区域数据仍按维度 <b>id</b> 分表（存档与网络协议里也是 id），因此本类在两个方向上都做一次
 * 收敛：入口用 {@code player.dimension.getId()} 入表，出口用 {@link #dimType(int)} 还原成类型。
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
    public void onListRequest(ServerPlayerEntity player) {
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
    public void onSetProps(ServerPlayerEntity player, MessageSetProps packet) {
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
        // 效果集已允许为空（区域可以不带任何效果）。区域允许任意重叠，不做同种效果权重平局校验，
        // 重叠集内同种效果谁显示由渲染端"权重优先、同权取较大 id"决定（见 ClientProxy#updateClientLight）。
        area.setEffects(deduped);
        area.setRemark(packet.remark);
        store.markDirty();
        broadcast(packet.dim, area.id, area, false);
    }

    /**
     * 按 id 删除：成功则标记落盘并广播（含单机，客户端依赖广播统一刷新本地数据，
     * 本地不提前移除以免竞态）。
     */
    public void onDeleteRequest(ServerPlayerEntity player, MessageDeleteRequest packet) {
        if (!Players.canManage(player)) {
            Players.chat(player, "chat.perm.denied");
            return;
        }
        if (table.remove(packet.dim, packet.id) == null) {
            Players.chat(player, "chat.area.notfound");
            return;
        }
        store.markDirty();
        broadcast(packet.dim, packet.id, null, true);
    }

    /** 按 id 传送：校验 OP 权限后跳转到区域中心。 */
    public void onTpRequest(ServerPlayerEntity player, MessageTpRequest packet) {
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
    public void onSelectShape(ServerPlayerEntity player, MessageSelectShape packet) {
        if (!Players.canManage(player)) {
            // 非 OP：客户端此前已本地乐观切换形状并弹出 overlay，这里回发权威选区回声，
            // 让客户端丢弃"成功假象"（无权限时不发 chat，与 onClickAir 的静默规则一致）。若该玩家
            // 尚无选区，sync 会发空选区，同样把乐观状态清掉。
            selections.sync(player);
            return;
        }
        if (ShapeTypes.isValid(packet.type)) {
            selections.reset(player, packet.type);
        } else {
            LOGGER.warn("Rejected unknown select shape '{}' from {}", packet.type, player.getName().getString());
        }
    }

    /**
     * 右键空气：与右键方块共用 {@link SelectionManager#clickRight} 入口
     * （{@code Selection#onClickRight}），作为多边形选区的撤回触发；二点形状无坐标故忽略。
     */
    public void onClickAir(ServerPlayerEntity player) {
        if (!Players.canManage(player)) {
            return;
        }
        selections.clickRight(player, null);
    }

    /**
     * 按当前选区创建区域（指令入口）：只创建区域本体，<b>不携带任何效果</b>。
     * 区域<b>允许任意重叠</b>，不做任何重叠/平局校验；效果在面板里配置，保存即生效。
     */
    public void create(ServerPlayerEntity player) {
        Selection sel = selections.existing(player);
        if (sel == null || !sel.isBuildable()) {
            Players.chat(player, "chat.create.noselect");
            return;
        }
        AreaShape intent = sel.build();
        Area area = table.add(player.dimension.getId(), intent);
        Players.chat(player, "chat.create.done");
        // 单机（集成服）同样走完整网络回环，与专用服一致，杜绝"共享实例即时可见"的捷径
        AreaSync.toAll(player.dimension, area.id, area);
        // 创建成功后重置选区（保留形状类型，锚点清空），可直接开始下一个选区
        sel.anchors.clear();
        selections.sync(player);
        store.markDirty();
    }

    /** 传送到指定 id 区域的中心（跨维度会先切维度）；不存在则提示。 */
    public void teleportTo(ServerPlayerEntity player, int id) {
        if (player == null) {
            return;
        }
        AreaTable.Located located = table.locate(id);
        if (located == null) {
            Players.chat(player, "chat.area.notfound");
            return;
        }
        if (player.dimension.getId() != located.dim) {
            DimensionType target = dimType(located.dim);
            if (target == null) {
                // 区域所在维度在当前服务端已不存在（存档里的自定义维度但模组已卸载）
                Players.chat(player, "chat.area.notfound");
                return;
            }
            // 落点先算出来：跨维度转移要一次性把目标坐标交给转移流程，落地后不再需要二次定位
            Vec3d center = located.area.shape().center();
            if (!changeDimension(player, target, center.x, center.y, center.z)) {
                // 被其它模组经 ForgeHooks#onTravelToDimension 否决（维度锁定、传送禁用等）
                Players.chat(player, "chat.tp.blocked");
                return;
            }
        }
        located.area.center(player);
    }

    /**
     * 跨维度传送：只要转移簿记，不要任何传送门逻辑。
     *
     * <p>1.13 用的 Forge {@code ITeleporter} 在 1.14 已被移除，而 vanilla 的
     * {@code Entity#changeDimension(DimensionType)} 会在部分维度（进下界时尤其明显）顺带找/造传送门：
     * 玩家落点会被门顶掉，还可能凭空多出一座门 —— 与本模组"落点由区域中心精确指定"的语义冲突。
     * 因此这里手工完成转移，步骤与 vanilla 的玩家换维流程同序，只是不含任何传送门环节。
     *
     * <p>两处用了 SRG 名（本版映射未覆盖）：{@code ServerWorld#func_217447_b}（玩家进新世界的登记）
     * 与 {@code PlayerInteractionManager#func_73080_a}（把交互管理器切到新维度）；
     * {@code MinecraftServer#getWorld} 同样未映射，故改用 Forge 的维度→世界映射表。
     */
    private static boolean changeDimension(ServerPlayerEntity player, DimensionType to,
                                           double toX, double toY, double toZ) {
        if (player.dimension == to) {
            player.setPositionAndUpdate(toX, toY, toZ);
            return true;
        }
        if (!ForgeHooks.onTravelToDimension(player, to)) {
            return false;
        }
        MinecraftServer server = player.server;
        if (server == null) {
            return false;
        }
        DimensionType from = player.dimension;
        ServerWorld fromWorld = (ServerWorld) player.world;
        ServerWorld toWorld = server.forgeGetWorldMap().get(to);
        if (toWorld == null) {
            return false;
        }
        WorldInfo oldInfo = player.world.getWorldInfo();

        player.detach();
        player.dimension = to;
        // 先把新维度信息发给客户端，否则客户端仍按旧维度计算视野与雾
        NetworkHooks.sendDimensionDataPacket(player.connection.netManager, player);
        player.connection.sendPacket(new SRespawnPacket(to, oldInfo.getGenerator(),
                player.interactionManager.getGameType()));
        player.connection.sendPacket(new SServerDifficultyPacket(oldInfo.getDifficulty(),
                oldInfo.isDifficultyLocked()));
        PlayerList playerList = server.getPlayerList();
        playerList.updatePermissionLevel(player);
        fromWorld.removePlayer(player, true);
        player.revive();

        float pitch = player.rotationPitch;
        float yaw = player.rotationYaw;
        fromWorld.getProfiler().startSection("moving");
        player.setLocationAndAngles(toX, toY, toZ, yaw, pitch);
        fromWorld.getProfiler().endSection();

        player.setWorld(toWorld);
        toWorld.func_217447_b(player);
        player.connection.setPlayerLocation(player.posX, player.posY, player.posZ, yaw, pitch);
        player.interactionManager.func_73080_a(toWorld);
        player.connection.sendPacket(new SPlayerAbilitiesPacket(player.abilities));
        playerList.func_72354_b(player, toWorld);
        playerList.sendInventory(player);
        for (EffectInstance instance : player.getActivePotionEffects()) {
            player.connection.sendPacket(new SPlayEntityEffectPacket(player.getEntityId(), instance));
        }
        player.connection.sendPacket(new SPlaySoundEventPacket(1032, BlockPos.ZERO, 0, false));
        // 与 vanilla 一致地派发"玩家换维度"事件，让其它模组（床位记录、地图等）能同步
        BasicEventHooks.firePlayerChangedDimensionEvent(player, from, to);
        return true;
    }

    /**
     * 把区域变更广播给该维度内的玩家。
     *
     * <p>维度 id 在注册表里查不到时只记一条日志并跳过广播：数据改动与落盘照旧完成，
     * 不能让一次广播失败把删除/写回结果一起丢掉。
     */
    private void broadcast(int dimId, int id, Area area, boolean delete) {
        DimensionType dim = dimType(dimId);
        if (dim == null) {
            LOGGER.warn("维度 {} 不在当前注册表中，跳过区域广播（id={}）", dimId, id);
            return;
        }
        if (delete) {
            AreaSync.deleteToAll(dim, id);
        } else {
            AreaSync.toAll(dim, id, area);
        }
    }

    /**
     * 维度 id → {@link DimensionType}；未登记的 id 返回 null。
     *
     * <p>{@code DimensionType#getById} 对未知 id 会回落到注册表默认值（主世界），若直接采信，
     * 存档里遗留的已卸载维度会被当成主世界，广播与传送都会打到错误的地方。故按 id 回比一次。
     */
    private static DimensionType dimType(int id) {
        DimensionType type = DimensionType.getById(id);
        return type != null && type.getId() == id ? type : null;
    }
}
