package org.soraworld.areaeffect.handler;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import net.minecraft.client.entity.EntityPlayerSP;
import org.soraworld.areaeffect.proxy.ClientProxy;

public class FMLClientHandler {

    private final ClientProxy proxy;

    public FMLClientHandler(ClientProxy proxy) {
        this.proxy = proxy;
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (event.player instanceof EntityPlayerSP) {
            proxy.updateClientLight(event.player);
        }
    }

    @SubscribeEvent
    public void onReceivePacket(FMLNetworkEvent.ClientCustomPacketEvent event) {
        proxy.handlePacket(event.packet.payload());
    }

    @SubscribeEvent
    public void onLogout(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        proxy.clientReset();
    }
}
