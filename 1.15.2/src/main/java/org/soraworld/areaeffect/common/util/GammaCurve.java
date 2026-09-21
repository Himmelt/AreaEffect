package org.soraworld.areaeffect.common.util;

/**
 * sRGB / CIE L* 感知亮度换算与缓动函数，供光照贴图 LUT 与效果过渡使用。
 *
 * <p>本模组的亮度效果接管 vanilla lightmap 的接收端（见客户端
 * {@code LightmapHook}）：把贴图像素按通道经「码值 → 线性亮度 → CIE L*
 * → 目标亮度插值 → 码值」的链路重映射，其中正向
 * {@link #lightnessFromCode} 与逆向 {@link #toLuminance}/{@link #toCode}
 * 均由本类提供；过渡动画使用 {@link #ease}（smoothstep）。
 */
public final class GammaCurve {

    private GammaCurve() {
    }

    // ------------------------------------------------------------------
    // forward chain: sRGB code value -> perceived lightness
    // ------------------------------------------------------------------

    /**
     * sRGB code value -> linear luminance (sRGB EOTF).
     */
    public static double toLinear(double code) {
        return code <= 0.04045 ? code / 12.92 : Math.pow((code + 0.055) / 1.055, 2.4);
    }

    /**
     * Linear luminance -> CIE L* (0..100).
     */
    public static double lightness(double luminance) {
        double f = luminance > 0.008856 ? Math.cbrt(luminance) : luminance * 7.787 + 16.0 / 116.0;
        return 116.0 * f - 16.0;
    }

    /**
     * sRGB code value (0..1) -> CIE L* (0..100).
     */
    public static double lightnessFromCode(double code) {
        return lightness(toLinear(code));
    }

    // ------------------------------------------------------------------
    // inverse chain: perceived lightness -> sRGB code value
    // ------------------------------------------------------------------

    /**
     * CIE L* (0..100) -> linear luminance.
     */
    public static double toLuminance(double lightness) {
        double f = (lightness + 16.0) / 116.0;
        double cube = f * f * f;
        return cube > 0.008856 ? cube : lightness / 903.292;
    }

    /**
     * Linear luminance -> sRGB code value (inverse EOTF).
     */
    public static double toCode(double luminance) {
        return luminance <= 0.0031308 ? luminance * 12.92 : 1.055 * Math.pow(luminance, 1.0 / 2.4) - 0.055;
    }

    // ------------------------------------------------------------------
    // color mix
    // ------------------------------------------------------------------

    /**
     * 两个 sRGB 码值（0..1）按 t 混合，**在线性光空间里插值**再回到码值空间。
     *
     * <p>直接对码值做线性插值会让中间色偏暗偏灰（sRGB 编码不是线性的）；线性光空间混合更接近
     * 物理上的混色，颜色过渡的中段亮度不会掉下来。用于雾色 / 天空色这类颜色过渡。
     */
    public static double mix(double from, double to, double t) {
        double a = toLinear(from);
        return toCode(a + (toLinear(to) - a) * t);
    }

    // ------------------------------------------------------------------
    // transition easing
    // ------------------------------------------------------------------

    /**
     * Smooth 0..1 easing (smoothstep).
     */
    public static double ease(double progress) {
        double t = clamp(progress, 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    private static double clamp(double value, double min, double max) {
        // NaN and -Infinity land on min, +Infinity lands on max
        return !(value >= min) ? min : (value > max ? max : value);
    }
}
