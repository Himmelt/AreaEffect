package org.soraworld.areaeffect.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Keyboard;
import org.soraworld.areaeffect.client.ClientProxy;
import org.soraworld.areaeffect.common.effect.LightnessEffect;
import org.soraworld.areaeffect.common.network.Area;

import java.util.Collections;

/**
 * 区域详情界面：透明背景（直接可见游戏画面），扁平极简自绘。
 * 滑动条调节目标亮度/过渡时长并实时预览（只改客户端本地副本）。
 * 传送不关闭界面；保存走服务端流程后返回列表；删除/返回回到列表；Esc 关闭。
 * 不暂停游戏（doesGuiPauseGame = false），传送/保存即时生效。
 */
public class GuiAreaEdit extends GuiScreen {

    private static final int BTN_SAVE = 0;
    private static final int BTN_BACK = 1;
    private static final int BTN_TP = 2;
    private static final int BTN_DELETE = 3;
    private static final int BTN_SEL = 4;
    private static final int BTN_W = 70;
    private static final int BTN_H = 18;
    private static final int BTN_GAP = 8;

    private final ClientProxy proxy;
    private final int dim;
    private final Area area;

    /** 进入界面时的原始参数：未保存退出时据此还原本地副本。 */
    private float originalLightness;
    private float originalDuration;
    private boolean saved = false;

    private Slider lightSlider;
    private Slider durationSlider;
    private float previewL;
    private float previewD;

    public GuiAreaEdit(ClientProxy proxy, int dim, Area area) {
        this.proxy = proxy;
        this.dim = dim;
        this.area = area;
        this.originalLightness = area.getLightness();
        this.originalDuration = area.getDuration();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void initGui() {
        super.initGui();
        buttonList.clear();
        // 右上角：区域线框开关
        buttonList.add(new FlatBtn(BTN_SEL, width - 170, 8, 150, 16, selectionText()));
        // 底部按钮行：抬高于物品栏上方
        int total = BTN_W * 4 + BTN_GAP * 3;
        int x = (width - total) / 2;
        int y = height - 58;
        buttonList.add(new FlatBtn(BTN_TP, x, y, BTN_W, BTN_H, translate("gui.areaeffect.edit.tp")));
        buttonList.add(new FlatBtn(BTN_DELETE, x + BTN_W + BTN_GAP, y, BTN_W, BTN_H, translate("gui.areaeffect.edit.del")));
        buttonList.add(new FlatBtn(BTN_SAVE, x + (BTN_W + BTN_GAP) * 2, y, BTN_W, BTN_H, translate("gui.areaeffect.edit.save")));
        buttonList.add(new FlatBtn(BTN_BACK, x + (BTN_W + BTN_GAP) * 3, y, BTN_W, BTN_H, translate("gui.areaeffect.edit.cancel")));
        // 滑动条
        int sw = 260;
        int sx = (width - sw) / 2;
        lightSlider = new Slider(sx, 56, sw, 18,
                translate("gui.areaeffect.edit.lightness"), 0.0F, 100.0F, area.getLightness(), 1.0F);
        durationSlider = new Slider(sx, 84, sw, 18,
                translate("gui.areaeffect.edit.duration"), 0.0F, 60.0F, area.getDuration(), 0.5F);
        buttonList.add(lightSlider);
        buttonList.add(durationSlider);
        previewL = lightSlider.getValue();
        previewD = durationSlider.getValue();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // 透明背景：不绘制 drawDefaultBackground，游戏画面直接可见

        String title = String.format(translate("gui.areaeffect.edit.title"), area.id);
        drawCenteredString(fontRendererObj, title, width / 2, 10, 0xFFFFFF);
        drawCenteredString(fontRendererObj, area.pos1() + " ~ " + area.pos2(), width / 2, 24, 0xE0E0E0);

        // 参数面板（半透明底，提升滑动条可读性）
        int px1 = width / 2 - 140;
        int px2 = width / 2 + 140;
        drawRect(px1, 48, px2, 110, 0x99222222);

        // 滑动条变化 → 实时预览（只改客户端本地副本）
        float l = lightSlider.getValue();
        float d = durationSlider.getValue();
        if (l != previewL || d != previewD) {
            proxy.previewAreaProps(dim, area.id, l, d);
            previewL = l;
            previewD = d;
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            restoreIfUnsaved();
            mc.displayGuiScreen(null);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BTN_SAVE) {
            save();
            backToList();
        } else if (button.id == BTN_BACK) {
            backToList();
        } else if (button.id == BTN_TP) {
            // 传送不关闭界面
            proxy.sendTpRequest(area.id);
        } else if (button.id == BTN_DELETE) {
            proxy.sendDeleteRequest(dim, area.id);
            proxy.clientRemoveArea(dim, area.id);
            backToList();
        } else if (button.id == BTN_SEL) {
            proxy.toggleAreaVisible(dim, area.id);
            button.displayString = selectionText();
        }
    }

    /** 保存：发送服务端流程并立即更新本地副本。 */
    private void save() {
        float l = lightSlider.getValue();
        float d = durationSlider.getValue();
        proxy.sendSetProps(dim, area.id, Collections.singletonList(new LightnessEffect(l, d)));
        proxy.previewAreaProps(dim, area.id, l, d);
        originalLightness = l;
        originalDuration = d;
        saved = true;
    }

    /** 返回列表界面。 */
    private void backToList() {
        restoreIfUnsaved();
        mc.displayGuiScreen(new GuiAreaList(proxy, dim, proxy.getAreasLocal(dim)));
    }

    /** 未保存退出时把本地副本还原为进入界面时的参数。 */
    private void restoreIfUnsaved() {
        if (!saved) {
            proxy.previewAreaProps(dim, area.id, originalLightness, originalDuration);
        }
    }

    /** 区域线框开关按钮文案（控制该区域边界框的显示）。 */
    private String selectionText() {
        String state = translate(proxy.isAreaVisible(dim, area.id)
                ? "gui.areaeffect.selection.on"
                : "gui.areaeffect.selection.off");
        return translate("gui.areaeffect.edit.selection") + ": " + state;
    }

    private static String translate(String key) {
        return StatCollector.translateToLocal(key);
    }

    /**
     * 扁平极简按钮：半透明深底 + 一像素描边 + 居中文字，悬停微亮。
     */
    private static class FlatBtn extends GuiButton {

        FlatBtn(int id, int x, int y, int w, int h, String label) {
            super(id, x, y, w, h, label);
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY) {
            if (!visible) {
                return;
            }
            boolean hover = mouseX >= xPosition && mouseX <= xPosition + width
                    && mouseY >= yPosition && mouseY <= yPosition + height;
            int bg = !enabled ? 0xAA111111 : hover ? 0xBB2E3E2E : 0xAA222222;
            drawRect(xPosition, yPosition, xPosition + width, yPosition + height, bg);
            drawRect(xPosition, yPosition, xPosition + width, yPosition + 1, 0xFF3E3E3E);
            drawRect(xPosition, yPosition + height - 1, xPosition + width, yPosition + height, 0xFF3E3E3E);
            drawRect(xPosition, yPosition, xPosition + 1, yPosition + height, 0xFF3E3E3E);
            drawRect(xPosition + width - 1, yPosition, xPosition + width, yPosition + height, 0xFF3E3E3E);
            drawCenteredString(mc.fontRenderer, displayString,
                    xPosition + width / 2, yPosition + (height - 8) / 2, 0xE0E0E0);
        }
    }

    /**
     * 扁平滑动条：连续拖动、步进取整，绿色填充 + 白色滑块，显示"标签 数值"。
     */
    private static class Slider extends GuiButton {

        private final float min;
        private final float max;
        private final float step;
        private final String label;
        private final boolean integerStep;
        private float value;
        private boolean dragging;

        Slider(int x, int y, int w, int h, String label, float min, float max, float initial, float step) {
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

        private void setValue(float v) {
            v = Math.max(min, Math.min(max, v));
            if (step > 0.0F) {
                v = Math.round(v / step) * step;
            }
            value = v;
            displayString = label + " " + (integerStep
                    ? String.format("%.0f", (double) v)
                    : String.format("%.1f", (double) v));
        }

        private void setValueFromMouse(int mouseX) {
            float frac = (float) (mouseX - xPosition - 4) / (float) (width - 8);
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
            // 轨道
            drawRect(xPosition, yPosition, xPosition + width, yPosition + height, 0xAA1A1A1A);
            drawRect(xPosition, yPosition, xPosition + width, yPosition + 1, 0xFF3E3E3E);
            drawRect(xPosition, yPosition + height - 1, xPosition + width, yPosition + height, 0xFF3E3E3E);
            drawRect(xPosition, yPosition, xPosition + 1, yPosition + height, 0xFF3E3E3E);
            drawRect(xPosition + width - 1, yPosition, xPosition + width, yPosition + height, 0xFF3E3E3E);
            // 填充与滑块
            float frac = (value - min) / (max - min);
            int fw = (int) ((width - 6) * frac);
            if (fw > 0) {
                drawRect(xPosition + 3, yPosition + 3, xPosition + 3 + fw, yPosition + height - 3, 0x809ADE91);
            }
            int tx = xPosition + 3 + fw - 1;
            drawRect(tx, yPosition + 2, tx + 2, yPosition + height - 2, 0xFFFFFFFF);
            drawCenteredString(mc.fontRenderer, displayString,
                    xPosition + width / 2, yPosition + (height - 8) / 2, 0xF0F0F0);
        }
    }
}
