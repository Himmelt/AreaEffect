package org.soraworld.areaeffect.handler;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.soraworld.areaeffect.util.GammaCurve;

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
    private static volatile double offset = 0.0D;
    private static volatile int[] lut = null;
    private static volatile double lutFor = Double.NaN;

    private LightmapHook() {
    }

    /**
     * Sets the current uniform lightness offset (CIE L*); {@code 0} is vanilla exact.
     */
    public static void setOffset(double dl) {
        offset = dl;
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
        field.set(owner, new HookedTexture((DynamicTexture) value));
        installed = true;
        return true;
    }

    /**
     * 256 entry LUT: code value -> code value, shifted by {@code offset} in CIE L* space.
     */
    private static int[] getLut() {
        double dl = offset;
        int[] table = lut;
        if (table != null && lutFor == dl) {
            return table;
        }
        table = new int[256];
        for (int c = 0; c < 256; c++) {
            double l = clamp(GammaCurve.lightnessFromCode(c / 255.0D) + dl, 0.0D, 100.0D);
            table[c] = (int) Math.round(255.0D * clamp(GammaCurve.toCode(GammaCurve.toLuminance(l)), 0.0D, 1.0D));
        }
        lut = table;
        lutFor = dl;
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
            this.origin = origin;
        }

        @Override
        public void updateDynamicTexture() {
            if (offset == 0.0D) {
                origin.updateDynamicTexture();
                return;
            }
            int[] pixels = origin.getTextureData();
            int[] table = getLut();
            for (int i = 0; i < pixels.length; i++) {
                int p = pixels[i];
                pixels[i] = (p & 0xFF000000)
                        | (table[(p >>> 16) & 0xFF] << 16)
                        | (table[(p >>> 8) & 0xFF] << 8)
                        | table[p & 0xFF];
            }
            origin.updateDynamicTexture();
        }
    }
}
