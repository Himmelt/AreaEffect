package org.soraworld.areaeffect.handler;

import net.minecraft.client.entity.EntityPlayerSP;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import org.soraworld.areaeffect.proxy.ClientProxy;
import org.soraworld.areaeffect.proxy.CommonProxy;

public class FMLClientHandler {

    private final ClientProxy proxy;

    public FMLClientHandler(ClientProxy proxy) {
        this.proxy = proxy;
    }

    @cpw.mods.fml.common.eventhandler.SubscribeEvent
    public void onPlayerTick(cpw.mods.fml.common.gameevent.TickEvent.PlayerTickEvent event) {
        if (event.phase != cpw.mods.fml.common.gameevent.TickEvent.Phase.END) {
            return;
        }
        if (event.player instanceof EntityPlayerSP) {
            proxy.updateClientLight(event.player);
        }
    }

    @cpw.mods.fml.common.eventhandler.SubscribeEvent
    public void onReceivePacket(cpw.mods.fml.common.network.FMLNetworkEvent.ClientCustomPacketEvent event) {
        proxy.handlePacket(event.packet.payload());
    }

    @cpw.mods.fml.common.eventhandler.SubscribeEvent
    public void onLogout(cpw.mods.fml.common.network.FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        proxy.clientReset();
    }
}
