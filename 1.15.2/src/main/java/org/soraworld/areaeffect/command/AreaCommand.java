package org.soraworld.areaeffect.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.ServerPlayerEntity;
import org.soraworld.areaeffect.network.Area;
import org.soraworld.areaeffect.proxy.CommonProxy;

/**
 * @author Himmelt
 */
public class AreaCommand {
    public static void register(CommandDispatcher<CommandSource> dispatcher, CommonProxy proxy) {
        dispatcher.register(Commands.literal("areaeffect")
                .requires((source) -> (source.getEntity() instanceof ServerPlayerEntity) && source.hasPermissionLevel(2))
                .then(Commands.literal("pos1").executes(context -> {
                    ServerPlayerEntity player = context.getSource().asPlayer();
                    proxy.setPos1(player, player.getPosition(), true);
                    return 1;
                }))
                .then(Commands.literal("pos2").executes(context -> {
                    ServerPlayerEntity player = context.getSource().asPlayer();
                    proxy.setPos2(player, player.getPosition(), true);
                    return 1;
                }))
                .then(Commands.literal("create")
                        .then(Commands.argument("lightness", FloatArgumentType.floatArg(0.0F, 100.0F))
                                .then(Commands.argument("duration", FloatArgumentType.floatArg(0.05F, 60.0F))
                                        .executes(context -> {
                                            ServerPlayerEntity player = context.getSource().asPlayer();
                                            float lightness = context.getArgument("lightness", float.class);
                                            float duration = context.getArgument("duration", float.class);
                                            proxy.createArea(player, lightness, duration);
                                            return 1;
                                        }))
                                .executes(context -> {
                                    ServerPlayerEntity player = context.getSource().asPlayer();
                                    float gamma = context.getArgument("gamma", float.class);
                                    proxy.createArea(player, lightness, 1.0F);
                                    return 1;
                                }))
                        .executes(context -> {
                            ServerPlayerEntity player = context.getSource().asPlayer();
                            proxy.createArea(player, 90.0F, 1.0F);
                            return 1;
                        }))
                .then(Commands.literal("delete").executes(context -> {
                    ServerPlayerEntity player = context.getSource().asPlayer();
                    proxy.deleteArea(player);
                    return 1;
                }))
                .then(Commands.literal("info").executes(context -> {
                    ServerPlayerEntity player = context.getSource().asPlayer();
                    Area area = proxy.findAreaAt(player);
                    if (area != null) {
                        proxy.sendChatTranslation(player, "info.pos1", area.pos1());
                        proxy.sendChatTranslation(player, "info.pos2", area.pos2());
                        proxy.sendChatTranslation(player, "info.lightness", area.lightness);
                        proxy.sendChatTranslation(player, "info.duration", area.duration);
                        proxy.setPos1(player, area.vec1(), false);
                        proxy.setPos2(player, area.vec2(), false);
                    } else {
                        proxy.sendChatTranslation(player, "info.notInArea");
                    }
                    return 1;
                }))
                .then(Commands.literal("list")
                        .then(Commands.literal("all").executes(context -> {
                            ServerPlayerEntity player = context.getSource().asPlayer();
                            proxy.showList(player, 0, true);
                            return 1;
                        }))
                        .then(Commands.argument("dim", IntegerArgumentType.integer()).executes(context -> {
                            ServerPlayerEntity player = context.getSource().asPlayer();
                            int dim = context.getArgument("dim", int.class);
                            proxy.showList(player, dim, false);
                            return 1;
                        }))
                        .executes(context -> {
                            ServerPlayerEntity player = context.getSource().asPlayer();
                            proxy.showList(player, player.dimension.getId(), false);
                            return 1;
                        }))
                .then(Commands.literal("tp")
                        .then(Commands.argument("id", IntegerArgumentType.integer(0)).executes(context -> {
                            ServerPlayerEntity player = context.getSource().asPlayer();
                            int id = context.getArgument("id", int.class);
                            proxy.tpAreaById(player, id);
                            return 1;
                        })))
                .then(Commands.literal("lightness")
                        .then(Commands.argument("lightness", FloatArgumentType.floatArg(0.0F, 100.0F)).executes(context -> {
                            ServerPlayerEntity player = context.getSource().asPlayer();
                            Area area = proxy.findAreaAt(player);
                            if (area != null) {
                                float value = context.getArgument("lightness", float.class);
                                float old = area.lightness;
                                area.lightness = value;
                                if (old != area.lightness) {
                                    if (CommonProxy.isDedicated(player)) {
                                        proxy.sendLightnessToAll(player.dimension.getId(), area.id, area.lightness);
                                    }
                                    proxy.save();
                                }
                                proxy.sendChatTranslation(player, "info.lightness", area.lightness);
                            } else {
                                proxy.sendChatTranslation(player, "info.notInArea");
                            }
                            return 1;
                        }))
                        .executes(context -> {
                            ServerPlayerEntity player = context.getSource().asPlayer();
                            Area area = proxy.findAreaAt(player);
                            if (area != null) {
                                proxy.sendChatTranslation(player, "info.lightness", area.lightness);
                            } else {
                                proxy.sendChatTranslation(player, "info.notInArea");
                            }
                            return 1;
                        }))
                .then(Commands.literal("duration")
                        .then(Commands.argument("duration", FloatArgumentType.floatArg(0.05F, 60.0F)).executes(context -> {
                            ServerPlayerEntity player = context.getSource().asPlayer();
                            Area area = proxy.findAreaAt(player);
                            if (area != null) {
                                float value = context.getArgument("duration", float.class);
                                float old = area.duration;
                                area.duration = value;
                                if (old != area.duration) {
                                    if (CommonProxy.isDedicated(player)) {
                                        proxy.sendDurationToAll(player.dimension.getId(), area.id, area.duration);
                                    }
                                    proxy.save();
                                }
                                proxy.sendChatTranslation(player, "info.duration", area.duration);
                            } else {
                                proxy.sendChatTranslation(player, "info.notInArea");
                            }
                            return 1;
                        }))
                        .executes(context -> {
                            ServerPlayerEntity player = context.getSource().asPlayer();
                            Area area = proxy.findAreaAt(player);
                            if (area != null) {
                                proxy.sendChatTranslation(player, "info.duration", area.duration);
                            } else {
                                proxy.sendChatTranslation(player, "info.notInArea");
                            }
                            return 1;
                        }))
                .then(Commands.literal("tool").executes(context -> {
                    ServerPlayerEntity player = context.getSource().asPlayer();
                    proxy.commandTool(player);
                    return 1;
                }))
        );
    }
}
