package org.soraworld.areaeffect.util;

import net.minecraft.entity.player.EntityPlayer;

import java.nio.ByteBuffer;

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

    public byte[] cui(int id, int size) {
        ByteBuffer buf = ByteBuffer.allocate(19);
        buf.put((byte) 0);
        buf.put((byte) 'p');
        buf.put((byte) id);
        buf.putInt(size);
        buf.putInt(x);
        buf.putInt(y);
        buf.putInt(z);
        return buf.array();
    }

    @Override
    public String toString() {
        return "(" + x + "," + y + "," + z + ')';
    }
}
