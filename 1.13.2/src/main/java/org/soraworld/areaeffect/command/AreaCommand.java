package org.soraworld.areaeffect.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.EntityPlayerMP;
import org.soraworld.areaeffect.network.Area;
import org.soraworld.areaeffect.proxy.CommonProxy;

/**
 * @author Himmelt
 */
public class AreaCommand {
    public static void register(CommandDispatcher<CommandSource> dispatcher, CommonProxy proxy) {
        dispatcher.register(Commands.literal("light")
                .requires((source) -> (source.getEntity() instanceof EntityPlayerMP) && source.hasPermissionLevel(2))
                .then(Commands.literal("pos1").executes(context -> {
                    EntityPlayerMP player = context.getSource().asPlayer();
                    proxy.setPos1(player, player.getPosition(), true);
                    return 1;
                }))
                .then(Commands.literal("pos2").executes(context -> {
                    EntityPlayerMP player = context.getSource().asPlayer();
                    proxy.setPos2(player, player.getPosition(), true);
                    return 1;
                }))
                .then(Commands.literal("create")
                        .then(Commands.argument("gamma", FloatArgumentType.floatArg())
                                .then(Commands.argument("speed", FloatArgumentType.floatArg(0.0F))
                                        .executes(context -> {
                                            EntityPlayerMP player = context.getSource().asPlayer();
                                            float gamma = context.getArgument("gamma", float.class);
                                            float speed = context.getArgument("speed", float.class);
                                            proxy.createArea(player, gamma, speed);
                                            return 1;
                                        }))
                                .executes(context -> {
                                    EntityPlayerMP player = context.getSource().asPlayer();
                                    float gamma = context.getArgument("gamma", float.class);
                                    proxy.createArea(player, gamma, 0.2F);
                                    return 1;
                                }))
                        .executes(context -> {
                            EntityPlayerMP player = context.getSource().asPlayer();
                            proxy.createArea(player, 1.0F, 0.2F);
                            return 1;
                        }))
                .then(Commands.literal("delete").executes(context -> {
                    EntityPlayerMP player = context.getSource().asPlayer();
                    proxy.deleteArea(player);
                    return 1;
                }))
                .then(Commands.literal("info").executes(context -> {
                    EntityPlayerMP player = context.getSource().asPlayer();
                    Area area = proxy.findAreaAt(player);
                    if (area != null) {
                        proxy.sendChatTranslation(player, "info.pos1", area.pos1());
                        proxy.sendChatTranslation(player, "info.pos2", area.pos2());
                        proxy.sendChatTranslation(player, "info.light", area.gamma);
                        proxy.sendChatTranslation(player, "info.speed", area.speed);
                        proxy.setPos1(player, area.vec1(), false);
                        proxy.setPos2(player, area.vec2(), false);
                    } else {
                        proxy.sendChatTranslation(player, "info.notInArea");
                    }
                    return 1;
                }))
                .then(Commands.literal("list")
                        .then(Commands.literal("all").executes(context -> {
                            EntityPlayerMP player = context.getSource().asPlayer();
                            proxy.showList(player, 0, true);
                            return 1;
                        }))
                        .then(Commands.argument("dim", IntegerArgumentType.integer()).executes(context -> {
                            EntityPlayerMP player = context.getSource().asPlayer();
                            int dim = context.getArgument("dim", int.class);
                            proxy.showList(player, dim, false);
                            return 1;
                        }))
                        .executes(context -> {
                            EntityPlayerMP player = context.getSource().asPlayer();
                            proxy.showList(player, player.dimension.getId(), false);
                            return 1;
                        }))
                .then(Commands.literal("tp")
                        .then(Commands.argument("id", IntegerArgumentType.integer(0)).executes(context -> {
                            EntityPlayerMP player = context.getSource().asPlayer();
                            int id = context.getArgument("id", int.class);
                            proxy.tpAreaById(player, id);
                            return 1;
                        })))
                .then(Commands.literal("level")
                        .then(Commands.argument("gamma", FloatArgumentType.floatArg()).executes(context -> {
                            EntityPlayerMP player = context.getSource().asPlayer();
                            Area area = proxy.findAreaAt(player);
                            if (area != null) {
                                float old = area.gamma;
                                area.gamma = context.getArgument("gamma", float.class);
                                if (old != area.gamma) {
                                    if (CommonProxy.isDedicated(player)) {
                                        proxy.sendGammaToAll(player.dimension.getId(), area.id, area.gamma);
                                    }
                                    proxy.save();
                                }
                                proxy.sendChatTranslation(player, "info.light", area.gamma);
                            } else {
                                proxy.sendChatTranslation(player, "info.notInArea");
                            }
                            return 1;
                        }))
                        .executes(context -> {
                            EntityPlayerMP player = context.getSource().asPlayer();
                            Area area = proxy.findAreaAt(player);
                            if (area != null) {
                                proxy.sendChatTranslation(player, "info.light", area.gamma);
                            } else {
                                proxy.sendChatTranslation(player, "info.notInArea");
                            }
                            return 1;
                        }))
                .then(Commands.literal("speed")
                        .then(Commands.argument("speed", FloatArgumentType.floatArg()).executes(context -> {
                            EntityPlayerMP player = context.getSource().asPlayer();
                            Area area = proxy.findAreaAt(player);
                            if (area != null) {
                                float old = area.speed;
                                area.speed = context.getArgument("speed", float.class);
                                if (old != area.speed) {
                                    if (CommonProxy.isDedicated(player)) {
                                        proxy.sendSpeedToAll(player.dimension.getId(), area.id, area.speed);
                                    }
                                    proxy.save();
                                }
                                proxy.sendChatTranslation(player, "info.speed", area.speed);
                            } else {
                                proxy.sendChatTranslation(player, "info.notInArea");
                            }
                            return 1;
                        }))
                        .executes(context -> {
                            EntityPlayerMP player = context.getSource().asPlayer();
                            Area area = proxy.findAreaAt(player);
                            if (area != null) {
                                proxy.sendChatTranslation(player, "info.speed", area.speed);
                            } else {
                                proxy.sendChatTranslation(player, "info.notInArea");
                            }
                            return 1;
                        }))
                .then(Commands.literal("tool").executes(context -> {
                    EntityPlayerMP player = context.getSource().asPlayer();
                    proxy.commandTool(player);
                    return 1;
                }))
        );
    }
}
