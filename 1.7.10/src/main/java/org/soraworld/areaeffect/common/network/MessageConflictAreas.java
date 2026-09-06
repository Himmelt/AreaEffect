package org.soraworld.areaeffect.common.network;

import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端 → 客户端：创建区域冲突时，携带该维度下所有冲突区域的 id 列表，
 * 客户端据此自动开启这些区域的线框显示。
 */
public class MessageConflictAreas implements IPacket {

    public int dim;
    public List<Integer> ids = new ArrayList<>();

    public MessageConflictAreas() {
    }

    public MessageConflictAreas(int dim, List<Integer> ids) {
        this.dim = dim;
        this.ids = ids == null ? new ArrayList<>() : ids;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(dim);
        buf.writeInt(ids.size());
        for (Integer id : ids) {
            buf.writeInt(id);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        dim = buf.readInt();
        int size = Math.min(Math.max(buf.readInt(), 0), 256);
        ids = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ids.add(buf.readInt());
        }
    }
}
