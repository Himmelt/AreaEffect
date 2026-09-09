package org.soraworld.areaeffect.common.handler;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import org.soraworld.areaeffect.common.CommonProxy;
import org.soraworld.areaeffect.common.network.PacketChannel;
import org.soraworld.areaeffect.common.util.Vec3i;

/**
 * 服务端业务事件处理：方块选点、玩家登录/登出/换维同步、服务端网络任务排空。
 * 同一实例注册到 Forge 总线与 FML 总线的玩家事件；"light"通道数据包统一由
 * {@link org.soraworld.areaeffect.common.network.PacketChannel} 路由。
 */
public class AreaServerHandler {

    private final CommonProxy proxy;

    public AreaServerHandler(CommonProxy proxy) {
        this.proxy = proxy;
    }

    /**
     * 每服务端 tick 排空网络投递任务：入站的服务端方向数据包在 netty 线程解码，
     * 业务处理统一在此（服务端主线程）执行，避免与选区/存档读写并发。
     */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            PacketChannel.drainServerTasks();
        }
    }

    @SubscribeEvent(receiveCanceled = true)
    public void onClickBlock(PlayerInteractEvent event) {
        EntityPlayer player = event.entityPlayer;
        ItemStack stack = player.getHeldItem();
        if (player instanceof EntityPlayerMP && proxy.hasPerm(player) && proxy.isSelectTool(stack)) {
            if (event.action == PlayerInteractEvent.Action.LEFT_CLICK_BLOCK) {
                proxy.onSelectToolLeft((EntityPlayerMP) player, new Vec3i(event.x, event.y, event.z));
                event.setCanceled(true);
            } else if (event.action == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) {
                proxy.onSelectToolRight((EntityPlayerMP) player, new Vec3i(event.x, event.y, event.z));
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            proxy.sendToolSync((EntityPlayerMP) event.player);
            proxy.sendAllAreasTo((EntityPlayerMP) event.player);
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        proxy.clearSelect(event.player);
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        proxy.clearSelect(event.player);
    }
}