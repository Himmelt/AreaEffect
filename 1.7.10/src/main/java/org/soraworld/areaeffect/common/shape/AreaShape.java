package org.soraworld.areaeffect.common.shape;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.soraworld.areaeffect.common.util.Vec3d;
import org.soraworld.areaeffect.common.util.Vec3i;

import java.util.ArrayList;
import java.util.List;

/**
 * 区域形状抽象基类。所有选区形状（柱状体族 / 球体）共用锚点列表语义：
 * 形状由构造时传入的锚点决定，渲染用 {@link #edges()}，判定用 {@link #contains}。
 */
public abstract class AreaShape {

    /** 闭区间方块坐标包围盒。通天柱 FULL 高度下 minY=0、maxY=255。 */
    public static final class Bounds {
        public final int minX, minY, minZ, maxX, maxY, maxZ;

        public Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }
    }

    /** 渲染线段（世界绝对坐标，线框外沿已 +1）。 */
    public static final class Edge {
        public final double x1, y1, z1, x2, y2, z2;

        public Edge(double x1, double y1, double z1, double x2, double y2, double z2) {
            this.x1 = x1;
            this.y1 = y1;
            this.z1 = z1;
            this.x2 = x2;
            this.y2 = y2;
            this.z2 = z2;
        }
    }

    /** 柱状体全高模式下的世界高度上界（闭区间）。 */
    public static final int FULL_MAX_Y = 255;

    protected final List<Vec3i> anchors;
    protected final boolean closed;
    protected final Bounds bounds;

    protected AreaShape(List<Vec3i> anchors, boolean closed, Bounds bounds) {
        this.anchors = anchors == null ? new ArrayList<>() : new ArrayList<>(anchors);
        this.closed = closed;
        this.bounds = bounds;
    }

    public abstract String typeId();

    public abstract boolean contains(double x, double y, double z);

    public abstract List<Edge> edges();

    /** 详情页展示文本。 */
    public abstract String describe();

    public List<Vec3i> anchors() {
        return anchors;
    }

    public boolean closed() {
        return closed;
    }

    public Bounds bounds() {
        return bounds;
    }

    /**
     * v1 冲突检测：AABB 包围盒相交粗判（保守）。
     */
    public boolean conflict(AreaShape other) {
        return bounds.minX <= other.bounds.maxX && bounds.maxX >= other.bounds.minX
                && bounds.minY <= other.bounds.maxY && bounds.maxY >= other.bounds.minY
                && bounds.minZ <= other.bounds.maxZ && bounds.maxZ >= other.bounds.minZ;
    }

    public Vec3d center() {
        return new Vec3d((bounds.minX + bounds.maxX + 1.0D) / 2.0D,
                (bounds.minY + bounds.maxY + 1.0D) / 2.0D,
                (bounds.minZ + bounds.maxZ + 1.0D) / 2.0D);
    }

    /**
     * 序列化到 NBT：写 "anchors" 列表 + "closed"（type 键由 {@link ShapeTypes} 写入）。
     */
    public void writeToNbt(NBTTagCompound tag) {
        NBTTagList list = new NBTTagList();
        for (Vec3i anchor : anchors) {
            NBTTagCompound anchorTag = new NBTTagCompound();
            anchorTag.setInteger("x", anchor.x);
            anchorTag.setInteger("y", anchor.y);
            anchorTag.setInteger("z", anchor.z);
            list.appendTag(anchorTag);
        }
        tag.setTag("anchors", list);
        tag.setBoolean("closed", closed);
    }

    /**
     * 序列化到网络缓冲：写锚点数 + 各锚点坐标 + closed（type 键由 {@link ShapeTypes} 写入）。
     */
    public void writeToBuf(ByteBuf buf) {
        buf.writeInt(anchors.size());
        for (Vec3i anchor : anchors) {
            buf.writeInt(anchor.x);
            buf.writeInt(anchor.y);
            buf.writeInt(anchor.z);
        }
        buf.writeBoolean(closed);
    }

    /**
     * 从 NBT 读回锚点列表（type 键由调用方 {@link ShapeTypes} 分派）。
     */
    protected static List<Vec3i> readAnchorsNbt(NBTTagCompound tag) {
        List<Vec3i> anchors = new ArrayList<>();
        NBTTagList list = tag.getTagList("anchors", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound anchorTag = list.getCompoundTagAt(i);
            anchors.add(new Vec3i(anchorTag.getInteger("x"), anchorTag.getInteger("y"), anchorTag.getInteger("z")));
        }
        return anchors;
    }

    /**
     * 从网络缓冲读回锚点列表（type 键由调用方 {@link ShapeTypes} 分派）。锚点数量上限 64 防恶意包。
     */
    protected static List<Vec3i> readAnchorsBuf(ByteBuf buf) {
        int size = Math.min(Math.max(buf.readInt(), 0), 64);
        List<Vec3i> anchors = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            anchors.add(new Vec3i(buf.readInt(), buf.readInt(), buf.readInt()));
        }
        return anchors;
    }
}
