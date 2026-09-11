package org.soraworld.areaeffect.common.network;

import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端 → 客户端：创建区域冲突时，携带该维度下所有冲突区域的 id 列表，
 * 客户端据此自动开启这些区域的线框显示。
 */
public class MessageConflictAreas implements IPacket {

    /**
     * 冲突 id 列表的收发上限。写侧与读侧<b>共用此值</b>（与 {@link Area#EFFECT_MAX}、
     * {@link org.soraworld.areaeffect.common.shape.Selection#MAX_ANCHORS} 同一原则）：
     * 写出的条数一旦多于读回的条数，包内后续字节会滞留、元素错位。
     */
    public static final int MAX_IDS = 256;

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
        // 与读侧同源的上限（MAX_IDS）：写出条数不得多于读回条数
        int count = Math.min(ids.size(), MAX_IDS);
        buf.writeInt(count);
        for (int i = 0; i < count; i++) {
            buf.writeInt(ids.get(i));
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        dim = buf.readInt();
        int size = Math.min(Math.max(buf.readInt(), 0), MAX_IDS);
        ids = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ids.add(buf.readInt());
        }
    }
}
