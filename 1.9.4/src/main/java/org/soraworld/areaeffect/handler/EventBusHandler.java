package org.soraworld.areaeffect.handler;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.soraworld.areaeffect.proxy.CommonProxy;
import org.soraworld.areaeffect.util.Vec3i;

public class EventBusHandler {

    private final CommonProxy proxy;

    public EventBusHandler(CommonProxy proxy) {
        this.proxy = proxy;
    }

    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent(receiveCanceled = true)
    public void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        EntityPlayer player = event.getEntityPlayer();
        ItemStack stack = player.getHeldItemMainhand();
        if (player instanceof EntityPlayerMP && proxy.hasPerm(player) && proxy.isSelectTool(stack) && event.getHand() == EnumHand.MAIN_HAND) {
            proxy.setPos1((EntityPlayerMP) player, new Vec3i(event.getPos().getX(), event.getPos().getY(), event.getPos().getZ()), true);
            event.setCanceled(true);
        }
    }

    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent(receiveCanceled = true)
    public void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        EntityPlayer player = event.getEntityPlayer();
        ItemStack stack = player.getHeldItemMainhand();
        if (player instanceof EntityPlayerMP && proxy.hasPerm(player) && proxy.isSelectTool(stack) && event.getHand() == EnumHand.MAIN_HAND) {
            proxy.setPos2((EntityPlayerMP) player, new Vec3i(event.getPos().getX(), event.getPos().getY(), event.getPos().getZ()), true);
            event.setCanceled(true);
        }
    }
}
