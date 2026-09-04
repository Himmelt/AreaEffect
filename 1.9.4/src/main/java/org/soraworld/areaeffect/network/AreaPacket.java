package org.soraworld.areaeffect.network;

import io.netty.buffer.ByteBuf;

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
}
