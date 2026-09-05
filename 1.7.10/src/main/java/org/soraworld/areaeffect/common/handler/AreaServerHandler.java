package org.soraworld.areaeffect.common.handler;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import org.soraworld.areaeffect.common.CommonProxy;
import org.soraworld.areaeffect.common.network.AreaPacket;
import org.soraworld.areaeffect.common.util.Vec3i;

/**
 * 服务端业务事件处理：方块选点、玩家登录/登出/换维同步、"light"通道请求。
 * 同一实例注册到 Forge 总线 / FML 总线 / light 通道三个订阅点。
 */
public class AreaServerHandler {

    private final CommonProxy proxy;

    public AreaServerHandler(CommonProxy proxy) {
        this.proxy = proxy;
    }

    @SubscribeEvent(receiveCanceled = true)
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

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
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

    @SubscribeEvent
    public void onPacket(FMLNetworkEvent.ServerCustomPacketEvent event) {
        ByteBuf buf = event.packet.payload();
        byte op = buf.readByte();
        EntityPlayerMP player = ((NetHandlerPlayServer) event.handler).playerEntity;
        if (op == CommonProxy.REQ_LIST) {
            proxy.handleListRequest(player);
        } else if (op == CommonProxy.REQ_SET) {
            proxy.handleSetProps(player, AreaPacket.SetProps.decode(buf));
        }
    }
}