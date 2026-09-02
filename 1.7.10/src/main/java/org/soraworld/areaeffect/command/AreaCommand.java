package org.soraworld.areaeffect.command;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

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
                    float gamma = args.size() >= 1 ? Float.parseFloat(args.get(0)) : 1.0F;
                    float speed = args.size() >= 2 ? Float.parseFloat(args.get(1)) : 0.2F;
                    proxy.createArea(player, gamma, speed);
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
                    proxy.sendChatTranslation(player, "info.light", area.gamma);
                    proxy.sendChatTranslation(player, "info.speed", area.speed);
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
        addSub(new ICommand(true, "level") {
            @Override
            public void execute(EntityPlayerMP player, Args args) {
                Area area = proxy.findAreaAt(player);
                if (area != null) {
                    if (args.notEmpty()) {
                        try {
                            float old = area.gamma;
                            area.gamma = Float.parseFloat(args.first());
                            if (old != area.gamma) {
                                if (CommonProxy.isDedicated(player)) {
                                    proxy.sendGammaToAll(player.dimension, area.id, area.gamma);
                                }
                                proxy.save();
                            }
                            proxy.sendChatTranslation(player, "info.light", area.gamma);
                        } catch (Throwable e) {
                            proxy.sendChatTranslation(player, "invalid.float");
                        }
                    } else {
                        proxy.sendChatTranslation(player, "info.light", area.gamma);
                    }
                } else {
                    proxy.sendChatTranslation(player, "info.notInArea");
                }
            }
        });
        addSub(new ICommand(true, "speed") {
            @Override
            public void execute(EntityPlayerMP player, Args args) {
                Area area = proxy.findAreaAt(player);
                if (area != null) {
                    if (args.notEmpty()) {
                        try {
                            float old = area.speed;
                            area.speed = Float.parseFloat(args.first());
                            if (old != area.speed) {
                                if (CommonProxy.isDedicated(player)) {
                                    proxy.sendSpeedToAll(player.dimension, area.id, area.speed);
                                }
                                proxy.save();
                            }
                            proxy.sendChatTranslation(player, "info.speed", area.speed);
                        } catch (Throwable e) {
                            proxy.sendChatTranslation(player, "invalid.float");
                        }
                    } else {
                        proxy.sendChatTranslation(player, "info.speed", area.speed);
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
    public String getCommandName() {
        return getAlias(0);
    }
    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/light pos1/pos2/create/level/speed/info/delete/tool";
    }
    @Override
    public List<String> getAliases() {
        return getAliases();
    }
    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        execute(sender, new Args(args));
    }
    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return sender.canCommandSenderUseCommand(2, "gamemode");
    }
    @Override
    public List<String> getTabCompletionOptions(ICommandSender sender, String[] args) {
        return tabCompletions(new Args(args));
    }
    @Override
    public boolean isUsernameIndex(String[] args, int index) {
        return false;
    }
    public int compareTo(net.minecraft.command.ICommand command) {
        if (command instanceof AreaCommand) {
            return this.getCommandName().compareTo(command.getCommandName());
        } else {
            return 1;
        }
    }
}
