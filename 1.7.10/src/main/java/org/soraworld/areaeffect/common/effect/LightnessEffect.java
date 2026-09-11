package org.soraworld.areaeffect.common.effect;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import org.soraworld.areaeffect.common.util.GammaCurve;

/**
 * 亮度效果：把区域整体渲染为设定的感知亮度（CIE L*），并支持秒级过渡时长。
 */
public class LightnessEffect extends AreaEffect {

    /**
     * 过渡时长下限（秒）：闭区间 [0,60] 的下界。<b>0 表示不过渡、亮度瞬间到位</b>
     * （由客户端 {@code LightnessEffectRenderer} 按 instant 处理）。与 README/GUI 滑条一致。
     */
    public static final float MIN_DURATION = 0.0F;
    /** 过渡时长上限（秒）。 */
    public static final float MAX_DURATION = 60.0F;

    private float lightness;
    private float duration;

    public LightnessEffect() {
        this(100.0F, 1.0F);
    }

    public LightnessEffect(float lightness, float duration) {
        this.lightness = floatIsNaN(lightness) ? 100.0F : Math.max(0.0F, Math.min(100.0F, lightness));
        this.duration = clampDuration(duration);
    }

    /**
     * 时长边界处理：NaN 回落默认 1 秒，其余收窄到闭区间
     * [{@link #MIN_DURATION}, {@link #MAX_DURATION}] = [0,60]（负数夹到 0，+∞ 夹到 60）。
     * 下限 0 是合法值，表示"不过渡、瞬间到位"，故不再像旧版那样把 ≤0 一律回落成 1 秒。
     */
    private static float clampDuration(float duration) {
        return floatIsNaN(duration) ? 1.0F : Math.max(MIN_DURATION, Math.min(MAX_DURATION, duration));
    }

    @Override
    public AreaEffect copy() {
        LightnessEffect copy = new LightnessEffect(lightness, duration);
        copy.setWeight(getWeight());
        return copy;
    }

    @Override
    public String typeId() {
        return EffectTypes.TYPE_LIGHTNESS;
    }

    @Override
    public void writeToNbt(NBTTagCompound tag) {
        writeNbtFields(tag);
        tag.setFloat("lightness", lightness);
        tag.setFloat("duration", duration);
    }

    @Override
    public void writeToBuf(ByteBuf buf) {
        writeBufFields(buf);
        buf.writeFloat(lightness);
        buf.writeFloat(duration);
    }

    @Override
    public void sanitize() {
        super.sanitize();
        lightness = floatIsNaN(lightness) ? 100.0F : Math.max(0.0F, Math.min(100.0F, lightness));
        duration = clampDuration(duration);
    }

    public float getLightness() {
        return lightness;
    }

    public void setLightness(float lightness) {
        this.lightness = floatIsNaN(lightness) ? 100.0F : Math.max(0.0F, Math.min(100.0F, lightness));
    }

    public float getDuration() {
        return duration;
    }

    public void setDuration(float duration) {
        this.duration = clampDuration(duration);
    }

    private static boolean floatIsNaN(float value) {
        return Float.isNaN(value);
    }
}