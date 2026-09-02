package org.soraworld.areaeffect.command;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;

import java.util.*;


public abstract class ICommand {

    private final boolean onlyPlayer;
    private final List<String> aliases = new ArrayList<>();
    private final HashMap<String, ICommand> subs = new LinkedHashMap<>();

    public ICommand(boolean onlyPlayer, String... aliases) {
        this.onlyPlayer = onlyPlayer;
        this.aliases.addAll(Arrays.asList(aliases));
    }

    public void addAlias(String alias) {
        aliases.add(alias);
    }

    public boolean hasAlias(String alias) {
        return aliases.contains(alias);
    }

    public String getAlias(int index) {
        return aliases.get(index);
    }

    public List<String> getAliases() {
        return aliases;
    }

    public void execute(ICommandSender sender, Args args) {
        if (args.empty()) {
            return;
        }
        ICommand sub = subs.get(args.first());
        if (sub == null) {
            return;
        }
        args.next();
        if (sender instanceof EntityPlayer) {
            sub.execute((EntityPlayer) sender, args);
            return;
        }
        if (!onlyPlayer) {
            sub.execute(sender, args);
        }
    }

    public void execute(EntityPlayer player, Args args) {
        execute((ICommandSender) player, args);
    }

    public List<String> tabCompletions(Args args) {
        String first = args.first();
        if (args.size() == 1) {
            return getMatchList(first, subs.keySet());
        }
        if (subs.containsKey(first)) {
            args.next();
            return subs.get(first).tabCompletions(args);
        }
        return new ArrayList<>();
    }

    protected void addSub(ICommand sub) {
        for (String alias : sub.aliases) {
            subs.putIfAbsent(alias, sub);
        }
    }

    public static List<String> getMatchList(String text, Collection<String> possibles) {
        ArrayList<String> list = new ArrayList<>();
        if (text.isEmpty()) {
            list.addAll(possibles);
        } else {
            for (String s : possibles) {
                if (s.startsWith(text)) {
                    list.add(s);
                }
            }
        }
        return list;
    }
}
