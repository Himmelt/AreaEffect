package org.soraworld.areaeffect.client.handler;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityAuraFX;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import org.lwjgl.opengl.GL11;

import java.util.Random;

/**
 * 雾效果渲染挂钩：把 {@code FogEffectRenderer} 每帧写下的当前雾状态应用到
 * {@code EntityViewRenderEvent.FogColors}/{@code FogDensity}，并在雾激活时生成漂浮尘粒。
 *
 * <p>1.7.10 无 {@code GlStateManager.setFog}，要让「取消 {@code FogDensity} 后设置的密度」真正起效，
 * 必须在事件回调里直接用 {@code GL11} 把雾模式强制成 {@code GL_EXP} 再写密度。
 * 非激活（密度≈0）时不取消、不改色，完全走原版雾。
 */
public class FogRenderHandler {

    /** 视为「有雾」的最小当前密度阈值；低于它则完全交给原版雾。 */
    private static final double EPSILON = 0.001D;

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Random rand = new Random();

    private static volatile boolean active = false;
    private static volatile double density = 0.0D;
    private static volatile double red = 0.0D;
    private static volatile double green = 0.0D;
    private static volatile double blue = 0.0D;
    private static volatile int dust = 0;

    /** 由 {@code FogEffectRenderer} 每帧写入当前雾状态（client/main 线程 tick 驱动）。 */
    public static void setState(boolean active, double density, double red, double green, double blue, int dust) {
        FogRenderHandler.active = active;
        FogRenderHandler.density = density;
        FogRenderHandler.red = red;
        FogRenderHandler.green = green;
        FogRenderHandler.blue = blue;
        FogRenderHandler.dust = dust;
    }

    @SubscribeEvent
    public void onFogColors(EntityViewRenderEvent.FogColors event) {
        if (active && density > EPSILON) {
            event.red = (float) red;
            event.green = (float) green;
            event.blue = (float) blue;
        }
    }

    @SubscribeEvent
    public void onFogDensity(EntityViewRenderEvent.FogDensity event) {
        if (active && density > EPSILON) {
            event.setCanceled(true);
            event.density = (float) density;
            // 取消后 vanilla 只写 GL_FOG_DENSITY 不改模式；此处强制 EXP 密度才真正作用于画面
            GL11.glFogi(GL11.GL_FOG_MODE, GL11.GL_EXP);
            GL11.glFogf(GL11.GL_FOG_DENSITY, (float) density);
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !active || dust <= 0
                || mc.theWorld == null || mc.thePlayer == null || mc.isGamePaused()) {
            return;
        }
        // 围绕玩家生成漂浮尘粒（1.7.10 用 EntityAuraFX 直接入效果渲染器，无 EnumParticleTypes.TOWN_AURA）
        for (int i = 0; i < dust; i++) {
            double px = mc.thePlayer.posX + 20 * (rand.nextDouble() - 0.5D);
            double py = mc.thePlayer.posY + 10 * (rand.nextDouble() - 0.5D);
            double pz = mc.thePlayer.posZ + 20 * (rand.nextDouble() - 0.5D);
            mc.effectRenderer.addEffect(new EntityAuraFX(mc.theWorld, px, py, pz, 0.0D, 0.0D, 0.0D));
        }
    }
}