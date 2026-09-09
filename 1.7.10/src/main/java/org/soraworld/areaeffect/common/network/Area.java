package org.soraworld.areaeffect.common.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import org.soraworld.areaeffect.common.effect.AreaEffect;
import org.soraworld.areaeffect.common.effect.EffectTypes;
import org.soraworld.areaeffect.common.effect.LightnessEffect;
import org.soraworld.areaeffect.common.shape.AreaShape;
import org.soraworld.areaeffect.common.shape.PrismShape;
import org.soraworld.areaeffect.common.shape.ShapeTypes;
import org.soraworld.areaeffect.common.util.Vec3d;
import org.soraworld.areaeffect.common.util.Vec3i;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Area {

    /** 备注最大长度（字符数）。 */
    public static final int REMARK_MAX = 60;

    public int id;

    private final AreaShape shape;

    /** 区域备注，供玩家标注用途；可为空串。 */
    private String remark = "";

    private final List<AreaEffect> effects = new ArrayList<>();

    public Area(AreaShape shape, float lightness, float duration) {
        this.shape = shape;
        this.effects.add(new LightnessEffect(lightness, duration));
    }

    /** 旧存档兼容工厂：以六坐标构造长方体区域。 */
    public static Area box(int x1, int y1, int z1, int x2, int y2, int z2, float lightness, float duration) {
        AreaShape shape = new PrismShape(PrismShape.Section.RECT, PrismShape.Height.BOUNDED,
                Arrays.asList(new Vec3i(x1, y1, z1), new Vec3i(x2, y2, z2)), false);
        return new Area(shape, lightness, duration);
    }

    public AreaShape shape() {
        return shape;
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
        ShapeTypes.writeBuf(area.shape(), buf);
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
        AreaShape shape = ShapeTypes.fromBuf(buf);
        if (shape == null) {
            return null;
        }
        Area area = new Area(shape, 100.0F, 1.0F);
        area.setRemark(EffectTypes.readString(buf));
        area.effects.clear();
        // 效果数量收窄到合理上限，防恶意包一次性申请海量对象（与 MessageSetProps 一致）
        int size = Math.min(Math.max(buf.readInt(), 0), 16);
        for (int i = 0; i < size; i++) {
            AreaEffect effect = EffectTypes.fromBuf(buf);
            if (effect != null) {
                area.effects.add(effect);
            }
        }
        return area;
    }

    public boolean contains(double x, double y, double z) {
        return shape.contains(x, y, z);
    }

    public boolean contains(Vec3d pos) {
        return contains(pos.x, pos.y, pos.z);
    }

    public boolean conflict(Area area) {
        return shape.conflict(area.shape());
    }

    @Override
    public String toString() {
        return shape.describe() + "," + getLightness() + "," + getDuration();
    }

    /** 详情页展示文本（旧接口保留，委托形状描述）。 */
    public String pos1() {
        return shape.describe();
    }

    public String pos2() {
        return "";
    }

    public Vec3i vec1() {
        AreaShape.Bounds b = shape.bounds();
        return new Vec3i(b.minX, b.minY, b.minZ);
    }

    public Vec3i vec2() {
        AreaShape.Bounds b = shape.bounds();
        return new Vec3i(b.maxX, b.maxY, b.maxZ);
    }

    public void center(EntityPlayer player) {
        Vec3d c = shape.center();
        if (player instanceof EntityPlayerMP) {
            // 服务端必须用 setPositionAndUpdate 才会把位置同步到客户端
            ((EntityPlayerMP) player).setPositionAndUpdate(c.x, c.y, c.z);
        } else {
            player.setPosition(c.x, c.y, c.z);
        }
    }
}
