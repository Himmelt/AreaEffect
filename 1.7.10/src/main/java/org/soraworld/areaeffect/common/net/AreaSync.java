package org.soraworld.areaeffect.common.net;

import net.minecraft.entity.player.EntityPlayerMP;
import org.soraworld.areaeffect.common.network.Area;
import org.soraworld.areaeffect.common.network.MessageAreaDelete;
import org.soraworld.areaeffect.common.network.MessageAreaUpdate;
import org.soraworld.areaeffect.common.network.PacketChannel;

/**
 * 区域数据的服务端 → 客户端广播。
 *
 * <p>只有"发送"语义、不持有任何状态，因此做成静态工具，让请求处理层不必为此依赖代理。
 * 统一放在 net 包，与消息定义同层。
 */
public final class AreaSync {

    private AreaSync() {
    }

    /** 把单个区域的完整数据推给指定玩家（登录全量同步用）。 */
    public static void toPlayer(EntityPlayerMP player, int dim, int id, Area area) {
        PacketChannel.sendTo(new MessageAreaUpdate(dim, id, area), player);
    }

    /** 把新增 / 更新的区域广播给所有在线玩家（服务端不存在时由 PacketChannel 静默返回）。 */
    public static void toAll(int dim, int id, Area area) {
        PacketChannel.sendToAll(new MessageAreaUpdate(dim, id, area));
    }

    /** 广播某个区域的删除。客户端据此统一刷新本地镜像。 */
    public static void deleteToAll(int dim, int id) {
        PacketChannel.sendToAll(new MessageAreaDelete(dim, id));
    }
}
