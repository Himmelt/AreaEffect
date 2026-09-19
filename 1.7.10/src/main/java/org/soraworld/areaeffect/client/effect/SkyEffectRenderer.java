package org.soraworld.areaeffect.client.effect;

import org.soraworld.areaeffect.client.handler.SkyRenderHandler;
import org.soraworld.areaeffect.common.effect.AreaEffect;
import org.soraworld.areaeffect.common.effect.SkyEffect;
import org.soraworld.areaeffect.common.util.GammaCurve;

/**
 * 天空效果运行时：把目标天空色平滑过渡，并写到 {@link SkyRenderHandler} 供自定义天空渲染器使用。
 *
 * <p>进入区域时颜色向目标过渡；离开时向<b>当时的原版天空色</b>过渡（由
 * {@link SkyRenderHandler#atmosphereRed()} 等每 tick 采样提供 —— 不是硬编码的白昼蓝，
 * 否则夜里离开天空区域会闪一下假天亮），过渡完成后关闭（移除自定义天空渲染器、交还原版）。
 * 颜色按<b>线性光空间</b>混合（见 {@link GammaCurve#mix}），中段不会偏暗偏灰；
 * {@code duration<=0} 瞬间到位。因为仅覆盖天空穹顶，离开/进入都以该渲染器的启停为界。
 */
public class SkyEffectRenderer implements EffectRenderer {

    /** 兜底色（近似原版白昼天蓝）：仅用于采样尚未就绪时的首帧之前。 */
    private static final double FALLBACK_RED = 135.0D / 255.0D;
    private static final double FALLBACK_GREEN = 206.0D / 255.0D;
    private static final double FALLBACK_BLUE = 235.0D / 255.0D;

    private double curR = FALLBACK_RED;
    private double curG = FALLBACK_GREEN;
    private double curB = FALLBACK_BLUE;

    private double targetR = FALLBACK_RED;
    private double targetG = FALLBACK_GREEN;
    private double targetB = FALLBACK_BLUE;

    private boolean anim = false;
    private double fromR = 0.0D;
    private double fromG = 0.0D;
    private double fromB = 0.0D;
    private long animStart = 0L;

    /**
     * 上一帧是否处于接管状态（在区域内，或正在淡出）。用来区分"淡出中"与"已完全退出"：
     * 后者不启动任何过渡，只让色值悄悄跟随原版天空色。
     */
    private boolean active = false;

    @Override
    public void onFrame(boolean areaChanged, AreaEffect effect, float fallbackDuration) {
        boolean inArea = effect instanceof SkyEffect;
        float seconds = inArea ? ((SkyEffect) effect).getDuration() : fallbackDuration;
        if (inArea) {
            SkyEffect sky = (SkyEffect) effect;
            // 0xRRGGBBAA 单整数 → 三个 0..1 分量（低字节是 AA，渲染端暂不用）
            int rgb = sky.getRgb();
            double tr = ((rgb >>> 16) & 255) / 255.0D;
            double tg = ((rgb >>> 8) & 255) / 255.0D;
            double tb = (rgb & 255) / 255.0D;
            if (areaChanged || targetR != tr || targetG != tg || targetB != tb) {
                beginAnim(tr, tg, tb);
            }
        } else if (active) {
            // 淡出中：终点实时跟随"此刻的原版天空色"，但刻意不因目标浮动而重启过渡
            //（from 固定、进度不重置），于是 p→1 时色值正好落在当前的原版天空上，
            // 卸下自定义渲染器时不会跳变；反过来若只取"离开那一刻"的固定快照，
            // 时长较长时收尾就会与原版天空对不上而跳一下。与雾效果"目标未变则不重启"同一考量。
            double ar = SkyRenderHandler.atmosphereRed();
            double ag = SkyRenderHandler.atmosphereGreen();
            double ab = SkyRenderHandler.atmosphereBlue();
            // 刚离开的那一帧（或过渡尚未起跑）才启动一次；之后仅推进终点
            if (!anim || areaChanged) {
                beginAnim(ar, ag, ab);
            }
            targetR = ar;
            targetG = ag;
            targetB = ab;
        } else {
            // 已完全退出：颜色悄悄跟随原版天空色（此时渲染器已卸下、画面上看不见），
            // 下次进入即从"此刻该有的天空"起步，不会从上一次退出时的旧色跳变过来。
            curR = targetR = SkyRenderHandler.atmosphereRed();
            curG = targetG = SkyRenderHandler.atmosphereGreen();
            curB = targetB = SkyRenderHandler.atmosphereBlue();
        }
        step(seconds);
        active = inArea || anim;
        SkyRenderHandler.setState(active, curR, curG, curB);
    }

    /** 以当前色为起点、给定目标色起一段过渡（起点固定，供 {@link #step} 插值）。 */
    private void beginAnim(double tr, double tg, double tb) {
        targetR = tr;
        targetG = tg;
        targetB = tb;
        fromR = curR;
        fromG = curG;
        fromB = curB;
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
            curR = targetR;
            curG = targetG;
            curB = targetB;
            anim = false;
        } else {
            double e = GammaCurve.ease(p);
            // 颜色在线性光空间混合：码值直接插值的中段会偏暗偏灰
            curR = GammaCurve.mix(fromR, targetR, e);
            curG = GammaCurve.mix(fromG, targetG, e);
            curB = GammaCurve.mix(fromB, targetB, e);
        }
    }
}
