package org.soraworld.areaeffect.common.network;

import io.netty.buffer.ByteBuf;

/**
 * 客户端 → 服务端：请求当前维度的区域列表（仅 OP 允许）。
 */
public class MessageListRequest implements IPacket {

    @Override
    public void toBytes(ByteBuf buf) {
    }

    @Override
    public void fromBytes(ByteBuf buf) {
    }
}