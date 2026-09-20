package org.soraworld.areaeffect.common.effect;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;

/**
 * 一种区域效果的抽象：一个区域可同时挂多种效果，每种效果各自负责自身参数的
 * 持久化、网络序列化、边界处理与复制。反序列化统一由 {@link EffectTypes} 完成。
 */
public abstract class AreaEffect {

    /**
     * 权重的合法上限，GUI 滑条（GuiAreas 的 weightSlider）与 {@link #sanitize} 共用它，
     * 使数据模型与 UI 对权重的取值范围保持一致。
     */
    public static final float MAX_WEIGHT = 100.0F;

    /** 时间段模式：始终开启。 */
    public static final int TIME_ALWAYS = 0;
    /** 时间段模式：按游戏时间（世界时钟 0..24 小时）开启。 */
    public static final int TIME_GAME = 1;
    /** 时间段模式：按现实时间（本机时钟 0..24 小时）开启。 */
    public static final int TIME_REAL = 2;
    /** 小时的取值下/上限（闭区间，允许小数表达分钟粒度）。 */
    public static final float MIN_HOUR = 0.0F;
    public static final float MAX_HOUR = 24.0F;

    /**
     * 过渡时长下限（秒）：闭区间 [0,60] 的下界。<b>0 表示不过渡、效果瞬间到位</b>。
     * 与 README/GUI 滑条一致，为所有需要过渡的效果（亮度/雾/天空等）的公共参数。
     */
    public static final float MIN_DURATION = 0.0F;
    /** 过渡时长上限（秒）。 */
    public static final float MAX_DURATION = 60.0F;

    /**
     * 效果权重：重叠区域内同种效果按权重选最高者显示，权重相同则取较大 id 的区域
     * （即后创建者，见 ClientProxy#updateClientLight 的决胜规则）。
     * 仅在"一区域内每种效果至多一个实例"的前提下定义，故权重归属到单个效果实例即可。
     */
    private float weight = 0.0F;

    /**
     * 过渡时长（秒）：进入/离开区域与参数变更时的平滑过渡耗时，闭区间
     * [{@link #MIN_DURATION}, {@link #MAX_DURATION}] = [0,60]。
     * 0 表示瞬间到位。作为公共字段，供亮度/雾/天空等所有需要过渡的效果复用。
     */
    private float duration = 1.0F;

    /** 时间段模式，见 {@link #TIME_ALWAYS}/{@link #TIME_GAME}/{@link #TIME_REAL}。 */
    private int timeMode = TIME_ALWAYS;
    /** 启用时间段的起始小时（闭区间，{@link #clampHour} 收窄到 [0,24]）。 */
    private float startHour = 6.0F;
    /** 启用时间段的结束小时（半开区间 [start,end），{@link #clampHour} 收窄到 [0,24]）。 */
    private float endHour = 18.0F;

    /**
     * 复制一份独立的实例（含权重与时间段设置）。GUI 编辑效果参数时使用副本做成"工作列表"，
     * 避免原地改写共享 {@code Area} 持有的效果对象、脏掉存档原值。
     * 子类必须实现，且要把各自类型专属字段一并复制过来，并调用 {@link #copyCommonTo} 复制公共字段。
     */
    public abstract AreaEffect copy();

    /**
     * 效果类型 id，用于网络/NBT 反序列化时区分实现。
     */
    public abstract String typeId();

    /**
     * 当前时段是否开启。
     *
     * <p>{@code gameHour} 为当前游戏时钟小时（0..24，换算见 {@code ClientProxy#gameHourOf}：
     * 原版一天自 <b>06:00</b> 起算，故钟点需在 {@code worldTime / 1000} 的基础上偏移 6 小时），
     * {@code realHour} 为当前现实时钟小时（0..24）；两者按 {@link #timeMode} 取其一。
     * {@link #TIME_ALWAYS} 恒开启；{@code start==end} 视为<b>空窗口，恒不开启</b>
     * （时段控件允许把两个游标拉到重合，那语义上就是长度为 0 的区间 —— 想要整天生效请用
     * {@link #TIME_ALWAYS}，不再让这两种状态互相等效却看不出区别）；{@code start<end} 为普通区间
     * {@code [start,end)}；{@code start>end} 表示跨午夜（如 22:00-6:00），两端任一段命中即开启。
     *
     * <p>供客户端决胜循环在每帧选获胜效果时调用：窗口关闭的效果按「不存在」处理（不参与权重决胜）。
     */
    public boolean inTimeWindow(float gameHour, float realHour) {
        if (timeMode == TIME_ALWAYS) {
            return true;
        }
        float now = timeMode == TIME_GAME ? gameHour : realHour;
        if (startHour == endHour) {
            // 空窗口：半开区间 [h,h) 不含任何时刻。时段控件允许两游标重合，若把它当成
            // "整天开启"，就与 TIME_ALWAYS 等效却在外观上看不出区别了 —— 故按最直白的
            // "长度为 0 的区间"解释：恒不开启。
            return false;
        }
        if (startHour < endHour) {
            return now >= startHour && now < endHour;
        }
        // 跨午夜：start -> 24 与 0 -> end 两段任命中即开启
        return now >= startHour || now < endHour;
    }

    public int getTimeMode() {
        return timeMode;
    }

    public void setTimeMode(int timeMode) {
        this.timeMode = timeMode < TIME_ALWAYS ? TIME_ALWAYS : (Math.min(timeMode, TIME_REAL));
    }

    public float getStartHour() {
        return startHour;
    }

    public void setStartHour(float startHour) {
        this.startHour = clampHour(startHour);
    }

    public float getEndHour() {
        return endHour;
    }

    public void setEndHour(float endHour) {
        this.endHour = clampHour(endHour);
    }

    /**
     * 写入 NBT（不含 type 字段，type 由 EffectTypes 写入/读取）。
     */
    public abstract void writeToNbt(NBTTagCompound tag);

    /**
     * 写入网络缓冲（不含 type 字段，type 由调用方写入/读取）。
     */
    public abstract void writeToBuf(ByteBuf buf);

    /**
     * 对参数做边界处理（如亮度 0..100、时长 0..60）。默认只收窄权重与时间段字段，
     * 子类覆写时记得各自处理。
     */
    public void sanitize() {
        weight = clampWeight(weight);
        duration = clampDuration(duration);
        timeMode = timeMode < TIME_ALWAYS ? TIME_ALWAYS : (Math.min(timeMode, TIME_REAL));
        startHour = clampHour(startHour);
        endHour = clampHour(endHour);
    }

    public float getWeight() {
        return weight;
    }

    public void setWeight(float weight) {
        this.weight = clampWeight(weight);
    }

    public float getDuration() {
        return duration;
    }

    public void setDuration(float duration) {
        this.duration = clampDuration(duration);
    }

    private static float clampWeight(float weight) {
        return Float.isNaN(weight) ? 0.0F
                : (weight < 0.0F ? 0.0F : (Math.min(weight, MAX_WEIGHT)));
    }

    /**
     * 时长边界处理：NaN 回落默认 1 秒，其余收窄到闭区间
     * [{@link #MIN_DURATION}, {@link #MAX_DURATION}] = [0,60]（负数夹到 0，+∞ 夹到 60）。
     * 下限 0 是合法值，表示"不过渡、瞬间到位"，故不像旧版那样把 ≤0 一律回落成 1 秒。
     */
    private static float clampDuration(float duration) {
        return Float.isNaN(duration) ? 1.0F : Math.max(MIN_DURATION, Math.min(MAX_DURATION, duration));
    }

    /** 写入公共字段（权重与时间段）到 NBT。子类在 writeToNbt 里、类型专用字段<b>之前</b>先调用本方法。 */
    public void writeNbtFields(NBTTagCompound tag) {
        // 1.13+ NBT 写入方法统一为 putXxx
        tag.putFloat("weight", weight);
        tag.putByte("timeMode", (byte) timeMode);
        tag.putFloat("startHour", startHour);
        tag.putFloat("endHour", endHour);
        tag.putFloat("duration", duration);
    }

    /** 从 NBT 读回公共字段（权重与时间段）。与 {@link #writeNbtFields} 的字段顺序保持一致。 */
    public void readNbtFields(NBTTagCompound tag) {
        setWeight(tag.getFloat("weight"));
        setTimeMode(tag.getByte("timeMode"));
        setStartHour(tag.getFloat("startHour"));
        setEndHour(tag.getFloat("endHour"));
        setDuration(tag.getFloat("duration"));
    }

    /** 写入公共字段（权重、过渡时长与时间段）到网络缓冲。子类在 writeToBuf 里、类型专用字段<b>之前</b>先调用。 */
    public void writeBufFields(ByteBuf buf) {
        buf.writeFloat(weight);
        buf.writeByte(timeMode);
        buf.writeFloat(startHour);
        buf.writeFloat(endHour);
        buf.writeFloat(duration);
    }

    /** 从网络缓冲读回公共字段（权重、过渡时长与时间段）。与 {@link #writeBufFields} 的元素顺序保持一致。 */
    public void readBufFields(ByteBuf buf) {
        setWeight(buf.readFloat());
        setTimeMode(buf.readByte());
        setStartHour(buf.readFloat());
        setEndHour(buf.readFloat());
        setDuration(buf.readFloat());
    }

    /** 把公共字段（权重、过渡时长与时间段）复制到另一实例；子类 {@link #copy()} 构造新实例后调用。 */
    protected void copyCommonTo(AreaEffect target) {
        target.weight = weight;
        target.duration = duration;
        target.timeMode = timeMode;
        target.startHour = startHour;
        target.endHour = endHour;
    }

    /** 小时取值收窄到 [MIN_HOUR, MAX_HOUR]；NaN 回落为 0。 */
    private static float clampHour(float hour) {
        return Float.isNaN(hour) ? MIN_HOUR : Math.max(MIN_HOUR, Math.min(MAX_HOUR, hour));
    }
}
