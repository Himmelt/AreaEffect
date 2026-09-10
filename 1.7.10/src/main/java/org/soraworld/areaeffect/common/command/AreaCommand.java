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
            proxy.onSelectToolLeft(player, new Vec3i(player));
        } else if ("pos2".equals(sub)) {
            proxy.onSelectToolRight(player, new Vec3i(player));
        } else if ("create".equals(sub)) {
            float lightness = 100.0F;
            float duration = 1.0F;
            // 只把参数解析放进 try：原先 createArea 也在 try 里，于是创建过程中的任何异常
            // 都会被报成"参数不是有效的小数"，把玩家和排查者一起带偏。
            try {
                if (args.length >= 2) {
                    lightness = Float.parseFloat(args[1]);
                }
                if (args.length >= 3) {
                    duration = Float.parseFloat(args[2]);
                }
            } catch (NumberFormatException e) {
                proxy.sendChatTranslation(player, "chat.invalid.float");
                return;
            }
            proxy.createArea(player, lightness, duration);
        } else if ("tool".equals(sub)) {
            proxy.commandTool(player);
        } else {
            // 未识别或缺失子命令：必须给出用法，不能静默返回。
            // 区域管理（列表/传送/删除/改亮度时长）已迁移到客户端面板（默认 J 键）不再提供指令，
            // 缺少提示会让玩家误判为指令失效。
            proxy.sendChatTranslation(player, "chat.command.usage");
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