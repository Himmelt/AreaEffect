package org.soraworld.areaeffect.common.selection;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import org.soraworld.areaeffect.common.network.MessageSelection;
import org.soraworld.areaeffect.common.network.PacketChannel;
import org.soraworld.areaeffect.common.shape.Selection;
import org.soraworld.areaeffect.common.util.Vec3i;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 每玩家选区状态（服务端权威）与选区同步下发。
 *
 * <p>选区本身的状态机在 {@link Selection} 里；本类只负责"按玩家持有"与"变更后把镜像发给客户端"，
 * 因此调用点不必再各自记得发送同步包。
 */
public class SelectionManager {

    private final Map<UUID, Selection> selections = new ConcurrentHashMap<>();

    /** 取得玩家选区，不存在则新建（开始选点即隐含创建）。 */
    public Selection of(EntityPlayerMP player) {
        return selections.computeIfAbsent(player.getUniqueID(), uuid -> new Selection());
    }

    /** 当前选区；玩家还没开始选点时返回 null。 */
    public Selection existing(EntityPlayer player) {
        return selections.get(player.getUniqueID());
    }

    /** 左键：二点形状覆盖起点；多边形追加顶点。 */
    public void clickLeft(EntityPlayerMP player, Vec3i pos) {
        of(player).onClickLeft(pos);
        sync(player);
    }

    /** 右键：二点形状覆盖终点；多边形撤回上一顶点。{@code pos} 为 null 表示右键空气。 */
    public void clickRight(EntityPlayerMP player, Vec3i pos) {
        of(player).onClickRight(pos);
        sync(player);
    }

    /** 切换选区形状（重置锚点）。 */
    public void reset(EntityPlayerMP player, String type) {
        of(player).reset(type);
        sync(player);
    }

    /** 清除玩家选区并回发同步包。 */
    public void clear(EntityPlayer player) {
        clear(player, true);
    }

    /** 清除玩家选区；{@code syncClient=false} 时不回发（登出时连接已断，发包无谓）。 */
    public void clear(EntityPlayer player, boolean syncClient) {
        selections.remove(player.getUniqueID());
        if (syncClient && player instanceof EntityPlayerMP) {
            sync((EntityPlayerMP) player);
        }
    }

    /** 清空全部选区（客户端断线重置用）。 */
    public void clearAll() {
        selections.clear();
    }

    /**
     * 把玩家的当前选区同步给客户端，由客户端自绘选区线框。
     * 选区不存在时发送空选区（客户端据此清掉线框）。
     */
    public void sync(EntityPlayerMP player) {
        PacketChannel.sendTo(new MessageSelection(selections.get(player.getUniqueID())), player);
    }
}
