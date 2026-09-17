package org.soraworld.areaeffect.common.effect;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;

/**
 * 天空效果：把区域内的天空穹顶染成自定义色（保留太阳/月亮/星空）。
 * 过渡时长 {@code duration} 为公共字段（见 {@link AreaEffect}）。
 */
public class SkyEffect extends AreaEffect {

    private int red;
    private int green;
    private int blue;

    public SkyEffect() {
        this(135, 206, 235, 1.0F);
    }

    public SkyEffect(int red, int green, int blue, float duration) {
        setRed(red);
        setGreen(green);
        setBlue(blue);
        setDuration(duration);
    }

    @Override
    public AreaEffect copy() {
        SkyEffect copy = new SkyEffect(getRed(), getGreen(), getBlue(), getDuration());
        copyCommonTo(copy);
        return copy;
    }

    @Override
    public String typeId() {
        return EffectTypes.TYPE_SKY;
    }

    @Override
    public void writeToNbt(NBTTagCompound tag) {
        writeNbtFields(tag);
        tag.setInteger("red", red);
        tag.setInteger("green", green);
        tag.setInteger("blue", blue);
    }

    @Override
    public void writeToBuf(ByteBuf buf) {
        writeBufFields(buf);
        buf.writeInt(red);
        buf.writeInt(green);
        buf.writeInt(blue);
    }

    @Override
    public void sanitize() {
        super.sanitize();
        setRed(red);
        setGreen(green);
        setBlue(blue);
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

    /** 颜色分量取值收窄到 [0,255]。 */
    private static int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }
}