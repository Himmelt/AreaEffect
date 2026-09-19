package org.soraworld.areaeffect.common.shape;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import org.soraworld.areaeffect.common.util.Vec3i;

import java.util.ArrayList;
import java.util.List;

/**
 * 球体形状：X/Y/Z 三轴都取方块中心。球心 = 中心锚点方块中心，半径 = 球心到球面点方块中心的连续三维距离
 * （不做 {@code (int)} 截断；球面点方块中心恰在球面上，判定 `{@code <=}` ⇒ 判入）。
 * 判定点即玩家脚下碰撞箱底部中心，直接喂三维连续几何。
 */
public class SphereShape extends AreaShape {

    public static final int RING_SEGMENTS = 24;

    private final double cx, cy, cz;
    private final double r;

    /** 由方块锚点归一化构造（选区 create 入口）。 */
    public SphereShape(Vec3i center, Vec3i surface) {
        super(new Bounds(Math.min(center.x, surface.x) - 1.0D, Math.min(center.y, surface.y) - 1.0D,
                Math.min(center.z, surface.z) - 1.0D, Math.max(center.x, surface.x) + 1.0D,
                Math.max(center.y, surface.y) + 1.0D, Math.max(center.z, surface.z) + 1.0D));
        this.cx = center.x + 0.5D;
        this.cy = center.y + 0.5D;
        this.cz = center.z + 0.5D;
        this.r = Math.sqrt(sq(surface.x - center.x) + sq(surface.y - center.y) + sq(surface.z - center.z));
    }

    /** 由连续几何参数反序列化构造（NBT / Buf 入口）。 */
    private SphereShape(Bounds bounds, double cx, double cy, double cz, double r) {
        super(bounds);
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.r = r;
    }

    private static double sq(double v) {
        return v * v;
    }

    @Override
    public String typeId() {
        return ShapeTypes.TYPE_SPHERE;
    }

    @Override
    public boolean contains(double x, double y, double z) {
        double dx = x - cx;
        double dy = y - cy;
        double dz = z - cz;
        return dx * dx + dy * dy + dz * dz <= r * r;
    }

    @Override
    protected List<Edge> computeEdges() {
        List<Edge> result = new ArrayList<>();
        ring(result, cx, cy, cz, r);
        double halfSqrt3 = Math.sqrt(3.0D) / 2.0D;
        for (double sinLat : new double[]{0.5D, -0.5D, halfSqrt3, -halfSqrt3}) {
            double h = r * sinLat;
            double rr = Math.sqrt(r * r - h * h);
            ring(result, cx, cy + h, cz, rr);
        }
        for (int m = 0; m < 6; m++) {
            double theta = Math.PI * m / 6.0D;
            double cosT = Math.cos(theta);
            double sinT = Math.sin(theta);
            double prevX = 0.0D;
            double prevY = 0.0D;
            double prevZ = 0.0D;
            for (int i = 0; i <= RING_SEGMENTS; i++) {
                double angle = 2.0D * Math.PI * i / RING_SEGMENTS;
                double ca = Math.cos(angle) * r;
                double sa = Math.sin(angle) * r;
                double x = cx + ca * cosT;
                double z = cz + ca * sinT;
                double y = cy + sa;
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

    private static void ring(List<Edge> result, double cx, double cy, double cz, double r) {
        double prevX = 0.0D;
        double prevZ = 0.0D;
        for (int i = 0; i <= RING_SEGMENTS; i++) {
            double angle = 2.0D * Math.PI * i / RING_SEGMENTS;
            double x = cx + r * Math.cos(angle);
            double z = cz + r * Math.sin(angle);
            if (i > 0) {
                line(result, prevX, cy, prevZ, x, cy, z);
            }
            prevX = x;
            prevZ = z;
        }
    }

    private static void line(List<Edge> result, double x1, double y1, double z1, double x2, double y2, double z2) {
        result.add(new Edge(x1, y1, z1, x2, y2, z2));
    }

    // ===================== 序列化 =====================

    @Override
    public void writeToNbt(NBTTagCompound tag) {
        tag.setDouble("cx", cx);
        tag.setDouble("cy", cy);
        tag.setDouble("cz", cz);
        tag.setDouble("r", r);
    }

    @Override
    public void writeToBuf(ByteBuf buf) {
        buf.writeDouble(cx);
        buf.writeDouble(cy);
        buf.writeDouble(cz);
        buf.writeDouble(r);
    }

    /** NBT 反序列化（type 键由 {@link ShapeTypes} 分派）。 */
    public static SphereShape fromNbt(NBTTagCompound tag) {
        return from(bb(tag), tag.getDouble("cx"), tag.getDouble("cy"), tag.getDouble("cz"), tag.getDouble("r"));
    }

    /** Buf 反序列化（type 键由 {@link ShapeTypes} 分派）。 */
    public static SphereShape fromBuf(ByteBuf buf) {
        double cx = buf.readDouble();
        double cy = buf.readDouble();
        double cz = buf.readDouble();
        double r = buf.readDouble();
        return from(new Bounds(cx - r - 1, cy - r - 1, cz - r - 1, cx + r + 1, cy + r + 1, cz + r + 1),
                cx, cy, cz, r);
    }

    private static Bounds bb(NBTTagCompound tag) {
        double cx = tag.getDouble("cx");
        double cy = tag.getDouble("cy");
        double cz = tag.getDouble("cz");
        double r = tag.getDouble("r");
        return new Bounds(cx - r - 1, cy - r - 1, cz - r - 1, cx + r + 1, cy + r + 1, cz + r + 1);
    }

    private static SphereShape from(Bounds b, double cx, double cy, double cz, double r) {
        if (r < 0) {
            return null;
        }
        return new SphereShape(b, cx, cy, cz, r);
    }
}