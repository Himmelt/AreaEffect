package org.soraworld.areaeffect.client.effect;

import org.soraworld.areaeffect.common.effect.EffectTypes;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

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

    public Collection<EffectRenderer> all() {
        return renderers.values();
    }
}