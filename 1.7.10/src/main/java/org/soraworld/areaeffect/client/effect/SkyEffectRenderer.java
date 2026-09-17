package org.soraworld.areaeffect.client.effect;

import org.soraworld.areaeffect.client.handler.SkyRenderHandler;
import org.soraworld.areaeffect.common.effect.AreaEffect;
import org.soraworld.areaeffect.common.effect.SkyEffect;
import org.soraworld.areaeffect.common.util.GammaCurve;

/**
 * 天空效果运行时：把目标天空色平滑过渡，并写到 {@link SkyRenderHandler} 供自定义天空渲染器使用。
 *
 * <p>进入区域时颜色向目标过渡；离开时向「默认天蓝」过渡，过渡完成后关闭（移除自定义天空渲染器、交还原版）。
 * {@code duration<=0} 瞬间到位。因为仅覆盖天空穹顶，离开/进入都以该渲染器的启停为界。
 */
public class SkyEffectRenderer implements EffectRenderer {

    /** 离开区域时淡出的目标（近似原版白昼天蓝），伽马之上足够自然即可。 */
    private static final double EXIT_RED = 135.0D / 255.0D;
    private static final double EXIT_GREEN = 206.0D / 255.0D;
    private static final double EXIT_BLUE = 235.0D / 255.0D;

    private double curR = EXIT_RED;
    private double curG = EXIT_GREEN;
    private double curB = EXIT_BLUE;

    private double targetR = EXIT_RED;
    private double targetG = EXIT_GREEN;
    private double targetB = EXIT_BLUE;

    private boolean anim = false;
    private double fromR = 0.0D;
    private double fromG = 0.0D;
    private double fromB = 0.0D;
    private long animStart = 0L;

    @Override
    public void onFrame(boolean areaChanged, AreaEffect effect, float fallbackDuration) {
        boolean inArea = effect instanceof SkyEffect;
        float seconds = inArea ? ((SkyEffect) effect).getDuration() : fallbackDuration;
        double tr = EXIT_RED;
        double tg = EXIT_GREEN;
        double tb = EXIT_BLUE;
        if (inArea) {
            SkyEffect sky = (SkyEffect) effect;
            tr = sky.getRed() / 255.0D;
            tg = sky.getGreen() / 255.0D;
            tb = sky.getBlue() / 255.0D;
        }
        if (areaChanged || targetR != tr || targetG != tg || targetB != tb) {
            targetR = tr;
            targetG = tg;
            targetB = tb;
            fromR = curR;
            fromG = curG;
            fromB = curB;
            animStart = System.nanoTime();
            anim = true;
        }
        step(seconds);
        // 离开区域且过渡完成后关闭；进入/过渡期间一直处于激活（渲染器保持安装）
        boolean stillAnimating = anim;
        boolean active = inArea || stillAnimating;
        SkyRenderHandler.setState(active, curR, curG, curB);
    }

    private void step(float seconds) {
        double durationNanos = Math.max(0.0D, seconds) * 1.0e9D;
        boolean instant = durationNanos <= 0.0D;
        if (!anim) {
            return;
        }
        double p = instant ? 1.0D : (System.nanoTime() - animStart) / durationNanos;
        if (p >= 1.0D) {
            curR = targetR;
            curG = targetG;
            curB = targetB;
            anim = false;
        } else {
            double e = GammaCurve.ease(p);
            curR = fromR + (targetR - fromR) * e;
            curG = fromG + (targetG - fromG) * e;
            curB = fromB + (targetB - fromB) * e;
        }
    }
}