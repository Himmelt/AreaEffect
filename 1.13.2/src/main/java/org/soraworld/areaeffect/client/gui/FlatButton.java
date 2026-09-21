package org.soraworld.areaeffect.client.gui;

import net.minecraft.client.Minecraft;

import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_BORDER;
import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_BTN_BG;
import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_BTN_DISABLED;
import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_BTN_HOVER;
import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_TEXT_BODY;
import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_TEXT_DISABLED;
import static org.soraworld.areaeffect.client.gui.GuiTheme.argb;

/**
 * 扁平极简按钮：半透明深底 + 一像素描边 + 居中文字，悬停微亮。
 *
 * <p>替代 vanilla 的贴图按钮，避免引入材质文件；配色来自 {@link GuiTheme}。
 *
 * <p><b>框线共用</b>：整块界面只保留一层边框，相邻元素之间共用同一根线。因此按钮描边可以逐边裁剪
 * （见 {@link #B_LEFT} 等掩码）—— 贴在某一栏里的按钮，其左右两边由栏的共用竖线提供，组内两个按钮
 * 之间只留一根分隔线；否则会出现"两条 1px 并排"的加粗观感。
 */
class FlatButton extends AefButton {

    /** 描边掩码：只画选中的那几条边。 */
    static final int B_LEFT = 1;
    static final int B_RIGHT = 2;
    static final int B_TOP = 4;
    static final int B_BOTTOM = 8;
    static final int B_ALL = B_LEFT | B_RIGHT | B_TOP | B_BOTTOM;

    private final int borders;

    FlatButton(int id, int x, int y, int w, int h, String label) {
        this(id, x, y, w, h, label, B_ALL);
    }

    FlatButton(int id, int x, int y, int w, int h, String label, int borders) {
        super(id, x, y, w, h, label);
        this.borders = borders;
    }

    /** 1.13 的绘制钩子是 {@code render}(取代 {@code drawButton})，且不再传入 Minecraft 实例。 */
    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        if (!visible) {
            return;
        }
        boolean hover = enabled && mouseX >= x && mouseX <= x + width
                && mouseY >= y && mouseY <= y + height;
        int bg = !enabled ? COLOR_BTN_DISABLED : hover ? COLOR_BTN_HOVER : COLOR_BTN_BG;
        drawRect(x, y, x + width, y + height, argb(bg));
        if ((borders & B_TOP) != 0) {
            drawRect(x, y, x + width, y + 1, argb(COLOR_BORDER));
        }
        if ((borders & B_BOTTOM) != 0) {
            drawRect(x, y + height - 1, x + width, y + height, argb(COLOR_BORDER));
        }
        if ((borders & B_LEFT) != 0) {
            drawRect(x, y, x + 1, y + height, argb(COLOR_BORDER));
        }
        if ((borders & B_RIGHT) != 0) {
            drawRect(x + width - 1, y, x + width, y + height, argb(COLOR_BORDER));
        }
        int color = enabled ? COLOR_TEXT_BODY : COLOR_TEXT_DISABLED;
        drawCenteredString(Minecraft.getInstance().fontRenderer, displayString,
                x + width / 2, y + (height - 8) / 2, color);
    }
}
