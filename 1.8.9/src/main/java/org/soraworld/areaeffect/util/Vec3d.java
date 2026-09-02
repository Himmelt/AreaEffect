package org.soraworld.areaeffect.util;

import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;

public class Vec3d {
    public final double x, y, z;

    public Vec3d(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3d(Entity entity) {
        this.x = entity.posX;
        double tmpY;
        AxisAlignedBB box = entity.getEntityBoundingBox();
        if (box != null) {
            tmpY = box.minY;
        } else {
            tmpY = entity.posY;
        }
        this.y = tmpY;
        this.z = entity.posZ;
    }
}
