package org.soraworld.areaeffect.client.effect;

import org.soraworld.areaeffect.client.handler.FogRenderHandler;
import org.soraworld.areaeffect.common.effect.AreaEffect;
import org.soraworld.areaeffect.common.effect.FogEffect;
import org.soraworld.areaeffect.common.util.GammaCurve;

/**
 * 雾效果运行时：把目标「覆盖率坡度、起雾距离、雾色」平滑过渡，写到 {@link FogRenderHandler} 供事件使用。
 *
 * <p>内部用<b>坡度</b>（覆盖率/米 = 1 ÷ 过渡距离）表示"雾有多浓"：坡度 → 0 即雾散尽，越大斜坡越陡
 * （过渡距离 0 = 硬边，按 {@link FogEffect#MIN_RAMP_LENGTH} 的极小斜坡处理）。
 *
 * <p><b>过渡规则</b> —— 进出"无雾"这一对状态只有覆盖率一个旋钮，颜色只在雾已可见时才过渡：
 * <ul>
 *   <li><b>从无雾进入</b>：覆盖率 0 → 目标；雾色直接取目标值（此刻覆盖率≈0，画面上看不见这次赋值）。
 *       于是"由淡到浓"完全由覆盖率一条曲线决定，感知速度正好等于设定的过渡时长。</li>
 *   <li><b>雾 A → 雾 B（或区域内改参数）</b>：覆盖率、起雾距离、雾色一起过渡，
 *       颜色在<b>线性光空间</b>混合（码值直接插值中段会偏暗偏灰）。</li>
 *   <li><b>离开区域</b>：覆盖率 → 0，起雾距离与雾色都保持不动 —— 雾是"变薄"，不是"变淡"；
 *       完全淡出后颜色才悄悄跟随大气色（此时已不接管雾事件，画面上看不见）。</li>
 * </ul>
 */
public class FogEffectRenderer implements EffectRenderer {

    /** 视为「没雾」的最小坡度（覆盖率/米），与 {@link FogRenderHandler} 的阈值保持一致。 */
    private static final double EPSILON = 1.0e-4D;

    private double slope = 0.0D;
    private double start = 0.0D;
    private double red = 0.0D;
    private double green = 0.0D;
    private double blue = 0.0D;

    private double tSlope = 0.0D;
    private double tStart = 0.0D;
    private double tRed = 0.0D;
    private double tGreen = 0.0D;
    private double tBlue = 0.0D;

    private double fSlope = 0.0D;
    private double fStart = 0.0D;
    private double fRed = 0.0D;
    private double fGreen = 0.0D;
    private double fBlue = 0.0D;

    private boolean anim = false;
    private long animStart = 0L;

    private int dust = 0;

    @Override
    public void onFrame(boolean areaChanged, AreaEffect effect, float fallbackDuration) {
        boolean inArea = effect instanceof FogEffect;
        float seconds = inArea ? ((FogEffect) effect).getDuration() : fallbackDuration;
        // 本帧开始时的可见状态：只有"雾已经看得见"时才让雾色参与过渡
        boolean visible = slope > EPSILON;
        if (inArea) {
            FogEffect fog = (FogEffect) effect;
            dust = fog.getDust();
            double slopeTarget = 1.0D / Math.max(FogEffect.MIN_RAMP_LENGTH, fog.getRampLength());
            double startTarget = fog.getStartDistance();
            // 0xRRGGBBAA 单整数 → 三个 0..1 分量（低字节是 AA，渲染端暂不用）
            int rgb = fog.getRgb();
            double r = ((rgb >>> 16) & 255) / 255.0D;
            double g = ((rgb >>> 8) & 255) / 255.0D;
            double b = (rgb & 255) / 255.0D;
            if (visible) {
                // 雾已经看得见（A→B 或区域内改参数）：三个量一起过渡
                if (areaChanged || slopeTarget != tSlope || startTarget != tStart
                        || r != tRed || g != tGreen || b != tBlue) {
                    beginAnim(slopeTarget, startTarget, r, g, b);
                }
            } else {
                // 从"没有雾"进来：雾色直接摆到目标（此刻覆盖率≈0，这次赋值看不见），
                // 于是本次过渡只有覆盖率在动 —— 正好是"严格按设定时长变浓"。
                red = tRed = r;
                green = tGreen = g;
                blue = tBlue = b;
                if (areaChanged || slopeTarget != tSlope || startTarget != tStart) {
                    beginAnim(slopeTarget, startTarget, r, g, b);
                }
            }
        } else if (visible) {
            // 淡出中：坡度降到 0，起雾距离与雾色都保持不动（雾是"变薄"，不是"变淡"）
            dust = 0;
            if (areaChanged || tSlope != 0.0D) {
                beginAnim(0.0D, start, red, green, blue);
            }
        } else {
            // 已完全淡出：颜色悄悄跟随大气色（此时不接管雾事件，画面上看不见）
            dust = 0;
            slope = tSlope = 0.0D;
            red = tRed = FogRenderHandler.atmosphereRed();
            green = tGreen = FogRenderHandler.atmosphereGreen();
            blue = tBlue = FogRenderHandler.atmosphereBlue();
        }
        step(seconds);
        FogRenderHandler.setState(slope > EPSILON, slope, start, red, green, blue, dust);
    }

    /** 目标变化（或刚跨区域）时从当前值重新过渡；目标未变则不动，正在跑的那次过渡继续推进。 */
    private void beginAnim(double slopeTarget, double startTarget, double r, double g, double b) {
        tSlope = slopeTarget;
        tStart = startTarget;
        tRed = r;
        tGreen = g;
        tBlue = b;
        fSlope = slope;
        fStart = start;
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
            slope = tSlope;
            start = tStart;
            red = tRed;
            green = tGreen;
            blue = tBlue;
            anim = false;
        } else {
            double e = GammaCurve.ease(p);
            slope = fSlope + (tSlope - fSlope) * e;
            start = fStart + (tStart - fStart) * e;
            // 颜色在线性光空间混合：码值直接插值的中段会偏暗偏灰
            red = GammaCurve.mix(fRed, tRed, e);
            green = GammaCurve.mix(fGreen, tGreen, e);
            blue = GammaCurve.mix(fBlue, tBlue, e);
        }
    }
}
