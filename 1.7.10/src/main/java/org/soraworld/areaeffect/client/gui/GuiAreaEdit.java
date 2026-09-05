package org.soraworld.areaeffect.client.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Keyboard;
import org.soraworld.areaeffect.client.ClientProxy;
import org.soraworld.areaeffect.common.effect.AreaEffect;
import org.soraworld.areaeffect.common.effect.LightnessEffect;
import org.soraworld.areaeffect.common.network.Area;

import java.util.ArrayList;
import java.util.List;

/**
 * 区域详情界面：展示区域信息，并允许修改目标亮度与过渡时长。
 */
public class GuiAreaEdit extends GuiScreen {

    private static final int BTN_SAVE = 0;
    private static final int BTN_BACK = 1;
    private static final int FIELD_W = 140;

    private final ClientProxy proxy;
    private final int dim;
    private final Area area;

    private GuiTextField lightnessField;
    private GuiTextField durationField;

    public GuiAreaEdit(ClientProxy proxy, int dim, Area area) {
        this.proxy = proxy;
        this.dim = dim;
        this.area = area;
    }

    @Override
    public void initGui() {
        super.initGui();
        buttonList.clear();
        buttonList.add(new GuiButton(BTN_SAVE, width - 160, height - 30, 70, 20, translate("gui.areaeffect.edit.save")));
        buttonList.add(new GuiButton(BTN_BACK, width - 84, height - 30, 70, 20, translate("gui.areaeffect.edit.cancel")));

        int fieldX = width / 2 + 20;
        lightnessField = new GuiTextField(fontRendererObj, fieldX, height / 2 - 34, FIELD_W, 20);
        lightnessField.setText(fmt(area.getLightness()));
        lightnessField.setFocused(true);

        durationField = new GuiTextField(fontRendererObj, fieldX, height / 2, FIELD_W, 20);
        durationField.setText(fmt(area.getDuration()));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        String title = String.format(translate("gui.areaeffect.edit.title"), area.id);
        drawCenteredString(fontRendererObj, title, width / 2, 12, 0xFFFFFF);
        drawCenteredString(fontRendererObj, area.pos1() + " ~ " + area.pos2(), width / 2, 28, 0xE0E0E0);

        String lightLabel = translate("gui.areaeffect.edit.lightness");
        fontRendererObj.drawStringWithShadow(lightLabel, width / 2 - fontRendererObj.getStringWidth(lightLabel) - 20, height / 2 - 28, 0xAAAAAA);
        lightnessField.drawTextBox();

        String durLabel = translate("gui.areaeffect.edit.duration");
        fontRendererObj.drawStringWithShadow(durLabel, width / 2 - fontRendererObj.getStringWidth(durLabel) - 20, height / 2 + 6, 0xAAAAAA);
        durationField.drawTextBox();

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(null);
        } else if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            save();
        } else {
            lightnessField.textboxKeyTyped(typedChar, keyCode);
            durationField.textboxKeyTyped(typedChar, keyCode);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        lightnessField.mouseClicked(mouseX, mouseY, mouseButton);
        durationField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BTN_SAVE) {
            save();
        } else if (button.id == BTN_BACK) {
            mc.displayGuiScreen(null);
        }
    }

    private void save() {
        float lightness;
        float duration;
        try {
            lightness = Float.parseFloat(lightnessField.getText().trim());
        } catch (NumberFormatException e) {
            lightness = area.getLightness();
        }
        try {
            duration = Float.parseFloat(durationField.getText().trim());
        } catch (NumberFormatException e) {
            duration = area.getDuration();
        }
        List<AreaEffect> effects = new ArrayList<>();
        effects.add(new LightnessEffect(lightness, duration));
        proxy.sendSetProps(dim, area.id, effects);
        mc.displayGuiScreen(null);
    }

    private static String translate(String key) {
        return StatCollector.translateToLocal(key);
    }

    private static String fmt(float v) {
        return String.format("%.1f", (double) v);
    }
}