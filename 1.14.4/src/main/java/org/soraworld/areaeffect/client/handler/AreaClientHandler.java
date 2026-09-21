package org.soraworld.areaeffect.client.handler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.soraworld.areaeffect.client.ClientProxy;

/**
 * 客户端业务事件处理：按键请求、每帧驱动亮度过渡、连接建立时清镜像、断线时复位本地状态。
 *
 * <p>只注册到 Forge 总线一处（{@code ClientProxy#onClientSetup}）：1.14 的 {@link TickEvent} 系列
 * 也在 {@code MinecraftForge.EVENT_BUS} 上派发。
 *
 * <p>1.14 起 Forge 补上了 {@link ClientPlayerNetworkEvent}（1.13 时期没有客户端连接/断线事件），
 * 于是连接与断线恢复为 1.12 那套两条钩子：
 * <ul>
 *   <li>{@link ClientPlayerNetworkEvent.LoggedInEvent} —— 客户端玩家实体就绪时触发，早于登录全量同步包
 *       的处理（区域同步包与进世界包在同一批里，进世界更早），此刻清空客户端镜像不会与刚到的新数据抢时序；</li>
 *   <li>{@link ClientPlayerNetworkEvent.LoggedOutEvent} —— 断线 / 退出世界回主菜单，整体复位本地状态。</li>
 * </ul>
 * 两者都发生在客户端主线程，故无需再投递。
 */
public class AreaClientHandler {

    private final ClientProxy proxy;

    /** 上一 tick 的按键按下状态，用于边沿检测（见 {@link #onPlayerTick}）。 */
    private boolean listKeyDown = false;
    private boolean renderKeyDown = false;

    public AreaClientHandler(ClientProxy proxy) {
        this.proxy = proxy;
    }

    /** 连接建立：清空客户端区域镜像，随后由登录全量同步重建（见 {@code ClientProxy#onServerConnected}）。 */
    @SubscribeEvent
    public void onLoggedIn(ClientPlayerNetworkEvent.LoggedInEvent event) {
        proxy.onServerConnected();
    }

    /** 断线 / 退出世界：复位全部客户端本地状态（含清空镜像与本模组的渲染覆盖）。 */
    @SubscribeEvent
    public void onLoggedOut(ClientPlayerNetworkEvent.LoggedOutEvent event) {
        proxy.clientReset();
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (event.player instanceof ClientPlayerEntity) {
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
     * 同时承担 LightmapHook 安装 —— 首次装好之前每帧尝试一次，代价可忽略。
     */
    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            proxy.updateClientLight(mc.player);
        }
    }
}
