package org.soraworld.areaeffect.util;

import net.minecraft.entity.Entity;

import java.nio.charset.StandardCharsets;

/**
 * @author Himmelt
 */
public class Vec3i {

    public final int x, y, z;

    public Vec3i(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3i(Entity entity) {
        this.x = floor(entity.getPosX());
        this.y = floor(entity.getPosY());
        this.z = floor(entity.getPosZ());
    }

    public static int floor(double value) {
        int i = (int) value;
        return value < (double) i ? i - 1 : i;
    }

    @Override
    public String toString() {
        return "(" + x + " , " + y + " , " + z + ")";
    }

    public byte[] cui(int pos, int size) {
        return ("p|" + (pos - 1) + '|' + x + '|' + y + '|' + z + '|' + size).getBytes(StandardCharsets.UTF_8);
    }
}
