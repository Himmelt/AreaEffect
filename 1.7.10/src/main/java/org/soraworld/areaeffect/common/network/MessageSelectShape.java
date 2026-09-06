package org.soraworld.areaeffect.common.network;

import io.netty.buffer.ByteBuf;
import org.soraworld.areaeffect.common.effect.EffectTypes;

/**
 * 客户端 → 服务端：设置选区形状 / 闭合多边形。
 * type 非空 = 切换形状并重置选区；type 为空且 close=true = 闭合多边形。
 * 右键空气撤回请走 {@link MessageClickAir}。
 */
public class MessageSelectShape implements IPacket {

    public String type = "";
    public boolean close = false;

    public MessageSelectShape() {
    }

    public MessageSelectShape(String type, boolean close) {
        this.type = type == null ? "" : type;
        this.close = close;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        EffectTypes.writeString(buf, type);
        buf.writeBoolean(close);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        type = EffectTypes.readString(buf);
        close = buf.readBoolean();
    }
}
