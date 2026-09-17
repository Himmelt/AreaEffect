package org.soraworld.areaeffect.common.shape;

import org.soraworld.areaeffect.common.util.Vec3i;

import java.util.ArrayList;
import java.util.List;

/**
 * 维度形状：覆盖整个维度，无视选区。
 *
 * <p>选区时（{@link Selection} 的 shapeType 设为 dimension）不收集任何锚点，
 * 创建出的区域即"整个维度"，玩家在该维度任意坐标都被判定为区域内，
 * 效果在全维度生效。主要用于希望效果全局覆盖、又不想（或无法）框出一个巨大选区的场景。
 *
 * <p>几何约定：
 * <ul>
 *   <li>{@link #boundsContains} 与 {@link #contains} 恒为 true——维度内任意坐标都在区域内，
 *       因此 {@code AreaTable.findAt} 的 AABB 粗筛对维度形状永远命中；</li>
 *   <li>包围盒取世界硬边界（±30,000,000 × Y 0..255），保证与真实世界坐标语义一致，
 *       也不参与任何数值计算（判定恒真，不含锚点计算）；</li>
 *   <li>{@link #computeEdges()} 返回空——整个维度没有可绘制的线框边界。</li>
 * </ul>
 */
public class DimensionShape extends AreaShape {

    /** 世界硬边界（Minecraft 1.7.10 世界边界为 ±30,000,000）。 */
    public static final int WORLD_LIMIT = 30_000_000;

    public DimensionShape() {
        super(new ArrayList<Vec3i>(), false,
                new Bounds(-WORLD_LIMIT, 0, -WORLD_LIMIT, WORLD_LIMIT, FULL_MAX_Y, WORLD_LIMIT));
    }

    @Override
    public String typeId() {
        return ShapeTypes.TYPE_DIMENSION;
    }

    @Override
    public boolean contains(double x, double y, double z) {
        return true;
    }

    @Override
    public boolean boundsContains(double x, double y, double z) {
        return true;
    }

    @Override
    protected List<Edge> computeEdges() {
        // 整个维度无边界线框可画；区域列表的详情文案足以表达其范围
        return new ArrayList<>();
    }
}
