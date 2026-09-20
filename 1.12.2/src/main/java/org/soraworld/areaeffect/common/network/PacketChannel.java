package org.soraworld.areaeffect.common.network;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.FMLEventChannel;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.internal.FMLProxyPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.soraworld.areaeffect.AreaEffectMod;

import java.util.Map;
import java.util.Set;
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

    private static final Logger LOGGER = LogManager.getLogger(AreaEffectMod.MOD_NAME);

    /** opcode → 消息类：接收解码查表。 */
    private static final Map<Byte, Class<? extends IPacket>> BY_ID = new ConcurrentHashMap<>();
    /** 消息类 → opcode：发送编码查表。 */
    private static final Map<Class<? extends IPacket>, Byte> BY_TYPE = new ConcurrentHashMap<>();
    /** 已告警过的 opcode：畸形包反复到达时只记一次，避免客户端刷屏拖垮日志。 */
    private static final Set<Byte> REPORTED_IDS = ConcurrentHashMap.newKeySet();
    /** 客户端方向处理逻辑：仅物理客户端绑定（ClientProxy），玩家参数恒为 null。 */
    private static final Map<Byte, BiConsumer<IPacket, EntityPlayerMP>> CLIENT = new ConcurrentHashMap<>();
    /** 服务端方向处理逻辑：仅服务端侧绑定（CommonProxy），第二参为发包玩家。 */
    private static final Map<Byte, BiConsumer<IPacket, EntityPlayerMP>> SERVER = new ConcurrentHashMap<>();

    private static final FMLEventChannel CHANNEL = NetworkRegistry.INSTANCE.newEventDrivenChannel(AreaEffectMod.MOD_ID);

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
            throw new IllegalArgumentException("duplicate packet id " + id + " on channel " + AreaEffectMod.MOD_ID);
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
            throw new IllegalArgumentException("packet not registered on channel " + AreaEffectMod.MOD_ID + ": " + type.getName());
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
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) {
            return;
        }
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            sendTo(packet, player);
        }
    }

    /** 出站编码：opcode 字节 + 消息负载。 */
    private static FMLProxyPacket encode(IPacket packet) {
        Byte id = requireRegistered(packet.getClass());
        ByteBuf buf = Unpooled.buffer();
        buf.writeByte(id);
        packet.toBytes(buf);
        // 1.12.2 的 FMLProxyPacket 接收的是 PacketBuffer（轻量 ByteBuf 封装）
        return new FMLProxyPacket(new PacketBuffer(buf), AreaEffectMod.MOD_ID);
    }

    /**
     * 同一 opcode 只告警一次。未知 opcode / 未绑定处理逻辑属于持续状态（版本不一致或
     * 构造错误）而非瞬时事件，逐包记录会被畸形包以极低成本刷爆日志。
     *
     * @param error {@code true} 记 ERROR（本侧缺处理逻辑，属代码缺陷）；
     *              {@code false} 记 WARN（对端与本侧不匹配）
     */
    private static void reportOnce(byte id, String message, boolean error) {
        if (!REPORTED_IDS.add(id)) {
            return;
        }
        String full = message + " on channel " + AreaEffectMod.MOD_ID + " (further occurrences on this id are suppressed)";
        if (error) {
            LOGGER.error(full);
        } else {
            LOGGER.warn(full);
        }
    }

    /**
     * 入站路由。解码失败/未知 opcode 只丢弃并记日志，不向外抛。
     *
     * <p>线程模型：两侧回调均发生在 netty 线程。客户端方向的处理逻辑自行管理线程
     * （GUI 等经 {@code ClientProxy#runOnClientThread} 用 {@code Minecraft#addScheduledTask}
     * 回主线程）；服务端方向由 {@code MinecraftServer#addScheduledTask} 投递到服务端主线程执行
     * —— 1.12.2 的 MinecraftServer 已内置线程安全的任务队列（synchronized），直接使用官方通道，
     * 不再需要 1.7.10 时为凑主线程而自建 SERVER_TASKS 队列 + 每 tick 排空。
     */
    static void route(ByteBuf buf, boolean serverSide, EntityPlayerMP player) {
        Map<Byte, BiConsumer<IPacket, EntityPlayerMP>> handlers = serverSide ? SERVER : CLIENT;
        try {
            if (buf.readableBytes() < 1) {
                LOGGER.warn("Dropped empty packet on channel " + AreaEffectMod.MOD_ID);
                return;
            }
            byte id = buf.readByte();
            Class<? extends IPacket> type = BY_ID.get(id);
            if (type == null) {
                reportOnce(id, "Dropped packet with unknown id " + id, false);
                return;
            }
            BiConsumer<IPacket, EntityPlayerMP> handler = handlers.get(id);
            if (handler == null) {
                reportOnce(id, "No handler bound for id " + id, true);
                return;
            }
            IPacket packet = type.newInstance();
            packet.fromBytes(buf);
            if (serverSide) {
                // 服务端方向：投递到服务端主线程，与主线程业务串行化。
                // 解码到执行之间隔着若干 tick，玩家可能已断线，故执行前再确认一次连接有效。
                MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
                if (server != null) {
                    server.addScheduledTask(() -> {
                        if (!isConnected(player)) {
                            return;
                        }
                        handler.accept(packet, player);
                    });
                }
            } else {
                handler.accept(packet, player);
            }
        } catch (Throwable t) {
            LOGGER.error("Error handling packet on channel " + AreaEffectMod.MOD_ID, t);
        }
    }

    /**
     * 玩家连接是否仍然有效。
     *
     * <p>入站包在 netty 线程解码、投递到服务端主线程执行，这中间玩家可能已经断线；此时继续跑
     * 业务只有坏处：回执与广播都发不出去，而 {@code NetworkManager#scheduleOutboundPacket}
     * 对已关闭的 channel 并非丢弃而是<b>塞进出站队列</b>，只是白占内存。
     * 连接尚未绑定（{@code playerNetServerHandler} 为 null）同样按不可用处理。
     */
    private static boolean isConnected(EntityPlayerMP player) {
        return player != null && player.connection != null
                && player.connection.netManager.isChannelOpen();
    }
}