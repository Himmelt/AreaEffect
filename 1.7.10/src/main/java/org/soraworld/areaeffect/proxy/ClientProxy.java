package org.soraworld.areaeffect.proxy;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.network.internal.FMLProxyPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.input.Keyboard;
import org.soraworld.areaeffect.client.gui.GuiAreaList;
import org.soraworld.areaeffect.handler.FMLClientHandler;
import org.soraworld.areaeffect.handler.LightmapHook;
import org.soraworld.areaeffect.handler.SelectionRenderHandler;
import org.soraworld.areaeffect.network.Area;
import org.soraworld.areaeffect.network.AreaPacket;
import org.soraworld.areaeffect.util.GammaCurve;
import org.soraworld.areaeffect.util.Vec3i;

import java.util.HashMap;
import java.util.Map;

public class ClientProxy extends CommonProxy {

    public static final KeyBinding KEY_LIST = new KeyBinding("key.areaeffect.list", Keyboard.KEY_J, "key.categories.areaeffect");

    private double curDL = 0.0D;
    private double fromDL = 0.0D;
    private int elapsed = Integer.MAX_VALUE;
    private boolean inArea = false;
    private int lastAreaId = -1;
    private float lastLightness = -1.0F;
    private float lastDuration = -1.0F;

    private Vec3i selPos1 = null;
    private Vec3i selPos2 = null;

    private final Minecraft mc = Minecraft.getMinecraft();

    @Override
    public void onPreInit(FMLPreInitializationEvent event) {
        super.onPreInit(event);
        FMLCommonHandler.instance().bus().register(new FMLClientHandler(this));
        channel.register(new FMLClientHandler(this));
        MinecraftForge.EVENT_BUS.register(new SelectionRenderHandler(this));
        ClientRegistry.registerKeyBinding(KEY_LIST);
    }

    public void handlePacket(ByteBuf buf) {
        switch (buf.readByte()) {
            case UPDATE:
                processUpdate(AreaPacket.Update.decode(buf));
                break;
            case DELETE:
                processDelete(AreaPacket.Delete.decode(buf));
                break;
            case LIGHTNESS:
                processLightness(AreaPacket.Lightness.decode(buf));
                break;
            case DURATION:
                processDuration(AreaPacket.Duration.decode(buf));
                break;
            case SELECTION:
                processSelection(AreaPacket.Selection.decode(buf));
                break;
            case LIST_REPLY:
                processListReply(AreaPacket.ListReply.decode(buf));
                break;
            default:
        }
    }

    public void sendListRequest() {
        ByteBuf buf = Unpooled.buffer();
        buf.writeByte(REQ_LIST);
        channel.sendToServer(new FMLProxyPacket(buf, "light"));
    }

    public void sendSetProps(int dim, int id, float lightness, float duration) {
        ByteBuf buf = Unpooled.buffer();
        buf.writeByte(REQ_SET);
        AreaPacket.SetProps.encode(new AreaPacket.SetProps(dim, id, lightness, duration), buf);
        channel.sendToServer(new FMLProxyPacket(buf, "light"));
    }

    public void processUpdate(AreaPacket.Update packet) {
        lightAreas.computeIfAbsent(packet.dim, dim -> new HashMap<>()).put(packet.id, packet.data);
    }

    public void processDelete(AreaPacket.Delete packet) {
        Map<Integer, Area> areas = lightAreas.get(packet.dim);
        if (areas != null && !areas.isEmpty()) {
            areas.remove(packet.id);
        }
    }

    public void processLightness(AreaPacket.Lightness packet) {
        Map<Integer, Area> areas = lightAreas.get(packet.dim);
        if (areas != null && !areas.isEmpty()) {
            Area area = areas.get(packet.id);
            if (area != null) {
                area.lightness = packet.lightness;
            }
        }
    }

    public void processDuration(AreaPacket.Duration packet) {
        Map<Integer, Area> areas = lightAreas.get(packet.dim);
        if (areas != null && !areas.isEmpty()) {
            Area area = areas.get(packet.id);
            if (area != null) {
                area.duration = packet.duration;
            }
        }
    }

    public void processSelection(AreaPacket.Selection packet) {
        selPos1 = packet.pos1;
        selPos2 = packet.pos2;
    }

    public void processListReply(AreaPacket.ListReply packet) {
        mc.displayGuiScreen(new GuiAreaList(this, packet.dim, packet.areas));
    }

    public Vec3i getSelPos1() {
        return selPos1;
    }

    public Vec3i getSelPos2() {
        return selPos2;
    }

    /**
     * Per tick client update: keeps the lightmap a uniform CIE L* offset away
     * from what the player's own gamma setting would currently produce. The
     * gamma setting itself is only ever read, never written.
     */
    public void updateClientLight(EntityPlayer player) {
        LightmapHook.tryInstall(mc);
        double toDL;
        float seconds;
        boolean restart = false;
        Area area = findAreaAt(player);
        if (area != null) {
            seconds = area.duration;
            // 区域内恒定输出设定的亮度，不受环境光/方块亮度影响
            toDL = area.lightness;
            if (!inArea || area.id != lastAreaId || area.lightness != lastLightness || area.duration != lastDuration) {
                restart = true;
            }
            inArea = true;
            lastAreaId = area.id;
            lastLightness = area.lightness;
            lastDuration = area.duration;
            duration = area.duration;
        } else {
            seconds = duration;
            toDL = 0.0D;
            if (inArea) {
                restart = true;
            }
            inArea = false;
            lastAreaId = -1;
            lastDuration = -1.0F;
        }
        if (restart) {
            fromDL = curDL;
            elapsed = 0;
        }
        int ticks = Math.max(1, Math.round(seconds * 20.0F));
        if (elapsed >= ticks) {
            curDL = toDL;
        } else {
            curDL = GammaCurve.glide(fromDL, toDL, elapsed, ticks);
            elapsed++;
        }
        LightmapHook.setOffset(curDL);
    }

    public void clientReset() {
        tool = Items.wooden_axe;
        duration = 1.0F;
        elapsed = Integer.MAX_VALUE;
        inArea = false;
        lastAreaId = -1;
        lastDuration = -1.0F;
        AREA_ID = 0;
        lightAreas.clear();
        pos1s.clear();
        pos2s.clear();
        selPos1 = null;
        selPos2 = null;
        curDL = 0.0D;
        fromDL = 0.0D;
        LightmapHook.setOffset(0.0D);
    }
}
