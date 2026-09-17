package org.soraworld.areaeffect.common.effect;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;

/**
 * 雾效果：把区域内的视野雾染成自定义色并改变浓度，可附带雾中漂浮尘粒。
 * 过渡时长 {@code duration} 为公共字段（见 {@link AreaEffect}）。
 */
public class FogEffect extends AreaEffect {

    /** 尘粒数量上限（个/帧，围绕玩家生成）。 */
    public static final int MAX_DUST = 50;

    private float density;
    private int red;
    private int green;
    private int blue;
    private int dust;

    public FogEffect() {
        this(0.5F, 40, 80, 50, 0, 1.0F);
    }

    public FogEffect(float density, int red, int green, int blue, int dust, float duration) {
        setDensity(density);
        setRed(red);
        setGreen(green);
        setBlue(blue);
        setDust(dust);
        setDuration(duration);
    }

    @Override
    public AreaEffect copy() {
        FogEffect copy = new FogEffect(getDensity(), getRed(), getGreen(), getBlue(), getDust(), getDuration());
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
        tag.setFloat("density", density);
        tag.setInteger("red", red);
        tag.setInteger("green", green);
        tag.setInteger("blue", blue);
        tag.setInteger("dust", dust);
    }

    @Override
    public void writeToBuf(ByteBuf buf) {
        writeBufFields(buf);
        buf.writeFloat(density);
        buf.writeInt(red);
        buf.writeInt(green);
        buf.writeInt(blue);
        buf.writeInt(dust);
    }

    @Override
    public void sanitize() {
        super.sanitize();
        setDensity(density);
        setRed(red);
        setGreen(green);
        setBlue(blue);
        setDust(dust);
    }

    public float getDensity() {
        return density;
    }

    public void setDensity(float density) {
        this.density = Float.isNaN(density) ? 0.5F : Math.max(0.0F, Math.min(1.0F, density));
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