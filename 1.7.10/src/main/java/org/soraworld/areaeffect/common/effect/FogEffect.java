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
 */
public class FogEffect extends AreaEffect {

    /** 尘粒数量上限（个/帧，围绕玩家生成）。 */
    public static final int MAX_DUST = 50;

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
    private int red;
    private int green;
    private int blue;
    private int dust;

    public FogEffect() {
        this(DEFAULT_RAMP_LENGTH, DEFAULT_START_DISTANCE, 40, 80, 50, 0, 1.0F);
    }

    public FogEffect(float rampLength, float startDistance, int red, int green, int blue, int dust, float duration) {
        setRampLength(rampLength);
        setStartDistance(startDistance);
        setRed(red);
        setGreen(green);
        setBlue(blue);
        setDust(dust);
        setDuration(duration);
    }

    @Override
    public AreaEffect copy() {
        FogEffect copy = new FogEffect(getRampLength(), getStartDistance(), getRed(), getGreen(), getBlue(),
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
        tag.setFloat("rampLength", rampLength);
        tag.setFloat("startDistance", startDistance);
        tag.setInteger("red", red);
        tag.setInteger("green", green);
        tag.setInteger("blue", blue);
        tag.setInteger("dust", dust);
    }

    @Override
    public void writeToBuf(ByteBuf buf) {
        writeBufFields(buf);
        buf.writeFloat(rampLength);
        buf.writeFloat(startDistance);
        buf.writeInt(red);
        buf.writeInt(green);
        buf.writeInt(blue);
        buf.writeInt(dust);
    }

    @Override
    public void sanitize() {
        super.sanitize();
        setRampLength(rampLength);
        setStartDistance(startDistance);
        setRed(red);
        setGreen(green);
        setBlue(blue);
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

    public int getRed() {
        return red;
    }

    public void setRed(int red) {
        this.red = clampColor(red);
    }

    public int getGreen() {
        return green;
    }

    public void setGreen(int green) {
        this.green = clampColor(green);
    }

    public int getBlue() {
        return blue;
    }

    public void setBlue(int blue) {
        this.blue = clampColor(blue);
    }

    public int getDust() {
        return dust;
    }

    public void setDust(int dust) {
        this.dust = clampDust(dust);
    }

    /** 颜色分量取值收窄到 [0,255]。 */
    private static int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }

    /** 尘粒数量收窄到 [0, {@link #MAX_DUST}]。 */
    private static int clampDust(int value) {
        return Math.max(0, Math.min(MAX_DUST, value));
    }
}
