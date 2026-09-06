package org.soraworld.areaeffect.common.shape;

import org.soraworld.areaeffect.common.util.Vec3i;

import java.util.ArrayList;
import java.util.List;

/**
 * 柱状体族：XZ 截面 × 高度模式，覆盖 6 种形状。
 * <ul>
 *   <li>RECT+BOUNDED → box 长方体</li>
 *   <li>RECT+FULL → square_pillar 方柱（通天）</li>
 *   <li>CIRCLE+BOUNDED → cylinder 圆柱</li>
 *   <li>CIRCLE+FULL → round_pillar 通天圆柱</li>
 *   <li>POLYGON+BOUNDED → polygon 有界多边形柱</li>
 *   <li>POLYGON+FULL → polygon_pillar 通天多边形柱</li>
 * </ul>
 * 锚点约定（选点顺序）：
 * <ul>
 *   <li>RECT：anchors[0]、anchors[1] 为两点；XZ 取 min/max；BOUNDED 时 Y 取两锚点 min/max，FULL 时 0..255</li>
 *   <li>CIRCLE：anchors[0] 为圆心，anchors[1] 为半径点；半径 = 水平距离；BOUNDED 时 Y 取两锚点 min/max</li>
 *   <li>POLYGON：FULL 时全部锚点为 XZ 顶点；BOUNDED 时除末两个 Y 锚点外均为顶点，且 closed 必须为 true</li>
 * </ul>
 */
public class PrismShape extends AreaShape {

    public enum Section { RECT, CIRCLE, POLYGON }

    public enum Height { BOUNDED, FULL }

    public static final int RING_SEGMENTS = 24;

    private final Section section;
    private final Height height;
    private final int cx;
    private final int cz;
    private final int radius;

    public PrismShape(Section section, Height height, List<Vec3i> anchors, boolean closed) {
        super(anchors, closed, computeBounds(section, height, anchors, closed));
        this.section = section;
        this.height = height;
        this.cx = section == Section.CIRCLE ? this.anchors.get(0).x : 0;
        this.cz = section == Section.CIRCLE ? this.anchors.get(0).z : 0;
        this.radius = section == Section.CIRCLE
                ? circleRadius(this.anchors.get(0), this.anchors.get(1))
                : 0;
    }

    public Section section() {
        return section;
    }

    public Height height() {
        return height;
    }

    public int radius() {
        return radius;
    }

    public int centerX() {
        return cx;
    }

    public int centerZ() {
        return cz;
    }

    /** 多边形顶点（全部锚点即顶点，XZ 取方块最小角；有界高度 = 顶点 Y 区间）。 */
    public List<Vec3i> polygonVertices() {
        return new ArrayList<>(anchors);
    }

    @Override
    public String typeId() {
        switch (section) {
            case RECT:
                return height == Height.BOUNDED ? ShapeTypes.TYPE_BOX : ShapeTypes.TYPE_SQUARE_PILLAR;
            case CIRCLE:
                return height == Height.BOUNDED ? ShapeTypes.TYPE_CYLINDER : ShapeTypes.TYPE_ROUND_PILLAR;
            default:
                return height == Height.BOUNDED ? ShapeTypes.TYPE_POLYGON : ShapeTypes.TYPE_POLYGON_PILLAR;
        }
    }

    @Override
    public boolean contains(double x, double y, double z) {
        // 通天柱无视高度：Y 不参与判断
        if (height == Height.BOUNDED && (y < bounds.minY || y >= bounds.maxY + 1.0D)) {
            return false;
        }
        switch (section) {
            case RECT:
                return x >= bounds.minX && x < bounds.maxX + 1.0D
                        && z >= bounds.minZ && z < bounds.maxZ + 1.0D;
            case CIRCLE: {
                int bx = floor(x);
                int bz = floor(z);
                int dx = bx - cx;
                int dz = bz - cz;
                return dx * dx + dz * dz <= radius * radius + 0.001D;
            }
            default:
                return polygonContains(floor(x), floor(z));
        }
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    /** 射线法：点 (bx,bz) 是否在顶点多边形内（边界极小容差）。 */
    private boolean polygonContains(int bx, int bz) {
        List<Vec3i> verts = polygonVertices();
        if (verts.size() < 3) {
            return false;
        }
        boolean inside = false;
        int n = verts.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            Vec3i vi = verts.get(i);
            Vec3i vj = verts.get(j);
            if ((vi.z > bz) != (vj.z > bz)) {
                double cx = (double) (vj.x - vi.x) * (bz - vi.z) / (double) (vj.z - vi.z) + vi.x;
                if (bx < cx) {
                    inside = !inside;
                }
            }
        }
        return inside;
    }

    private static int circleRadius(Vec3i center, Vec3i surface) {
        int dx = surface.x - center.x;
        int dz = surface.z - center.z;
        return (int) Math.sqrt(dx * dx + dz * dz);
    }

    private static Bounds computeBounds(Section section, Height height, List<Vec3i> anchors, boolean closed) {
        List<Vec3i> list = anchors == null ? new ArrayList<>() : new ArrayList<>(anchors);
        switch (section) {
            case RECT:
                return rectBounds(height, list);
            case CIRCLE:
                return circleBounds(height, list);
            default:
                return polygonBounds(height, list);
        }
    }

    private static Bounds rectBounds(Height height, List<Vec3i> list) {
        if (list.size() < 2) {
            return new Bounds(0, 0, 0, 0, 0, 0);
        }
        Vec3i a = list.get(0);
        Vec3i b = list.get(1);
        int minX = Math.min(a.x, b.x);
        int minZ = Math.min(a.z, b.z);
        int maxX = Math.max(a.x, b.x);
        int maxZ = Math.max(a.z, b.z);
        if (height == Height.FULL) {
            return new Bounds(minX, 0, minZ, maxX, FULL_MAX_Y, maxZ);
        }
        int minY = Math.min(a.y, b.y);
        int maxY = Math.max(a.y, b.y);
        return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static Bounds circleBounds(Height height, List<Vec3i> list) {
        if (list.size() < 2) {
            return new Bounds(0, 0, 0, 0, 0, 0);
        }
        Vec3i center = list.get(0);
        Vec3i surface = list.get(1);
        int r = circleRadius(center, surface);
        int minX = center.x - r;
        int maxX = center.x + r;
        int minZ = center.z - r;
        int maxZ = center.z + r;
        if (height == Height.FULL) {
            return new Bounds(minX, 0, minZ, maxX, FULL_MAX_Y, maxZ);
        }
        int minY = Math.min(center.y, surface.y);
        int maxY = Math.max(center.y, surface.y);
        return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static Bounds polygonBounds(Height height, List<Vec3i> list) {
        if (list.size() < 3) {
            return new Bounds(0, 0, 0, 0, 0, 0);
        }
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (Vec3i v : list) {
            minX = Math.min(minX, v.x);
            minZ = Math.min(minZ, v.z);
            maxX = Math.max(maxX, v.x);
            maxZ = Math.max(maxZ, v.z);
            minY = Math.min(minY, v.y);
            maxY = Math.max(maxY, v.y);
        }
        if (height == Height.FULL) {
            return new Bounds(minX, 0, minZ, maxX, FULL_MAX_Y, maxZ);
        }
        return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override
    public List<Edge> edges() {
        List<Edge> result = new ArrayList<>();
        if (height == Height.FULL) {
            // 通天柱：每 64 格渲染一个横向围栏 + 贯穿竖棱
            double top = bounds.maxY + 1.0D;
            for (int y = 0; y <= bounds.maxY + 1; y += 64) {
                horizontalOutline(result, y);
            }
            verticalLines(result, top);
            return result;
        }
        switch (section) {
            case RECT:
                boxEdges(result, bounds.minX, bounds.minY, bounds.minZ,
                        bounds.maxX + 1.0D, bounds.maxY + 1.0D, bounds.maxZ + 1.0D);
                break;
            case CIRCLE:
                circleEdges(result);
                break;
            default:
                polygonEdges(result);
                break;
        }
        return result;
    }

    /** 通天柱：在指定 Y 高度绘制该截面的横向轮廓（围栏）。 */
    private void horizontalOutline(List<Edge> result, int y) {
        switch (section) {
            case RECT:
                line(result, bounds.minX, y, bounds.minZ, bounds.maxX + 1.0D, y, bounds.minZ);
                line(result, bounds.maxX + 1.0D, y, bounds.minZ, bounds.maxX + 1.0D, y, bounds.maxZ + 1.0D);
                line(result, bounds.maxX + 1.0D, y, bounds.maxZ + 1.0D, bounds.minX, y, bounds.maxZ + 1.0D);
                line(result, bounds.minX, y, bounds.maxZ + 1.0D, bounds.minX, y, bounds.minZ);
                break;
            case CIRCLE: {
                double xc = cx + 0.5D;
                double zc = cz + 0.5D;
                for (int i = 0; i < RING_SEGMENTS; i++) {
                    double a1 = 2.0D * Math.PI * i / RING_SEGMENTS;
                    double a2 = 2.0D * Math.PI * (i + 1) / RING_SEGMENTS;
                    line(result, xc + radius * Math.cos(a1), y, zc + radius * Math.sin(a1),
                            xc + radius * Math.cos(a2), y, zc + radius * Math.sin(a2));
                }
                break;
            }
            default: {
                List<Vec3i> verts = polygonVertices();
                int n = verts.size();
                for (int i = 0; i < n; i++) {
                    Vec3i a = verts.get(i);
                    Vec3i b = verts.get((i + 1) % n);
                    line(result, a.x, y, a.z, b.x, y, b.z);
                }
                break;
            }
        }
    }

    /** 通天柱：贯穿全高的竖棱。 */
    private void verticalLines(List<Edge> result, double top) {
        double bottom = 0.0D;
        switch (section) {
            case RECT:
                line(result, bounds.minX, bottom, bounds.minZ, bounds.minX, top, bounds.minZ);
                line(result, bounds.maxX + 1.0D, bottom, bounds.minZ, bounds.maxX + 1.0D, top, bounds.minZ);
                line(result, bounds.maxX + 1.0D, bottom, bounds.maxZ + 1.0D, bounds.maxX + 1.0D, top, bounds.maxZ + 1.0D);
                line(result, bounds.minX, bottom, bounds.maxZ + 1.0D, bounds.minX, top, bounds.maxZ + 1.0D);
                break;
            case CIRCLE: {
                double xc = cx + 0.5D;
                double zc = cz + 0.5D;
                for (int i = 0; i < RING_SEGMENTS; i++) {
                    double a = 2.0D * Math.PI * i / RING_SEGMENTS;
                    double x = xc + radius * Math.cos(a);
                    double z = zc + radius * Math.sin(a);
                    line(result, x, bottom, z, x, top, z);
                }
                break;
            }
            default:
                for (Vec3i v : polygonVertices()) {
                    line(result, v.x, bottom, v.z, v.x, top, v.z);
                }
                break;
        }
    }

    private void boxEdges(List<Edge> result, double x1, double y1, double z1, double x2, double y2, double z2) {
        line(result, x1, y1, z1, x2, y1, z1);
        line(result, x2, y1, z1, x2, y1, z2);
        line(result, x2, y1, z2, x1, y1, z2);
        line(result, x1, y1, z2, x1, y1, z1);
        line(result, x1, y2, z1, x2, y2, z1);
        line(result, x2, y2, z1, x2, y2, z2);
        line(result, x2, y2, z2, x1, y2, z2);
        line(result, x1, y2, z2, x1, y2, z1);
        line(result, x1, y1, z1, x1, y2, z1);
        line(result, x2, y1, z1, x2, y2, z1);
        line(result, x2, y1, z2, x2, y2, z2);
        line(result, x1, y1, z2, x1, y2, z2);
    }

    private void circleEdges(List<Edge> result) {
        double xc = cx + 0.5D;
        double zc = cz + 0.5D;
        double y0 = bounds.minY;
        double y1 = bounds.maxY + 1.0D;
        double[] ringX = new double[RING_SEGMENTS + 1];
        double[] ringZ = new double[RING_SEGMENTS + 1];
        for (int i = 0; i <= RING_SEGMENTS; i++) {
            double angle = 2.0D * Math.PI * i / RING_SEGMENTS;
            ringX[i] = xc + radius * Math.cos(angle);
            ringZ[i] = zc + radius * Math.sin(angle);
        }
        for (int i = 0; i < RING_SEGMENTS; i++) {
            line(result, ringX[i], y0, ringZ[i], ringX[i + 1], y0, ringZ[i + 1]);
            line(result, ringX[i], y1, ringZ[i], ringX[i + 1], y1, ringZ[i + 1]);
            line(result, ringX[i], y0, ringZ[i], ringX[i], y1, ringZ[i]);
        }
    }

    private void polygonEdges(List<Edge> result) {
        List<Vec3i> verts = polygonVertices();
        if (verts.size() < 3) {
            return;
        }
        double y0 = bounds.minY;
        double y1 = bounds.maxY + 1.0D;
        int n = verts.size();
        for (int i = 0; i < n; i++) {
            Vec3i a = verts.get(i);
            Vec3i b = verts.get((i + 1) % n);
            line(result, a.x, y0, a.z, b.x, y0, b.z);
            line(result, a.x, y1, a.z, b.x, y1, b.z);
            line(result, a.x, y0, a.z, a.x, y1, a.z);
        }
    }

    private static void line(List<Edge> result, double x1, double y1, double z1, double x2, double y2, double z2) {
        result.add(new Edge(x1, y1, z1, x2, y2, z2));
    }

    @Override
    public String describe() {
        switch (section) {
            case RECT:
                if (height == Height.FULL) {
                    return "(" + bounds.minX + "," + bounds.minZ + ") ~ (" + bounds.maxX + "," + bounds.maxZ + ") 全高";
                }
                return "(" + bounds.minX + "," + bounds.minY + "," + bounds.minZ + ") ~ ("
                        + bounds.maxX + "," + bounds.maxY + "," + bounds.maxZ + ")";
            case CIRCLE:
                if (height == Height.FULL) {
                    return "圆心(" + cx + "," + cz + ") R=" + radius + " 全高";
                }
                return "圆心(" + cx + "," + cz + ") R=" + radius + " 高=" + (bounds.maxY - bounds.minY + 1);
            default:
                if (height == Height.FULL) {
                    return "多边形 顶点=" + polygonVertices().size() + " 全高";
                }
                return "多边形 顶点=" + polygonVertices().size() + " 高=" + (bounds.maxY - bounds.minY + 1);
        }
    }
}
