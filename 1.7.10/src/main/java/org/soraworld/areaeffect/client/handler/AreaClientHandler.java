package org.soraworld.areaeffect.client.handler;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import org.soraworld.areaeffect.client.ClientProxy;

/**
 * 客户端业务事件处理：按键请求、每帧驱动亮度过渡、连接建立时清镜像、断线时重置本地状态。
 *
 * <p>只注册到 FML 总线一处（{@code ClientProxy#onPreInit}）：{@link TickEvent} 与
 * {@link FMLNetworkEvent.ClientConnectedToServerEvent} /
 * {@link FMLNetworkEvent.ClientDisconnectionFromServerEvent} 在 1.7.10 都由该总线派发。
 */
public class AreaClientHandler {

    private final ClientProxy proxy;

    /** 上一 tick 的按键按下状态，用于边沿检测（见 {@link #onPlayerTick}）。 */
    private boolean listKeyDown = false;
    private boolean renderKeyDown = false;

    public AreaClientHandler(ClientProxy proxy) {
        this.proxy = proxy;
    }

    /**
     * 连接建立：连外部服务器时先清掉上一个会话的区域镜像（见 {@code ClientProxy#onServerConnected}）。
     * 单机（本地连接）不清 —— 那时客户端与服务端共用同一份数据，清了就是把世界数据清了。
     */
    @SubscribeEvent
    public void onConnected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        proxy.onServerConnected(event.isLocal);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (event.player instanceof EntityPlayerSP) {
            // 用「按下沿」而不是 isPressed()：后者按住期间每 tick 都返回 true，
            // 于是按住 J 会每秒发 20 个列表请求、按住 K 会把线框来回切花。
            listKeyDown = edge(ClientProxy.KEY_LIST.getIsKeyPressed(), listKeyDown, proxy::sendListRequest);
            renderKeyDown = edge(ClientProxy.KEY_SEL_RENDER.getIsKeyPressed(), renderKeyDown, proxy::toggleSelection);
        }
    }

    /** 只在按下沿（false → true）执行一次 action，返回本次按下状态供下次比较。 */
    private static boolean edge(boolean down, boolean wasDown, Runnable action) {
        if (down && !wasDown) {
            action.run();
        }
        return down;
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
