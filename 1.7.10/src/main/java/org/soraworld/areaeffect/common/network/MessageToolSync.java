package org.soraworld.areaeffect.common.network;

import io.netty.buffer.ByteBuf;
import org.soraworld.areaeffect.common.effect.EffectTypes;

/**
 * 服务端 → 客户端：同步当前选区工具（MP 客户端不执行 config.load()，需显式告知）。
 */
public class MessageToolSync implements IPacket {

    public String toolName = "wooden_axe";

    public MessageToolSync() {
    }

    public MessageToolSync(String toolName) {
        this.toolName = toolName == null ? "wooden_axe" : toolName;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        EffectTypes.writeString(buf, toolName);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        toolName = EffectTypes.readString(buf);
    }
}
