package org.soraworld.areaeffect.common.network;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.dimension.DimensionType;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.fml.network.simple.SimpleChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.soraworld.areaeffect.AreaEffectMod;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 自建轻量网络通道：对 1.13+ Forge {@link SimpleChannel} 的最小封装，取代每类手写
 * discriminator switch。保留"消息类 + opcode 注册表 + 类型化发送"，去掉"每条消息强制内嵌 Handler"。
 *
 * <p>1.13 起旧的 SimpleNetworkWrapper / FMLEventChannel / FMLProxyPacket 全部移除，
 * Forge 改为 SimpleChannel：registerMessage 五参式（id、消息类、编码器、解码器、处理器），
 * discriminator 由通道自动写入，入站处理器统一收到 {@code Supplier<NetworkEvent.Context>}，
 * 并经 {@link NetworkEvent.Context#enqueueWork(Runnable)} 自动投递到<b>本侧主线程</b>
 * （服务端主线程 / 客户端渲染线程），不再需要手工 addScheduledTask，也不再需要 1.12 时代的
 * InboundRouter —— 通道自身就是路由器。
 *
 * <p>方向性处理逻辑不在消息类上：由 {@code CommonProxy}/{@code ClientProxy}
 * 经 {@link #bindServer}/{@link #bindClient} 绑定；入站时按 {@link NetworkDirection}
 * （PLAY_TO_SERVER / PLAY_TO_CLIENT）选择本侧处理表。
 */
public final class PacketChannel {

    private static final Logger LOGGER = LogManager.getLogger(AreaEffectMod.MOD_NAME);

    /** 协议版本：两端不一致时 SimpleChannel 直接拒绝握手，避免新旧包格式互解。 */
    private static final String PROTOCOL_VERSION = "1.4.0";

    /** opcode → 消息类：注册查表（同时作为 SimpleChannel 的 discriminator）。 */
    private static final Map<Byte, Class<? extends IPacket>> BY_ID = new ConcurrentHashMap<>();
    /** 消息类 → opcode：发送/入站分派查表。 */
    private static final Map<Class<? extends IPacket>, Byte> BY_TYPE = new ConcurrentHashMap<>();
    /** 已告警过的 opcode：畸形包反复到达时只记一次，避免客户端刷屏拖垮日志。 */
    private static final Set<Byte> REPORTED_IDS = ConcurrentHashMap.newKeySet();
    /** 客户端方向处理逻辑：仅物理客户端绑定（ClientProxy），玩家参数恒为 null。 */
    private static final Map<Byte, BiConsumer<IPacket, ServerPlayerEntity>> CLIENT = new ConcurrentHashMap<>();
    /** 服务端方向处理逻辑：仅服务端侧绑定（CommonProxy），第二参为发包玩家。 */
    private static final Map<Byte, BiConsumer<IPacket, ServerPlayerEntity>> SERVER = new ConcurrentHashMap<>();

    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(AreaEffectMod.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private PacketChannel() {
    }

    /**
     * 注册 opcode ↔ 消息类型，并向 SimpleChannel 登记编解码器与统一处理器。
     * 须在任何收发之前完成，由 {@code CommonProxy} 的幂等注册入口保证整个进程恰执行一次。
     */
    public static <T extends IPacket> void register(int id, Class<T> type) {
        Byte key = (byte) id;
        if (BY_ID.put(key, type) != null) {
            throw new IllegalArgumentException("duplicate packet id " + id + " on channel " + AreaEffectMod.MOD_ID);
        }
        BY_TYPE.put(type, key);
        // 编码器：IPacket 契约与 1.12 相同（opcode/discriminator 由通道负责，负载只写字段）。
        BiConsumer<T, net.minecraft.network.PacketBuffer> encoder = (msg, buf) -> msg.toBytes(buf);
        // 解码器：反射调用无参构造器后 fromBytes（IPacket 实现类必须保留无参构造器）。
        java.util.function.Function<net.minecraft.network.PacketBuffer, T> decoder = buf -> {
            try {
                T packet = type.newInstance();
                packet.fromBytes(buf);
                return packet;
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalArgumentException("failed to decode packet " + type.getName(), e);
            }
        };
        CHANNEL.registerMessage(id, type, encoder, decoder, PacketChannel::handle);
    }

    /** SimpleChannel 统一入站处理器：按方向选处理表，并经 enqueueWork 投递到本侧主线程。 */
    private static void handle(IPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        boolean toServer = ctx.getDirection() == NetworkDirection.PLAY_TO_SERVER;
        ServerPlayerEntity sender = ctx.getSender();
        // enqueueWork 自动把任务排到本侧主线程（服务端主线程 / 客户端渲染线程），
        // 与 1.12 手工 MinecraftServer#addScheduledTask / Minecraft#addScheduledTask 等价但两侧统一。
        ctx.enqueueWork(() -> {
            Byte id = BY_TYPE.get(msg.getClass());
            if (id == null) {
                return;
            }
            Map<Byte, BiConsumer<IPacket, ServerPlayerEntity>> handlers = toServer ? SERVER : CLIENT;
            BiConsumer<IPacket, ServerPlayerEntity> handler = handlers.get(id);
            if (handler == null) {
                // 本侧缺处理逻辑，属代码缺陷（ERROR）
                reportOnce(id, "No handler bound for id " + id, true);
                return;
            }
            if (toServer) {
                // 解码到执行之间隔着若干 tick，玩家可能已断线，执行前再确认一次连接有效。
                if (!isConnected(sender)) {
                    return;
                }
            }
            handler.accept(msg, sender);
        });
        ctx.setPacketHandled(true);
    }

    /** 绑定客户端方向消息的处理逻辑（由 ClientProxy 调用）。 */
    public static <T extends IPacket> void bindClient(Class<T> type, Consumer<T> handler) {
        CLIENT.put(requireRegistered(type), (pkt, player) -> handler.accept(type.cast(pkt)));
    }

    /** 绑定服务端方向消息的处理逻辑（由服务端侧调用）；第二参为发包玩家。 */
    public static <T extends IPacket> void bindServer(Class<T> type, BiConsumer<T, ServerPlayerEntity> handler) {
        SERVER.put(requireRegistered(type), (pkt, player) -> handler.accept(type.cast(pkt), player));
    }

    private static Byte requireRegistered(Class<? extends IPacket> type) {
        Byte id = BY_TYPE.get(type);
        if (id == null) {
            throw new IllegalArgumentException("packet not registered on channel " + AreaEffectMod.MOD_ID + ": " + type.getName());
        }
        return id;
    }

    /** 发给指定玩家（服务端侧调用）。 */
    public static void sendTo(IPacket packet, ServerPlayerEntity player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /** 发往所连服务端（仅客户端侧调用）。 */
    public static void sendToServer(IPacket packet) {
        CHANNEL.sendToServer(packet);
    }

    /** 向全部在线玩家广播（服务端侧调用）。 */
    public static void sendToAll(IPacket packet) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
    }

    /**
     * 向指定维度内的全部玩家广播（服务端侧调用）。
     * 1.13+ 专用优化：直接用 {@link PacketDistributor#DIMENSION} 按维度追踪集合下发，
     * 取代 1.12 时代"取全服玩家列表 → 逐个比对 dimension → 逐个发送"。
     */
    public static void sendToDimension(IPacket packet, DimensionType dimension) {
        CHANNEL.send(PacketDistributor.DIMENSION.with(() -> dimension), packet);
    }

    /**
     * 同一 opcode 只告警一次。未绑定处理逻辑属于持续状态（构造错误）而非瞬时事件，
     * 逐包记录会被畸形包以极低成本刷爆日志。
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
     * 玩家连接是否仍然有效。
     *
     * <p>入站包在 netty 线程解码、经 enqueueWork 投递到服务端主线程执行，这中间玩家可能已经断线；
     * 此时继续跑业务只有坏处：回执与广播都发不出去，而对已关闭 channel 的发送并非丢弃而是塞进出站队列，
     * 只是白占内存。
     */
    private static boolean isConnected(ServerPlayerEntity player) {
        return player != null && player.connection != null
                && player.connection.netManager.isChannelOpen();
    }

    /** 供 proxy 取得底层通道（一般无需直接使用）。 */
    public static SimpleChannel raw() {
        return CHANNEL;
    }
}
