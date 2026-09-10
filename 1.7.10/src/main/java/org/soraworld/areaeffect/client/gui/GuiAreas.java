package org.soraworld.areaeffect.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.soraworld.areaeffect.client.ClientProxy;
import org.soraworld.areaeffect.common.effect.LightnessEffect;
import org.soraworld.areaeffect.common.network.Area;
import org.soraworld.areaeffect.common.shape.AreaShape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_ACCENT;
import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_BORDER;
import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_HOVER_ROW;
import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_SCROLL_THUMB;
import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_SCROLL_TRACK;
import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_SELECTED;
import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_TEXT_BODY;
import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_TEXT_HEAD;
import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_TEXT_HINT;
import static org.soraworld.areaeffect.client.gui.GuiTheme.argb;

/**
 * 区域管理主界面：三栏布局（维度列表 / 区域列表 / 详情编辑），
 * 单页无跳转——点击区域即在右栏展开编辑。透明背景，扁平极简自绘，
 * 不暂停游戏。
 *
 * <p>详情栏交互：滑动条实时预览（只改客户端本地副本，切换/关闭时未保存则还原）；
 * 传送不切页；保存走服务端流程后刷新；删除本地移除并清空选中。
 */
public class GuiAreas extends GuiScreen {

    private static final int ROW_H = 18;
    private static final int HEADER_H = 26;
    private static final int FOOTER_H = 30;
    private static final int GAP = 1;
    private static final int BTN_W = 60;
    private static final int BTN_H = 18;
    /** 三栏宽度比例：维度 : 区域 : 详情。 */
    private static final float DIM_FRAC = 0.14F;
    private static final float AREA_FRAC = 0.26F;

    private static final int BTN_TP = 0;
    private static final int BTN_DELETE = 1;
    private static final int BTN_SAVE = 2;
    private static final int BTN_SEL = 3;

    private final ClientProxy proxy;

    /** 左栏：维度列表（所有存在区域的维度）。 */
    private List<Integer> dims = new ArrayList<>();
    private int dimIdx = -1;
    /** 左栏滚动状态（滚动量与拖拽都由它自己管）。 */
    private final ScrollColumn dimCol = new ScrollColumn(ROW_H);

    /** 中栏：当前维度的区域列表。 */
    private List<Area> areas = new ArrayList<>();
    private final ScrollColumn areaCol = new ScrollColumn(ROW_H);
    private Area selected = null;

    /** 详情：编辑对象与预览状态。 */
    private FlatSlider lightSlider;
    private FlatSlider durationSlider;
    private GuiTextField remarkField;
    private float previewL;
    private float previewD;

    // 三栏几何
    private int dimX1, dimX2, areaX1, areaX2, detX1, detX2;
    private int top, bottom;

    public GuiAreas(ClientProxy proxy) {
        this.proxy = proxy;
        Minecraft mc = Minecraft.getMinecraft();
        int preferDim = mc.thePlayer != null ? mc.thePlayer.dimension : -1;
        refreshDims(preferDim);
        // 打开时若玩家正站在某区域内，初始即选中该区域
        if (mc.thePlayer != null) {
            Area standing = proxy.findAreaAt(mc.thePlayer);
            if (standing != null) {
                for (Area area : areas) {
                    if (area.id == standing.id) {
                        selected = area;
                        break;
                    }
                }
            }
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    /**
     * 界面关闭的统一清理点。预览覆盖只存在于客户端覆盖表（不入共享 Area），
     * 必须在<b>任何</b>关闭路径上移除：否则"拖过滑条但没点保存"的值会继续参与渲染，
     * 与存档值不一致。Esc 只是其中一条路径（死亡换屏、被其它界面抢占同样会触发这里）。
     */
    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        clearSelection();
    }

    /** 供外部（列表请求回复）触发刷新：保留当前选中维度。 */
    public void refreshFromProxy() {
        int prefer = dimIdx >= 0 && dimIdx < dims.size() ? dims.get(dimIdx) : -1;
        refreshDims(prefer);
        syncDetailWidgets();
    }

    /** 重新读取维度与区域（选中项尽力保留）。 */
    private void refreshDims(int preferDim) {
        dims = proxy.getDimsLocal();
        dimIdx = -1;
        for (int i = 0; i < dims.size(); i++) {
            if (dims.get(i) == preferDim) {
                dimIdx = i;
                break;
            }
        }
        if (dimIdx < 0 && !dims.isEmpty()) {
            dimIdx = 0;
        }
        dimCol.reset();
        refreshAreas();
    }

    private void refreshAreas() {
        if (dimIdx >= 0 && dimIdx < dims.size()) {
            areas = proxy.getAreasLocal(dims.get(dimIdx));
        } else {
            areas = new ArrayList<>();
        }
        areaCol.reset();
        // 选中项若已不存在则清空
        if (selected != null) {
            boolean found = false;
            for (Area area : areas) {
                if (area.id == selected.id) {
                    selected = area;
                    found = true;
                    break;
                }
            }
            if (!found) {
                clearSelection();
            }
        }
    }

    /** 清空选中并清除该区域的预览覆盖（预览不入共享，剔除覆盖即还原原值），隐藏详情控件。 */
    private void clearSelection() {
        if (selected != null && dimIdx >= 0 && dimIdx < dims.size()) {
            proxy.clearPreview(dims.get(dimIdx), selected.id);
        }
        selected = null;
        syncDetailWidgets();
    }

    @Override
    public void initGui() {
        super.initGui();
        // 比例布局：屏幕左右留 4% 边距，三栏按 14% : 26% : 60% 分配（GAP=1 紧邻）
        int left = Math.round(width * 0.04F);
        int right = width - left;
        int totalW = right - left;
        int dimW = Math.round(totalW * DIM_FRAC);
        int areaW = Math.round(totalW * AREA_FRAC);
        dimX1 = left;
        dimX2 = dimX1 + dimW;
        areaX1 = dimX2 + GAP;
        areaX2 = areaX1 + areaW;
        detX1 = areaX2 + GAP;
        detX2 = right;
        top = HEADER_H;
        bottom = height - FOOTER_H;

        // 两列滚动条：贴着各自面板的右侧内边界，纵向范围与列一致
        dimCol.x = dimX2 - ScrollColumn.BAR_W - 1;
        dimCol.top = top;
        dimCol.bottom = bottom;
        areaCol.x = areaX2 - ScrollColumn.BAR_W - 1;
        areaCol.top = top;
        areaCol.bottom = bottom;

        buttonList.clear();
        // 详情栏内底部操作按钮（位于面板底边内侧）
        int n = 4;
        int total = BTN_W * n + GAP * (n - 1);
        int bx = (detX1 + detX2 - total) / 2;
        int by = bottom - BTN_H - 4;
        buttonList.add(new FlatButton(BTN_TP, bx, by, BTN_W, BTN_H, translate("gui.areaeffect.edit.tp")));
        buttonList.add(new FlatButton(BTN_DELETE, bx + BTN_W + GAP, by, BTN_W, BTN_H, translate("gui.areaeffect.edit.del")));
        buttonList.add(new FlatButton(BTN_SAVE, bx + (BTN_W + GAP) * 2, by, BTN_W, BTN_H, translate("gui.areaeffect.edit.save")));
        buttonList.add(new FlatButton(BTN_SEL, bx + (BTN_W + GAP) * 3, by, BTN_W, BTN_H, ""));
        // 滑动条 + 备注输入框（与 #id 同行）
        int sw = detX2 - detX1 - 20;
        lightSlider = new FlatSlider(detX1 + 10, top + 44, sw, 18,
                translate("gui.areaeffect.edit.lightness"), 0.0F, 100.0F, 90.0F, 1.0F);
        // 下限取 0.1（与渲染端 Math.max(0.05, …) 的实际生效下限一致）：
        // LightnessEffect 对 <=0 会静默回落为 1 秒，滑条不应允许拖出这种无效值
        durationSlider = new FlatSlider(detX1 + 10, top + 70, sw, 18,
                translate("gui.areaeffect.edit.duration"), 0.1F, 60.0F, 1.0F, 0.1F);
        remarkField = new GuiTextField(fontRendererObj, detX1 + 34, top + 6, sw - 24, 16);
        remarkField.setMaxStringLength(Area.REMARK_MAX);
        buttonList.add(lightSlider);
        buttonList.add(durationSlider);
        syncDetailWidgets();
    }

    /** 选中项变化后同步滑动条/按钮文案与预览基线。 */
    private void syncDetailWidgets() {
        boolean has = selected != null;
        for (Object b : buttonList) {
            GuiButton btn = (GuiButton) b;
            if (btn.id >= BTN_TP && btn.id <= BTN_SEL) {
                btn.enabled = has;
            }
        }
        // 防御：initGui 若中途异常被吞，控件可能为 null；此时清空选中走空界面分支，避免 NPE 连锁
        if (lightSlider == null || durationSlider == null || remarkField == null) {
            selected = null;
            return;
        }
        lightSlider.visible = has;
        durationSlider.visible = has;
        remarkField.setVisible(has);
        if (has) {
            // 起始基线读共享原始值（预览不污染共享，此处恒为存档值）
            lightSlider.setValueRaw(selected.getLightness());
            durationSlider.setValueRaw(selected.getDuration());
            remarkField.setText(selected.getRemark());
            previewL = lightSlider.getValue();
            previewD = durationSlider.getValue();
            for (Object b : buttonList) {
                GuiButton btn = (GuiButton) b;
                if (btn.id == BTN_SEL) {
                    btn.displayString = selectionText();
                }
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // 透明背景：不绘制 drawDefaultBackground

        drawCenteredString(fontRendererObj, translate("gui.areaeffect.list.title2"), width / 2, 8, COLOR_TEXT_HEAD);

        drawPanel(dimX1, dimX2);
        drawPanel(areaX1, areaX2);
        drawPanel(detX1, detX2);

        // 左栏：维度项
        for (int i = 0; i < dims.size(); i++) {
            int y = dimCol.rowY(i);
            if (y < top || y + ROW_H > bottom) {
                continue;
            }
            boolean sel = i == dimIdx;
            boolean hov = !sel && mouseX >= dimX1 && mouseX < dimX2 && mouseY >= y && mouseY < y + ROW_H;
            if (sel) {
                drawRect(dimX1 + 1, y, dimX2 - 1, y + ROW_H, argb(COLOR_SELECTED));
            } else if (hov) {
                drawRect(dimX1 + 1, y, dimX2 - 1, y + ROW_H, argb(COLOR_HOVER_ROW));
            }
            String label = StatCollector.translateToLocalFormatted("gui.areaeffect.list.dim", dims.get(i));
            drawCenteredString(fontRendererObj, label, (dimX1 + dimX2) / 2, y + 5, sel ? COLOR_TEXT_HEAD : COLOR_TEXT_BODY);
        }

        // 中栏：区域项
        for (int i = 0; i < areas.size(); i++) {
            int y = areaCol.rowY(i);
            if (y < top || y + ROW_H > bottom) {
                continue;
            }
            Area area = areas.get(i);
            boolean sel = selected != null && area.id == selected.id;
            boolean hov = !sel && mouseX >= areaX1 && mouseX < areaX2 && mouseY >= y && mouseY < y + ROW_H;
            if (sel) {
                drawRect(areaX1 + 1, y, areaX2 - 1, y + ROW_H, argb(COLOR_SELECTED));
            } else if (hov) {
                drawRect(areaX1 + 1, y, areaX2 - 1, y + ROW_H, argb(COLOR_HOVER_ROW));
            }
            fontRendererObj.drawStringWithShadow("#" + area.id, areaX1 + 6, y + 5, sel ? COLOR_TEXT_HEAD : COLOR_TEXT_BODY);
            // 备注：ID 后显示，超出可用宽度截断为省略号（右侧与滚动条留隙）
            int rightPad = ScrollColumn.BAR_W + 10;
            String right = "L" + fmt(area.getLightness());
            int rightW = fontRendererObj.getStringWidth(right) + rightPad;
            int idW = fontRendererObj.getStringWidth("#" + area.id);
            int avail = (areaX2 - areaX1) - idW - rightW - 12;
            // 列表备注显示预览覆盖后的有效值（预览不入共享 Area）
            String remark = proxy.getEffectiveRemark(dims.get(dimIdx), area);
            if (!remark.isEmpty() && avail > 8) {
                String shown = fontRendererObj.trimStringToWidth(remark, avail);
                if (!shown.equals(remark)) {
                    shown = fontRendererObj.trimStringToWidth(remark, avail - 5) + "..";
                }
                fontRendererObj.drawStringWithShadow(shown, areaX1 + 6 + idW + 4, y + 5, COLOR_ACCENT);
            }
            fontRendererObj.drawStringWithShadow(right, areaX2 - fontRendererObj.getStringWidth(right) - rightPad, y + 5, COLOR_TEXT_BODY);
        }

        // 右栏：详情
        if (selected != null) {
            // #id 左对齐 + 备注输入框同行
            fontRendererObj.drawStringWithShadow("#" + selected.id, detX1 + 10, top + 10, COLOR_TEXT_HEAD);
            // 实时预览
            float l = lightSlider.getValue();
            float d = durationSlider.getValue();
            if (l != previewL || d != previewD) {
                proxy.previewAreaProps(dims.get(dimIdx), selected.id, l, d);
                previewL = l;
                previewD = d;
            }
            remarkField.drawTextBox();
            // 备注实时写覆盖表（列表行即时更新；预览不入共享 Area）
            String text = remarkField.getText();
            if (!text.equals(proxy.getEffectiveRemark(dims.get(dimIdx), selected))) {
                proxy.previewAreaRemark(dims.get(dimIdx), selected.id, text);
            }
            // 坐标详情：最后一行（滑动条之后），按形状显示。
            // 文案本地化：形状层只提供键与参数（StatCollector 负责组装）
            AreaShape shape = selected.shape();
            drawCenteredString(fontRendererObj,
                    StatCollector.translateToLocalFormatted(shape.describeKey(), shape.describeArgs()),
                    (detX1 + detX2) / 2, top + 96, COLOR_TEXT_BODY);
        } else {
            String hint = translate("gui.areaeffect.list.noselect");
            drawCenteredString(fontRendererObj, hint, (detX1 + detX2) / 2, (top + bottom) / 2 - 4, COLOR_TEXT_HINT);
        }

        // 列滚动条
        drawScrollBar(dimCol, dims.size());
        drawScrollBar(areaCol, areas.size());

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    /** 栏面板：框内透明，仅深灰框线。 */
    private void drawPanel(int x1, int x2) {
        drawRect(x1, top, x2, top + 1, argb(COLOR_BORDER));
        drawRect(x1, bottom - 1, x2, bottom, argb(COLOR_BORDER));
        drawRect(x1, top, x1 + 1, bottom, argb(COLOR_BORDER));
        drawRect(x2 - 1, top, x2, bottom, argb(COLOR_BORDER));
    }

    /** 绘制某一列的滚动条（内容不足一屏时 {@link ScrollColumn#bar(int)} 返回 null，不画）。 */
    private void drawScrollBar(ScrollColumn col, int count) {
        int[] bar = col.bar(count);
        if (bar == null) {
            return;
        }
        // 轨道 + 滑块
        drawRect(col.x, col.top + 4, col.x + ScrollColumn.BAR_W, col.bottom - 4, argb(COLOR_SCROLL_TRACK));
        drawRect(bar[0], bar[1], bar[2], bar[3], argb(COLOR_SCROLL_THUMB));
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (remarkField != null) {
            remarkField.updateCursorCounter();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            // 预览清理统一由 onGuiClosed 负责（displayGuiScreen(null) 会回调它）
            mc.displayGuiScreen(null);
            return;
        }
        // 备注输入框聚焦时优先接收按键（含退格/粘贴等），但 Esc 已在上面处理
        if (selected != null && remarkField.isFocused()) {
            if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                remarkField.setFocused(false);
                return;
            }
            remarkField.textboxKeyTyped(typedChar, keyCode);
            return;
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton == 0 && selected != null) {
            remarkField.mouseClicked(mouseX, mouseY, mouseButton);
        }
        if (mouseButton == 0) {
            // 滚动条：命中滑块开始拖拽；命中轨道按位置跳转
            if (dimCol.press(mouseX, mouseY, dims.size())) {
                return;
            }
            if (areaCol.press(mouseX, mouseY, areas.size())) {
                return;
            }
            // 左栏选中维度
            if (mouseX >= dimX1 && mouseX < dimX2 && mouseY >= top && mouseY < bottom) {
                int idx = (mouseY - top - 4 + dimCol.scroll()) / ROW_H;
                if (idx >= 0 && idx < dims.size() && idx != dimIdx) {
                    clearSelection();
                    dimIdx = idx;
                    refreshAreas();
                }
                return;
            }
            // 中栏选中区域
            if (mouseX >= areaX1 && mouseX < areaX2 && mouseY >= top && mouseY < bottom) {
                int idx = (mouseY - top - 4 + areaCol.scroll()) / ROW_H;
                if (idx >= 0 && idx < areas.size()) {
                    Area area = areas.get(idx);
                    if (selected == null || area.id != selected.id) {
                        clearSelection();
                        selected = area;
                        syncDetailWidgets();
                    }
                }
            }
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        if (clickedMouseButton != 0) {
            return;
        }
        // 未处于拖拽的列会自行忽略
        dimCol.drag(mouseY, dims.size());
        areaCol.drag(mouseY, areas.size());
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int state) {
        super.mouseMovedOrUp(mouseX, mouseY, state);
        if (state == 0) {
            dimCol.release();
            areaCol.release();
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int dwheel = Mouse.getDWheel();
        if (dwheel == 0) {
            return;
        }
        // 滚轮向上为向后翻，故行偏移取负
        int rows = dwheel > 0 ? -1 : 1;
        int x = getMouseX();
        if (x >= dimX1 && x < dimX2) {
            dimCol.wheel(rows, dims.size());
        } else if (x >= areaX1 && x < areaX2) {
            areaCol.wheel(rows, areas.size());
        }
    }

    private int getMouseX() {
        return Mouse.getEventX() * width / mc.displayWidth;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (selected == null || dimIdx < 0) {
            return;
        }
        int dim = dims.get(dimIdx);
        if (button.id == BTN_SAVE) {
            float l = lightSlider.getValue();
            float d = durationSlider.getValue();
            String remark = remarkField.getText().trim();
            proxy.sendSetProps(dim, selected.id, remark, Collections.singletonList(new LightnessEffect(l, d)));
            // 保留预览覆盖避免渲染跳变，服务端广播返回后共享即为新值；切换/关闭时统一清除
            proxy.previewAreaProps(dim, selected.id, l, d);
            proxy.previewAreaRemark(dim, selected.id, remark);
        } else if (button.id == BTN_TP) {
            proxy.sendTpRequest(selected.id);
        } else if (button.id == BTN_DELETE) {
            // 只发删除请求并立即清空选中；本地列表由服务端广播统一刷新（避免单机竞态误报）
            proxy.sendDeleteRequest(dim, selected.id);
            clearSelection();
        } else if (button.id == BTN_SEL) {
            proxy.toggleAreaVisible(dim, selected.id);
            button.displayString = selectionText();
        }
    }

    /** 区域线框按钮的当前文案（开/关状态）。 */
    private String selectionText() {
        String state = translate(proxy.isAreaVisible(dims.get(dimIdx), selected.id)
                ? "gui.areaeffect.selection.on"
                : "gui.areaeffect.selection.off");
        return translate("gui.areaeffect.edit.selection") + ": " + state;
    }

    private static String translate(String key) {
        return StatCollector.translateToLocal(key);
    }

    private static String fmt(float v) {
        return String.format("%.0f", (double) v);
    }

}
