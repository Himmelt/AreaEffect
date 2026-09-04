package org.soraworld.areaeffect.proxy;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiVideoSettings;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.soraworld.areaeffect.handler.EventBusClientHandler;
import org.soraworld.areaeffect.handler.FMLClientHandler;
import org.soraworld.areaeffect.network.Area;
import org.soraworld.areaeffect.network.AreaPacket;

import java.util.HashMap;
import java.util.Map;
import org.soraworld.areaeffect.util.GammaCurve;

public class ClientProxy extends CommonProxy {

    private float originGamma = 0.0F;
    private double originLightness = 0.0D;
    private int elapsed = Integer.MAX_VALUE;
    private boolean inArea = false;
    private int lastAreaId = -1;
    private float lastLightness = -1.0F;

    private final Minecraft mc = Minecraft.getMinecraft();
    private final GameSettings gameSettings = mc.gameSettings;

    @Override
    public void onPreInit(FMLPreInitializationEvent event) {
        super.onPreInit(event);
        net.minecraftforge.fml.common.FMLCommonHandler.instance().bus().register(new FMLClientHandler(this));
        channel.register(new FMLClientHandler(this));
        originGamma = mc.gameSettings.gammaSetting;
    }

    @Override
    public void onInit(FMLInitializationEvent event) {
        super.onInit(event);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new EventBusClientHandler(this));
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

    public void updateClientGamma(EntityPlayer player) {
        if (mc.currentScreen instanceof GuiVideoSettings) {
            return;
        }
        double raw = rawLightAt(player);
        double targetGamma;
        double targetLightness;
        float seconds;
        boolean restart = false;
        Area area = findAreaAt(player);
        if (area != null) {
            seconds = area.duration;
            targetLightness = area.lightness;
            targetGamma = GammaCurve.gammaFromLightness(area.lightness, raw);
            if (!inArea || area.id != lastAreaId || area.lightness != lastLightness) {
                restart = true;
            }
            inArea = true;
            lastAreaId = area.id;
            lastLightness = area.lightness;
            duration = area.duration;
        } else {
            seconds = duration;
            targetGamma = originGamma;
            targetLightness = GammaCurve.perceive(originGamma, raw);
            if (inArea) {
                restart = true;
            }
            inArea = false;
            lastAreaId = -1;
        }
        if (restart) {
            originLightness = GammaCurve.perceive(gameSettings.gammaSetting, raw);
            elapsed = 0;
        }
        int ticks = Math.max(1, Math.round(seconds * 20.0F));
        double gamma;
        if (elapsed >= ticks) {
            gamma = targetGamma;
        } else {
            double lightness = GammaCurve.glide(originLightness, targetLightness, elapsed, ticks);
            gamma = GammaCurve.gammaFromLightness(lightness, raw);
            elapsed++;
        }
        gameSettings.gammaSetting = (float) gamma;
    }


    /** Scene base light level (the lightmap's raw value) at the player's position. */
    private double rawLightAt(EntityPlayer player) {
        return player.world.getLightBrightness(new net.minecraft.util.math.BlockPos(player));
    }

    public void saveLight() {
        originGamma = mc.gameSettings.gammaSetting;
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
        mc.gameSettings.gammaSetting = originGamma;
    }
}
