package org.soraworld.areaeffect.client.effect;

import org.soraworld.areaeffect.client.handler.FogRenderHandler;
import org.soraworld.areaeffect.common.effect.AreaEffect;
import org.soraworld.areaeffect.common.effect.FogEffect;
import org.soraworld.areaeffect.common.util.GammaCurve;

/**
 * 雾效果运行时：把目标雾浓度与颜色平滑过渡，并写到 {@link FogRenderHandler} 供事件使用。
 *
 * <p>当前密度/颜色随进入/离开区域和目标值变化以真实时间（纳秒）作基准驱动；
 * {@code seconds<=0} 瞬间到位。离开区域时密度渐降至 0、颜色清零（此时雾事件不再接管，交还原版）。
 */
public class FogEffectRenderer implements EffectRenderer {

    /** 视为「没有雾」的密度阈值（与 {@link FogRenderHandler} 对齐）。 */
    private static final double EPSILON = 0.001D;

    private double density = 0.0D;
    private double red = 0.0D;
    private double green = 0.0D;
    private double blue = 0.0D;

    private double tDensity = 0.0D;
    private double tRed = 0.0D;
    private double tGreen = 0.0D;
    private double tBlue = 0.0D;

    private boolean anim = false;
    private double fDensity = 0.0D;
    private double fRed = 0.0D;
    private double fGreen = 0.0D;
    private double fBlue = 0.0D;
    private long animStart = 0L;

    private int dust = 0;

    @Override
    public void onFrame(boolean areaChanged, AreaEffect effect, float fallbackDuration) {
        boolean inArea = effect instanceof FogEffect;
        float seconds = inArea ? ((FogEffect) effect).getDuration() : fallbackDuration;
        if (inArea) {
            FogEffect fog = (FogEffect) effect;
            double td = fog.getDensity();
            double tr = fog.getRed() / 255.0D;
            double tg = fog.getGreen() / 255.0D;
            double tb = fog.getBlue() / 255.0D;
            if (!areaChanged && anim && tDensity == td && tRed == tr && tGreen == tg && tBlue == tb) {
                // 目标未变且已在过渡：仅推进
            } else {
                beginAnim(td, tr, tg, tb);
            }
            dust = fog.getDust();
        } else if (areaChanged) {
            beginAnim(0.0D, 0.0D, 0.0D, 0.0D);
            dust = 0;
        }
        step(seconds);
        boolean on = anim || density > EPSILON;
        FogRenderHandler.setState(on, density, red, green, blue, dust);
    }

    /** 目标值变化时从当前值重新过渡。 */
    private void beginAnim(double td, double tr, double tg, double tb) {
        tDensity = td;
        tRed = tr;
        tGreen = tg;
        tBlue = tb;
        fDensity = density;
        fRed = red;
        fGreen = green;
        fBlue = blue;
        animStart = System.nanoTime();
        anim = true;
    }

    private void step(float seconds) {
        double durationNanos = Math.max(0.0D, seconds) * 1.0e9D;
        boolean instant = durationNanos <= 0.0D;
        if (!anim) {
            return;
        }
        double p = instant ? 1.0D : (System.nanoTime() - animStart) / durationNanos;
        if (p >= 1.0D) {
            density = tDensity;
            red = tRed;
            green = tGreen;
            blue = tBlue;
            anim = false;
        } else {
            double e = GammaCurve.ease(p);
            density = fDensity + (tDensity - fDensity) * e;
            red = fRed + (tRed - fRed) * e;
            green = fGreen + (tGreen - fGreen) * e;
            blue = fBlue + (tBlue - fBlue) * e;
        }
    }
}