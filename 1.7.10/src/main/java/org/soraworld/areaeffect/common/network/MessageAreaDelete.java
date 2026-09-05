package org.soraworld.areaeffect.common.network;

import io.netty.buffer.ByteBuf;

/**
 * 服务端 → 客户端：删除某个区域。
 */
public class MessageAreaDelete implements IPacket {

    public int dim;
    public int id;

    public MessageAreaDelete() {
    }

    public MessageAreaDelete(int dim, int id) {
        this.dim = dim;
        this.id = id;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(dim);
        buf.writeInt(id);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        dim = buf.readInt();
        id = buf.readInt();
    }
}