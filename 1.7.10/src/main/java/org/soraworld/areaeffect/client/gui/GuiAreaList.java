package org.soraworld.areaeffect.client.gui;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Mouse;
import org.soraworld.areaeffect.common.network.Area;
import org.soraworld.areaeffect.client.ClientProxy;

import java.util.List;

/**
 * 区域列表界面：以可滚动列表展示当前维度的所有区域，点击进入详情页。
 */
public class GuiAreaList extends GuiScreen {

    private static final int ROW_H = 18;
    private static final int LIST_LEFT = 24;
    private static final int LIST_TOP = 32;
    private static final int LIST_BOTTOM_OFFSET = 24;

    private final ClientProxy proxy;
    private final int dim;
    private final List<Area> areas;
    private int scroll = 0;
    private int listWidth;
    private int listHeight;
    private int maxScroll;

    public GuiAreaList(ClientProxy proxy, int dim, List<Area> areas) {
        this.proxy = proxy;
        this.dim = dim;
        this.areas = areas;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void initGui() {
        super.initGui();
        listWidth = width - LIST_LEFT * 2;
        listHeight = height - LIST_TOP - LIST_BOTTOM_OFFSET;
        maxScroll = Math.max(0, areas.size() * ROW_H - listHeight);
        if (scroll > maxScroll) {
            scroll = maxScroll;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        String title = String.format(StatCollector.translateToLocal("gui.areaeffect.list.title"), dim);
        drawCenteredString(fontRendererObj, title, width / 2, 8, 0xFFFFFF);

        int left = LIST_LEFT;
        int right = left + listWidth;
        int top = LIST_TOP;
        int bottom = top + listHeight;
        Gui.drawRect(left - 4, top - 4, right + 4, bottom + 4, 0xAA222222);

        int first = scroll / ROW_H;
        int rowY = top - (scroll % ROW_H);
        for (int i = first; i < areas.size(); i++) {
            if (rowY + ROW_H > bottom) {
                break;
            }
            Area area = areas.get(i);
            if (mouseX >= left && mouseX <= right && mouseY >= rowY && mouseY < rowY + ROW_H) {
                Gui.drawRect(left, rowY, right, rowY + ROW_H, 0x55333333);
            }
            String line = "#" + area.id + "  " + area.x1 + "," + area.y1 + "," + area.z1
                    + " -> " + area.x2 + "," + area.y2 + "," + area.z2;
            fontRendererObj.drawStringWithShadow(line, left + 2, rowY + 2, 0xE0E0E0);
            String rightText = "L:" + fmt(area.getLightness()) + "  T:" + fmt(area.getDuration()) + "s";
            fontRendererObj.drawStringWithShadow(rightText, right - fontRendererObj.getStringWidth(rightText) - 2, rowY + 2, 0x9ADE91);
            rowY += ROW_H;
        }

        // 滚动条
        if (maxScroll > 0) {
            int barH = Math.max(16, listHeight * listHeight / (areas.size() * ROW_H));
            int barY = top + (listHeight - barH) * scroll / maxScroll;
            Gui.drawRect(right + 4, barY, right + 7, barY + barH, 0xFFFFFFFF);
        }

        fontRendererObj.drawStringWithShadow(StatCollector.translateToLocal("gui.areaeffect.list.footer"), left, bottom + 8, 0x808080);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton == 0) {
            int left = LIST_LEFT;
            int right = left + listWidth;
            int top = LIST_TOP;
            int bottom = top + listHeight;
            int rem = scroll % ROW_H;
            int localY = mouseY - (top - rem);
            if (mouseX >= left && mouseX <= right && localY >= 0 && mouseY < bottom) {
                int idx = scroll / ROW_H + localY / ROW_H;
                if (idx >= 0 && idx < areas.size()) {
                    mc.displayGuiScreen(new GuiAreaEdit(proxy, dim, areas.get(idx)));
                }
            }
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int dwheel = Mouse.getDWheel();
        if (dwheel != 0 && maxScroll > 0) {
            scroll = Math.max(0, Math.min(maxScroll, scroll - (dwheel > 0 ? ROW_H : -ROW_H)));
        }
    }

    private static String fmt(float v) {
        return String.format("%.1f", (double) v);
    }
}