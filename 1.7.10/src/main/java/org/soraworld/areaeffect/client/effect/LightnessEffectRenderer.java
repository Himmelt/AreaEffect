package org.soraworld.areaeffect.client.effect;

import org.soraworld.areaeffect.client.handler.LightmapHook;
import org.soraworld.areaeffect.common.effect.AreaEffect;
import org.soraworld.areaeffect.common.effect.LightnessEffect;
import org.soraworld.areaeffect.common.util.GammaCurve;

/**
 * 亮度效果运行时：从当前感知亮度向目标亮度做平滑过渡，并接管光照贴图偏移。
 */
public class LightnessEffectRenderer implements EffectRenderer {

    private double curDL = 0.0D;
    private double fromDL = 0.0D;
    private int elapsed = Integer.MAX_VALUE;
    private double lastTarget = Double.NaN;

    @Override
    public void onFrame(boolean areaChanged, AreaEffect effect, float fallbackDuration) {
        boolean inArea = effect instanceof LightnessEffect;
        double target = inArea ? ((LightnessEffect) effect).getLightness() : 0.0D;
        float seconds = inArea ? ((LightnessEffect) effect).getDuration() : fallbackDuration;
        if (areaChanged || Math.abs(target - lastTarget) > 1.0e-6) {
            fromDL = curDL;
            elapsed = 0;
        }
        int ticks = Math.max(1, Math.round(seconds * 20.0F));
        if (elapsed >= ticks) {
            curDL = target;
        } else {
            curDL = GammaCurve.glide(fromDL, target, elapsed, ticks);
            elapsed++;
        }
        lastTarget = target;
        LightmapHook.setOffset(curDL);
    }
}