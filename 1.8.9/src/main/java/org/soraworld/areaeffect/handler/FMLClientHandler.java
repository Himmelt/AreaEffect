package org.soraworld.areaeffect.handler;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import org.soraworld.areaeffect.proxy.ClientProxy;
import org.soraworld.areaeffect.proxy.CommonProxy;

public class FMLClientHandler {

    private final ClientProxy proxy;

    public FMLClientHandler(ClientProxy proxy) {
        this.proxy = proxy;
    }

    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (event.player instanceof EntityPlayerSP) {
            proxy.updateClientGamma(event.player);
        }
    }

    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent
    public void onReceivePacket(FMLNetworkEvent.ClientCustomPacketEvent event) {
        proxy.handlePacket(event.packet.payload());
    }

    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent
    public void onLogout(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        proxy.clientReset();
    }
}
