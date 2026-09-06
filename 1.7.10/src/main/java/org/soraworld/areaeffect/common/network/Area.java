package org.soraworld.areaeffect.common.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import org.soraworld.areaeffect.common.effect.AreaEffect;
import org.soraworld.areaeffect.common.effect.EffectTypes;
import org.soraworld.areaeffect.common.effect.LightnessEffect;
import org.soraworld.areaeffect.common.util.Vec3d;
import org.soraworld.areaeffect.common.util.Vec3i;

import java.util.ArrayList;
import java.util.List;

public class Area {

    /** 备注最大长度（字符数）。 */
    public static final int REMARK_MAX = 60;

    public int id;

    public final int x1;
    public final int y1;
    public final int z1;
    public final int x2;
    public final int y2;
    public final int z2;

    /** 区域备注，供玩家标注用途；可为空串。 */
    private String remark = "";

    private final List<AreaEffect> effects = new ArrayList<>();

    public Area(int x1, int y1, int z1, int x2, int y2, int z2, float lightness, float duration) {
        this.x1 = Math.min(x1, x2);
        this.y1 = Math.min(y1, y2);
        this.z1 = Math.min(z1, z2);
        this.x2 = Math.max(x1, x2);
        this.y2 = Math.max(y1, y2);
        this.z2 = Math.max(z1, z2);
        this.effects.add(new LightnessEffect(lightness, duration));
    }

    public String getRemark() {
        return remark == null ? "" : remark;
    }

    public void setRemark(String remark) {
        if (remark == null) {
            this.remark = "";
        } else {
            this.remark = remark.length() > REMARK_MAX ? remark.substring(0, REMARK_MAX) : remark;
        }
    }

    /**
     * 该区域挂载的效果列表（每种效果类型至多一个实例）。
     */
    public List<AreaEffect> getEffects() {
        return effects;
    }

    public void setEffects(List<AreaEffect> list) {
        effects.clear();
        if (list != null) {
            effects.addAll(list);
        }
    }

    public void addEffect(AreaEffect effect) {
        if (effect != null) {
            effects.removeIf(e -> e.typeId().equals(effect.typeId()));
            effects.add(effect);
        }
    }

    /**
     * 亮度效果的当前目标亮度（CIE L*），无亮度效果时返回默认 100。
     */
    public float getLightness() {
        LightnessEffect effect = lightnessEffect();
        return effect == null ? 100.0F : effect.getLightness();
    }

    /**
     * 亮度效果的过渡时长（秒），无亮度效果时返回默认 1。
     */
    public float getDuration() {
        LightnessEffect effect = lightnessEffect();
        return effect == null ? 1.0F : effect.getDuration();
    }

    public void setLightness(float lightness) {
        ensureLightnessEffect().setLightness(lightness);
    }

    public void setDuration(float duration) {
        ensureLightnessEffect().setDuration(duration);
    }

    private LightnessEffect lightnessEffect() {
        for (AreaEffect effect : effects) {
            if (effect instanceof LightnessEffect) {
                return (LightnessEffect) effect;
            }
        }
        return null;
    }

    private LightnessEffect ensureLightnessEffect() {
        LightnessEffect effect = lightnessEffect();
        if (effect == null) {
            effect = new LightnessEffect();
            effects.add(effect);
        }
        return effect;
    }

    public static ByteBuf toByteBuf(Area area) {
        ByteBuf buf = Unpooled.buffer();
        buf.writeInt(area.x1);
        buf.writeInt(area.y1);
        buf.writeInt(area.z1);
        buf.writeInt(area.x2);
        buf.writeInt(area.y2);
        buf.writeInt(area.z2);
        EffectTypes.writeString(buf, area.getRemark());
        List<AreaEffect> effects = area.effects;
        buf.writeInt(effects.size());
        for (AreaEffect effect : effects) {
            EffectTypes.writeString(buf, effect.typeId());
            effect.writeToBuf(buf);
        }
        return buf;
    }

    public static Area fromByteBuf(ByteBuf buf) {
        int x1 = buf.readInt();
        int y1 = buf.readInt();
        int z1 = buf.readInt();
        int x2 = buf.readInt();
        int y2 = buf.readInt();
        int z2 = buf.readInt();
        Area area = new Area(x1, y1, z1, x2, y2, z2, 100.0F, 1.0F);
        area.setRemark(EffectTypes.readString(buf));
        area.effects.clear();
        int size = buf.readInt();
        for (int i = 0; i < size; i++) {
            AreaEffect effect = EffectTypes.fromBuf(buf);
            if (effect != null) {
                area.effects.add(effect);
            }
        }
        return area;
    }

    public boolean contains(double x, double y, double z) {
        return x >= x1 && x <= x2 + 1 && y >= y1 && y < y2 + 1 && z >= z1 && z <= z2 + 1;
    }

    public boolean contains(Vec3d pos) {
        return contains(pos.x, pos.y, pos.z);
    }

    public boolean conflict(Area area) {
        return x1 <= area.x2 && x2 >= area.x1 && y1 <= area.y2 && y2 >= area.y1 && z1 <= area.z2 && z2 >= area.z1;
    }

    @Override
    public String toString() {
        return x1 + "," + y1 + "," + z1 + "," + x2 + "," + y2 + "," + z2 + "," + getLightness() + "," + getDuration();
    }

    public String pos1() {
        return "(" + x1 + "," + y1 + "," + z1 + ')';
    }

    public String pos2() {
        return "(" + x2 + "," + y2 + "," + z2 + ')';
    }

    public Vec3i vec1() {
        return new Vec3i(x1, y1, z1);
    }

    public Vec3i vec2() {
        return new Vec3i(x2, y2, z2);
    }

    public void center(EntityPlayer player) {
        double x = (x1 + x2) / 2.0;
        double y = (y1 + y2) / 2.0;
        double z = (z1 + z2) / 2.0;
        if (player instanceof EntityPlayerMP) {
            // 服务端必须用 setPositionAndUpdate 才会把位置同步到客户端
            ((EntityPlayerMP) player).setPositionAndUpdate(x, y, z);
        } else {
            player.setPosition(x, y, z);
        }
    }
}