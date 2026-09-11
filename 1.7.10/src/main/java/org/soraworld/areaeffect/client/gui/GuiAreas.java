package org.soraworld.areaeffect.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.soraworld.areaeffect.client.ClientProxy;
import org.soraworld.areaeffect.common.effect.AreaEffect;
import org.soraworld.areaeffect.common.effect.EffectTypes;
import org.soraworld.areaeffect.common.effect.LightnessEffect;
import org.soraworld.areaeffect.common.network.Area;
import org.soraworld.areaeffect.common.shape.AreaShape;

import java.util.ArrayList;
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
 * 区域管理主界面：<b>四栏</b>布局 —— 维度列表 / 区域列表 / 效果列表 / 效果详情。
 *
 * <p>层级关系：选维度 → 列区域；选区域 → 列该区域挂载的效果；选效果 → 右侧编辑其参数与权重；
 * 效果可增删（新增用 {@link EffectTypes#ALL} + {@link EffectTypes#newDefault}）。
 *
 * <p>编辑模型：选中区域时把共享 {@link Area} 的效果<b>复制</b>成一份可变的「工作列表」{@link #pendingEffects}，
 * 所有滑条改动只写这份副本；实时用预览覆盖表让渲染端看到编辑效果，不污染共享 Area。
 * 保存时把工作列表整组写回服务端；关闭界面统一清除预览覆盖（见 {@link #onGuiClosed}）。
 */
public class GuiAreas extends GuiScreen {

    private static final int ROW_H = 18;
    private static final int HEADER_H = 26;
    private static final int FOOTER_H = 30;
    private static final int GAP = 1;
    private static final int BTN_W = 54;
    private static final int BTN_H = 18;
    /** 效果栏顶部「添加/删除」按钮行的高度。 */
    private static final int EFFECT_TOOLBAR_H = 16;
    /** 四栏宽度比例：维度 : 区域 : 效果 : 详情。 */
    private static final float DIM_FRAC = 0.12F;
    private static final float AREA_FRAC = 0.20F;
    private static final float EFFECT_FRAC = 0.24F;

    private static final int BTN_TP = 0;
    private static final int BTN_DELETE = 1;
    private static final int BTN_SAVE = 2;
    private static final int BTN_SEL = 3;
    private static final int BTN_ADD_EFFECT = 4;
    private static final int BTN_DEL_EFFECT = 5;

    private final ClientProxy proxy;

    /** 左栏：维度列表。 */
    private List<Integer> dims = new ArrayList<>();
    private int dimIdx = -1;
    private final ScrollColumn dimCol = new ScrollColumn(ROW_H);

    /** 中左栏：当前维度的区域列表。 */
    private List<Area> areas = new ArrayList<>();
    private final ScrollColumn areaCol = new ScrollColumn(ROW_H);
    private Area selected = null;

    /** 中右栏：选中区域的效果工作列表（副本，不污染共享 Area）与选中下标。 */
    private final ScrollColumn effectCol = new ScrollColumn(ROW_H);
    private List<AreaEffect> pendingEffects = null;
    private int effectIdx = -1;

    /** 详情栏：区域级备注 + 效果参数编辑控件。 */
    private FlatSlider weightSlider;
    private FlatSlider lightSlider;
    private FlatSlider durationSlider;
    private GuiTextField remarkField;

    // 四栏几何
    private int dimX1, dimX2, areaX1, areaX2, effectX1, effectX2, detX1, detX2;
    private int effectTop;
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
        // 只重建工作列表，不触碰控件（initGui 尚未创建滑条/输入框）
        rebuildPendingEffects();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    /**
     * 界面关闭的统一清理点。预览覆盖只存在于预览覆盖表（不入共享 Area），
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

    /** 重建本维度区域列表；选中项若仍存在则重建其效果工作列表，否则清空选中。 */
    private void refreshAreas() {
        if (dimIdx >= 0 && dimIdx < dims.size()) {
            areas = proxy.getAreasLocal(dims.get(dimIdx));
        } else {
            areas = new ArrayList<>();
        }
        areaCol.reset();
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
                return;
            }
        }
        refreshEffects();
    }

    /** 把选中区域的效果<b>复制</b>成工作列表（副本），并默认选中第一个效果。 */
    private void refreshEffects() {
        rebuildPendingEffects();
        syncDetailWidgets();
    }

    /** 仅重建工作列表与选中下标，<b>不触碰控件</b>（initGui 前的构造路径用，控件尚未创建）。 */
    private void rebuildPendingEffects() {
        if (selected == null) {
            pendingEffects = null;
            effectIdx = -1;
            return;
        }
        pendingEffects = new ArrayList<>();
        for (AreaEffect effect : selected.getEffects()) {
            pendingEffects.add(effect.copy());
        }
        effectIdx = pendingEffects.isEmpty() ? -1 : 0;
        effectCol.reset();
    }

    /** 清空选中并清除该区域的预览覆盖，隐藏详情控件。 */
    private void clearSelection() {
        if (selected != null && dimIdx >= 0 && dimIdx < dims.size()) {
            proxy.clearPreview(dims.get(dimIdx), selected.id);
        }
        selected = null;
        pendingEffects = null;
        effectIdx = -1;
        syncDetailWidgets();
    }

    /** 该类型效果是否已存在于工作列表（用于「添加效果」去重）。 */
    private boolean containsType(List<AreaEffect> list, String typeId) {
        for (AreaEffect effect : list) {
            if (typeId.equals(effect.typeId())) {
                return true;
            }
        }
        return false;
    }

    /** 当前工作列表内是否已包含全部可添加的效果类型（决定「添加效果」是否可点）。 */
    private boolean hasAllEffectTypes() {
        if (pendingEffects == null) {
            return false;
        }
        for (String typeId : EffectTypes.ALL) {
            if (!containsType(pendingEffects, typeId)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void initGui() {
        super.initGui();
        // 比例布局：屏幕左右留 3% 边距，四栏按 12% : 20% : 24% : 44% 分配（GAP=1 紧邻）
        int left = Math.round(width * 0.03F);
        int right = width - left;
        int totalW = right - left;
        int dimW = Math.round(totalW * DIM_FRAC);
        int areaW = Math.round(totalW * AREA_FRAC);
        int effectW = Math.max(40, Math.round(totalW * EFFECT_FRAC));
        dimX1 = left;
        dimX2 = dimX1 + dimW;
        areaX1 = dimX2 + GAP;
        areaX2 = areaX1 + areaW;
        effectX1 = areaX2 + GAP;
        effectX2 = effectX1 + effectW;
        detX1 = effectX2 + GAP;
        detX2 = right;
        top = HEADER_H;
        bottom = height - FOOTER_H;
        effectTop = top + EFFECT_TOOLBAR_H + 4;

        // 三列滚动条：贴着各自面板的右侧内边界，纵向范围与列一致
        dimCol.x = dimX2 - ScrollColumn.BAR_W - 1;
        dimCol.top = top;
        dimCol.bottom = bottom;
        areaCol.x = areaX2 - ScrollColumn.BAR_W - 1;
        areaCol.top = top;
        areaCol.bottom = bottom;
        effectCol.x = effectX2 - ScrollColumn.BAR_W - 1;
        effectCol.top = effectTop;
        effectCol.bottom = bottom;

        buttonList.clear();
        // 效果栏顶部工具栏：添加/删除效果
        int toolW = (effectX2 - effectX1 - GAP) / 2;
        buttonList.add(new FlatButton(BTN_ADD_EFFECT, effectX1, top, toolW, EFFECT_TOOLBAR_H,
                translate("gui.areaeffect.edit.addEffect")));
        buttonList.add(new FlatButton(BTN_DEL_EFFECT, effectX1 + toolW + GAP, top, effectX2 - (effectX1 + toolW + GAP),
                EFFECT_TOOLBAR_H, translate("gui.areaeffect.edit.delEffect")));
        // 详情栏内底部操作按钮（位于面板底边内侧）
        int n = 4;
        int total = BTN_W * n + GAP * (n - 1);
        int bx = Math.max(detX1 + 4, (detX1 + detX2 - total) / 2);
        int by = bottom - BTN_H - 4;
        buttonList.add(new FlatButton(BTN_TP, bx, by, BTN_W, BTN_H, translate("gui.areaeffect.edit.tp")));
        buttonList.add(new FlatButton(BTN_DELETE, bx + BTN_W + GAP, by, BTN_W, BTN_H, translate("gui.areaeffect.edit.del")));
        buttonList.add(new FlatButton(BTN_SAVE, bx + (BTN_W + GAP) * 2, by, BTN_W, BTN_H, translate("gui.areaeffect.edit.save")));
        buttonList.add(new FlatButton(BTN_SEL, bx + (BTN_W + GAP) * 3, by, BTN_W, BTN_H, ""));
        // 效果参数编辑滑条 + 备注输入框
        int sw = detX2 - detX1 - 20;
        weightSlider = new FlatSlider(detX1 + 10, top + 44, sw, 18,
                translate("gui.areaeffect.edit.weight"), 0.0F, 100.0F, 0.0F, 1.0F);
        lightSlider = new FlatSlider(detX1 + 10, top + 66, sw, 18,
                translate("gui.areaeffect.edit.lightness"), 0.0F, 100.0F, 90.0F, 1.0F);
        durationSlider = new FlatSlider(detX1 + 10, top + 88, sw, 18,
                translate("gui.areaeffect.edit.duration"),
                LightnessEffect.MIN_DURATION, LightnessEffect.MAX_DURATION, 1.0F, 0.1F);
        remarkField = new GuiTextField(fontRendererObj, detX1 + 34, top + 6, sw - 24, 16);
        remarkField.setMaxStringLength(Area.REMARK_MAX);
        buttonList.add(weightSlider);
        buttonList.add(lightSlider);
        buttonList.add(durationSlider);
        syncDetailWidgets();
    }

    /** 选中项/选中效果变化后同步各按钮、滑条可见性与取值基线。 */
    private void syncDetailWidgets() {
        boolean has = selected != null;
        for (Object b : buttonList) {
            GuiButton btn = (GuiButton) b;
            if (btn.id >= BTN_TP && btn.id <= BTN_SEL) {
                btn.enabled = has;
            } else if (btn.id == BTN_ADD_EFFECT) {
                btn.enabled = has && !hasAllEffectTypes();
            } else if (btn.id == BTN_DEL_EFFECT) {
                btn.enabled = has && effectIdx >= 0;
            }
        }
        // 防御：initGui 若中途异常被吞，控件可能为 null；此时清空选中走空界面分支，避免 NPE 连锁
        if (weightSlider == null || lightSlider == null || durationSlider == null || remarkField == null) {
            selected = null;
            return;
        }
        boolean hasEffect = has && effectIdx >= 0 && pendingEffects != null && effectIdx < pendingEffects.size();
        weightSlider.visible = hasEffect;
        lightSlider.visible = hasEffect;
        durationSlider.visible = hasEffect;
        remarkField.setVisible(has);
        if (hasEffect) {
            AreaEffect effect = pendingEffects.get(effectIdx);
            weightSlider.setValueRaw(effect.getWeight());
            boolean isLight = effect instanceof LightnessEffect;
            lightSlider.visible = isLight;
            durationSlider.visible = isLight;
            if (isLight) {
                LightnessEffect light = (LightnessEffect) effect;
                lightSlider.setValueRaw(light.getLightness());
                durationSlider.setValueRaw(light.getDuration());
            }
            for (Object b : buttonList) {
                GuiButton btn = (GuiButton) b;
                if (btn.id == BTN_SEL) {
                    btn.displayString = selectionText();
                }
            }
        }
        if (has) {
            remarkField.setText(selected.getRemark());
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // 透明背景：不绘制 drawDefaultBackground

        drawCenteredString(fontRendererObj, translate("gui.areaeffect.list.title2"), width / 2, 8, COLOR_TEXT_HEAD);

        drawPanel(dimX1, dimX2);
        drawPanel(areaX1, areaX2);
        drawPanel(effectX1, effectX2);
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

        // 中左栏：区域项
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
            // 效果数量：ID 后显示，超出可用宽度截断为省略号（右侧与滚动条留隙）
            int rightPad = ScrollColumn.BAR_W + 10;
            String right = area.getEffects().size() + "fx";
            int rightW = fontRendererObj.getStringWidth(right) + rightPad;
            int idW = fontRendererObj.getStringWidth("#" + area.id);
            int avail = (areaX2 - areaX1) - idW - rightW - 12;
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

        // 中右栏：效果列表
        if (pendingEffects != null) {
            for (int i = 0; i < pendingEffects.size(); i++) {
                int y = effectCol.rowY(i);
                if (y < effectTop || y + ROW_H > bottom) {
                    continue;
                }
                AreaEffect effect = pendingEffects.get(i);
                boolean sel = i == effectIdx;
                boolean hov = !sel && mouseX >= effectX1 && mouseX < effectX2 && mouseY >= y && mouseY < y + ROW_H;
                if (sel) {
                    drawRect(effectX1 + 1, y, effectX2 - 1, y + ROW_H, argb(COLOR_SELECTED));
                } else if (hov) {
                    drawRect(effectX1 + 1, y, effectX2 - 1, y + ROW_H, argb(COLOR_HOVER_ROW));
                }
                String name = translate("gui.areaeffect.effect." + effect.typeId());
                fontRendererObj.drawStringWithShadow(name, effectX1 + 6, y + 5, sel ? COLOR_TEXT_HEAD : COLOR_TEXT_BODY);
                String w = "W" + fmt(effect.getWeight());
                fontRendererObj.drawStringWithShadow(w, effectX2 - fontRendererObj.getStringWidth(w) - (ScrollColumn.BAR_W + 10), y + 5, COLOR_ACCENT);
            }
            if (pendingEffects.isEmpty()) {
                String hint = translate("gui.areaeffect.effectlist.none");
                drawCenteredString(fontRendererObj, hint, (effectX1 + effectX2) / 2, (effectTop + bottom) / 2 - 4, COLOR_TEXT_HINT);
            }
        }

        // 右栏：详情
        boolean editing = selected != null && effectIdx >= 0 && pendingEffects != null && effectIdx < pendingEffects.size();
        if (selected != null) {
            // #id 左对齐 + 备注输入框同行
            fontRendererObj.drawStringWithShadow("#" + selected.id, detX1 + 10, top + 10, COLOR_TEXT_HEAD);
            remarkField.drawTextBox();
            // 备注实时写覆盖表（列表行即时更新；预览不入共享 Area）
            String text = remarkField.getText();
            if (!text.equals(proxy.getEffectiveRemark(dims.get(dimIdx), selected))) {
                proxy.previewAreaRemark(dims.get(dimIdx), selected.id, text);
            }
        }
        if (editing) {
            AreaEffect effect = pendingEffects.get(effectIdx);
            // 当前编辑对象标签
            String effLabel = StatCollector.translateToLocalFormatted("gui.areaeffect.edit.effectName",
                    translate("gui.areaeffect.effect." + effect.typeId()));
            fontRendererObj.drawStringWithShadow(effLabel, detX1 + 10, top + 30, COLOR_TEXT_HEAD);
            // 实时写入工作副本，发生变化时刷新预览（驱动渲染端即时反馈）
            boolean changed = false;
            float w = weightSlider.getValue();
            if (w != effect.getWeight()) {
                effect.setWeight(w);
                changed = true;
            }
            if (effect instanceof LightnessEffect) {
                LightnessEffect light = (LightnessEffect) effect;
                float lv = lightSlider.getValue();
                float dv = durationSlider.getValue();
                if (lv != light.getLightness()) {
                    light.setLightness(lv);
                    changed = true;
                }
                if (dv != light.getDuration()) {
                    light.setDuration(dv);
                    changed = true;
                }
            }
            if (changed) {
                proxy.previewAreaProps(dims.get(dimIdx), selected.id, effectiveLightness(), effectiveDuration());
            }
            // 坐标详情：末尾一行，按形状显示
            AreaShape shape = selected.shape();
            drawCenteredString(fontRendererObj,
                    StatCollector.translateToLocalFormatted(shape.describeKey(), shape.describeArgs()),
                    (detX1 + detX2) / 2, bottom - BTN_H - 4 - 14, COLOR_TEXT_BODY);
        } else if (selected != null) {
            String hint = translate("gui.areaeffect.list.noeffect");
            drawCenteredString(fontRendererObj, hint, (detX1 + detX2) / 2, (top + bottom) / 2 - 4, COLOR_TEXT_HINT);
        } else {
            String hint = translate("gui.areaeffect.list.noselect");
            drawCenteredString(fontRendererObj, hint, (detX1 + detX2) / 2, (top + bottom) / 2 - 4, COLOR_TEXT_HINT);
        }

        // 列滚动条
        drawScrollBar(dimCol, dims.size());
        drawScrollBar(areaCol, areas.size());
        drawScrollBar(effectCol, pendingEffects == null ? 0 : pendingEffects.size());

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    /** 工作列表当前生效的亮度（无亮度效果时为 100）。 */
    private float effectiveLightness() {
        for (AreaEffect effect : pendingEffects) {
            if (effect instanceof LightnessEffect) {
                return ((LightnessEffect) effect).getLightness();
            }
        }
        return 100.0F;
    }

    /** 工作列表当前生效的过渡时长（无亮度效果时为 1）。 */
    private float effectiveDuration() {
        for (AreaEffect effect : pendingEffects) {
            if (effect instanceof LightnessEffect) {
                return ((LightnessEffect) effect).getDuration();
            }
        }
        return 1.0F;
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
            mc.displayGuiScreen(null);
            return;
        }
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
        if (mouseButton != 0) {
            return;
        }
        // 滚动条：命中滑块开始拖拽；命中轨道按位置跳转
        if (dimCol.press(mouseX, mouseY, dims.size())) {
            return;
        }
        if (areaCol.press(mouseX, mouseY, areas.size())) {
            return;
        }
        if (effectCol.press(mouseX, mouseY, pendingEffects == null ? 0 : pendingEffects.size())) {
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
        // 中左栏选中区域
        if (mouseX >= areaX1 && mouseX < areaX2 && mouseY >= top && mouseY < bottom) {
            int idx = (mouseY - top - 4 + areaCol.scroll()) / ROW_H;
            if (idx >= 0 && idx < areas.size()) {
                Area area = areas.get(idx);
                if (selected == null || area.id != selected.id) {
                    clearSelection();
                    selected = area;
                    refreshEffects();
                }
            }
            return;
        }
        // 中右栏选中效果
        if (pendingEffects != null && mouseX >= effectX1 && mouseX < effectX2 && mouseY >= effectTop && mouseY < bottom) {
            int idx = (mouseY - effectTop - 4 + effectCol.scroll()) / ROW_H;
            if (idx >= 0 && idx < pendingEffects.size() && idx != effectIdx) {
                effectIdx = idx;
                syncDetailWidgets();
            }
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        if (clickedMouseButton != 0) {
            return;
        }
        dimCol.drag(mouseY, dims.size());
        areaCol.drag(mouseY, areas.size());
        effectCol.drag(mouseY, pendingEffects == null ? 0 : pendingEffects.size());
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int state) {
        super.mouseMovedOrUp(mouseX, mouseY, state);
        if (state == 0) {
            dimCol.release();
            areaCol.release();
            effectCol.release();
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int dwheel = Mouse.getDWheel();
        if (dwheel == 0) {
            return;
        }
        int rows = dwheel > 0 ? -1 : 1;
        int x = getMouseX();
        if (x >= dimX1 && x < dimX2) {
            dimCol.wheel(rows, dims.size());
        } else if (x >= areaX1 && x < areaX2) {
            areaCol.wheel(rows, areas.size());
        } else if (x >= effectX1 && x < effectX2) {
            effectCol.wheel(rows, pendingEffects == null ? 0 : pendingEffects.size());
        }
    }

    private int getMouseX() {
        return Mouse.getEventX() * width / mc.displayWidth;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        int dim = dimIdx >= 0 && dimIdx < dims.size() ? dims.get(dimIdx) : -1;
        if (button.id == BTN_ADD_EFFECT) {
            if (selected == null || pendingEffects == null) {
                return;
            }
            // 依次遍历可添加类型，取第一个还没挂到该区域的类型。
            // 目前只有亮度一种，因此有亮度即不可再点（按钮已禁用，这里是防御）。
            for (String typeId : EffectTypes.ALL) {
                if (!containsType(pendingEffects, typeId)) {
                    AreaEffect added = EffectTypes.newDefault(typeId);
                    if (added != null) {
                        pendingEffects.add(added);
                        if (effectIdx < 0) {
                            effectIdx = 0;
                        }
                        // 新增后立即预览一次，让渲染端立刻反映新效果
                        proxy.previewAreaProps(dim, selected.id, effectiveLightness(), effectiveDuration());
                        syncDetailWidgets();
                    }
                    return;
                }
            }
            return;
        }
        if (button.id == BTN_DEL_EFFECT) {
            if (pendingEffects != null && effectIdx >= 0 && effectIdx < pendingEffects.size()) {
                pendingEffects.remove(effectIdx);
                if (pendingEffects.isEmpty()) {
                    effectIdx = -1;
                } else if (effectIdx >= pendingEffects.size()) {
                    effectIdx = pendingEffects.size() - 1;
                }
                proxy.previewAreaProps(dim, selected.id, effectiveLightness(), effectiveDuration());
                syncDetailWidgets();
            }
            return;
        }
        if (selected == null || dim < 0) {
            return;
        }
        if (button.id == BTN_SAVE) {
            String remark = remarkField.getText().trim();
            // 只回写工作副本（原本就是从共享 Area 复制的，安全）。服务端校验差异后广播。
            proxy.sendSetProps(dim, selected.id, remark, new ArrayList<>(pendingEffects));
            // 保留预览覆盖避免渲染跳变，服务端广播返回后共享即为新值；切换/关闭时统一清除
            proxy.previewAreaProps(dim, selected.id, effectiveLightness(), effectiveDuration());
            proxy.previewAreaRemark(dim, selected.id, remark);
        } else if (button.id == BTN_TP) {
            proxy.sendTpRequest(selected.id);
        } else if (button.id == BTN_DELETE) {
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