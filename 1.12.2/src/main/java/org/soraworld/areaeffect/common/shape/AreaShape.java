package org.soraworld.areaeffect.common.shape;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import org.soraworld.areaeffect.common.util.Vec3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 区域形状抽象基类。
 *
 * <p>几何模型：形状以 <b>double 连续坐标</b>存储，判定与线框都直接作用于连续空间，
 * 不再依赖"方块号 → floor → 方块代表点"的离散化。玩家判定点取脚下碰撞箱底部中心
 * （{@code Vec3d(Entity)}），三个轴的归属规则如下：
 * <ul>
 *   <li>矩形族（box / square_pillar）：三轴一律取<b>完整包围体</b>，范围 {@code [min, max)}（max 已有 +1 包络）；</li>
 *   <li>圆柱 / 多边形柱族：X-Z 取<b>方块中心点</b>坐标即圆心/顶点，Y 取<b>外包络</b> {@code [minY, maxY)}；</li>
 *   <li>球：X/Y/Z 全部取方块中心（球心 + 三维半径）。</li>
 * </ul>
 *
 * <p>因此连续几何是唯一真源：判定、线框、网络/NBT 序列化全部写到同一份 double 参数上，
 * 外力（如手工改 NBT）可以把它改成任意实数，判定与线框都会随之一致地反映。
 * 方块锚点（{@code Vec3i}）只在选区与 {@code create} 归一化时出现，不进入本层。
 */
public abstract class AreaShape {

    /** 连续坐标的半开包围体 {@code [min, max)}。max 已含"包围体外沿 +1"或"半径外沿"。 */
    public static final class Bounds {
        public final double minX, minY, minZ, maxX, maxY, maxZ;

        public Bounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }
    }

    /** 渲染线段（世界绝对坐标，即判定边界的连续曲线本身）。 */
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

    /** 世界建筑高度上界（方块号闭区间最大 255，连续范围为 [0,256)）。 */
    public static final int FULL_MAX_Y = 255;

    protected final Bounds bounds;

    /**
     * {@link #edges()} 的惰性缓存。形状实例一经构造即不可变，故线段只算一次；
     * 线框在渲染线程按帧读取，缓存字段用 volatile 保证发布可见性（重算幂等，无需加锁）。
     */
    private volatile List<Edge> cachedEdges = null;

    protected AreaShape(Bounds bounds) {
        this.bounds = bounds;
    }

    public abstract String typeId();

    /** 点是否在区域内。判定点即玩家脚下碰撞箱底部中心，走连续几何。 */
    public abstract boolean contains(double x, double y, double z);

    /**
     * AABB 包围盒级包含粗判（保守）：坐标落在形状的轴对齐包围体内才可能真正命中。
     * 用于"玩家所在区域"查询时<b>先粗筛快速排除远距离区域</b>，命中的再走精确 {@link #contains}。
     * <b>下界闭、上界开</b>：与 {@code contains} 的连续语义对齐，保证粗筛是精确判定的超集。
     */
    public boolean boundsContains(double x, double y, double z) {
        return x >= bounds.minX && x < bounds.maxX
                && y >= bounds.minY && y < bounds.maxY
                && z >= bounds.minZ && z < bounds.maxZ;
    }

    /**
     * 渲染线段（世界绝对坐标，即判定边界曲线本身）。
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

    /** 区域中心（包围体中心），用于传送。 */
    public Vec3d center() {
        return new Vec3d((bounds.minX + bounds.maxX) / 2.0D,
                (bounds.minY + bounds.maxY) / 2.0D,
                (bounds.minZ + bounds.maxZ) / 2.0D);
    }

    /** 序列化到 NBT：写入本形状的连续几何参数（type 键由 {@link ShapeTypes} 写入）。 */
    public abstract void writeToNbt(NBTTagCompound tag);

    /** 序列化到网络缓冲：写入本形状的连续几何参数（type 键由 {@link ShapeTypes#writeBuf} 写入）。 */
    public abstract void writeToBuf(ByteBuf buf);
}