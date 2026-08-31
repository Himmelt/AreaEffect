package org.soraworld.areaeffect.handler;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import org.soraworld.areaeffect.proxy.ClientProxy;
import org.soraworld.areaeffect.proxy.CommonProxy;

/**
 * @author Himmelt
 */
public class FMLClientHandler {

    private final ClientProxy proxy;

    public FMLClientHandler(ClientProxy proxy) {
        this.proxy = proxy;
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.player instanceof EntityPlayerSP) {
            proxy.updateClientGamma(event.player);
        }
    }

    @cpw.mods.fml.common.eventhandler.SubscribeEvent
    public void onPlayerTick(cpw.mods.fml.common.gameevent.TickEvent.PlayerTickEvent event) {
        if (event.player instanceof EntityPlayerSP) {
            proxy.updateClientGamma(event.player);
        }
    }

    @SubscribeEvent
    public void onReceivePacket(FMLNetworkEvent.ClientCustomPacketEvent event) {
        if (CommonProxy.v_1_8) {
            proxy.handlePacket(event.packet.payload());
        } else {
            proxy.handlePacket(event.getPacket().payload());
        }
    }

    @cpw.mods.fml.common.eventhandler.SubscribeEvent
    public void onReceivePacket(cpw.mods.fml.common.network.FMLNetworkEvent.ClientCustomPacketEvent event) {
        proxy.handlePacket(event.packet.payload());
    }

    @SubscribeEvent
    public void onLogout(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        proxy.clientReset();
    }

    @cpw.mods.fml.common.eventhandler.SubscribeEvent
    public void onLogout(cpw.mods.fml.common.network.FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        proxy.clientReset();
    }
}
