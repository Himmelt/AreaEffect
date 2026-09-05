package org.soraworld.areaeffect.common;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.event.ClickEvent;
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
import org.soraworld.areaeffect.common.effect.AreaEffect;
import org.soraworld.areaeffect.common.effect.EffectTypes;
import org.soraworld.areaeffect.common.handler.AreaServerHandler;
import org.soraworld.areaeffect.common.network.Area;
import org.soraworld.areaeffect.common.network.MessageAreaDelete;
import org.soraworld.areaeffect.common.network.MessageAreaUpdate;
import org.soraworld.areaeffect.common.network.MessageDeleteRequest;
import org.soraworld.areaeffect.common.network.MessageListReply;
import org.soraworld.areaeffect.common.network.MessageListRequest;
import org.soraworld.areaeffect.common.network.MessageSelection;
import org.soraworld.areaeffect.common.network.MessageSetProps;
import org.soraworld.areaeffect.common.network.MessageTpRequest;
import org.soraworld.areaeffect.common.network.PacketChannel;
import org.soraworld.areaeffect.common.util.Vec3d;
import org.soraworld.areaeffect.common.util.Vec3i;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CommonProxy {

    protected final HashMap<UUID, Vec3i> pos1s = new HashMap<>();
    protected final HashMap<UUID, Vec3i> pos2s = new HashMap<>();
    protected final Map<Integer, Map<Integer, Area>> lightAreas = new ConcurrentHashMap<>();
    protected final AreaServerHandler serverHandler = new AreaServerHandler(this);
    public Configuration config;
    protected File storeFile = null;
    protected Item tool = Items.wooden_axe;
    protected float duration = 1.0F;
    protected int AREA_ID = 0;

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
        PacketChannel.register(5, MessageSelection.class);
        PacketChannel.register(6, MessageListRequest.class);
        PacketChannel.register(7, MessageListReply.class);
        PacketChannel.register(8, MessageSetProps.class);
        PacketChannel.register(9, MessageDeleteRequest.class);
        PacketChannel.register(10, MessageTpRequest.class);
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
        if (storeFile != null && storeFile.exists()) {
            readAreasNbt(storeFile);
        }
    }

    public void save() {
        config.get("general", "tool", "wooden_axe", "Select Tool").set(getToolName());
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
                Area area = new Area(tag.getInteger("x1"), tag.getInteger("y1"), tag.getInteger("z1"),
                        tag.getInteger("x2"), tag.getInteger("y2"), tag.getInteger("z2"),
                        90.0F, 1.0F);
                area.setEffects(readEffectsNbt(tag.getTagList("effects", 10)));
                area.id = tag.getInteger("id");
                lightAreas.computeIfAbsent(dim, d -> new ConcurrentHashMap<Integer, Area>()).put(area.id, area);
                if (area.id > AREA_ID) {
                    AREA_ID = area.id;
                }
            }
        } catch (Throwable ignored) {
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
            tag.setInteger("x1", area.x1);
            tag.setInteger("y1", area.y1);
            tag.setInteger("z1", area.z1);
            tag.setInteger("x2", area.x2);
            tag.setInteger("y2", area.y2);
            tag.setInteger("z2", area.z2);
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
        } catch (Throwable ignored) {
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

    public void setPos1(EntityPlayerMP player, Vec3i pos1, boolean msg) {
        pos1s.put(player.getUniqueID(), pos1);
        if (msg) {
            sendChatTranslation(player, "chat.set.pos1", pos1);
        }
        updateSelection(player);
    }

    public void setPos2(EntityPlayerMP player, Vec3i pos2, boolean msg) {
        pos2s.put(player.getUniqueID(), pos2);
        if (msg) {
            sendChatTranslation(player, "chat.set.pos2", pos2);
        }
        updateSelection(player);
    }

    /**
     * 将当前选区同步到客户端，由客户端自绘选区线框。
     */
    protected void updateSelection(EntityPlayerMP player) {
        Vec3i pos1 = pos1s.get(player.getUniqueID());
        Vec3i pos2 = pos2s.get(player.getUniqueID());
        PacketChannel.sendTo(new MessageSelection(pos1, pos2), player);
    }

    /**
     * 服务端响应列表请求：校验 OP 权限后回复当前维度的区域列表。
     */
    public void handleListRequest(EntityPlayerMP player) {
        if (!hasPerm(player)) {
            return; // 无权限，静默忽略，客户端不会收到回复也就不会打开 GUI
        }
        Map<Integer, Area> areas = lightAreas.get(player.dimension);
        List<Area> list = areas == null ? Collections.emptyList() : new ArrayList<>(areas.values());
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
        // 以客户端回写的整组效果替换，并对每条做参数边界处理
        List<AreaEffect> incoming = packet.effects;
        for (AreaEffect effect : incoming) {
            effect.sanitize();
        }
        area.setEffects(incoming);
        save();
        sendUpdateToAll(packet.dim, area.id, area);
        sendChatTranslation(player, "chat.area.updated");
    }

    /**
     * 服务端按 id 删除区域：再次校验 OP 权限，成功则持久化并广播。
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
        if (isDedicated(player)) {
            sendDeleteToAll(packet.dim, packet.id);
        }
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

    public void sendAllAreasTo(EntityPlayerMP player) {
        if (isDedicated(player)) {
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
        Vec3i pos1 = pos1s.get(player.getUniqueID());
        Vec3i pos2 = pos2s.get(player.getUniqueID());
        if (pos1 != null && pos2 != null) {
            Area area = addArea(player.dimension, pos1, pos2, lightness, duration);
            if (area == null) {
                sendChatTranslation(player, "chat.create.conflict");
            } else {
                sendChatTranslation(player, "chat.create.done");
                if (isDedicated(player)) {
                    sendUpdateToAll(player.dimension, area.id, area);
                }
                save();
            }
        } else {
            sendChatTranslation(player, "chat.create.noselect");
        }
    }

    public Area addArea(int dim, Vec3i pos1, Vec3i pos2, float lightness, float duration) {
        Area area = new Area(pos1.x, pos1.y, pos1.z, pos2.x, pos2.y, pos2.z, lightness, duration);
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
        for (Map.Entry<Integer, Area> entry : lightAreas.getOrDefault(player.dimension, new HashMap<>()).entrySet()) {
            Area area = entry.getValue();
            if (area.contains(new Vec3d(player))) {
                area.id = entry.getKey();
                return area;
            }
        }
        return null;
    }

    public boolean checkConflict(int dim, Area intent) {
        for (Area area : lightAreas.getOrDefault(dim, new HashMap<>()).values()) {
            if (intent.conflict(area)) {
                return true;
            }
        }
        return false;
    }

    public void clearSelect(EntityPlayer player) {
        pos1s.remove(player.getUniqueID());
        pos2s.remove(player.getUniqueID());
        if (player instanceof EntityPlayerMP) {
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
                    area.center(player);
                    sendChatTranslation(player, "chat.area.tp");
                } else {
                    area.center(player);
                    sendChatTranslation(player, "chat.area.tp");
                }
                return;
            }
        }
        sendChatTranslation(player, "chat.area.notfound");
    }

    public boolean isSelectTool(ItemStack stack) {
        return stack != null && stack.getItem().equals(tool);
    }

    public static boolean isDedicated(EntityPlayerMP player) {
        MinecraftServer server = getServer(player);
        return server != null && server.isDedicatedServer();
    }

    public static MinecraftServer getServer(EntityPlayerMP player) {
        return MinecraftServer.getServer();
    }
}