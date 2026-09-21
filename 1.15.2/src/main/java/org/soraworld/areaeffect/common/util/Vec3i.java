package org.soraworld.areaeffect.common.util;

import net.minecraft.entity.player.PlayerEntity;

public class Vec3i {
    public final int x, y, z;

    public Vec3i(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3i(PlayerEntity player) {
        // 1.15.2 将 Entity 的 posX/posY/posZ 字段设为私有，改用 getPosX()/getPosY()/getPosZ()
        this(floor(player.getPosX()), floor(player.getPosY()), floor(player.getPosZ()));
    }

    /**
     * 世界坐标 → 方块坐标（向下取整）。必须向下取整而非 {@code (int)} 强转截断：
     * 负坐标时截断会选错方块（{@code (int) -0.5} 得 0，而 -0.5 所在的方块是 -1）。
     * 行为与原版 {@code MathHelper.floor_double} 一致。
     *
     * <p>形状的点在形状内判定与选点都依赖这个语义，原先 {@code PrismShape} 与
     * {@code SphereShape} 各写了一份私有副本，现统一到这里，避免三处实现各自漂移
     * —— 它与 {@code AreaShape#boundsContains} 的"下界闭、上界开"约定是配套的。
     */
    public static int floor(double value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }

    @Override
    public String toString() {
        return "(" + x + "," + y + "," + z + ')';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Vec3i)) {
            return false;
        }
        Vec3i v = (Vec3i) o;
        return x == v.x && y == v.y && z == v.z;
    }

    @Override
    public int hashCode() {
        return 31 * (31 * x + y) + z;
    }
}
