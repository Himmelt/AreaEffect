package org.soraworld.areaeffect.client.effect;

import org.soraworld.areaeffect.common.effect.EffectTypes;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 客户端效果运行时注册表：typeId -> renderer。
 * 新增效果时在此注册对应的客户端运行时，代理每 tick 依效果类型分发帧。
 */
public final class EffectRenderers {

    private final Map<String, EffectRenderer> renderers = new HashMap<>();

    public EffectRenderers() {
        renderers.put(EffectTypes.TYPE_LIGHTNESS, new LightnessEffectRenderer());
    }

    public EffectRenderer get(String typeId) {
        return renderers.get(typeId);
    }

    /**
     * 已注册的效果类型 id。用于每帧驱动<b>全部</b>渲染器 —— 区域内没有对应效果时按 null 传入，
     * 渲染器便会按"离开该效果"处理（见 {@code ClientProxy#updateClientLight}）。
     * 返回的是内部视图：注册表只在构造期写入，调用方不应修改。
     */
    public Set<String> typeIds() {
        return renderers.keySet();
    }

    public Collection<EffectRenderer> all() {
        return renderers.values();
    }
}