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
                // 雾效果默认：深绿雾色（0x285032FF）、16 米起雾、再过 7 米全白、无尘粒、过渡 1 秒
                return new FogEffect(7.0F, 16.0F, FogEffect.DEFAULT_COLOR, 0, 1.0F);
            case TYPE_SKY:
                // 天空效果默认：近似原版白昼天蓝（0x87CEEBFF）、过渡 1 秒
                return new SkyEffect(SkyEffect.DEFAULT_COLOR, 1.0F);
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
     * 把单个效果写入缓冲：{@code type} + <b>负载长度</b> + 负载。写效果一律走这里，
     * 不要自己拼 {@code writeString(type) + writeToBuf(...)}。
     *
     * <p>长度前缀是"读侧遇到未知类型也能安全跳过"的前提：效果各字段长度可变，读侧一旦
     * 不认识 {@code typeId}，就只有这个长度能让它把该效果的字节整段跳过；否则会停在该效果
     * 负载的中间，同包内后续元素全部错位（形状侧 {@code ShapeTypes.fromBuf} 靠"无论认不认识
     * 都把 type/锚点/closed 读完"达到同一目的，而效果侧做不到按固定字段数读完）。
     */
    public static void writeBuf(ByteBuf buf, AreaEffect effect) {
        writeString(buf, effect.typeId());
        buf.writeInt(0); // 长度占位，负载写完回填
        int payloadStart = buf.writerIndex();
        effect.writeToBuf(buf);
        buf.setInt(payloadStart - 4, buf.writerIndex() - payloadStart);
    }

    /**
     * 从网络缓冲反序列化一个效果（与 {@link #writeBuf} 对应，前置 type 字符串与负载长度）。
     *
     * <p>无论能否识别类型，返回时缓冲区都停在<b>该效果的末尾</b>，即 {@code Area.fromByteBuf}
     * 契约里要求的"元素边界"：
     * <ul>
     *   <li>未知 {@code typeId}：按长度字段整段跳过负载，返回 null；</li>
     *   <li>已知类型但实际读到的长度与长度字段不一致（两端版本不一致）：以长度字段为准对齐，
     *       于是"新端多写字段、老端少读"仍能继续解析同包内的后续元素。</li>
     * </ul>
     */
    public static AreaEffect fromBuf(ByteBuf buf) {
        String type = readString(buf);
        int payloadLength = buf.readInt();
        if (payloadLength < 0 || payloadLength > buf.readableBytes()) {
            // 长度字段自身不可信（畸形包/截断包）：无从安全跳过，抛出让调用方丢弃整包
            throw new IllegalArgumentException("invalid effect payload length " + payloadLength);
        }
        int payloadEnd = buf.readerIndex() + payloadLength;
        AreaEffect effect = readPayload(type, buf);
        if (buf.readerIndex() != payloadEnd) {
            buf.readerIndex(payloadEnd);
        }
        return effect;
    }

    /** 按 typeId 分派读取负载；未知类型返回 null 且不消费任何字节（由 {@link #fromBuf} 按长度跳过）。 */
    private static AreaEffect readPayload(String type, ByteBuf buf) {
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
     *
     * <p>长度前缀是 {@code short}（上限 65535 字节）。写侧先截断到上限，避免超长字符串
     * 让 {@code writeShort} 静默截断长度字段、读侧按错误偏移解析。当前调用点不可达，仅作协议层兜底。
     */
    public static void writeString(ByteBuf buf, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 0xFFFF) {
            bytes = Arrays.copyOf(bytes, 0xFFFF);
        }
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
        // 1.13+ NBT 键存在判定改名为 contains。
        effect.setRampLength(tag.contains("rampLength") ? tag.getFloat("rampLength")
                : FogEffect.DEFAULT_RAMP_LENGTH);
        effect.setStartDistance(tag.contains("startDistance") ? tag.getFloat("startDistance")
                : FogEffect.DEFAULT_START_DISTANCE);
        effect.setColor(tag.contains("color") ? tag.getInteger("color") : FogEffect.DEFAULT_COLOR);
        effect.setDust(tag.getInteger("dust"));
        return effect;
    }

    private static FogEffect readFogBuf(ByteBuf buf) {
        FogEffect effect = new FogEffect();
        effect.readBufFields(buf);
        effect.setRampLength(buf.readFloat());
        effect.setStartDistance(buf.readFloat());
        effect.setColor(buf.readInt());
        effect.setDust(buf.readInt());
        return effect;
    }

    private static SkyEffect readSkyNbt(NBTTagCompound tag) {
        SkyEffect effect = new SkyEffect();
        effect.readNbtFields(tag);
        effect.setColor(tag.contains("color") ? tag.getInteger("color") : SkyEffect.DEFAULT_COLOR);
        return effect;
    }

    private static SkyEffect readSkyBuf(ByteBuf buf) {
        SkyEffect effect = new SkyEffect();
        effect.readBufFields(buf);
        effect.setColor(buf.readInt());
        return effect;
    }
}
