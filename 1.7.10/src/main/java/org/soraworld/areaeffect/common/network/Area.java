package org.soraworld.areaeffect.common.network;

import io.netty.buffer.ByteBuf;
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
import java.util.Collections;
import java.util.List;

public class Area {

    /** 备注最大长度（字符数）。 */
    public static final int REMARK_MAX = 60;

    /**
     * 单个区域的效果条数上限。收发<b>两端必须共用此值</b>：写出的条数一旦大于读回的条数，
     * 同一元素在包内的字节长度就不一致，后续元素会整体错位（见 {@code MessageListReply}、
     * {@code MessageSetProps}）。与锚点上限（{@link org.soraworld.areaeffect.common.shape.Selection#MAX_ANCHORS}）
     * 同一原则：上限只在一处定义，读写都引用它。
     */
    public static final int EFFECT_MAX = 16;

    public int id;

    private final AreaShape shape;

    /** 区域备注，供玩家标注用途；可为空串。 */
    private String remark = "";

    /**
     * 挂载的效果列表（copy-on-write）：单机下集成服在服务器线程整组替换，
     * 渲染线程每帧遍历同一对象，原地 clear/addAll 会触发并发修改异常，
     * 故所有变更都替换为新的不可变快照，读取方拿到的永远是完整旧列表。
     */
    private volatile List<AreaEffect> effects = new ArrayList<>();

    public Area(AreaShape shape, float lightness, float duration) {
        this.shape = shape;
        this.effects = Collections.singletonList(new LightnessEffect(lightness, duration));
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
        // copy-on-write：整组替换为新的不可变快照，渲染线程遍历中的旧列表不受影响
        this.effects = list == null ? Collections.<AreaEffect>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(list));
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

    private LightnessEffect lightnessEffect() {
        for (AreaEffect effect : effects) {
            if (effect instanceof LightnessEffect) {
                return (LightnessEffect) effect;
            }
        }
        return null;
    }

    /** 流式写入目标缓冲：type + 锚点 + closed + 备注 + 效果列表（与 fromByteBuf 对应）。 */
    public static void writeBuf(ByteBuf buf, Area area) {
        ShapeTypes.writeBuf(area.shape(), buf);
        EffectTypes.writeString(buf, area.getRemark());
        List<AreaEffect> effects = area.effects;
        // 与读侧同源的上限：见 EFFECT_MAX 的说明
        int count = Math.min(effects.size(), EFFECT_MAX);
        buf.writeInt(count);
        for (int i = 0; i < count; i++) {
            AreaEffect effect = effects.get(i);
            EffectTypes.writeString(buf, effect.typeId());
            effect.writeToBuf(buf);
        }
    }

    /**
     * 从缓冲读回区域。无论形状能否识别，remark 与 effects 都会被完整读走再返回：
     * 这样返回 null 时也停在元素边界上，调用方（{@code MessageAreaUpdate} 等按元素连续读取的
     * 消息）不会因一次解析失败就让后续元素从错误偏移开始解析（表现为缺项 / 乱码 / 整包丢弃）。
     * 写侧的条数上限见 {@link #EFFECT_MAX}，与读侧同源。
     */
    public static Area fromByteBuf(ByteBuf buf) {
        AreaShape shape = ShapeTypes.fromBuf(buf);
        String remark = EffectTypes.readString(buf);
        // 效果数量收窄到合理上限，防恶意包一次性申请海量对象（与写入侧共用 EFFECT_MAX）
        int size = Math.min(Math.max(buf.readInt(), 0), EFFECT_MAX);
        List<AreaEffect> effects = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            AreaEffect effect = EffectTypes.fromBuf(buf);
            if (effect != null) {
                effects.add(effect);
            }
        }
        if (shape == null) {
            return null; // 形状不可识别：字节已消费到元素边界，调用方可安全继续读下一个
        }
        Area area = new Area(shape, 100.0F, 1.0F);
        area.setRemark(remark);
        area.setEffects(effects);
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

    /**
     * 平局规则下的冲突判定（创建/更新区域时使用）：两区域<b>形状重叠</b>，且存在某类
     * <b>同种效果、权重相等</b>时，才视为冲突。
     *
     * <p>这样区域<b>允许重叠</b>——只要重叠区域内同种效果的权重互不相等即可；一旦有
     * 同种效果权重平局则拒绝，保证重叠集合里同种效果的权重唯一，渲染端取权重最高者时确定、不抖动。
     */
    public boolean weightConflict(Area other) {
        if (!shape.conflict(other.shape())) {
            return false;
        }
        for (AreaEffect a : effects) {
            for (AreaEffect b : other.effects) {
                if (a.typeId().equals(b.typeId()) && a.getWeight() == b.getWeight()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 亮度效果的权重；无亮度效果时返回默认 0。 */
    public float getWeight() {
        LightnessEffect effect = lightnessEffect();
        return effect == null ? 0.0F : effect.getWeight();
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
