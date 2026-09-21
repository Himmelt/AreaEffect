package org.soraworld.areaeffect.client.handler;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.particles.ParticleTypes;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;

import java.util.Random;

/**
 * 雾效果渲染挂钩：把 {@code FogEffectRenderer} 每帧写下的当前雾状态应用到
 * {@code EntityViewRenderEvent.FogColors}/{@code FogDensity}，并在雾激活时生成漂浮尘粒。
 *
 * <p>取消 {@code FogDensity} 事件会让 vanilla <b>整段跳过</b>原版地形雾设置（含
 * {@code GL_FOG_MODE}、{@code FOG_START/END} 的写入，只剩 {@code glFogf(GL_FOG_DENSITY, …)} 一句），
 * 所以模式与起止距离都得在这里自己写。本模组用 {@code LINEAR}：{@code FOG_START = 起雾距离}、
 * {@code FOG_END = 起雾距离 + 1/坡度}，于是"起雾距离内完全无雾、其外按坡度加雾"
 * （{@code EXP} 只有指数密度，没有"起始距离"这一档）。
 *
 * <p>1.13 起改写雾状态统一走 {@link GlStateManager}（而非 1.7.10 时代的裸 {@code GL11}）：
 * GlStateManager 内部维护状态缓存，绕过它直接发 GL 调用会让缓存与实际状态不一致，
 * 后续 vanilla 的 {@code disableFog}/{@code fogMode} 就可能被缓存判定为"无需下发"。
 * 非激活（坡度≈0）时不取消、不改色，完全走原版雾。
 */
public class FogRenderHandler {

    /**
     * 视为「有雾」的最小坡度（覆盖率/米）；低于它则完全交给原版雾。
     * 取 {@code 1e-4} 相当于 10 公里的过渡距离：此时画面上的雾早已淡到看不见，
     * 继续压着原版雾不写没有意义，于是交还给它。
     */
    private static final double EPSILON = 1.0e-4D;

    private static final Minecraft mc = Minecraft.getInstance();
    private static final Random rand = new Random();

    private static volatile boolean active = false;
    /** 当前坡度（覆盖率/米）= 1 / 过渡距离；0 表示无雾。 */
    private static volatile double slope = 0.0D;
    private static volatile double startDistance = 0.0D;
    private static volatile double red = 0.0D;
    private static volatile double green = 0.0D;
    private static volatile double blue = 0.0D;
    private static volatile int dust = 0;

    /**
     * 原版本帧算出的雾色（0..1）：在 {@link #onFogColors} 入口处（本模组改色<b>之前</b>）采样，
     * 因此始终是「没有区域雾效时画面该有的雾色」。
     * {@code FogEffectRenderer} 用它作为进出区域的过渡基准色 —— 否则雾色得从纯黑抬起，
     * 进出区域时画面会先整体压黑再逐渐显出雾色。
     */
    private static volatile double atmRed = 0.53D;
    private static volatile double atmGreen = 0.81D;
    private static volatile double atmBlue = 0.92D;

    /** 最近一帧的原版雾色分量（0..1），雾色过渡的基准色。 */
    public static double atmosphereRed() {
        return atmRed;
    }

    /** 见 {@link #atmosphereRed()}。 */
    public static double atmosphereGreen() {
        return atmGreen;
    }

    /** 见 {@link #atmosphereRed()}。 */
    public static double atmosphereBlue() {
        return atmBlue;
    }

    /** 由 {@code FogEffectRenderer} 每帧写入当前雾状态（client/main 线程 tick 驱动）。 */
    public static void setState(boolean active, double slope, double startDistance, double red, double green,
                                double blue, int dust) {
        FogRenderHandler.active = active;
        FogRenderHandler.slope = slope;
        FogRenderHandler.startDistance = startDistance;
        FogRenderHandler.red = red;
        FogRenderHandler.green = green;
        FogRenderHandler.blue = blue;
        FogRenderHandler.dust = dust;
    }

    @SubscribeEvent
    public void onFogColors(EntityViewRenderEvent.FogColors event) {
        // 事件字段此刻仍是原版算出的雾色（我们还没动它），先记下来作为"大气色"基准
        atmRed = event.getRed();
        atmGreen = event.getGreen();
        atmBlue = event.getBlue();
        if (active && slope > EPSILON) {
            event.setRed((float) red);
            event.setGreen((float) green);
            event.setBlue((float) blue);
        }
    }

    @SubscribeEvent
    public void onFogDensity(EntityViewRenderEvent.FogDensity event) {
        if (active && slope > EPSILON) {
            event.setCanceled(true);
            // 事件被取消后 vanilla 只会写 GL_FOG_DENSITY（LINEAR 模式下被忽略），
            // 所以这里把模式与起止距离一并写上：走完 1/坡度 米即 100% 雾色。
            float start = (float) startDistance;
            float end = start + (float) (1.0D / slope);
            GlStateManager.fogMode(GlStateManager.FogMode.LINEAR);
            GlStateManager.fogStart(start);
            GlStateManager.fogEnd(end);
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !active || dust <= 0
                || mc.world == null || mc.player == null || mc.isGamePaused()) {
            return;
        }
        // 围绕玩家生成漂浮尘粒。
        // 1.13 起粒子改为注册表式（{@code EnumParticleTypes} 与按数字 id 生成的方式都已移除）：
        // 改走 {@code ParticleManager#addParticle(IParticleData, …)}，粒子类型取 {@code ParticleTypes.MYCELIUM}
        // —— 它就是 1.12 的 TOWN_AURA（菌丝孢子／浮尘）在 1.13 的对应项。
        for (int i = 0; i < dust; i++) {
            double px = mc.player.posX + 20 * (rand.nextDouble() - 0.5D);
            double py = mc.player.posY + 10 * (rand.nextDouble() - 0.5D);
            double pz = mc.player.posZ + 20 * (rand.nextDouble() - 0.5D);
            mc.particles.addParticle(ParticleTypes.MYCELIUM, px, py, pz, 0.0D, 0.0D, 0.0D);
        }
    }
}
