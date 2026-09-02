package org.soraworld.areaeffect.handler;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiVideoSettings;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.soraworld.areaeffect.proxy.CommonProxy;

/**
 * @author Himmelt
 */
public class ClientEventHandler {

    private final CommonProxy proxy;

    public ClientEventHandler(CommonProxy proxy) {
        this.proxy = proxy;
    }

    @SubscribeEvent
    public void onGuiOpen(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (event.getGui() instanceof GuiVideoSettings) {
            proxy.saveLight();
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.player instanceof EntityPlayerSP) {
            proxy.updateClientGamma(event.player);
        }
    }

    @SubscribeEvent
    public void onLogout(GuiOpenEvent event) {
        GuiScreen screen = event.getGui();
        if (screen instanceof GuiMainMenu || screen instanceof GuiMultiplayer) {
            proxy.clientReset();
        }
    }
}
