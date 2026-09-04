package org.soraworld.areaeffect.handler;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import net.minecraft.entity.player.EntityPlayerMP;
import org.soraworld.areaeffect.proxy.CommonProxy;

public class FMLHandler {

    private final CommonProxy proxy;

    public FMLHandler(CommonProxy proxy) {
        this.proxy = proxy;
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            proxy.sendAllAreasTo((EntityPlayerMP) event.player);
        }
    }

    @SubscribeEvent
    public void onLogout(cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent event) {
        proxy.clearSelect(event.player);
    }

    @SubscribeEvent
    public void onChangeDim(cpw.mods.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent event) {
        proxy.clearSelect(event.player);
    }
}
