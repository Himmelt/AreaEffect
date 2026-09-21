package org.soraworld.areaeffect.client.gui;

import net.minecraft.client.Minecraft;

import java.util.Locale;

import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_BORDER;
import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_SLIDER_FILL;
import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_SLIDER_TEXT;
import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_SLIDER_THUMB;
import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_SLIDER_THUMB_HOT;
import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_SLIDER_TRACK;
import static org.soraworld.areaeffect.client.gui.GuiTheme.argb;

/**
 * 扁平滑动条：拖动步进取整，天蓝填充 + 白色滑块，显示"标签 数值"。
 *
 * <p>取值区间与步进由构造参数决定；{@link #setValueRaw(float)} 用于外部直接设值
 * （切换选中项时用，不做步进），{@link #getValue()} 供保存时读取。
 *
 * <p>默认是<b>线性刻度</b>（位置与值成正比）。跨数量级的参数（如"过渡距离"从 0.2 米到 256 米）
 * 可传入 {@link Scale}（见 {@link #logarithmic}）：等距移动对应等比值变化，两端手感一致 ——
 * 否则会出现"1→2 米变化巨大、100→110 米毫无感觉"的线性刻度问题。
 */
class FlatSlider extends AefButton {

    private static final int THUMB_W = 8;
    /** 距滑块中心不超过此像素即视为悬停在滑块上（与 RangeSlider 口径一致）。 */
    private static final int GRAB = THUMB_W;

    private final float min;
    private final float max;
    private final float step;
    private final String label;
    private final boolean integerStep;
    /** 刻度映射；{@code null} 表示默认线性刻度。 */
    private final Scale scale;
    private float value;
    private boolean dragging;

    FlatSlider(int x, int y, int w, int h, String label, float min, float max, float initial, float step) {
        this(x, y, w, h, label, min, max, initial, step, null);
    }

    /** 自定义刻度的构造：{@code step} 传 0（量化交给 {@link Scale#snap}）。 */
    FlatSlider(int x, int y, int w, int h, String label, float min, float max, float initial, float step, Scale scale) {
        super(-1, x, y, w, h, "");
        this.label = label;
        this.min = min;
        this.max = max;
        this.step = step;
        this.integerStep = step >= 1.0F;
        this.scale = scale;
        setValue(initial);
    }

    /**
     * 等比（对数）刻度：{@code frac <= 0} 时为 0（最左端＝零值，用于"无过渡"这类语义），
     * 否则 {@code min·(max/min)^frac}；量化到三位有效数字。
     */
    static Scale logarithmic(final float min, final float max) {
        return new LogScale(min, max);
    }

    /** 取值刻度：位置(0..1) 与值互相映射，并负责量化与显示。 */
    interface Scale {

        /** 把拖动得到的原始值吸附到最近的可取值。 */
        float snap(float value);

        /** 滑条位置(0..1) → 值。 */
        float toValue(float frac);

        /** 值 → 滑条位置(0..1)。 */
        float toFrac(float value);

        /** 数值显示文本（不含标签）。 */
        String format(float value);
    }

    private static final class LogScale implements Scale {

        private final float min;
        private final float max;
        private final double logSpan;

        LogScale(float min, float max) {
            this.min = min;
            this.max = max;
            this.logSpan = Math.log((double) max / (double) min);
        }

        @Override
        public float snap(float value) {
            if (value <= 0.0F) {
                return 0.0F;
            }
            // 三位有效数字：跨越 0.2~256 这种量级时，绝对值步进没有意义
            double magnitude = Math.pow(10.0D, Math.floor(Math.log10(value)) - 2.0D);
            return (float) (Math.round(value / magnitude) * magnitude);
        }

        @Override
        public float toValue(float frac) {
            return frac <= 0.0F ? 0.0F : (float) (min * Math.exp(logSpan * frac));
        }

        @Override
        public float toFrac(float value) {
            if (value <= 0.0F) {
                return 0.0F;
            }
            double frac = Math.log(value / (double) min) / logSpan;
            return (float) Math.max(0.0D, Math.min(1.0D, frac));
        }

        @Override
        public String format(float value) {
            if (value <= 0.0F) {
                return "0";
            }
            if (value < 10.0F) {
                return String.format(Locale.ROOT, "%.2f", (double) value);
            }
            return value < 100.0F ? String.format(Locale.ROOT, "%.1f", (double) value)
                    : String.format(Locale.ROOT, "%.0f", (double) value);
        }
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
        if (scale != null) {
            v = Math.max(min, Math.min(max, scale.snap(v)));
        } else if (step > 0.0F) {
            v = Math.round(v / step) * step;
        }
        value = v;
        updateDisplay();
    }

    private void updateDisplay() {
        // 一律用 Locale.ROOT：否则在德语/法语等区域会打出 "7,14" 这种逗号小数点
        String shown = scale != null ? scale.format(value)
                : (integerStep ? String.format(Locale.ROOT, "%.0f", (double) value)
                : String.format(Locale.ROOT, "%.1f", (double) value));
        displayString = label + " " + shown;
    }

    private void setValueFromMouse(int mouseX) {
        // 与绘制几何一致：滑块中心对齐鼠标（滑块宽 8）
        float frac = (float) (mouseX - x - 8) / (float) (width - 16);
        frac = Math.max(0.0F, Math.min(1.0F, frac));
        setValue(scale != null ? scale.toValue(frac) : min + frac * (max - min));
    }

    /** 某数值对应的滑块中心 x（与 {@link #setValueFromMouse} 互逆）。 */
    private int centerOf(float v) {
        int track = width - 8 - THUMB_W;
        float frac = scale != null ? scale.toFrac(v) : (v - min) / (max - min);
        return x + 8 + (int) (track * frac);
    }

    /**
     * 按下：进入拖拽并按落点取值。
     *
     * <p>1.13 的按下钩子是 {@code onClick}（取代 {@code mousePressed(Minecraft,int,int)}），
     * 命中判定（可用 + 可见 + 落在框内）已由 {@code GuiButton#mouseClicked} 先做掉；
     * 另外宿主不再自动收到 {@code actionPerformed}，故这里主动回调一次（本控件 id=-1，宿主按 id 分发时自然忽略）。
     */
    @Override
    public void onClick(double mouseX, double mouseY) {
        fireAction();
        dragging = true;
        setValueFromMouse((int) mouseX);
    }

    /**
     * 拖动中：由 {@code GuiScreen} 只发给"按下时命中的那个控件"（见 {@link AefButton} 类注释），
     * 因此不再需要 1.7.10 时代"每帧自驱拖拽"的补丁。
     */
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!dragging) {
            return false;
        }
        setValueFromMouse((int) mouseX);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        if (!visible) {
            return;
        }
        // 轨道
        drawRect(x, y, x + width, y + height, argb(COLOR_SLIDER_TRACK));
        drawRect(x, y, x + width, y + 1, argb(COLOR_BORDER));
        drawRect(x, y + height - 1, x + width, y + height, argb(COLOR_BORDER));
        drawRect(x, y, x + 1, y + height, argb(COLOR_BORDER));
        drawRect(x + width - 1, y, x + width, y + height, argb(COLOR_BORDER));
        // 填充与滑块（8px 宽，便于拖动）
        int cx = centerOf(value);
        int tx = cx - THUMB_W / 2;
        if (tx > x + 4) {
            drawRect(x + 3, y + 3, cx, y + height - 3, argb(COLOR_SLIDER_FILL));
        }
        // 悬停/拖拽中滑块点亮为高亮黄（与 RangeSlider 观感一致）
        boolean over = enabled && mouseY >= y && mouseY < y + height;
        boolean hot = dragging || (over && Math.abs(mouseX - cx) <= GRAB);
        drawRect(tx, y + 2, tx + THUMB_W, y + height - 2, argb(hot ? COLOR_SLIDER_THUMB_HOT : COLOR_SLIDER_THUMB));
        drawCenteredString(Minecraft.getInstance().fontRenderer, displayString,
                x + width / 2, y + (height - 8) / 2, COLOR_SLIDER_TEXT);
    }
}
