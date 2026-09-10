package org.soraworld.areaeffect.common.network;

import io.netty.buffer.ByteBuf;
import org.soraworld.areaeffect.common.effect.AreaEffect;
import org.soraworld.areaeffect.common.effect.EffectTypes;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端 → 服务端：将某个区域的整组效果写回（服务端仍需再次鉴权）。
 */
public class MessageSetProps implements IPacket {

    public int dim;
    public int id;
    public String remark = "";
    public List<AreaEffect> effects = new ArrayList<>();

    public MessageSetProps() {
    }

    public MessageSetProps(int dim, int id, String remark, List<AreaEffect> effects) {
        this.dim = dim;
        this.id = id;
        this.remark = remark == null ? "" : remark;
        this.effects = effects;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(dim);
        buf.writeInt(id);
        EffectTypes.writeString(buf, remark);
        // 与读侧同源的上限（见 Area.EFFECT_MAX）：写出条数不得多于读回条数
        int count = Math.min(effects.size(), Area.EFFECT_MAX);
        buf.writeInt(count);
        for (int i = 0; i < count; i++) {
            AreaEffect effect = effects.get(i);
            EffectTypes.writeString(buf, effect.typeId());
            effect.writeToBuf(buf);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        dim = buf.readInt();
        id = buf.readInt();
        remark = EffectTypes.readString(buf);
        // 效果数量收窄到合理上限，防恶意包一次性申请海量对象（与写入侧共用 Area.EFFECT_MAX）
        int size = Math.min(Math.max(buf.readInt(), 0), Area.EFFECT_MAX);
        effects = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            AreaEffect effect = EffectTypes.fromBuf(buf);
            if (effect != null) {
                effects.add(effect);
            }
        }
    }
}