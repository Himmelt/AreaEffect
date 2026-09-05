package org.soraworld.areaeffect.common.network;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;

/**
 * 频道的唯一入站路由（挂频道私有 EventBus）。
 */
public final class InboundRouter {

    @SubscribeEvent
    public void onClientPacket(FMLNetworkEvent.ClientCustomPacketEvent event) {
        PacketChannel.route(event.packet.payload(), false, null);
    }

    @SubscribeEvent
    public void onServerPacket(FMLNetworkEvent.ServerCustomPacketEvent event) {
        EntityPlayerMP player = event.handler instanceof NetHandlerPlayServer
                ? ((NetHandlerPlayServer) event.handler).playerEntity
                : null;
        PacketChannel.route(event.packet.payload(), true, player);
    }
}