package org.soraworld.areaeffect.client.handler;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import net.minecraft.world.WorldProvider;
import net.minecraftforge.client.IRenderHandler;
import org.lwjgl.opengl.GL11;

import java.util.Random;

/**
 * 天空效果渲染挂钩：持有当前天空色状态，并把 {@link AreaSkyRenderer} 安装到
 * {@link WorldProvider#setSkyRenderer}（1.7.10 无天空色事件，只能整块接管天空）。
 * 激活时安装自定义渲染器、恢复时卸载交还原版。
 *
 * <p>{@link AreaSkyRenderer} 复刻 {@code RenderGlobal.renderSky} 的表面分支：天空顶盖
 *（平铺平面）、日出日落霞光带、太阳、月亮、星空均保留，仅把天空颜色换为效果目标色。
 *
 * <p>另外每 tick 采样一份「原版天空色」（见 {@link #sampleAtmosphere()}），供
 * {@code SkyEffectRenderer} 在离开区域时当过渡终点用 —— 与雾效果的大气色采样同构。
 */
public class SkyRenderHandler {

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final AreaSkyRenderer AREA_SKY = new AreaSkyRenderer();

    private static volatile boolean active = false;
    private static volatile float red = 0.53F;
    private static volatile float green = 0.81F;
    private static volatile float blue = 0.92F;

    /**
     * 最近采样到的<b>原版天空色</b>（0..1）：不接管天空时画面上该有的那个色。
     *
     * <p>由 {@link #sampleAtmosphere()} 每客户端 tick 刷新。{@code SkyEffectRenderer} 拿它当
     * 「离开区域」时的过渡终点 —— 1.7.10 没有天空色事件，若不主动取，就只能淡向一个硬编码的
     * 白昼蓝，夜里离开天空区域会表现为突兀的假天亮（雾效果的大气色采样是同一思路）。
     */
    private static volatile float atmRed = 0.53F;
    private static volatile float atmGreen = 0.81F;
    private static volatile float atmBlue = 0.92F;

    public SkyRenderHandler() {
    }

    /** 见 {@link #atmRed}。 */
    public static double atmosphereRed() {
        return atmRed;
    }

    /** 见 {@link #atmRed}。 */
    public static double atmosphereGreen() {
        return atmGreen;
    }

    /** 见 {@link #atmRed}。 */
    public static double atmosphereBlue() {
        return atmBlue;
    }

    /** 由 {@code SkyEffectRenderer} 每帧写入当前天空色（0..1）。 */
    public static void setState(boolean active, double red, double green, double blue) {
        SkyRenderHandler.active = active;
        SkyRenderHandler.red = (float) red;
        SkyRenderHandler.green = (float) green;
        SkyRenderHandler.blue = (float) blue;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || mc.theWorld == null || mc.theWorld.provider == null) {
            return;
        }
        sampleAtmosphere();
        WorldProvider provider = mc.theWorld.provider;
        IRenderHandler current = provider.getSkyRenderer();
        if (active && current != AREA_SKY) {
            provider.setSkyRenderer(AREA_SKY);
        } else if (!active && current == AREA_SKY) {
            provider.setSkyRenderer(null);
        }
    }

    /**
     * 采样"此刻的原版天空色"，供离开区域时作过渡终点。
     *
     * <p>取的是 {@code World#getSkyColor} —— 原版 {@code RenderGlobal#renderSky} 正是用它给穹顶上色
     * （已含日月位置、生物群系温度与降雨的影响），因此它就是"不接管天空时该有的色"。
     *
     * <p>{@code partialTicks} 传 0：取"本 tick 的准确值"。1.7.10 的 {@code Minecraft#timer} 是私有
     * 字段，拿不到 {@code renderPartialTicks}；而天空色随日月位置变化极慢（游戏内一整天 20 分钟），
     * 半个 tick 的相位差在观感上不可见。
     */
    private static void sampleAtmosphere() {
        Entity view = mc.renderViewEntity;
        if (view == null) {
            return;
        }
        Vec3 color = mc.theWorld.getSkyColor(view, 0.0F);
        if (color == null) {
            return;
        }
        atmRed = (float) color.xCoord;
        atmGreen = (float) color.yCoord;
        atmBlue = (float) color.zCoord;
    }

    /**
     * 自定义天空渲染器：复刻原版表面天空，但天空颜色取自 {@link SkyRenderHandler#red}/{@link #green}/{@link #blue}。
     * 太阳/月亮/星空全部保留（星空为缓存的一组四边形，无需反射私有显示列表）。
     */
    static final class AreaSkyRenderer extends IRenderHandler {

        private static final ResourceLocation LOCATION_SUN = new ResourceLocation("textures/environment/sun.png");
        private static final ResourceLocation LOCATION_MOON = new ResourceLocation("textures/environment/moon_phases.png");

        /** 星空四边形顶点缓存：每星 12 个 float（4 顶点 × xyz），首次进入时按原版算法生成一次。 */
        private float[] starQuads = null;

        @Override
        public void render(float partialTicks, WorldClient world, Minecraft mcw) {
            Tessellator tessellator = Tessellator.instance;
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_FOG);
            GL11.glColor3f(SkyRenderHandler.red, SkyRenderHandler.green, SkyRenderHandler.blue);
            drawSkyDome(tessellator);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(770, 771, 1, 0);
            RenderHelper.disableStandardItemLighting();

            // 日出/日落霞光带：原版这层来自 WorldProvider#calcSunriseSunsetColors（地平线一圈的放射渐变），
            // 不画的话换色后的天空会比原版"平"，日落时尤其明显。顶点与配色完全照抄原版 renderSky。
            drawSunriseSunset(tessellator, world, mcw, partialTicks);

            GL11.glEnable(GL11.GL_TEXTURE_2D);
            OpenGlHelper.glBlendFunc(770, 1, 1, 0);
            GL11.glPushMatrix();
            float rain = 1.0F - world.getRainStrength(partialTicks);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, rain);
            GL11.glRotatef(-90.0F, 0.0F, 1.0F, 0.0F);
            GL11.glRotatef(world.getCelestialAngle(partialTicks) * 360.0F, 1.0F, 0.0F, 0.0F);
            drawSun(tessellator, mcw);
            drawMoon(tessellator, mcw, world);

            GL11.glDisable(GL11.GL_TEXTURE_2D);
            float starBrightness = world.getStarBrightness(partialTicks) * rain;
            if (starBrightness > 0.0F) {
                GL11.glColor4f(starBrightness, starBrightness, starBrightness, starBrightness);
                drawStars(tessellator);
            }
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glEnable(GL11.GL_FOG);
            GL11.glPopMatrix();

            GL11.glDepthMask(true);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
        }

        /** 天空顶盖：y=16 处横跨 ±384 的平铺平面（与原版 glSkyList 尺寸一致），用当前目标色、开启雾作地平线渐隐。 */
        private static void drawSkyDome(Tessellator tessellator) {
            int b2 = 64;
            int i = 6;
            float f = 16.0F;
            for (int j = -b2 * i; j <= b2 * i; j += b2) {
                for (int k = -b2 * i; k <= b2 * i; k += b2) {
                    tessellator.startDrawingQuads();
                    tessellator.addVertex(j, f, k);
                    tessellator.addVertex(j + b2, f, k);
                    tessellator.addVertex(j + b2, f, k + b2);
                    tessellator.addVertex(j, f, k + b2);
                    tessellator.draw();
                }
            }
        }

        /**
         * 日出/日落霞光带：与原版 {@code RenderGlobal.renderSky} 同款 —— 三角形扇 + 顶点色插值，
         * 中心点用 {@code colors[3]} 作 alpha、外圈 alpha 为 0，形成向地平线外淡出的放射渐变。
         */
        private static void drawSunriseSunset(Tessellator tessellator, WorldClient world, Minecraft mcw, float partialTicks) {
            float[] colors = world.provider.calcSunriseSunsetColors(world.getCelestialAngle(partialTicks), partialTicks);
            if (colors == null) {
                return;
            }
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glShadeModel(GL11.GL_SMOOTH);
            GL11.glPushMatrix();
            GL11.glRotatef(90.0F, 0.0F, 1.0F, 0.0F);
            GL11.glRotatef(MathHelper.sin(world.getCelestialAngleRadians(partialTicks)) < 0.0F ? 180.0F : 0.0F,
                    0.0F, 0.0F, 1.0F);
            GL11.glRotatef(90.0F, 0.0F, 0.0F, 1.0F);
            float red = colors[0];
            float green = colors[1];
            float blue = colors[2];
            if (mcw.gameSettings.anaglyph) {
                float r = (red * 30.0F + green * 59.0F + blue * 11.0F) / 100.0F;
                float g = (red * 30.0F + green * 70.0F) / 100.0F;
                float b = (red * 30.0F + blue * 70.0F) / 100.0F;
                red = r;
                green = g;
                blue = b;
            }
            tessellator.startDrawing(6);
            tessellator.setColorRGBA_F(red, green, blue, colors[3]);
            tessellator.addVertex(0.0D, 100.0D, 0.0D);
            tessellator.setColorRGBA_F(colors[0], colors[1], colors[2], 0.0F);
            for (int i = 0; i <= 16; ++i) {
                float angle = (float) i * (float) Math.PI * 2.0F / 16.0F;
                float sin = MathHelper.sin(angle);
                float cos = MathHelper.cos(angle);
                tessellator.addVertex((double) (sin * 120.0F), (double) (cos * 120.0F),
                        (double) (-cos * 40.0F * colors[3]));
            }
            tessellator.draw();
            GL11.glPopMatrix();
            GL11.glShadeModel(GL11.GL_FLAT);
        }

        private static void drawSun(Tessellator tessellator, Minecraft mcw) {
            float f = 30.0F;
            mcw.renderEngine.bindTexture(LOCATION_SUN);
            tessellator.startDrawingQuads();
            tessellator.addVertexWithUV(-f, 100.0D, -f, 0.0D, 0.0D);
            tessellator.addVertexWithUV(f, 100.0D, -f, 1.0D, 0.0D);
            tessellator.addVertexWithUV(f, 100.0D, f, 1.0D, 1.0D);
            tessellator.addVertexWithUV(-f, 100.0D, f, 0.0D, 1.0D);
            tessellator.draw();
        }

        private static void drawMoon(Tessellator tessellator, Minecraft mcw, WorldClient world) {
            float f = 20.0F;
            mcw.renderEngine.bindTexture(LOCATION_MOON);
            int k = world.getMoonPhase();
            int l = k % 4;
            int i1 = k / 4 % 2;
            float f14 = (l + 0) / 4.0F;
            float f15 = (i1 + 0) / 2.0F;
            float f16 = (l + 1) / 4.0F;
            float f17 = (i1 + 1) / 2.0F;
            tessellator.startDrawingQuads();
            tessellator.addVertexWithUV(-f, -100.0D, f, f16, f17);
            tessellator.addVertexWithUV(f, -100.0D, f, f14, f17);
            tessellator.addVertexWithUV(f, -100.0D, -f, f14, f15);
            tessellator.addVertexWithUV(-f, -100.0D, -f, f16, f15);
            tessellator.draw();
        }

        /** 绘制星空：首帧按 {@code RenderGlobal.renderStars} 同款算法（Random(10842L)）生成并缓存四边形。 */
        private void drawStars(Tessellator tessellator) {
            if (starQuads == null) {
                starQuads = buildStars();
            }
            tessellator.startDrawingQuads();
            int base = 0;
            int starCount = starQuads.length / 12;
            for (int i = 0; i < starCount; i++) {
                int p = base + i * 12;
                tessellator.addVertex(starQuads[p], starQuads[p + 1], starQuads[p + 2]);
                tessellator.addVertex(starQuads[p + 3], starQuads[p + 4], starQuads[p + 5]);
                tessellator.addVertex(starQuads[p + 6], starQuads[p + 7], starQuads[p + 8]);
                tessellator.addVertex(starQuads[p + 9], starQuads[p + 10], starQuads[p + 11]);
            }
            tessellator.draw();
        }

        private static float[] buildStars() {
            Random random = new Random(10842L);
            float[] quads = new float[1500 * 12];
            int idx = 0;
            for (int i = 0; i < 1500; ++i) {
                double d0 = random.nextFloat() * 2.0F - 1.0F;
                double d1 = random.nextFloat() * 2.0F - 1.0F;
                double d2 = random.nextFloat() * 2.0F - 1.0F;
                double d3 = 0.15F + random.nextFloat() * 0.1F;
                double d4 = d0 * d0 + d1 * d1 + d2 * d2;
                if (d4 < 1.0D && d4 > 0.01D) {
                    d4 = 1.0D / Math.sqrt(d4);
                    d0 *= d4;
                    d1 *= d4;
                    d2 *= d4;
                    double d5 = d0 * 100.0D;
                    double d6 = d1 * 100.0D;
                    double d7 = d2 * 100.0D;
                    double d8 = Math.atan2(d0, d2);
                    double d9 = Math.sin(d8);
                    double d10 = Math.cos(d8);
                    double d11 = Math.atan2(Math.sqrt(d0 * d0 + d2 * d2), d1);
                    double d12 = Math.sin(d11);
                    double d13 = Math.cos(d11);
                    double d14 = random.nextDouble() * Math.PI * 2.0D;
                    double d15 = Math.sin(d14);
                    double d16 = Math.cos(d14);
                    for (int j = 0; j < 4; ++j) {
                        double d17 = 0.0D;
                        double d18 = ((j & 2) - 1) * d3;
                        double d19 = ((j + 1 & 2) - 1) * d3;
                        double d20 = d18 * d16 - d19 * d15;
                        double d21 = d19 * d16 + d18 * d15;
                        double d22 = d20 * d12 + d17 * d13;
                        double d23 = d17 * d12 - d20 * d13;
                        double d24 = d23 * d9 - d21 * d10;
                        double d25 = d21 * d9 + d23 * d10;
                        quads[idx++] = (float) (d5 + d24);
                        quads[idx++] = (float) (d6 + d22);
                        quads[idx++] = (float) (d7 + d25);
                    }
                }
            }
            // 实际星数可能 < 1500（少数落在近原点被滤除），把剩余槽位置 0 不会绘制（菱形退化为点）。
            for (; idx < quads.length; idx++) {
                quads[idx] = 0.0F;
            }
            return quads;
        }
    }
}