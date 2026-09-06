package org.soraworld.areaeffect.client.handler;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.opengl.GL11;
import org.soraworld.areaeffect.client.ClientProxy;
import org.soraworld.areaeffect.client.gui.GuiAreas;
import org.soraworld.areaeffect.common.network.Area;
import org.soraworld.areaeffect.common.shape.AreaShape;
import org.soraworld.areaeffect.common.shape.Selection;
import org.soraworld.areaeffect.common.shape.ShapeTypes;
import org.soraworld.areaeffect.common.util.Vec3i;

import java.util.List;

/**
 * 客户端自绘选区线框（按形状）+ 已保存区域线框（按形状边缘）。
 */
public class SelectionRenderHandler {

    private final ClientProxy proxy;
    private final Minecraft mc = Minecraft.getMinecraft();

    public SelectionRenderHandler(ClientProxy proxy) {
        this.proxy = proxy;
    }

    /**
     * 区域管理界面为全透明背景，HUD 准星会透出来；界面打开期间取消准星渲染。
     */
    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Pre event) {
        if (event.type == RenderGameOverlayEvent.ElementType.CROSSHAIRS
                && mc.currentScreen instanceof GuiAreas) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (mc.theWorld == null || mc.thePlayer == null) {
            return;
        }
        Selection sel = proxy.getLocalSelection();
        boolean drawSelection = proxy.isShowSelection() && sel != null && !sel.anchors.isEmpty();
        List<Area> visibleAreas = proxy.getVisibleAreas(mc.thePlayer.dimension);
        if (!drawSelection && visibleAreas.isEmpty()) {
            return;
        }

        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(false);

        double px = RenderManager.instance.viewerPosX;
        double py = RenderManager.instance.viewerPosY;
        double pz = RenderManager.instance.viewerPosZ;
        GL11.glTranslated(-px, -py, -pz);

        Tessellator tessellator = Tessellator.instance;
        if (drawSelection) {
            // 多边形选区：未闭合时按通天柱围栏方式逐边显示，闭合后按完整形状
            if (sel.isPolygon() && !sel.closed) {
                drawPolygonPartial(tessellator, sel.anchors);
            } else {
                AreaShape preview = ShapeTypes.build(sel);
                if (preview != null) {
                    drawShapeEdges(tessellator, preview, 0.0F, 1.0F, 0.0F, 2.0F);
                }
            }
            // 锚点标记：锚点0红、锚点1蓝、多边形其余顶点白
            for (int i = 0; i < sel.anchors.size(); i++) {
                Vec3i anchor = sel.anchors.get(i);
                if (anchor == null) {
                    continue;
                }
                if (i == 0) {
                    drawMarker(tessellator, anchor.x, anchor.y, anchor.z, 1.0F, 0.2F, 0.2F);
                } else if (i == 1) {
                    drawMarker(tessellator, anchor.x, anchor.y, anchor.z, 0.2F, 0.5F, 1.0F);
                } else {
                    drawMarker(tessellator, anchor.x, anchor.y, anchor.z, 1.0F, 1.0F, 1.0F);
                }
            }
        }

        // 已开启显示的区域线框（黄色）
        for (Area area : visibleAreas) {
            drawAreaShape(tessellator, area);
        }

        GL11.glDepthMask(true);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glPopMatrix();
    }

    /** 以指定颜色/线宽绘制形状的全部边缘线段。 */
    private void drawShapeEdges(Tessellator tessellator, AreaShape shape, float r, float g, float b, float width) {
        GL11.glLineWidth(width);
        GL11.glColor4f(r, g, b, 1.0F);
        tessellator.startDrawing(GL11.GL_LINES);
        for (AreaShape.Edge edge : shape.edges()) {
            tessellator.addVertex(edge.x1, edge.y1, edge.z1);
            tessellator.addVertex(edge.x2, edge.y2, edge.z2);
        }
        tessellator.draw();
    }

    /** 绘制已保存区域的边界线框（黄色，按形状边缘）。 */
    private void drawAreaShape(Tessellator tessellator, Area area) {
        drawShapeEdges(tessellator, area.shape(), 1.0F, 0.85F, 0.2F, 2.5F);
    }

    /**
     * 多边形选点预览：每个最新点自动与第一点连线闭合，无需手动闭合动作；
     * 按通天柱方式每 64 格画一道横向围栏，顶点画贯穿竖棱。撤回顶点后自动更新。
     */
    private void drawPolygonPartial(Tessellator tessellator, List<Vec3i> anchors) {
        if (anchors.size() < 2) {
            return;
        }
        GL11.glLineWidth(2.0F);
        GL11.glColor4f(0.0F, 1.0F, 0.0F, 1.0F);
        tessellator.startDrawing(GL11.GL_LINES);
        int levels = 256;
        int n = anchors.size();
        // 相邻边
        for (int i = 0; i < n - 1; i++) {
            Vec3i a = anchors.get(i);
            Vec3i b = anchors.get(i + 1);
            if (a == null || b == null) {
                continue;
            }
            for (int y = 0; y <= levels; y += 64) {
                line(tessellator, a.x, y, a.z, b.x, y, b.z);
            }
        }
        // 自动闭合：最新点 → 第一点（≥3 顶点时形成闭合多边形）
        if (n >= 3) {
            Vec3i a = anchors.get(n - 1);
            Vec3i b = anchors.get(0);
            if (a != null && b != null) {
                for (int y = 0; y <= levels; y += 64) {
                    line(tessellator, a.x, y, a.z, b.x, y, b.z);
                }
            }
        }
        tessellator.draw();
        // 顶点竖棱贯穿全高
        GL11.glLineWidth(1.5F);
        GL11.glColor4f(0.0F, 1.0F, 0.0F, 0.6F);
        tessellator.startDrawing(GL11.GL_LINES);
        for (Vec3i v : anchors) {
            if (v != null) {
                line(tessellator, v.x, 0.0D, v.z, v.x, levels + 1.0D, v.z);
            }
        }
        tessellator.draw();
    }

    private void line(Tessellator tessellator, double x1, double y1, double z1, double x2, double y2, double z2) {
        tessellator.addVertex(x1, y1, z1);
        tessellator.addVertex(x2, y2, z2);
    }

    /** 在指定方块格子处绘制单格线框标记。 */
    private void drawMarker(Tessellator tessellator, int bx, int by, int bz, float r, float g, float b) {
        GL11.glLineWidth(3.0F);
        GL11.glColor4f(r, g, b, 1.0F);
        tessellator.startDrawing(GL11.GL_LINES);
        double x = bx;
        double y = by;
        double z = bz;
        box(tessellator, x, y, z, x + 1.0D, y + 1.0D, z + 1.0D);
        tessellator.draw();
    }

    /** 绘制一个长方体线框的 12 条边。 */
    private void box(Tessellator tessellator, double x1, double y1, double z1, double x2, double y2, double z2) {
        line(tessellator, x1, y1, z1, x2, y1, z1);
        line(tessellator, x2, y1, z1, x2, y1, z2);
        line(tessellator, x2, y1, z2, x1, y1, z2);
        line(tessellator, x1, y1, z2, x1, y1, z1);
        line(tessellator, x1, y2, z1, x2, y2, z1);
        line(tessellator, x2, y2, z1, x2, y2, z2);
        line(tessellator, x2, y2, z2, x1, y2, z2);
        line(tessellator, x1, y2, z2, x1, y2, z1);
        line(tessellator, x1, y1, z1, x1, y2, z1);
        line(tessellator, x2, y1, z1, x2, y2, z1);
        line(tessellator, x2, y1, z2, x2, y2, z2);
        line(tessellator, x1, y1, z2, x1, y2, z2);
    }
}
