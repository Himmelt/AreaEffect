package org.soraworld.areaeffect.common.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

/**
 * 频道的唯一入站路由（挂频道私有 EventBus）。
 */
public final class InboundRouter {

    @SubscribeEvent
    public void onClientPacket(FMLNetworkEvent.ClientCustomPacketEvent event) {
        PacketChannel.route(event.getPacket().payload(), false, null);
    }

    @SubscribeEvent
    public void onServerPacket(FMLNetworkEvent.ServerCustomPacketEvent event) {
        EntityPlayerMP player = event.getHandler() instanceof NetHandlerPlayServer
                ? ((NetHandlerPlayServer) event.getHandler()).player
                : null;
        PacketChannel.route(event.getPacket().payload(), true, player);
    }
}