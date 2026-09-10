package org.soraworld.areaeffect.client.gui;

/**
 * 单列可滚动列表的滚动状态与滚动条几何。
 *
 * <p>抽出来的直接原因：原先两列各自持有一份 scroll 字段，再靠"传进来的 List 是不是 dims"
 * 这种<b>引用相等</b>去判断该改哪一个——既脆弱（列表实例被替换就失效）又难读。
 * 现在每列一个实例，各持各的状态，调用方不再需要声明"我是谁"。
 *
 * <p>职责边界：只管状态与几何，<b>不绘制</b>、不持有 {@code GuiScreen}。
 * 需要画滚动条时向 {@link #bar(int)} 取矩形，由界面自己画，避免控件反向依赖界面。
 */
final class ScrollColumn {

    /** 滚动条宽度，同时也是它在列表右侧占用的横向宽度。 */
    static final int BAR_W = 6;

    /** 列表区上下内边距，与列表绘制时的首行偏移保持一致。 */
    private static final int PAD = 4;

    /** 滚动条最小长度，保证仍可抓取。 */
    private static final int MIN_BAR_H = 14;

    /** 滚动条左边界的 x（绘制与命中判定共用）。 */
    int x;
    /** 列表区的上下边界。 */
    int top;
    int bottom;
    /** 行高，与列表绘制共用。 */
    final int rowHeight;

    private int scroll = 0;
    private boolean dragging = false;
    private int dragStartY = 0;
    private int dragStartScroll = 0;

    ScrollColumn(int rowHeight) {
        this.rowHeight = rowHeight;
    }

    /** 当前滚动量（像素）。 */
    int scroll() {
        return scroll;
    }

    /** 第 {@code index} 行的 y 坐标（已含内边距与滚动偏移）。 */
    int rowY(int index) {
        return top + PAD + index * rowHeight - scroll;
    }

    /** 列表可视高度（滚动计算的基准）。 */
    int viewHeight() {
        return bottom - top - PAD * 2;
    }

    /** 回到顶部（列表内容整体换掉时调用）。 */
    void reset() {
        scroll = 0;
    }

    /** 把滚动量收进合法范围（列表长度变化后调用）。 */
    void clamp(int count) {
        scroll = clampTo(scroll, count);
    }

    /** 按行滚动（滚轮）：{@code rows} 为正表示内容上移（向后翻）。 */
    void wheel(int rows, int count) {
        scroll = clampTo(scroll + rows * rowHeight, count);
    }

    /**
     * 处理滚动条上的按下：命中滑块直接进入拖拽，命中轨道则先按点击位置跳转再进入拖拽。
     *
     * @return 是否消费了这次点击（调用方据此决定要不要继续处理面板内的行选择）
     */
    boolean press(int mouseX, int mouseY, int count) {
        int[] bar = bar(count);
        if (bar == null || mouseX < x || mouseX >= x + BAR_W
                || mouseY < top + PAD || mouseY >= bottom - PAD) {
            return false;
        }
        if (mouseY < bar[1] || mouseY >= bar[3]) {
            // 命中轨道：按点击位置在轨道上的比例跳转
            int travel = Math.max(1, viewHeight() - (bar[3] - bar[1]));
            int frac = (mouseY - top - PAD - (bar[3] - bar[1]) / 2) * maxScroll(count) / travel;
            scroll = clampTo(frac, count);
        }
        dragging = true;
        dragStartY = mouseY;
        dragStartScroll = scroll;
        return true;
    }

    /** 拖拽中：把鼠标纵向位移映射为滚动量（未处于拖拽状态时什么也不做）。 */
    void drag(int mouseY, int count) {
        if (!dragging) {
            return;
        }
        if (count * rowHeight <= 0) {
            return; // 拖拽中列表被清空（如收到广播删除）：无内容可滚，避免除零
        }
        int travel = Math.max(1, viewHeight() - barHeight(count));
        scroll = clampTo(dragStartScroll + (mouseY - dragStartY) * maxScroll(count) / travel, count);
    }

    /** 结束拖拽。 */
    void release() {
        dragging = false;
    }

    /**
     * 滚动条矩形 {x1, y1, x2, y2}；内容不足一屏时返回 null（此时不需要滚动条）。
     */
    int[] bar(int count) {
        if (maxScroll(count) <= 0) {
            return null;
        }
        int barH = barHeight(count);
        int barY = top + PAD + (viewHeight() - barH) * scroll / maxScroll(count);
        return new int[]{x, barY, x + BAR_W, barY + barH};
    }

    private int maxScroll(int count) {
        return Math.max(0, count * rowHeight - viewHeight());
    }

    private int barHeight(int count) {
        int view = viewHeight();
        return Math.max(MIN_BAR_H, view * view / Math.max(1, count * rowHeight));
    }

    private int clampTo(int value, int count) {
        return Math.max(0, Math.min(maxScroll(count), value));
    }
}
