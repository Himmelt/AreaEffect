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

    public int centerX() {
        return cx;
    }

    public int centerY() {
        return cy;
    }

    public int centerZ() {
        return cz;
    }

    public int radius() {
        return radius;
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
    protected boolean exactConflict(AreaShape other) {
        if (other instanceof SphereShape) {
            return conflictSphere((SphereShape) other);
        }
        if (other instanceof PrismShape) {
            // 柱 × 球的逐层采样实现挂在 PrismShape，委托过去保证判据单点维护
            return other.exactConflict(this);
        }
        // 未知形状配对：无精确判据，保守视为冲突（拒绝创建），避免静默漏判
        LOGGER.warn("No exact conflict rule for SphereShape vs {}, treating as conflict", other.getClass().getName());
        return true;
    }

    /**
     * 球 × 球：按 Y 逐层降维——两球在层 by 上的截面各是圆盘
     * dx²+dz² <= r²+ε-dy²（与 {@link #contains} 判据一致），做 2D 共享点采样。
     */
    private boolean conflictSphere(SphereShape s) {
        int y1 = Math.max(bounds.minY, s.bounds.minY);
        int y2 = Math.min(bounds.maxY, s.bounds.maxY);
        int x1 = Math.max(bounds.minX, s.bounds.minX);
        int x2 = Math.min(bounds.maxX, s.bounds.maxX);
        int z1 = Math.max(bounds.minZ, s.bounds.minZ);
        int z2 = Math.min(bounds.maxZ, s.bounds.maxZ);
        long grid = (long) (x2 - x1 + 1) * (z2 - z1 + 1);
        if (grid <= 0L) {
            return false;
        }
        if ((long) (y2 - y1 + 1) * grid > CONFLICT_SAMPLE_LIMIT) {
            return true; // 采样预算超限：退回保守判定（视为冲突）
        }
        double raBase = (double) radius * radius + 0.001D;
        double rbBase = (double) s.radius * s.radius + 0.001D;
        for (int by = y1; by <= y2; by++) {
            double ra = raBase - (double) (by - cy) * (by - cy);
            double rb = rbBase - (double) (by - s.cy) * (by - s.cy);
            if (ra < 0.0D || rb < 0.0D) {
                continue; // 该层至少一球未覆盖
            }
            for (int bx = x1; bx <= x2; bx++) {
                int dxa = bx - cx;
                int dxb = bx - s.cx;
                for (int bz = z1; bz <= z2; bz++) {
                    int dza = bz - cz;
                    int dzb = bz - s.cz;
                    if (dxa * dxa + dza * dza <= ra && dxb * dxb + dzb * dzb <= rb) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public List<Edge> edges() {
        List<Edge> result = new ArrayList<>();
        double xc = cx + 0.5D;
        double yc = cy + 0.5D;
        double zc = cz + 0.5D;
        // 纬线圈：赤道 + ±30°、±60° 纬度（高度 = R*sin，环半径 = sqrt(R²-h²)）
        ring(result, xc, yc, zc, radius);
        double halfSqrt3 = Math.sqrt(3.0D) / 2.0D;
        for (double sinLat : new double[]{0.5D, -0.5D, halfSqrt3, -halfSqrt3}) {
            double h = radius * sinLat;
            double rr = Math.sqrt((double) radius * radius - h * h);
            ring(result, xc, yc + h, zc, rr);
        }
        // 经线圈：垂直大圆每 30° 一个（含原 XY/ZY 平面），共 6 个圆 = 12 条经线
        for (int m = 0; m < 6; m++) {
            double theta = Math.PI * m / 6.0D;
            double cosT = Math.cos(theta);
            double sinT = Math.sin(theta);
            double prevX = 0.0D;
            double prevY = 0.0D;
            double prevZ = 0.0D;
            for (int i = 0; i <= RING_SEGMENTS; i++) {
                double angle = 2.0D * Math.PI * i / RING_SEGMENTS;
                double ca = Math.cos(angle) * radius;
                double sa = Math.sin(angle) * radius;
                double x = xc + ca * cosT;
                double z = zc + ca * sinT;
                double y = yc + sa;
                if (i > 0) {
                    line(result, prevX, prevY, prevZ, x, y, z);
                }
                prevX = x;
                prevY = y;
                prevZ = z;
            }
        }
        return result;
    }

    /** 在高度 y 处绕 XZ 平面画一圈水平环。 */
    private static void ring(List<Edge> result, double xc, double y, double zc, double r) {
        double prevX = 0.0D;
        double prevZ = 0.0D;
        for (int i = 0; i <= RING_SEGMENTS; i++) {
            double angle = 2.0D * Math.PI * i / RING_SEGMENTS;
            double x = xc + r * Math.cos(angle);
            double z = zc + r * Math.sin(angle);
            if (i > 0) {
                line(result, prevX, y, prevZ, x, y, z);
            }
            prevX = x;
            prevZ = z;
        }
    }

    private static void line(List<Edge> result, double x1, double y1, double z1, double x2, double y2, double z2) {
        result.add(new Edge(x1, y1, z1, x2, y2, z2));
    }

    @Override
    public String describeKey() {
        return "gui.areaeffect.desc." + typeId();
    }

    @Override
    public Object[] describeArgs() {
        return new Object[]{cx, cy, cz, radius};
    }
}
