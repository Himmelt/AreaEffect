package org.soraworld.areaeffect.common.network;

import io.netty.buffer.ByteBuf;

/**
 * 客户端 → 服务端：按 id 删除某个维度下的区域（服务端仍需再次鉴权）。
 */
public class MessageDeleteRequest implements IPacket {

    public int dim;
    public int id;

    public MessageDeleteRequest() {
    }

    public MessageDeleteRequest(int dim, int id) {
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