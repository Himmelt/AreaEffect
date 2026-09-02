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

    public static class Gamma extends AreaPacket {
        public final float gamma;

        public Gamma(int dim, int id, float gamma) {
            super(dim, id);
            this.gamma = gamma;
        }

        public static void encode(Gamma packet, ByteBuf buf) {
            buf.writeInt(packet.dim);
            buf.writeInt(packet.id);
            buf.writeFloat(packet.gamma);
        }

        public static Gamma decode(ByteBuf buf) {
            int dim = buf.readInt();
            int id = buf.readInt();
            float gamma = buf.readFloat();
            return new Gamma(dim, id, gamma);
        }
    }

    public static class Speed extends AreaPacket {
        public final float speed;

        public Speed(int dim, int id, float speed) {
            super(dim, id);
            this.speed = speed;
        }

        public static void encode(Speed packet, ByteBuf buf) {
            buf.writeInt(packet.dim);
            buf.writeInt(packet.id);
            buf.writeFloat(packet.speed);
        }

        public static Speed decode(ByteBuf buf) {
            int dim = buf.readInt();
            int id = buf.readInt();
            float speed = buf.readFloat();
            return new Speed(dim, id, speed);
        }
    }
}
