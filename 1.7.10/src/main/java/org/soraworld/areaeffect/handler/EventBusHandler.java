package org.soraworld.areaeffect.handler;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import org.soraworld.areaeffect.proxy.CommonProxy;
import org.soraworld.areaeffect.util.Vec3i;

public class EventBusHandler {

    private final CommonProxy proxy;

    public EventBusHandler(CommonProxy proxy) {
        this.proxy = proxy;
    }

    @cpw.mods.fml.common.eventhandler.SubscribeEvent(receiveCanceled = true)
    public void onClickBlock(PlayerInteractEvent event) {
        EntityPlayer player = event.entityPlayer;
        ItemStack stack = player.getHeldItem();
        if (player instanceof EntityPlayerMP && proxy.hasPerm(player) && proxy.isSelectTool(stack)) {
            if (event.action == PlayerInteractEvent.Action.LEFT_CLICK_BLOCK) {
                proxy.setPos1((EntityPlayerMP) player, new Vec3i(event.x, event.y, event.z), true);
                event.setCanceled(true);
            } else if (event.action == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) {
                proxy.setPos2((EntityPlayerMP) player, new Vec3i(event.x, event.y, event.z), true);
                event.setCanceled(true);
            }
        }
    }
}
