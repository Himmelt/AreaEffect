package org.soraworld.areaeffect.common.util;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentTranslation;

/**
 * 与玩家 / 服务端运行环境相关的无状态工具。
 *
 * <p>这些能力既不持有数据也不依赖代理，故收成静态方法，避免为了发一条消息或判一次权限
 * 而把整个代理对象传进来。
 */
public final class Players {

    private Players() {
    }

    /** OP 权限等级 2 判定。与指令门禁（{@code CommandBase#checkPermission}）保持一致，
     * 客户端提交"已鉴权"的说法不可信，服务端每个入口都要独立判一次。
     * 1.12.2 的 ICommandSender 权限判定方法由 canCommandSenderUseCommand 改名为 canUseCommand。
     */
    public static boolean canManage(ICommandSender sender) {
        return sender != null && sender.canUseCommand(2, "gamemode");
    }

    /** 发送一条翻译键消息（参数交给客户端本地化）。 */
    public static void chat(ICommandSender sender, String key, Object... args) {
        sender.sendMessage(new TextComponentTranslation(key, args));
    }

    /** 发送一条以另一翻译键作为参数的消息（用于拼装物品名等）。 */
    public static void chatWith(EntityPlayerMP player, String key, String argKey) {
        player.sendMessage(new TextComponentTranslation(key, new TextComponentTranslation(argKey)));
    }
}
