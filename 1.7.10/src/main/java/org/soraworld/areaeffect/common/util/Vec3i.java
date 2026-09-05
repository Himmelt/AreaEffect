package org.soraworld.areaeffect.common.util;

import net.minecraft.entity.player.EntityPlayer;

public class Vec3i {
    public final int x, y, z;

    public Vec3i(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3i(EntityPlayer player) {
        this((int) player.posX, (int) player.posY, (int) player.posZ);
    }

    @Override
    public String toString() {
        return "(" + x + "," + y + "," + z + ')';
    }
}
