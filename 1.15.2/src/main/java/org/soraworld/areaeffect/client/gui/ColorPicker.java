package org.soraworld.areaeffect.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.renderer.BufferBuilder;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.I18n;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.util.Locale;

import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_BORDER;
import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_MODAL_BG;
import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_TEXT_HINT;
import static org.soraworld.areaeffect.client.gui.GuiTheme.argb;

/**
 * 颜色选择器（模态弹窗控件）：与一个 {@code 0xRRGGBBAA} 的 int 颜色绑定，自绘、自驱动输入。
 *
 * <p>版式（自左向右，对应「图」）：
 * <ul>
 *   <li><b>SV 平面</b>：120×120 的饱和度-明度平面（横轴饱和度、纵轴明度，上亮下暗），
 *       四角逐顶点着色靠 GL_SMOOTH 硬件插值；圆点指示当前位置。</li>
 *   <li><b>色相条</b>：纵向彩虹渐变（六段），改色相；游标横条填充当前色。</li>
 *   <li><b>透明度条</b>：棋盘格底 + 当前色自上而下不透明→全透明的渐变，直观看出 alpha。</li>
 *   <li><b>右列</b>：预览色块（棋盘格底）、HEX 输入框（#RRGGBBAA）、RGBA 四个数字输入框、确定/取消。</li>
 * </ul>
 *
 * <p>交互模型：{@code color} 是唯一事实来源，其它表示（HSV/HEX/RGBA/平面位置）全部由它反解，
 * 任何一处改动都写回它再刷新其它表示 —— 不会出现「HEX 与 RGBA 对不上」的失同步。
 * 所有改动（拖平面/拖条、键入）都<b>实时</b>经 {@link Listener#onColorChanged} 通知宿主（预览即时生效）；
 * 弹窗关闭时按结果回调：{@link Listener#onConfirmed}（携带最终颜色）或 {@link Listener#onCanceled}
 * （此前已以初始色回调一次 {@link Listener#onColorChanged} 还原）。
 *
 * <p>宿主职责：把 {@code keyPressed/charTyped/mouseClicked/mouseDragged/mouseReleased/tick} 转发进来
 * （模态 —— 打开期间宿主应消费一切输入），并在最后调用 {@link #drawScreen}。本控件不依赖宿主具体类型。
 *
 * <p>1.13 的输入模型变化：键盘输入拆成 {@code keyPressed(键码,扫描码,修饰键)} 与
 * {@code charTyped(字符,修饰键)} 两条通道（1.12 只有一个 {@code keyTyped(字符,键码)}），
 * 因此"控制键"（Esc / 退格 / 方向键 / Ctrl 组合）走前者、可打印字符走后者；
 * TextFieldWidget 的对应入口也随之变成 {@code keyPressed}/{@code charTyped}，
 * 光标计时从 {@code updateCursorCounter()} 改名为 {@code tick()}，绘制从
 * {@code drawTextBox()} 改名为 {@code drawTextField(mouseX, mouseY, partialTicks)}。
 */
final class ColorPicker extends AbstractGui {

    /**
     * 结果回调：颜色变化实时报告；弹窗关闭时按「确定 / 取消」分别报告一次。
     *
     * <p>调用顺序约定：
     * <ul>
     *   <li><b>颜色</b>：{@link #onColorChanged} 在每次拖动/键入后立即触发（宿主可实时预览）；</li>
     *   <li><b>确定</b>（确定按钮 / Enter）：{@link #onConfirmed} 携带最终颜色触发，随后弹窗关闭；</li>
     *   <li><b>取消</b>（取消按钮 / Esc）：先以初始色触发一次 {@link #onColorChanged}
     *       还原，再触发 {@link #onCanceled}，随后弹窗关闭。</li>
     * </ul>
     */
    interface Listener {

        /** 颜色实时变化（拖平面/拖条、键入 HEX/RGBA）。 */
        void onColorChanged(int rgba);

        /** 点「确定」/按 Enter：携带最终颜色（此前已实时回调过），弹窗随即关闭。 */
        void onConfirmed(int rgba);

        /** 点「取消」/按 Esc：颜色已还原为打开前的值，弹窗随即关闭。 */
        void onCanceled();
    }

    private static final int NONE = 0;
    private static final int PLANE = 1;
    private static final int HUE = 2;
    private static final int ALPHA = 3;

    // ===================== 布局常量 =====================
    private static final int PAD = 12;
    private static final int PLANE_SIZE = 120;
    private static final int PLANE_GAP = 8;
    /** 竖条宽度：取 6 的倍数（12），使透明度条 12×120 的棋盘格能整格铺满。 */
    private static final int SLIDER_W = 12;
    private static final int SLIDER_GAP = 4;
    private static final int RIGHT_GAP = 12;
    private static final int RIGHT_W = 108;
    /** 右列输入框/色块/按钮的统一宽度（右列右边 12px 留给标签列）。 */
    private static final int FIELD_W = RIGHT_W - 12;
    private static final int SWATCH_H = 18;
    private static final int FIELD_H = 14;
    private static final int FIELD_PITCH = 18;
    private static final int BUTTON_H = 18;
    /** 透明度条 / 预览色块棋盘格的边长；两者宽高都是它的整数倍。 */
    private static final int CHECKER_CELL = 6;
    /** 右列高度 = 色块 + HEX 标签/输入框 + RGBA 四行 + 按钮行。 */
    private static final int PANEL_H = PAD + SWATCH_H + 8 + 10 + FIELD_H + 8 + FIELD_PITCH * 3 + FIELD_H + 8 + BUTTON_H + PAD;
    private static final int PANEL_W = PAD + PLANE_SIZE + PLANE_GAP + SLIDER_W + SLIDER_GAP + SLIDER_W + RIGHT_GAP + RIGHT_W + PAD;

    private static final String[] CH_LABELS = {"R", "G", "B", "A"};
    /** RGBA 四个通道在 {@code 0xRRGGBBAA} 里的位移。 */
    private static final int[] CH_SHIFT = {24, 16, 8, 0};

    /** 六段彩虹端点（红→黄→绿→青→蓝→品红→红），色相条纵向复用。 */
    private static final int[] HUE_STOPS = buildHueStops();

    private static int[] buildHueStops() {
        int[] stops = new int[7];
        for (int i = 0; i < stops.length; i++) {
            stops[i] = ColorUtil.hsvToRgb(i * 60.0F, 1.0F, 1.0F);
        }
        return stops;
    }

    private final Listener listener;

    /** 当前颜色 {@code 0xRRGGBBAA}，唯一事实来源。 */
    private int color;
    /** 打开时快照，取消时还原。 */
    private int initialColor;
    /** 由 color 反解的 HSV 与 alpha（绘制与交互直接用，不另存一份真值）。 */
    private float hue;
    private float sat;
    private float val;
    private int alpha;

    private boolean open;
    private int drag = NONE;
    private int overlayW;
    private int overlayH;

    // 几何（open 时按屏幕居中计算）
    private int x1, y1, x2, y2;
    private int planeX1, planeY1, planeX2, planeY2;
    private int hueX, alphaX, sliderY1;
    private int rightX, swatchY, hexLabelY, hexFieldY, chanY0, buttonY;

    private TextFieldWidget hexField;
    private final TextFieldWidget[] channels = new TextFieldWidget[4];
    private AefButton okBtn;
    private AefButton cancelBtn;

    ColorPicker(Listener listener) {
        this.listener = listener;
    }

    // ===================== 生命周期 =====================

    /** 打开（模态）：按屏幕尺寸居中布局，快照初始色并回填所有表示。 */
    void open(Minecraft mc, int screenW, int screenH, int rgba) {
        overlayW = screenW;
        overlayH = screenH;
        x1 = Math.max(0, (screenW - PANEL_W) / 2);
        y1 = Math.max(0, (screenH - PANEL_H) / 2);
        x2 = x1 + PANEL_W;
        y2 = y1 + PANEL_H;
        planeX1 = x1 + PAD;
        planeY1 = y1 + PAD;
        planeX2 = planeX1 + PLANE_SIZE;
        planeY2 = planeY1 + PLANE_SIZE;
        hueX = x1 + PAD + PLANE_SIZE + PLANE_GAP;
        alphaX = hueX + SLIDER_W + SLIDER_GAP;
        sliderY1 = planeY1;
        rightX = alphaX + SLIDER_W + RIGHT_GAP;
        swatchY = y1 + PAD;
        hexLabelY = swatchY + SWATCH_H + 8;
        hexFieldY = hexLabelY + 10;
        chanY0 = hexFieldY + FIELD_H + 8;
        buttonY = y1 + PANEL_H - BUTTON_H - PAD;

        // 每次打开重建输入框与按钮：不残留上次的焦点/文本编辑状态
        // 输入框/色块/按钮统一从 rightX + 12 起、宽 FIELD_W，右对齐成列
        hexField = new TextFieldWidget(mc.fontRenderer, rightX + 12, hexFieldY, FIELD_W, FIELD_H, "");
        hexField.setMaxStringLength(9);
        for (int i = 0; i < 4; i++) {
            channels[i] = new TextFieldWidget(mc.fontRenderer, rightX + 12,
                    chanY0 + i * FIELD_PITCH, FIELD_W, FIELD_H, "");
            channels[i].setMaxStringLength(3);
        }
        // 确定/取消：两个按钮在弹窗整体宽度内水平居中（不是贴右列）
        int okW = (FIELD_W - 4) / 2;
        int pairX = x1 + (PANEL_W - okW * 2 - 4) / 2;
        okBtn = new FlatButton(0, pairX, buttonY, okW, BUTTON_H, translate("gui.areaeffect.color.ok"));
        cancelBtn = new FlatButton(1, pairX + okW + 4, buttonY, okW, BUTTON_H, translate("gui.areaeffect.color.cancel"));

        open = true;
        drag = NONE;
        initialColor = rgba;
        applyColor(rgba);
        syncFields(null);
    }

    boolean isOpen() {
        return open;
    }

    // ===================== 输入 =====================

    boolean mousePressed(int mx, int my, int button) {
        if (!open) {
            return false;
        }
        // 模态：非左键一律吞掉
        if (button != 0) {
            return true;
        }
        // 面板外点击：吞掉事件、不做任何操作（取色器是模态窗口，误点不该丢已选颜色）
        if (mx < x1 || mx >= x2 || my < y1 || my >= y2) {
            return true;
        }
        if (hit(okBtn, mx, my)) {
            onOk();
            return true;
        }
        if (hit(cancelBtn, mx, my)) {
            onCancel();
            return true;
        }
        // 输入框：先统一失焦，再让被点击者获得焦点 —— 与 vanilla 一致，同一时刻只有一根光标
        for (TextFieldWidget f : allFields()) {
            if (overField(f, mx, my)) {
                for (TextFieldWidget g : allFields()) {
                    g.setFocused2(false);
                }
                f.mouseClicked(mx, my, button);
                return true;
            }
        }
        // 其余面板区域：输入框失焦
        for (TextFieldWidget f : allFields()) {
            f.setFocused2(false);
        }
        // 平面 / 色相条 / 透明度条（区域互不重叠，按几何判定）
        // 平面命中区外扩 1px：右下角与底边线（v=0/s=1）也要能直接点到
        if (mx >= planeX1 && mx < planeX2 + 1 && my >= planeY1 && my < planeY2 + 1) {
            drag = PLANE;
            planeFromMouse(mx, my);
            return true;
        }
        if (mx >= hueX && mx < hueX + SLIDER_W && my >= sliderY1 && my < sliderY1 + PLANE_SIZE) {
            drag = HUE;
            hueFromMouse(my);
            return true;
        }
        if (mx >= alphaX && mx < alphaX + SLIDER_W && my >= sliderY1 && my < sliderY1 + PLANE_SIZE) {
            drag = ALPHA;
            alphaFromMouse(my);
            return true;
        }
        return true;
    }

    void mouseDragged(int mx, int my) {
        if (!open) {
            return;
        }
        switch (drag) {
            case PLANE:
                planeFromMouse(mx, my);
                break;
            case HUE:
                hueFromMouse(my);
                break;
            case ALPHA:
                alphaFromMouse(my);
                break;
            default:
                break;
        }
    }

    void mouseReleased() {
        drag = NONE;
    }

    /**
     * 控制键通道（1.13 新增）：Esc 取消；其余按键交给聚焦的输入框
     * （退格 / 删除 / 方向键 / Home / End 以及 Ctrl+A/C/V/X 都由 TextFieldWidget 自己处理）。
     */
    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!open) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            // Esc 取消并关闭；其它控制键不再绑定任何操作
            onCancel();
            return true;
        }
        for (TextFieldWidget f : allFields()) {
            if (f.isFocused()) {
                f.keyPressed(keyCode, scanCode, modifiers);
                afterFieldEdit(f);
                return true;
            }
        }
        return true;
    }

    /** 字符通道（1.13 新增）：只有白名单字符会进入聚焦的输入框；未聚焦时也吞掉（模态）。 */
    boolean charTyped(char typedChar, int modifiers) {
        if (!open) {
            return false;
        }
        for (TextFieldWidget f : allFields()) {
            if (f.isFocused()) {
                if (acceptChar(f, typedChar)) {
                    f.charTyped(typedChar, modifiers);
                    afterFieldEdit(f);
                }
                return true;
            }
        }
        return true;
    }

    void updateScreen() {
        if (!open) {
            return;
        }
        for (TextFieldWidget f : allFields()) {
            f.tick();
        }
    }

    // ===================== 绘制 =====================

    void drawScreen(Minecraft mc, int mouseX, int mouseY) {
        if (!open) {
            return;
        }
        // 全屏半透明遮罩：取色器是模态弹窗，盖住底层界面
        fill(0, 0, overlayW, overlayH, 0x66000000);
        // 面板（不透明底，避免透出底层界面干扰取色）
        fill(x1, y1, x2, y2, argb(COLOR_MODAL_BG));
        fill(x1, y1, x2, y1 + 1, argb(COLOR_BORDER));
        fill(x1, y2 - 1, x2, y2, argb(COLOR_BORDER));
        fill(x1, y1, x1 + 1, y2, argb(COLOR_BORDER));
        fill(x2 - 1, y1, x2, y2, argb(COLOR_BORDER));

        drawPlane();
        drawHueBar();
        drawAlphaBar();
        drawSwatch();

        // HEX / RGBA 输入区：标签右对齐到同一列（rightX + 10），并与各自输入框垂直居中
        mc.fontRenderer.drawStringWithShadow("HEX", rightX + 10 - mc.fontRenderer.getStringWidth("HEX"),
                hexFieldY + 3, COLOR_TEXT_HINT);
        hexField.render(mouseX, mouseY, 0.0F);
        for (int i = 0; i < 4; i++) {
            int labelW = mc.fontRenderer.getStringWidth(CH_LABELS[i]);
            mc.fontRenderer.drawStringWithShadow(CH_LABELS[i], rightX + 10 - labelW,
                    chanY0 + i * FIELD_PITCH + 3, COLOR_TEXT_HINT);
            channels[i].render(mouseX, mouseY, 0.0F);
        }
        okBtn.render(mouseX, mouseY, 0.0F);
        cancelBtn.render(mouseX, mouseY, 0.0F);
    }

    /**
     * SV 平面：四角逐顶点着色（左上白 → 右上纯色相，下两角黑），圆点指示当前位置。
     * GL 状态与顶点流写法同 1.12，仅把裸 {@code GL11} 状态改写换成 {@link GlStateManager}
     * （与 {@code Gui.drawRect} 共用同一套状态缓存）。
     */
    private void drawPlane() {
        int pure = ColorUtil.hsvToRgb(hue, 1.0F, 1.0F);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        GlStateManager.enableBlend();
        GlStateManager.disableTexture();
        GlStateManager.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buffer = tess.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        setVertex(buffer, 0);            // 左下黑
        buffer.pos(planeX1, planeY2, 0.0D).endVertex();
        setVertex(buffer, 0);            // 右下黑
        buffer.pos(planeX2, planeY2, 0.0D).endVertex();
        setVertex(buffer, pure);         // 右上纯色相
        buffer.pos(planeX2, planeY1, 0.0D).endVertex();
        setVertex(buffer, 0xFFFFFF);     // 左上白
        buffer.pos(planeX1, planeY1, 0.0D).endVertex();
        tess.draw();
        GlStateManager.enableTexture();
        GlStateManager.disableBlend();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        border(planeX1 - 1, planeY1 - 1, planeX2 + 1, planeY2 + 1);
        // 选择指示：准星式光标（4 条白色线段，中心留空露出底下颜色）。
        // 位置映射到平面内最后一像素（PLANE_SIZE-1），使 v=0/s=1 也能落在右下角
        int cx = planeX1 + Math.round(sat * (PLANE_SIZE - 1));
        int cy = planeY1 + Math.round((1.0F - val) * (PLANE_SIZE - 1));
        drawCrosshair(cx, cy);
    }

    /** 色相条：纵向彩虹 + 矩形线框游标（无悬停效果）。 */
    private void drawHueBar() {
        int hy2 = sliderY1 + PLANE_SIZE;
        drawVertGradient(hueX, sliderY1, hueX + SLIDER_W, hy2, HUE_STOPS);
        border(hueX - 1, sliderY1 - 1, hueX + SLIDER_W + 1, hy2 + 1);
        int ty = clamp(sliderY1 + Math.round(hue / 360.0F * PLANE_SIZE) - 4, sliderY1 - 2, hy2 - 6);
        drawThumb(hueX, ty);
    }

    /** 透明度条：棋盘格底 + 当前色自顶不透明→自底全透明的渐变；矩形线框游标。 */
    private void drawAlphaBar() {
        int hy2 = sliderY1 + PLANE_SIZE;
        ColorUtil.drawCheckerboard(alphaX, sliderY1, alphaX + SLIDER_W, hy2, CHECKER_CELL);
        drawAlphaGradient(alphaX, sliderY1, alphaX + SLIDER_W, hy2, (color >>> 8) & 0xFFFFFF);
        border(alphaX - 1, sliderY1 - 1, alphaX + SLIDER_W + 1, hy2 + 1);
        int ty = clamp(sliderY1 + Math.round((1.0F - alpha / 255.0F) * PLANE_SIZE) - 4, sliderY1 - 2, hy2 - 6);
        drawThumb(alphaX, ty);
    }

    /** 游标指示（色相/透明度条）：白色矩形线框（1px 边框，高度约 5px），左右对齐竖条边缘、对称。 */
    private void drawThumb(int sx, int ty) {
        int cy = ty + 4;
        int hh = 2;                   // 半高：总高约 5px
        int x1 = sx;
        int x2 = sx + SLIDER_W;
        int y1 = cy - hh;
        int y2 = cy + hh + 1;
        fill(x1, y1, x2, y1 + 1, 0xFFFFFFFF);       // 上边
        fill(x1, y2 - 1, x2, y2, 0xFFFFFFFF);       // 下边
        fill(x1, y1, x1 + 1, y2, 0xFFFFFFFF);       // 左边
        fill(x2 - 1, y1, x2, y2, 0xFFFFFFFF);       // 右边
    }

    /**
     * 准星式选择光标：4 条等长白色线段（枪械准星样式，1px 宽），中心留空露出底下平面颜色。
     * 每条线段长 7px、距中心 2px。
     */
    private static void drawCrosshair(int cx, int cy) {
        int len = 7;
        int gap = 2;
        fill(cx - len, cy, cx - gap, cy + 1, 0xFFFFFFFF);      // 左
        fill(cx + gap, cy, cx + len + 1, cy + 1, 0xFFFFFFFF);  // 右
        fill(cx, cy - len, cx + 1, cy - gap, 0xFFFFFFFF);      // 上
        fill(cx, cy + gap, cx + 1, cy + len + 1, 0xFFFFFFFF);  // 下
    }

    /** 预览色块：棋盘格底 + 当前颜色（与输入框同宽），实时反映 alpha。 */
    private void drawSwatch() {
        ColorUtil.drawCheckerboard(rightX + 12, swatchY, rightX + 12 + FIELD_W, swatchY + SWATCH_H, CHECKER_CELL);
        fill(rightX + 12, swatchY, rightX + 12 + FIELD_W, swatchY + SWATCH_H, argb(color));
        border(rightX + 12, swatchY, rightX + 12 + FIELD_W, swatchY + SWATCH_H);
    }

    /** 1px 描边框（{@code x1,y1} 为外接矩形左上，{@code x2,y2} 为右下）。 */
    private void border(int bx1, int by1, int bx2, int by2) {
        int line = argb(COLOR_BORDER);
        fill(bx1, by1, bx2, by1 + 1, line);
        fill(bx1, by2 - 1, bx2, by2, line);
        fill(bx1, by1, bx1 + 1, by2, line);
        fill(bx2 - 1, by1, bx2, by2, line);
    }

    /** 纵向渐变（沿 y，每行同色）：{@code stops} 为 0xRRGGBB 端点色，顶部 = stops[0]。 */
    private static void drawVertGradient(int gx1, int gy1, int gx2, int gy2, int[] stops) {
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        GlStateManager.enableBlend();
        GlStateManager.disableTexture();
        GlStateManager.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buffer = tess.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < stops.length - 1; i++) {
            int yTop = gy1 + (gy2 - gy1) * i / (stops.length - 1);
            int yBot = gy1 + (gy2 - gy1) * (i + 1) / (stops.length - 1);
            // 顶点顺序与 Gui.drawRect 一致（左下→右下→右上→左上）：GUI 渲染时 GL_CULL_FACE
            // 可能处于开启（本 mod 详情栏透明、世界在 GUI 之后仍可见），反向绕序会被剔除成黑。
            setVertex(buffer, stops[i + 1]);
            buffer.pos(gx1, yBot, 0.0D).endVertex();
            buffer.pos(gx2, yBot, 0.0D).endVertex();
            setVertex(buffer, stops[i]);
            buffer.pos(gx2, yTop, 0.0D).endVertex();
            buffer.pos(gx1, yTop, 0.0D).endVertex();
        }
        tess.draw();
        GlStateManager.enableTexture();
        GlStateManager.disableBlend();
        GlStateManager.shadeModel(GL11.GL_FLAT);
    }

    /** 纵向透明度渐变：顶部不透明、底部全透明（叠在棋盘格上）。 */
    private static void drawAlphaGradient(int gx1, int gy1, int gx2, int gy2, int rgb) {
        int r = (rgb >>> 16) & 255;
        int g = (rgb >>> 8) & 255;
        int b = rgb & 255;
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        GlStateManager.enableBlend();
        GlStateManager.disableTexture();
        GlStateManager.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buffer = tess.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        // 顶点顺序同 Gui.drawRect（左下→右下→右上→左上），避免被 GL_CULL_FACE 剔除
        buffer.color(r, g, b, 0);       // 底行全透明
        buffer.pos(gx1, gy2, 0.0D).endVertex();
        buffer.pos(gx2, gy2, 0.0D).endVertex();
        buffer.color(r, g, b, 255);     // 顶行不透明
        buffer.pos(gx2, gy1, 0.0D).endVertex();
        buffer.pos(gx1, gy1, 0.0D).endVertex();
        tess.draw();
        GlStateManager.enableTexture();
        GlStateManager.disableBlend();
        GlStateManager.shadeModel(GL11.GL_FLAT);
    }

    private static void setVertex(BufferBuilder buffer, int rgb) {
        buffer.color((rgb >>> 16) & 255, (rgb >>> 8) & 255, rgb & 255, 255);
    }

    // ===================== 交互计算 =====================

    /**
     * 直接按颜色反解 HSV/alpha（不发回调）。用于 RGB 直输（HEX/RGBA 输入）与初始载入：
     * 色相取规范表示 0..360，故纯红反解为 0°；HSV 交互路径不走这里（见 {@link #updateFromInteraction}）。
     */
    private void applyColor(int rgba) {
        color = rgba;
        int rgb = (rgba >>> 8) & 0xFFFFFF;
        hue = ColorUtil.hueOf(rgb);
        sat = ColorUtil.satOf(rgb);
        val = ColorUtil.valOf(rgb);
        alpha = rgba & 0xFF;
    }

    /**
     * HSV 交互（拖平面 / 拖色相条 / 拖透明度条）改色：写入颜色并通知宿主。
     *
     * <p>不再从结果反解回各分量：颜色本就是由当前 h/s/v/alpha 字段算出来的，反解在退化点
     * （黑：s 无定义；灰：h 无定义；拖出平面底边时 v=0）会把用户刚选的分量冲掉 —— 比如
     * 拖到纯黑会令 satOf 返回 0，准星随即跳到最左（下角）。分量由各 fromMouse 直接维护。
     */
    private void updateFromInteraction(int rgba) {
        color = rgba;
        syncFields(null);
        if (listener != null) {
            listener.onColorChanged(color);
        }
    }

    private void planeFromMouse(int mx, int my) {
        // 映射到平面内最后一像素：拖到平面底边/右下角（含命中区外扩的 1px 边线）即得 v=0 / s=1
        float s = clamp01((mx - planeX1) / (float) (PLANE_SIZE - 1));
        float v = clamp01(1.0F - (my - planeY1) / (float) (PLANE_SIZE - 1));
        sat = s;
        val = v;
        updateFromInteraction((ColorUtil.hsvToRgb(hue, sat, val) << 8) | alpha);
    }

    private void hueFromMouse(int my) {
        hue = clamp01((my - sliderY1) / (float) PLANE_SIZE) * 360.0F;
        updateFromInteraction((ColorUtil.hsvToRgb(hue, sat, val) << 8) | alpha);
    }

    private void alphaFromMouse(int my) {
        float frac = clamp01((my - sliderY1) / (float) PLANE_SIZE);
        alpha = Math.round((1.0F - frac) * 255.0F);
        updateFromInteraction((ColorUtil.hsvToRgb(hue, sat, val) << 8) | alpha);
    }

    /** 输入框编辑后的收尾：解析并应用（只在合法时改色，避免半截输入污染）。 */
    private void afterFieldEdit(TextFieldWidget field) {
        if (field == hexField) {
            applyHexText();
        } else {
            for (int i = 0; i < 4; i++) {
                if (field == channels[i]) {
                    applyChannelText(field, CH_SHIFT[i]);
                    return;
                }
            }
        }
    }

    /** HEX 输入：{#RRGGBB} 或 {#RRGGBBAA}（6 位时保留原 alpha）。用 long 解析，避免 R≥0x80 时溢出。 */
    private void applyHexText() {
        String t = hexField.getText();
        if (t.startsWith("#")) {
            t = t.substring(1);
        }
        if (t.length() != 6 && t.length() != 8) {
            return;
        }
        long v;
        try {
            v = Long.parseLong(t, 16);
        } catch (NumberFormatException e) {
            return;
        }
        int rgba = t.length() == 8 ? (int) v : ((int) v << 8) | (color & 0xFF);
        applyColor(rgba);
        syncFields(hexField);
        if (listener != null) {
            listener.onColorChanged(color);
        }
    }

    /** RGBA 数字输入：仅 0..255 合法时应用。 */
    private void applyChannelText(TextFieldWidget field, int shift) {
        String t = field.getText();
        if (t.isEmpty()) {
            return;
        }
        int v;
        try {
            v = Integer.parseInt(t);
        } catch (NumberFormatException e) {
            return;
        }
        if (v < 0 || v > 255) {
            return;
        }
        int rgba = (color & ~(0xFF << shift)) | (v << shift);
        applyColor(rgba);
        syncFields(field);
        if (listener != null) {
            listener.onColorChanged(color);
        }
    }

    /** 刷新各输入框文本为当前颜色；{@code skip} 是正在被用户编辑的那个（不动它的光标）。 */
    private void syncFields(TextFieldWidget skip) {
        if (skip != hexField) {
            hexField.setText(String.format(Locale.ROOT, "#%08X", color));
        }
        for (int i = 0; i < 4; i++) {
            if (skip != channels[i]) {
                channels[i].setText(String.valueOf((color >>> CH_SHIFT[i]) & 0xFF));
            }
        }
    }

    /**
     * 是否放行该字符给输入框。
     *
     * <p>1.13 把键盘拆成"控制键 + 字符"两条通道：退格 / 方向键 / Home / End / Ctrl 组合
     * 都由 {@code keyPressed} 走 {@link TextFieldWidget#keyPressed} 自行处理，因此这里只剩
     * "本字段允许哪些可打印字符"这一件事。
     */
    private boolean acceptChar(TextFieldWidget field, char c) {
        return field == hexField ? (c == '#' || Character.digit(c, 16) >= 0) : Character.isDigit(c);
    }

    /** 确定：先把未提交的半截输入归一化回当前色，再报告「确定 + 最终颜色」并关闭。 */
    private void onOk() {
        syncFields(null);
        finish(true);
    }

    /** 取消：以初始色还原并报告「取消」，随后关闭。 */
    private void onCancel() {
        finish(false);
    }

    /** 关闭弹窗的唯一通道：按确认/取消分别报告结果，随后复位内部状态。 */
    private void finish(boolean confirmed) {
        open = false;
        drag = NONE;
        for (TextFieldWidget f : allFields()) {
            f.setFocused2(false);
        }
        if (confirmed) {
            if (listener != null) {
                listener.onConfirmed(color);
            }
        } else if (listener != null) {
            listener.onColorChanged(initialColor);
            listener.onCanceled();
        }
    }

    private TextFieldWidget[] allFields() {
        TextFieldWidget[] all = new TextFieldWidget[5];
        all[0] = hexField;
        System.arraycopy(channels, 0, all, 1, 4);
        return all;
    }

    private static boolean hit(AefButton button, int mx, int my) {
        return mx >= button.x && mx < button.x + button.getWidth()
                && my >= button.y && my < button.y + button.getHeight();
    }

    private static boolean overField(TextFieldWidget field, int mx, int my) {
        return mx >= field.x && mx < field.x + field.getWidth()
                && my >= field.y && my < field.y + field.getHeight();
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : Math.min(value, max);
    }

    private static float clamp01(float v) {
        return v < 0.0F ? 0.0F : Math.min(v, 1.0F);
    }

    private static String translate(String key) {
        return I18n.format(key);
    }
}
