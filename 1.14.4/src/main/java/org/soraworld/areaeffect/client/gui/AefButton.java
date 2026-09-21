package org.soraworld.areaeffect.client.gui;

import net.minecraft.client.gui.widget.button.AbstractButton;

/**
 * 本项目所有自绘控件的基座：把「按下 → 通知宿主界面」这一层补回来，并自带控件 id。
 *
 * <p>1.13 移除了 {@code Screen#actionPerformed}，1.14 又把按钮改成回调式
 * （{@code AbstractButton#onPress()}）—— 控件都不会再通知宿主界面。于是这里让控件在按下时
 * 回调宿主挂上的动作（{@link #setOnPress}），宿主仍按 id 分发，沿用 1.12 时代的接线方式。
 *
 * <p>1.14 的 {@code Widget} 不再有 id 字段（按钮靠回调标识身份），故本类自带一份 {@link #id}，
 * 让宿主的"按 id 分发"结构原样保留。
 *
 * <p>事件分发链路：{@code Screen} 把鼠标事件按 {@code children} 列表逐个下发，命中并消费后把该控件
 * 记为焦点，之后的拖动/释放事件只发给它 —— 因此控件必须经 {@code Screen#addButton} 加入
 * （它同时进 {@code buttons} 绘制表与 {@code children} 事件表），且 {@code Screen#init()} 重建时
 * 两张表都要清，否则会残留上一轮（窗口缩放前）的旧控件。
 */
abstract class AefButton extends AbstractButton {

    /** 控件 id：1.14 的 {@code Widget} 不再提供，本类自带以便宿主按 id 分发。 */
    private final int id;

    /** 宿主界面挂上的"按下"回调。 */
    private Runnable onPress;

    AefButton(int id, int x, int y, int w, int h, String label) {
        super(x, y, w, h, label);
        this.id = id;
    }

    int getId() {
        return id;
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

    /** 1.14 的按下入口：{@code AbstractButton#onClick} 会转到这里；滑条会覆写 onClick 以取坐标。 */
    @Override
    public void onPress() {
        fireAction();
    }
}
