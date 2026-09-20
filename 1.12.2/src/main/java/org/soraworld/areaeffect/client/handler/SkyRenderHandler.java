package org.soraworld.areaeffect.client.handler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldProvider;
import net.minecraftforge.client.IRenderHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

import java.util.Random;

/**
 * 天空效果渲染挂钩：持有当前天空色状态，并把 {@link AreaSkyRenderer} 安装到
 * {@link WorldProvider#setSkyRenderer}（1.7.10 无天空色事件，只能整块接管天空）。
 * 激活时安装自定义渲染器、恢复时卸载交还原版。
 *
 * <p>{@link AreaSkyRenderer} 复刻 {@code RenderGlobal.renderSky} 的表面分支：天空顶盖
 *（平铺平面）、日出日落霞光带、太阳、月亮、星空均保留，仅把天空颜色换为效果目标色。
 * 非地表维度（下界 / 末地）原版本就不画天空，本渲染器同样直接返回、不接管。
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
        if (event.phase != TickEvent.Phase.END || mc.world == null || mc.world.provider == null) {
            return;
        }
        sampleAtmosphere();
        WorldProvider provider = mc.world.provider;
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
        Entity view = mc.getRenderViewEntity();
        if (view == null) {
            return;
        }
        Vec3d color = mc.world.getSkyColor(view, 0.0F);
        if (color == null) {
            return;
        }
        atmRed = (float) color.x;
        atmGreen = (float) color.y;
        atmBlue = (float) color.z;
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
            // 原版 RenderGlobal#renderSky 是"有自定义渲染器就调用并 return"，其后的分派为：
            // dimensionId == 1（末地）画方形天空 → isSurfaceWorld() 画穹顶 → 其余维度什么都不画。
            // 自定义渲染器整块接管了这三条分支，所以必须自己把"非地表维度不画"补回来，
            // 否则末地的方形天空会被主世界式穹顶顶替、下界基岩层之上也会凭空多出一块穹顶。
            // 不接管时直接返回、不碰任何 GL 状态，渲染表现与"没有自定义渲染器"一致。
            if (!world.provider.isSurfaceWorld()) {
                return;
            }
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_FOG);
            GL11.glColor3f(SkyRenderHandler.red, SkyRenderHandler.green, SkyRenderHandler.blue);
            drawSkyDome();
            GL11.glDisable(GL11.GL_FOG);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(770, 771, 1, 0);
            RenderHelper.disableStandardItemLighting();

            // 日出/日落霞光带：原版这层来自 WorldProvider#calcSunriseSunsetColors（地平线一圈的放射渐变），
            // 不画的话换色后的天空会比原版"平"，日落时尤其明显。顶点与配色完全照抄原版 renderSky。
            drawSunriseSunset(world, mcw, partialTicks);

            GL11.glEnable(GL11.GL_TEXTURE_2D);
            OpenGlHelper.glBlendFunc(770, 1, 1, 0);
            GL11.glPushMatrix();
            float rain = 1.0F - world.getRainStrength(partialTicks);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, rain);
            GL11.glRotatef(-90.0F, 0.0F, 1.0F, 0.0F);
            GL11.glRotatef(world.getCelestialAngle(partialTicks) * 360.0F, 1.0F, 0.0F, 0.0F);
            drawSun(mcw);
            drawMoon(mcw, world);

            GL11.glDisable(GL11.GL_TEXTURE_2D);
            float starBrightness = world.getStarBrightness(partialTicks) * rain;
            if (starBrightness > 0.0F) {
                GL11.glColor4f(starBrightness, starBrightness, starBrightness, starBrightness);
                drawStars();
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
        private static void drawSkyDome() {
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();
            int b2 = 64;
            int i = 6;
            float f = 16.0F;
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);
            for (int j = -b2 * i; j <= b2 * i; j += b2) {
                for (int k = -b2 * i; k <= b2 * i; k += b2) {
                    buffer.pos(j, f, k).endVertex();
                    buffer.pos(j + b2, f, k).endVertex();
                    buffer.pos(j + b2, f, k + b2).endVertex();
                    buffer.pos(j, f, k + b2).endVertex();
                }
            }
            tessellator.draw();
        }

        /**
         * 日出/日落霞光带：与原版 {@code RenderGlobal.renderSky} 同款 —— 三角形扇 + 顶点色插值，
         * 中心点用 {@code colors[3]} 作 alpha、外圈 alpha 为 0，形成向地平线外淡出的放射渐变。
         */
        private static void drawSunriseSunset(WorldClient world, Minecraft mcw, float partialTicks) {
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
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();
            buffer.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);
            buffer.pos(0.0D, 100.0D, 0.0D).color(red, green, blue, colors[3]).endVertex();
            for (int i = 0; i <= 16; ++i) {
                float angle = (float) i * (float) Math.PI * 2.0F / 16.0F;
                float sin = MathHelper.sin(angle);
                float cos = MathHelper.cos(angle);
                buffer.pos((double) (sin * 120.0F), (double) (cos * 120.0F),
                        (double) (-cos * 40.0F * colors[3])).color(colors[0], colors[1], colors[2], 0.0F).endVertex();
            }
            tessellator.draw();
            GL11.glPopMatrix();
            GL11.glShadeModel(GL11.GL_FLAT);
        }

        private static void drawSun(Minecraft mcw) {
            float f = 30.0F;
            mcw.renderEngine.bindTexture(LOCATION_SUN);
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
            buffer.pos(-f, 100.0D, -f).tex(0.0D, 0.0D).endVertex();
            buffer.pos(f, 100.0D, -f).tex(1.0D, 0.0D).endVertex();
            buffer.pos(f, 100.0D, f).tex(1.0D, 1.0D).endVertex();
            buffer.pos(-f, 100.0D, f).tex(0.0D, 1.0D).endVertex();
            tessellator.draw();
        }

        private static void drawMoon(Minecraft mcw, WorldClient world) {
            float f = 20.0F;
            mcw.renderEngine.bindTexture(LOCATION_MOON);
            int k = world.getMoonPhase();
            int l = k % 4;
            int i1 = k / 4 % 2;
            float f14 = (l + 0) / 4.0F;
            float f15 = (i1 + 0) / 2.0F;
            float f16 = (l + 1) / 4.0F;
            float f17 = (i1 + 1) / 2.0F;
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
            buffer.pos(-f, -100.0D, f).tex(f16, f17).endVertex();
            buffer.pos(f, -100.0D, f).tex(f14, f17).endVertex();
            buffer.pos(f, -100.0D, -f).tex(f14, f15).endVertex();
            buffer.pos(-f, -100.0D, -f).tex(f16, f15).endVertex();
            tessellator.draw();
        }

        /** 绘制星空：首帧按 {@code RenderGlobal.renderStars} 同款算法（Random(10842L)）生成并缓存四边形。 */
        private void drawStars() {
            if (starQuads == null) {
                starQuads = buildStars();
            }
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);
            int base = 0;
            int starCount = starQuads.length / 12;
            for (int i = 0; i < starCount; i++) {
                int p = base + i * 12;
                buffer.pos(starQuads[p], starQuads[p + 1], starQuads[p + 2]).endVertex();
                buffer.pos(starQuads[p + 3], starQuads[p + 4], starQuads[p + 5]).endVertex();
                buffer.pos(starQuads[p + 6], starQuads[p + 7], starQuads[p + 8]).endVertex();
                buffer.pos(starQuads[p + 9], starQuads[p + 10], starQuads[p + 11]).endVertex();
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