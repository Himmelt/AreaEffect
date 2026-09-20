package org.soraworld.areaeffect.common.effect;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;

/**
 * 天空效果：把区域内的天空穹顶染成自定义色（保留太阳/月亮/星空）。
 * 过渡时长 {@code duration} 为公共字段（见 {@link AreaEffect}）。
 *
 * <p><b>颜色用单个整数存储与传输</b>，格式 {@code 0xRRGGBBAA}（低字节是 AA 透明度，当前恒 FF），
 * 与 {@code GuiTheme} 填充色常量同一约定；渲染端按需取 RGB 分量，要 ARGB 时经 {@code GuiTheme#argb} 换算。
 * UI 侧由三条 HSV 滑条（色相/饱和度/明度）编辑，打包成 24 位 RGB 后经 {@link #setRgb} 写回，
 * {@code AA} 字节原样保留。
 */
public class SkyEffect extends AreaEffect {

    /** 默认色（近似原版白昼天蓝），0xRRGGBBAA。 */
    public static final int DEFAULT_COLOR = 0x87CEEBFF;

    /** 颜色：{@code 0xRRGGBBAA} 单整数存储/传输（NBT 键 {@code color}）。 */
    private int color;

    public SkyEffect() {
        this(DEFAULT_COLOR, 1.0F);
    }

    public SkyEffect(int color, float duration) {
        setColor(color);
        setDuration(duration);
    }

    @Override
    public AreaEffect copy() {
        SkyEffect copy = new SkyEffect(getColor(), getDuration());
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
        tag.setInteger("color", color);
    }

    @Override
    public void writeToBuf(ByteBuf buf) {
        writeBufFields(buf);
        buf.writeInt(color);
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
}
