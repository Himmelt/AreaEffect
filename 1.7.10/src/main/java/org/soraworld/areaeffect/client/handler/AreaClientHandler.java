package org.soraworld.areaeffect.client.handler;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import org.soraworld.areaeffect.client.ClientProxy;

/**
 * 客户端业务事件处理：每 tick 处理按键请求、每帧驱动亮度过渡、断线重置。
 *
 * <p>只注册到 FML 总线一处（{@code ClientProxy#onPreInit}）：{@link TickEvent} 与
 * {@link FMLNetworkEvent.ClientDisconnectionFromServerEvent} 在 1.7.10 都由该总线派发。
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
            if (ClientProxy.KEY_LIST.isPressed()) {
                proxy.sendListRequest();
            }
            if (ClientProxy.KEY_SEL_RENDER.isPressed()) {
                proxy.toggleSelection();
            }
        }
    }

    /**
     * 每帧驱动亮度过渡（渲染器内部按真实时间插值，帧率无关）。
     * 同时承担 LightmapHook 安装与客户端任务队列排空（GUI 打开等）。
     */
    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) {
            proxy.updateClientLight(mc.thePlayer);
        }
    }

    @SubscribeEvent
    public void onLogout(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        proxy.clientReset();
    }
}
