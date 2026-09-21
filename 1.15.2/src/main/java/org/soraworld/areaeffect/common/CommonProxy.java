package org.soraworld.areaeffect.common;

import net.minecraft.command.ICommandSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.dimension.DimensionType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.LogicalSidedProvider;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppingEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import org.soraworld.areaeffect.AreaEffectMod;
import org.soraworld.areaeffect.common.area.AreaTable;
import org.soraworld.areaeffect.common.command.AreaCommand;
import org.soraworld.areaeffect.common.handler.AreaServerHandler;
import org.soraworld.areaeffect.common.net.AreaSync;
import org.soraworld.areaeffect.common.network.MessageAreaDelete;
import org.soraworld.areaeffect.common.network.MessageAreaUpdate;
import org.soraworld.areaeffect.common.network.MessageClickAir;
import org.soraworld.areaeffect.common.network.MessageDeleteRequest;
import org.soraworld.areaeffect.common.network.MessageListReply;
import org.soraworld.areaeffect.common.network.MessageListRequest;
import org.soraworld.areaeffect.common.network.MessageSelectShape;
import org.soraworld.areaeffect.common.network.MessageSelection;
import org.soraworld.areaeffect.common.network.MessageSetProps;
import org.soraworld.areaeffect.common.network.MessageToolSync;
import org.soraworld.areaeffect.common.network.MessageTpRequest;
import org.soraworld.areaeffect.common.network.PacketChannel;
import org.soraworld.areaeffect.common.selection.SelectionManager;
import org.soraworld.areaeffect.common.server.AreaRequests;
import org.soraworld.areaeffect.common.storage.AreaStore;
import org.soraworld.areaeffect.common.util.Players;
import org.soraworld.areaeffect.common.util.Vec3i;

import java.io.File;

/**
 * 公共侧代理：网络注册与装配点。
 *
 * <p>本类<b>只保留生命周期装配与薄委托</b>，业务逻辑已按职责迁移到四个组件：
 * <ul>
 *   <li>{@link AreaTable} —— 区域数据、玩家所在区域查询与 id 分配</li>
 *   <li>{@link SelectionManager} —— 每玩家选区状态与同步下发</li>
 *   <li>{@link AreaStore} —— 配置与区域 NBT 持久化（含合并落盘）</li>
 *   <li>{@link AreaRequests} —— 入站请求的鉴权与业务编排</li>
 * </ul>
 * 无状态的权限/消息/环境判定收在 {@link Players}，区域广播收在 {@link AreaSync}。
 *
 * <p>客户端代理 {@link org.soraworld.areaeffect.client.ClientProxy} 继承本类，
 * 复用同一套装配与客户端侧的镜像数据（{@link #areas}）。
 *
 * <p>1.13 的生命周期差异：没有 {@code @SidedProxy} 与预初始化事件，装配钩子改为
 * {@link FMLCommonSetupEvent}（两侧）与 {@link FMLClientSetupEvent}（仅客户端）；
 * 配置与随世界的存档在 {@link FMLServerStartingEvent} 里建立 —— 专用服只进一次，
 * 单机（集成服）每次进入世界重建一次，与"一个世界一个文件"的语义一致。
 */
public class CommonProxy {

    /** 服务端权威区域数据集；客户端另有独立镜像 {@code ClientProxy#clientAreas}，二者不共享实例、经协议同步。 */
    protected final AreaTable areas = new AreaTable();
    /** 每玩家选区状态（服务端权威）。 */
    protected final SelectionManager selections = new SelectionManager();
    protected final AreaServerHandler serverHandler = new AreaServerHandler(this);

    /** 配置与存档；服务端启动时建立（见 {@link #onServerStarting}）。 */
    protected AreaStore store;
    /** 入站请求处理；与服务端存档一同建立。 */
    protected AreaRequests requests;

    /**
     * 服务端权威的当前选区工具，仅在服务端线程读写（指令改、config 载入、点击判定、下发同步）。
     * 客户端另有独立镜像 {@code ClientProxy#clientTool}，经 {@code MessageToolSync} 同步、不共享本字段。
     * 保留 volatile 仅作廉价的跨线程安全发布保险。
     */
    protected volatile Item tool = Items.WOODEN_AXE;

    /** 网络消息是否已注册（每进程恰一次，见 registerAllMessagePackets）。 */
    private static boolean networkRegistered = false;

    /** 两侧公共装配：注册全部消息、绑定服务端方向处理逻辑，并挂上服务端业务事件。 */
    public void onCommonSetup(FMLCommonSetupEvent event) {
        registerAllMessagePackets();
        bindServerPacketHandlers();
        // 1.13 的 ServerTick / PlayerEvent / PlayerInteractEvent 全部在
        // MinecraftForge.EVENT_BUS 上派发，handler 单点注册于此。
        MinecraftForge.EVENT_BUS.register(serverHandler);
    }

    /**
     * 客户端装配钩子；公共侧无事可做，由 {@link org.soraworld.areaeffect.client.ClientProxy} 覆写
     * （绑定客户端方向消息、注册按键与渲染挂钩）。这样入口类只需持有 {@link CommonProxy} 类型。
     */
    public void onClientSetup(FMLClientSetupEvent event) {
    }

    /**
     * 服务端启动：注册指令，建立配置与随世界的区域存档并载入。
     *
     * <p>每次服务端启动都新建一份 {@link AreaStore}：它的配置句柄在关服时会被关闭
     * （见 {@link #onServerStopping}），单机里退出世界再进另一个世界不能复用已关闭的句柄。
     * 入站处理层 {@link #requests} 随之一并重建 —— 消息绑定读的是本字段而非捕获的旧实例。
     */
    public void onServerStarting(FMLServerStartingEvent event) {
        AreaCommand.register(event.getCommandDispatcher(), this);
        File config = FMLPaths.CONFIGDIR.get().resolve(AreaEffectMod.MOD_ID + ".toml").toFile();
        store = new AreaStore(config);
        requests = new AreaRequests(areas, selections, store);
        // 1.14 的 MinecraftServer#getWorld 在这一版映射里仍是 SRG 名（func_71218_a），
        // 故改走 Forge 提供的维度→世界映射表（同一张表，语义等价且可读）
        ServerWorld overworld = event.getServer().forgeGetWorldMap().get(DimensionType.OVERWORLD);
        if (overworld != null) {
            // 1.14 的存档目录在 ServerWorld#getSaveHandler → SaveHandler#getWorldDirectory 上
            store.setFile(new File(overworld.getSaveHandler().getWorldDirectory(), AreaEffectMod.MOD_ID + ".dat"));
            load();
        }
    }

    /** 关服：兜底落盘并释放配置句柄；下次服务端启动会重建（专用服重开/单机换世界）。 */
    public void onServerStopping(FMLServerStoppingEvent event) {
        flushStore();
        if (store != null) {
            store.close();
            store = null;
            requests = null;
        }
    }

    /**
     * 注册全部 opcode ↔ 消息类型（幂等）：发送编码与接收解码共表，故无条件全量注册。
     * 单机时客户端与服务端同进程各会调用一次，静态标志保证只真正注册一次。
     */
    protected void registerAllMessagePackets() {
        if (networkRegistered) {
            return;
        }
        networkRegistered = true;
        PacketChannel.register(1, MessageAreaUpdate.class);
        PacketChannel.register(2, MessageAreaDelete.class);
        // 3、4 为历史遗留空缺（旧版消息已移除）。保留编号不回收：
        // 一旦复用，与旧客户端/旧服务端混连时会误解析成已删除的消息类型。
        PacketChannel.register(5, MessageSelection.class);
        PacketChannel.register(6, MessageListRequest.class);
        PacketChannel.register(7, MessageListReply.class);
        PacketChannel.register(8, MessageSetProps.class);
        PacketChannel.register(9, MessageDeleteRequest.class);
        PacketChannel.register(10, MessageTpRequest.class);
        PacketChannel.register(11, MessageSelectShape.class);
        PacketChannel.register(12, MessageToolSync.class);
        // 13 为历史遗留空缺（原冲突提示消息 MessageConflictAreas 已随"放开重叠"删除）。
        // 保留编号不回收：一旦复用，与旧包混连时会误解析成已删除的消息类型。
        PacketChannel.register(14, MessageClickAir.class);
    }

    /**
     * 绑定服务端方向消息的处理逻辑。lambda 只做"取玩家 + 转交请求层"，
     * 业务规则在 {@link AreaRequests} 里。
     */
    protected void bindServerPacketHandlers() {
        PacketChannel.bindServer(MessageListRequest.class, (message, player) -> {
            if (player != null && requests != null) {
                requests.onListRequest(player);
            }
        });
        PacketChannel.bindServer(MessageSetProps.class, (message, player) -> {
            if (player != null && requests != null) {
                requests.onSetProps(player, message);
            }
        });
        PacketChannel.bindServer(MessageDeleteRequest.class, (message, player) -> {
            if (player != null && requests != null) {
                requests.onDeleteRequest(player, message);
            }
        });
        PacketChannel.bindServer(MessageTpRequest.class, (message, player) -> {
            if (player != null && requests != null) {
                requests.onTpRequest(player, message);
            }
        });
        PacketChannel.bindServer(MessageSelectShape.class, (message, player) -> {
            if (player != null && requests != null) {
                requests.onSelectShape(player, message);
            }
        });
        PacketChannel.bindServer(MessageClickAir.class, (message, player) -> {
            if (player != null && requests != null) {
                requests.onClickAir(player);
            }
        });
    }

    /** 设置随世界存储的区域文件（一个世界一个文件）。 */
    public void setStoreFile(File file) {
        if (store != null) {
            store.setFile(file);
        }
    }

    /** 载入配置与随世界的区域数据，并按配置恢复选区工具。 */
    public void load() {
        if (store == null) {
            return;
        }
        store.load(areas);
        setSelectTool(store.readToolName());
    }

    /** 标记存档为脏（含把当前工具名写回内存配置），实际落盘合并到下一 tick 的 {@link #flushStore()}。 */
    public void save() {
        if (store == null) {
            return;
        }
        store.writeToolName(getToolName());
        store.markDirty();
    }

    /** 把脏存档落盘；由服务端每 tick 调用一次，服务端停止时也会兜底调用一次。 */
    public void flushStore() {
        if (store == null) {
            return;
        }
        store.flush(areas);
    }

    /**
     * 按名字解析选区工具。
     *
     * <p>1.13 起物品的数字 id 已废弃，{@code Item.getByNameOrId} 一并移除：这里统一按
     * 注册名（{@code mod:item}）查 {@link ForgeRegistries#ITEMS}，查不到回落到木斧。
     */
    private void setSelectTool(String toolName) {
        try {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(toolName));
            tool = item != null ? item : Items.WOODEN_AXE;
        } catch (Throwable ignored) {
            // 名字非法（非 mod:item 形式）或注册表未就绪：回落木斧
            tool = Items.WOODEN_AXE;
        }
    }

    private String getToolName() {
        try {
            ResourceLocation registryName = ForgeRegistries.ITEMS.getKey(tool);
            if (registryName != null) {
                return registryName.toString();
            }
        } catch (Throwable ignored) {
        }
        return "wooden_axe";
    }

    public void onSelectToolLeft(ServerPlayerEntity player, Vec3i pos) {
        selections.clickLeft(player, pos);
    }

    public void onSelectToolRight(ServerPlayerEntity player, Vec3i pos) {
        selections.clickRight(player, pos);
    }

    /** 清除玩家选区（并回发同步）。 */
    public void clearSelect(PlayerEntity player) {
        selections.clear(player);
    }

    /** 清除玩家选区；syncClient=false 时不回发同步包（登出时连接已断，发包无谓）。 */
    public void clearSelect(PlayerEntity player, boolean syncClient) {
        selections.clear(player, syncClient);
    }

    /** 按当前选区创建区域（指令入口，只创建空区域），业务逻辑在请求层。 */
    public void createArea(ServerPlayerEntity player) {
        if (requests != null) {
            requests.create(player);
        }
    }

    /** 向客户端同步当前选区工具（MP 客户端不读 config）。 */
    public void sendToolSync(ServerPlayerEntity player) {
        PacketChannel.sendTo(new MessageToolSync(getToolName()), player);
    }

    /** 玩家登录时把已有区域全量推给他；单机（集成服）同样走此协议回环，令客户端镜像与专用服一致地重建。 */
    public void sendAllAreasTo(ServerPlayerEntity player) {
        areas.byDim().forEach((dim, dimAreas) -> dimAreas.forEach((id, area) -> AreaSync.toPlayer(player, dim, id, area)));
    }

    public void commandTool(ServerPlayerEntity player) {
        ItemStack stack = player.getHeldItemMainhand();
        // 空手为 ItemStack.EMPTY（非 null），须用 isEmpty() 判定：否则空手也会把工具设成空气
        if (!stack.isEmpty()) {
            tool = stack.getItem();
            save();
            // 每个在线 OP 的客户端 tool 镜像（clientTool）独立，不广播则其他 OP 重登前停留在旧物品。
            // 发送给所有在线 OP（含操作者本人），单机同样经此回环更新镜像。
            broadcastToolSync();
            Players.chatWith(player, "chat.tool.set", tool.getTranslationKey() + ".name");
        } else {
            Players.chatWith(player, "chat.tool.get", tool.getTranslationKey() + ".name");
        }
    }

    /** 把当前选区工具同步给所有在线 OP；服务端不存在时静默返回。 */
    private void broadcastToolSync() {
        // 1.12 的 FMLCommonHandler#getMinecraftServerInstance 已移除：当前服务的服务端实例
        // 从 1.13 的逻辑侧提供者取（专用服与单机集成服都注册在 SERVER 侧）。
        MinecraftServer server = LogicalSidedProvider.INSTANCE.get(LogicalSide.SERVER);
        if (server == null) {
            return;
        }
        for (ServerPlayerEntity obj : server.getPlayerList().getPlayers()) {
            if (Players.canManage(obj)) {
                sendToolSync(obj);
            }
        }
    }

    public boolean isSelectTool(ItemStack stack) {
        // 空手为 ItemStack.EMPTY，用 isEmpty() 明确排除
        return stack != null && !stack.isEmpty() && stack.getItem().equals(tool);
    }

    /** OP 权限判定（与指令门禁一致）；见 {@link Players#canManage(net.minecraft.command.ICommandSource)}。 */
    public boolean hasPerm(PlayerEntity player) {
        return Players.canManage(player);
    }

    /** 发送一条翻译键消息；见 {@link Players#chat(ICommandSource, String, Object...)}。 */
    public void sendChatTranslation(ICommandSource sender, String key, Object... args) {
        Players.chat(sender, key, args);
    }
}
