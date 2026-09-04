package org.soraworld.areaeffect.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketBuffer;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.SPacketCustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.network.FMLEventChannel;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.internal.FMLProxyPacket;
import org.soraworld.areaeffect.handler.EventBusHandler;
import org.soraworld.areaeffect.handler.FMLHandler;
import org.soraworld.areaeffect.network.Area;
import org.soraworld.areaeffect.network.AreaPacket;
import org.soraworld.areaeffect.util.GammaCurve;
import org.soraworld.areaeffect.util.Vec3d;
import org.soraworld.areaeffect.util.Vec3i;

import java.nio.charset.StandardCharsets;
import java.util.*;

public class CommonProxy {

    public static final byte UPDATE = 1;
    public static final byte DELETE = 2;
    public static final byte LIGHTNESS = 3;
    public static final byte DURATION = 4;

    private static final byte[] CUBOID = "s|cuboid".getBytes(StandardCharsets.UTF_8);
    private static final String WECUI_CHANNEL = "WECUI";
    protected final HashMap<UUID, Vec3i> pos1s = new HashMap<>();
    protected final HashMap<UUID, Vec3i> pos2s = new HashMap<>();
    protected final HashMap<Integer, HashMap<Integer, Area>> lightAreas = new HashMap<>();
    protected final net.minecraftforge.fml.common.network.FMLEventChannel channel = net.minecraftforge.fml.common.network.NetworkRegistry.INSTANCE.newEventDrivenChannel("light");
    public Configuration config;
    protected Item tool = Items.WOODEN_AXE;
    protected float duration = 1.0F;
    protected int AREA_ID = 0;

    public void onPreInit(FMLPreInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new FMLHandler(this));
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
                    int off = "2".equals(ss[0]) ? 1 : 0;
                    int dim = Integer.parseInt(ss[off]);
                    Vec3i pos1 = new Vec3i(Integer.parseInt(ss[off + 1]), Integer.parseInt(ss[off + 2]), Integer.parseInt(ss[off + 3]));
                    Vec3i pos2 = new Vec3i(Integer.parseInt(ss[off + 4]), Integer.parseInt(ss[off + 5]), Integer.parseInt(ss[off + 6]));
                    float lightness;
                    float duration;
                    if (off == 0) {
                        // legacy row: field 8 is an old gamma value, convert it with the old reference scene
                        float gamma = ss.length >= 8 ? Float.parseFloat(ss[7]) : 1.0F;
                        lightness = (float) GammaCurve.perceive(gamma, 0.05);
                        duration = 1.0F;
                    } else {
                        lightness = ss.length >= 9 ? Float.parseFloat(ss[8]) : 90.0F;
                        duration = ss.length >= 10 ? Float.parseFloat(ss[9]) : 1.0F;
                    }
                    addArea(dim, pos1, pos2, lightness, duration);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    public void save() {
        config.get("general", "tool", "wooden_axe", "Select Tool").set(getToolName());
        List<String> list = new ArrayList<>();
        lightAreas.forEach((dim, areas) -> areas.values().forEach(area -> list.add("2," + dim + "," + area)));
        config.get("general", "areas", new String[]{}, "Light Areas").set(list.toArray(new String[]{}));
        config.save();
    }

    private void setSelectTool(String toolName) {
        try {
            Object object = Item.REGISTRY.getObject(new ResourceLocation(toolName));
            if (object instanceof Item) {
                tool = (Item) object;
            } else {
                tool = Items.WOODEN_AXE;
            }
        } catch (Throwable ignored) {
            tool = Items.WOODEN_AXE;
        }
    }

    private String getToolName() {
        try {
            Object object = Item.REGISTRY.getNameForObject(tool);
            if (object != null) {
                return object.toString();
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
        player.connection.sendPacket((Packet) new SPacketCustomPayload(WECUI_CHANNEL, new PacketBuffer(Unpooled.copiedBuffer(CUBOID))));
        player.connection.sendPacket((Packet) new SPacketCustomPayload(WECUI_CHANNEL, new PacketBuffer(Unpooled.copiedBuffer(pos1.cui(1, size)))));
        player.connection.sendPacket((Packet) new SPacketCustomPayload(WECUI_CHANNEL, new PacketBuffer(Unpooled.copiedBuffer(pos2.cui(2, size)))));
    }

    private void sendTo(ByteBuf buf, EntityPlayerMP player) {
        channel.sendTo(new FMLProxyPacket(new PacketBuffer(buf), "light"), player);
    }

    private void sendToAll(ByteBuf buf) {
        channel.sendToAll(new FMLProxyPacket(new PacketBuffer(buf), "light"));
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
    }

    public boolean hasPerm(EntityPlayer player) {
        return player.canCommandSenderUseCommand(2, "gamemode");
    }

    public void sendChatTranslation(ICommandSender sender, String key, Object... args) {
        sender.addChatMessage(new TextComponentTranslation(key, args));
    }

    public void sendChatTranslation2(EntityPlayerMP player, String key, String objKey) {
        player.addChatMessage(new TextComponentTranslation(key, new TextComponentTranslation(objKey)));
    }

    public void sendAreaInfo(EntityPlayerMP player, int dim, int id, Area area) {
        Style style = new Style().setColor(TextFormatting.GREEN).setBold(true)
                .setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/areaeffect tp " + id));
        ITextComponent click = new TextComponentTranslation("text.click").setStyle(style);
        player.addChatMessage(new TextComponentTranslation("info.list", id, dim, area.pos1(), area.pos2(), area.lightness, area.duration, click));
    }

    public void commandTool(EntityPlayerMP player) {
        ItemStack stack = player.getHeldItemMainhand();
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
                    EntityPlayerMP newPlayer = (EntityPlayerMP) player.changeDimension(dim);
                    if (newPlayer != null) {
                        newPlayer.moveToBlockPosAndAngles(area.center(), newPlayer.rotationYaw, newPlayer.rotationPitch);
                        sendChatTranslation(newPlayer, "areaTpSuccess");
                    }
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
        return net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance();
    }
}
