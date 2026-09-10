package org.soraworld.areaeffect.common.network;

import io.netty.buffer.ByteBuf;

/**
 * 服务端 → 客户端：区域面板的开启授权 / 刷新触发（无负载）。
 *
 * <p><b>刻意不带负载</b>：面板数据一律取自客户端本地镜像（专用服登录时全量推送 + 此后增删改广播；
 * 单机下客户端与集成服务端是同一实例），服务端再回一份区域列表只是把同样的数据搬运两遍。
 * 历史上这层负载确实被消费过（{@code new GuiAreaList(this, packet.dim, packet.areas)}），但
 * {@code 6303265} 把面板改成跨维度三栏之后就再没有消费者了 —— 那时就该顺手删掉。
 *
 * <p>现在本消息的唯一作用是<b>服务端 OP 门禁的应答</b>：鉴权不通过时服务端回
 * {@code chat.perm.denied} 而不回本消息，客户端据此不开面板。发送频率很低（按键触发），
 * 因此也不值得为它做批量。
 */
public class MessageListReply implements IPacket {

    public MessageListReply() {
    }

    @Override
    public void toBytes(ByteBuf buf) {
    }

    @Override
    public void fromBytes(ByteBuf buf) {
    }
}
