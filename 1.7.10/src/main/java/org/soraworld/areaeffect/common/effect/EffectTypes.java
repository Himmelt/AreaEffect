package org.soraworld.areaeffect.common.effect;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 效果类型注册表与反序列化工厂。新增效果时：
 * 1. 实现 {@link AreaEffect} 子类并给出 typeId；
 * 2. 把 typeId 加进 {@link #ALL}，并在此围绕 NBT/Buf 的读写各接一条分支；
 * 3. 在 {@link #newDefault} 里给出"添加效果"用的默认实例。
 */
public final class EffectTypes {

    public static final String TYPE_LIGHTNESS = "lightness";
    public static final String TYPE_FOG = "fog";
    public static final String TYPE_SKY = "sky";

    /** 可添加的效果类型全集（面板"添加效果"按钮遍历此表）。 */
    public static final List<String> ALL = Collections.unmodifiableList(Arrays.asList(TYPE_LIGHTNESS, TYPE_FOG, TYPE_SKY));

    private EffectTypes() {
    }

    /** 新增一个指定类型的默认效果实例（面板"添加效果"用）；未知类型返回 null。 */
    public static AreaEffect newDefault(String typeId) {
        switch (typeId) {
            case TYPE_LIGHTNESS:
                // 亮度效果默认：亮度 100、过渡 1 秒（权重由 AreaEffect 默认为 0）
                return new LightnessEffect(100.0F, 1.0F);
            case TYPE_FOG:
                // 雾效果默认：浅雾色、16 米起雾、再过 7 米全白、无尘粒、过渡 1 秒
                return new FogEffect(7.0F, 16.0F, 40, 80, 50, 0, 1.0F);
            case TYPE_SKY:
                // 天空效果默认：天蓝色、过渡 1 秒
                return new SkyEffect(135, 206, 235, 1.0F);
            default:
                return null;
        }
    }

    /**
     * 从 NBT 反序列化（根 tag 需含 "type" 字符串）。
     */
    public static AreaEffect fromNbt(NBTTagCompound tag) {
        String type = tag.getString("type");
        switch (type) {
            case TYPE_LIGHTNESS:
                return readLightnessNbt(tag);
            case TYPE_FOG:
                return readFogNbt(tag);
            case TYPE_SKY:
                return readSkyNbt(tag);
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
            case TYPE_FOG:
                return readFogBuf(buf);
            case TYPE_SKY:
                return readSkyBuf(buf);
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
        return effect;
    }

    private static LightnessEffect readLightnessBuf(ByteBuf buf) {
        LightnessEffect effect = new LightnessEffect();
        effect.readBufFields(buf);
        effect.setLightness(buf.readFloat());
        return effect;
    }

    private static FogEffect readFogNbt(NBTTagCompound tag) {
        FogEffect effect = new FogEffect();
        effect.readNbtFields(tag);
        // 缺键时 getFloat 返回 0 —— 而这两个字段的 0 都是"极端值"（过渡距离 0 = 硬边全雾、
        // 起雾距离 0 = 从脚下起雾），旧存档没有这些键时不该被解读成极端值，故缺键回落到默认值。
        effect.setRampLength(tag.hasKey("rampLength") ? tag.getFloat("rampLength")
                : FogEffect.DEFAULT_RAMP_LENGTH);
        effect.setStartDistance(tag.hasKey("startDistance") ? tag.getFloat("startDistance")
                : FogEffect.DEFAULT_START_DISTANCE);
        effect.setRed(tag.getInteger("red"));
        effect.setGreen(tag.getInteger("green"));
        effect.setBlue(tag.getInteger("blue"));
        effect.setDust(tag.getInteger("dust"));
        return effect;
    }

    private static FogEffect readFogBuf(ByteBuf buf) {
        FogEffect effect = new FogEffect();
        effect.readBufFields(buf);
        effect.setRampLength(buf.readFloat());
        effect.setStartDistance(buf.readFloat());
        effect.setRed(buf.readInt());
        effect.setGreen(buf.readInt());
        effect.setBlue(buf.readInt());
        effect.setDust(buf.readInt());
        return effect;
    }

    private static SkyEffect readSkyNbt(NBTTagCompound tag) {
        SkyEffect effect = new SkyEffect();
        effect.readNbtFields(tag);
        effect.setRed(tag.getInteger("red"));
        effect.setGreen(tag.getInteger("green"));
        effect.setBlue(tag.getInteger("blue"));
        return effect;
    }

    private static SkyEffect readSkyBuf(ByteBuf buf) {
        SkyEffect effect = new SkyEffect();
        effect.readBufFields(buf);
        effect.setRed(buf.readInt());
        effect.setGreen(buf.readInt());
        effect.setBlue(buf.readInt());
        return effect;
    }
}