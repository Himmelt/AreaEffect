package org.soraworld.areaeffect.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;

import java.util.Locale;

import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_BORDER;
import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_BTN_HOVER;
import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_SLIDER_FILL;
import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_SLIDER_TEXT;
import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_SLIDER_THUMB;
import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_SLIDER_THUMB_HOT;
import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_SLIDER_TRACK;
import static org.soraworld.areaeffect.client.gui.GuiTheme.argb;

/**
 * 时段三合一控件：<b>进度条 + 双游标滑块 + 模式按钮</b>。
 *
 * <p>交互分工：
 * <ul>
 *   <li><b>拖拽游标</b>修改生效时段——起点为三角 ▲、终点为方块 ■，两游标各自独立可越界，
 *       因此可表示 {@link AreaEffect} 支持的<b>任意时段</b>：{@code start<end} 普通区间；
 *       {@code start==end} 空窗口（长度为 0，恒不开启，填充也随之收成零宽）；
 *       {@code start>end} 跨午夜（填充首尾两段 {@code [start,max]∪[min,end]}）。</li>
 *   <li><b>点击非游标区域</b>（轨道/文字）轮切时段模式，经 {@link #setOnModeToggle} 回调交给外部处理
 *       （始终→游戏→现实→始终）。</li>
 * </ul>
 *
 * <p>"始终开启"（{@code timed==false}）时段无意义：整条按全天生效绘制、不显示可拖游标，任何点击都走模式轮切。
 *
 * <p>轨道几何、步进、外部直设（{@link #setRangeRaw}/{@link #setMode}）与每帧自驱拖动（原因见 {@link FlatSlider}
 * 注释）均与 {@link FlatSlider} 一致。id 固定为 -1，使宿主 {@code actionPerformed} 不响应、避免与游标拖拽冲突。
 */
class RangeSlider extends GuiButton {

    private static final int THUMB_W = 8;
    /** 无抓取状态。 */
    private static final int NONE = 0;
    private static final int START = 1;
    private static final int END = 2;
    /** 距游标中心不超过此像素即视为"点中游标"（可拖拽），否则视为点击非游标区域（轮切模式）。 */
    private static final int GRAB = THUMB_W;

    private final float min;
    private final float max;
    private final float step;
    private String modeText = "";
    private boolean timed = true;
    private float start;
    private float end;
    private int dragging;
    private Runnable onModeToggle;

    RangeSlider(int x, int y, int w, int h, float min, float max,
                float initialStart, float initialEnd, float step) {
        super(-1, x, y, w, h, "");
        this.min = min;
        this.max = max;
        this.step = step;
        setRangeRaw(initialStart, initialEnd);
    }

    float getStart() {
        return start;
    }

    float getEnd() {
        return end;
    }

    boolean isTimed() {
        return timed;
    }

    /** 点击非游标区域时的模式轮切回调（由宿主挂上，负责改 effect 并刷新）。 */
    void setOnModeToggle(Runnable onModeToggle) {
        this.onModeToggle = onModeToggle;
    }

    /** 设置模式文案与是否为计时时段（timed=false 即"始终开启"）。 */
    void setMode(String modeText, boolean timed) {
        this.modeText = modeText;
        this.timed = timed;
        updateDisplay();
    }

    /** 外部直接设定区间（选中项切换）：只夹取到 [min,max]，不改顺序（可跨午夜）。 */
    void setRangeRaw(float s, float e) {
        start = clamp(s);
        end = clamp(e);
        updateDisplay();
    }

    private float clamp(float v) {
        return Math.max(min, Math.min(max, v));
    }

    private float snap(float v) {
        if (step > 0.0F) {
            v = Math.round(v / step) * step;
        }
        return v;
    }

    /** 小时浮点值（0..24，步进到分钟）格式化为 {@code HH:mm}；24 点显示为 24:00。 */
    private String fmtClock(float hour) {
        int totalMin = Math.round(hour * 60.0F);
        totalMin = Math.max(0, Math.min(24 * 60, totalMin));
        // 时钟文本与区域无关，固定 Locale.ROOT 避免部分地区出现非 ASCII 数字形态
        return String.format(Locale.ROOT, "%02d:%02d", totalMin / 60, totalMin % 60);
    }

    /**
     * 文案刻意压紧（箭头两侧不留空格）：这一行的实宽决定了详情栏的宽度下限，
     * 原文案 {@code 生效时段: 游戏时间  06:00 → 18:00} 实测 186px，会把详情栏锁在 46% 以上。
     */
    private void updateDisplay() {
        displayString = timed ? modeText + "  " + fmtClock(start) + "→" + fmtClock(end) : modeText;
    }

    /** 把鼠标 x 映射到 [min,max] 上并夹取、步进。与绘制几何一致：滑块中心对齐鼠标。 */
    private float valueFromMouse(int mouseX) {
        int track = width - 8 - THUMB_W;
        float frac = (float) (mouseX - xPosition - 8) / (float) track;
        frac = Math.max(0.0F, Math.min(1.0F, frac));
        return snap(min + frac * (max - min));
    }

    /** 某数值在轨道上的像素中心 x（与 valueFromMouse 互逆）。 */
    private int centerOf(float v) {
        int track = width - 8 - THUMB_W;
        float frac = (v - min) / (max - min);
        return xPosition + 8 + (int) (track * frac);
    }

    private void applyDrag(int mouseX) {
        float v = valueFromMouse(mouseX);
        if (dragging == START && v != start) {
            start = v;
            updateDisplay();
        } else if (dragging == END && v != end) {
            end = v;
            updateDisplay();
        }
    }

    @Override
    public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
        if (!super.mousePressed(mc, mouseX, mouseY)) {
            return false;
        }
        if (!timed) {
            // 始终开启无游标，整块即模式按钮
            toggleMode();
            return true;
        }
        // 点中游标：拖拽改区间；否则（轨道/文字等非游标区域）：轮切模式
        if (Math.abs(mouseX - centerOf(start)) <= GRAB) {
            dragging = START;
            applyDrag(mouseX);
        } else if (Math.abs(mouseX - centerOf(end)) <= GRAB) {
            dragging = END;
            applyDrag(mouseX);
        } else {
            toggleMode();
        }
        return true;
    }

    private void toggleMode() {
        dragging = NONE;
        if (onModeToggle != null) {
            onModeToggle.run();
        }
    }

    @Override
    protected void mouseDragged(Minecraft mc, int mouseX, int mouseY) {
        if (dragging != NONE) {
            applyDrag(mouseX);
        }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY) {
        dragging = NONE;
    }

    /**
     * 用 Tessellator 画一个实心三角形，GL 状态设置与 {@code Gui.drawRect} 一致：
     * 关纹理、开混合、按 {@code argb}(0xAARRGGBB) 取色。三个顶点即三条真边（含斜边）。
     */
    private void fillTriangle(double x1, double y1, double x2, double y2, double x3, double y3, int argb) {
        float a = (float) ((argb >> 24) & 255) / 255.0F;
        float r = (float) ((argb >> 16) & 255) / 255.0F;
        float g = (float) ((argb >> 8) & 255) / 255.0F;
        float b = (float) (argb & 255) / 255.0F;
        Tessellator tessellator = Tessellator.instance;
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GL11.glColor4f(r, g, b, a);
        tessellator.startDrawing(GL11.GL_TRIANGLES);
        tessellator.addVertex(x1, y1, 0.0D);
        tessellator.addVertex(x2, y2, 0.0D);
        tessellator.addVertex(x3, y3, 0.0D);
        tessellator.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
    }

    private void drawMarker(int cx, boolean isStart, int color) {
        int top = yPosition + 2;
        int bot = yPosition + height - 2;
        if (isStart) {
            // 起点：真正的向上三角形——顶角在上、底边在下，两条斜边为实线边（非逐像素阶梯）
            fillTriangle(cx, top, cx - THUMB_W / 2.0, bot, cx + THUMB_W / 2.0, bot, argb(color));
        } else {
            // 终点：方块
            drawRect(cx - THUMB_W / 2, top, cx + THUMB_W / 2, bot, argb(color));
        }
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (!visible) {
            return;
        }
        // 与 FlatSlider 同理：1.7.10 无人回调 mouseDragged，靠每帧自驱一次使拖动生效，勿删。
        this.mouseDragged(mc, mouseX, mouseY);
        boolean over = enabled && mouseX >= xPosition && mouseX < xPosition + width
                && mouseY >= yPosition && mouseY < yPosition + height;
        int scx = centerOf(start);
        int ecx = centerOf(end);
        // 命中/拖拽中的游标点亮为高亮黄（用不透明填充色，勿用文字色 COLOR_ACCENT：经 argb 会得 alpha=0 透明）
        boolean hotStart = timed && (dragging == START || (over && Math.abs(mouseX - scx) <= GRAB));
        boolean hotEnd = timed && (dragging == END || (over && Math.abs(mouseX - ecx) <= GRAB));
        // 轨道底 + 恒定边框（悬停不改边框，改的是框内整片，观感与 FlatButton 一致）
        drawRect(xPosition, yPosition, xPosition + width, yPosition + height, argb(COLOR_SLIDER_TRACK));
        drawRect(xPosition, yPosition, xPosition + width, yPosition + 1, argb(COLOR_BORDER));
        drawRect(xPosition, yPosition + height - 1, xPosition + width, yPosition + height, argb(COLOR_BORDER));
        drawRect(xPosition, yPosition, xPosition + 1, yPosition + height, argb(COLOR_BORDER));
        drawRect(xPosition + width - 1, yPosition, xPosition + width, yPosition + height, argb(COLOR_BORDER));
        // 填充几何与 FlatSlider 对齐：竖向内缩 3、左右极值 x+3 / x+width-3，避免两根进度条粗细/端点不一致
        int leftEdge = xPosition + 3;
        int rightEdge = xPosition + width - 3;
        // 先生效区间填充
        if (timed) {
            if (start <= end) {
                drawRect(scx, yPosition + 3, ecx, yPosition + height - 3, argb(COLOR_SLIDER_FILL));
            } else {
                // 跨午夜：填首尾两段
                drawRect(scx, yPosition + 3, rightEdge, yPosition + height - 3, argb(COLOR_SLIDER_FILL));
                drawRect(leftEdge, yPosition + 3, ecx, yPosition + height - 3, argb(COLOR_SLIDER_FILL));
            }
        } else {
            // 始终开启：整条铺满 = 全天生效，无游标
            drawRect(leftEdge, yPosition + 3, rightEdge, yPosition + height - 3, argb(COLOR_SLIDER_FILL));
        }
        // 悬停：边框以内整片刷一层按钮式半透明底（同时覆盖背景与进度条），再画游标使其保持清晰
        if (over) {
            drawRect(xPosition + 1, yPosition + 1, xPosition + width - 1, yPosition + height - 1, argb(COLOR_BTN_HOVER));
        }
        if (timed) {
            // 两个游标：起点三角、终点方块（最后画，压在悬停底之上）
            drawMarker(scx, true, hotStart ? COLOR_SLIDER_THUMB_HOT : COLOR_SLIDER_THUMB);
            drawMarker(ecx, false, hotEnd ? COLOR_SLIDER_THUMB_HOT : COLOR_SLIDER_THUMB);
        }
        drawCenteredString(mc.fontRenderer, displayString,
                xPosition + width / 2, yPosition + (height - 8) / 2, COLOR_SLIDER_TEXT);
    }
}
