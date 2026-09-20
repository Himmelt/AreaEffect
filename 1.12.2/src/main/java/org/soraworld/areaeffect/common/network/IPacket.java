package org.soraworld.areaeffect.common.network;

import io.netty.buffer.ByteBuf;

/**
 * 网络消息序列化契约（与 SimpleNetworkWrapper 时代 IMessage 同构）。
 *
 * <p>实现类必须保留无参构造器：{@link PacketChannel} 入站解码经反射 newInstance()
 * 创建实例后再 {@link #fromBytes}。
 *
 * <p>方向性处理逻辑不在消息类上——由 {@code CommonProxy}/{@code ClientProxy}
 * 经 {@link PacketChannel#bindServer}/{@link PacketChannel#bindClient} 绑定。
 */
public interface IPacket {

    /** 出站编码：写入消息字段（opcode 字节由 PacketChannel 负责，在其后写入）。 */
    void toBytes(ByteBuf buf);

    /** 入站解码：读回消息字段（调用时 opcode 字节已被读走）。 */
    void fromBytes(ByteBuf buf);
}