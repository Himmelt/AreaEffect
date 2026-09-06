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
        buf.writeInt(effects.size());
        for (AreaEffect effect : effects) {
            EffectTypes.writeString(buf, effect.typeId());
            effect.writeToBuf(buf);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        dim = buf.readInt();
        id = buf.readInt();
        remark = EffectTypes.readString(buf);
        int size = buf.readInt();
        effects = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            AreaEffect effect = EffectTypes.fromBuf(buf);
            if (effect != null) {
                effects.add(effect);
            }
        }
    }
}