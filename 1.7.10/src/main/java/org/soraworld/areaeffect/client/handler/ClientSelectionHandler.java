package org.soraworld.areaeffect.client.handler;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import org.soraworld.areaeffect.client.ClientProxy;
import org.soraworld.areaeffect.client.gui.GuiShapeSelect;
import org.soraworld.areaeffect.common.shape.Selection;

/**
 * 客户端选区交互：
 * <ul>
 *   <li>Shift+右键空气（持工具）→ 打开形状选择菜单</li>
 *   <li>Shift+左键（空气/方块，多边形顶点 ≥3）→ 闭合多边形（取消本次左键并发送闭合请求）</li>
 *   <li>非 Shift 右键空气（多边形）→ 撤回上一个顶点</li>
 * </ul>
 * 注意：普通（非 Shift）左键/右键方块不得取消，否则 C02/C08 不发、服务端收不到锚点。
 */
public class ClientSelectionHandler {

    private final ClientProxy proxy;
    private final Minecraft mc = Minecraft.getMinecraft();

    public ClientSelectionHandler(ClientProxy proxy) {
        this.proxy = proxy;
    }

    @SubscribeEvent(receiveCanceled = true)
    public void onInteract(PlayerInteractEvent event) {
        EntityPlayer player = event.entityPlayer;
        if (player != mc.thePlayer || !proxy.isSelectTool(player.getHeldItem())) {
            return;
        }
        if (event.action == PlayerInteractEvent.Action.RIGHT_CLICK_AIR) {
            // 菜单 / 撤回
            if (mc.thePlayer.isSneaking()) {
                event.setCanceled(true);
                mc.displayGuiScreen(new GuiShapeSelect(proxy));
            } else {
                Selection sel = proxy.getLocalSelection();
                if (sel != null && sel.isPolygon()) {
                    event.setCanceled(true);
                    proxy.sendUndoVertex();
                }
            }
        }
    }
}
