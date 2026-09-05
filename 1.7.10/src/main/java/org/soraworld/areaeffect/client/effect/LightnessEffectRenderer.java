package org.soraworld.areaeffect.client.effect;

import org.soraworld.areaeffect.client.handler.LightmapHook;
import org.soraworld.areaeffect.common.effect.AreaEffect;
import org.soraworld.areaeffect.common.effect.LightnessEffect;
import org.soraworld.areaeffect.common.util.GammaCurve;

/**
 * 亮度效果运行时：区域画面与 vanilla 光照贴图之间的平滑混合。
 *
 * <p>blend 从 0（vanilla 原画）过渡到 1（统一目标亮度）由进入/离开区域触发，
 * 目标亮度变化（如编辑界面实时预览）单独过渡 uL。两个动画都以真实时间
 * （纳秒）为基准，与驱动频率无关；blend 在两端均为精确 vanilla/vanilla
 * 连续衔接，不产生跳变。
 */
public class LightnessEffectRenderer implements EffectRenderer {

    /** 混合系数：0 = vanilla，1 = 统一亮度。 */
    private double blend = 0.0D;
    /** 当前统一亮度（blend>0 时生效）。 */
    private double uL = 0.0D;
    private double lastL = Double.NaN;

    private boolean bAnim = false;
    private double bFrom = 0.0D;
    private double bTo = 0.0D;
    private long bStart = 0L;

    private boolean lAnim = false;
    private double lFrom = 0.0D;
    private double lTo = 0.0D;
    private long lStart = 0L;

    @Override
    public void onFrame(boolean areaChanged, AreaEffect effect, float fallbackDuration) {
        boolean inArea = effect instanceof LightnessEffect;
        float seconds = inArea ? ((LightnessEffect) effect).getDuration() : fallbackDuration;
        if (inArea) {
            double l = ((LightnessEffect) effect).getLightness();
            if (areaChanged) {
                // 进入/切换区域：blend 渐入到 1，亮度过渡到新目标
                if (blend < 1.0D - 1.0e-9D) {
                    bFrom = blend;
                    bTo = 1.0D;
                    bStart = System.nanoTime();
                    bAnim = true;
                }
                startL(l);
            } else if (Math.abs(l - lastL) > 1.0e-6D) {
                // 目标亮度变化（编辑预览/服务端更新）：仅过渡亮度
                startL(l);
            }
            lastL = l;
        } else if (areaChanged) {
            // 离开区域：blend 渐出到 0，uL 保持在最后的亮度上淡出
            bFrom = blend;
            bTo = 0.0D;
            bStart = System.nanoTime();
            bAnim = true;
            lAnim = false;
        }
        double durationNanos = Math.max(0.05D, seconds) * 1.0e9D;
        if (bAnim) {
            double p = (System.nanoTime() - bStart) / durationNanos;
            if (p >= 1.0D) {
                blend = bTo;
                bAnim = false;
            } else {
                blend = bFrom + (bTo - bFrom) * GammaCurve.ease(p);
            }
        }
        if (lAnim) {
            double p = (System.nanoTime() - lStart) / durationNanos;
            if (p >= 1.0D) {
                uL = lTo;
                lAnim = false;
            } else {
                uL = lFrom + (lTo - lFrom) * GammaCurve.ease(p);
            }
        }
        LightmapHook.setOffset(uL, blend);
    }

    private void startL(double l) {
        lFrom = uL;
        lTo = l;
        lStart = System.nanoTime();
        lAnim = true;
    }
}
