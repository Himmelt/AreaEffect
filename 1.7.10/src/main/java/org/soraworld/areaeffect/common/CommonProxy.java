package org.soraworld.areaeffect.common;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.soraworld.areaeffect.common.effect.AreaEffect;
import org.soraworld.areaeffect.common.effect.EffectTypes;
import org.soraworld.areaeffect.common.handler.AreaServerHandler;
import org.soraworld.areaeffect.common.network.Area;
import org.soraworld.areaeffect.common.network.MessageAreaDelete;
import org.soraworld.areaeffect.common.network.MessageAreaUpdate;
import org.soraworld.areaeffect.common.network.MessageClickAir;
import org.soraworld.areaeffect.common.network.MessageConflictAreas;
import org.soraworld.areaeffect.common.network.MessageDeleteRequest;
import org.soraworld.areaeffect.common.network.MessageListReply;
import org.soraworld.areaeffect.common.network.MessageListRequest;
import org.soraworld.areaeffect.common.network.MessageSelection;
import org.soraworld.areaeffect.common.network.MessageSetProps;
import org.soraworld.areaeffect.common.network.MessageTpRequest;
import org.soraworld.areaeffect.common.network.PacketChannel;
import org.soraworld.areaeffect.common.network.MessageSelectShape;
import org.soraworld.areaeffect.common.network.MessageToolSync;
import org.soraworld.areaeffect.common.shape.AreaShape;
import org.soraworld.areaeffect.common.shape.Selection;
import org.soraworld.areaeffect.common.shape.ShapeTypes;
import org.soraworld.areaeffect.common.util.Vec3d;
import org.soraworld.areaeffect.common.util.Vec3i;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CommonProxy {

    private static final Logger LOGGER = LogManager.getLogger("AreaEffect");

    protected final Map<UUID, Selection> selections = new ConcurrentHashMap<>();
    protected final Map<Integer, Map<Integer, Area>> lightAreas = new ConcurrentHashMap<>();
    protected final AreaServerHandler serverHandler = new AreaServerHandler(this);
    public Configuration config;
    protected File storeFile = null;
    protected Item tool = Items.wooden_axe;
    protected int AREA_ID = 0;

    /**
     * 存档是否有未落盘的改动。NBT 落盘合并到 {@link #flushStore()}（每服务端 tick 一次），
     * 避免在 GUI 里连续调参时每次改动都全量重写整个存档文件。
     */
    private volatile boolean storeDirty = false;

    /** 网络消息是否已注册（每进程恰一次，见 registerAllMessagePackets）。 */
    private static boolean networkRegistered = false;

    public void onPreInit(FMLPreInitializationEvent event) {
        registerAllMessagePackets();
        bindServerPacketHandlers();
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(serverHandler);
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
        PacketChannel.register(13, MessageConflictAreas.class);
        PacketChannel.register(14, MessageClickAir.class);
    }

    /**
     * 绑定服务端方向消息的处理逻辑。lambda 位于公共层，引用服务端逻辑方法。
     */
    protected void bindServerPacketHandlers() {
        PacketChannel.bindServer(MessageListRequest.class, (message, player) -> {
            if (player != null) {
                handleListRequest(player);
            }
        });
        PacketChannel.bindServer(MessageSetProps.class, (message, player) -> {
            if (player != null) {
                handleSetProps(player, message);
            }
        });
        PacketChannel.bindServer(MessageDeleteRequest.class, (message, player) -> {
            if (player != null) {
                handleDeleteRequest(player, message);
            }
        });
        PacketChannel.bindServer(MessageTpRequest.class, (message, player) -> {
            if (player != null) {
                handleTpRequest(player, message);
            }
        });
        PacketChannel.bindServer(MessageSelectShape.class, (message, player) -> {
            if (player != null) {
                handleSelectShape(player, message);
            }
        });
        PacketChannel.bindServer(MessageClickAir.class, (message, player) -> {
            if (player != null) {
                handleClickAir(player);
            }
        });
    }

    public void onInit(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(serverHandler);
    }

    /**
     * 设置随世界存储的区域文件路径（一个世界一个文件）。
     */
    public void setStoreFile(File file) {
        this.storeFile = file;
    }

    public void load() {
        config.load();
        setSelectTool(config.getString("tool", "general", "wooden_axe", "Select Tool"));
        lightAreas.clear();
        AREA_ID = 0;
        storeDirty = false;
        if (storeFile != null && storeFile.exists()) {
            readAreasNbt(storeFile);
        }
    }

    /**
     * 标记存档为脏（含把当前工具名写回内存配置）。真正的落盘由 {@link #flushStore()} 合并执行，
     * 因此本方法可以在一次 GUI 操作里被反复调用而不产生重复 IO。
     */
    public void save() {
        config.get("general", "tool", "wooden_axe", "Select Tool").set(getToolName());
        storeDirty = true;
    }

    /**
     * 把脏存档落盘（配置文件 + 区域 NBT）。由 {@code AreaServerHandler} 每服务端 tick 调用，
     * 服务端停止时也会调用一次（{@code AreaEffectMod#onServerStopping}），
     * 因此改动最多延迟一个 tick 写入，正常关服不会丢失数据。
     */
    public void flushStore() {
        if (!storeDirty) {
            return;
        }
        storeDirty = false;
        config.save();
        if (storeFile != null) {
            writeAreasNbt(storeFile);
        }
    }

    /**
     * 从随世界的 NBT 文件读入区域（一个世界一个文件）。
     */
    private void readAreasNbt(File file) {
        try {
            NBTTagCompound root;
            try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
                root = CompressedStreamTools.readCompressed(in);
            }
            NBTTagList areas = root.getTagList("areas", 10);
            for (int i = 0; i < areas.tagCount(); i++) {
                NBTTagCompound tag = areas.getCompoundTagAt(i);
                int dim = tag.getInteger("dim");
                Area area;
                if (tag.hasKey("shape")) {
                    AreaShape shape = ShapeTypes.fromNbt(tag.getCompoundTag("shape"));
                    if (shape == null) {
                        continue;
                    }
                    area = new Area(shape, 100.0F, 1.0F);
                } else {
                    // 旧存档兼容：无 shape 键按 box 从 x1..z2 读取
                    area = Area.box(tag.getInteger("x1"), tag.getInteger("y1"), tag.getInteger("z1"),
                            tag.getInteger("x2"), tag.getInteger("y2"), tag.getInteger("z2"),
                            100.0F, 1.0F);
                }
                area.setRemark(tag.getString("remark"));
                area.setEffects(readEffectsNbt(tag.getTagList("effects", 10)));
                area.id = tag.getInteger("id");
                lightAreas.computeIfAbsent(dim, d -> new ConcurrentHashMap<Integer, Area>()).put(area.id, area);
                if (area.id > AREA_ID) {
                    AREA_ID = area.id;
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("读取区域存档失败: {}", file, t);
        }
    }

    /**
     * 从 NBT 效果列表反序列化出效果集合。
     */
    private List<AreaEffect> readEffectsNbt(NBTTagList list) {
        List<AreaEffect> effects = new ArrayList<>();
        for (int i = 0; i < list.tagCount(); i++) {
            AreaEffect effect = EffectTypes.fromNbt(list.getCompoundTagAt(i));
            if (effect != null) {
                effects.add(effect);
            }
        }
        return effects;
    }

    /**
     * 把区域以 NBT 原子写入随世界文件：先写临时文件再替换，避免崩溃损坏。
     */
    private void writeAreasNbt(File file) {
        NBTTagCompound root = new NBTTagCompound();
        NBTTagList areas = new NBTTagList();
        lightAreas.forEach((dim, map) -> map.values().forEach(area -> {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("dim", dim);
            tag.setInteger("id", area.id);
            NBTTagCompound shapeTag = new NBTTagCompound();
            ShapeTypes.writeNbt(area.shape(), shapeTag);
            tag.setTag("shape", shapeTag);
            tag.setString("remark", area.getRemark());
            NBTTagList effectList = new NBTTagList();
            for (AreaEffect effect : area.getEffects()) {
                NBTTagCompound effectTag = new NBTTagCompound();
                effectTag.setString("type", effect.typeId());
                effect.writeToNbt(effectTag);
                effectList.appendTag(effectTag);
            }
            tag.setTag("effects", effectList);
            areas.appendTag(tag);
        }));
        root.setTag("areas", areas);
        File tmp = new File(file.getPath() + ".tmp");
        try {
            try (DataOutputStream out = new DataOutputStream(new FileOutputStream(tmp))) {
                CompressedStreamTools.writeCompressed(root, out);
            }
            if (!tmp.renameTo(file)) {
                java.nio.file.Files.copy(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                tmp.delete();
            }
        } catch (Throwable t) {
            LOGGER.warn("写入区域存档失败: {}", file, t);
        }
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
        selectionOf(player).onClickLeft(pos);
        updateSelection(player);
    }

    public void onSelectToolRight(EntityPlayerMP player, Vec3i pos) {
        selectionOf(player).onClickRight(pos);
        updateSelection(player);
    }

    /** 切换选区形状（重置锚点）。 */
    public void selectShape(EntityPlayerMP player, String type) {
        selectionOf(player).reset(type);
        updateSelection(player);
    }

    public Selection selectionOf(EntityPlayerMP player) {
        return selections.computeIfAbsent(player.getUniqueID(), uuid -> new Selection());
    }

    /**
     * 将当前选区同步到客户端，由客户端自绘选区线框。
     */
    protected void updateSelection(EntityPlayerMP player) {
        Selection sel = selections.get(player.getUniqueID());
        PacketChannel.sendTo(new MessageSelection(sel), player);
    }

    /**
     * 服务端响应列表请求：校验 OP 权限后回复当前维度的区域列表。
     */
    public void handleListRequest(EntityPlayerMP player) {
        if (!hasPerm(player)) {
            // 与其它入口保持一致：必须给出反馈。静默返回会让玩家以为按键失效/面板坏了
            sendChatTranslation(player, "chat.perm.denied");
            return;
        }
        Map<Integer, Area> areas = lightAreas.get(player.dimension);
        List<Area> list;
        if (areas == null || areas.isEmpty()) {
            list = Collections.emptyList();
        } else {
            list = new ArrayList<>(areas.values());
            list.sort((a, b) -> Integer.compare(a.id, b.id));
        }
        PacketChannel.sendTo(new MessageListReply(player.dimension, list), player);
    }

    /**
     * 服务端应用写回：再次校验 OP 权限后再修改，并广播给所有客户端。
     */
    public void handleSetProps(EntityPlayerMP player, MessageSetProps packet) {
        if (!hasPerm(player)) {
            sendChatTranslation(player, "chat.perm.denied");
            return;
        }
        Map<Integer, Area> areas = lightAreas.get(packet.dim);
        if (areas == null) {
            return;
        }
        Area area = areas.get(packet.id);
        if (area == null) {
            sendChatTranslation(player, "chat.area.notfound");
            return;
        }
        // 以客户端回写的整组效果替换：逐条参数边界处理，并按类型去重
        // （与 getEffects "每种效果类型至多一个实例" 的约定一致，重复取首个）
        List<AreaEffect> incoming = packet.effects;
        List<AreaEffect> deduped = new ArrayList<>(incoming.size());
        Set<String> seenTypes = new HashSet<>();
        for (AreaEffect effect : incoming) {
            effect.sanitize();
            if (seenTypes.add(effect.typeId())) {
                deduped.add(effect);
            }
        }
        area.setEffects(deduped);
        area.setRemark(packet.remark);
        save();
        sendUpdateToAll(packet.dim, area.id, area);
    }

    /**
     * 服务端按 id 删除区域：再次校验 OP 权限，成功则持久化并广播（含单机，
     * 客户端依赖广播统一刷新本地数据，本地不提前移除以免竞态）。
     */
    public void handleDeleteRequest(EntityPlayerMP player, MessageDeleteRequest packet) {
        if (!hasPerm(player)) {
            sendChatTranslation(player, "chat.perm.denied");
            return;
        }
        Map<Integer, Area> areas = lightAreas.get(packet.dim);
        if (areas == null || areas.remove(packet.id) == null) {
            sendChatTranslation(player, "chat.area.notfound");
            return;
        }
        save();
        sendDeleteToAll(packet.dim, packet.id);
    }

    /**
     * 服务端按 id 传送：再次校验 OP 权限后跳转到区域中心。
     */
    public void handleTpRequest(EntityPlayerMP player, MessageTpRequest packet) {
        if (!hasPerm(player)) {
            sendChatTranslation(player, "chat.perm.denied");
            return;
        }
        tpAreaById(player, packet.id);
    }

    /**
     * 服务端响应选区形状设置：校验 OP 权限，并按 {@link ShapeTypes#ALL} 白名单校验类型。
     * 形状类型来自客户端，不校验就会让任意字符串进入选区状态并随 {@code MessageSelection}
     * 回显到所有客户端。
     */
    public void handleSelectShape(EntityPlayerMP player, MessageSelectShape packet) {
        if (!hasPerm(player)) {
            sendChatTranslation(player, "chat.perm.denied");
            return;
        }
        if (ShapeTypes.isValid(packet.type)) {
            selectShape(player, packet.type);
        } else {
            LOGGER.warn("Rejected unknown select shape '{}' from {}", packet.type, player.getCommandSenderName());
        }
    }

    /**
     * 服务端接收右键空气事件：与右键方块共用 {@link #onSelectToolRight} 入口
     * （Selection.onClickRight），作为多边形选区的撤回触发；二点形状无坐标忽略。
     */
    public void handleClickAir(EntityPlayerMP player) {
        if (!hasPerm(player)) {
            return;
        }
        onSelectToolRight(player, null);
    }

    /** 向客户端同步当前选区工具（MP 客户端不读 config）。 */
    public void sendToolSync(EntityPlayerMP player) {
        PacketChannel.sendTo(new MessageToolSync(getToolName()), player);
    }

    public void sendAllAreasTo(EntityPlayerMP player) {
        if (isDedicated()) {
            lightAreas.forEach((dim, areas) -> areas.forEach((id, area) -> sendUpdateTo(player, dim, id, area)));
        }
    }

    public void sendUpdateTo(EntityPlayerMP player, int dim, int id, Area area) {
        PacketChannel.sendTo(new MessageAreaUpdate(dim, id, area), player);
    }

    public void sendUpdateToAll(int dim, int id, Area area) {
        PacketChannel.sendToAll(new MessageAreaUpdate(dim, id, area));
    }

    public void sendDeleteToAll(int dim, int id) {
        PacketChannel.sendToAll(new MessageAreaDelete(dim, id));
    }

    public void createArea(EntityPlayerMP player, float lightness, float duration) {
        Selection sel = selections.get(player.getUniqueID());
        if (sel != null && sel.isBuildable()) {
            AreaShape intent = sel.build();
            List<Integer> conflicts = findConflictIds(player.dimension, intent);
            if (!conflicts.isEmpty()) {
                sendChatTranslation(player, "chat.create.conflict");
                // 把冲突区域的框线都显示出来
                PacketChannel.sendTo(new MessageConflictAreas(player.dimension, conflicts), player);
                return;
            }
            Area area = addArea(player.dimension, intent, lightness, duration);
            if (area == null) {
                sendChatTranslation(player, "chat.create.conflict");
            } else {
                sendChatTranslation(player, "chat.create.done");
                if (isDedicated()) {
                    sendUpdateToAll(player.dimension, area.id, area);
                }
                // 创建成功后重置选区（保留形状类型，锚点清空），可直接开始下一个选区
                sel.anchors.clear();
                updateSelection(player);
                save();
            }
        } else {
            sendChatTranslation(player, "chat.create.noselect");
        }
    }

    /** 找出与给定形状冲突（方块级精确判定）的全部已存区域 id。 */
    public List<Integer> findConflictIds(int dim, AreaShape intent) {
        List<Integer> conflicts = new ArrayList<>();
        for (Map.Entry<Integer, Area> entry : lightAreas.getOrDefault(dim, Collections.emptyMap()).entrySet()) {
            if (intent.conflict(entry.getValue().shape())) {
                conflicts.add(entry.getKey());
            }
        }
        return conflicts;
    }

    public Area addArea(int dim, AreaShape shape, float lightness, float duration) {
        Area area = new Area(shape, lightness, duration);
        if (checkConflict(dim, area)) {
            return null;
        } else {
            AREA_ID++;
            area.id = AREA_ID;
            lightAreas.computeIfAbsent(dim, d -> new ConcurrentHashMap<Integer, Area>()).put(AREA_ID, area);
            return area;
        }
    }

    public Area findAreaAt(EntityPlayer player) {
        for (Map.Entry<Integer, Area> entry : lightAreas.getOrDefault(player.dimension, Collections.emptyMap()).entrySet()) {
            Area area = entry.getValue();
            if (area.contains(new Vec3d(player))) {
                return area;
            }
        }
        return null;
    }

    public boolean checkConflict(int dim, Area intent) {
        for (Area area : lightAreas.getOrDefault(dim, Collections.emptyMap()).values()) {
            if (intent.conflict(area)) {
                return true;
            }
        }
        return false;
    }

    public void clearSelect(EntityPlayer player) {
        clearSelect(player, true);
    }

    /** 清除玩家选区；syncClient=false 时不回发同步包（登出时连接已断，发包无谓）。 */
    public void clearSelect(EntityPlayer player, boolean syncClient) {
        selections.remove(player.getUniqueID());
        if (syncClient && player instanceof EntityPlayerMP) {
            updateSelection((EntityPlayerMP) player);
        }
    }

    public boolean hasPerm(EntityPlayer player) {
        return player.canCommandSenderUseCommand(2, "gamemode");
    }

    public void sendChatTranslation(ICommandSender sender, String key, Object... args) {
        sender.addChatMessage(new ChatComponentTranslation(key, args));
    }

    public void sendChatTranslation2(EntityPlayerMP player, String key, String objKey) {
        player.addChatMessage(new ChatComponentTranslation(key, new ChatComponentTranslation(objKey)));
    }

    public void commandTool(EntityPlayerMP player) {
        ItemStack stack = player.getHeldItem();
        if (stack != null) {
            tool = stack.getItem();
            save();
            // 专用服客户端持有独立的 tool 副本，变更后须实时推送（单机共享字段，推送也无害）
            sendToolSync(player);
            sendChatTranslation2(player, "chat.tool.set", tool.getUnlocalizedName(stack) + ".name");
        } else {
            sendChatTranslation2(player, "chat.tool.get", tool.getUnlocalizedName() + ".name");
        }
    }

    public void tpAreaById(EntityPlayerMP player, int id) {
        if (player == null) {
            return;
        }
        for (Map.Entry<Integer, Map<Integer, Area>> entry : lightAreas.entrySet()) {
            int dim = entry.getKey();
            Map<Integer, Area> areas = entry.getValue();
            Area area = areas.get(id);
            if (area != null) {
                if (player.dimension != dim) {
                    player.travelToDimension(dim);
                }
                area.center(player);
                return;
            }
        }
        sendChatTranslation(player, "chat.area.notfound");
    }

    public boolean isSelectTool(ItemStack stack) {
        return stack != null && stack.getItem().equals(tool);
    }

    /**
     * 当前是否专用服务端。单机下客户端与服务端加载的是同一 proxy 实例、数据本就同源，
     * 调用点据此跳过对本地玩家的回发同步（推送也无害，只是无谓）。
     */
    public static boolean isDedicated() {
        MinecraftServer server = MinecraftServer.getServer();
        return server != null && server.isDedicatedServer();
    }
}