package org.soraworld.areaeffect.client.gui;

import net.minecraft.client.Minecraft;

import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_BORDER;
import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_BTN_BG;
import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_BTN_DISABLED;
import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_BTN_HOVER;
import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_TEXT_BODY;
import static org.soraworld.areaeffect.client.gui.GuiTheme.COLOR_TEXT_DISABLED;
import static org.soraworld.areaeffect.client.gui.GuiTheme.argb;

/**
 * 方形图标按钮：半透明底盘 + 描边 + 居中的矢量字形（全部用 drawRect / 直线绘制，无位图资源）。
 *
 * <p>字形几何沿用原先"区域行内三图标"的那套（传送 ↗、删除 ✕、线框 方框内填=开），新增保存 ✓
 * 与 tab 条的翻动箭头 ❮ ❯。四者同为 18×18，这样底栏可以整体右对齐；文字按钮混排做不到这一点。
 *
 * <p>悬停色按语义分：一般动作用黄（{@link GuiTheme#COLOR_SLIDER_THUMB_HOT}），危险动作（删除）
 * 用红（{@link GuiTheme#COLOR_DANGER}）。禁用时底盘变暗、字形转灰且不响应悬停。
 *
 * <p>描边同样支持逐边裁剪（见 {@link FlatButton#B_LEFT}）：tab 条的翻动按钮两侧由外框与 tab 条
 * 的共用线提供，自己不画边。
 */
class IconButton extends AefButton {

    static final int GLYPH_DELETE = 0;
    static final int GLYPH_TP = 1;
    static final int GLYPH_WIRE = 2;
    static final int GLYPH_SAVE = 3;
    static final int GLYPH_PREV = 4;
    static final int GLYPH_NEXT = 5;

    private final int glyph;
    private final int hotColor;
    private final int borders;
    /** 线框开关的"开启"态：内填；关闭时只留外框。 */
    private boolean on = true;

    IconButton(int id, int x, int y, int size, int glyph, int hotColor) {
        this(id, x, y, size, size, glyph, hotColor, FlatButton.B_ALL);
    }

    IconButton(int id, int x, int y, int w, int h, int glyph, int hotColor) {
        this(id, x, y, w, h, glyph, hotColor, FlatButton.B_ALL);
    }

    IconButton(int id, int x, int y, int w, int h, int glyph, int hotColor, int borders) {
        super(id, x, y, w, h, "");
        this.glyph = glyph;
        this.hotColor = hotColor;
        this.borders = borders;
    }

    void setOn(boolean on) {
        this.on = on;
    }

    /** 1.14 的绘制钩子是 {@code renderButton}(1.13 叫 {@code render}、1.12 叫 {@code drawButton})，且不再传入 Minecraft 实例。 */
    @Override
    public void renderButton(int mouseX, int mouseY, float partialTicks) {
        if (!visible) {
            return;
        }
        boolean hover = active && mouseX >= x && mouseX < x + width
                && mouseY >= y && mouseY < y + height;
        int bg = !active ? COLOR_BTN_DISABLED : hover ? COLOR_BTN_HOVER : COLOR_BTN_BG;
        fill(x, y, x + width, y + height, argb(bg));
        if ((borders & FlatButton.B_TOP) != 0) {
            fill(x, y, x + width, y + 1, argb(COLOR_BORDER));
        }
        if ((borders & FlatButton.B_BOTTOM) != 0) {
            fill(x, y + height - 1, x + width, y + height, argb(COLOR_BORDER));
        }
        if ((borders & FlatButton.B_LEFT) != 0) {
            fill(x, y, x + 1, y + height, argb(COLOR_BORDER));
        }
        if ((borders & FlatButton.B_RIGHT) != 0) {
            fill(x + width - 1, y, x + width, y + height, argb(COLOR_BORDER));
        }
        int color = !active ? COLOR_TEXT_DISABLED : hover ? hotColor : COLOR_TEXT_BODY;
        drawGlyph(color);
    }

    private void drawGlyph(int color) {
        int a = argb(color);
        int s = Math.min(width, height) - 6;
        // 字形原点（本方法内所有坐标都相对它）。不能写成 `int x = x + ...`：局部变量遮蔽字段后
        // 自引用属"未初始化"（Java 明确报错），故两套坐标分开命名。
        int gx = x + (width - s) / 2;
        int gy = y + (height - s) / 2;
        switch (glyph) {
            case GLYPH_DELETE: {
                drawLine(gx + 3, gy + 3, gx + s - 3, gy + s - 3, a);
                drawLine(gx + s - 3, gy + 3, gx + 3, gy + s - 3, a);
                break;
            }
            case GLYPH_TP: {
                int tipX = gx + s - 3;
                int tipY = gy + 3;
                drawLine(gx + 3, gy + s - 3, tipX, tipY, a);
                drawLine(tipX - 4, tipY, tipX, tipY, a);
                drawLine(tipX, tipY, tipX, tipY + 4, a);
                break;
            }
            case GLYPH_WIRE: {
                int in = 3;
                int r = gx + s - 1 - in;
                int b = gy + s - 1 - in;
                fill(gx + in, gy + in, r + 1, gy + in + 1, a);
                fill(gx + in, b, r + 1, b + 1, a);
                fill(gx + in, gy + in, gx + in + 1, b + 1, a);
                fill(r, gy + in, r + 1, b + 1, a);
                if (on) {
                    fill(gx + in + 1, gy + in + 1, r, b, a);
                }
                break;
            }
            case GLYPH_SAVE: {
                // 对勾：两笔均为严格 45°（横纵增量相等），左短右长，顶点落在下方偏左
                int vx = gx + s / 3;
                int vy = gy + (s * 2) / 3;
                int leftArm = s / 4;
                int rightArm = s / 2;
                drawLine(vx, vy, vx - leftArm, vy - leftArm, a);   // 左笔 ↖ 45°
                drawLine(vx, vy, vx + rightArm, vy - rightArm, a); // 右笔 ↗ 45°
                break;
            }
            case GLYPH_PREV: {
                int cy = gy + s / 2;
                drawLine(gx + s - 4, cy - 4, gx + 4, cy, a);
                drawLine(gx + 4, cy, gx + s - 4, cy + 4, a);
                break;
            }
            case GLYPH_NEXT: {
                int cy = gy + s / 2;
                drawLine(gx + 4, cy - 4, gx + s - 4, cy, a);
                drawLine(gx + s - 4, cy, gx + 4, cy + 4, a);
                break;
            }
            default:
                break;
        }
    }

    /** 1px 直线（Bresenham），供字形斜边使用。 */
    private void drawLine(int x1, int y1, int x2, int y2, int argb) {
        int dx = Math.abs(x2 - x1);
        int dy = -Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx + dy;
        for (int guard = 0; guard < 1024; guard++) {
            fill(x1, y1, x1 + 1, y1 + 1, argb);
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
}
