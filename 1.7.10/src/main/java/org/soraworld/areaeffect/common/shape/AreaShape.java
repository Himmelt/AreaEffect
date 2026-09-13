package org.soraworld.areaeffect.common.shape;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.soraworld.areaeffect.common.util.Vec3d;
import org.soraworld.areaeffect.common.util.Vec3i;

import java.util.ArrayList;
import java.util.Collections;
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

    /**
     * {@link #edges()} 的惰性缓存。形状实例一经构造即不可变，故线段只算一次；
     * 线框在渲染线程按帧读取，缓存字段用 volatile 保证发布可见性（重算幂等，无需加锁）。
     */
    private volatile List<Edge> cachedEdges = null;

    protected AreaShape(List<Vec3i> anchors, boolean closed, Bounds bounds) {
        this.anchors = anchors == null ? new ArrayList<>() : new ArrayList<>(anchors);
        this.closed = closed;
        this.bounds = bounds;
    }

    public abstract String typeId();

    public abstract boolean contains(double x, double y, double z);

    /**
     * AABB 包围盒级包含粗判（保守、闭区间）：坐标落在形状的轴对齐包围盒内才可能真正命中。
     * 用于"玩家所在区域"查询时<b>先粗筛快速排除远距离区域</b>，命中的再走精确 {@link #contains}，
     * 避免每次查询都对全部区域做昂贵的点在多边形判定。
     */
    public boolean boundsContains(double x, double y, double z) {
        return x >= bounds.minX && x <= bounds.maxX
                && y >= bounds.minY && y <= bounds.maxY
                && z >= bounds.minZ && z <= bounds.maxZ;
    }

    /**
     * 渲染线段（世界绝对坐标，线框外沿已 +1）。
     *
     * <p>形状不可变，因此结果只计算一次并缓存：线框是<b>每帧</b>绘制的，而一次
     * {@link #computeEdges()} 会新建几百个 {@link Edge}（球体约 269 条、64 顶点多边形柱
     * 250+ 条），逐帧重建纯属给 GC 添负担。调用方拿到的列表不可修改。
     */
    public final List<Edge> edges() {
        List<Edge> cached = cachedEdges;
        if (cached == null) {
            cached = Collections.unmodifiableList(new ArrayList<>(computeEdges()));
            cachedEdges = cached;
        }
        return cached;
    }

    /** 各形状实际的线段构造；由 {@link #edges()} 缓存，不要直接调用。 */
    protected abstract List<Edge> computeEdges();

    /**
     * 详情页展示文案的本地化键（形如 {@code gui.areaeffect.desc.<typeId>}）。
     * 具体文案由客户端经 StatCollector 组装，形状层不硬编码界面语言。
     */
    public abstract String describeKey();

    /** {@link #describeKey()} 对应的参数，顺序与语言文件中的占位符一致。 */
    public abstract Object[] describeArgs();

    /**
     * 冲突几何判定（conflict/boundsOverlap/exactConflict/采样预算/Section/existsShared）随
     * "放开区域重叠"一并移除：区域允许任意重叠后，形状间冲突检测不再有调用方。
     * 玩家所在区域查询已改用 {@link #boundsContains} 粗筛 + {@link #contains} 精确判定。
     */

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
     *
     * <p>写出的条数<b>必须</b>与 {@link #readAnchorsBuf} 能读回的条数同源（同样受
     * {@link Selection#MAX_ANCHORS} 约束）：否则同一元素在收发两端的字节长度不一致，
     * 包内后续元素会整体错位 —— 那正是上一轮 P0-3 修掉的失败模式。
     */
    public void writeToBuf(ByteBuf buf) {
        int size = Math.min(anchors.size(), Selection.MAX_ANCHORS);
        buf.writeInt(size);
        for (int i = 0; i < size; i++) {
            Vec3i anchor = anchors.get(i);
            buf.writeInt(anchor.x);
            buf.writeInt(anchor.y);
            buf.writeInt(anchor.z);
        }
        buf.writeBoolean(closed);
    }

    /**
     * 从 NBT 读回锚点列表（type 键由调用方 {@link ShapeTypes} 分派）。
     * 与 buf 路径同源地收窄到 {@link Selection#MAX_ANCHORS}：正常选区不可能超过该值，
     * 超限只可能来自被手工编辑或异版本的存档，截断好过让它在网络上被静默截断。
     */
    protected static List<Vec3i> readAnchorsNbt(NBTTagCompound tag) {
        List<Vec3i> anchors = new ArrayList<>();
        NBTTagList list = tag.getTagList("anchors", 10);
        int count = Math.min(list.tagCount(), Selection.MAX_ANCHORS);
        for (int i = 0; i < count; i++) {
            NBTTagCompound anchorTag = list.getCompoundTagAt(i);
            anchors.add(new Vec3i(anchorTag.getInteger("x"), anchorTag.getInteger("y"), anchorTag.getInteger("z")));
        }
        return anchors;
    }

    /**
     * 从网络缓冲读回锚点列表（type 键由调用方 {@link ShapeTypes} 分派）。
     * 锚点数量上限与选区一致（{@link Selection#MAX_ANCHORS}），防恶意包撑爆包体与内存。
     * 注意：无论能否构成有效形状，本方法都会读完声明的锚点，保持元素边界对齐。
     */
    protected static List<Vec3i> readAnchorsBuf(ByteBuf buf) {
        int size = Math.min(Math.max(buf.readInt(), 0), Selection.MAX_ANCHORS);
        List<Vec3i> anchors = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            anchors.add(new Vec3i(buf.readInt(), buf.readInt(), buf.readInt()));
        }
        return anchors;
    }
}
