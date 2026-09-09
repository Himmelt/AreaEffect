package org.soraworld.areaeffect.client.handler;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.soraworld.areaeffect.common.util.GammaCurve;

import java.lang.reflect.Field;

/**
 * Takes over the receiving end of the vanilla lightmap without ever touching
 * {@code GameSettings.gammaSetting}: the {@link DynamicTexture} vanilla uses
 * for the lightmap is swapped for a delegating subclass whose
 * {@code updateDynamicTexture()} applies a uniform CIE L* offset to the freshly
 * computed pixels, then lets the original upload run. Because the upload stays
 * on the original texture object, the GL id registered under the vanilla
 * resource location never changes.
 *
 * <p>The offset is channel-order agnostic (the same per channel LUT is applied
 * to the three low bytes, the alpha high byte is preserved) and {@code 0}
 * means an exact vanilla passthrough. Installation uses a pure type scan, so
 * it survives obfuscation mappings; if the structure is not recognised the
 * hook simply stays uninstalled and vanilla behaviour is kept.
 */
@SideOnly(Side.CLIENT)
public final class LightmapHook {

    private static boolean installed = false;
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
        if (installed || mc == null) {
            return;
        }
        EntityRenderer renderer = mc.entityRenderer;
        if (renderer == null) {
            return;
        }
        try {
            // case one: the renderer itself holds the texture (1.7.10, 1.12.2)
            Field field = findField(EntityRenderer.class, DynamicTexture.class);
            if (field != null && wrap(field, renderer)) {
                return;
            }
            // case two: a lightmap wrapper object holds it (1.8.9 - 1.11.2)
            for (Field f : EntityRenderer.class.getDeclaredFields()) {
                Class<?> type = f.getType();
                if (type.isPrimitive() || type.isArray() || type == String.class
                        || !type.getSimpleName().contains("Light")) {
                    continue;
                }
                f.setAccessible(true);
                Object holder = f.get(renderer);
                if (holder == null) {
                    continue;
                }
                Field inner = findField(holder.getClass(), DynamicTexture.class);
                if (inner != null && wrap(inner, holder)) {
                    return;
                }
            }
        } catch (Throwable ignored) {
            // unexpected structure or mapping: keep vanilla behaviour
        }
    }

    /**
     * First declared field of exactly {@code type}, made accessible.
     */
    private static Field findField(Class<?> owner, Class<?> type) {
        for (Field f : owner.getDeclaredFields()) {
            if (f.getType() == type) {
                f.setAccessible(true);
                return f;
            }
        }
        return null;
    }

    private static boolean wrap(Field field, Object owner) throws IllegalAccessException {
        Object value = field.get(owner);
        if (!(value instanceof DynamicTexture) || value instanceof HookedTexture) {
            return false;
        }
        HookedTexture texture = new HookedTexture((DynamicTexture) value);
        field.set(owner, texture);
        hooked = texture;
        renderThread = Thread.currentThread();
        installed = true;
        return true;
    }

    /**
     * 256 项 LUT：把每个输入码值的感知亮度（CIE L*）向目标亮度按 blend 插值。
     * blend=0 时是恒等映射（不过此情况下走透传，不会用到），blend=1 时全部
     * 映射到目标亮度，中间值则逐像素连续混合，两端均无跳变。
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
            double lo = GammaCurve.lightnessFromCode(i / 255.0D) * (1.0D - b) + clampedL * b;
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
     * Delegating texture: post-processes the original's pixel array (the very
     * array vanilla writes every frame) and then delegates the upload, so the
     * registered texture keeps its GL id and content ordering.
     */
    private static class HookedTexture extends DynamicTexture {

        private final DynamicTexture origin;

        HookedTexture(DynamicTexture origin) {
            super(16, 16);
            // 构造仅为继承 DynamicTexture 类型，分配出的纹理本类永不使用，立即释放避免 GL id 泄漏
            deleteGlTexture();
            this.origin = origin;
        }

        @Override
        public void updateDynamicTexture() {
            if (blend <= 0.0D) {
                origin.updateDynamicTexture();
                return;
            }
            int[] pixels = origin.getTextureData();
            int[] table = getLut();
            // 混合 LUT 非幂等：备份 vanilla 像素，映射后上传，再还原。
            // 之后的主动上传/vanilla 重算都基于干净的原始值，不会反复复合。
            int[] backup = pixelBackup;
            if (backup == null || backup.length != pixels.length) {
                backup = pixelBackup = new int[pixels.length];
            }
            System.arraycopy(pixels, 0, backup, 0, pixels.length);
            for (int i = 0; i < pixels.length; i++) {
                int p = pixels[i];
                pixels[i] = (p & 0xFF000000)
                        | (table[(p >>> 16) & 0xFF] << 16)
                        | (table[(p >>> 8) & 0xFF] << 8)
                        | table[p & 0xFF];
            }
            origin.updateDynamicTexture();
            System.arraycopy(backup, 0, pixels, 0, pixels.length);
        }
    }
}
