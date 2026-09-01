package net.minecraft.command;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

import java.util.List;

public interface ICommand extends Comparable<ICommand> {
    /*
     * 1.12.2 - getName
     * 1.7.10-1.10.2 - getCommandName
     * */
    String func_71517_b();

    /*
     * 1.9-1.12.2 - getUsage
     * 1.7.10-1.8 - getCommandUsage
     * */
    String func_71518_a(ICommandSender sender);

    /*
     * 1.7.10 - getCommandAliases
     * 1.8-1.12.2 - getAliases
     * */
    List<String> func_71514_a();

    /*
     * 1.10.2-1.12.2 - execute
     * */
    void func_184881_a(MinecraftServer server, ICommandSender sender, String[] args);

    /*
     * 1.7.10 - processCommand
     * 1.8 - execute
     * */
    void func_71515_b(ICommandSender sender, String[] args);

    /*
     * 1.10.2-1.12.2 - checkPermission
     * */
    boolean func_184882_a(MinecraftServer server, ICommandSender sender);

    /*
     * 1.7.10 - canCommandSenderUseCommand
     * 1.8 - canCommandSenderUse
     * */
    boolean func_71519_b(ICommandSender sender);

    /*
     * 1.11.2-1.12.2 - getTabCompletions
     * 1.10.2 - getTabCompletionOptions
     * */
    List<String> func_184883_a(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos);

    /*
     * 1.7.10 - addTabCompletionOptions
     * */
    List<String> func_71516_a(ICommandSender sender, String[] args);

    /*
     * 1.10.2-1.12.2 - isUsernameIndex
     * */
    boolean func_82358_a(String[] args, int index);

    /*
     * 1.8 - addTabCompletionOptions
     * */
    List<String> func_180525_a(ICommandSender sender, String[] args, net.minecraft.util.BlockPos pos);

}
