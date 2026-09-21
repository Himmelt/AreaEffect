package org.soraworld.areaeffect.client.handler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.soraworld.areaeffect.client.ClientProxy;

/**
 * 客户端业务事件处理：按键请求、每帧驱动亮度过渡、断线时重置本地状态。
 *
 * <p>只注册到 Forge 总线一处（{@code ClientProxy#onClientSetup}）：1.13 的
 * {@link TickEvent} 系列在 {@code MinecraftForge.EVENT_BUS} 上派发。
 *
 * <p><b>断线判定</b>：1.12 的 {@code FMLNetworkEvent.ClientDisconnectionFromServerEvent}
 * 在 1.13 已不存在（后继的 {@code ClientPlayerNetworkEvent} 此时还没加上），故改为从
 * 世界对象的<b>卸载沿</b>推断：上一帧还在世界里、这一帧 {@code mc.world == null}，
 * 即"断线 / 退出世界回主菜单"，做一次整体复位（{@link ClientProxy#clientReset()}）。
 *
 * <p>复位里就包含清空客户端区域镜像，因此<b>不再</b>像 1.12 那样额外在"连接建立"时清一次：
 * 那需要识别"已连上但还没进世界"，而 1.13 既没有该事件、{@code Minecraft#getConnection()}
 * 又只在玩家实体存在时才有值。更关键的是，进入世界的那个沿上清镜像会与登录全量同步抢时序
 * —— 服务端的区域同步包与进世界的包在同一 tick 内处理，清空会把这批刚到的新数据抹掉。
 * 镜像在每个会话结束时都已清干净，故只在断线时复位即可。
 */
public class AreaClientHandler {

    private final ClientProxy proxy;

    /** 上一 tick 的按键按下状态，用于边沿检测（见 {@link #onPlayerTick}）。 */
    private boolean listKeyDown = false;
    private boolean renderKeyDown = false;

    /** 上一帧是否处于世界中：用于识别"世界卸载"这一沿（见类注释）。 */
    private boolean inWorld = false;

    public AreaClientHandler(ClientProxy proxy) {
        this.proxy = proxy;
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (event.player instanceof EntityPlayerSP) {
            // 用「按下沿」而不是 isPressed()：后者按住期间每 tick 都返回 true，
            // 于是按住 J 会每秒发 20 个列表请求、按住 K 会把线框来回切花。
            listKeyDown = edge(ClientProxy.KEY_LIST.isKeyDown(), listKeyDown, proxy::sendListRequest);
            renderKeyDown = edge(ClientProxy.KEY_SEL_RENDER.isKeyDown(), renderKeyDown, proxy::toggleSelection);
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
     * 同时承担 LightmapHook 安装与断线复位（见类注释）。
     */
    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        syncWorldState(mc);
        if (mc.player != null) {
            proxy.updateClientLight(mc.player);
        }
    }

    /** 世界卸载沿上做一次客户端复位；处于世界中时只置位，不重复动作。 */
    private void syncWorldState(Minecraft mc) {
        if (mc.world == null) {
            if (inWorld) {
                inWorld = false;
                // 世界卸载（断线 / 退出世界回主菜单）：整体复位本地状态（含清空客户端镜像）
                proxy.clientReset();
            }
            return;
        }
        inWorld = true;
    }
}
