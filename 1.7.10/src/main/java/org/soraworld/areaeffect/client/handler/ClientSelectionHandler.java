package org.soraworld.areaeffect.client.handler;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import org.soraworld.areaeffect.client.ClientProxy;
import org.soraworld.areaeffect.common.shape.Selection;
import org.soraworld.areaeffect.common.shape.ShapeTypes;

/**
 * 客户端选区交互：
 * <ul>
 *   <li>Shift+右键空气（持工具）→ 沿 {@link ShapeTypes#ALL} 轮切选区形状，overlay 图标+文字提示（无 GUI）。
 *       轮切所依据的"当前形状"来自本地选区镜像；该镜像在发送请求时会由
 *       {@code ClientProxy#sendSelectShape} 乐观更新，服务端回声到达后再以权威值覆盖，
 *       因此高延迟下连续轮切不会"跳不动"</li>
 *   <li>非 Shift 右键空气（多边形）→ 以自定义消息 {@link org.soraworld.areaeffect.common.network.MessageClickAir}
 *       发送到服务端，与右键方块同入口处理，作为撤回上一顶点的触发事件</li>
 * </ul>
 * 注意：普通（非 Shift）左键/右键方块不得取消，否则 C02/C08 不发、服务端收不到锚点。
 * 多边形无手动闭合动作：顶点 ≥3 即自动闭合可创建。
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
            if (mc.thePlayer.isSneaking()) {
                // 轮切选区形状：按注册表顺序取下一项，overlay 提示
                event.setCanceled(true);
                String next = nextShape(currentShape());
                proxy.sendSelectShape(next);
                proxy.showShapeOverlay(next);
            } else {
                Selection sel = proxy.getLocalSelection();
                if (sel != null && sel.isPolygon()) {
                    // 右键空气 → 自定义消息，服务端与右键方块同入口处理（撤回顶点）
                    event.setCanceled(true);
                    proxy.sendClickAir();
                }
            }
        }
    }

    /** 当前本地选区形状（无选区时按 box）。 */
    private String currentShape() {
        Selection sel = proxy.getLocalSelection();
        String type = sel == null ? null : sel.shapeType;
        return type == null || type.isEmpty() ? ShapeTypes.TYPE_BOX : type;
    }

    /** 沿 ShapeTypes.ALL 顺序轮切到下一形状。 */
    private static String nextShape(String current) {
        String[] all = ShapeTypes.ALL;
        for (int i = 0; i < all.length; i++) {
            if (all[i].equals(current)) {
                return all[(i + 1) % all.length];
            }
        }
        return all[0];
    }
}
