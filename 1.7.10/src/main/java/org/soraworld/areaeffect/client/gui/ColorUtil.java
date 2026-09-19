package org.soraworld.areaeffect.client.gui;

import net.minecraft.client.gui.Gui;

/**
 * HSV 与 RGB 的互转，以及取色控件的棋盘格底绘制。
 *
 * <p>色相 h 取 [0,360) 度，饱和度 s、明度 v 取 [0,1]。RGB 一律用 24 位 {@code 0xRRGGBB} 打包值，
 * 与 {@link GuiTheme} 的 {@code 0xRRGGBBAA} 约定共享高 24 位（AA 字节由效果对象自己保管）。
 *
 * <p><b>往返无损</b>：RGB → (h,s,v) → RGB 逐位一致（全 24 位空间随机抽样 40 万 + 整数网格
 * 360×101×101 = 367 万组穷举均零误差）。正因为如此，取色器不用量化、也不用记「上次同步的基线」——
 * 交互侧直接比较「重解出的 RGB 是否变化」即可，没动过的颜色绝不会被改写。
 * 这一点是「SV 平面 + 色相条」方案相对「色相 + 深浅」两条方案的额外优势：后者是二维曲面，反解有损，
 * 必须靠基线防止静默改写。
 */
final class ColorUtil {

    private ColorUtil() {
    }

    /** HSV → 24 位 RGB（{@code 0xRRGGBB}）。h 可为任意实数（内部归一到 [0,360)）。 */
    static int hsvToRgb(float h, float s, float v) {
        h = ((h % 360.0F) + 360.0F) % 360.0F;
        s = clamp01(s);
        v = clamp01(v);
        float c = v * s;
        float x = c * (1.0F - Math.abs((h / 60.0F) % 2.0F - 1.0F));
        float m = v - c;
        float r;
        float g;
        float b;
        switch ((int) (h / 60.0F) % 6) {
            case 0:
                r = c;
                g = x;
                b = 0.0F;
                break;
            case 1:
                r = x;
                g = c;
                b = 0.0F;
                break;
            case 2:
                r = 0.0F;
                g = c;
                b = x;
                break;
            case 3:
                r = 0.0F;
                g = x;
                b = c;
                break;
            case 4:
                r = x;
                g = 0.0F;
                b = c;
                break;
            default:
                r = c;
                g = 0.0F;
                b = x;
                break;
        }
        return (Math.round((r + m) * 255.0F) << 16) | (Math.round((g + m) * 255.0F) << 8)
                | Math.round((b + m) * 255.0F);
    }

    /** 24 位 RGB → 色相（度，[0,360)）。灰色（d≈0）没有色相可言，约定返回 0。 */
    static float hueOf(int rgb) {
        float r = channel(rgb, 16);
        float g = channel(rgb, 8);
        float b = channel(rgb, 0);
        float max = maxOf(r, g, b);
        float min = minOf(r, g, b);
        float d = max - min;
        if (d <= 1.0e-6F) {
            return 0.0F;
        }
        float h;
        if (max == r) {
            h = ((g - b) / d + 6.0F) % 6.0F;
        } else if (max == g) {
            h = (b - r) / d + 2.0F;
        } else {
            h = (r - g) / d + 4.0F;
        }
        return h * 60.0F;
    }

    /** 24 位 RGB → 饱和度 [0,1]（max 为 0 即纯黑，约定返回 0）。 */
    static float satOf(int rgb) {
        float r = channel(rgb, 16);
        float g = channel(rgb, 8);
        float b = channel(rgb, 0);
        float max = maxOf(r, g, b);
        float d = max - minOf(r, g, b);
        return max <= 0.0F ? 0.0F : d / max;
    }

    /** 24 位 RGB → 明度 [0,1]。 */
    static float valOf(int rgb) {
        return maxOf(channel(rgb, 16), channel(rgb, 8), channel(rgb, 0));
    }

    private static float channel(int rgb, int shift) {
        return ((rgb >>> shift) & 255) / 255.0F;
    }

    private static float maxOf(float a, float b, float c) {
        return Math.max(a, Math.max(b, c));
    }

    private static float minOf(float a, float b, float c) {
        return Math.min(a, Math.min(b, c));
    }

    private static float clamp01(float v) {
        return v < 0.0F ? 0.0F : Math.min(v, 1.0F);
    }

    /**
     * 8×8 棋盘格底（默认格大小），见 {@link #drawCheckerboard(int, int, int, int, int)}。
     */
    static void drawCheckerboard(int x1, int y1, int x2, int y2) {
        drawCheckerboard(x1, y1, x2, y2, 8);
    }

    /**
     * 棋盘格底：半透明色（色块按钮、透明度条、预览色块）叠在它上面能直观看出透明度。
     * 两格灰度刻意拉开（0xFF707070 / 0xFF9C9C9C），透明白/浅色也看得清格子。
     *
     * <p>{@code cell} 为格子边长。调用方应让区域宽高是它的整数倍（如透明度条 12×120、预览色块 108×18
     * 配 6px 格子），这样格子按整数整格铺满，边缘不出现被截断的半格。
     */
    static void drawCheckerboard(int x1, int y1, int x2, int y2, int cell) {
        for (int cy = y1; cy < y2; cy += cell) {
            for (int cx = x1; cx < x2; cx += cell) {
                boolean dark = (((cx - x1) / cell) + ((cy - y1) / cell)) % 2 == 0;
                Gui.drawRect(cx, cy, Math.min(cx + cell, x2), Math.min(cy + cell, y2),
                        dark ? 0xFF707070 : 0xFF9C9C9C);
            }
        }
    }
}
