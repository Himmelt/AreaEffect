package org.soraworld.areaeffect.common.shape;

import org.soraworld.areaeffect.common.util.Vec3i;

import java.util.ArrayList;
import java.util.List;

/**
 * 球体形状：球心 + 球面点，半径 = 3D 距离。
 */
public class SphereShape extends AreaShape {

    public static final int RING_SEGMENTS = 24;

    private final int cx;
    private final int cy;
    private final int cz;
    private final int radius;

    public SphereShape(Vec3i center, Vec3i surface) {
        super(java.util.Arrays.asList(center, surface), false, computeBounds(center, surface));
        this.cx = center.x;
        this.cy = center.y;
        this.cz = center.z;
        int dx = surface.x - center.x;
        int dy = surface.y - center.y;
        int dz = surface.z - center.z;
        this.radius = (int) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static Bounds computeBounds(Vec3i center, Vec3i surface) {
        int dx = surface.x - center.x;
        int dy = surface.y - center.y;
        int dz = surface.z - center.z;
        int r = (int) Math.sqrt(dx * dx + dy * dy + dz * dz);
        return new Bounds(center.x - r, center.y - r, center.z - r,
                center.x + r, center.y + r, center.z + r);
    }

    @Override
    public String typeId() {
        return ShapeTypes.TYPE_SPHERE;
    }

    @Override
    public boolean contains(double x, double y, double z) {
        int bx = floor(x);
        int by = floor(y);
        int bz = floor(z);
        int dx = bx - cx;
        int dy = by - cy;
        int dz = bz - cz;
        return dx * dx + dy * dy + dz * dz <= radius * radius + 0.001D;
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    @Override
    public List<Edge> edges() {
        List<Edge> result = new ArrayList<>();
        double xc = cx + 0.5D;
        double yc = cy + 0.5D;
        double zc = cz + 0.5D;
        double[] cos = new double[RING_SEGMENTS + 1];
        double[] sin = new double[RING_SEGMENTS + 1];
        for (int i = 0; i <= RING_SEGMENTS; i++) {
            double angle = 2.0D * Math.PI * i / RING_SEGMENTS;
            cos[i] = radius * Math.cos(angle);
            sin[i] = radius * Math.sin(angle);
        }
        // XZ 平面大圆（Y = 球心高度）
        for (int i = 0; i < RING_SEGMENTS; i++) {
            line(result, xc + cos[i], yc, zc + sin[i], xc + cos[i + 1], yc, zc + sin[i + 1]);
        }
        // XY 平面大圆（Z = 球心深度）
        for (int i = 0; i < RING_SEGMENTS; i++) {
            line(result, xc + cos[i], yc + sin[i], zc, xc + cos[i + 1], yc + sin[i + 1], zc);
        }
        // ZY 平面大圆（X = 球心横向）
        for (int i = 0; i < RING_SEGMENTS; i++) {
            line(result, xc, yc + sin[i], zc + cos[i], xc, yc + sin[i + 1], zc + cos[i + 1]);
        }
        return result;
    }

    private static void line(List<Edge> result, double x1, double y1, double z1, double x2, double y2, double z2) {
        result.add(new Edge(x1, y1, z1, x2, y2, z2));
    }

    @Override
    public String describe() {
        return "球心(" + cx + "," + cy + "," + cz + ") R=" + radius;
    }
}
