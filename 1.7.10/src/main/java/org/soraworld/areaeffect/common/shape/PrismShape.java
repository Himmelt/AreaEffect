package org.soraworld.areaeffect.common.shape;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
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
 *
 * <p>归一化语义（锚点为方块，创建时转成连续几何）：
 * <ul>
 *   <li>RECT：X/Z/Y 三轴都取<b>方块包络</b> {@code [min, max)}（max = 方块号 + 1）；</li>
 *   <li>CIRCLE：圆心 = 中心锚点方块中心 {@code (a0.x+0.5, a0.z+0.5)}，半径 = 圆心到半径点中心在 X-Z 的连续距离
 *       （不做 {@code (int)} 截断；半径点方块中心恰在圆周上，判定 `{@code <=}` ⇒ 判入，修掉旧的半径点出局）；</li>
 *   <li>POLYGON：顶点 = 各锚点方块中心，X-Z 判定走点在多边形内（含顶点区块中心，均在边界上判入）；</li>
 *   <li>BOUNDED 时 Y 取外包络 {@code [minY, maxY)}，FULL 时 Y 不参与。</li>
 * </ul>
 * 即"X-Z 中心 + Y 外包络"适用于圆柱/多边形柱，RECT 则三轴包络。判定点即玩家脚下碰撞箱底部中心
 * （{@code contains(x,y,z)}），直接喂连续几何。
 */
public class PrismShape extends AreaShape {

    public enum Section { RECT, CIRCLE, POLYGON }

    public enum Height { BOUNDED, FULL }

    public static final int RING_SEGMENTS = 24;

    private final Section section;
    private final boolean full;

    // 连续几何字段：RECT 用 minX..maxZ（包围体）；CIRCLE 用 cx/cz/r + Y 区间；POLYGON 用 xs/zs + Y 区间。
    private final double minX, maxX, minY, maxY, minZ, maxZ;
    private final double cx, cz, r;
    private final double[] xs, zs;

    /** 由方块锚点归一化构造（选区 create 入口）。锚点不足时取防御值，但不判 null（调用方已用 canBuild 把关）。 */
    public PrismShape(Section section, Height height, List<Vec3i> anchors, boolean closed) {
        super(computeBounds(section, height, anchors));
        this.section = section;
        this.full = height == Height.FULL;
        List<Vec3i> list = anchors == null ? new ArrayList<Vec3i>() : new ArrayList<>(anchors);
        this.minX = bounds.minX;
        this.maxX = bounds.maxX;
        this.minY = bounds.minY;
        this.maxY = bounds.maxY;
        this.minZ = bounds.minZ;
        this.maxZ = bounds.maxZ;
        this.cx = !list.isEmpty() ? list.get(0).x + 0.5D : 0.0D;
        this.cz = !list.isEmpty() ? list.get(0).z + 0.5D : 0.0D;
        this.r = list.size() > 1 ? Math.hypot((double) list.get(1).x - list.get(0).x,
                (double) list.get(1).z - list.get(0).z) : 0.0D;
        this.xs = toCentersX(list);
        this.zs = toCentersZ(list);
    }

    /** 由连续几何参数反序列化构造（NBT / Buf 入口）。 */
    private PrismShape(Section section, boolean full, Bounds bounds,
                       double cx, double cz, double r, double[] xs, double[] zs) {
        super(bounds);
        this.section = section;
        this.full = full;
        this.minX = bounds.minX;
        this.maxX = bounds.maxX;
        this.minY = bounds.minY;
        this.maxY = bounds.maxY;
        this.minZ = bounds.minZ;
        this.maxZ = bounds.maxZ;
        this.cx = cx;
        this.cz = cz;
        this.r = r;
        this.xs = xs;
        this.zs = zs;
    }

    private static double[] toCentersX(List<Vec3i> list) {
        double[] a = new double[list.size()];
        for (int i = 0; i < list.size(); i++) {
            a[i] = list.get(i).x + 0.5D;
        }
        return a;
    }

    private static double[] toCentersZ(List<Vec3i> list) {
        double[] a = new double[list.size()];
        for (int i = 0; i < list.size(); i++) {
            a[i] = list.get(i).z + 0.5D;
        }
        return a;
    }

    @Override
    public String typeId() {
        switch (section) {
            case RECT:
                return full ? ShapeTypes.TYPE_SQUARE_PILLAR : ShapeTypes.TYPE_BOX;
            case CIRCLE:
                return full ? ShapeTypes.TYPE_ROUND_PILLAR : ShapeTypes.TYPE_CYLINDER;
            default:
                return full ? ShapeTypes.TYPE_POLYGON_PILLAR : ShapeTypes.TYPE_POLYGON;
        }
    }

    @Override
    public boolean contains(double x, double y, double z) {
        switch (section) {
            case RECT:
                if (x < minX || x >= maxX || z < minZ || z >= maxZ) {
                    return false;
                }
                return full || (y >= minY && y < maxY);
            case CIRCLE: {
                double dx = x - cx;
                double dz = z - cz;
                if (dx * dx + dz * dz > r * r) {
                    return false;
                }
                return full || (y >= minY && y < maxY);
            }
            default:
                if (!full && (y < minY || y >= maxY)) {
                    return false;
                }
                return polygonContains(x, z, xs, zs);
        }
    }

    /** 点 (px,pz) 是否在顶点中心多边形内（含边界，顶点方块中心即顶点必入）。偶数-奇数射线法。 */
    private static boolean polygonContains(double px, double pz, double[] xs, double[] zs) {
        int n = xs.length;
        if (n < 3) {
            return false;
        }
        if (onAnyEdge(px, pz, xs, zs, 1.0e-9D)) {
            return true;
        }
        boolean inside = false;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = xs[i], zi = zs[i];
            double xj = xs[j], zj = zs[j];
            if ((zi > pz) != (zj > pz)) {
                double intersect = (xj - xi) * (pz - zi) / (zj - zi) + xi;
                if (px < intersect) {
                    inside = !inside;
                }
            }
        }
        return inside;
    }

    private static boolean onAnyEdge(double px, double pz, double[] xs, double[] zs, double eps) {
        int n = xs.length;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double ax = xs[i], az = zs[i];
            double bx = xs[j], bz = zs[j];
            double ex = bx - ax, ez = bz - az;
            double len2 = ex * ex + ez * ez;
            if (len2 < 1.0e-12D) {
                continue;
            }
            double t = ((px - ax) * ex + (pz - az) * ez) / len2;
            if (t < 0.0D) {
                t = 0.0D;
            } else if (t > 1.0D) {
                t = 1.0D;
            }
            double ddx = px - (ax + ex * t);
            double ddz = pz - (az + ez * t);
            if (ddx * ddx + ddz * ddz <= eps * eps) {
                return true;
            }
        }
        return false;
    }

    /** 由锚点求包围体（连续半开范围）。CIRCLE/POLYGON 的 X-Z 含边界且取半径/顶点 ±1 的保守超集（粗筛用）。 */
    private static Bounds computeBounds(Section section, Height height, List<Vec3i> anchors) {
        List<Vec3i> list = anchors == null ? new ArrayList<Vec3i>() : new ArrayList<>(anchors);
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
            return new Bounds(0, 0, 0, 1, 1, 1);
        }
        Vec3i a = list.get(0);
        Vec3i b = list.get(1);
        double minX = Math.min(a.x, b.x);
        double maxX = Math.max(a.x, b.x) + 1.0D;
        double minZ = Math.min(a.z, b.z);
        double maxZ = Math.max(a.z, b.z) + 1.0D;
        if (height == Height.FULL) {
            return new Bounds(minX, 0, minZ, maxX, FULL_MAX_Y + 1.0D, maxZ);
        }
        double minY = Math.min(a.y, b.y);
        double maxY = Math.max(a.y, b.y) + 1.0D;
        return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static Bounds circleBounds(Height height, List<Vec3i> list) {
        if (list.size() < 2) {
            return new Bounds(0, 0, 0, 1, 1, 1);
        }
        Vec3i a0 = list.get(0);
        Vec3i a1 = list.get(1);
        double cx = a0.x + 0.5D;
        double cz = a0.z + 0.5D;
        double r = Math.hypot((double) a1.x - a0.x, (double) a1.z - a0.z);
        if (height == Height.FULL) {
            return new Bounds(cx - r - 1.0D, 0, cz - r - 1.0D, cx + r + 1.0D, FULL_MAX_Y + 1.0D, cz + r + 1.0D);
        }
        double minY = Math.min(a0.y, a1.y);
        double maxY = Math.max(a0.y, a1.y) + 1.0D;
        return new Bounds(cx - r - 1.0D, minY, cz - r - 1.0D, cx + r + 1.0D, maxY, cz + r + 1.0D);
    }

    private static Bounds polygonBounds(Height height, List<Vec3i> list) {
        if (list.size() < 3) {
            return new Bounds(0, 0, 0, 1, 1, 1);
        }
        double lox = Double.MAX_VALUE, hix = -Double.MAX_VALUE;
        double loz = Double.MAX_VALUE, hiz = -Double.MAX_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (Vec3i v : list) {
            double vx = v.x + 0.5D;
            double vz = v.z + 0.5D;
            lox = Math.min(lox, vx);
            hix = Math.max(hix, vx);
            loz = Math.min(loz, vz);
            hiz = Math.max(hiz, vz);
            minY = Math.min(minY, v.y);
            maxY = Math.max(maxY, v.y);
        }
        if (height == Height.FULL) {
            return new Bounds(lox - 1.0D, 0, loz - 1.0D, hix + 1.0D, FULL_MAX_Y + 1.0D, hiz + 1.0D);
        }
        return new Bounds(lox - 1.0D, minY, loz - 1.0D, hix + 1.0D, maxY + 1.0D, hiz + 1.0D);
    }

    @Override
    protected List<Edge> computeEdges() {
        List<Edge> result = new ArrayList<>();
        if (full) {
            double top = FULL_MAX_Y + 1.0D;
            for (int y = 0; y <= FULL_MAX_Y + 1; y += 64) {
                horizontalOutline(result, y);
            }
            verticalLines(result, 0.0D, top);
            return result;
        }
        switch (section) {
            case RECT:
                boxEdges(result, minX, minY, minZ, maxX, maxY, maxZ);
                break;
            case CIRCLE:
                circleEdges(result, cx, cz, r, minY, maxY);
                break;
            default:
                polygonEdges(result, xs, zs, minY, maxY);
                break;
        }
        return result;
    }

    private void horizontalOutline(List<Edge> result, int y) {
        switch (section) {
            case RECT:
                outlineRect(result, minX, maxX, minZ, maxZ, y);
                break;
            case CIRCLE:
                ring(result, cx, cz, r, y);
                break;
            default:
                outlinePolygon(result, xs, zs, y);
                break;
        }
    }

    private static void outlineRect(List<Edge> result, double minX, double maxX, double minZ, double maxZ, double y) {
        line(result, minX, y, minZ, maxX, y, minZ);
        line(result, maxX, y, minZ, maxX, y, maxZ);
        line(result, maxX, y, maxZ, minX, y, maxZ);
        line(result, minX, y, maxZ, minX, y, minZ);
    }

    private static void outlinePolygon(List<Edge> result, double[] xs, double[] zs, double y) {
        int n = xs.length;
        for (int i = 0; i < n; i++) {
            int nx = (i + 1) % n;
            line(result, xs[i], y, zs[i], xs[nx], y, zs[nx]);
        }
    }

    private static void ring(List<Edge> result, double cx, double cz, double r, double y) {
        for (int i = 0; i < RING_SEGMENTS; i++) {
            double a1 = 2.0D * Math.PI * i / RING_SEGMENTS;
            double a2 = 2.0D * Math.PI * (i + 1) / RING_SEGMENTS;
            line(result, cx + r * Math.cos(a1), y, cz + r * Math.sin(a1),
                    cx + r * Math.cos(a2), y, cz + r * Math.sin(a2));
        }
    }

    private void verticalLines(List<Edge> result, double bottom, double top) {
        switch (section) {
            case RECT:
                line(result, minX, bottom, minZ, minX, top, minZ);
                line(result, maxX, bottom, minZ, maxX, top, minZ);
                line(result, maxX, bottom, maxZ, maxX, top, maxZ);
                line(result, minX, bottom, maxZ, minX, top, maxZ);
                break;
            case CIRCLE:
                for (int i = 0; i < RING_SEGMENTS; i++) {
                    double a = 2.0D * Math.PI * i / RING_SEGMENTS;
                    double x = cx + r * Math.cos(a);
                    double z = cz + r * Math.sin(a);
                    line(result, x, bottom, z, x, top, z);
                }
                break;
            default:
                for (int i = 0; i < xs.length; i++) {
                    line(result, xs[i], bottom, zs[i], xs[i], top, zs[i]);
                }
                break;
        }
    }

    private static void boxEdges(List<Edge> result, double x1, double y1, double z1, double x2, double y2, double z2) {
        outlineRect(result, x1, x2, z1, z2, y1);
        outlineRect(result, x1, x2, z1, z2, y2);
        line(result, x1, y1, z1, x1, y2, z1);
        line(result, x2, y1, z1, x2, y2, z1);
        line(result, x2, y1, z2, x2, y2, z2);
        line(result, x1, y1, z2, x1, y2, z2);
    }

    private static void circleEdges(List<Edge> result, double cx, double cz, double r, double y0, double y1) {
        ring(result, cx, cz, r, y0);
        ring(result, cx, cz, r, y1);
        for (int i = 0; i < RING_SEGMENTS; i++) {
            double a = 2.0D * Math.PI * i / RING_SEGMENTS;
            line(result, cx + r * Math.cos(a), y0, cz + r * Math.sin(a),
                    cx + r * Math.cos(a), y1, cz + r * Math.sin(a));
        }
    }

    private static void polygonEdges(List<Edge> result, double[] xs, double[] zs, double y0, double y1) {
        outlinePolygon(result, xs, zs, y0);
        outlinePolygon(result, xs, zs, y1);
        for (int i = 0; i < xs.length; i++) {
            line(result, xs[i], y0, zs[i], xs[i], y1, zs[i]);
        }
    }

    private static void line(List<Edge> result, double x1, double y1, double z1, double x2, double y2, double z2) {
        result.add(new Edge(x1, y1, z1, x2, y2, z2));
    }

    // ===================== 序列化 =====================

    @Override
    public void writeToNbt(NBTTagCompound tag) {
        switch (section) {
            case RECT:
                tag.setDouble("minX", minX);
                tag.setDouble("maxX", maxX);
                tag.setDouble("minY", minY);
                tag.setDouble("maxY", maxY);
                tag.setDouble("minZ", minZ);
                tag.setDouble("maxZ", maxZ);
                break;
            case CIRCLE:
                tag.setDouble("cx", cx);
                tag.setDouble("cz", cz);
                tag.setDouble("r", r);
                if (!full) {
                    tag.setDouble("minY", minY);
                    tag.setDouble("maxY", maxY);
                }
                break;
            default:
                writePolygonNbt(tag, xs, zs);
                if (!full) {
                    tag.setDouble("minY", minY);
                    tag.setDouble("maxY", maxY);
                }
                break;
        }
    }

    private static void writePolygonNbt(NBTTagCompound tag, double[] xs, double[] zs) {
        NBTTagList verts = new NBTTagList();
        int count = Math.min(xs.length, Selection.MAX_ANCHORS);
        for (int i = 0; i < count; i++) {
            NBTTagCompound c = new NBTTagCompound();
            c.setDouble("x", xs[i]);
            c.setDouble("z", zs[i]);
            verts.appendTag(c);
        }
        tag.setTag("verts", verts);
    }

    @Override
    public void writeToBuf(ByteBuf buf) {
        switch (section) {
            case RECT:
                buf.writeDouble(minX);
                buf.writeDouble(maxX);
                buf.writeDouble(minY);
                buf.writeDouble(maxY);
                buf.writeDouble(minZ);
                buf.writeDouble(maxZ);
                break;
            case CIRCLE:
                buf.writeDouble(cx);
                buf.writeDouble(cz);
                buf.writeDouble(r);
                if (!full) {
                    buf.writeDouble(minY);
                    buf.writeDouble(maxY);
                }
                break;
            default:
                writePolygonBuf(buf, xs, zs);
                if (!full) {
                    buf.writeDouble(minY);
                    buf.writeDouble(maxY);
                }
                break;
        }
    }

    private static void writePolygonBuf(ByteBuf buf, double[] xs, double[] zs) {
        int count = Math.min(xs.length, Selection.MAX_ANCHORS);
        buf.writeInt(count);
        for (int i = 0; i < count; i++) {
            buf.writeDouble(xs[i]);
            buf.writeDouble(zs[i]);
        }
    }

    /** NBT 反序列化（type 键由 {@link ShapeTypes} 分派）。 */
    public static PrismShape fromNbt(String type, NBTTagCompound tag) {
        Section section = sectionOf(type);
        boolean full = fullOf(type);
        if (section == null) {
            return null;
        }
        Bounds b;
        if (section == Section.RECT) {
            b = new Bounds(tag.getDouble("minX"), tag.getDouble("minY"), tag.getDouble("minZ"),
                    tag.getDouble("maxX"), tag.getDouble("maxY"), tag.getDouble("maxZ"));
            return new PrismShape(section, full, b, 0, 0, 0, new double[0], new double[0]);
        }
        double cx = tag.getDouble("cx");
        double cz = tag.getDouble("cz");
        double r = tag.getDouble("r");
        double minY = full ? 0 : tag.getDouble("minY");
        double maxY = full ? FULL_MAX_Y + 1.0D : tag.getDouble("maxY");
        double[] xs;
        double[] zs;
        if (section == Section.CIRCLE) {
            xs = new double[0];
            zs = new double[0];
        } else {
            double[][] v = readPolygonNbt(tag);
            xs = v[0];
            zs = v[1];
            if (xs.length < 3 || xs.length != zs.length) {
                return null;
            }
        }
        b = boundsFromGeometry(section, full, cx, cz, r, xs, zs, minY, maxY);
        return new PrismShape(section, full, b, cx, cz, r, xs, zs);
    }

    private static double[][] readPolygonNbt(NBTTagCompound tag) {
        NBTTagList list = tag.getTagList("verts", 10);
        int count = Math.min(list.tagCount(), Selection.MAX_ANCHORS);
        double[] xs = new double[count];
        double[] zs = new double[count];
        for (int i = 0; i < count; i++) {
            NBTTagCompound c = list.getCompoundTagAt(i);
            xs[i] = c.getDouble("x");
            zs[i] = c.getDouble("z");
        }
        return new double[][]{xs, zs};
    }

    /** Buf 反序列化（type 键由 {@link ShapeTypes} 分派）。 */
    public static PrismShape fromBuf(String type, ByteBuf buf) {
        Section section = sectionOf(type);
        boolean full = fullOf(type);
        if (section == null) {
            return null;
        }
        Bounds b;
        if (section == Section.RECT) {
            b = new Bounds(buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble());
            return new PrismShape(section, full, b, 0, 0, 0, new double[0], new double[0]);
        }
        double cx = buf.readDouble();
        double cz = buf.readDouble();
        double r = buf.readDouble();
        double minY = 0;
        double maxY = FULL_MAX_Y + 1.0D;
        if (!full) {
            minY = buf.readDouble();
            maxY = buf.readDouble();
        }
        double[] xs;
        double[] zs;
        if (section == Section.CIRCLE) {
            xs = new double[0];
            zs = new double[0];
        } else {
            xs = readDoublesBuf(buf);
            zs = readDoublesBuf(buf);
            if (xs.length < 3 || xs.length != zs.length) {
                return null;
            }
        }
        b = boundsFromGeometry(section, full, cx, cz, r, xs, zs, minY, maxY);
        return new PrismShape(section, full, b, cx, cz, r, xs, zs);
    }

    private static double[] readDoublesBuf(ByteBuf buf) {
        int count = Math.min(Math.max(buf.readInt(), 0), Selection.MAX_ANCHORS);
        double[] a = new double[count];
        for (int i = 0; i < count; i++) {
            a[i] = buf.readDouble();
        }
        return a;
    }

    private static Bounds boundsFromGeometry(Section section, boolean full, double cx, double cz, double r,
                                             double[] xs, double[] zs, double minY, double maxY) {
        switch (section) {
            case RECT:
                throw new IllegalStateException("RECT bounds must come directly from stored 6-coords");
            case CIRCLE:
                if (full) {
                    return new Bounds(cx - r - 1, 0, cz - r - 1, cx + r + 1, FULL_MAX_Y + 1.0D, cz + r + 1);
                }
                return new Bounds(cx - r - 1, minY, cz - r - 1, cx + r + 1, maxY, cz + r + 1);
            default: {
                double lox = Double.MAX_VALUE, hix = -Double.MAX_VALUE;
                double loz = Double.MAX_VALUE, hiz = -Double.MAX_VALUE;
                for (int i = 0; i < xs.length; i++) {
                    lox = Math.min(lox, xs[i]);
                    hix = Math.max(hix, xs[i]);
                    loz = Math.min(loz, zs[i]);
                    hiz = Math.max(hiz, zs[i]);
                }
                if (full) {
                    return new Bounds(lox - 1, 0, loz - 1, hix + 1, FULL_MAX_Y + 1.0D, hiz + 1);
                }
                return new Bounds(lox - 1, minY, loz - 1, hix + 1, maxY, hiz + 1);
            }
        }
    }

    private static Section sectionOf(String type) {
        if (ShapeTypes.TYPE_BOX.equals(type) || ShapeTypes.TYPE_SQUARE_PILLAR.equals(type)) {
            return Section.RECT;
        }
        if (ShapeTypes.TYPE_CYLINDER.equals(type) || ShapeTypes.TYPE_ROUND_PILLAR.equals(type)) {
            return Section.CIRCLE;
        }
        if (ShapeTypes.TYPE_POLYGON.equals(type) || ShapeTypes.TYPE_POLYGON_PILLAR.equals(type)) {
            return Section.POLYGON;
        }
        return null;
    }

    private static boolean fullOf(String type) {
        return ShapeTypes.TYPE_SQUARE_PILLAR.equals(type)
                || ShapeTypes.TYPE_ROUND_PILLAR.equals(type)
                || ShapeTypes.TYPE_POLYGON_PILLAR.equals(type);
    }
}