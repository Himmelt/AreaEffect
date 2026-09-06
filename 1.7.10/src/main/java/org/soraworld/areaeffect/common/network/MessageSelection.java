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
    public boolean closed = false;
    public int heightPhase = Selection.PHASE_VERTICES;

    public MessageSelection() {
    }

    public MessageSelection(Selection sel) {
        if (sel != null) {
            this.shapeType = sel.shapeType;
            this.anchors = sel.anchors.toArray(new Vec3i[0]);
            this.closed = sel.closed;
            this.heightPhase = sel.heightPhase;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        EffectTypes.writeString(buf, shapeType);
        buf.writeInt(anchors.length);
        for (Vec3i anchor : anchors) {
            if (anchor != null) {
                buf.writeInt(anchor.x);
                buf.writeInt(anchor.y);
                buf.writeInt(anchor.z);
            } else {
                buf.writeInt(0);
                buf.writeInt(0);
                buf.writeInt(0);
            }
        }
        buf.writeBoolean(closed);
        buf.writeByte(heightPhase);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        shapeType = EffectTypes.readString(buf);
        int size = Math.min(Math.max(buf.readInt(), 0), Selection.MAX_ANCHORS);
        anchors = new Vec3i[size];
        for (int i = 0; i < size; i++) {
            anchors[i] = new Vec3i(buf.readInt(), buf.readInt(), buf.readInt());
        }
        closed = buf.readBoolean();
        heightPhase = buf.readByte();
    }
}
