package org.soraworld.areaeffect.handler;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import org.soraworld.areaeffect.proxy.CommonProxy;

/**
 * @author Himmelt
 */
public class CommonEventHandler {

    private final CommonProxy proxy;

    public CommonEventHandler(CommonProxy proxy) {
        this.proxy = proxy;
    }

    @SubscribeEvent(receiveCanceled = true)
    public void onLeftClick(PlayerInteractEvent event) {
        if (event instanceof PlayerInteractEvent.LeftClickBlock && event.getHand() == EnumHand.MAIN_HAND) {
            EntityPlayer player = event.getEntityPlayer();
            ItemStack stack = player.getHeldItemMainhand();
            if (player instanceof EntityPlayerMP && hasPerm(player) && proxy.isSelectTool(stack)) {
                proxy.setPos1(player, event.getPos(), true);
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent(receiveCanceled = true)
    public void onRightClick(PlayerInteractEvent event) {
        if (event instanceof PlayerInteractEvent.RightClickBlock && event.getHand() == EnumHand.MAIN_HAND) {
            EntityPlayer player = event.getEntityPlayer();
            ItemStack stack = player.getHeldItemMainhand();
            if (player instanceof EntityPlayerMP && hasPerm(player) && proxy.isSelectTool(stack)) {
                proxy.setPos2(player, event.getPos(), true);
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getPlayer() instanceof EntityPlayerMP) {
            proxy.sendAllAreasTo((EntityPlayerMP) event.getPlayer());
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        proxy.clearSelect(event.getPlayer());
    }

    @SubscribeEvent
    public void onChangeDim(PlayerEvent.PlayerChangedDimensionEvent event) {
        proxy.clearSelect(event.getPlayer());
    }

    public static boolean hasPerm(EntityPlayer player) {
        return player.hasPermissionLevel(2);
    }
}
