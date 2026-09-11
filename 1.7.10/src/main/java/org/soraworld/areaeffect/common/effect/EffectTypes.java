package org.soraworld.areaeffect.common.effect;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;

import java.nio.charset.StandardCharsets;

/**
 * 效果类型注册表与反序列化工厂。新增效果时：
 * 1. 实现 {@link AreaEffect} 子类并给出 typeId；
 * 2. 在此围绕 NBT/Buf 的读写各接一条分支。
 */
public final class EffectTypes {

    public static final String TYPE_LIGHTNESS = "lightness";

    private EffectTypes() {
    }

    /**
     * 从 NBT 反序列化（根 tag 需含 "type" 字符串）。
     */
    public static AreaEffect fromNbt(NBTTagCompound tag) {
        String type = tag.getString("type");
        switch (type) {
            case TYPE_LIGHTNESS:
                return readLightnessNbt(tag);
            default:
                return null;
        }
    }

    /**
     * 从网络缓冲反序列化（前置一个 type 字符串）。
     */
    public static AreaEffect fromBuf(ByteBuf buf) {
        String type = readString(buf);
        switch (type) {
            case TYPE_LIGHTNESS:
                return readLightnessBuf(buf);
            default:
                return null;
        }
    }

    /**
     * 网络缓冲中写入长度前缀 UTF-8 字符串。
     */
    public static void writeString(ByteBuf buf, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
    }

    /**
     * 读取 {@link #writeString} 写入的字符串。
     */
    public static String readString(ByteBuf buf) {
        int len = buf.readUnsignedShort();
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static LightnessEffect readLightnessNbt(NBTTagCompound tag) {
        LightnessEffect effect = new LightnessEffect();
        effect.readNbtFields(tag);
        effect.setLightness(tag.getFloat("lightness"));
        effect.setDuration(tag.getFloat("duration"));
        return effect;
    }

    private static LightnessEffect readLightnessBuf(ByteBuf buf) {
        LightnessEffect effect = new LightnessEffect();
        effect.readBufFields(buf);
        effect.setLightness(buf.readFloat());
        effect.setDuration(buf.readFloat());
        return effect;
    }
}