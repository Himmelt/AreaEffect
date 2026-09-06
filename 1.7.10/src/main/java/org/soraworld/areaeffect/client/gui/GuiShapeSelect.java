package org.soraworld.areaeffect.client.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.StatCollector;
import org.soraworld.areaeffect.client.ClientProxy;
import org.soraworld.areaeffect.common.shape.Selection;
import org.soraworld.areaeffect.common.shape.ShapeTypes;

/**
 * 选区形状选择菜单：Shift+右键空气（持工具）打开，点击形状后发送并关闭。
 */
public class GuiShapeSelect extends GuiScreen {

    private static final int BTN_W = 200;
    private static final int BTN_H = 24;
    private static final int GAP = 6;

    private final ClientProxy proxy;

    public GuiShapeSelect(ClientProxy proxy) {
        this.proxy = proxy;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int count = ShapeTypes.ALL.length;
        int totalH = count * BTN_H + (count - 1) * GAP;
        int x = width / 2 - BTN_W / 2;
        int y = height / 2 - totalH / 2 - 20;
        for (int i = 0; i < count; i++) {
            String type = ShapeTypes.ALL[i];
            GuiButton button = new GuiAreas.FlatBtn(i, x, y + i * (BTN_H + GAP), BTN_W, BTN_H, translate("gui.areaeffect.shape." + type));
            button.enabled = true;
            buttonList.add(button);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id >= 0 && button.id < ShapeTypes.ALL.length) {
            proxy.sendSelectShape(ShapeTypes.ALL[button.id]);
            mc.displayGuiScreen(null);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        Selection sel = proxy.getLocalSelection();
        String title = translate("gui.areaeffect.shape.title");
        if (sel != null) {
            title += " · " + translate("gui.areaeffect.shape." + sel.shapeType);
        }
        drawCenteredString(fontRendererObj, title, width / 2, height / 2 - 70, GuiAreas.COLOR_TEXT_BODY);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private static String translate(String key) {
        return StatCollector.translateToLocal(key);
    }
}
