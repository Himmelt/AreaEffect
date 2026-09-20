package org.soraworld.areaeffect.common.effect;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;

/**
 * 雾效果：把区域内的视野雾染成自定义色，可附带雾中漂浮尘粒。
 *
 * <p><b>距离模型</b>：{@link #getStartDistance() 起雾距离} 之内完全无雾；从该距离之外开始，
 * 按 {@link #getRampLength() 过渡距离} 决定的线性梯度加雾，走完过渡距离即 100% 雾色
 * （{@code 覆盖率(z) = clamp((z − 起雾距离) ÷ 过渡距离, 0, 1)}）。
 * 过渡距离取 0 表示<b>没有过渡</b>：跨过起雾距离就是全雾（硬边）；渲染时按 {@link #MIN_RAMP_LENGTH}
 * 的极小斜坡处理（GL_LINEAR 的 {@code FOG_START == FOG_END} 是未定义行为，不能真写 0）。
 * 过渡时长 {@code duration} 为公共字段（见 {@link AreaEffect}）。
 *
 * <p><b>颜色用单个整数存储与传输</b>，格式 {@code 0xRRGGBBAA}（低字节是 AA 透明度，当前恒 FF），
 * 与 {@link SkyEffect} 同一约定；UI 侧由三条 HSV 滑条编辑，打包成 24 位 RGB 后经 {@link #setRgb} 写回。
 */
public class FogEffect extends AreaEffect {

    /** 尘粒数量上限（个/帧，围绕玩家生成）。 */
    public static final int MAX_DUST = 50;

    /** 默认色（深绿雾），0xRRGGBBAA。 */
    public static final int DEFAULT_COLOR = 0x285032FF;

    /** 起雾距离上限（米）。 */
    public static final float MAX_START_DISTANCE = 256.0F;

    /** 过渡距离上限（米）：再长在视距内也看不出雾，约合"极淡薄雾"。 */
    public static final float MAX_RAMP_LENGTH = 256.0F;

    /**
     * 过渡距离的下界（米）：0 表示"没有过渡"（硬边），渲染时按这个极小值处理 ——
     * {@code GL_LINEAR} 的 {@code FOG_START == FOG_END} 是未定义行为，不能真写 0；
     * 而 0.2 米的斜坡在画面上与硬边没有区别。它同时充当滑条等比刻度的下界。
     */
    public static final float MIN_RAMP_LENGTH = 0.2F;

    /** 过渡距离默认值（米）：相当于"16 米起雾、再过 7 米全白"。 */
    public static final float DEFAULT_RAMP_LENGTH = 7.0F;

    /** 起雾距离默认值（米）。 */
    public static final float DEFAULT_START_DISTANCE = 16.0F;

    /** 过渡距离（米）：起雾距离之外再过这么远即 100% 雾色；0 = 无过渡（硬边）。 */
    private float rampLength;
    /** 起雾距离（米）：该距离内完全无雾。 */
    private float startDistance;
    /** 颜色：{@code 0xRRGGBBAA} 单整数存储/传输（NBT 键 {@code color}）。 */
    private int color;
    private int dust;

    public FogEffect() {
        this(DEFAULT_RAMP_LENGTH, DEFAULT_START_DISTANCE, DEFAULT_COLOR, 0, 1.0F);
    }

    public FogEffect(float rampLength, float startDistance, int color, int dust, float duration) {
        setRampLength(rampLength);
        setStartDistance(startDistance);
        setColor(color);
        setDust(dust);
        setDuration(duration);
    }

    @Override
    public AreaEffect copy() {
        FogEffect copy = new FogEffect(getRampLength(), getStartDistance(), getColor(),
                getDust(), getDuration());
        copyCommonTo(copy);
        return copy;
    }

    @Override
    public String typeId() {
        return EffectTypes.TYPE_FOG;
    }

    @Override
    public void writeToNbt(NBTTagCompound tag) {
        writeNbtFields(tag);
        tag.putFloat("rampLength", rampLength);
        tag.putFloat("startDistance", startDistance);
        tag.putInt("color", color);
        tag.putInt("dust", dust);
    }

    @Override
    public void writeToBuf(ByteBuf buf) {
        writeBufFields(buf);
        buf.writeFloat(rampLength);
        buf.writeFloat(startDistance);
        buf.writeInt(color);
        buf.writeInt(dust);
    }

    @Override
    public void sanitize() {
        super.sanitize();
        setRampLength(rampLength);
        setStartDistance(startDistance);
        setDust(dust);
    }

    public float getRampLength() {
        return rampLength;
    }

    /** 过渡距离收窄到 [0, {@link #MAX_RAMP_LENGTH}]；NaN 回落为 {@link #DEFAULT_RAMP_LENGTH}。 */
    public void setRampLength(float rampLength) {
        this.rampLength = Float.isNaN(rampLength) ? DEFAULT_RAMP_LENGTH
                : Math.max(0.0F, Math.min(MAX_RAMP_LENGTH, rampLength));
    }

    public float getStartDistance() {
        return startDistance;
    }

    public void setStartDistance(float startDistance) {
        this.startDistance = Float.isNaN(startDistance) ? 0.0F
                : Math.max(0.0F, Math.min(MAX_START_DISTANCE, startDistance));
    }

    /** 完整颜色（含 AA 透明度字节），供 NBT / 网络读写。 */
    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
    }

    /** 24 位 RGB 视图（{@code 0xRRGGBB}），UI 取色用；写回时保留 AA 字节。 */
    public int getRgb() {
        return (color >>> 8) & 0xFFFFFF;
    }

    public void setRgb(int rgb) {
        color = ((rgb & 0xFFFFFF) << 8) | (color & 0xFF);
    }

    public int getDust() {
        return dust;
    }

    public void setDust(int dust) {
        this.dust = clampDust(dust);
    }

    /** 尘粒数量收窄到 [0, {@link #MAX_DUST}]。 */
    private static int clampDust(int value) {
        return Math.max(0, Math.min(MAX_DUST, value));
    }
}
