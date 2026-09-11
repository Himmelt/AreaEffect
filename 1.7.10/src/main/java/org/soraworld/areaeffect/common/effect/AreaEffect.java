package org.soraworld.areaeffect.common.effect;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;

/**
 * 一种区域效果的抽象：一个区域可同时挂多种效果，每种效果各自负责自身参数的
 * 持久化、网络序列化、边界处理与复制。反序列化统一由 {@link EffectTypes} 完成。
 */
public abstract class AreaEffect {

    /**
     * 效果权重：重叠区域内同种效果按权重选最高者显示（见 Area#weightConflict 的平局规则）。
     * 仅在"一区域内每种效果至多一个实例"的前提下定义，故权重归属到单个效果实例即可。
     */
    private float weight = 0.0F;

    /**
     * 复制一份独立的实例（含权重）。GUI 编辑效果参数时使用副本做成"工作列表"，
     * 避免原地改写共享 {@code Area} 持有的效果对象、脏掉存档原值。
     * 子类必须实现，且要把各自类型专属字段一并复制过来。
     */
    public abstract AreaEffect copy();

    /**
     * 效果类型 id，用于网络/NBT 反序列化时区分实现。
     */
    public abstract String typeId();

    /**
     * 写入 NBT（不含 type 字段，type 由 EffectTypes 写入/读取）。
     */
    public abstract void writeToNbt(NBTTagCompound tag);

    /**
     * 写入网络缓冲（不含 type 字段，type 由调用方写入/读取）。
     */
    public abstract void writeToBuf(ByteBuf buf);

    /**
     * 对参数做边界处理（如亮度 0..100、时长 0..60）。默认只收窄权重，子类覆写时记得各自处理。
     */
    public void sanitize() {
        weight = clampWeight(weight);
    }

    public float getWeight() {
        return weight;
    }

    public void setWeight(float weight) {
        this.weight = clampWeight(weight);
    }

    private static float clampWeight(float weight) {
        return Float.isNaN(weight) ? 0.0F : Math.max(0.0F, weight);
    }

    /** 写入公共字段（权重）到 NBT。子类在 writeToNbt 里、类型专用字段<b>之前</b>先调用本方法。 */
    public void writeNbtFields(NBTTagCompound tag) {
        tag.setFloat("weight", weight);
    }

    /** 从 NBT 读回公共字段（权重）。与 {@link #writeNbtFields} 的字段顺序保持一致。 */
    public void readNbtFields(NBTTagCompound tag) {
        setWeight(tag.getFloat("weight"));
    }

    /** 写入公共字段（权重）到网络缓冲。子类在 writeToBuf 里、类型专用字段<b>之前</b>先调用。 */
    public void writeBufFields(ByteBuf buf) {
        buf.writeFloat(weight);
    }

    /** 从网络缓冲读回公共字段（权重）。与 {@link #writeBufFields} 的元素顺序保持一致。 */
    public void readBufFields(ByteBuf buf) {
        setWeight(buf.readFloat());
    }
}