package org.soraworld.areaeffect.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

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
 */
class FlatButton extends GuiButton {

    FlatButton(int id, int x, int y, int w, int h, String label) {
        super(id, x, y, w, h, label);
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (!visible) {
            return;
        }
        boolean hover = enabled && mouseX >= xPosition && mouseX <= xPosition + width
                && mouseY >= yPosition && mouseY <= yPosition + height;
        int bg = !enabled ? COLOR_BTN_DISABLED : hover ? COLOR_BTN_HOVER : COLOR_BTN_BG;
        drawRect(xPosition, yPosition, xPosition + width, yPosition + height, argb(bg));
        drawRect(xPosition, yPosition, xPosition + width, yPosition + 1, argb(COLOR_BORDER));
        drawRect(xPosition, yPosition + height - 1, xPosition + width, yPosition + height, argb(COLOR_BORDER));
        drawRect(xPosition, yPosition, xPosition + 1, yPosition + height, argb(COLOR_BORDER));
        drawRect(xPosition + width - 1, yPosition, xPosition + width, yPosition + height, argb(COLOR_BORDER));
        int color = enabled ? COLOR_TEXT_BODY : COLOR_TEXT_DISABLED;
        drawCenteredString(mc.fontRenderer, displayString,
                xPosition + width / 2, yPosition + (height - 8) / 2, color);
    }
}
