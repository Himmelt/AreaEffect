package net.minecraft.entity.player;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;


public abstract class EntityPlayer extends Entity implements ICommandSender {

    /*
     * 1.7.10 - dimension
     * 1.12.2 - dimension
     * */
    public int field_71093_bK;

    public EntityPlayer(World world) {
        super(world);
    }

    /*
     * 1.7.10 - getHeldItem
     * */
    public abstract ItemStack func_70694_bm();

    /*
     * 1.12.2 - getHeldItemMainhand
     * */
    public abstract ItemStack func_184614_ca();

    /*
     * 1.7.10-1.12.2 - setPosition
     * */
    public abstract void func_70107_b(double x, double y, double z);

    /*
     * 1.7.10-1.12.2 - setPositionAndUpdate
     * */
    public abstract void func_70634_a(double x, double y, double z);

    /*
     * 1.7-1.8 - travelToDimension
     * */
    public abstract void func_71027_c(int dimension);

    /*
     * 1.9-1.12 - changeDimension
     * */
    public abstract Entity func_184204_a(int dimension);
}
