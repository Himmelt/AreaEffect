package org.soraworld.areaeffect.client.effect;

import org.soraworld.areaeffect.common.effect.AreaEffect;

/**
 * 客户端某类效果的运行时。每 tick 由代理以当前区域帧驱动，
 * 效果各自根据目标值/区域变化做平滑过渡并输出。
 */
public interface EffectRenderer {

    /**
     * 处理一帧。
     *
     * @param areaChanged       区域级上下文是否发生变化（进入/离开/切换区域）
     * @param effect            当前生效的该类效果；为 null 表示玩家不在任何区域内
     * @param fallbackDuration  区域外回退到 0 时使用的过渡时长（秒）
     */
    void onFrame(boolean areaChanged, AreaEffect effect, float fallbackDuration);
}