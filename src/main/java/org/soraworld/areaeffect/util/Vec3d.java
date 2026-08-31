package org.soraworld.areaeffect.util;

import net.minecraft.entity.Entity;

/**
 * @author Himmelt
 */
public class Vec3d {

    public final double x, y, z;

    public Vec3d(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3d(Entity entity) {
        this.x = entity.getPosX();
        this.y = entity.getBoundingBox().minY;
        this.z = entity.getPosZ();
    }
}
