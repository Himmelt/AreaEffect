package org.soraworld.areaeffect.client.gui;

import net.minecraft.client.gui.GuiButton;

/**
 * 本项目所有自绘控件的基座：把「按下 → 通知宿主界面」这一层补回来。
 *
 * <p>1.13 移除了 {@code GuiScreen#actionPerformed(GuiButton)}，{@code GuiButton} 自身也不再持有
 * 宿主界面引用 —— 点击只会在按钮内部走一次 {@code onClick}，宿主无从得知。于是这里让控件在按下时
 * 回调宿主挂上的动作（{@link #setOnPress}），宿主仍按 id 分发，沿用 1.12 时代的接线方式。
 *
 * <p>事件分发链路（1.13）：{@code GuiScreen} 把鼠标事件按 {@code children} 列表逐个下发，
 * 命中并消费后把该控件记为焦点，之后的拖动/释放事件只发给它 —— 因此控件必须经
 * {@code GuiScreen#addButton} 加入（它同时进 {@code buttons} 绘制表与 {@code children} 事件表），
 * 且 {@code initGui} 重建时要两张表一起清，否则会残留上一轮（窗口缩放前）的旧控件。
 */
abstract class AefButton extends GuiButton {

    /** 宿主界面挂上的"按下"回调。 */
    private Runnable onPress;

    AefButton(int id, int x, int y, int w, int h, String label) {
        super(id, x, y, w, h, label);
    }

    /** 由宿主在创建控件后立即挂上（见 {@code GuiAreas#addWidget}）。 */
    final void setOnPress(Runnable onPress) {
        this.onPress = onPress;
    }

    /** 触发宿主回调；未挂回调时静默。 */
    final void fireAction() {
        if (onPress != null) {
            onPress.run();
        }
    }

    /** 默认按下即通知宿主；滑条类控件会覆写以先进入拖拽。 */
    @Override
    public void onClick(double mouseX, double mouseY) {
        fireAction();
    }
}
