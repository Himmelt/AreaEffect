package org.soraworld.areaeffect.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

import java.util.Locale;

import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_BORDER;
import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_SLIDER_TEXT;
import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_SLIDER_THUMB_HOT;
import static org.soraworld.areaeffect.client.gui.GuiTheme.argb;

/**
 * 取色入口按钮：背景就是当前颜色（先铺棋盘格再叠色，半透明可见），点它弹出取色器。
 *
 * <p>取代原先三行 HSV 取色滑条成为「颜色」这个参数在详情栏里的唯一控件 —— 一整行就是一块色板，
 * 直观反映当前颜色，把具体的取色交互收进弹窗。id 由宿主指定（GuiAreas 的 {@code BTN_COLOR}）。
 *
 * <p>文字是「标签 + #RRGGBBAA」，带阴影直接叠在色块上（不垫暗板）；悬停时描边转黄，与其余按钮观感一致。
 */
class ColorButton extends GuiButton {

    private final String label;
    /** 当前颜色 {@code 0xRRGGBBAA}。 */
    private int rgba;

    ColorButton(int id, int x, int y, int w, int h, String label, int rgba) {
        super(id, x, y, w, h, "");
        this.label = label;
        this.rgba = rgba;
    }

    /** 外部回填当前颜色（选中效果切换 / 取色器拖动时）。 */
    void setColor(int rgba) {
        this.rgba = rgba;
    }

    int getColor() {
        return rgba;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (!visible) {
            return;
        }
        boolean hover = enabled && mouseX >= x && mouseX < x + width
                && mouseY >= y && mouseY < y + height;
        // 棋盘格 + 颜色叠加（棋盘格在 alpha < 255 时透出）
        ColorUtil.drawCheckerboard(x + 1, y + 1, x + width - 1, y + height - 1);
        drawRect(x + 1, y + 1, x + width - 1, y + height - 1, argb(rgba));
        // 描边：悬停黄（与 FlatButton 的悬停高亮一致）
        int border = hover ? COLOR_SLIDER_THUMB_HOT : COLOR_BORDER;
        drawRect(x, y, x + width, y + 1, argb(border));
        drawRect(x, y + height - 1, x + width, y + height, argb(border));
        drawRect(x, y, x + 1, y + height, argb(border));
        drawRect(x + width - 1, y, x + width, y + height, argb(border));
        // 文字：标签 + 十六进制（#RRGGBBAA），带阴影直接叠在色块上（不垫暗板）
        String text = label + " " + String.format(Locale.ROOT, "#%08X", rgba);
        int tw = mc.fontRenderer.getStringWidth(text);
        int tx = x + (width - tw) / 2;
        int ty = y + (height - 8) / 2;
        mc.fontRenderer.drawStringWithShadow(text, tx, ty, COLOR_SLIDER_TEXT);
    }
}
