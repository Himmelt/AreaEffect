package org.soraworld.areaeffect.common.handler;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent;
import org.soraworld.areaeffect.common.CommonProxy;
import org.soraworld.areaeffect.common.util.Vec3i;

/**
 * 服务端业务事件处理：方块选点、玩家登录/登出/换维同步与延迟落盘。
 *
 * <p>注册关系：同一实例在 {@link net.minecraftforge.common.MinecraftForge#EVENT_BUS} 上单点注册一次
 * （见 {@code CommonProxy#onCommonSetup}），承接 {@link TickEvent.ServerTickEvent}、
 * {@link PlayerEvent} 系列与 {@link PlayerInteractEvent} —— 1.13 的这三类事件全部在 Forge 总线上派发，
 * 与 1.12 一致，无需分挂两条总线。
 *
 * <p>入站数据包统一由 {@link org.soraworld.areaeffect.common.network.PacketChannel} 经
 * SimpleChannel 的 {@code enqueueWork} 投递到服务端主线程执行，本类不再承担"排空任务队列"的职责。
 */
public class AreaServerHandler {

    private final CommonProxy proxy;

    public AreaServerHandler(CommonProxy proxy) {
        this.proxy = proxy;
    }

    /**
     * 每服务端 tick 延迟落盘：入站的服务端方向数据包已由 SimpleChannel 的 {@code enqueueWork}
     * 投递到服务端主线程执行（见 {@code PacketChannel#handle}），这里只需把上一 tick 累积的改动
     * 合并写一次文件。
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
        // 1.13 的 PlayerInteractEvent 走的是 forge 的实体版 PlayerEvent，访问器是 getEntityPlayer()
        // （fml.common.gameevent 那一支的 PlayerEvent 才是 getPlayer()）
        PlayerEntity player = event.getEntityPlayer();
        ItemStack stack = player.getHeldItemMainhand();
        if (player instanceof ServerPlayerEntity && proxy.hasPerm(player) && proxy.isSelectTool(stack)) {
            BlockPos pos = event.getPos();
            proxy.onSelectToolLeft((ServerPlayerEntity) player, new Vec3i(pos.getX(), pos.getY(), pos.getZ()));
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(receiveCanceled = true)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        PlayerEntity player = event.getEntityPlayer();
        ItemStack stack = player.getHeldItemMainhand();
        if (player instanceof ServerPlayerEntity && proxy.hasPerm(player) && proxy.isSelectTool(stack)) {
            BlockPos pos = event.getPos();
            proxy.onSelectToolRight((ServerPlayerEntity) player, new Vec3i(pos.getX(), pos.getY(), pos.getZ()));
            // 取消即等于 DENY 双方（1.13 的 ForgeEventFactory 会把取消映射成 useBlock/useItem 的 DENY），
            // 于是持工具右键方块只落选区端点、不会连带放置方块或开箱
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getPlayer() instanceof ServerPlayerEntity) {
            ServerPlayerEntity player = (ServerPlayerEntity) event.getPlayer();
            proxy.sendToolSync(player);
            proxy.sendAllAreasTo(player);
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        // 连接已断，不回发选区同步包，仅清除服务端状态
        proxy.clearSelect(event.getPlayer(), false);
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        proxy.clearSelect(event.getPlayer());
    }
}
