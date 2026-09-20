package org.soraworld.areaeffect.common.handler;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.soraworld.areaeffect.common.CommonProxy;
import org.soraworld.areaeffect.common.util.Vec3i;

/**
 * 服务端业务事件处理：方块选点、玩家登录/登出/换维同步与延迟落盘。
 *
 * <p>注册关系：同一实例在 1.12.2 的 {@link net.minecraftforge.common.MinecraftForge#EVENT_BUS} 上单点
 * 注册一次（见 {@code CommonProxy#onPreInit}），承接 {@link TickEvent.ServerTickEvent}、
 * {@link PlayerEvent} 系列与 {@link PlayerInteractEvent}（1.12.2 的 FML 事件也在 EVENT_BUS 派发，
 * 无需像 1.7.10 那样分挂两条总线）。
 *
 * <p>"light" 通道数据包统一由 {@link org.soraworld.areaeffect.common.network.PacketChannel} 路由。
 */
public class AreaServerHandler {

    private final CommonProxy proxy;

    public AreaServerHandler(CommonProxy proxy) {
        this.proxy = proxy;
    }

    /**
     * 每服务端 tick 延迟落盘：入站的服务端方向数据包已由 {@code MinecraftServer#addScheduledTask}
     * 直接投递到服务端主线程执行（见 {@link org.soraworld.areaeffect.common.network.PacketChannel#route}），
     * 这里只需把上一 tick 累积的改动合并写一次文件。
     */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            // 延迟落盘：把上一 tick 累积的改动（可能来自多个包）合并写一次文件
            proxy.flushStore();
        }
    }

    @SubscribeEvent(receiveCanceled = true)
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        EntityPlayer player = event.getEntityPlayer();
        ItemStack stack = player.getHeldItemMainhand();
        if (player instanceof EntityPlayerMP && proxy.hasPerm(player) && proxy.isSelectTool(stack)) {
            BlockPos pos = event.getPos();
            proxy.onSelectToolLeft((EntityPlayerMP) player, new Vec3i(pos.getX(), pos.getY(), pos.getZ()));
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(receiveCanceled = true)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        EntityPlayer player = event.getEntityPlayer();
        ItemStack stack = player.getHeldItemMainhand();
        if (player instanceof EntityPlayerMP && proxy.hasPerm(player) && proxy.isSelectTool(stack)) {
            BlockPos pos = event.getPos();
            proxy.onSelectToolRight((EntityPlayerMP) player, new Vec3i(pos.getX(), pos.getY(), pos.getZ()));
            event.setCanceled(true);
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