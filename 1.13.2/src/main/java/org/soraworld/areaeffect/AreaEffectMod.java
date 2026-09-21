package org.soraworld.areaeffect;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.soraworld.areaeffect.client.ClientProxy;
import org.soraworld.areaeffect.common.CommonProxy;

/**
 * 模组入口：装配唯一的代理实例，并把生命周期事件转交代理。
 *
 * <p>1.13 起 {@code @SidedProxy} 与 FML 预初始化/初始化事件都已移除，改为：
 * <ul>
 *   <li>两侧代理由 {@link DistExecutor} 在<b>本条进程的实际分发侧</b>构造一次 —— 客户端得
 *       {@link ClientProxy}（多一层客户端镜像与渲染挂钩），专用服得 {@link CommonProxy}。
 *       嵌套 {@code Supplier<Supplier<T>>} 的写法保证客户端 lambda 只在客户端求值，
 *       专用服进程不会去解析客户端专属类型；</li>
 *   <li>装配钩子改挂在 mod 事件总线（{@link FMLCommonSetupEvent} / {@link FMLClientSetupEvent}）；</li>
 *   <li>服务端生命周期（{@link FMLServerStartingEvent} / {@link FMLServerStoppingEvent}）
 *       与 1.12 一样在 Forge 总线上派发，故本类注册到 {@code MinecraftForge.EVENT_BUS}。</li>
 * </ul>
 *
 * <p>单机（集成服）同样只有这一个代理实例、走完整的网络回环：权威数据与客户端镜像
 * 仍是两个独立实例（见 {@code CommonProxy#areas} 与 {@code ClientProxy#clientAreas}）。
 */
@Mod(AreaEffectMod.MOD_ID)
public final class AreaEffectMod {

    public static final String MOD_ID = "areaeffect";
    public static final String MOD_NAME = "AreaEffect";
    public static final String MOD_VERSION = "1.4.0";

    /**
     * 两侧共用的字段类型是 {@link CommonProxy}；客户端那一支在运行期才被求值，
     * 因此专用服上 {@code ClientProxy} 不会被加载。
     */
    private static final CommonProxy PROXY = DistExecutor.runForDist(
            () -> () -> new ClientProxy(),
            () -> CommonProxy::new);

    public AreaEffectMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::onCommonSetup);
        modBus.addListener(this::onClientSetup);
        // 服务端生命周期事件（ServerStarting / ServerStopping）在 Forge 总线上派发
        MinecraftForge.EVENT_BUS.register(this);
    }

    /** 两侧公共装配：网络通道注册、入站请求绑定、服务端业务事件注册。 */
    @SubscribeEvent
    public void onCommonSetup(FMLCommonSetupEvent event) {
        PROXY.onCommonSetup(event);
    }

    /** 客户端装配：客户端方向消息绑定、按键注册与渲染挂钩（专用服上不会触发）。 */
    @SubscribeEvent
    public void onClientSetup(FMLClientSetupEvent event) {
        PROXY.onClientSetup(event);
    }

    /** 服务端启动：注册指令、建立配置与随世界的区域存档并载入。 */
    @SubscribeEvent
    public void onServerStarting(FMLServerStartingEvent event) {
        PROXY.onServerStarting(event);
    }

    /** 关服：兜底落盘（区域改动平时由服务端每 tick 合并写入，见 {@code CommonProxy#flushStore}）。 */
    @SubscribeEvent
    public void onServerStopping(FMLServerStoppingEvent event) {
        PROXY.onServerStopping(event);
    }
}
