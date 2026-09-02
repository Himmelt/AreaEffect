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

public class ClientProxy extends CommonProxy {

    private float originGamma = 0.0F;

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
            case GAMMA:
                processGamma(AreaPacket.Gamma.decode(buf));
                break;
            case SPEED:
                processSpeed(AreaPacket.Speed.decode(buf));
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

    public void processGamma(AreaPacket.Gamma packet) {
        Map<Integer, Area> areas = lightAreas.get(packet.dim);
        if (areas != null && !areas.isEmpty()) {
            Area area = areas.get(packet.id);
            if (area != null) {
                area.gamma = packet.gamma;
            }
        }
    }

    public void processSpeed(AreaPacket.Speed packet) {
        Map<Integer, Area> areas = lightAreas.get(packet.dim);
        if (areas != null && !areas.isEmpty()) {
            Area area = areas.get(packet.id);
            if (area != null) {
                area.speed = packet.speed;
            }
        }
    }

    public void updateClientGamma(EntityPlayer player) {
        if (mc.currentScreen instanceof GuiVideoSettings) {
            return;
        }
        Area area = findAreaAt(player);
        if (area != null) {
            speed = area.speed;
            gameSettings.gammaSetting = area.nextGamma(gameSettings.gammaSetting);
            return;
        }
        fallbackDefaultGamma();
    }

    private void fallbackDefaultGamma() {
        if (originGamma > 1) {
            originGamma = 1.0F;
        }
        if (gameSettings.gammaSetting < this.originGamma - speed) {
            gameSettings.gammaSetting += speed;
        } else if (gameSettings.gammaSetting > this.originGamma + speed) {
            gameSettings.gammaSetting -= speed;
        } else {
            gameSettings.gammaSetting = this.originGamma;
        }
    }

    public void saveLight() {
        originGamma = mc.gameSettings.gammaSetting;
    }

    public void clientReset() {
        tool = Items.wooden_axe;
        speed = 1.0F;
        AREA_ID = 0;
        lightAreas.clear();
        pos1s.clear();
        pos2s.clear();
        mc.gameSettings.gammaSetting = originGamma;
    }
}
