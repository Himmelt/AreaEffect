package org.soraworld.areaeffect.common.network;

import io.netty.buffer.ByteBuf;

/**
 * 服务端 → 客户端：/areaeffect duration 指令专用，更新亮度效果的过渡时长。
 */
public class MessageDurationUpdate implements IPacket {

    public int dim;
    public int id;
    public float duration;

    public MessageDurationUpdate() {
    }

    public MessageDurationUpdate(int dim, int id, float duration) {
        this.dim = dim;
        this.id = id;
        this.duration = duration;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(dim);
        buf.writeInt(id);
        buf.writeFloat(duration);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        dim = buf.readInt();
        id = buf.readInt();
        duration = buf.readFloat();
    }
}