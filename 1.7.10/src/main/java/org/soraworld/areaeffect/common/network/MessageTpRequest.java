package org.soraworld.areaeffect.common.network;

import io.netty.buffer.ByteBuf;

/**
 * 客户端 → 服务端：按 id 传送到某个区域中心（服务端仍需再次鉴权）。
 */
public class MessageTpRequest implements IPacket {

    public int id;

    public MessageTpRequest() {
    }

    public MessageTpRequest(int id) {
        this.id = id;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(id);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        id = buf.readInt();
    }
}