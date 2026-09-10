package org.soraworld.areaeffect.common.util;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;

/**
 * 与玩家 / 服务端运行环境相关的无状态工具。
 *
 * <p>这些能力既不持有数据也不依赖代理，故收成静态方法，避免为了发一条消息或判一次权限
 * 而把整个代理对象传进来。
 */
public final class Players {

    private Players() {
    }

    /**
     * OP 权限等级 2 判定。与指令门禁（{@code CommandBase#canCommandSenderUseCommand}）保持一致，
     * 客户端提交"已鉴权"的说法不可信，服务端每个入口都要独立判一次。
     */
    public static boolean canManage(ICommandSender sender) {
        return sender != null && sender.canCommandSenderUseCommand(2, "gamemode");
    }

    /** 发送一条翻译键消息（参数交给客户端本地化）。 */
    public static void chat(ICommandSender sender, String key, Object... args) {
        sender.addChatMessage(new ChatComponentTranslation(key, args));
    }

    /** 发送一条以另一翻译键作为参数的消息（用于拼装物品名等）。 */
    public static void chatWith(EntityPlayerMP player, String key, String argKey) {
        player.addChatMessage(new ChatComponentTranslation(key, new ChatComponentTranslation(argKey)));
    }

    /**
     * 当前是否专用服务端。单机下客户端与服务端加载的是同一代理实例、数据本就同源，
     * 调用点据此跳过对本地玩家的回发同步（推送也无害，只是无谓）。
     */
    public static boolean isDedicated() {
        MinecraftServer server = MinecraftServer.getServer();
        return server != null && server.isDedicatedServer();
    }
}
