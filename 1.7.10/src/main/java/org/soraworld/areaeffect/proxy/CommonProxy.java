package org.soraworld.areaeffect.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S3FPacketCustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.event.ClickEvent;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.network.FMLEventChannel;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.internal.FMLProxyPacket;
import org.soraworld.areaeffect.handler.EventBusHandler;
import org.soraworld.areaeffect.handler.FMLHandler;
import org.soraworld.areaeffect.network.Area;
import org.soraworld.areaeffect.network.AreaPacket;
import org.soraworld.areaeffect.util.Vec3d;
import org.soraworld.areaeffect.util.Vec3i;

import java.nio.charset.StandardCharsets;
import java.util.*;

public class CommonProxy {

    public static final byte UPDATE = 1;
    public static final byte DELETE = 2;
    public static final byte GAMMA = 3;
    public static final byte SPEED = 4;

    private static final byte[] CUBOID = "s|cuboid".getBytes(StandardCharsets.UTF_8);
    private static final String WECUI_CHANNEL = "WECUI";
    protected final HashMap<UUID, Vec3i> pos1s = new HashMap<>();
    protected final HashMap<UUID, Vec3i> pos2s = new HashMap<>();
    protected final HashMap<Integer, HashMap<Integer, Area>> lightAreas = new HashMap<>();
    protected final cpw.mods.fml.common.network.FMLEventChannel channel = cpw.mods.fml.common.network.NetworkRegistry.INSTANCE.newEventDrivenChannel("light");
    public Configuration config;
    protected Item tool = Items.wooden_axe;
    protected float speed;
    protected int AREA_ID = 0;

    public void onPreInit(FMLPreInitializationEvent event) {
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new FMLHandler(this));
    }

    public void onInit(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new EventBusHandler(this));
    }

    public void regEventBus(Object object) {
        MinecraftForge.EVENT_BUS.register(object);
    }

    public void load() {
        config.load();
        setSelectTool(config.getString("tool", "general", "wooden_axe", "Select Tool"));
        String[] list = config.getStringList("areas", "general", new String[]{}, "Light Areas");
        lightAreas.clear();
        AREA_ID = 0;
        if (list != null) {
            for (String text : list) {
                String[] ss = text.split(",");
                try {
                    int dim = Integer.parseInt(ss[0]);
                    Vec3i pos1 = new Vec3i(Integer.parseInt(ss[1]), Integer.parseInt(ss[2]), Integer.parseInt(ss[3]));
                    Vec3i pos2 = new Vec3i(Integer.parseInt(ss[4]), Integer.parseInt(ss[5]), Integer.parseInt(ss[6]));
                    float gamma = ss.length >= 8 ? Float.parseFloat(ss[7]) : 1.0F;
                    float speed = ss.length >= 9 ? Float.parseFloat(ss[8]) : 0.2F;
                    addArea(dim, pos1, pos2, gamma, speed);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    public void save() {
        config.get("general", "tool", "wooden_axe", "Select Tool").set(getToolName());
        List<String> list = new ArrayList<>();
        lightAreas.forEach((dim, areas) -> areas.values().forEach(area -> list.add(dim + "," + area)));
        config.get("general", "areas", new String[]{}, "Light Areas").set(list.toArray(new String[]{}));
        config.save();
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
            Object object = Item.itemRegistry.getNameForObject(tool);
            if (object instanceof String) {
                return (String) object;
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
        updateCUI(player);
    }

    public void setPos2(EntityPlayerMP player, Vec3i pos2, boolean msg) {
        pos2s.put(player.getUniqueID(), pos2);
        if (msg) {
            sendChatTranslation(player, "set.pos2", pos2);
        }
        updateCUI(player);
    }

    public void updateCUI(EntityPlayerMP player) {
        Vec3i pos1 = pos1s.get(player.getUniqueID());
        Vec3i pos2 = pos2s.get(player.getUniqueID());
        if (pos1 == null) {
            if (pos2 == null) {
                return;
            }
            pos1 = pos2;
        } else if (pos2 == null) {
            pos2 = pos1;
        }
        long sizeLong = (long) (pos2.x - pos1.x + 1) * (pos2.y - pos1.y + 1) * (pos2.z - pos1.z + 1);
        int size = (int) Math.min(sizeLong, Integer.MAX_VALUE);
        player.playerNetServerHandler.sendPacket((Packet) new S3FPacketCustomPayload(WECUI_CHANNEL, CUBOID));
        player.playerNetServerHandler.sendPacket((Packet) new S3FPacketCustomPayload(WECUI_CHANNEL, pos1.cui(1, size)));
        player.playerNetServerHandler.sendPacket((Packet) new S3FPacketCustomPayload(WECUI_CHANNEL, pos2.cui(2, size)));
    }

    private void sendTo(ByteBuf buf, EntityPlayerMP player) {
        channel.sendTo(new cpw.mods.fml.common.network.internal.FMLProxyPacket(buf, "light"), player);
    }

    private void sendToAll(ByteBuf buf) {
        channel.sendToAll(new cpw.mods.fml.common.network.internal.FMLProxyPacket(buf, "light"));
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

    public void sendGammaToAll(int dim, int id, float gamma) {
        ByteBuf buf = Unpooled.buffer();
        buf.writeByte(GAMMA);
        AreaPacket.Gamma.encode(new AreaPacket.Gamma(dim, id, gamma), buf);
        sendToAll(buf);
    }

    public void sendSpeedToAll(int dim, int id, float speed) {
        ByteBuf buf = Unpooled.buffer();
        buf.writeByte(SPEED);
        AreaPacket.Speed.encode(new AreaPacket.Speed(dim, id, speed), buf);
        sendToAll(buf);
    }

    public void createArea(EntityPlayerMP player, float light, float speed) {
        Vec3i pos1 = pos1s.get(player.getUniqueID());
        Vec3i pos2 = pos2s.get(player.getUniqueID());
        if (pos1 != null && pos2 != null) {
            Area area = addArea(player.dimension, pos1, pos2, light, speed);
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

    public Area addArea(int dim, Vec3i pos1, Vec3i pos2, float light, float speed) {
        Area area = new Area(pos1.x, pos1.y, pos1.z, pos2.x, pos2.y, pos2.z, light, speed);
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
                .setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/light tp " + id));
        IChatComponent click = new ChatComponentTranslation("text.click").setChatStyle(style);
        player.addChatMessage(new ChatComponentTranslation("info.list", id, dim, area.pos1(), area.pos2(), area.gamma, click));
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
