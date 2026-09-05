package org.soraworld.areaeffect.common.effect;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import org.soraworld.areaeffect.common.util.GammaCurve;

/**
 * 亮度效果：把区域整体渲染为设定的感知亮度（CIE L*），并支持秒级过渡时长。
 */
public class LightnessEffect extends AreaEffect {

    private float lightness;
    private float duration;

    public LightnessEffect() {
        this(90.0F, 1.0F);
    }

    public LightnessEffect(float lightness, float duration) {
        this.lightness = floatIsNaN(lightness) ? 90.0F : Math.max(0.0F, Math.min(100.0F, lightness));
        this.duration = !(duration > 0.0F) ? 1.0F : Math.min(60.0F, duration);
    }

    @Override
    public String typeId() {
        return EffectTypes.TYPE_LIGHTNESS;
    }

    @Override
    public void writeToNbt(NBTTagCompound tag) {
        tag.setFloat("lightness", lightness);
        tag.setFloat("duration", duration);
    }

    @Override
    public void writeToBuf(ByteBuf buf) {
        buf.writeFloat(lightness);
        buf.writeFloat(duration);
    }

    @Override
    public void sanitize() {
        lightness = floatIsNaN(lightness) ? 90.0F : Math.max(0.0F, Math.min(100.0F, lightness));
        duration = !(duration > 0.0F) ? 1.0F : Math.min(60.0F, duration);
    }

    public float getLightness() {
        return lightness;
    }

    public void setLightness(float lightness) {
        this.lightness = floatIsNaN(lightness) ? 90.0F : Math.max(0.0F, Math.min(100.0F, lightness));
    }

    public float getDuration() {
        return duration;
    }

    public void setDuration(float duration) {
        this.duration = !(duration > 0.0F) ? 1.0F : Math.min(60.0F, duration);
    }

    private static boolean floatIsNaN(float value) {
        return Float.isNaN(value);
    }
}