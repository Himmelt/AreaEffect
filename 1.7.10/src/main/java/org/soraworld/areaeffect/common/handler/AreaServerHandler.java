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
 * 服务端业务事件处理：方块选点、玩家登录/登出/换维同步、服务端网络任务排空与延迟落盘。
 *
 * <p>注册关系：同一实例在两个总线上各注册一次，事件类型互不重叠故不会重复触发——
 * FML 总线（{@code CommonProxy#onPreInit}）承接 {@link TickEvent.ServerTickEvent} 与
 * {@link PlayerEvent} 系列；Forge 总线（{@code CommonProxy#onInit}）承接
 * {@link PlayerInteractEvent}。这与 1.7.10 的事件派发归属一致。
 *
 * <p>"light" 通道数据包统一由 {@link PacketChannel} 路由。
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
            // 延迟落盘：把上一 tick 累积的改动（可能来自多个包）合并写一次文件
            proxy.flushStore();
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
        // 连接已断，不回发选区同步包，仅清除服务端状态
        proxy.clearSelect(event.player, false);
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        proxy.clearSelect(event.player);
    }
}