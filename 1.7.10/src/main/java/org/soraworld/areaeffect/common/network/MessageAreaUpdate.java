package org.soraworld.areaeffect.common.network;

import io.netty.buffer.ByteBuf;

/**
 * 服务端 → 客户端：新增/更新某个区域的完整数据（含效果列表）。
 */
public class MessageAreaUpdate implements IPacket {

    public int dim;
    public int id;
    public Area data;

    public MessageAreaUpdate() {
    }

    public MessageAreaUpdate(int dim, int id, Area data) {
        this.dim = dim;
        this.id = id;
        this.data = data;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(dim);
        buf.writeInt(id);
        Area.writeBuf(buf, data);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        dim = buf.readInt();
        id = buf.readInt();
        data = Area.fromByteBuf(buf);
    }
}