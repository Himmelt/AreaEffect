package org.soraworld.areaeffect.client.handler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.NativeImage;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.soraworld.areaeffect.AreaEffectMod;
import org.soraworld.areaeffect.common.util.GammaCurve;

import java.lang.reflect.Field;

/**
 * Takes over the receiving end of the vanilla lightmap without ever touching
 * {@code GameSettings.gammaSetting}: the {@link DynamicTexture} used for the
 * lightmap is swapped for a delegating subclass whose
 * {@code updateDynamicTexture()} applies a uniform CIE L* offset to the freshly
 * computed pixels, then lets the original upload run. Because the upload stays
 * on the original texture object, the GL id registered under the vanilla
 * resource location never changes.
 *
 * <p>The offset is channel-order agnostic (the same per channel LUT is applied
 * to the three low bytes, the alpha high byte is preserved) and {@code 0}
 * means an exact vanilla passthrough. Installation uses a pure type scan (only
 * class literals, which FML remaps at runtime, so it survives obfuscation) and
 * requires the lightmap texture to be the only field of its type; if the
 * structure is not recognised the hook simply stays uninstalled and vanilla
 * behaviour is kept.
 *
 * <p>1.13 的结构变化：光照贴图从 {@code EntityRenderer}（今 {@link GameRenderer}）
 * 自己的 {@code DynamicTexture} 字段搬进了新类 {@link LightTexture}，像素也不再是
 * {@code int[]} 而是 {@link NativeImage}。因此安装路径变为两级：
 * GameRenderer 上唯一的 {@code LightTexture} → 它内部的唯一 {@code DynamicTexture}。
 * 逐像素读写改走 NativeImage 的 RGBA 存取器（其 32 位像素按 RGBA 字节序，
 * 即 int 低位是 R），亮度映射本身与 1.12 完全一致。
 */
@OnlyIn(Dist.CLIENT)
public final class LightmapHook {

    private static final Logger LOGGER = LogManager.getLogger(AreaEffectMod.MOD_NAME);

    private static boolean installed = false;
    /** 结构歧义（同类型字段多于一个）时置位：停止每帧重试与重试告警。 */
    private static boolean installAborted = false;
    /** 目标亮度（CIE L*），blend=1 时整张贴图统一到该亮度。 */
    private static volatile double lightness = 0.0D;
    /** 混合系数：0 = vanilla 原样，1 = 完全统一到目标亮度。 */
    private static volatile double blend = 0.0D;
    private static volatile int[] lut = null;
    private static volatile double lutLightness = Double.NaN;
    private static volatile double lutBlend = Double.NaN;
    /** 已替换的贴图实例：输入变化时主动触发上传。 */
    private static volatile HookedTexture hooked = null;
    /** vanilla 像素备份：混合映射非幂等，上传后必须还原防止复合。 */
    private static int[] pixelBackup = null;
    /** 安装线程（客户端渲染线程）：仅在该线程允许主动上传。 */
    private static volatile Thread renderThread = null;

    /**
     * 256 项「输入码值 → 感知亮度（CIE L*）」基础表。与 {@code lightness}/{@code blend}
     * 无关，进程内只算一次；否则过渡动画期间每帧重建 LUT 都要重复 256 次 pow + cbrt。
     */
    private static final double[] BASE_LIGHTNESS = new double[256];

    static {
        for (int i = 0; i < 256; i++) {
            BASE_LIGHTNESS[i] = GammaCurve.lightnessFromCode(i / 255.0D);
        }
    }

    private LightmapHook() {
    }

    /**
     * 设置当前混合输入：目标亮度 {@code lightness}（CIE L*）与混合系数 {@code blend}。
     * {@code blend <= 0} 时为 vanilla 精确透传。输入变化时在渲染线程主动上传
     * 光照贴图，不依赖 vanilla 的 lightmapUpdateNeeded 标志（GUI 打开时该标志
     * 可能长期不置位，导致预览延迟）。
     */
    public static void setOffset(double lightness, double blend) {
        boolean changed = lightness != LightmapHook.lightness || blend != LightmapHook.blend;
        LightmapHook.lightness = lightness;
        LightmapHook.blend = blend;
        if (changed && hooked != null && Thread.currentThread() == renderThread) {
            hooked.updateDynamicTexture();
        }
    }

    /**
     * Locates the lightmap texture and swaps in the hooking wrapper. Cheap to
     * call every tick; does nothing once installed, and keeps looking until
     * the renderer actually exists.
     */
    public static void tryInstall(Minecraft mc) {
        if (installed || installAborted || mc == null) {
            return;
        }
        GameRenderer renderer = mc.gameRenderer;
        if (renderer == null) {
            return;
        }
        try {
            // 第一步：GameRenderer 上的光照贴图对象。定位前提是「类型唯一」——若其它模组
            // 新增了同类型字段就无法判断该换哪一个，此时宁可放弃安装（保持 vanilla 表现），
            // 也不能换错对象：换错是静默失败，画面看不出异常，光照却再也不会更新。
            Field lightmapField = uniqueField(GameRenderer.class, LightTexture.class);
            if (lightmapField == null) {
                aborted(GameRenderer.class, LightTexture.class);
                return;
            }
            Object lightmap = lightmapField.get(renderer);
            if (!(lightmap instanceof LightTexture)) {
                return;
            }
            // 第二步：LightTexture 内部的贴图。1.13 的 LightTexture 恰好持有一个
            // DynamicTexture（另有一个 NativeImage 是同一份像素的宿主），同样按类型唯一判定。
            Field textureField = uniqueField(LightTexture.class, DynamicTexture.class);
            if (textureField == null) {
                aborted(LightTexture.class, DynamicTexture.class);
                return;
            }
            Object texture = textureField.get(lightmap);
            if (!(texture instanceof DynamicTexture) || texture instanceof HookedTexture) {
                return;
            }
            HookedTexture wrapper = new HookedTexture((DynamicTexture) texture);
            textureField.set(lightmap, wrapper);
            hooked = wrapper;
            renderThread = Thread.currentThread();
            installed = true;
        } catch (Throwable ignored) {
            // unexpected structure or mapping: keep vanilla behaviour
        }
    }

    /**
     * {@code owner} 上类型恰为 {@code type} 的唯一字段（已置为可访问）。
     * 同类型字段多于一个时返回 null —— 无法判断该换哪一个，调用方据此放弃安装。
     */
    private static Field uniqueField(Class<?> owner, Class<?> type) {
        Field target = null;
        int count = 0;
        for (Field f : owner.getDeclaredFields()) {
            if (f.getType() == type) {
                target = f;
                count++;
            }
        }
        if (count != 1) {
            return null;
        }
        target.setAccessible(true);
        return target;
    }

    /** 结构歧义（同类型字段 0 个或多个）时置位并告警一次，停止每帧重试。 */
    private static void aborted(Class<?> owner, Class<?> type) {
        installAborted = true;
        LOGGER.warn("Lightmap hook disabled: expected exactly one {} field in {}, "
                + "refusing to replace a possibly wrong object", type.getName(), owner.getName());
    }

    /**
     * 256 项 LUT：把每个输入码值的感知亮度（CIE L*）向目标亮度按 blend 插值。
     * blend=0 时是恒等映射（不过此情况下走透传，不会用到），blend=1 时全部
     * 映射到目标亮度，中间值则逐像素连续混合，两端均无跳变。
     *
     * <p>正向（码值 → L*）用静态基础表，每帧只做一次加权；逆向（L* → 码值）保持
     * 精确数学不做近似，以免牺牲暗部的码值精度。输入未变化时直接复用上次结果。
     */
    private static int[] getLut() {
        double l = lightness;
        double b = blend;
        int[] table = lut;
        if (table != null && lutLightness == l && lutBlend == b) {
            return table;
        }
        double clampedL = clamp(l, 0.0D, 100.0D);
        table = new int[256];
        for (int i = 0; i < 256; i++) {
            double lo = BASE_LIGHTNESS[i] * (1.0D - b) + clampedL * b;
            table[i] = (int) Math.round(255.0D * clamp(GammaCurve.toCode(GammaCurve.toLuminance(lo)), 0.0D, 1.0D));
        }
        lut = table;
        lutLightness = l;
        lutBlend = b;
        return table;
    }

    private static double clamp(double value, double min, double max) {
        // NaN and -Infinity land on min, +Infinity lands on max
        return !(value >= min) ? min : (value > max ? max : value);
    }

    /**
     * Delegating texture: post-processes the original's pixel image (the very
     * image vanilla writes every frame) and then delegates the upload, so the
     * registered texture keeps its GL id and content ordering.
     */
    private static class HookedTexture extends DynamicTexture {

        private final DynamicTexture origin;

        HookedTexture(DynamicTexture origin) {
            super(16, 16, false);
            // 构造仅为继承 DynamicTexture 类型；这张 16x16 贴图本类从不 bind，
            // GL id 由 AbstractTexture 惰性分配，故此处删除只为杜绝"万一"的分配
            deleteGlTexture();
            this.origin = origin;
        }

        @Override
        public void updateDynamicTexture() {
            if (blend <= 0.0D) {
                origin.updateDynamicTexture();
                return;
            }
            NativeImage image = origin.getTextureData();
            if (image == null) {
                origin.updateDynamicTexture();
                return;
            }
            int[] table = getLut();
            int width = image.getWidth();
            int height = image.getHeight();
            int size = width * height;
            // 混合 LUT 非幂等：备份 vanilla 像素，映射后上传，再还原。
            // 之后的主动上传/vanilla 重算都基于干净的原始值，不会反复复合。
            int[] backup = pixelBackup;
            if (backup == null || backup.length != size) {
                backup = pixelBackup = new int[size];
            }
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    int p = image.getPixelRGBA(x, y);
                    backup[y * width + x] = p;
                    image.setPixelRGBA(x, y, map(p, table));
                }
            }
            origin.updateDynamicTexture();
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    image.setPixelRGBA(x, y, backup[y * width + x]);
                }
            }
        }

        /**
         * 单像素的亮度映射：保留 alpha，三个颜色分量各自过 LUT。
         *
         * <p>NativeImage 的 32 位像素按 RGBA 字节序存放（int 低位是 R，即 {@code 0xAABBGGRR}），
         * 与 1.12 的 {@code int[] ARGB} 布局相反，故各分量的取值位置也相应互换。
         */
        private static int map(int pixel, int[] table) {
            return (pixel & 0xFF000000)
                    | (table[(pixel >>> 16) & 0xFF] << 16)
                    | (table[(pixel >>> 8) & 0xFF] << 8)
                    | table[pixel & 0xFF];
        }
    }
}
