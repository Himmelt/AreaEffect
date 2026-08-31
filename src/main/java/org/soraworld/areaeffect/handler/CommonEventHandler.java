package org.soraworld.areaeffect.handler;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
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
        if (event instanceof PlayerInteractEvent.LeftClickBlock && event.getHand() == Hand.MAIN_HAND) {
            PlayerEntity player = event.getEntityPlayer();
            ItemStack stack = player.getHeldItemMainhand();
            if (player instanceof ServerPlayerEntity && hasPerm(player) && proxy.isSelectTool(stack)) {
                proxy.setPos1(player, event.getPos(), true);
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent(receiveCanceled = true)
    public void onRightClick(PlayerInteractEvent event) {
        if (event instanceof PlayerInteractEvent.RightClickBlock && event.getHand() == Hand.MAIN_HAND) {
            PlayerEntity player = event.getEntityPlayer();
            ItemStack stack = player.getHeldItemMainhand();
            if (player instanceof ServerPlayerEntity && hasPerm(player) && proxy.isSelectTool(stack)) {
                proxy.setPos2(player, event.getPos(), true);
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getPlayer() instanceof ServerPlayerEntity) {
            proxy.sendAllAreasTo((ServerPlayerEntity) event.getPlayer());
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

    public static boolean hasPerm(PlayerEntity player) {
        return player.hasPermissionLevel(2);
    }
}
