package org.soraworld.areaeffect.common.util;

import net.minecraft.command.ICommandSource;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.TranslationTextComponent;

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
     * OP 权限等级 2 判定。客户端提交"已鉴权"的说法不可信，服务端每个入口都要独立判一次。
     *
     * <p>1.14 把权限判定收敛到 {@code Entity#hasPermissionLevel(int)}（对玩家即查 OP 等级），
     * 因此这里按"有实体就查实体、无实体（控制台/命令方块）按最高权限"判定 ——
     * 与 1.12 的 {@code canUseCommand(2, "gamemode")} 在控制台恒为 true 的语义一致。
     */
    public static boolean canManage(ICommandSource sender) {
        if (sender == null) {
            return false;
        }
        if (sender instanceof Entity) {
            return ((Entity) sender).hasPermissionLevel(2);
        }
        return true;
    }

    /** 发送一条翻译键消息（参数交给客户端本地化）。 */
    public static void chat(ICommandSource sender, String key, Object... args) {
        sender.sendMessage(new TranslationTextComponent(key, args));
    }

    /** 发送一条以另一翻译键作为参数的消息（用于拼装物品名等）。 */
    public static void chatWith(ServerPlayerEntity player, String key, String argKey) {
        player.sendMessage(new TranslationTextComponent(key, new TranslationTextComponent(argKey)));
    }
}
