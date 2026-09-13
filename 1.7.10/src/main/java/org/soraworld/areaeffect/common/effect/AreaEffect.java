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
     * 效果权重：重叠区域内同种效果按权重选最高者显示，权重相同则取较大 id 的区域
     * （即后创建者，见 ClientProxy#updateClientLight 的决胜规则）。
     * 仅在"一区域内每种效果至多一个实例"的前提下定义，故权重归属到单个效果实例即可。
     */
    private float weight = 0.0F;

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
     * <p>{@code gameHour} 为当前游戏时钟小时（0..24，由 {@code world.getWorldTime() % 24000 / 1000}（映射到译者）
     * 得出），{@code realHour} 为当前现实时钟小时（0..24）。只被「是否为 {timeMode} 生效」取其一：
     * {@link #TIME_ALWAYS} 恒开启；{@code start==end} 视为整天开启；{@code start<end} 为普通区间
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
            return true;
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
        this.timeMode = timeMode < TIME_ALWAYS ? TIME_ALWAYS : (timeMode > TIME_REAL ? TIME_REAL : timeMode);
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
        timeMode = timeMode < TIME_ALWAYS ? TIME_ALWAYS : (timeMode > TIME_REAL ? TIME_REAL : timeMode);
        startHour = clampHour(startHour);
        endHour = clampHour(endHour);
    }

    public float getWeight() {
        return weight;
    }

    public void setWeight(float weight) {
        this.weight = clampWeight(weight);
    }

    private static float clampWeight(float weight) {
        return Float.isNaN(weight) ? 0.0F
                : (weight < 0.0F ? 0.0F : (weight > MAX_WEIGHT ? MAX_WEIGHT : weight));
    }

    /** 写入公共字段（权重与时间段）到 NBT。子类在 writeToNbt 里、类型专用字段<b>之前</b>先调用本方法。 */
    public void writeNbtFields(NBTTagCompound tag) {
        tag.setFloat("weight", weight);
        tag.setByte("timeMode", (byte) timeMode);
        tag.setFloat("startHour", startHour);
        tag.setFloat("endHour", endHour);
    }

    /** 从 NBT 读回公共字段（权重与时间段）。与 {@link #writeNbtFields} 的字段顺序保持一致。 */
    public void readNbtFields(NBTTagCompound tag) {
        setWeight(tag.getFloat("weight"));
        setTimeMode(tag.getByte("timeMode"));
        setStartHour(tag.getFloat("startHour"));
        setEndHour(tag.getFloat("endHour"));
    }

    /** 写入公共字段（权重与时间段）到网络缓冲。子类在 writeToBuf 里、类型专用字段<b>之前</b>先调用。 */
    public void writeBufFields(ByteBuf buf) {
        buf.writeFloat(weight);
        buf.writeByte(timeMode);
        buf.writeFloat(startHour);
        buf.writeFloat(endHour);
    }

    /** 从网络缓冲读回公共字段（权重与时间段）。与 {@link #writeBufFields} 的元素顺序保持一致。 */
    public void readBufFields(ByteBuf buf) {
        setWeight(buf.readFloat());
        setTimeMode(buf.readByte());
        setStartHour(buf.readFloat());
        setEndHour(buf.readFloat());
    }

    /** 把公共字段（权重与时间段）复制到另一实例；子类 {@link #copy()} 构造新实例后调用。 */
    protected void copyCommonTo(AreaEffect target) {
        target.weight = weight;
        target.timeMode = timeMode;
        target.startHour = startHour;
        target.endHour = endHour;
    }

    /** 小时取值收窄到 [MIN_HOUR, MAX_HOUR]；NaN 回落为 0。 */
    private static float clampHour(float hour) {
        return Float.isNaN(hour) ? MIN_HOUR : Math.max(MIN_HOUR, Math.min(MAX_HOUR, hour));
    }
}