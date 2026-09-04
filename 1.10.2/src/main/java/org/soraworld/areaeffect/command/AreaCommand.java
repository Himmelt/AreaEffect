package org.soraworld.areaeffect.command;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import org.soraworld.areaeffect.network.Area;
import org.soraworld.areaeffect.proxy.CommonProxy;
import org.soraworld.areaeffect.util.Vec3i;

import java.util.List;

public class AreaCommand extends ICommand implements net.minecraft.command.ICommand {

    public AreaCommand(CommonProxy proxy, boolean onlyPlayer, String... aliases) {
        super(onlyPlayer, aliases);
        addSub(new ICommand(true, "pos1") {
            @Override
            public void execute(EntityPlayerMP player, Args args) {
                proxy.setPos1(player, new Vec3i(player), true);
            }
        });
        addSub(new ICommand(true, "pos2") {
            @Override
            public void execute(EntityPlayerMP player, Args args) {
                proxy.setPos2(player, new Vec3i(player), true);
            }
        });
        addSub(new ICommand(true, "create") {
            @Override
            public void execute(EntityPlayerMP player, Args args) {
                try {
                    float lightness = args.size() >= 1 ? Float.parseFloat(args.get(0)) : 90.0F;
                    float duration = args.size() >= 2 ? Float.parseFloat(args.get(1)) : 1.0F;
                    proxy.createArea(player, lightness, duration);
                } catch (Throwable e) {
                    proxy.sendChatTranslation(player, "invalid.float");
                }
            }
        });
        addSub(new ICommand(true, "delete") {
            @Override
            public void execute(EntityPlayerMP player, Args args) {
                proxy.deleteArea(player);
            }
        });
        addSub(new ICommand(true, "info") {
            @Override
            public void execute(EntityPlayerMP player, Args args) {
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
            }
        });
        addSub(new ICommand(true, "list") {
            @Override
            public void execute(EntityPlayerMP player, Args args) {
                if (args.empty()) {
                    proxy.showList(player, player.dimension, false);
                    return;
                }
                if ("all".equals(args.first())) {
                    proxy.showList(player, 0, true);
                    return;
                }
                try {
                    proxy.showList(player, Integer.parseInt(args.first()), false);
                } catch (Throwable ignored) {
                    proxy.sendChatTranslation(player, "invalid.int");
                }
            }
        });
        addSub(new ICommand(true, "tp") {
            @Override
            public void execute(EntityPlayerMP player, Args args) {
                if (args.notEmpty()) {
                    try {
                        proxy.tpAreaById(player, Integer.parseInt(args.first()));
                    } catch (Throwable e) {
                        proxy.sendChatTranslation(player, "invalid.int");
                    }
                } else {
                    proxy.sendChatTranslation(player, "empty.args");
                }
            }
        });
        addSub(new ICommand(true, "lightness") {
            @Override
            public void execute(EntityPlayerMP player, Args args) {
                Area area = proxy.findAreaAt(player);
                if (area != null) {
                    if (args.notEmpty()) {
                        try {
                            float value = Float.parseFloat(args.first());
                            if (!(value >= 0.0F)) {
                                value = 0.0F;
                            }
                            if (value > 100.0F) {
                                value = 100.0F;
                            }
                            float old = area.lightness;
                            area.lightness = value;
                            if (old != area.lightness) {
                                if (CommonProxy.isDedicated(player)) {
                                    proxy.sendLightnessToAll(player.dimension, area.id, area.lightness);
                                }
                                proxy.save();
                            }
                            proxy.sendChatTranslation(player, "info.lightness", area.lightness);
                        } catch (Throwable e) {
                            proxy.sendChatTranslation(player, "invalid.float");
                        }
                    } else {
                        proxy.sendChatTranslation(player, "info.lightness", area.lightness);
                    }
                } else {
                    proxy.sendChatTranslation(player, "info.notInArea");
                }
            }
        });
        addSub(new ICommand(true, "duration") {
            @Override
            public void execute(EntityPlayerMP player, Args args) {
                Area area = proxy.findAreaAt(player);
                if (area != null) {
                    if (args.notEmpty()) {
                        try {
                            float value = Float.parseFloat(args.first());
                            if (!(value >= 0.05F)) {
                                value = 0.05F;
                            }
                            if (value > 60.0F) {
                                value = 60.0F;
                            }
                            float old = area.duration;
                            area.duration = value;
                            if (old != area.duration) {
                                if (CommonProxy.isDedicated(player)) {
                                    proxy.sendDurationToAll(player.dimension, area.id, area.duration);
                                }
                                proxy.save();
                            }
                            proxy.sendChatTranslation(player, "info.duration", area.duration);
                        } catch (Throwable e) {
                            proxy.sendChatTranslation(player, "invalid.float");
                        }
                    } else {
                        proxy.sendChatTranslation(player, "info.duration", area.duration);
                    }
                } else {
                    proxy.sendChatTranslation(player, "info.notInArea");
                }
            }
        });
        addSub(new ICommand(true, "tool") {
            @Override
            public void execute(EntityPlayerMP player, Args args) {
                proxy.commandTool(player);
            }
        });
    }

    @Override
    public String getName() {
        return getAlias(0);
    }
    @Override
    public String getUsage(ICommandSender sender) {
        return "/areaeffect pos1/pos2/create/lightness/duration/info/delete/tool";
    }
    @Override
    public List<String> getAliases() {
        return getAliases();
    }
    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        execute(sender, new Args(args));
    }
    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return sender.canUseCommand(2, "gamemode");
    }
    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, net.minecraft.util.math.BlockPos pos) {
        return tabCompletions(new Args(args));
    }
    @Override
    public boolean isUsernameIndex(String[] args, int index) {
        return false;
    }
    public int compareTo(net.minecraft.command.ICommand command) {
        if (command instanceof AreaCommand) {
            return this.getName().compareTo(command.getName());
        } else {
            return 1;
        }
    }
}
