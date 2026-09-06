package org.soraworld.areaeffect.common.shape;

import org.soraworld.areaeffect.common.util.Vec3i;

import java.util.ArrayList;
import java.util.List;

/**
 * 选区状态（服务端权威，客户端镜像同一格式）。
 * <p>状态机：
 * <ul>
 *   <li>二点形状（box/square_pillar/cylinder/round_pillar/sphere）：anchors 固定 0/1 两个槽位，重复点击覆盖</li>
 *   <li>polygon / polygon_pillar：左键追加顶点，右键撤回上一个点；多边形自动闭合预览，顶点 ≥3 即可创建</li>
 * </ul>
 */
public class Selection {

    public static final int PHASE_VERTICES = 0;
    public static final int PHASE_NEED_Y1 = 1;
    public static final int PHASE_NEED_Y2 = 2;
    public static final int PHASE_COMPLETE = 3;

    /** 锚点数量上限（防恶意包 / 包体膨胀）。 */
    public static final int MAX_ANCHORS = 64;

    public String shapeType = ShapeTypes.TYPE_BOX;
    public final List<Vec3i> anchors = new ArrayList<>();
    public boolean closed = false;
    public int heightPhase = PHASE_VERTICES;

    public Selection() {
    }

    /** 换形状：清空锚点 / 闭合标志 / 高度阶段。 */
    public void reset(String type) {
        this.shapeType = type == null || type.isEmpty() ? ShapeTypes.TYPE_BOX : type;
        this.anchors.clear();
        this.closed = false;
        this.heightPhase = PHASE_VERTICES;
    }

    public boolean isPolygon() {
        return ShapeTypes.isPolygon(shapeType);
    }

    public boolean isFullHeight() {
        return ShapeTypes.isFullHeight(shapeType);
    }

    /** 左键点击处理（服务端分派）。 */
    public void onClickLeft(Vec3i pos) {
        if (ShapeTypes.isPolygon(shapeType)) {
            // 多边形：追加顶点（自动闭合预览由渲染处理，无需手动闭合）
            if (anchors.size() < MAX_ANCHORS) {
                anchors.add(pos);
            }
        } else {
            // 二点形状：覆盖槽位 0
            setAnchor(0, pos);
        }
    }

    /** 右键点击处理（服务端分派，BLOCK / AIR 交互共用）。多边形下撤回上一个点；AIR 无坐标，二点形状忽略。 */
    public void onClickRight(Vec3i pos) {
        if (ShapeTypes.isPolygon(shapeType)) {
            undoLastVertex();
        } else if (pos != null) {
            // 二点形状：覆盖槽位 1
            setAnchor(1, pos);
        }
    }

    /** 撤回多边形最后一个顶点。 */
    public void undoLastVertex() {
        if (!anchors.isEmpty()) {
            anchors.remove(anchors.size() - 1);
        }
    }

    /** 多边形闭合（Shift+左键 → 客户端发包 → 服务端处理）：顶点 ≥3 即完成，无定高步骤。 */
    public boolean closePolygon() {
        if (ShapeTypes.isPolygon(shapeType) && !closed && anchors.size() >= 3) {
            closed = true;
            heightPhase = PHASE_COMPLETE;
            return true;
        }
        return false;
    }

    private void setAnchor(int index, Vec3i pos) {
        if (index == 0) {
            if (anchors.isEmpty()) {
                anchors.add(pos);
            } else {
                anchors.set(0, pos);
            }
        } else {
            if (anchors.size() < 2) {
                anchors.add(pos);
            } else {
                anchors.set(1, pos);
            }
        }
    }

    public boolean isBuildable() {
        return ShapeTypes.canBuild(this);
    }

    public AreaShape build() {
        return ShapeTypes.build(this);
    }
}
