package org.soraworld.areaeffect.common.network;

import io.netty.buffer.ByteBuf;
import org.soraworld.areaeffect.common.effect.EffectTypes;

/**
 * 客户端 → 服务端：设置选区形状 / 闭合多边形 / 撤回多边形顶点。
 * type 非空 = 切换形状并重置选区；type 为空且 close=true = 闭合多边形；undo=true = 撤回上一个顶点。
 */
public class MessageSelectShape implements IPacket {

    public String type = "";
    public boolean close = false;
    public boolean undo = false;

    public MessageSelectShape() {
    }

    public MessageSelectShape(String type, boolean close) {
        this.type = type == null ? "" : type;
        this.close = close;
    }

    public MessageSelectShape(boolean close, boolean undo, String type) {
        this.type = type == null ? "" : type;
        this.close = close;
        this.undo = undo;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        EffectTypes.writeString(buf, type);
        buf.writeBoolean(close);
        buf.writeBoolean(undo);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        type = EffectTypes.readString(buf);
        close = buf.readBoolean();
        undo = buf.readBoolean();
    }
}
