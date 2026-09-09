package org.soraworld.areaeffect.common.util;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;

public class Vec3i {
    public final int x, y, z;

    public Vec3i(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3i(EntityPlayer player) {
        // 必须向下取整而非 (int) 强转截断：负坐标时截断会选错方块（如 -0.5 截断为 0，实际应为 -1）
        this(MathHelper.floor_double(player.posX), MathHelper.floor_double(player.posY), MathHelper.floor_double(player.posZ));
    }

    @Override
    public String toString() {
        return "(" + x + "," + y + "," + z + ')';
    }
}
