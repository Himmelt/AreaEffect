package org.soraworld.areaeffect.common.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;

import org.soraworld.areaeffect.common.CommonProxy;
import org.soraworld.areaeffect.common.util.Vec3i;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AreaCommand extends CommandBase {

    private static final String[] SUBS = {"pos1", "pos2", "create", "tool"};

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
        return "/areaeffect pos1|pos2|create|tool";
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
                proxy.sendChatTranslation(player, "chat.invalid.float");
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