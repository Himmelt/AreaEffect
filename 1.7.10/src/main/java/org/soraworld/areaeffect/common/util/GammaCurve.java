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
