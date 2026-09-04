package org.soraworld.areaeffect.proxy;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import org.soraworld.areaeffect.handler.FMLClientHandler;
import org.soraworld.areaeffect.handler.LightmapHook;
import org.soraworld.areaeffect.network.Area;
import org.soraworld.areaeffect.network.AreaPacket;
import org.soraworld.areaeffect.util.GammaCurve;

import java.util.HashMap;
import java.util.Map;

public class ClientProxy extends CommonProxy {

    private double curDL = 0.0D;
    private double fromDL = 0.0D;
    private int elapsed = Integer.MAX_VALUE;
    private boolean inArea = false;
    private int lastAreaId = -1;
    private float lastLightness = -1.0F;

    private final Minecraft mc = Minecraft.getMinecraft();

    @Override
    public void onPreInit(FMLPreInitializationEvent event) {
        super.onPreInit(event);
        FMLCommonHandler.instance().bus().register(new FMLClientHandler(this));
        channel.register(new FMLClientHandler(this));
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
            default:
        }
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

    /**
     * Per tick client update: keeps the lightmap a uniform CIE L* offset away
     * from what the player's own gamma setting would currently produce. The
     * gamma setting itself is only ever read, never written.
     */
    public void updateClientLight(EntityPlayer player) {
        LightmapHook.tryInstall(mc);
        double raw = rawLightAt(player);
        double liveGamma = mc.gameSettings.gammaSetting;
        double toDL;
        float seconds;
        boolean restart = false;
        Area area = findAreaAt(player);
        if (area != null) {
            seconds = area.duration;
            toDL = area.lightness - GammaCurve.perceive(liveGamma, raw);
            if (!inArea || area.id != lastAreaId || area.lightness != lastLightness) {
                restart = true;
            }
            inArea = true;
            lastAreaId = area.id;
            lastLightness = area.lightness;
            duration = area.duration;
        } else {
            seconds = duration;
            toDL = 0.0D;
            if (inArea) {
                restart = true;
            }
            inArea = false;
            lastAreaId = -1;
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

    /** Scene base light level (the lightmap's raw value) at the player's position. */
    private double rawLightAt(EntityPlayer player) {
        return player.worldObj.getLightBrightness((int) Math.floor(player.posX), (int) Math.floor(player.posY), (int) Math.floor(player.posZ));
    }

    public void clientReset() {
        tool = Items.wooden_axe;
        duration = 1.0F;
        elapsed = Integer.MAX_VALUE;
        inArea = false;
        lastAreaId = -1;
        AREA_ID = 0;
        lightAreas.clear();
        pos1s.clear();
        pos2s.clear();
        curDL = 0.0D;
        fromDL = 0.0D;
        LightmapHook.setOffset(0.0D);
    }
}
