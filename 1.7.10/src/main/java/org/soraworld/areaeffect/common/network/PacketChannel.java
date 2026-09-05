package org.soraworld.areaeffect.common.network;

import cpw.mods.fml.common.network.FMLEventChannel;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.internal.FMLProxyPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.soraworld.areaeffect.AreaEffect;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 自建轻量网络通道：对 FML {@link FMLEventChannel} 的最小封装，取代每类手写 opcode
 * switch。保留"消息类 + opcode 注册表 + 类型化发送"，去掉"每条消息强制内嵌 Handler"。
 *
 * <p>wire 格式：第 0 字节 opcode，其后为消息负载。入站统一由 {@link InboundRouter}
 * 分发到本类的 {@link #route}：查 opcode→消息类→本侧处理逻辑。方向性处理由两侧
 * proxy 经 {@link #bindClient}/{@link #bindServer} 绑定，不做消息内嵌 Handler。
 */
public final class PacketChannel {

    private static final Logger LOGGER = LogManager.getLogger(AreaEffect.MOD_NAME);

    /** opcode → 消息类：接收解码查表。 */
    private static final Map<Byte, Class<? extends IPacket>> BY_ID = new ConcurrentHashMap<>();
    /** 消息类 → opcode：发送编码查表。 */
    private static final Map<Class<? extends IPacket>, Byte> BY_TYPE = new ConcurrentHashMap<>();
    /** 客户端方向处理逻辑：仅物理客户端绑定（ClientProxy），玩家参数恒为 null。 */
    private static final Map<Byte, BiConsumer<IPacket, EntityPlayerMP>> CLIENT = new ConcurrentHashMap<>();
    /** 服务端方向处理逻辑：仅服务端侧绑定（CommonProxy），第二参为发包玩家。 */
    private static final Map<Byte, BiConsumer<IPacket, EntityPlayerMP>> SERVER = new ConcurrentHashMap<>();

    private static final FMLEventChannel CHANNEL = NetworkRegistry.INSTANCE.newEventDrivenChannel(AreaEffect.MOD_ID);

    static {
        CHANNEL.register(new InboundRouter());
    }

    private PacketChannel() {
    }

    /**
     * 注册 opcode ↔ 消息类型。须在任何收发之前完成，由 {@code CommonProxy} 的
     * 幂等注册入口保证整个进程恰执行一次。
     */
    public static <T extends IPacket> void register(int id, Class<T> type) {
        Byte key = (byte) id;
        if (BY_ID.put(key, type) != null) {
            throw new IllegalArgumentException("duplicate packet id " + id + " on channel " + AreaEffect.MOD_ID);
        }
        BY_TYPE.put(type, key);
    }

    /** 绑定客户端方向消息的处理逻辑（由 ClientProxy 调用）。 */
    public static <T extends IPacket> void bindClient(Class<T> type, Consumer<T> handler) {
        CLIENT.put(requireRegistered(type), (pkt, player) -> handler.accept(type.cast(pkt)));
    }

    /** 绑定服务端方向消息的处理逻辑（由服务端侧调用）；第二参为发包玩家。 */
    public static <T extends IPacket> void bindServer(Class<T> type, BiConsumer<T, EntityPlayerMP> handler) {
        SERVER.put(requireRegistered(type), (pkt, player) -> handler.accept(type.cast(pkt), player));
    }

    private static Byte requireRegistered(Class<? extends IPacket> type) {
        Byte id = BY_TYPE.get(type);
        if (id == null) {
            throw new IllegalArgumentException("packet not registered on channel " + AreaEffect.MOD_ID + ": " + type.getName());
        }
        return id;
    }

    public static void sendTo(IPacket packet, EntityPlayerMP player) {
        CHANNEL.sendTo(encode(packet), player);
    }

    /** 发往所连服务端（仅客户端侧调用）。 */
    public static void sendToServer(IPacket packet) {
        CHANNEL.sendToServer(encode(packet));
    }

    /** 向全部在线玩家广播（服务端不存在时静默返回）。 */
    public static void sendToAll(IPacket packet) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) {
            return;
        }
        for (Object player : server.getConfigurationManager().playerEntityList) {
            if (player instanceof EntityPlayerMP) {
                sendTo(packet, (EntityPlayerMP) player);
            }
        }
    }

    /** 出站编码：opcode 字节 + 消息负载。 */
    private static FMLProxyPacket encode(IPacket packet) {
        Byte id = requireRegistered(packet.getClass());
        ByteBuf buf = Unpooled.buffer();
        buf.writeByte(id);
        packet.toBytes(buf);
        return new FMLProxyPacket(buf, AreaEffect.MOD_ID);
    }

    /**
     * 入站路由。解码失败/未知 opcode 只丢弃并记日志，不向外抛。
     */
    static void route(ByteBuf buf, boolean serverSide, EntityPlayerMP player) {
        Map<Byte, BiConsumer<IPacket, EntityPlayerMP>> handlers = serverSide ? SERVER : CLIENT;
        try {
            if (buf.readableBytes() < 1) {
                LOGGER.warn("Dropped empty packet on channel " + AreaEffect.MOD_ID);
                return;
            }
            byte id = buf.readByte();
            Class<? extends IPacket> type = BY_ID.get(id);
            if (type == null) {
                LOGGER.warn("Dropped packet with unknown id " + id + " on channel " + AreaEffect.MOD_ID);
                return;
            }
            BiConsumer<IPacket, EntityPlayerMP> handler = handlers.get(id);
            if (handler == null) {
                LOGGER.error("No handler bound for id " + id + " on channel " + AreaEffect.MOD_ID);
                return;
            }
            IPacket packet = type.newInstance();
            packet.fromBytes(buf);
            handler.accept(packet, player);
        } catch (Throwable t) {
            LOGGER.error("Error handling packet on channel " + AreaEffect.MOD_ID, t);
        }
    }
}