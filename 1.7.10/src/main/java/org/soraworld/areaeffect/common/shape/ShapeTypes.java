package org.soraworld.areaeffect.common.shape;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import org.soraworld.areaeffect.common.effect.EffectTypes;
import org.soraworld.areaeffect.common.util.Vec3i;

import java.util.List;

/**
 * 形状类型注册表与工厂，仿 {@link EffectTypes} 的 switch 分支模式。
 * 新增形状时：实现 {@link AreaShape} 子类并在此围绕 NBT/Buf 各接一条分支。
 */
public final class ShapeTypes {

    public static final String TYPE_BOX = "box";
    public static final String TYPE_SQUARE_PILLAR = "square_pillar";
    public static final String TYPE_CYLINDER = "cylinder";
    public static final String TYPE_ROUND_PILLAR = "round_pillar";
    public static final String TYPE_SPHERE = "sphere";
    public static final String TYPE_POLYGON = "polygon";
    public static final String TYPE_POLYGON_PILLAR = "polygon_pillar";

    /** GUI 形状菜单按钮顺序。 */
    public static final String[] ALL = {
            TYPE_BOX, TYPE_SQUARE_PILLAR, TYPE_CYLINDER, TYPE_ROUND_PILLAR,
            TYPE_SPHERE, TYPE_POLYGON, TYPE_POLYGON_PILLAR
    };

    private ShapeTypes() {
    }

    public static boolean isPolygon(String type) {
        return TYPE_POLYGON.equals(type) || TYPE_POLYGON_PILLAR.equals(type);
    }

    public static boolean isFullHeight(String type) {
        return TYPE_SQUARE_PILLAR.equals(type) || TYPE_ROUND_PILLAR.equals(type) || TYPE_POLYGON_PILLAR.equals(type);
    }

    /** 各形状锚点完备性检查。 */
    public static boolean canBuild(Selection sel) {
        if (sel == null) {
            return false;
        }
        List<Vec3i> anchors = sel.anchors;
        switch (sel.shapeType) {
            case TYPE_BOX:
            case TYPE_SQUARE_PILLAR:
            case TYPE_CYLINDER:
            case TYPE_ROUND_PILLAR:
            case TYPE_SPHERE:
                return anchors.size() >= 2 && anchors.get(0) != null && anchors.get(1) != null;
            case TYPE_POLYGON:
            case TYPE_POLYGON_PILLAR:
                // 多边形自动闭合：顶点 ≥3 即视为闭合可创建
                return anchors.size() >= 3;
            default:
                return false;
        }
    }

    /** 从选区构建形状，不可构建返回 null。 */
    public static AreaShape build(Selection sel) {
        if (!canBuild(sel)) {
            return null;
        }
        List<Vec3i> anchors = sel.anchors;
        switch (sel.shapeType) {
            case TYPE_BOX:
                return new PrismShape(PrismShape.Section.RECT, PrismShape.Height.BOUNDED, anchors, false);
            case TYPE_SQUARE_PILLAR:
                return new PrismShape(PrismShape.Section.RECT, PrismShape.Height.FULL, anchors, false);
            case TYPE_CYLINDER:
                return anchors.size() >= 2 ? new PrismShape(PrismShape.Section.CIRCLE, PrismShape.Height.BOUNDED, anchors, false) : null;
            case TYPE_ROUND_PILLAR:
                return anchors.size() >= 2 ? new PrismShape(PrismShape.Section.CIRCLE, PrismShape.Height.FULL, anchors, false) : null;
            case TYPE_SPHERE:
                return new SphereShape(anchors.get(0), anchors.get(1));
            case TYPE_POLYGON:
                return new PrismShape(PrismShape.Section.POLYGON, PrismShape.Height.BOUNDED, anchors, true);
            case TYPE_POLYGON_PILLAR:
                return new PrismShape(PrismShape.Section.POLYGON, PrismShape.Height.FULL, anchors, true);
            default:
                return null;
        }
    }

    /** 从 NBT 反序列化（tag 需含 "type" 字符串）。 */
    public static AreaShape fromNbt(NBTTagCompound tag) {
        String type = tag.getString("type");
        boolean closed = tag.getBoolean("closed");
        List<Vec3i> anchors = AreaShape.readAnchorsNbt(tag);
        switch (type) {
            case TYPE_BOX:
                return new PrismShape(PrismShape.Section.RECT, PrismShape.Height.BOUNDED, anchors, false);
            case TYPE_SQUARE_PILLAR:
                return new PrismShape(PrismShape.Section.RECT, PrismShape.Height.FULL, anchors, false);
            case TYPE_CYLINDER:
                return anchors.size() >= 2 ? new PrismShape(PrismShape.Section.CIRCLE, PrismShape.Height.BOUNDED, anchors, false) : null;
            case TYPE_ROUND_PILLAR:
                return anchors.size() >= 2 ? new PrismShape(PrismShape.Section.CIRCLE, PrismShape.Height.FULL, anchors, false) : null;
            case TYPE_SPHERE:
                return anchors.size() >= 2 ? new SphereShape(anchors.get(0), anchors.get(1)) : null;
            case TYPE_POLYGON:
                return new PrismShape(PrismShape.Section.POLYGON, PrismShape.Height.BOUNDED, anchors, closed);
            case TYPE_POLYGON_PILLAR:
                return new PrismShape(PrismShape.Section.POLYGON, PrismShape.Height.FULL, anchors, closed);
            default:
                return null;
        }
    }

    /** 序列化到 NBT。 */
    public static void writeNbt(AreaShape shape, NBTTagCompound tag) {
        tag.setString("type", shape.typeId());
        shape.writeToNbt(tag);
    }

    /** 从网络缓冲反序列化（前置一个 type 字符串）。 */
    public static AreaShape fromBuf(ByteBuf buf) {
        String type = EffectTypes.readString(buf);
        List<Vec3i> anchors = AreaShape.readAnchorsBuf(buf);
        boolean closed = buf.readBoolean();
        switch (type) {
            case TYPE_BOX:
                return new PrismShape(PrismShape.Section.RECT, PrismShape.Height.BOUNDED, anchors, false);
            case TYPE_SQUARE_PILLAR:
                return new PrismShape(PrismShape.Section.RECT, PrismShape.Height.FULL, anchors, false);
            case TYPE_CYLINDER:
                return anchors.size() >= 2 ? new PrismShape(PrismShape.Section.CIRCLE, PrismShape.Height.BOUNDED, anchors, false) : null;
            case TYPE_ROUND_PILLAR:
                return anchors.size() >= 2 ? new PrismShape(PrismShape.Section.CIRCLE, PrismShape.Height.FULL, anchors, false) : null;
            case TYPE_SPHERE:
                return anchors.size() >= 2 ? new SphereShape(anchors.get(0), anchors.get(1)) : null;
            case TYPE_POLYGON:
                return new PrismShape(PrismShape.Section.POLYGON, PrismShape.Height.BOUNDED, anchors, closed);
            case TYPE_POLYGON_PILLAR:
                return new PrismShape(PrismShape.Section.POLYGON, PrismShape.Height.FULL, anchors, closed);
            default:
                return null;
        }
    }

    /** 序列化到网络缓冲。 */
    public static void writeBuf(AreaShape shape, ByteBuf buf) {
        EffectTypes.writeString(buf, shape.typeId());
        shape.writeToBuf(buf);
    }
}
