package org.soraworld.areaeffect.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_BORDER;
import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_SLIDER_FILL;
import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_SLIDER_TEXT;
import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_SLIDER_THUMB;
import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_SLIDER_TRACK;
import static org.soraworld.areaeffect.client.gui.GuiTheme.argb;

/**
 * 扁平滑动条：拖动步进取整，天蓝填充 + 白色滑块，显示"标签 数值"。
 *
 * <p>取值区间与步进由构造参数决定；{@link #setValueRaw(float)} 用于外部直接设值
 * （切换选中项时用，不做步进），{@link #getValue()} 供保存时读取。
 */
class FlatSlider extends GuiButton {

    private final float min;
    private final float max;
    private final float step;
    private final String label;
    private final boolean integerStep;
    private float value;
    private boolean dragging;

    FlatSlider(int x, int y, int w, int h, String label, float min, float max, float initial, float step) {
        super(-1, x, y, w, h, "");
        this.label = label;
        this.min = min;
        this.max = max;
        this.step = step;
        this.integerStep = step >= 1.0F;
        setValue(initial);
    }

    float getValue() {
        return value;
    }

    /** 外部直接设定值（选中项切换），不做步进。 */
    void setValueRaw(float v) {
        value = Math.max(min, Math.min(max, v));
        updateDisplay();
    }

    private void setValue(float v) {
        v = Math.max(min, Math.min(max, v));
        if (step > 0.0F) {
            v = Math.round(v / step) * step;
        }
        value = v;
        updateDisplay();
    }

    private void updateDisplay() {
        displayString = label + " " + (integerStep
                ? String.format("%.0f", (double) value)
                : String.format("%.1f", (double) value));
    }

    private void setValueFromMouse(int mouseX) {
        // 与绘制几何一致：滑块中心对齐鼠标（滑块宽 8）
        float frac = (float) (mouseX - xPosition - 8) / (float) (width - 16);
        frac = Math.max(0.0F, Math.min(1.0F, frac));
        setValue(min + frac * (max - min));
    }

    @Override
    public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
        if (super.mousePressed(mc, mouseX, mouseY)) {
            dragging = true;
            setValueFromMouse(mouseX);
            return true;
        }
        return false;
    }

    @Override
    protected void mouseDragged(Minecraft mc, int mouseX, int mouseY) {
        if (dragging) {
            setValueFromMouse(mouseX);
        }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY) {
        dragging = false;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (!visible) {
            return;
        }
        // 【不可删除】1.7.10 没有任何框架回调会调用 mouseDragged：GuiScreen.mouseClickMove
        // 是空实现（GuiScreen.java 中方法体为空），GuiButton.mouseDragged 也没有调用方，
        // mouseClicked 只处理按下、不转发拖动。拖动之所以生效完全依赖这里每帧自驱一次，
        // 删掉这一行即所有滑条都无法拖动。
        this.mouseDragged(mc, mouseX, mouseY);
        // 轨道
        drawRect(xPosition, yPosition, xPosition + width, yPosition + height, argb(COLOR_SLIDER_TRACK));
        drawRect(xPosition, yPosition, xPosition + width, yPosition + 1, argb(COLOR_BORDER));
        drawRect(xPosition, yPosition + height - 1, xPosition + width, yPosition + height, argb(COLOR_BORDER));
        drawRect(xPosition, yPosition, xPosition + 1, yPosition + height, argb(COLOR_BORDER));
        drawRect(xPosition + width - 1, yPosition, xPosition + width, yPosition + height, argb(COLOR_BORDER));
        // 填充与滑块（8px 宽，便于拖动）
        float frac = (value - min) / (max - min);
        int thumbW = 8;
        int track = width - 8 - thumbW;
        int tx = xPosition + 4 + (int) (track * frac);
        if (tx > xPosition + 4) {
            drawRect(xPosition + 3, yPosition + 3, tx + thumbW / 2, yPosition + height - 3, argb(COLOR_SLIDER_FILL));
        }
        drawRect(tx, yPosition + 2, tx + thumbW, yPosition + height - 2, argb(COLOR_SLIDER_THUMB));
        drawCenteredString(mc.fontRenderer, displayString,
                xPosition + width / 2, yPosition + (height - 8) / 2, COLOR_SLIDER_TEXT);
    }
}
