package org.soraworld.areaeffect.common.util;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;

public class Vec3d {
    public final double x, y, z;

    public Vec3d(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3d(Entity entity) {
        // 1.15.2 将 Entity 的 posX/posY/posZ 字段设为私有，改用 getPosX()/getPosY()/getPosZ()
        this.x = entity.getPosX();
        double tmpY;
        // 1.13+ 重命名为 getBoundingBox()
        AxisAlignedBB box = entity.getBoundingBox();
        if (box != null) {
            tmpY = box.minY;
        } else {
            tmpY = entity.getPosY();
        }
        this.y = tmpY;
        this.z = entity.getPosZ();
    }
}
