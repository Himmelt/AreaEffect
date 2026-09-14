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

import java.util.ArrayList;
import java.util.List;

import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_ACCENT;
import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_BORDER;
import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_BTN_BG;
import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_HOVER_ROW;
import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_SCROLL_THUMB;
import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_SCROLL_TRACK;
import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_SELECTED;
import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_SLIDER_THUMB_HOT;
import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_TEXT_BODY;
import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_TEXT_HEAD;
import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_TEXT_HINT;
import static org.soraworld.areaeffect.client.gui.GuiTheme.argb;

/**
 * 区域管理主界面：<b>四栏</b>布局 —— 维度列表 / 区域列表 / 效果操作栏 / 效果详情。
 *
 * <p>层级模型（每层的控件只按<b>本层及其以上</b>的状态门控，切勿跨层错配）：
 * <ul>
 *   <li>L1 维度 {@link #dimIdx}；</li>
 *   <li>L2 区域 {@link #selected}——区域级控件：详情栏备注框与保存按钮，以及区域<b>选中行右侧的三个图标按钮</b>
 *       （传送 / 删除 / 线框开关，悬停变黄、线框图标随可见态切开/关）；</li>
 *   <li>L3 效果 {@link #pendingEffects}/{@link #effectIdx}——效果级控件：权重滑条、时段三合一控件、增删效果；</li>
 *   <li>L4 效果子类型/模式——亮度滑条与过渡时长仅 {@link LightnessEffect} 可见；时段游标仅在计时模式可拖。</li>
 * </ul>
 * 所有控件的可见性/文案/取值统一在 {@link #syncDetailWidgets()} 一处按层刷新；每个控件用哪一层的条件
 * 都显式标注，避免"区域级控件误关在效果级分支里"这类跨层错位。
 *
 * <p>编辑模型：选中区域时把共享 {@link Area} 的效果<b>复制</b>成可变的「工作列表」{@link #pendingEffects}，
 * 滑条改动只写这份副本并实时用预览覆盖表反馈渲染端，不污染共享 Area。保存整组写回服务端；
 * 切换选择/关闭界面清除预览。外部广播触发的 {@link #refreshFromProxy()} 若检测到未保存编辑（{@link #dirty}）
 * 会<b>保留工作副本</b>而非重拷，避免丢改动（详见该方法）。
 *
 * <p>生命周期两条初始化路径：构造期只备数据（此时尚无控件，{@link #rebuildPendingEffects()} 不触碰控件），
 * {@link #initGui()} 建控件后再 {@link #syncDetailWidgets()} 完成首帧同步；二者顺序是隐式契约，勿颠倒。
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
    /** 区域选中行右侧图标按钮：单个底盘边长与间距。 */
    private static final int ICON_S = 12;
    private static final int ICON_GAP = 3;
    /** 时段步进：1 分钟（以小时计）。 */
    private static final float HOUR_STEP = 1.0F / 60.0F;
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
    /** 存在未保存编辑（效果参数或备注），用于让外部刷新不清盘。 */
    private boolean dirty = false;
    /** 备注基线：加载/保存时的已提交值；当前文本与之不同即视为有未保存编辑。 */
    private String remarkBaseline = "";

    /** 详情栏：区域级备注 + 效果参数编辑控件。 */
    private FlatSlider weightSlider;
    private FlatSlider lightSlider;
    private FlatSlider durationSlider;
    private RangeSlider timeRangeSlider;
    private GuiTextField remarkField;

    // 四栏几何
    private int dimX1, dimX2, areaX1, areaX2, effectX1, effectX2, detX1, detX2;
    private int effectTop;
    private int top, bottom;

    public GuiAreas(ClientProxy proxy) {
        this.proxy = proxy;
        Minecraft mc = Minecraft.getMinecraft();
        int preferDim = mc.thePlayer != null ? mc.thePlayer.dimension : -1;
        // 构造期只备数据（控件尚未在 initGui 创建），故这里不触碰任何控件
        reloadDims(preferDim);
        reloadAreas();
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

    /**
     * 供外部（列表/删除广播回复）触发刷新：重建维度/区域列表，并尽力保留选中。
     *
     * <p>层级不变式：选中项按 id 重绑到最新镜像实例（{@code handleUpdate} 只换实例、不触达此方法，
     * 故单区域更新不重开界面；此方法在整表刷新时负责把 {@link #selected} 对齐到新实例）。
     *
     * <p>若存在未保存编辑（{@link #dirty}）且选中区域仍在，则<b>保留工作副本与备注文本</b>，只收敛下标后
     * 重画控件；否则按镜像重建副本。
     */
    public void refreshFromProxy() {
        int prefer = currentDim();
        reloadDims(prefer);
        reloadAreas();
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
        if (selected != null && dirty && pendingEffects != null) {
            // 保留未保存编辑：不重建副本、不改备注文本，仅把选中下标收敛回合法范围
            if (effectIdx >= pendingEffects.size()) {
                effectIdx = pendingEffects.size() - 1;
            }
            if (effectIdx < 0 && !pendingEffects.isEmpty()) {
                effectIdx = 0;
            }
            syncDetailWidgets();
        } else {
            rebuildPendingEffects();
            syncDetailWidgets();
        }
    }

    // ===================== 层级数据重载（只动列表，不触碰控件） =====================

    /** 重读维度并定位选中维度（{@code preferDim} 不在则取首个），重置维度列滚动。 */
    private void reloadDims(int preferDim) {
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
    }

    /** 重建本维度区域列表（不处理选中项）。 */
    private void reloadAreas() {
        int dim = currentDim();
        areas = dim >= 0 ? proxy.getAreasLocal(dim) : new ArrayList<>();
        areaCol.reset();
    }

    /** 仅重建工作列表与选中下标（构造路径用；控件可能尚未创建，故经判空写备注文本）。 */
    private void rebuildPendingEffects() {
        dirty = false;
        if (selected == null) {
            pendingEffects = null;
            effectIdx = -1;
            remarkBaseline = "";
            setRemarkText("");
            return;
        }
        pendingEffects = new ArrayList<>();
        for (AreaEffect effect : selected.getEffects()) {
            pendingEffects.add(effect.copy());
        }
        effectIdx = pendingEffects.isEmpty() ? -1 : 0;
        effectCol.reset();
        remarkBaseline = selected.getRemark();
        setRemarkText(remarkBaseline);
    }

    /** 清空选中并清除该区域的预览覆盖（用变更前的 {@link #currentDim()} 定位旧区域所属维度）。 */
    private void clearSelection() {
        if (selected != null) {
            int dim = currentDim();
            if (dim >= 0) {
                proxy.clearPreview(dim, selected.id);
            }
        }
        selected = null;
        pendingEffects = null;
        effectIdx = -1;
        dirty = false;
        remarkBaseline = "";
        setRemarkText("");
        syncDetailWidgets();
    }

    /** 切换到某区域：清旧区域预览，重挂工作副本。 */
    private void selectArea(Area area) {
        if (selected != null) {
            int dim = currentDim();
            if (dim >= 0) {
                proxy.clearPreview(dim, selected.id);
            }
        }
        selected = area;
        rebuildPendingEffects();
        syncDetailWidgets();
    }

    /** 切换到某维度：先清当前选择（旧区域预览），再重载该维度区域列表。 */
    private void selectDim(int idx) {
        clearSelection();
        dimIdx = idx;
        reloadAreas();
        syncDetailWidgets();
    }

    /** 备注文本写入（控件未就绪时静默跳过）。 */
    private void setRemarkText(String text) {
        if (remarkField != null) {
            remarkField.setText(text);
        }
    }

    /** 当前选中维度 id；无效返回 -1。所有按维度定位的读写都应先取它并判 {@code >=0}。 */
    private int currentDim() {
        return dimIdx >= 0 && dimIdx < dims.size() ? dims.get(dimIdx) : -1;
    }

    /** 当前选中效果实例；无有效选中返回 null。可见性与绘制都以它为准（单一来源）。 */
    private AreaEffect selectedEffect() {
        if (pendingEffects == null || effectIdx < 0 || effectIdx >= pendingEffects.size()) {
            return null;
        }
        return pendingEffects.get(effectIdx);
    }

    /** 选中区域在 {@link #areas} 中的下标；未选中/找不到返回 -1。 */
    private int indexOfSelected() {
        if (selected == null) {
            return -1;
        }
        for (int i = 0; i < areas.size(); i++) {
            if (areas.get(i).id == selected.id) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 选中行右侧三个图标（传送 / 删除 / 线框）的几何：从行右、滚动条之左向内排布。
     *
     * @return {@code {xTp, xDel, xSel, chipY, size}}；选中行不在可视区时返回 null
     */
    private int[] iconLayout() {
        int idx = indexOfSelected();
        if (idx < 0) {
            return null;
        }
        int y = areaCol.rowY(idx);
        if (y < top || y + ROW_H > bottom) {
            return null;
        }
        int cell = ICON_S + ICON_GAP;
        int iconRight = areaX2 - ScrollColumn.BAR_W - 2;
        int x0 = iconRight - (cell * 3 - ICON_GAP);
        int cy = y + (ROW_H - ICON_S) / 2;
        return new int[]{x0, x0 + cell, x0 + cell * 2, cy, ICON_S};
    }

    /** 绘制选中行的三个图标按钮，命中者以高亮黄显示；{@code wireOn} 决定线框图标画"开启"还是"关闭"态。 */
    private void drawAreaIcons(int mouseX, int mouseY, boolean wireOn) {
        int[] g = iconLayout();
        if (g == null) {
            return;
        }
        int[] ids = {BTN_TP, BTN_DELETE, BTN_SEL};
        for (int k = 0; k < 3; k++) {
            int x = g[k];
            boolean hot = mouseX >= x && mouseX < x + g[4] && mouseY >= g[3] && mouseY < g[3] + g[4];
            drawIconChip(x, g[3], g[4]);
            drawIconGlyph(ids[k], x, g[3], g[4], hot ? COLOR_SLIDER_THUMB_HOT : COLOR_TEXT_BODY, wireOn);
        }
    }

    /** 图标底盘：半透明深底 + 描边，保证任意行底色下都清晰。 */
    private void drawIconChip(int x, int y, int s) {
        drawRect(x, y, x + s, y + s, argb(COLOR_BTN_BG));
        drawRect(x, y, x + s, y + 1, argb(COLOR_BORDER));
        drawRect(x, y + s - 1, x + s, y + s, argb(COLOR_BORDER));
        drawRect(x, y, x + 1, y + s, argb(COLOR_BORDER));
        drawRect(x + s - 1, y, x + s, y + s, argb(COLOR_BORDER));
    }

    /** 图标字形（全用 drawRect/直线绘制，无位图资源）：传送=↗ 箭头、删除=✕、线框=方框（开=内填、关=仅框）。 */
    private void drawIconGlyph(int id, int x, int y, int s, int color, boolean wireOn) {
        if (id == BTN_DELETE) {
            drawLine(x + 3, y + 3, x + s - 3, y + s - 3, color);
            drawLine(x + s - 3, y + 3, x + 3, y + s - 3, color);
        } else if (id == BTN_TP) {
            int tipX = x + s - 3;
            int tipY = y + 3;
            drawLine(x + 3, y + s - 3, tipX, tipY, color);
            drawLine(tipX - 4, tipY, tipX, tipY, color);
            drawLine(tipX, tipY, tipX, tipY + 4, color);
        } else {
            int in = 3;
            int r = x + s - 1 - in;
            int b = y + s - 1 - in;
            drawRect(x + in, y + in, r + 1, y + in + 1, argb(color));
            drawRect(x + in, b, r + 1, b + 1, argb(color));
            drawRect(x + in, y + in, x + in + 1, b + 1, argb(color));
            drawRect(r, y + in, r + 1, b + 1, argb(color));
            if (wireOn) {
                drawRect(x + in + 3, y + in + 3, r - 2, b - 2, argb(color));
            }
        }
    }

    /** 1px 直线（Bresenham），供图标斜边使用。 */
    private void drawLine(int x1, int y1, int x2, int y2, int color) {
        int dx = Math.abs(x2 - x1);
        int dy = -Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx + dy;
        int argb = argb(color);
        for (int guard = 0; guard < 1024; guard++) {
            drawRect(x1, y1, x1 + 1, y1 + 1, argb);
            if (x1 == x2 && y1 == y2) {
                break;
            }
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x1 += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y1 += sy;
            }
        }
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
        // 效果栏顶部工具栏：添加/删除效果（L3 效果级）
        int toolW = (effectX2 - effectX1 - GAP) / 2;
        buttonList.add(new FlatButton(BTN_ADD_EFFECT, effectX1, top, toolW, EFFECT_TOOLBAR_H,
                translate("gui.areaeffect.edit.addEffect")));
        buttonList.add(new FlatButton(BTN_DEL_EFFECT, effectX1 + toolW + GAP, top, effectX2 - (effectX1 + toolW + GAP),
                EFFECT_TOOLBAR_H, translate("gui.areaeffect.edit.delEffect")));
        // 传送/删除/线框开关已改为区域选中行右侧的图标按钮（见 drawScreen 图标绘制 + doAreaAction），不再是面板按钮
        // 详情栏底部：保存（面板底边内侧居中）
        int by = bottom - BTN_H - 4;
        buttonList.add(new FlatButton(BTN_SAVE, (detX1 + detX2 - BTN_W) / 2, by, BTN_W, BTN_H,
                translate("gui.areaeffect.edit.save")));
        // 效果参数编辑滑条 + 备注输入框
        int sw = detX2 - detX1 - 20;
        weightSlider = new FlatSlider(detX1 + 10, top + 44, sw, 18,
                translate("gui.areaeffect.edit.weight"), 0.0F, AreaEffect.MAX_WEIGHT, 0.0F, 1.0F);
        lightSlider = new FlatSlider(detX1 + 10, top + 66, sw, 18,
                translate("gui.areaeffect.edit.lightness"), 0.0F, 100.0F, 100.0F, 1.0F);
        durationSlider = new FlatSlider(detX1 + 10, top + 88, sw, 18,
                translate("gui.areaeffect.edit.duration"),
                LightnessEffect.MIN_DURATION, LightnessEffect.MAX_DURATION, 1.0F, 0.1F);
        // 时段三合一控件：拖游标改区间、点非游标区域轮切模式（始终→游戏→现实），步进 1 分钟、显示 HH:mm
        timeRangeSlider = new RangeSlider(detX1 + 10, top + 110, sw, 18,
                0.0F, 24.0F, 6.0F, 18.0F, HOUR_STEP);
        timeRangeSlider.setOnModeToggle(this::cycleTimeMode);
        remarkField = new GuiTextField(fontRendererObj, detX1 + 34, top + 6, sw - 24, 16);
        remarkField.setMaxStringLength(Area.REMARK_MAX);
        buttonList.add(weightSlider);
        buttonList.add(lightSlider);
        buttonList.add(durationSlider);
        buttonList.add(timeRangeSlider);
        syncDetailWidgets();
        // 构造期已定 remarkBaseline，但当时无控件；控件建好后补写一次初始备注
        setRemarkText(remarkBaseline);
    }

    /**
     * 按层刷新各控件的启用态/可见性/文案/取值基线。<b>每个控件的门控层级已显式标注</b>，
     * 区域级控件一律按 {@code has}（是否选中区域），不得放进效果级分支。
     */
    private void syncDetailWidgets() {
        boolean has = selected != null;
        // 启用态：保存按钮属 L2（按 has）；增删效果属 L3。传送/删除/线框已是行内图标，不在此列
        for (Object b : buttonList) {
            GuiButton btn = (GuiButton) b;
            int id = btn.id;
            if (id == BTN_SAVE) {
                btn.enabled = has;
            } else if (id == BTN_ADD_EFFECT) {
                btn.enabled = has && !hasAllEffectTypes();
            } else if (id == BTN_DEL_EFFECT) {
                btn.enabled = has && effectIdx >= 0;
            }
        }
        // 防御：initGui 若中途异常被吞，控件可能为 null；此时清空选中走空界面分支，避免 NPE 连锁
        if (weightSlider == null || lightSlider == null || durationSlider == null
                || timeRangeSlider == null || remarkField == null) {
            selected = null;
            return;
        }
        // L2 区域级：备注（可见性按 has；文本仅在切换/加载时由 rebuild 写入，不在此重设以免覆盖用户输入）
        remarkField.setVisible(has);
        // L3 效果级：权重滑条、时段控件仅在有选中效果时可见
        AreaEffect effect = selectedEffect();
        boolean hasEffect = effect != null;
        weightSlider.visible = hasEffect;
        timeRangeSlider.visible = hasEffect;
        if (!hasEffect) {
            lightSlider.visible = false;
            durationSlider.visible = false;
            return;
        }
        weightSlider.setValueRaw(effect.getWeight());
        // L4 子类型：亮度/过渡滑条只属于 LightnessEffect
        boolean isLight = effect instanceof LightnessEffect;
        lightSlider.visible = isLight;
        durationSlider.visible = isLight;
        if (isLight) {
            LightnessEffect light = (LightnessEffect) effect;
            lightSlider.setValueRaw(light.getLightness());
            durationSlider.setValueRaw(light.getDuration());
        }
        // L4 模式：时段三合一控件（"始终"态 timed=false 隐藏游标、整条全天）
        timeRangeSlider.setMode(timeModeLabel(effect.getTimeMode()),
                effect.getTimeMode() != AreaEffect.TIME_ALWAYS);
        timeRangeSlider.setRangeRaw(effect.getStartHour(), effect.getEndHour());
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
        int listDim = currentDim();
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
            // 备注：ID 后显示，超出可用宽度截断为省略号（右侧留滚动条；选中行再留图标位）
            int rightPad = ScrollColumn.BAR_W + 10 + (sel ? (ICON_S + ICON_GAP) * 3 - ICON_GAP + 4 : 0);
            int idW = fontRendererObj.getStringWidth("#" + area.id);
            int avail = (areaX2 - areaX1) - idW - rightPad - 12;
            String remark = listDim >= 0 ? proxy.getEffectiveRemark(listDim, area) : "";
            if (!remark.isEmpty() && avail > 8) {
                String shown = fontRendererObj.trimStringToWidth(remark, avail);
                if (!shown.equals(remark)) {
                    shown = fontRendererObj.trimStringToWidth(remark, avail - 5) + "..";
                }
                fontRendererObj.drawStringWithShadow(shown, areaX1 + 6 + idW + 4, y + 5, COLOR_ACCENT);
            }
            // 选中行右侧图标按钮：传送 / 删除 / 线框开关（线框图标随当前可见态显示开/关）
            if (sel) {
                drawAreaIcons(mouseX, mouseY, listDim >= 0 && proxy.isAreaVisible(listDim, area.id));
            }
        }

        // 中右栏：效果列表
        if (pendingEffects != null) {
            for (int i = 0; i < pendingEffects.size(); i++) {
                int y = effectCol.rowY(i);
                if (y < effectTop || y + ROW_H > effectCol.bottom) {
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
                drawCenteredString(fontRendererObj, hint, (effectX1 + effectX2) / 2, (effectTop + effectCol.bottom) / 2 - 4, COLOR_TEXT_HINT);
            }
        }

        // 右栏：详情（paint 只做绘制与"提交滑条编辑"，后者已抽到 commitSliderEdits）
        AreaEffect editing = selectedEffect();
        if (selected != null) {
            int dim = currentDim();
            fontRendererObj.drawStringWithShadow("#" + selected.id, detX1 + 10, top + 10, COLOR_TEXT_HEAD);
            remarkField.drawTextBox();
            // 备注实时写覆盖表（列表行即时更新；预览不入共享 Area）；与基线不同即标记未保存
            String text = remarkField.getText();
            if (!text.equals(remarkBaseline)) {
                dirty = true;
            }
            if (dim >= 0 && !text.equals(proxy.getEffectiveRemark(dim, selected))) {
                proxy.previewAreaRemark(dim, selected.id, text);
            }
            if (editing != null) {
                commitSliderEdits();
            } else {
                String hint = translate("gui.areaeffect.list.noeffect");
                drawCenteredString(fontRendererObj, hint, (detX1 + detX2) / 2, (top + bottom) / 2 - 4, COLOR_TEXT_HINT);
            }
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

    /**
     * 把滑条当前值写回工作副本，变化时刷新预览并标记未保存。
     *
     * <p>注：受 1.7.10 框架限制（滑条靠 {@code drawButton} 自驱拖动、无独立变更回调），此方法由
     * {@link #drawScreen} 每帧调用；逻辑已从绘制里析出，绘制通道只负责触发它。
     */
    private void commitSliderEdits() {
        AreaEffect effect = selectedEffect();
        if (effect == null) {
            return;
        }
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
        // 时段：仅在计时（控件可见）时回写，"始终"下不改动已存小时值；两游标可越界，直接映射 start/end
        if (effect.getTimeMode() != AreaEffect.TIME_ALWAYS) {
            float sh = timeRangeSlider.getStart();
            float eh = timeRangeSlider.getEnd();
            if (sh != effect.getStartHour()) {
                effect.setStartHour(sh);
                changed = true;
            }
            if (eh != effect.getEndHour()) {
                effect.setEndHour(eh);
                changed = true;
            }
        }
        if (changed) {
            dirty = true;
            int dim = currentDim();
            if (dim >= 0) {
                proxy.previewAreaProps(dim, selected.id, effectiveLightness(), effectiveDuration());
            }
        }
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
        // 选中行右侧图标（传送/删除/线框）：优先于区域行选中处理，命中即执行对应操作
        int[] g = iconLayout();
        if (g != null) {
            int size = g[4];
            for (int k = 0; k < 3; k++) {
                if (mouseX >= g[k] && mouseX < g[k] + size && mouseY >= g[3] && mouseY < g[3] + size) {
                    doAreaAction(new int[]{BTN_TP, BTN_DELETE, BTN_SEL}[k]);
                    return;
                }
            }
        }
        // 左栏选中维度（L1）
        if (mouseX >= dimX1 && mouseX < dimX2 && mouseY >= top && mouseY < bottom) {
            int idx = (mouseY - top - 4 + dimCol.scroll()) / ROW_H;
            if (idx >= 0 && idx < dims.size() && idx != dimIdx) {
                selectDim(idx);
            }
            return;
        }
        // 中左栏选中区域（L2）
        if (mouseX >= areaX1 && mouseX < areaX2 && mouseY >= top && mouseY < bottom) {
            int idx = (mouseY - top - 4 + areaCol.scroll()) / ROW_H;
            if (idx >= 0 && idx < areas.size()) {
                Area area = areas.get(idx);
                if (selected == null || area.id != selected.id) {
                    selectArea(area);
                }
            }
            return;
        }
        // 中右栏选中效果（L3，仅在列表区内，避开底部区域级按钮带）
        if (pendingEffects != null && mouseX >= effectX1 && mouseX < effectX2 && mouseY >= effectTop && mouseY < effectCol.bottom) {
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
        int dim = currentDim();
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
                        afterEffectStructuralChange(dim);
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
                afterEffectStructuralChange(dim);
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
            // 保存即视为已提交：推进基线、清脏标记，避免下一帧又判定为未保存
            remarkBaseline = remark;
            dirty = false;
            // 保留预览覆盖避免渲染跳变，服务端广播返回后共享即为新值；切换/关闭时统一清除
            proxy.previewAreaProps(dim, selected.id, effectiveLightness(), effectiveDuration());
            proxy.previewAreaRemark(dim, selected.id, remark);
        }
    }

    /**
     * 区域行图标的操作分发（传送 / 删除 / 线框开关），作用于当前 {@link #selected}。
     * 由 {@link #mouseClicked} 的图标命中调用。
     */
    private void doAreaAction(int id) {
        int dim = currentDim();
        if (selected == null) {
            return;
        }
        if (id == BTN_TP) {
            proxy.sendTpRequest(selected.id);
        } else if (id == BTN_DELETE) {
            if (dim < 0) {
                return;
            }
            proxy.sendDeleteRequest(dim, selected.id);
            clearSelection();
        } else if (id == BTN_SEL) {
            if (dim < 0) {
                return;
            }
            proxy.toggleAreaVisible(dim, selected.id);
        }
    }

    /** 效果增删后的统一收尾：标记未保存、收敛滚动、刷新预览与控件。 */
    private void afterEffectStructuralChange(int dim) {
        dirty = true;
        effectCol.clamp(pendingEffects.size());
        if (dim >= 0) {
            proxy.previewAreaProps(dim, selected.id, effectiveLightness(), effectiveDuration());
        }
        syncDetailWidgets();
    }

    /** 时段三合一控件的"点击非游标区域"回调：把当前效果的时段模式轮切 始终→游戏→现实→始终。 */
    private void cycleTimeMode() {
        if (selectedEffect() == null) {
            return;
        }
        AreaEffect effect = selectedEffect();
        effect.setTimeMode((effect.getTimeMode() + 1) % 3);
        dirty = true;
        syncDetailWidgets();
    }

    /** 时间段模式的按钮文案（始终开启 / 游戏时间 / 现实时间）。 */
    private String timeModeLabel(int mode) {
        String name;
        if (mode == AreaEffect.TIME_GAME) {
            name = translate("gui.areaeffect.edit.timeMode.game");
        } else if (mode == AreaEffect.TIME_REAL) {
            name = translate("gui.areaeffect.edit.timeMode.real");
        } else {
            name = translate("gui.areaeffect.edit.timeMode.always");
        }
        return translate("gui.areaeffect.edit.timeMode") + ": " + name;
    }

    /** 本地化文案。 */
    private static String translate(String key) {
        return StatCollector.translateToLocal(key);
    }

    private static String fmt(float v) {
        return String.format("%.0f", (double) v);
    }

}
