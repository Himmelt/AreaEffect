package org.soraworld.areaeffect.util;

/**
 * Perceptual gamma mapping and a duration based transition engine.
 *
 * <p>Minecraft builds its lightmap in {@code EntityRenderer#updateLightmap};
 * after the gamma blend the output is
 * <pre>
 *     out(g) = clamp( (raw * (1 - g) + (1 - (1 - raw)^4) * g) * 0.96 + 0.03, 0, 1 )
 * </pre>
 * where {@code raw} is the scene's base light level (0..1) and {@code g} is
 * {@code GameSettings.gamma}. The eye however does not see {@code out}: it sees
 * {@code out} pushed through the monitor's sRGB electro-optical transfer
 * function and then compressed into CIE L* (perceived lightness, 0 = black,
 * 100 = white). That composite mapping is neither linear nor scene
 * independent, so this class provides:
 * <ul>
 *   <li>the forward chain {@code (raw, gamma) -> lightness} ({@link #perceive}),</li>
 *   <li>the exact inverse {@code (raw, lightness) -> gamma}
 *       ({@link #gammaFromLightness}), which is what lets an area target a
 *       <b>perceived lightness</b> instead of a raw gamma value, and</li>
 *   <li>the perceptual glide used by the duration based transition
 *       ({@link #glide}/{@link #ease}).</li>
 * </ul>
 *
 * <p>All functions take the <b>live</b> scene base light {@code raw}; callers
 * should sample it every tick (e.g. {@code world.getLightBrightness(pos)}), so
 * the perceived rate stays correct at noon and in a pitch black cave alike.
 */
public final class GammaCurve {

    /** Perceptual difference (CIE L*) below which two gammas look identical. */
    private static final double JND = 0.05;

    private GammaCurve() {
    }

    // ------------------------------------------------------------------
    // forward chain: (raw, gamma) -> perceived lightness
    // ------------------------------------------------------------------

    /** Minecraft lightmap output (0..1) for the given base light and gamma. */
    public static double lightmap(double raw, double gamma) {
        double bright = 1.0 - Math.pow(1.0 - raw, 4.0);
        double value = raw * (1.0 - gamma) + bright * gamma;
        return clamp(value * 0.96 + 0.03, 0.0, 1.0);
    }

    /** sRGB code value -> linear luminance (sRGB EOTF). */
    public static double toLinear(double code) {
        return code <= 0.04045 ? code / 12.92 : Math.pow((code + 0.055) / 1.055, 2.4);
    }

    /** Linear luminance -> CIE L* (0..100). */
    public static double lightness(double luminance) {
        double f = luminance > 0.008856 ? Math.cbrt(luminance) : luminance * 7.787 + 16.0 / 116.0;
        return 116.0 * f - 16.0;
    }

    /** Gamma -> perceived lightness (CIE L*, 0..100) in a scene with base light {@code raw}. */
    public static double perceive(double gamma, double raw) {
        return lightness(toLinear(lightmap(raw, gamma)));
    }

    /** sRGB code value (0..1) -> CIE L* (0..100). */
    public static double lightnessFromCode(double code) {
        return lightness(toLinear(code));
    }

    // ------------------------------------------------------------------
    // inverse chain: perceived lightness -> (raw, gamma)
    // ------------------------------------------------------------------

    /** CIE L* (0..100) -> linear luminance. */
    public static double toLuminance(double lightness) {
        double f = (lightness + 16.0) / 116.0;
        double cube = f * f * f;
        return cube > 0.008856 ? cube : lightness / 903.292;
    }

    /** Linear luminance -> sRGB code value (inverse EOTF). */
    public static double toCode(double luminance) {
        return luminance <= 0.0031308 ? luminance * 12.92 : 1.055 * Math.pow(luminance, 1.0 / 2.4) - 0.055;
    }

    /** {@code 1 - (1 - raw)^4 - raw}: how much a unit of gamma shifts the blend. */
    public static double delta(double raw) {
        return 1.0 - Math.pow(1.0 - raw, 4.0) - raw;
    }

    /** Lowest gamma with a visible effect in a scene with base light {@code raw}. */
    public static double liveMinGamma(double raw) {
        double d = delta(raw);
        return d <= 1.0e-9 ? 0.0 : ((-0.03) / 0.96 - raw) / d;
    }

    /** Highest gamma with a visible effect in a scene with base light {@code raw}. */
    public static double liveMaxGamma(double raw) {
        double d = delta(raw);
        return d <= 1.0e-9 ? 0.0 : ((1.0 - 0.03) / 0.96 - raw) / d;
    }

    /**
     * Gamma that makes a scene with base light {@code raw} appear at the given
     * perceived lightness (CIE L*, 0..100). Lightness values beyond what gamma
     * can reach for this scene clamp onto the live range boundary, so the
     * picture simply saturates instead of producing dead values.
     */
    public static double gammaFromLightness(double lightness, double raw) {
        double d = delta(raw);
        if (!(d > 1.0e-9)) {
            return 0.0; // degenerate scene (raw 0 or 1), gamma has no effect
        }
        double code = clamp(toCode(toLuminance(lightness)), 0.0, 1.0);
        return clamp(((code - 0.03) / 0.96 - raw) / d, liveMinGamma(raw), liveMaxGamma(raw));
    }

    // ------------------------------------------------------------------
    // duration based transition
    // ------------------------------------------------------------------

    /** Smooth 0..1 easing (smoothstep). */
    public static double ease(double progress) {
        double t = clamp(progress, 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    /**
     * Perceived lightness on the glide from {@code fromLightness} to
     * {@code toLightness} after {@code elapsedTicks} of a {@code durationTicks}
     * tick transition. Snaps to the target once the remaining difference is
     * below the just noticeable difference.
     */
    public static double glide(double fromLightness, double toLightness, double elapsedTicks, double durationTicks) {
        if (!(Math.abs(toLightness - fromLightness) > JND)) {
            return toLightness;
        }
        double t = ease(elapsedTicks / durationTicks);
        return fromLightness + (toLightness - fromLightness) * t;
    }

    private static double clamp(double value, double min, double max) {
        // NaN and -Infinity land on min, +Infinity lands on max
        return !(value >= min) ? min : (value > max ? max : value);
    }
}
