package org.soraworld.areaeffect.common.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.ServerPlayerEntity;
import org.soraworld.areaeffect.common.CommonProxy;
import org.soraworld.areaeffect.common.util.Vec3i;

/**
 * {@code /areaeffect}（别名 {@code /aef}）指令树：pos1 / pos2 / create / tool。
 *
 * <p>1.13 起指令系统换成 Brigadier，旧的 {@code ICommand}/{@code CommandBase} 一并移除：
 * 指令树在 {@code FMLServerStartingEvent} 上一次性注册进 {@link CommandDispatcher}，
 * 权限与"必须是玩家"这两个门禁都收在根节点的 {@code requires} 里，子命令不再各自判定。
 *
 * <p>区域管理（列表 / 传送 / 删除 / 特效与参数）已迁移到客户端面板（默认 J 键），
 * 这里只保留选点与创建这几条与客户端交互无关的入口。
 */
public final class AreaCommand {

    private AreaCommand() {
    }

    public static void register(CommandDispatcher<CommandSource> dispatcher, CommonProxy proxy) {
        LiteralCommandNode<CommandSource> node = dispatcher.register(build("areaeffect", proxy));
        // 别名 /aef：重定向到同一棵子命令树，避免把整棵树注册两遍（两棵树会各自维护建议与补全）
        dispatcher.register(Commands.literal("aef").redirect(node));
    }

    private static LiteralArgumentBuilder<CommandSource> build(String name, CommonProxy proxy) {
        return Commands.literal(name)
                // 门禁：执行者必须是玩家（控制台/命令方块无选区也无手持物），且为 OP 权限等级 2+
                .requires(source -> source.getEntity() instanceof ServerPlayerEntity && source.hasPermissionLevel(2))
                .then(Commands.literal("pos1").executes(context -> {
                    // 以玩家当前脚下方块为选区起点（球体 / 圆柱下即圆心）
                    proxy.onSelectToolLeft(player(context), new Vec3i(player(context)));
                    return 1;
                }))
                .then(Commands.literal("pos2").executes(context -> {
                    // 选区终点（球体 / 圆柱下即半径点）
                    proxy.onSelectToolRight(player(context), new Vec3i(player(context)));
                    return 1;
                }))
                .then(Commands.literal("create").executes(context -> {
                    // 只创建空区域（不带效果），效果由面板（默认 J 键）后续添加
                    proxy.createArea(player(context));
                    return 1;
                }))
                .then(Commands.literal("tool").executes(context -> {
                    proxy.commandTool(player(context));
                    return 1;
                }))
                // 未识别或缺失子命令：给出用法而不静默返回 —— Brigadier 的默认报错只列子命令名，
                // 玩家看不出"区域管理已经搬到面板里"。区域管理相关操作不再提供指令。
                .executes(context -> {
                    proxy.sendChatTranslation(player(context), "chat.command.usage");
                    return 0;
                });
    }

    /** 取执行者玩家；根节点的 requires 已经保证其存在与类型，此处仅做一次收窄。 */
    private static ServerPlayerEntity player(CommandContext<CommandSource> context) {
        return (ServerPlayerEntity) context.getSource().getEntity();
    }
}
