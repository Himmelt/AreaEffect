package org.soraworld.areaeffect.network;

import io.netty.buffer.ByteBuf;
import org.soraworld.areaeffect.util.Vec3i;

import java.util.ArrayList;
import java.util.List;

public abstract class AreaPacket {

    public final int id;
    public final int dim;

    public AreaPacket(int dim, int id) {
        this.dim = dim;
        this.id = id;
    }

    public static class Update extends AreaPacket {
        public final Area data;

        public Update(int dim, int id, Area data) {
            super(dim, id);
            this.data = data;
        }

        public static void encode(Update packet, ByteBuf buf) {
            buf.writeInt(packet.dim);
            buf.writeInt(packet.id);
            buf.writeBytes(Area.toByteBuf(packet.data));
        }

        public static Update decode(ByteBuf buf) {
            int dim = buf.readInt();
            int id = buf.readInt();
            Area data = Area.fromByteBuf(buf);
            return new Update(dim, id, data);
        }
    }

    public static class Delete extends AreaPacket {
        public Delete(int dim, int id) {
            super(dim, id);
        }

        public static void encode(Delete packet, ByteBuf buf) {
            buf.writeInt(packet.dim);
            buf.writeInt(packet.id);
        }

        public static Delete decode(ByteBuf buf) {
            int dim = buf.readInt();
            int id = buf.readInt();
            return new Delete(dim, id);
        }
    }

    public static class Lightness extends AreaPacket {
        public final float lightness;

        public Lightness(int dim, int id, float lightness) {
            super(dim, id);
            this.lightness = lightness;
        }

        public static void encode(Lightness packet, ByteBuf buf) {
            buf.writeInt(packet.dim);
            buf.writeInt(packet.id);
            buf.writeFloat(packet.lightness);
        }

        public static Lightness decode(ByteBuf buf) {
            int dim = buf.readInt();
            int id = buf.readInt();
            float lightness = buf.readFloat();
            return new Lightness(dim, id, lightness);
        }
    }

    public static class Selection {
        public final Vec3i pos1;
        public final Vec3i pos2;

        public Selection(Vec3i pos1, Vec3i pos2) {
            this.pos1 = pos1;
            this.pos2 = pos2;
        }

        public static void encode(Selection packet, ByteBuf buf) {
            buf.writeBoolean(packet.pos1 != null);
            if (packet.pos1 != null) {
                buf.writeInt(packet.pos1.x);
                buf.writeInt(packet.pos1.y);
                buf.writeInt(packet.pos1.z);
            }
            buf.writeBoolean(packet.pos2 != null);
            if (packet.pos2 != null) {
                buf.writeInt(packet.pos2.x);
                buf.writeInt(packet.pos2.y);
                buf.writeInt(packet.pos2.z);
            }
        }

        public static Selection decode(ByteBuf buf) {
            Vec3i pos1 = readPos(buf);
            Vec3i pos2 = readPos(buf);
            return new Selection(pos1, pos2);
        }

        private static Vec3i readPos(ByteBuf buf) {
            return buf.readBoolean() ? new Vec3i(buf.readInt(), buf.readInt(), buf.readInt()) : null;
        }
    }

    public static class Duration extends AreaPacket {
        public final float duration;

        public Duration(int dim, int id, float duration) {
            super(dim, id);
            this.duration = duration;
        }

        public static void encode(Duration packet, ByteBuf buf) {
            buf.writeInt(packet.dim);
            buf.writeInt(packet.id);
            buf.writeFloat(packet.duration);
        }

        public static Duration decode(ByteBuf buf) {
            int dim = buf.readInt();
            int id = buf.readInt();
            float duration = buf.readFloat();
            return new Duration(dim, id, duration);
        }
    }

    /**
     * 客户端 → 服务端：请求当前维度的区域列表（仅 OP 允许）。
     */
    public static class ListRequest {
        public static void encode(ListRequest packet, ByteBuf buf) {
        }

        public static ListRequest decode(ByteBuf buf) {
            return new ListRequest();
        }
    }

    /**
     * 服务端 → 客户端：回复某个维度的区域列表。
     */
    public static class ListReply {
        public final int dim;
        public final List<Area> areas;

        public ListReply(int dim, List<Area> areas) {
            this.dim = dim;
            this.areas = areas;
        }

        public static void encode(ListReply packet, ByteBuf buf) {
            buf.writeInt(packet.dim);
            buf.writeInt(packet.areas.size());
            for (Area area : packet.areas) {
                buf.writeInt(area.id);
                buf.writeBytes(Area.toByteBuf(area));
            }
        }

        public static ListReply decode(ByteBuf buf) {
            int dim = buf.readInt();
            int size = buf.readInt();
            List<Area> areas = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                int id = buf.readInt();
                Area area = Area.fromByteBuf(buf);
                area.id = id;
                areas.add(area);
            }
            return new ListReply(dim, areas);
        }
    }

    /**
     * 客户端 → 服务端：将某个区域的参数写回（服务端仍需再次鉴权）。
     */
    public static class SetProps {
        public final int dim;
        public final int id;
        public final float lightness;
        public final float duration;

        public SetProps(int dim, int id, float lightness, float duration) {
            this.dim = dim;
            this.id = id;
            this.lightness = lightness;
            this.duration = duration;
        }

        public static void encode(SetProps packet, ByteBuf buf) {
            buf.writeInt(packet.dim);
            buf.writeInt(packet.id);
            buf.writeFloat(packet.lightness);
            buf.writeFloat(packet.duration);
        }

        public static SetProps decode(ByteBuf buf) {
            int dim = buf.readInt();
            int id = buf.readInt();
            float lightness = buf.readFloat();
            float duration = buf.readFloat();
            return new SetProps(dim, id, lightness, duration);
        }
    }
}
