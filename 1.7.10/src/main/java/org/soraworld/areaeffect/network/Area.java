package org.soraworld.areaeffect.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.entity.player.EntityPlayer;
import org.soraworld.areaeffect.util.Vec3d;
import org.soraworld.areaeffect.util.Vec3i;

public class Area {

    public int id;
    public float gamma;
    public float speed;

    public final int x1;
    public final int y1;
    public final int z1;
    public final int x2;
    public final int y2;
    public final int z2;

    public Area(int x1, int y1, int z1, int x2, int y2, int z2, float gamma, float speed) {
        this.x1 = Math.min(x1, x2);
        this.y1 = Math.min(y1, y2);
        this.z1 = Math.min(z1, z2);
        this.x2 = Math.max(x1, x2);
        this.y2 = Math.max(y1, y2);
        this.z2 = Math.max(z1, z2);
        this.gamma = gamma;
        this.speed = Math.abs(speed);
    }

    public static ByteBuf toByteBuf(Area area) {
        ByteBuf buf = Unpooled.buffer();
        buf.writeInt(area.x1);
        buf.writeInt(area.y1);
        buf.writeInt(area.z1);
        buf.writeInt(area.x2);
        buf.writeInt(area.y2);
        buf.writeInt(area.z2);
        buf.writeFloat(area.gamma);
        buf.writeFloat(area.speed);
        return buf;
    }

    public static Area fromByteBuf(ByteBuf buf) {
        int x1 = buf.readInt();
        int y1 = buf.readInt();
        int z1 = buf.readInt();
        int x2 = buf.readInt();
        int y2 = buf.readInt();
        int z2 = buf.readInt();
        float light = buf.readFloat();
        float speed = buf.readFloat();
        return new Area(x1, y1, z1, x2, y2, z2, light, speed);
    }

    public boolean contains(double x, double y, double z) {
        return x >= x1 && x <= x2 + 1 && y >= y1 && y < y2 + 1 && z >= z1 && z <= z2 + 1;
    }

    public boolean contains(Vec3d pos) {
        return contains(pos.x, pos.y, pos.z);
    }

    public boolean conflict(Area area) {
        return x1 <= area.x2 && x2 >= area.x1 && y1 <= area.y2 && y2 >= area.y1 && z1 <= area.z2 && z2 >= area.z1;
    }

    @Override
    public String toString() {
        return x1 + "," + y1 + "," + z1 + "," + x2 + "," + y2 + "," + z2 + "," + gamma + "," + speed;
    }

    public String pos1() {
        return "(" + x1 + "," + y1 + "," + z1 + ')';
    }

    public String pos2() {
        return "(" + x2 + "," + y2 + "," + z2 + ')';
    }

    public Vec3i vec1() {
        return new Vec3i(x1, y1, z1);
    }

    public Vec3i vec2() {
        return new Vec3i(x2, y2, z2);
    }

    public void center(EntityPlayer player) {
        player.setPosition((x1 + x2) / 2.0, (y1 + y2) / 2.0, (z1 + z2) / 2.0);
    }

    public float nextGamma(float gamma) {
        if (gamma < this.gamma - speed) {
            gamma += speed;
        } else if (gamma > this.gamma + speed) {
            gamma -= speed;
        } else {
            gamma = this.gamma;
        }
        return gamma;
    }
}
