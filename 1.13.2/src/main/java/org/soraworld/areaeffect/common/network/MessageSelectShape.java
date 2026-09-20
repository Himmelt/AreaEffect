package org.soraworld.areaeffect.common.network;

import io.netty.buffer.ByteBuf;
import org.soraworld.areaeffect.common.effect.EffectTypes;

/**
 * 客户端 → 服务端：设置选区形状（切换形状并重置选区）。
 * 多边形为自动闭合（≥3 顶点即可创建），右键空气撤回走 {@link MessageClickAir}。
 */
public class MessageSelectShape implements IPacket {

    public String type = "";

    public MessageSelectShape() {
    }

    public MessageSelectShape(String type) {
        this.type = type == null ? "" : type;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        EffectTypes.writeString(buf, type);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        type = EffectTypes.readString(buf);
    }
}
