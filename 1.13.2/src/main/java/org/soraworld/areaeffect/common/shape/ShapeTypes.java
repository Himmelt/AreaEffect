package org.soraworld.areaeffect.common.shape;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.NBTTagCompound;
import org.soraworld.areaeffect.common.effect.EffectTypes;
import org.soraworld.areaeffect.common.util.Vec3i;

import java.util.List;

/**
 * 形状类型注册表与工厂，仿 {@link EffectTypes} 的 switch 分支模式。
 * 新增形状时：实现 {@link AreaShape} 子类并在此围绕 NBT/Buf 各接一条分支。
 *
 * <p>序列化写的是<b>连续几何参数</b>（double），不再写方块锚点：{@code create} 阶段把
 * {@code Vec3i} 锚点归一化成 double 几何后，后续判定 / 线框 / 网络 / NBT 全部走同一份连续几何。
 */
public final class ShapeTypes {

    public static final String TYPE_BOX = "box";
    public static final String TYPE_SQUARE_PILLAR = "square_pillar";
    public static final String TYPE_CYLINDER = "cylinder";
    public static final String TYPE_ROUND_PILLAR = "round_pillar";
    public static final String TYPE_SPHERE = "sphere";
    public static final String TYPE_POLYGON = "polygon";
    public static final String TYPE_POLYGON_PILLAR = "polygon_pillar";
    /** 维度形状：无视选区，覆盖整个维度（见 {@link DimensionShape}）。 */
    public static final String TYPE_DIMENSION = "dimension";

    /** GUI 形状菜单按钮顺序。 */
    public static final String[] ALL = {
            TYPE_BOX, TYPE_SQUARE_PILLAR, TYPE_CYLINDER, TYPE_ROUND_PILLAR,
            TYPE_SPHERE, TYPE_POLYGON, TYPE_POLYGON_PILLAR, TYPE_DIMENSION
    };

    /** 形状网络负载的最大字节数（防御恶意长度前缀）。 */
    private static final int MAX_PAYLOAD = 1 << 20;

    private ShapeTypes() {
    }

    public static boolean isPolygon(String type) {
        return TYPE_POLYGON.equals(type) || TYPE_POLYGON_PILLAR.equals(type);
    }

    /**
     * 是否为已注册的形状类型。客户端可指定选区形状（{@code MessageSelectShape}），
     * 服务端必须先经此白名单校验，不接受任意字符串进入选区状态。
     */
    public static boolean isValid(String type) {
        if (type == null) {
            return false;
        }
        for (String known : ALL) {
            if (known.equals(type)) {
                return true;
            }
        }
        return false;
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
            case TYPE_DIMENSION:
                // 维度形状无视选区：不需要任何锚点，恒可创建
                return true;
            default:
                return false;
        }
    }

    /** 从选区构建形状，不可构建返回 null。 */
    public static AreaShape build(Selection sel) {
        if (!canBuild(sel)) {
            return null;
        }
        // 多边形自动闭合（顶点 ≥3 即视为闭合）；其余形状的 closed 无意义，统一 false
        return createFromAnchors(sel.shapeType, sel.anchors, isPolygon(sel.shapeType));
    }

    /** 形状的唯一"由方块锚点归一化构造"入口：type + 锚点 + closed → double 连续几何形状。 */
    private static AreaShape createFromAnchors(String type, List<Vec3i> anchors, boolean closed) {
        if (type == null || anchors == null) {
            return null;
        }
        switch (type) {
            case TYPE_BOX:
                return anchors.size() >= 2
                        ? new PrismShape(PrismShape.Section.RECT, PrismShape.Height.BOUNDED, anchors, false) : null;
            case TYPE_SQUARE_PILLAR:
                return anchors.size() >= 2
                        ? new PrismShape(PrismShape.Section.RECT, PrismShape.Height.FULL, anchors, false) : null;
            case TYPE_CYLINDER:
                return anchors.size() >= 2 ? new PrismShape(PrismShape.Section.CIRCLE, PrismShape.Height.BOUNDED, anchors, false) : null;
            case TYPE_ROUND_PILLAR:
                return anchors.size() >= 2 ? new PrismShape(PrismShape.Section.CIRCLE, PrismShape.Height.FULL, anchors, false) : null;
            case TYPE_SPHERE:
                return anchors.size() >= 2 ? new SphereShape(anchors.get(0), anchors.get(1)) : null;
            case TYPE_POLYGON:
                return anchors.size() >= 3
                        ? new PrismShape(PrismShape.Section.POLYGON, PrismShape.Height.BOUNDED, anchors, closed) : null;
            case TYPE_POLYGON_PILLAR:
                return anchors.size() >= 3
                        ? new PrismShape(PrismShape.Section.POLYGON, PrismShape.Height.FULL, anchors, closed) : null;
            case TYPE_DIMENSION:
                // 维度形状不消费锚点，忽略传入的 closed
                return new DimensionShape();
            default:
                return null;
        }
    }

    /** 从 NBT 反序列化（tag 需含 "type" 字符串）。 */
    public static AreaShape fromNbt(NBTTagCompound tag) {
        if (tag == null) {
            return null;
        }
        try {
            return dispatch(tag.getString("type"), null, tag);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 序列化到 NBT。 */
    public static void writeNbt(AreaShape shape, NBTTagCompound tag) {
        // 1.13+ NBT 写入方法统一为 putXxx
        tag.putString("type", shape.typeId());
        shape.writeToNbt(tag);
    }

    /**
     * 从网络缓冲反序列化。
     *
     * <p>网络格式带<b>负载长度前缀</b>：{@code int 负载长度 + type 字符串 + 几何参数}。读取方先按长度
     * 切出独立 sub-buffer 再解析类型；即使 type 未知或参数畸形，父缓冲也已跳到本元素末尾 ——
     * 同包后续元素不会因一次解析失败而错位（这也是旧的"固定字段可整体跳过"在新格式下不再成立的原因：
     * 各形状的几何负载长度不再统一）。暴露长度本身也防恶意超大包：长度超限直接拒绝。
     */
    public static AreaShape fromBuf(ByteBuf buf) {
        int len = buf.readInt();
        if (len < 0 || len > MAX_PAYLOAD || len > buf.readableBytes()) {
            return null;
        }
        ByteBuf payload = buf.readSlice(len);
        try {
            String type = EffectTypes.readString(payload);
            return dispatch(type, payload, null);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 序列化到网络缓冲（带负载长度前缀）。 */
    public static void writeBuf(AreaShape shape, ByteBuf buf) {
        ByteBuf payload = Unpooled.buffer();
        EffectTypes.writeString(payload, shape.typeId());
        shape.writeToBuf(payload);
        buf.writeInt(payload.readableBytes());
        buf.writeBytes(payload);
    }

    /** 按 type 分派反序列化（NBT 与 Buf 二选一，传 null 的那个忽略）。 */
    private static AreaShape dispatch(String type, ByteBuf buf, NBTTagCompound tag) {
        if (TYPE_DIMENSION.equals(type)) {
            return new DimensionShape();
        }
        if (TYPE_SPHERE.equals(type)) {
            return buf != null ? SphereShape.fromBuf(buf) : SphereShape.fromNbt(tag);
        }
        if (buf != null) {
            return PrismShape.fromBuf(type, buf);
        }
        return PrismShape.fromNbt(type, tag);
    }
}
