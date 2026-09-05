package org.soraworld.areaeffect.client.handler;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import net.minecraft.client.entity.EntityPlayerSP;
import org.soraworld.areaeffect.client.ClientProxy;

/**
 * 客户端业务事件处理：每 tick 更新光照与按键请求、接收 light 通道数据包、断线重置。
 * 同一实例注册到 FML 总线与 light 通道两个订阅点。
 */
public class AreaClientHandler {

    private final ClientProxy proxy;

    public AreaClientHandler(ClientProxy proxy) {
        this.proxy = proxy;
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (event.player instanceof EntityPlayerSP) {
            proxy.updateClientLight(event.player);
            if (ClientProxy.KEY_LIST.isPressed()) {
                proxy.sendListRequest();
            }
        }
    }

    @SubscribeEvent
    public void onLogout(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        proxy.clientReset();
    }
}
