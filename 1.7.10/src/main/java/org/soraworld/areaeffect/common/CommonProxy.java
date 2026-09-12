package org.soraworld.areaeffect.common;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import org.soraworld.areaeffect.common.area.AreaTable;
import org.soraworld.areaeffect.common.handler.AreaServerHandler;
import org.soraworld.areaeffect.common.net.AreaSync;
import org.soraworld.areaeffect.common.network.Area;
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
 */
public class CommonProxy {

    /** 区域数据集。单机下客户端与服务端共用同一实例，故无需回发同步包。 */
    protected final AreaTable areas = new AreaTable();
    /** 每玩家选区状态（服务端权威）。 */
    protected final SelectionManager selections = new SelectionManager();
    protected final AreaServerHandler serverHandler = new AreaServerHandler(this);

    /** 配置与存档；由 {@link #initConfig(File)} 建立。 */
    protected AreaStore store;
    /** 入站请求处理；由 {@link #initConfig(File)} 与 store 一同建立。 */
    protected AreaRequests requests;

    /**
     * 当前选区工具。单机下服务端线程会在指令里改写它，而客户端线程（渲染/交互）与 netty 线程
     * 都要读，故声明为 volatile 保证可见性 —— 引用赋值本身是原子的，不需要加锁。
     */
    protected volatile Item tool = Items.wooden_axe;

    /** 网络消息是否已注册（每进程恰一次，见 registerAllMessagePackets）。 */
    private static boolean networkRegistered = false;

    /**
     * 建立配置与存盘组件。必须在 {@link #onPreInit} 之前调用一次：
     * 消息绑定会把入站请求交给 {@link #requests}。
     */
    public void initConfig(File configFile) {
        store = new AreaStore(new Configuration(configFile));
        requests = new AreaRequests(areas, selections, store);
    }

    public void onPreInit(FMLPreInitializationEvent event) {
        registerAllMessagePackets();
        bindServerPacketHandlers();
        FMLCommonHandler.instance().bus().register(serverHandler);
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
            if (player != null) {
                requests.onListRequest(player);
            }
        });
        PacketChannel.bindServer(MessageSetProps.class, (message, player) -> {
            if (player != null) {
                requests.onSetProps(player, message);
            }
        });
        PacketChannel.bindServer(MessageDeleteRequest.class, (message, player) -> {
            if (player != null) {
                requests.onDeleteRequest(player, message);
            }
        });
        PacketChannel.bindServer(MessageTpRequest.class, (message, player) -> {
            if (player != null) {
                requests.onTpRequest(player, message);
            }
        });
        PacketChannel.bindServer(MessageSelectShape.class, (message, player) -> {
            if (player != null) {
                requests.onSelectShape(player, message);
            }
        });
        PacketChannel.bindServer(MessageClickAir.class, (message, player) -> {
            if (player != null) {
                requests.onClickAir(player);
            }
        });
    }

    public void onInit(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(serverHandler);
    }

    /** 设置随世界存储的区域文件（一个世界一个文件）。 */
    public void setStoreFile(File file) {
        store.setFile(file);
    }

    /** 载入配置与随世界的区域数据，并按配置恢复选区工具。 */
    public void load() {
        store.load(areas);
        setSelectTool(store.readToolName());
    }

    /** 标记存档为脏（含把当前工具名写回内存配置），实际落盘合并到下一 tick 的 {@link #flushStore()}。 */
    public void save() {
        store.writeToolName(getToolName());
        store.markDirty();
    }

    /** 把脏存档落盘；由服务端每 tick 调用一次，服务端停止时也会兜底调用一次。 */
    public void flushStore() {
        store.flush(areas);
    }

    private void setSelectTool(String toolName) {
        try {
            Object object = Item.itemRegistry.getObject(toolName);
            if (object instanceof Item) {
                tool = (Item) object;
            } else {
                tool = Items.wooden_axe;
            }
        } catch (Throwable ignored) {
            tool = Items.wooden_axe;
        }
    }

    private String getToolName() {
        try {
            String object = Item.itemRegistry.getNameForObject(tool);
            if (object != null) {
                return object;
            }
        } catch (Throwable ignored) {
        }
        return "wooden_axe";
    }

    public void onSelectToolLeft(EntityPlayerMP player, Vec3i pos) {
        selections.clickLeft(player, pos);
    }

    public void onSelectToolRight(EntityPlayerMP player, Vec3i pos) {
        selections.clickRight(player, pos);
    }

    /** 清除玩家选区（并回发同步）。 */
    public void clearSelect(EntityPlayer player) {
        selections.clear(player);
    }

    /** 清除玩家选区；syncClient=false 时不回发同步包（登出时连接已断，发包无谓）。 */
    public void clearSelect(EntityPlayer player, boolean syncClient) {
        selections.clear(player, syncClient);
    }

    /** 玩家当前所在区域；不在任何区域内返回 null。 */
    public Area findAreaAt(EntityPlayer player) {
        return areas.findAt(player);
    }

    /** 按当前选区创建区域（指令入口，只创建空区域），业务逻辑在请求层。 */
    public void createArea(EntityPlayerMP player) {
        requests.create(player);
    }

    /** 向客户端同步当前选区工具（MP 客户端不读 config）。 */
    public void sendToolSync(EntityPlayerMP player) {
        PacketChannel.sendTo(new MessageToolSync(getToolName()), player);
    }

    /** 玩家登录时把已有区域全量推给他（单机下数据同源，无需推送）。 */
    public void sendAllAreasTo(EntityPlayerMP player) {
        if (Players.isDedicated()) {
            areas.byDim().forEach((dim, dimAreas) -> dimAreas.forEach((id, area) -> AreaSync.toPlayer(player, dim, id, area)));
        }
    }

    public void commandTool(EntityPlayerMP player) {
        ItemStack stack = player.getHeldItem();
        if (stack != null) {
            tool = stack.getItem();
            save();
            // 专用服客户端持有独立的 tool 副本，变更后须实时推送（单机共享字段，推送也无害）
            sendToolSync(player);
            Players.chatWith(player, "chat.tool.set", tool.getUnlocalizedName(stack) + ".name");
        } else {
            Players.chatWith(player, "chat.tool.get", tool.getUnlocalizedName() + ".name");
        }
    }

    public boolean isSelectTool(ItemStack stack) {
        return stack != null && stack.getItem().equals(tool);
    }

    /** OP 权限判定（与指令门禁一致）；见 {@link Players#canManage(ICommandSender)}。 */
    public boolean hasPerm(EntityPlayer player) {
        return Players.canManage(player);
    }

    /** 发送一条翻译键消息；见 {@link Players#chat(ICommandSender, String, Object...)}。 */
    public void sendChatTranslation(ICommandSender sender, String key, Object... args) {
        Players.chat(sender, key, args);
    }
}
