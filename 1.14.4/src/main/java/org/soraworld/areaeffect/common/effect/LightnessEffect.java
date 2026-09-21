package org.soraworld.areaeffect.common.effect;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundNBT;

/**
 * 亮度效果：把区域整体渲染为设定的感知亮度（CIE L*），并支持秒级过渡时长。
 * 过渡时长 {@code duration} 为公共字段（见 {@link AreaEffect}）。
 */
public class LightnessEffect extends AreaEffect {

    private float lightness;

    public LightnessEffect() {
        this(100.0F, 1.0F);
    }

    public LightnessEffect(float lightness, float duration) {
        this.lightness = floatIsNaN(lightness) ? 100.0F : Math.max(0.0F, Math.min(100.0F, lightness));
        setDuration(duration);
    }

    @Override
    public AreaEffect copy() {
        LightnessEffect copy = new LightnessEffect(lightness, getDuration());
        copyCommonTo(copy);
        return copy;
    }

    @Override
    public String typeId() {
        return EffectTypes.TYPE_LIGHTNESS;
    }

    @Override
    public void writeToNbt(CompoundNBT tag) {
        writeNbtFields(tag);
        tag.putFloat("lightness", lightness);
    }

    @Override
    public void writeToBuf(ByteBuf buf) {
        writeBufFields(buf);
        buf.writeFloat(lightness);
    }

    @Override
    public void sanitize() {
        super.sanitize();
        lightness = floatIsNaN(lightness) ? 100.0F : Math.max(0.0F, Math.min(100.0F, lightness));
    }

    public float getLightness() {
        return lightness;
    }

    public void setLightness(float lightness) {
        this.lightness = floatIsNaN(lightness) ? 100.0F : Math.max(0.0F, Math.min(100.0F, lightness));
    }

    private static boolean floatIsNaN(float value) {
        return Float.isNaN(value);
    }
}
