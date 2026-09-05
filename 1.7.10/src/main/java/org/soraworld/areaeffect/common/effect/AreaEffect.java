package org.soraworld.areaeffect.common.effect;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;

/**
 * 一种区域效果的抽象：一个区域可同时挂多种效果，每种效果各自负责自身参数的
 * 持久化、网络序列化、边界处理与复制。反序列化统一由 {@link EffectTypes} 完成。
 */
public abstract class AreaEffect {

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
     * 对参数做边界处理（如亮度 0..100、时长 0.05..60）。默认空，子类覆写。
     */
    public void sanitize() {
    }
}