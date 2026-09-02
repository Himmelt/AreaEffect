package org.soraworld.areaeffect.handler;

import net.minecraft.client.gui.GuiVideoSettings;
import net.minecraftforge.client.event.GuiScreenEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import org.soraworld.areaeffect.proxy.ClientProxy;
import org.soraworld.areaeffect.proxy.CommonProxy;

public class EventBusClientHandler {

    private final ClientProxy proxy;

    public EventBusClientHandler(ClientProxy proxy) {
        this.proxy = proxy;
    }

    @cpw.mods.fml.common.eventhandler.SubscribeEvent
    public void onVideoSetting(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (event.gui instanceof GuiVideoSettings) {
            proxy.saveLight();
        }
    }
}
