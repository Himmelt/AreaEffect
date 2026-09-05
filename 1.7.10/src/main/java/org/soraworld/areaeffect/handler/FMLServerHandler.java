package org.soraworld.areaeffect.handler;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import org.soraworld.areaeffect.network.AreaPacket;
import org.soraworld.areaeffect.proxy.CommonProxy;

/**
 * 接收客户端通过 "light" 通道发来的请求并转发给服务端逻辑处理。
 */
public class FMLServerHandler {

    private final CommonProxy proxy;

    public FMLServerHandler(CommonProxy proxy) {
        this.proxy = proxy;
    }

    @SubscribeEvent
    public void onPacket(FMLNetworkEvent.ServerCustomPacketEvent event) {
        ByteBuf buf = event.packet.payload();
        byte op = buf.readByte();
        EntityPlayerMP player = ((NetHandlerPlayServer) event.handler).playerEntity;
        if (op == CommonProxy.REQ_LIST) {
            proxy.handleListRequest(player);
        } else if (op == CommonProxy.REQ_SET) {
            proxy.handleSetProps(player, AreaPacket.SetProps.decode(buf));
        }
    }
}