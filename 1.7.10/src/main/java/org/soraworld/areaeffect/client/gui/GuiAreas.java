package org.soraworld.areaeffect.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.soraworld.areaeffect.client.ClientProxy;
import org.soraworld.areaeffect.common.effect.AreaEffect;
import org.soraworld.areaeffect.common.effect.EffectTypes;
import org.soraworld.areaeffect.common.effect.LightnessEffect;
import org.soraworld.areaeffect.common.network.Area;

import java.util.ArrayList;
import java.util.List;

import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_ACCENT;
import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_BG;
import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_BORDER;
import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_DANGER;
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
 * 区域管理主界面（按 J 键经服务端授权后打开）。
 *
 * <p><b>版式</b>：整块界面只有一个外框，内部靠共用线分隔 —— 标题带之下依次是维度 tab 条、三栏内容区、
 * 底栏「区域操作条」。相邻元素共用同一根线（tab 条与内容区、内容区与底栏、三栏之间、工具条两个按钮
 * 之间），不再各画一圈边框。
 * <ul>
 *   <li><b>L1 维度</b>：顶部横向 tab 条。宽度按文字实宽实测，总宽超出可见宽度时才出现两侧
 *       {@code ❮ ❯} 翻动按钮，到两端时按钮转灰；不需要翻动时两个按钮整块隐藏，并撤掉 tab 条
 *       左右两条竖线（否则会留下空边线）。</li>
 *   <li><b>L2 区域</b>：中左栏列表只负责"选择"（{@code #id} 右对齐成列 + 备注左对齐紧跟其后）；
 *       区域级的东西 —— 备注编辑、传送、删除、线框、保存 —— 全部收在底栏那条 22px 的操作条里。
 *       保存之所以也在底栏：它的门控是 L2（只要选中区域即可用），而一次提交覆盖备注与整份效果
 *       工作副本，本质是栏级动作，放进任何一栏内部都会被误读成"只保存这一段"。</li>
 *   <li><b>L3/L4 效果</b>：中右栏列表（顶部工具条增删效果）+ 右栏参数。右栏<b>只服务效果</b>：
 *       不含任何 L2 内容，也不再自报"是什么效果"（效果列表的选中行已表明），就是该效果的参数。</li>
 * </ul>
 *
 * <p>层级模型：每层的控件只按<b>本层及其以上</b>的状态门控，切勿跨层错配 —— L1 维度 {@link #dimIdx}；
 * L2 区域 {@link #selected}；L3 效果 {@link #pendingEffects}/{@link #effectIdx}；L4 效果子类型/模式。
 * 所有控件的可见性/文案/取值统一在 {@link #syncDetailWidgets()} 一处按层刷新。
 *
 * <p>编辑模型：选中区域时把共享 {@link Area} 的效果<b>复制</b>成可变的「工作列表」{@link #pendingEffects}，
 * 滑条改动只写这份副本并实时用预览覆盖表反馈渲染端，不污染共享 Area。保存整组写回服务端；
 * 切换选择/关闭界面清除预览。外部广播触发的 {@link #refreshFromProxy()} 若检测到未保存编辑（{@link #dirty}）
 * 会<b>保留工作副本</b>而非重拷，避免丢改动。
 *
 * <p>生命周期两条初始化路径：构造期只备数据（此时尚无控件、{@code fontRendererObj} 也未就绪，
 * 故 {@link #reloadDims(int)} 里的 tab 实测会自行跳过），{@link #initGui()} 建控件后再
 * {@link #syncDetailWidgets()} 完成首帧同步；二者顺序是隐式契约，勿颠倒。
 */
public class GuiAreas extends GuiScreen {

    private static final int ROW_H = 18;
    /** 标题带高度（在外框以上，只有一行居中标题）。 */
    private static final int HEADER_H = 26;
    /** 外框与窗口底边之间的距离。 */
    private static final int BOTTOM_MARGIN = 8;
    /** tab 条总高（含与内容区共用的那 1px 横线）。 */
    private static final int TAB_H = 18;
    /** 翻动按钮宽度。 */
    private static final int NAV_W = 18;
    /** 底栏「区域操作条」总高（含与内容区共用的那 1px 横线）。 */
    private static final int ACTION_H = 22;
    /** 效果栏顶部「添加/删除」按钮行的高度。 */
    private static final int TOOLBAR_H = 16;
    /** 底栏图标按钮边长、图标间距，以及删除后额外留出的空档。 */
    private static final int ICON_S = 18;
    private static final int ICON_GAP = 3;
    private static final int ICON_DANGER_GAP = 10;
    /** 底栏左右内边距（也用作底栏元素之间的固定间隔）。 */
    private static final int ACT_PAD = 8;
    private static final int SLIDER_H = 18;
    /** 详情栏滑条的行距与首行偏移。 */
    private static final int SLIDER_PITCH = 24;
    private static final int SLIDER_TOP = 6;
    /** tab 文字左右内边距。 */
    private static final int TAB_PAD = 8;
    /** 时段步进：1 分钟（以小时计）。 */
    private static final float HOUR_STEP = 1.0F / 60.0F;
    /**
     * 三栏宽度权重：区域 : 效果 : 详情。按内容实际需要定，不是把原四栏等比放大 ——
     * 区域栏每行都要放 {@code #id} + 备注（信息密度最高），详情栏只有 4 条滑条（够放最长文案即可），
     * 效果栏够放「名称 + W 值」即可。权重只有相对意义，不必凑满 100。
     */
    private static final int AREA_G = 26;
    private static final int EFFECT_G = 22;
    private static final int DETAIL_G = 40;

    private static final int BTN_DELETE = 0;
    private static final int BTN_TP = 1;
    private static final int BTN_SEL = 2;
    private static final int BTN_SAVE = 3;
    private static final int BTN_ADD_EFFECT = 4;
    private static final int BTN_DEL_EFFECT = 5;
    private static final int BTN_NAV_L = 6;
    private static final int BTN_NAV_R = 7;

    private final ClientProxy proxy;

    /** L1：维度列表、选中下标，以及每个 tab 的实测宽度与横向滚动量。 */
    private List<Integer> dims = new ArrayList<>();
    private int dimIdx = -1;
    private final List<Integer> tabW = new ArrayList<>();
    private int tabTotalW = 0;
    private int tabScroll = 0;
    private boolean tabOverflow = false;

    /** L2：当前维度的区域列表与选中区域。 */
    private List<Area> areas = new ArrayList<>();
    private final ScrollColumn areaCol = new ScrollColumn(ROW_H);
    private Area selected = null;

    /** L3：选中区域的效果工作列表（副本，不污染共享 Area）与选中下标。 */
    private final ScrollColumn effectCol = new ScrollColumn(ROW_H);
    private List<AreaEffect> pendingEffects = null;
    private int effectIdx = -1;
    /** 存在未保存编辑（效果参数或备注），用于底栏的未保存指示。 */
    private boolean dirty = false;
    /** 备注基线：加载/保存时的已提交值；当前文本与之不同即视为有未保存编辑。 */
    private String remarkBaseline = "";

    /** 详情栏：只有效果参数控件（本栏不含任何区域级控件）。 */
    private FlatSlider weightSlider;
    private FlatSlider lightSlider;
    private FlatSlider durationSlider;
    private RangeSlider timeRangeSlider;
    /** 底栏：备注输入框（区域级）。 */
    private GuiTextField remarkField;
    /** 底栏：线框开关按钮，需要按该区域当前是否显示线框切换字形。 */
    private IconButton wireBtn;

    // ===================== 几何 =====================
    private int frameX1, frameX2, frameY1, frameY2;
    private int tabY1, tabY2, tabAreaX1, tabAreaX2, tabViewX1, tabViewX2, tabLineY;
    private int bodyX1, bodyX2, bodyY1, bodyY2, actLineY, actY1, actY2;
    private int areaX1, areaX2, effectX1, effectX2, detX1, detX2;
    /** 效果列表首行区上界（工具条之下）。 */
    private int effectTop;
    /** 底栏：#id·形状 槽位、备注框、未保存圆点的位置。 */
    private int cidX1, cidW, cidY;
    private int dotX1, dotY;
    private int iconY;

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

    /** 重读维度并定位选中维度（{@code preferDim} 不在则取首个），重算 tab 宽度并收敛横向滚动。 */
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
        // 整表刷新（广播导致维度增删）时 tab 总宽会变，翻动按钮的显隐与位置要跟着重排
        layoutTabs();
    }

    /**
     * 重排 tab 条：实测文字宽度 → 判断是否需要翻动 → 定下 tab 区与两个翻动按钮的位置。
     *
     * <p>翻动按钮出现后 tab 区变窄，必然仍然超出（总宽已大于整个内宽），所以"是否需要翻动"不会来回抖动。
     * 初始化前（几何全为 0）直接跳过。
     */
    private void layoutTabs() {
        measureTabs();
        if (bodyX2 <= bodyX1) {
            return;
        }
        tabOverflow = tabTotalW > (bodyX2 - bodyX1);
        tabAreaX1 = tabOverflow ? bodyX1 + NAV_W : bodyX1;
        tabAreaX2 = tabOverflow ? bodyX2 - NAV_W : bodyX2;
        tabViewX1 = tabAreaX1 + (tabOverflow ? 1 : 0);
        tabViewX2 = tabAreaX2 - (tabOverflow ? 1 : 0);
        tabScroll = clampInt(tabScroll, 0, maxTabScroll());
        for (Object b : buttonList) {
            GuiButton btn = (GuiButton) b;
            if (btn.id == BTN_NAV_L) {
                btn.xPosition = tabAreaX1 - NAV_W;
                btn.yPosition = tabY1;
            } else if (btn.id == BTN_NAV_R) {
                btn.xPosition = tabAreaX2;
                btn.yPosition = tabY1;
            }
        }
        syncTabNav();
    }

    /** 重建本维度区域列表（不处理选中项）。 */
    private void reloadAreas() {
        int dim = currentDim();
        areas = dim >= 0 ? proxy.getAreasLocal(dim) : new ArrayList<Area>();
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
        pendingEffects = new ArrayList<AreaEffect>();
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
        if (remarkField != null) {
            remarkField.setFocused(false);
        }
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

    /** 该类型效果是否已存在于工作列表（用于「添加效果」去重）。 */
    private static boolean containsType(List<AreaEffect> list, String typeId) {
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

    // ===================== 几何 =====================

    /**
     * tab 宽度按文字实宽实测。维度名长度不同（"维度 -1" 比 "维度 0" 宽），不能按固定宽度排；
     * {@code fontRendererObj} 未就绪时（构造期）直接跳过，等 {@link #initGui()} 再算。
     */
    private void measureTabs() {
        tabW.clear();
        tabTotalW = 0;
        if (fontRendererObj == null) {
            return;
        }
        for (Integer dim : dims) {
            int w = fontRendererObj.getStringWidth(tabLabel(dim)) + TAB_PAD * 2;
            tabW.add(w);
            tabTotalW += w;
        }
    }

    @Override
    public void initGui() {
        super.initGui();
        // 左右各留 3% 边距；整块框从标题带下方一直到窗口底边留 BOTTOM_MARGIN
        int left = Math.round(width * 0.03F);
        frameX1 = left;
        frameX2 = width - left;
        frameY1 = HEADER_H;
        frameY2 = height - BOTTOM_MARGIN;

        tabY1 = frameY1 + 1;
        tabY2 = tabY1 + TAB_H;
        tabLineY = tabY2 - 1;
        actLineY = frameY2 - 1 - ACTION_H;
        bodyY1 = tabLineY + 1;
        bodyY2 = actLineY;
        actY1 = actLineY + 1;
        actY2 = frameY2 - 1;

        bodyX1 = frameX1 + 1;
        bodyX2 = frameX2 - 1;

        // 三栏：内宽先扣掉两条列间共用竖线，再按权重分配
        int colsW = (bodyX2 - bodyX1) - 2;
        int totalG = AREA_G + EFFECT_G + DETAIL_G;
        int areaW = Math.round((float) colsW * AREA_G / totalG);
        int effectW = Math.round((float) colsW * EFFECT_G / totalG);
        int detailW = colsW - areaW - effectW;
        areaX1 = bodyX1;
        areaX2 = areaX1 + areaW;
        effectX1 = areaX2 + 1;
        effectX2 = effectX1 + effectW;
        detX1 = effectX2 + 1;
        detX2 = bodyX2;
        effectTop = bodyY1 + TOOLBAR_H + 4;

        buttonList.clear();
        // 翻动按钮：贴在外框内边界上，自身不描边（两侧线由外框与 tab 条的共用竖线提供）
        buttonList.add(new IconButton(BTN_NAV_L, tabAreaX1 - NAV_W, tabY1, NAV_W, TAB_H - 1,
                IconButton.GLYPH_PREV, COLOR_SLIDER_THUMB_HOT, 0));
        buttonList.add(new IconButton(BTN_NAV_R, tabAreaX2, tabY1, NAV_W, TAB_H - 1,
                IconButton.GLYPH_NEXT, COLOR_SLIDER_THUMB_HOT, 0));
        // 效果栏顶部工具条：左右边由列分隔线提供；两按钮齐平、共用中间一根竖线（删除按钮的 B_LEFT 提供），各自一条底线
        int toolW = (effectX2 - effectX1) / 2;
        buttonList.add(new FlatButton(BTN_ADD_EFFECT, effectX1, bodyY1, toolW, TOOLBAR_H,
                translate("gui.areaeffect.edit.addEffect"), FlatButton.B_BOTTOM));
        buttonList.add(new FlatButton(BTN_DEL_EFFECT, effectX1 + toolW, bodyY1,
                effectX2 - (effectX1 + toolW), TOOLBAR_H,
                translate("gui.areaeffect.edit.delEffect"), FlatButton.B_LEFT | FlatButton.B_BOTTOM));

        // 底栏四个图标：自右向左排。保存常驻最右（最常用）；删除隔离在最左、右侧留空档、悬停红 ——
        // 它是唯一不可逆的动作（服务端立即落盘 + 广播），不该和其他三个等距混在一排。
        iconY = actY1 + (ACTION_H - 1 - ICON_S) / 2;
        int right = frameX2 - 1 - ACT_PAD;
        int saveX = right - ICON_S;
        int selX = saveX - ICON_GAP - ICON_S;
        int tpX = selX - ICON_GAP - ICON_S;
        int delX = tpX - ICON_GAP - ICON_DANGER_GAP - ICON_S;
        buttonList.add(new IconButton(BTN_DELETE, delX, iconY, ICON_S, IconButton.GLYPH_DELETE, COLOR_DANGER));
        buttonList.add(new IconButton(BTN_TP, tpX, iconY, ICON_S, IconButton.GLYPH_TP, COLOR_SLIDER_THUMB_HOT));
        wireBtn = new IconButton(BTN_SEL, selX, iconY, ICON_S, IconButton.GLYPH_WIRE, COLOR_SLIDER_THUMB_HOT);
        buttonList.add(wireBtn);
        buttonList.add(new IconButton(BTN_SAVE, saveX, iconY, ICON_S, IconButton.GLYPH_SAVE, COLOR_SLIDER_THUMB_HOT));

        // 底栏 #id 槽位宽度反算，使备注框左边界正好落在「区域列 | 效果列」那根共用竖线上：
        // 槽位左端 = 框内左 + ACT_PAD，槽位右端再走 ACT_PAD 就是输入框，故
        // 槽宽 = 区域列宽 + 1(竖线) - 2 * ACT_PAD。区域列宽随三栏比例变化，所以每次 initGui 重算。
        cidX1 = bodyX1 + ACT_PAD;
        cidY = actY1 + (ACTION_H - 1 - 8) / 2;
        dotX1 = delX - ACT_PAD - 6;
        dotY = actY1 + (ACTION_H - 1 - 6) / 2;
        int wantCid = areaW + 1 - ACT_PAD * 2;
        int roomCid = (dotX1 - ACT_PAD) - (cidX1 + ACT_PAD) - 60;
        cidW = Math.max(44, Math.min(wantCid, roomCid));
        int remarkX1 = cidX1 + cidW + ACT_PAD;
        int remarkX2 = dotX1 - ACT_PAD;
        remarkField = new GuiTextField(fontRendererObj, remarkX1, actY1 + (ACTION_H - 1 - 16) / 2,
                Math.max(40, remarkX2 - remarkX1), 16);
        remarkField.setMaxStringLength(Area.REMARK_MAX);

        // 详情栏四条滑条：权重 / 过渡时长 / 生效时段 是所有效果通用参数在前，亮度（LightnessEffect 专属）在后；
        // 本栏只服务效果（没有标题/备注/保存），直接从顶部起排
        int sw = detailW - 20;
        weightSlider = new FlatSlider(detX1 + 10, bodyY1 + SLIDER_TOP, sw, SLIDER_H,
                translate("gui.areaeffect.edit.weight"), 0.0F, AreaEffect.MAX_WEIGHT, 0.0F, 1.0F);
        durationSlider = new FlatSlider(detX1 + 10, bodyY1 + SLIDER_TOP + SLIDER_PITCH, sw, SLIDER_H,
                translate("gui.areaeffect.edit.duration"),
                LightnessEffect.MIN_DURATION, LightnessEffect.MAX_DURATION, 1.0F, 0.1F);
        // 时段三合一控件：拖游标改区间、点非游标区域轮切模式（始终→游戏→现实），步进 1 分钟、显示 HH:mm
        timeRangeSlider = new RangeSlider(detX1 + 10, bodyY1 + SLIDER_TOP + SLIDER_PITCH * 2, sw, SLIDER_H,
                0.0F, 24.0F, 6.0F, 18.0F, HOUR_STEP);
        timeRangeSlider.setOnModeToggle(this::cycleTimeMode);
        lightSlider = new FlatSlider(detX1 + 10, bodyY1 + SLIDER_TOP + SLIDER_PITCH * 3, sw, SLIDER_H,
                translate("gui.areaeffect.edit.lightness"), 0.0F, 100.0F, 100.0F, 1.0F);
        buttonList.add(weightSlider);
        buttonList.add(lightSlider);
        buttonList.add(durationSlider);
        buttonList.add(timeRangeSlider);

        // 两列滚动条：贴各自栏的右侧内边界（维度列表已改为 tab 条，不再需要滚动条）
        areaCol.x = areaX2 - ScrollColumn.BAR_W - 1;
        areaCol.top = bodyY1;
        areaCol.bottom = bodyY2;
        effectCol.x = effectX2 - ScrollColumn.BAR_W - 1;
        effectCol.top = effectTop;
        effectCol.bottom = bodyY2;

        layoutTabs();
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
        // 启用态：底栏四图标（含保存）属 L2（按 has）；增删效果属 L3
        for (Object b : buttonList) {
            GuiButton btn = (GuiButton) b;
            int id = btn.id;
            if (id == BTN_SAVE || id == BTN_DELETE || id == BTN_TP || id == BTN_SEL) {
                btn.enabled = has;
            } else if (id == BTN_ADD_EFFECT) {
                btn.enabled = has && !hasAllEffectTypes();
            } else if (id == BTN_DEL_EFFECT) {
                btn.enabled = has && effectIdx >= 0;
            }
        }
        syncTabNav();
        // 防御：initGui 若中途异常被吞，控件可能为 null；此时清空选中走空界面分支，避免 NPE 连锁
        if (weightSlider == null || lightSlider == null || durationSlider == null
                || timeRangeSlider == null || remarkField == null) {
            selected = null;
            return;
        }
        // L2 区域级：备注框常驻底栏，未选中区域时禁用（不隐藏 —— 底栏元素位置固定，避免左右跳动）
        remarkField.setEnabled(has);
        if (wireBtn != null) {
            int dim = currentDim();
            wireBtn.setOn(has && dim >= 0 && proxy.isAreaVisible(dim, selected.id));
        }
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

    /** 翻动按钮只在 tab 总宽超出可见宽度时出现；到两端时转灰不可点。 */
    private void syncTabNav() {
        for (Object b : buttonList) {
            GuiButton btn = (GuiButton) b;
            if (btn.id == BTN_NAV_L) {
                btn.visible = tabOverflow;
                btn.enabled = tabOverflow && tabScroll > 0;
            } else if (btn.id == BTN_NAV_R) {
                btn.visible = tabOverflow;
                btn.enabled = tabOverflow && tabScroll < maxTabScroll();
            }
        }
    }

    /** tab 条可见内容宽度（nav 出现时已扣掉两侧按钮与共用线）。 */
    private int tabViewWidth() {
        return tabViewX2 - tabViewX1;
    }

    private int maxTabScroll() {
        return Math.max(0, tabTotalW - tabViewWidth());
    }

    /** 命中下标：{@code offset} 为相对 tab 内容左端的像素偏移。 */
    private int tabIndexAt(int offset) {
        int x = 0;
        for (int i = 0; i < tabW.size(); i++) {
            x += tabW.get(i);
            if (offset < x) {
                return i;
            }
        }
        return -1;
    }

    // ===================== 绘制 =====================

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // 先铺全屏半透明底，再画标题与内容，使标题「区域管理」位于背景之上而非被其覆盖
        drawBackground();
        drawCenteredString(fontRendererObj, translate("gui.areaeffect.manager.title"), width / 2, 8, COLOR_TEXT_HEAD);

        drawChrome();
        drawTabs(mouseX, mouseY);
        drawAreaList(mouseX, mouseY);
        drawEffectList(mouseX, mouseY);
        drawDetail();
        drawActionBar(mouseX, mouseY);

        drawScrollBar(areaCol, areas.size());
        drawScrollBar(effectCol, pendingEffects == null ? 0 : pendingEffects.size());

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    /**
     * 全屏黑色半透明底：铺满整块画面（含外框之外），唯独把详情栏（右栏效果参数区
     * detX1..detX2 × bodyY1..bodyY2）留成透明 —— 拖亮度滑条时要能透过它看到世界里的实时预览。
     * 用四块不重叠矩形绕出这个方洞：洞上方整幅、洞下方整幅、洞左侧、洞右侧（均在 body 纵向区间内）。
     * 外框线与内容随后绘制，盖在这层底之上。
     */
    private void drawBackground() {
        int bg = argb(COLOR_BG);
        drawRect(0, 0, width, bodyY1, bg);
        drawRect(0, bodyY2, width, height, bg);
        drawRect(0, bodyY1, detX1, bodyY2, bg);
        drawRect(detX2, bodyY1, width, bodyY2, bg);
    }

    /**
     * 外框与所有共用线。相邻元素之间只画一根：外框四边各 1px，tab 条／底栏与内容区各共用一根横线，
     * 三栏之间两根竖线（工具条两个按钮之间的那根由 FlatButton 自己按描边掩码画）。
     */
    private void drawChrome() {
        int line = argb(COLOR_BORDER);
        drawRect(frameX1, frameY1, frameX2, frameY1 + 1, line);
        drawRect(frameX1, frameY2 - 1, frameX2, frameY2, line);
        drawRect(frameX1, frameY1, frameX1 + 1, frameY2, line);
        drawRect(frameX2 - 1, frameY1, frameX2, frameY2, line);
        drawRect(bodyX1, tabLineY, bodyX2, tabLineY + 1, line);
        drawRect(bodyX1, actLineY, bodyX2, actLineY + 1, line);
        drawRect(areaX2, bodyY1, areaX2 + 1, bodyY2, line);
        drawRect(effectX2, bodyY1, effectX2 + 1, bodyY2, line);
        // 翻动按钮与 tab 之间的共用竖线：不需要翻动时这两条线也不该在
        if (tabOverflow) {
            drawRect(tabAreaX1, tabY1, tabAreaX1 + 1, tabLineY, line);
            drawRect(tabAreaX2 - 1, tabY1, tabAreaX2, tabLineY, line);
        }
    }

    /** 维度 tab 条：按实宽排布，超出可见宽度时由两侧翻动按钮平移。 */
    private void drawTabs(int mouseX, int mouseY) {
        boolean inStrip = mouseY >= tabY1 && mouseY < tabLineY;
        int x = tabViewX1 - tabScroll;
        for (int i = 0; i < dims.size() && i < tabW.size(); i++) {
            int w = tabW.get(i);
            int x2 = x + w;
            if (x2 > tabViewX1 && x < tabViewX2) {
                // 部分露出的 tab 需要切齐，否则会压到翻动按钮上
                enableClip(tabViewX1, tabY1, tabViewX2, tabLineY);
                boolean sel = i == dimIdx;
                boolean hov = !sel && inStrip
                        && mouseX >= Math.max(x, tabViewX1) && mouseX < Math.min(x2, tabViewX2);
                if (sel) {
                    drawRect(x, tabY1, x2, tabLineY, argb(COLOR_SELECTED));
                } else if (hov) {
                    drawRect(x, tabY1, x2, tabLineY, argb(COLOR_HOVER_ROW));
                }
                // 每个 tab 各画自己右边界那根竖线：相邻 tab 仍共用一根（下一个不画左边），
                // 最右 tab 因此在自身右缘收口
                drawRect(x2 - 1, tabY1, x2, tabLineY, argb(COLOR_BORDER));
                drawCenteredString(fontRendererObj, tabLabel(dims.get(i)), (x + x2) / 2, tabY1 + 5,
                        sel ? COLOR_TEXT_HEAD : COLOR_TEXT_BODY);
                disableClip();
            }
            x = x2;
        }
    }

    /** 区域列表：{@code #id} 右对齐成列，备注左对齐紧跟其后。 */
    private void drawAreaList(int mouseX, int mouseY) {
        if (areas.isEmpty()) {
            drawCenteredString(fontRendererObj, translate("gui.areaeffect.manager.empty"),
                    (areaX1 + areaX2) / 2, (bodyY1 + bodyY2) / 2 - 4, COLOR_TEXT_HINT);
            return;
        }
        int idW = idColumnWidth();
        int listDim = currentDim();
        for (int i = 0; i < areas.size(); i++) {
            int y = areaCol.rowY(i);
            if (y < bodyY1 || y + ROW_H > bodyY2) {
                continue;
            }
            Area area = areas.get(i);
            boolean sel = selected != null && area.id == selected.id;
            boolean hov = !sel && mouseX >= areaX1 && mouseX < areaX2 && mouseY >= y && mouseY < y + ROW_H;
            if (sel) {
                drawRect(areaX1, y, areaX2, y + ROW_H, argb(COLOR_SELECTED));
            } else if (hov) {
                drawRect(areaX1, y, areaX2, y + ROW_H, argb(COLOR_HOVER_ROW));
            }
            String id = "#" + area.id;
            int idX = areaX1 + 6 + idW - fontRendererObj.getStringWidth(id);
            fontRendererObj.drawStringWithShadow(id, idX, y + 5, sel ? COLOR_TEXT_HEAD : COLOR_TEXT_BODY);
            // 备注：紧跟在编号列之后左对齐；超出可用宽度截成省略号（右侧给滚动条留位）
            int remarkX = areaX1 + 6 + idW + 4;
            int avail = (areaX2 - ScrollColumn.BAR_W - 4) - remarkX;
            String remark = listDim >= 0 ? proxy.getEffectiveRemark(listDim, area) : "";
            if (!remark.isEmpty()) {
                String shown = trim(remark, avail);
                if (!shown.isEmpty()) {
                    fontRendererObj.drawStringWithShadow(shown, remarkX, y + 5, COLOR_ACCENT);
                }
            }
        }
    }

    /** 编号列宽度：按本列表最宽的 {@code #id} 实测，让所有备注从同一个 x 起。 */
    private int idColumnWidth() {
        int w = 0;
        for (Area area : areas) {
            w = Math.max(w, fontRendererObj.getStringWidth("#" + area.id));
        }
        return w + 1;
    }

    /** 效果列表：左名称 + 右权重。 */
    private void drawEffectList(int mouseX, int mouseY) {
        if (pendingEffects == null) {
            return;
        }
        for (int i = 0; i < pendingEffects.size(); i++) {
            int y = effectCol.rowY(i);
            if (y < effectTop || y + ROW_H > bodyY2) {
                continue;
            }
            AreaEffect effect = pendingEffects.get(i);
            boolean sel = i == effectIdx;
            boolean hov = !sel && mouseX >= effectX1 && mouseX < effectX2 && mouseY >= y && mouseY < y + ROW_H;
            if (sel) {
                drawRect(effectX1, y, effectX2, y + ROW_H, argb(COLOR_SELECTED));
            } else if (hov) {
                drawRect(effectX1, y, effectX2, y + ROW_H, argb(COLOR_HOVER_ROW));
            }
            String name = translate("gui.areaeffect.effect." + effect.typeId());
            fontRendererObj.drawStringWithShadow(name, effectX1 + 6, y + 5, sel ? COLOR_TEXT_HEAD : COLOR_TEXT_BODY);
            String weight = "W" + fmt(effect.getWeight());
            fontRendererObj.drawStringWithShadow(weight,
                    effectX2 - fontRendererObj.getStringWidth(weight) - (ScrollColumn.BAR_W + 10),
                    y + 5, COLOR_ACCENT);
        }
        if (pendingEffects.isEmpty() && selected != null) {
            drawCenteredString(fontRendererObj, translate("gui.areaeffect.manager.noeffect"),
                    (effectX1 + effectX2) / 2, (effectTop + bodyY2) / 2 - 4, COLOR_TEXT_HINT);
        }
    }

    /**
     * 详情栏：只服务效果（L3/L4）。栏内不含任何区域级内容，也不再自报"是什么效果"——
     * 效果列表的选中行已表明它是什么效果，作用的区域则由左栏选中行 + 底栏 {@code #id} 指示。
     * 因此这里只剩空态提示要画，四条滑条由 GuiScreen#drawScreen 统一绘制；
     * 滑条编辑的提交也在这里触发（1.7.10 的滑条靠 drawButton 自驱，没有独立变更回调）。
     */
    private void drawDetail() {
        if (selected == null) {
            drawCenteredString(fontRendererObj, translate("gui.areaeffect.manager.noselect"),
                    (detX1 + detX2) / 2, (bodyY1 + bodyY2) / 2 - 4, COLOR_TEXT_HINT);
            return;
        }
        if (selectedEffect() == null) {
            drawCenteredString(fontRendererObj, translate("gui.areaeffect.manager.noeffect"),
                    (detX1 + detX2) / 2, (bodyY1 + bodyY2) / 2 - 4, COLOR_TEXT_HINT);
            return;
        }
        commitSliderEdits();
    }

    /**
     * 底栏「区域操作条」：区域级（L2）的元素全部在这里 —— #id·形状名、备注编辑、未保存圆点，
     * 以及四个图标按钮（删除 / 传送 / 线框 / 保存，由 buttonList 绘制）。
     */
    private void drawActionBar(int mouseX, int mouseY) {
        Area area = selected;
        String cid = area == null ? translate("gui.areaeffect.edit.noselect")
                : "#" + area.id + " · " + shapeName(area);
        String shownCid = trim(cid, cidW - 2);
        if (!shownCid.isEmpty()) {
            fontRendererObj.drawStringWithShadow(shownCid, cidX1, cidY, COLOR_TEXT_HEAD);
        }
        // 备注框由 vanilla GuiTextField 自己画（保留原版观感：黑底 + 灰描边）
        remarkField.drawTextBox();
        // 备注实时写覆盖表（列表行即时更新；预览不入共享 Area）；与基线不同即标记未保存
        if (area != null) {
            String text = remarkField.getText();
            if (!text.equals(remarkBaseline)) {
                dirty = true;
            }
            int dim = currentDim();
            if (dim >= 0 && !text.equals(proxy.getEffectiveRemark(dim, area))) {
                proxy.previewAreaRemark(dim, area.id, text);
            }
        }
        // 未保存改动指示：只有脏的时候才亮，位置常驻不跳
        if (dirty) {
            drawRect(dotX1, dotY, dotX1 + 6, dotY + 6, argb(COLOR_SLIDER_THUMB_HOT));
        }
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

    /** 裁剪：部分露出的 tab 需要切齐到 tab 内容区（1.7.10 没有现成裁剪，手动开 scissor）。 */
    private void enableClip(int x1, int y1, int x2, int y2) {
        ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int s = sr.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x1 * s, mc.displayHeight - y2 * s, (x2 - x1) * s, (y2 - y1) * s);
    }

    private void disableClip() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (remarkField != null) {
            remarkField.updateCursorCounter();
        }
    }

    // ===================== 输入 =====================

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
        // 备注框只在选中区域时接受点击：GuiTextField 自身不看 enabled，必须在这里把关
        if (mouseButton == 0 && selected != null) {
            remarkField.mouseClicked(mouseX, mouseY, mouseButton);
        }
        if (mouseButton != 0) {
            return;
        }
        // 滚动条：命中滑块开始拖拽；命中轨道按位置跳转
        if (areaCol.press(mouseX, mouseY, areas.size())) {
            return;
        }
        if (effectCol.press(mouseX, mouseY, pendingEffects == null ? 0 : pendingEffects.size())) {
            return;
        }
        // 维度 tab 条（翻动按钮由 buttonList 处理，不在这个区间内）
        if (mouseY >= tabY1 && mouseY < tabLineY) {
            if (mouseX >= tabViewX1 && mouseX < tabViewX2) {
                int idx = tabIndexAt(mouseX - tabViewX1 + tabScroll);
                if (idx >= 0 && idx < dims.size() && idx != dimIdx) {
                    selectDim(idx);
                }
            }
            return;
        }
        // 区域列表（L2 选择）
        if (mouseX >= areaX1 && mouseX < areaX2 && mouseY >= bodyY1 && mouseY < bodyY2) {
            int idx = (mouseY - bodyY1 - 4 + areaCol.scroll()) / ROW_H;
            if (idx >= 0 && idx < areas.size()) {
                Area area = areas.get(idx);
                if (selected == null || area.id != selected.id) {
                    selectArea(area);
                }
            }
            return;
        }
        // 效果列表（L3 选择，避开顶部工具条）
        if (pendingEffects != null && mouseX >= effectX1 && mouseX < effectX2
                && mouseY >= effectTop && mouseY < bodyY2) {
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
        areaCol.drag(mouseY, areas.size());
        effectCol.drag(mouseY, pendingEffects == null ? 0 : pendingEffects.size());
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int state) {
        super.mouseMovedOrUp(mouseX, mouseY, state);
        if (state == 0) {
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
        int y = getMouseY();
        // 鼠标在 tab 条上：横向翻动 tab；其余按所在栏纵向滚动
        if (y >= tabY1 && y < tabLineY) {
            if (tabOverflow) {
                tabScroll = clampInt(tabScroll + rows * navStep(), 0, maxTabScroll());
                syncTabNav();
            }
            return;
        }
        if (x >= areaX1 && x < areaX2) {
            areaCol.wheel(rows, areas.size());
        } else if (x >= effectX1 && x < effectX2) {
            effectCol.wheel(rows, pendingEffects == null ? 0 : pendingEffects.size());
        }
    }

    /** 翻动步长：一次挪一个 tab 宽（tab 宽度已按文字实测，故这里取首个 tab 的实宽）。 */
    private int navStep() {
        return tabW.isEmpty() ? 48 : tabW.get(0);
    }

    private int getMouseX() {
        return Mouse.getEventX() * width / mc.displayWidth;
    }

    private int getMouseY() {
        return height - Mouse.getEventY() * height / mc.displayHeight - 1;
    }

    // ===================== 动作 =====================

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BTN_NAV_L) {
            tabScroll = clampInt(tabScroll - navStep(), 0, maxTabScroll());
            syncTabNav();
            return;
        }
        if (button.id == BTN_NAV_R) {
            tabScroll = clampInt(tabScroll + navStep(), 0, maxTabScroll());
            syncTabNav();
            return;
        }
        if (button.id == BTN_DELETE || button.id == BTN_TP || button.id == BTN_SEL) {
            doAreaAction(button.id);
            return;
        }
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
            proxy.sendSetProps(dim, selected.id, remark, new ArrayList<AreaEffect>(pendingEffects));
            // 保存即视为已提交：推进基线、清脏标记，避免下一帧又判定为未保存
            remarkBaseline = remark;
            dirty = false;
            // 保留预览覆盖避免渲染跳变，服务端广播返回后共享即为新值；切换/关闭时统一清除
            proxy.previewAreaProps(dim, selected.id, effectiveLightness(), effectiveDuration());
            proxy.previewAreaRemark(dim, selected.id, remark);
            syncDetailWidgets();
        }
    }

    /** 底栏三个区域动作的分发（传送 / 删除 / 线框开关），作用于当前 {@link #selected}。 */
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
            // 字形要立刻反映新的可见态
            syncDetailWidgets();
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
        AreaEffect effect = selectedEffect();
        if (effect == null) {
            return;
        }
        effect.setTimeMode((effect.getTimeMode() + 1) % 3);
        dirty = true;
        syncDetailWidgets();
    }

    // ===================== 滑条提交 =====================

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

    // ===================== 小工具 =====================

    /** 形状紧凑名（如"长方体"）：底栏 #id 后面只放得下类型名，完整范围由悬停提示补。 */
    private static String shapeName(Area area) {
        return translate("gui.areaeffect.shape." + area.shape().typeId());
    }

    /** 文本按可用宽度裁成省略号形式；宽度不足时返回空串（调用方据此跳过绘制）。 */
    private String trim(String text, int avail) {
        if (avail <= 8) {
            return "";
        }
        String shown = fontRendererObj.trimStringToWidth(text, avail);
        if (!shown.equals(text)) {
            shown = fontRendererObj.trimStringToWidth(text, avail - 5) + "..";
        }
        return shown;
    }

    private static int clampInt(int value, int min, int max) {
        return value < min ? min : Math.min(value, max);
    }

    /**
     * 时段控件的按钮文案。用短名（时段 游戏）而不是全名（生效时段: 游戏时间）—— 全名实测 186px，
     * 会把详情栏的宽度下限锁在 46%，右栏也就缩不下来；全名在语言文件里仍保留给后续需要处。
     */
    private String timeModeLabel(int mode) {
        String name;
        if (mode == AreaEffect.TIME_GAME) {
            name = translate("gui.areaeffect.edit.time.game");
        } else if (mode == AreaEffect.TIME_REAL) {
            name = translate("gui.areaeffect.edit.time.real");
        } else {
            name = translate("gui.areaeffect.edit.time.always");
        }
        return translate("gui.areaeffect.edit.time") + " " + name;
    }

    private String tabLabel(int dim) {
        return StatCollector.translateToLocalFormatted("gui.areaeffect.manager.dim", dim);
    }

    private static String translate(String key) {
        return StatCollector.translateToLocal(key);
    }

    private static String fmt(float v) {
        return String.format("%.0f", (double) v);
    }
}
