package org.soraworld.areaeffect.common.network;

import io.netty.buffer.ByteBuf;

/**
 * 客户端 → 服务端：右键空气事件。服务端将其与右键方块走同一处理入口
 * （{@link org.soraworld.areaeffect.common.shape.Selection#onClickRight}），
 * 作为多边形选区时的撤回触发事件；二点形状因无坐标不做任何事。
 */
public class MessageClickAir implements IPacket {

    public MessageClickAir() {
    }

    @Override
    public void toBytes(ByteBuf buf) {
    }

    @Override
    public void fromBytes(ByteBuf buf) {
    }
}
