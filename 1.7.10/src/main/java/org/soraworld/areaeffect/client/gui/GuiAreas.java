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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    private static final int SCROLL_W = 6;
    /** 三栏宽度比例：维度 : 区域 : 详情。 */
    private static final float DIM_FRAC = 0.14F;
    private static final float AREA_FRAC = 0.26F;

    // ===================== 配色方案（青蓝 Teal）=====================
    // 主题色
    /** 选中高亮（维度/区域行）。 */
    static final int COLOR_SELECTED = 0xCC0D9488;
    /** 行悬停底。 */
    static final int COLOR_HOVER_ROW = 0x55123B34;
    /** 强调文字（备注、L 值）。 */
    static final int COLOR_ACCENT = 0x2DD4BF;
    /** 滑动条进度填充（半透明纯绿）。 */
    static final int COLOR_SLIDER_FILL = 0x8000FF00;
    /** 面板 / 按钮 / 滑动条描边。 */
    static final int COLOR_BORDER = 0xFF4A6666;
    /** 按钮悬停底。 */
    static final int COLOR_BTN_HOVER = 0xBB1A3A36;
    // 中性色
    /** 标题 / 选中项文字。 */
    static final int COLOR_TEXT_HEAD = 0xFFFFFF;
    /** 正文 / 按钮文字。 */
    static final int COLOR_TEXT_BODY = 0xE0E0E0;
    /** 提示文字。 */
    static final int COLOR_TEXT_HINT = 0x808080;
    /** 禁用文字。 */
    static final int COLOR_TEXT_DISABLED = 0x707070;
    /** 按钮常态底。 */
    static final int COLOR_BTN_BG = 0xAA222222;
    /** 按钮禁用底。 */
    static final int COLOR_BTN_DISABLED = 0xAA111111;
    /** 滑动条轨道底。 */
    static final int COLOR_SLIDER_TRACK = 0xAA1A1A1A;
    /** 滑动条滑块。 */
    static final int COLOR_SLIDER_THUMB = 0xFFE8E8E8;
    /** 滑动条数值文字。 */
    static final int COLOR_SLIDER_TEXT = 0xF0F0F0;
    /** 滚动条轨道。 */
    static final int COLOR_SCROLL_TRACK = 0x33000000;
    /** 滚动条滑块。 */
    static final int COLOR_SCROLL_THUMB = 0xFFCCCCCC;

    private static final int BTN_TP = 0;
    private static final int BTN_DELETE = 1;
    private static final int BTN_SAVE = 2;
    private static final int BTN_SEL = 3;

    private final ClientProxy proxy;

    /** 左栏：维度列表（所有存在区域的维度）。 */
    private List<Integer> dims = new ArrayList<>();
    private int dimIdx = -1;
    private int dimScroll = 0;

    /** 中栏：当前维度的区域列表。 */
    private List<Area> areas = new ArrayList<>();
    private int areaScroll = 0;
    private Area selected = null;

    /** 详情：编辑对象与预览状态。 */
    private Slider lightSlider;
    private Slider durationSlider;
    private GuiTextField remarkField;
    private float previewL;
    private float previewD;
    private float originalL;
    private float originalD;
    private String originalRemark = "";
    private boolean saved = false;

    // 三栏几何
    private int dimX1, dimX2, areaX1, areaX2, detX1, detX2;
    private int top, bottom;

    // 滚动条拖拽状态：非 null 表示正在拖该栏滑块（值为拖拽起始 Y 与起始 scroll 差）
    private Integer dragDim = null;
    private Integer dragArea = null;
    private int dragStartY = 0;
    private int dragStartScroll = 0;

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
        dimScroll = 0;
        refreshAreas();
    }

    private void refreshAreas() {
        if (dimIdx >= 0 && dimIdx < dims.size()) {
            areas = proxy.getAreasLocal(dims.get(dimIdx));
        } else {
            areas = new ArrayList<>();
        }
        areaScroll = 0;
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

    /** 清空选中并还原未保存的预览，同时隐藏详情控件。 */
    private void clearSelection() {
        restoreIfUnsaved();
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

        buttonList.clear();
        // 详情栏内底部操作按钮（位于面板底边内侧）
        int n = 4;
        int total = BTN_W * n + GAP * (n - 1);
        int bx = (detX1 + detX2 - total) / 2;
        int by = bottom - BTN_H - 4;
        buttonList.add(new FlatBtn(BTN_TP, bx, by, BTN_W, BTN_H, translate("gui.areaeffect.edit.tp")));
        buttonList.add(new FlatBtn(BTN_DELETE, bx + BTN_W + GAP, by, BTN_W, BTN_H, translate("gui.areaeffect.edit.del")));
        buttonList.add(new FlatBtn(BTN_SAVE, bx + (BTN_W + GAP) * 2, by, BTN_W, BTN_H, translate("gui.areaeffect.edit.save")));
        buttonList.add(new FlatBtn(BTN_SEL, bx + (BTN_W + GAP) * 3, by, BTN_W, BTN_H, ""));
        // 滑动条 + 备注输入框（与 #id 同行）
        int sw = detX2 - detX1 - 20;
        lightSlider = new Slider(detX1 + 10, top + 44, sw, 18,
                translate("gui.areaeffect.edit.lightness"), 0.0F, 100.0F, 90.0F, 1.0F);
        durationSlider = new Slider(detX1 + 10, top + 70, sw, 18,
                translate("gui.areaeffect.edit.duration"), 0.0F, 60.0F, 1.0F, 0.1F);
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
            lightSlider.setValueRaw(selected.getLightness());
            durationSlider.setValueRaw(selected.getDuration());
            remarkField.setText(selected.getRemark());
            previewL = lightSlider.getValue();
            previewD = durationSlider.getValue();
            originalL = selected.getLightness();
            originalD = selected.getDuration();
            originalRemark = selected.getRemark();
            saved = false;
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
            int y = top + 4 + i * ROW_H - dimScroll;
            if (y < top || y + ROW_H > bottom) {
                continue;
            }
            boolean sel = i == dimIdx;
            boolean hov = !sel && mouseX >= dimX1 && mouseX < dimX2 && mouseY >= y && mouseY < y + ROW_H;
            if (sel) {
                drawRect(dimX1 + 1, y, dimX2 - 1, y + ROW_H, COLOR_SELECTED);
            } else if (hov) {
                drawRect(dimX1 + 1, y, dimX2 - 1, y + ROW_H, COLOR_HOVER_ROW);
            }
            String label = "DIM " + dims.get(i);
            drawCenteredString(fontRendererObj, label, (dimX1 + dimX2) / 2, y + 5, sel ? COLOR_TEXT_HEAD : COLOR_TEXT_BODY);
        }

        // 中栏：区域项
        for (int i = 0; i < areas.size(); i++) {
            int y = top + 4 + i * ROW_H - areaScroll;
            if (y < top || y + ROW_H > bottom) {
                continue;
            }
            Area area = areas.get(i);
            boolean sel = selected != null && area.id == selected.id;
            boolean hov = !sel && mouseX >= areaX1 && mouseX < areaX2 && mouseY >= y && mouseY < y + ROW_H;
            if (sel) {
                drawRect(areaX1 + 1, y, areaX2 - 1, y + ROW_H, COLOR_SELECTED);
            } else if (hov) {
                drawRect(areaX1 + 1, y, areaX2 - 1, y + ROW_H, COLOR_HOVER_ROW);
            }
            fontRendererObj.drawStringWithShadow("#" + area.id, areaX1 + 6, y + 5, sel ? COLOR_TEXT_HEAD : COLOR_TEXT_BODY);
            // 备注：ID 后显示，超出可用宽度截断为省略号（右侧与滚动条留隙）
            int rightPad = SCROLL_W + 10;
            String right = "L" + fmt(area.getLightness());
            int rightW = fontRendererObj.getStringWidth(right) + rightPad;
            int idW = fontRendererObj.getStringWidth("#" + area.id);
            int avail = (areaX2 - areaX1) - idW - rightW - 12;
            String remark = area.getRemark();
            if (!remark.isEmpty() && avail > 8) {
                String shown = fontRendererObj.trimStringToWidth(remark, avail);
                if (!shown.equals(remark)) {
                    shown = fontRendererObj.trimStringToWidth(remark, avail - 5) + "..";
                }
                fontRendererObj.drawStringWithShadow(shown, areaX1 + 6 + idW + 4, y + 5, COLOR_ACCENT);
            }
            fontRendererObj.drawStringWithShadow(right, areaX2 - fontRendererObj.getStringWidth(right) - rightPad, y + 5, COLOR_ACCENT);
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
            // 备注实时本地写回（列表行即时更新）
            String text = remarkField.getText();
            if (!text.equals(selected.getRemark())) {
                proxy.previewAreaRemark(dims.get(dimIdx), selected.id, text);
            }
            // 坐标详情：最后一行（滑动条之后），按形状显示
            drawCenteredString(fontRendererObj, selected.shape().describe(),
                    (detX1 + detX2) / 2, top + 96, COLOR_TEXT_BODY);
        } else {
            String hint = translate("gui.areaeffect.list.noselect");
            drawCenteredString(fontRendererObj, hint, (detX1 + detX2) / 2, (top + bottom) / 2 - 4, COLOR_TEXT_HINT);
        }

        // 列滚动条
        drawScrollBar(dimX2 - SCROLL_W - 1, dims, dimScroll);
        drawScrollBar(areaX2 - SCROLL_W - 1, areas, areaScroll);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    /** 栏面板：框内透明，仅深灰框线。 */
    private void drawPanel(int x1, int x2) {
        drawRect(x1, top, x2, top + 1, COLOR_BORDER);
        drawRect(x1, bottom - 1, x2, bottom, COLOR_BORDER);
        drawRect(x1, top, x1 + 1, bottom, COLOR_BORDER);
        drawRect(x2 - 1, top, x2, bottom, COLOR_BORDER);
    }

    /** 列表可视高度（滚动计算基准）。 */
    private int listVisible() {
        return bottom - top - 8;
    }

    /** 滚动条几何。 */
    private int[] scrollBarGeom(int x, List<?> list) {
        int visible = listVisible();
        int content = list.size() * ROW_H;
        int maxScroll = Math.max(0, content - visible);
        if (maxScroll <= 0) {
            return null;
        }
        int barH = Math.max(14, visible * visible / content);
        int barY = top + 4 + (visible - barH) * listScroll(list) / maxScroll;
        return new int[]{x, barY, x + SCROLL_W, barY + barH};
    }

    private int listScroll(List<?> list) {
        // 泛型区分两栏；list 引用对比即可
        if (list == (List<?>) dims) {
            return dimScroll;
        }
        return areaScroll;
    }

    private void setListScroll(List<?> list, int v) {
        if (list == (List<?>) dims) {
            dimScroll = v;
        } else {
            areaScroll = v;
        }
    }

    private void drawScrollBar(int x, List<?> list, int scroll) {
        int[] g = scrollBarGeom(x, list);
        if (g == null) {
            return;
        }
        // 轨道 + 滑块
        drawRect(x, top + 4, x + SCROLL_W, bottom - 4, COLOR_SCROLL_TRACK);
        drawRect(g[0], g[1], g[2], g[3], COLOR_SCROLL_THUMB);
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
            clearSelection();
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
            if (tryClickScrollBar(dimX2 - SCROLL_W - 1, dims, mouseX, mouseY)) {
                return;
            }
            if (tryClickScrollBar(areaX2 - SCROLL_W - 1, areas, mouseX, mouseY)) {
                return;
            }
            // 左栏选中维度
            if (mouseX >= dimX1 && mouseX < dimX2 && mouseY >= top && mouseY < bottom) {
                int idx = (mouseY - top - 4 + dimScroll) / ROW_H;
                if (idx >= 0 && idx < dims.size() && idx != dimIdx) {
                    clearSelection();
                    dimIdx = idx;
                    refreshAreas();
                }
                return;
            }
            // 中栏选中区域
            if (mouseX >= areaX1 && mouseX < areaX2 && mouseY >= top && mouseY < bottom) {
                int idx = (mouseY - top - 4 + areaScroll) / ROW_H;
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

    /** 点击滚动条：滑块上则开始拖拽，轨道上则跳到点击位置。返回是否命中。 */
    private boolean tryClickScrollBar(int x, List<?> list, int mouseX, int mouseY) {
        int[] g = scrollBarGeom(x, list);
        if (g == null || mouseX < g[0] || mouseX >= g[2] + 1 || mouseY < top + 4 || mouseY >= bottom - 4) {
            return false;
        }
        if (mouseY >= g[1] && mouseY < g[3]) {
            // 命中滑块：记录拖拽起点
            if (list == (List<?>) dims) {
                dragDim = 1;
            } else {
                dragArea = 1;
            }
            dragStartY = mouseY;
            dragStartScroll = listScroll(list);
        } else {
            // 命中轨道：按点击位置跳转
            int visible = listVisible();
            int content = list.size() * ROW_H;
            int maxScroll = Math.max(0, content - visible);
            int barH = g[3] - g[1];
            int frac = (mouseY - top - 4 - barH / 2) * maxScroll / Math.max(1, visible - barH);
            setListScroll(list, clampScroll(frac, list));
            // 跳转后转为拖拽状态
            if (list == (List<?>) dims) {
                dragDim = 1;
            } else {
                dragArea = 1;
            }
            dragStartY = mouseY;
            dragStartScroll = listScroll(list);
        }
        return true;
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        if (clickedMouseButton != 0) {
            return;
        }
        List<?> list = null;
        if (dragDim != null) {
            list = dims;
        } else if (dragArea != null) {
            list = areas;
        }
        if (list == null) {
            return;
        }
        int visible = listVisible();
        int content = list.size() * ROW_H;
        int maxScroll = Math.max(0, content - visible);
        int barH = Math.max(14, visible * visible / content);
        // 滑块可用行程 = 可视高 - 滑块高；映射到滚动量
        int dy = mouseY - dragStartY;
        int scroll = dragStartScroll + dy * maxScroll / Math.max(1, visible - barH);
        setListScroll(list, clampScroll(scroll, list));
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int state) {
        super.mouseMovedOrUp(mouseX, mouseY, state);
        if (state == 0) {
            dragDim = null;
            dragArea = null;
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int dwheel = Mouse.getDWheel();
        if (dwheel == 0) {
            return;
        }
        int step = dwheel > 0 ? ROW_H : -ROW_H;
        // 鼠标所在栏滚动
        int x = getMouseX();
        if (x >= dimX1 && x < dimX2) {
            dimScroll = clampScroll(dimScroll - step, dims);
        } else if (x >= areaX1 && x < areaX2) {
            areaScroll = clampScroll(areaScroll - step, areas);
        }
    }

    private int clampScroll(int v, List<?> list) {
        int visible = bottom - top - 8;
        return Math.max(0, Math.min(Math.max(0, list.size() * ROW_H - visible), v));
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
            proxy.previewAreaProps(dim, selected.id, l, d);
            proxy.previewAreaRemark(dim, selected.id, remark);
            originalL = l;
            originalD = d;
            originalRemark = remark;
            saved = true;
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

    /** 未保存时把本地副本还原为选中时的参数。 */
    private void restoreIfUnsaved() {
        if (selected != null && !saved && dimIdx >= 0 && dimIdx < dims.size()) {
            proxy.previewAreaProps(dims.get(dimIdx), selected.id, originalL, originalD);
            proxy.previewAreaRemark(dims.get(dimIdx), selected.id, originalRemark);
        }
    }

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

    /**
     * 扁平极简按钮：半透明深底 + 一像素描边 + 居中文字，悬停微亮。
     */
    static class FlatBtn extends GuiButton {

        FlatBtn(int id, int x, int y, int w, int h, String label) {
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
            drawRect(xPosition, yPosition, xPosition + width, yPosition + height, bg);
            drawRect(xPosition, yPosition, xPosition + width, yPosition + 1, COLOR_BORDER);
            drawRect(xPosition, yPosition + height - 1, xPosition + width, yPosition + height, COLOR_BORDER);
            drawRect(xPosition, yPosition, xPosition + 1, yPosition + height, COLOR_BORDER);
            drawRect(xPosition + width - 1, yPosition, xPosition + width, yPosition + height, COLOR_BORDER);
            int color = enabled ? COLOR_TEXT_BODY : COLOR_TEXT_DISABLED;
            drawCenteredString(mc.fontRenderer, displayString,
                    xPosition + width / 2, yPosition + (height - 8) / 2, color);
        }
    }

    /**
     * 扁平滑动条：拖动步进取整（1.7.10 拖动由 drawButton 每帧驱动 mouseDragged），
     * 天蓝填充 + 白色滑块，显示"标签 数值"。
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
            // 1.7.10 拖动由绘制驱动（GuiButton.drawButton 每帧调 mouseDragged），这里必须自己触发
            this.mouseDragged(mc, mouseX, mouseY);
            // 轨道
            drawRect(xPosition, yPosition, xPosition + width, yPosition + height, COLOR_SLIDER_TRACK);
            drawRect(xPosition, yPosition, xPosition + width, yPosition + 1, COLOR_BORDER);
            drawRect(xPosition, yPosition + height - 1, xPosition + width, yPosition + height, COLOR_BORDER);
            drawRect(xPosition, yPosition, xPosition + 1, yPosition + height, COLOR_BORDER);
            drawRect(xPosition + width - 1, yPosition, xPosition + width, yPosition + height, COLOR_BORDER);
            // 填充与滑块（8px 宽，便于拖动）
            float frac = (value - min) / (max - min);
            int thumbW = 8;
            int track = width - 8 - thumbW;
            int tx = xPosition + 4 + (int) (track * frac);
            if (tx > xPosition + 4) {
                drawRect(xPosition + 3, yPosition + 3, tx + thumbW / 2, yPosition + height - 3, COLOR_SLIDER_FILL);
            }
            drawRect(tx, yPosition + 2, tx + thumbW, yPosition + height - 2, COLOR_SLIDER_THUMB);
            drawCenteredString(mc.fontRenderer, displayString,
                    xPosition + width / 2, yPosition + (height - 8) / 2, COLOR_SLIDER_TEXT);
        }
    }
}
