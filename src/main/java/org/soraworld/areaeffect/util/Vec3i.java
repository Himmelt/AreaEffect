package org.soraworld.areaeffect.util;

import net.minecraft.entity.player.EntityPlayer;

import java.nio.ByteBuffer;

public class Vec3i {
    public final int x, y, z;

    public Vec3i(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3i(EntityPlayer player) {
        this((int) player.posX, (int) player.posY, (int) player.posZ);
    }

    /**
     * 生成 WECUI 的 cuboid 角落点低层报文：
     * <pre>0x00 'p' &lt;cornerId&gt; &lt;size&gt; &lt;x&gt; &lt;y&gt; &lt;z&gt;</pre>
     * 与 CommonProxy 中先发送的 "s|cuboid" 形状报文配合使用。
     * （请核对 WECUI 协议格式）
     */
    public byte[] cui(int id, int size) {
        ByteBuffer buf = ByteBuffer.allocate(19);
        buf.put((byte) 0);
        buf.put((byte) 'p');
        buf.put((byte) id);
        buf.putInt(size);
        buf.putInt(x);
        buf.putInt(y);
        buf.putInt(z);
        return buf.array();
    }

    @Override
    public String toString() {
        return "(" + x + "," + y + "," + z + ')';
    }
}