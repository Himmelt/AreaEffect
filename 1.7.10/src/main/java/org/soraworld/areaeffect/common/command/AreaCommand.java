package org.soraworld.areaeffect.common.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;

import org.soraworld.areaeffect.common.network.Area;
import org.soraworld.areaeffect.common.CommonProxy;
import org.soraworld.areaeffect.common.util.Vec3i;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AreaCommand extends CommandBase {

    private static final String[] SUBS = {"pos1", "pos2", "create", "delete", "info", "list", "tp", "lightness", "duration", "tool"};

    private final String name;
    private final CommonProxy proxy;
    private final String[] aliases;

    public AreaCommand(CommonProxy proxy, String... aliases) {
        this.proxy = proxy;
        this.name = aliases != null && aliases.length > 0 ? aliases[0] : "areaeffect";
        this.aliases = aliases != null && aliases.length > 1
                ? Arrays.copyOfRange(aliases, 1, aliases.length)
                : new String[0];
    }

    @Override
    public String getCommandName() {
        return name;
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/areaeffect pos1|pos2|create|lightness|duration|info|delete|tool";
    }

    @Override
    public List<String> getCommandAliases() {
        List<String> aliases = new ArrayList<>();
        Collections.addAll(aliases, this.aliases);
        return aliases;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;
        String sub = args.length > 0 ? args[0] : "";
        if ("pos1".equals(sub)) {
            proxy.setPos1(player, new Vec3i(player), true);
        } else if ("pos2".equals(sub)) {
            proxy.setPos2(player, new Vec3i(player), true);
        } else if ("create".equals(sub)) {
            try {
                float lightness = args.length >= 2 ? Float.parseFloat(args[1]) : 90.0F;
                float duration = args.length >= 3 ? Float.parseFloat(args[2]) : 1.0F;
                proxy.createArea(player, lightness, duration);
            } catch (Throwable t) {
                proxy.sendChatTranslation(player, "invalid.float");
            }
        } else if ("delete".equals(sub)) {
            proxy.deleteArea(player);
        } else if ("info".equals(sub)) {
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
        } else if ("list".equals(sub)) {
            if (args.length < 2) {
                proxy.showList(player, player.dimension, false);
            } else if ("all".equals(args[1])) {
                proxy.showList(player, 0, true);
            } else {
                try {
                    proxy.showList(player, Integer.parseInt(args[1]), false);
                } catch (Throwable t) {
                    proxy.sendChatTranslation(player, "invalid.int");
                }
            }
        } else if ("tp".equals(sub)) {
            if (args.length >= 2) {
                try {
                    proxy.tpAreaById(player, Integer.parseInt(args[1]));
                } catch (Throwable t) {
                    proxy.sendChatTranslation(player, "invalid.int");
                }
            } else {
                proxy.sendChatTranslation(player, "empty.args");
            }
        } else if ("lightness".equals(sub)) {
            Area area = proxy.findAreaAt(player);
            if (area != null) {
                if (args.length >= 2) {
                    try {
                        float value = Float.parseFloat(args[1]);
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
                    } catch (Throwable t) {
                        proxy.sendChatTranslation(player, "invalid.float");
                    }
                }
                proxy.sendChatTranslation(player, "info.lightness", area.lightness);
            } else {
                proxy.sendChatTranslation(player, "info.notInArea");
            }
        } else if ("duration".equals(sub)) {
            Area area = proxy.findAreaAt(player);
            if (area != null) {
                if (args.length >= 2) {
                    try {
                        float value = Float.parseFloat(args[1]);
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
                    } catch (Throwable t) {
                        proxy.sendChatTranslation(player, "invalid.float");
                    }
                }
                proxy.sendChatTranslation(player, "info.duration", area.duration);
            } else {
                proxy.sendChatTranslation(player, "info.notInArea");
            }
        } else if ("tool".equals(sub)) {
            proxy.commandTool(player);
        }
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return sender.canCommandSenderUseCommand(2, "gamemode");
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, SUBS);
        }
        return null;
    }
}
