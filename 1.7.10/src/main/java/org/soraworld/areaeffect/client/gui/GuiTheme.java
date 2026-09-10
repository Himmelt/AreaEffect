package org.soraworld.areaeffect.client.gui;

/**
 * 区域管理界面的扁平配色与颜色换算。
 *
 * <p>单独成类的原因：配色原先散落在界面的静态字段里，控件类被抽出去后就无法引用。
 * 集中到一处后，界面与控件都以单一来源取色（用法见各文件的静态导入）。
 *
 * <p>约定：<b>填充类</b>常量按 RGBA(0xRRGGBBAA) 书写，使用时经 {@link #argb(int)}
 * 转成 Minecraft 需要的 ARGB；<b>文字类</b>常量只有 24 位 RGB（无 alpha 位），
 * 直接交给字体渲染，不做转换。
 */
final class GuiTheme {

    // ===================== 主题色 =====================

    /** 选中高亮（维度 / 区域行）。 */
    static final int COLOR_SELECTED = 0x00FFFFCC;
    /** 行悬停底。 */
    static final int COLOR_HOVER_ROW = 0x00ffff54;
    /** 强调文字（备注、L 值）。 */
    static final int COLOR_ACCENT = 0xFFFF00;
    /** 滑动条进度填充（半透明纯绿）。 */
    static final int COLOR_SLIDER_FILL = 0x64FF6480;
    /** 面板 / 按钮 / 滑动条描边。 */
    static final int COLOR_BORDER = 0x4A6666FF;
    /** 按钮悬停底。 */
    static final int COLOR_BTN_HOVER = 0x00ffff54;

    // ===================== 中性色 =====================

    /** 标题 / 选中项文字。 */
    static final int COLOR_TEXT_HEAD = 0xFFFFFF;
    /** 正文 / 按钮文字。 */
    static final int COLOR_TEXT_BODY = 0xE0E0E0;
    /** 提示文字。 */
    static final int COLOR_TEXT_HINT = 0x808080;
    /** 禁用文字。 */
    static final int COLOR_TEXT_DISABLED = 0x707070;
    /** 按钮常态底。 */
    static final int COLOR_BTN_BG = 0x222222AA;
    /** 按钮禁用底。 */
    static final int COLOR_BTN_DISABLED = 0x111111AA;
    /** 滑动条轨道底。 */
    static final int COLOR_SLIDER_TRACK = 0x1A1A1AAA;
    /** 滑动条滑块。 */
    static final int COLOR_SLIDER_THUMB = 0xE8E8E8FF;
    /** 滑动条数值文字。 */
    static final int COLOR_SLIDER_TEXT = 0xF0F0F0;
    /** 滚动条轨道。 */
    static final int COLOR_SCROLL_TRACK = 0x00000033;
    /** 滚动条滑块。 */
    static final int COLOR_SCROLL_THUMB = 0xCCCCCCFF;

    private GuiTheme() {
    }

    /** RGBA(0xRRGGBBAA) → ARGB(0xAARRGGBB)，供 Minecraft 填充 / 文字颜色使用。 */
    static int argb(int rgba) {
        return ((rgba & 0xFF) << 24) | ((rgba >> 8) & 0xFFFFFF);
    }
}
