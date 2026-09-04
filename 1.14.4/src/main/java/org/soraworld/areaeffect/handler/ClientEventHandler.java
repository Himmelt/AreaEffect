package org.soraworld.areaeffect.handler;

import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.gui.screen.VideoSettingsScreen;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
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
        if (event.getGui() instanceof VideoSettingsScreen) {
            proxy.saveLight();
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (event.player instanceof ClientPlayerEntity) {
            proxy.updateClientGamma(event.player);
        }
    }

    @SubscribeEvent
    public void onLogout(ClientPlayerNetworkEvent.LoggedOutEvent event) {
        proxy.clientReset();
    }
}
