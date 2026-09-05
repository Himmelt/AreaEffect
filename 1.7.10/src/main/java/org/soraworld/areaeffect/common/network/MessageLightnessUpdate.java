package org.soraworld.areaeffect.common.network;

import io.netty.buffer.ByteBuf;

/**
 * 服务端 → 客户端：/areaeffect lightness 指令专用，更新亮度效果的 lightness。
 */
public class MessageLightnessUpdate implements IPacket {

    public int dim;
    public int id;
    public float lightness;

    public MessageLightnessUpdate() {
    }

    public MessageLightnessUpdate(int dim, int id, float lightness) {
        this.dim = dim;
        this.id = id;
        this.lightness = lightness;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(dim);
        buf.writeInt(id);
        buf.writeFloat(lightness);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        dim = buf.readInt();
        id = buf.readInt();
        lightness = buf.readFloat();
    }
}