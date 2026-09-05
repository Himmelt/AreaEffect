package org.soraworld.areaeffect.common.network;

import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端 → 客户端：回复某个维度的区域列表。
 */
public class MessageListReply implements IPacket {

    public int dim;
    public List<Area> areas = new ArrayList<>();

    public MessageListReply() {
    }

    public MessageListReply(int dim, List<Area> areas) {
        this.dim = dim;
        this.areas = areas;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(dim);
        buf.writeInt(areas.size());
        for (Area area : areas) {
            buf.writeInt(area.id);
            buf.writeBytes(Area.toByteBuf(area));
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        dim = buf.readInt();
        int size = buf.readInt();
        areas = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            int id = buf.readInt();
            Area area = Area.fromByteBuf(buf);
            area.id = id;
            areas.add(area);
        }
    }
}