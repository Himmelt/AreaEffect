package org.soraworld.areaeffect.common.network;

import io.netty.buffer.ByteBuf;
import org.soraworld.areaeffect.common.util.Vec3i;

/**
 * 服务端 → 客户端：同步选区，客户端据此自绘选区线框。
 */
public class MessageSelection implements IPacket {

    public Vec3i pos1;
    public Vec3i pos2;

    public MessageSelection() {
    }

    public MessageSelection(Vec3i pos1, Vec3i pos2) {
        this.pos1 = pos1;
        this.pos2 = pos2;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(pos1 != null);
        if (pos1 != null) {
            buf.writeInt(pos1.x);
            buf.writeInt(pos1.y);
            buf.writeInt(pos1.z);
        }
        buf.writeBoolean(pos2 != null);
        if (pos2 != null) {
            buf.writeInt(pos2.x);
            buf.writeInt(pos2.y);
            buf.writeInt(pos2.z);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos1 = buf.readBoolean() ? new Vec3i(buf.readInt(), buf.readInt(), buf.readInt()) : null;
        pos2 = buf.readBoolean() ? new Vec3i(buf.readInt(), buf.readInt(), buf.readInt()) : null;
    }
}