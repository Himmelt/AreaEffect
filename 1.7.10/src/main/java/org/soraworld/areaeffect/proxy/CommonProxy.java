package org.soraworld.areaeffect.proxy;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.network.FMLEventChannel;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.internal.FMLProxyPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
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
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import org.soraworld.areaeffect.handler.EventBusHandler;
import org.soraworld.areaeffect.handler.FMLHandler;
import org.soraworld.areaeffect.handler.FMLServerHandler;
import org.soraworld.areaeffect.network.Area;
import org.soraworld.areaeffect.network.AreaPacket;
import org.soraworld.areaeffect.util.Vec3d;
import org.soraworld.areaeffect.util.Vec3i;

import java.io.*;
import java.util.*;

public class CommonProxy {

    public static final byte UPDATE = 1;
    public static final byte DELETE = 2;
    public static final byte LIGHTNESS = 3;
    public static final byte DURATION = 4;
    public static final byte SELECTION = 5;
    public static final byte REQ_LIST = 6;
    public static final byte LIST_REPLY = 7;
    public static final byte REQ_SET = 8;

    protected final HashMap<UUID, Vec3i> pos1s = new HashMap<>();
    protected final HashMap<UUID, Vec3i> pos2s = new HashMap<>();
    protected final HashMap<Integer, HashMap<Integer, Area>> lightAreas = new HashMap<>();
    protected final FMLEventChannel channel = NetworkRegistry.INSTANCE.newEventDrivenChannel("light");
    public Configuration config;
    protected File storeFile = null;
    protected Item tool = Items.wooden_axe;
    protected float duration = 1.0F;
    protected int AREA_ID = 0;

    public void onPreInit(FMLPreInitializationEvent event) {
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new FMLHandler(this));
        channel.register(new FMLServerHandler(this));
    }

    public void onInit(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new EventBusHandler(this));
    }

    public void regEventBus(Object object) {
        MinecraftForge.EVENT_BUS.register(object);
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
                        tag.getFloat("lightness"), tag.getFloat("duration"));
                area.id = tag.getInteger("id");
                lightAreas.computeIfAbsent(dim, d -> new HashMap<>()).put(area.id, area);
                if (area.id > AREA_ID) {
                    AREA_ID = area.id;
                }
            }
        } catch (Throwable ignored) {
        }
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
            tag.setFloat("lightness", area.lightness);
            tag.setFloat("duration", area.duration);
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
            sendChatTranslation(player, "set.pos1", pos1);
        }
        updateSelection(player);
    }

    public void setPos2(EntityPlayerMP player, Vec3i pos2, boolean msg) {
        pos2s.put(player.getUniqueID(), pos2);
        if (msg) {
            sendChatTranslation(player, "set.pos2", pos2);
        }
        updateSelection(player);
    }

    /**
     * 将当前选区同步到客户端，由客户端自绘选区线框，不再依赖 WECUI。
     */
    protected void updateSelection(EntityPlayerMP player) {
        Vec3i pos1 = pos1s.get(player.getUniqueID());
        Vec3i pos2 = pos2s.get(player.getUniqueID());
        ByteBuf buf = Unpooled.buffer();
        buf.writeByte(SELECTION);
        AreaPacket.Selection.encode(new AreaPacket.Selection(pos1, pos2), buf);
        sendTo(buf, player);
    }

    private void sendTo(ByteBuf buf, EntityPlayerMP player) {
        channel.sendTo(new FMLProxyPacket(buf, "light"), player);
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
        ByteBuf buf = Unpooled.buffer();
        buf.writeByte(LIST_REPLY);
        AreaPacket.ListReply.encode(new AreaPacket.ListReply(player.dimension, list), buf);
        sendTo(buf, player);
    }

    /**
     * 服务端应用写回：再次校验 OP 权限后再修改，并广播给所有客户端。
     */
    public void handleSetProps(EntityPlayerMP player, AreaPacket.SetProps packet) {
        if (!hasPerm(player)) {
            sendChatTranslation(player, "perm.denied");
            return;
        }
        Map<Integer, Area> areas = lightAreas.get(packet.dim);
        if (areas == null) {
            return;
        }
        Area area = areas.get(packet.id);
        if (area == null) {
            sendChatTranslation(player, "areaIdNotFound");
            return;
        }
        area.lightness = applyLightness(packet.lightness);
        area.duration = applyDuration(packet.duration);
        save();
        sendUpdateToAll(packet.dim, area.id, area);
        sendChatTranslation(player, "gui.set.done");
    }

    private float applyLightness(float lightness) {
        return Float.isNaN(lightness) ? 90.0F : Math.max(0.0F, Math.min(100.0F, lightness));
    }

    private float applyDuration(float duration) {
        return !(duration > 0.0F) ? 1.0F : Math.min(60.0F, duration);
    }

    private void sendToAll(ByteBuf buf) {
        channel.sendToAll(new FMLProxyPacket(buf, "light"));
    }

    public void sendAllAreasTo(EntityPlayerMP player) {
        if (isDedicated(player)) {
            lightAreas.forEach((dim, areas) -> areas.forEach((id, area) -> sendUpdateTo(player, dim, id, area)));
        }
    }

    public void sendUpdateTo(EntityPlayerMP player, int dim, int id, Area area) {
        ByteBuf buf = Unpooled.buffer();
        buf.writeByte(UPDATE);
        AreaPacket.Update.encode(new AreaPacket.Update(dim, id, area), buf);
        sendTo(buf, player);
    }

    public void sendUpdateToAll(int dim, int id, Area area) {
        ByteBuf buf = Unpooled.buffer();
        buf.writeByte(UPDATE);
        AreaPacket.Update.encode(new AreaPacket.Update(dim, id, area), buf);
        sendToAll(buf);
    }

    public void sendDeleteToAll(int dim, int id) {
        ByteBuf buf = Unpooled.buffer();
        buf.writeByte(DELETE);
        AreaPacket.Delete.encode(new AreaPacket.Delete(dim, id), buf);
        sendToAll(buf);
    }

    public void sendLightnessToAll(int dim, int id, float lightness) {
        ByteBuf buf = Unpooled.buffer();
        buf.writeByte(LIGHTNESS);
        AreaPacket.Lightness.encode(new AreaPacket.Lightness(dim, id, lightness), buf);
        sendToAll(buf);
    }

    public void sendDurationToAll(int dim, int id, float duration) {
        ByteBuf buf = Unpooled.buffer();
        buf.writeByte(DURATION);
        AreaPacket.Duration.encode(new AreaPacket.Duration(dim, id, duration), buf);
        sendToAll(buf);
    }

    public void createArea(EntityPlayerMP player, float lightness, float duration) {
        Vec3i pos1 = pos1s.get(player.getUniqueID());
        Vec3i pos2 = pos2s.get(player.getUniqueID());
        if (pos1 != null && pos2 != null) {
            Area area = addArea(player.dimension, pos1, pos2, lightness, duration);
            if (area == null) {
                sendChatTranslation(player, "create.conflict");
            } else {
                sendChatTranslation(player, "create.area");
                if (isDedicated(player)) {
                    sendUpdateToAll(player.dimension, area.id, area);
                }
                save();
            }
        } else {
            sendChatTranslation(player, "notSelect");
        }
    }

    public Area addArea(int dim, Vec3i pos1, Vec3i pos2, float lightness, float duration) {
        Area area = new Area(pos1.x, pos1.y, pos1.z, pos2.x, pos2.y, pos2.z, lightness, duration);
        if (checkConflict(dim, area)) {
            return null;
        } else {
            AREA_ID++;
            area.id = AREA_ID;
            lightAreas.computeIfAbsent(dim, d -> new HashMap<>()).put(AREA_ID, area);
            return area;
        }
    }

    public void deleteArea(EntityPlayerMP player) {
        Area area = findAreaAt(player);
        if (area != null) {
            lightAreas.getOrDefault(player.dimension, new HashMap<>()).remove(area.id);
            save();
            if (isDedicated(player)) {
                sendDeleteToAll(player.dimension, area.id);
            }
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

    public void sendAreaInfo(EntityPlayerMP player, int dim, int id, Area area) {
        ChatStyle style = new ChatStyle().setColor(EnumChatFormatting.GREEN).setBold(true)
                .setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/areaeffect tp " + id));
        IChatComponent click = new ChatComponentTranslation("text.click").setChatStyle(style);
        player.addChatMessage(new ChatComponentTranslation("info.list", id, dim, area.pos1(), area.pos2(), area.lightness, area.duration, click));
    }

    public void commandTool(EntityPlayerMP player) {
        ItemStack stack = player.getHeldItem();
        if (stack != null) {
            tool = stack.getItem();
            save();
            sendChatTranslation2(player, "tool.set", tool.getUnlocalizedName(stack) + ".name");
        } else {
            sendChatTranslation2(player, "tool.get", tool.getUnlocalizedName() + ".name");
        }
    }

    public void showList(EntityPlayerMP player, int dim, boolean all) {
        if (all) {
            lightAreas.forEach((dimId, dimAreas) -> dimAreas.forEach((id, area) -> sendAreaInfo(player, dimId, id, area)));
        } else {
            lightAreas.getOrDefault(dim, new HashMap<>()).forEach((id, area) -> sendAreaInfo(player, dim, id, area));
        }
    }

    public void tpAreaById(EntityPlayerMP player, int id) {
        if (player == null) {
            return;
        }
        for (Map.Entry<Integer, HashMap<Integer, Area>> entry : lightAreas.entrySet()) {
            int dim = entry.getKey();
            HashMap<Integer, Area> areas = entry.getValue();
            Area area = areas.get(id);
            if (area != null) {
                if (player.dimension != dim) {
                    player.travelToDimension(dim);
                    area.center(player);
                    sendChatTranslation(player, "areaTpSuccess");
                } else {
                    area.center(player);
                    sendChatTranslation(player, "areaTpSuccess");
                }
                return;
            }
        }
        sendChatTranslation(player, "areaIdNotFound");
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
