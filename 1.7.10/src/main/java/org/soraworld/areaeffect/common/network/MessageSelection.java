package org.soraworld.areaeffect.common.network;

import io.netty.buffer.ByteBuf;
import org.soraworld.areaeffect.common.effect.EffectTypes;
import org.soraworld.areaeffect.common.shape.Selection;
import org.soraworld.areaeffect.common.shape.ShapeTypes;
import org.soraworld.areaeffect.common.util.Vec3i;

/**
 * 服务端 → 客户端：同步选区，客户端据此自绘选区线框。
 */
public class MessageSelection implements IPacket {

    public String shapeType = ShapeTypes.TYPE_BOX;
    public Vec3i[] anchors = new Vec3i[0];

    public MessageSelection() {
    }

    public MessageSelection(Selection sel) {
        if (sel != null) {
            this.shapeType = sel.shapeType;
            this.anchors = sel.anchors.toArray(new Vec3i[0]);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        EffectTypes.writeString(buf, shapeType);
        // 与读侧同源的上限（Selection.MAX_ANCHORS）：写出条数不得多于读回条数，
        // 否则同一元素在两端长度不一致，会让包内后续元素错位
        int count = Math.min(anchors.length, Selection.MAX_ANCHORS);
        buf.writeInt(count);
        for (int i = 0; i < count; i++) {
            Vec3i anchor = anchors[i];
            buf.writeInt(anchor.x);
            buf.writeInt(anchor.y);
            buf.writeInt(anchor.z);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        shapeType = EffectTypes.readString(buf);
        int size = Math.min(Math.max(buf.readInt(), 0), Selection.MAX_ANCHORS);
        anchors = new Vec3i[size];
        for (int i = 0; i < size; i++) {
            anchors[i] = new Vec3i(buf.readInt(), buf.readInt(), buf.readInt());
        }
    }
}
