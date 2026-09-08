package org.soraworld.areaeffect.client.handler;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.StatCollector;
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

    /**
     * 选区形状轮切 overlay：Shift+右键轮切后显示图标+文字，1.5 秒无操作后 1.5 秒淡出。
     * 纯 HUD 绘制，不打断游戏内容。
     */
    @SubscribeEvent
    public void onRenderShapeOverlay(RenderGameOverlayEvent.Text event) {
        String type = proxy.getOverlayShape();
        if (type == null || mc.currentScreen != null || mc.thePlayer == null) {
            return;
        }
        long elapsed = Minecraft.getSystemTime() - proxy.getOverlayLastAction();
        float alpha;
        if (elapsed < 1500L) {
            alpha = 1.0F;
        } else if (elapsed < 3000L) {
            alpha = 1.0F - (float) (elapsed - 1500L) / 1500.0F;
        } else {
            // 淡出结束：清空 overlay 状态，避免残留状态在后续帧被再次绘制
            proxy.clearShapeOverlay();
            return;
        }
        // 淡出尾巴上图标(细线)与带阴影的文字在人眼感知透明度不同步，细线图标先于文字“看不见”，
        // 若降到 0.01 才清除，会在末尾残留一段“只有文字没有图标”的残影。
        // 因此把截断点抬到 0.15：图标仍隐约可见时与文字一起整体消失，避免文字单独闪一下。
        if (alpha <= 0.15F) {
            proxy.clearShapeOverlay();
            return;
        }

        ScaledResolution res = event.resolution;
        int cx = res.getScaledWidth() / 2;
        int cy = res.getScaledHeight() / 2 - 44;
        String label = StatCollector.translateToLocal("gui.areaeffect.shape.now")
                + StatCollector.translateToLocal("gui.areaeffect.shape." + type);
        int textWidth = mc.fontRenderer.getStringWidth(label);
        int iconSize = 12;
        int gap = 6;
        // 图标+文字作为整体水平居中，避免不同长度的文字提示视觉偏移
        int iconX = cx - (iconSize + gap + textWidth) / 2;
        int textX = iconX + iconSize + gap;
        int iconY = cy - 2; // 图标竖向中心与文字中心对齐（文字可视高约 8px）

        drawOverlayIcon(type, iconX, iconY, iconSize, alpha);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        mc.fontRenderer.drawStringWithShadow(label, textX, cy,
                0xFFFFFF | ((int) (alpha * 255.0F) << 24));
    }

    /** 绘制 overlay 图标：半透明底板 + 按形状族绘制的白色线框图形。 */
    private void drawOverlayIcon(String type, int x, int y, int size, float alpha) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        Tessellator tessellator = Tessellator.instance;
        GL11.glColor4f(0.0F, 0.0F, 0.0F, 0.45F * alpha);
        tessellator.startDrawingQuads();
        tessellator.addVertex(x, y + size, 0.0D);
        tessellator.addVertex(x + size, y + size, 0.0D);
        tessellator.addVertex(x + size, y, 0.0D);
        tessellator.addVertex(x, y, 0.0D);
        tessellator.draw();

        float[] rgb = iconColor(type);
        GL11.glColor4f(rgb[0], rgb[1], rgb[2], alpha);
        GL11.glLineWidth(2.0F);
        tessellator.startDrawing(GL11.GL_LINES);
        iconGlyph(tessellator, type, x, y, size);
        tessellator.draw();
    }

    /** 各形状族的图标线框。 */
    private static void iconGlyph(Tessellator tessellator, String type, int x, int y, int s) {
        float f = s;
        float cx = x + f / 2.0F;
        float cy = y + f / 2.0F;
        float r = f / 2.0F;
        switch (type) {
            case ShapeTypes.TYPE_BOX:
                rect(tessellator, x, y, x + f, y + f);
                break;
            case ShapeTypes.TYPE_SQUARE_PILLAR:
                rect(tessellator, x + f * 0.2F, y, x + f * 0.8F, y + f);
                break;
            case ShapeTypes.TYPE_CYLINDER:
                circle(tessellator, cx, cy, r);
                break;
            case ShapeTypes.TYPE_ROUND_PILLAR:
                circle(tessellator, cx, cy, r);
                line(tessellator, cx, y + f * 0.15F, cx, y + f * 0.85F);
                break;
            case ShapeTypes.TYPE_SPHERE:
                circle(tessellator, cx, cy, r);
                line(tessellator, x, cy, x + f, cy);
                line(tessellator, cx, y, cx, y + f);
                break;
            case ShapeTypes.TYPE_POLYGON:
                polygon(tessellator, x, y, s);
                break;
            default: // polygon_pillar
                polygon(tessellator, x, y, s);
                break;
        }
    }

    private static float[] iconColor(String type) {
        switch (type) {
            case ShapeTypes.TYPE_BOX:
                return new float[]{0.9F, 0.9F, 0.9F};
            case ShapeTypes.TYPE_SQUARE_PILLAR:
                return new float[]{1.0F, 0.85F, 0.2F};
            case ShapeTypes.TYPE_CYLINDER:
                return new float[]{0.4F, 0.8F, 1.0F};
            case ShapeTypes.TYPE_ROUND_PILLAR:
                return new float[]{0.5F, 1.0F, 0.6F};
            case ShapeTypes.TYPE_SPHERE:
                return new float[]{1.0F, 0.5F, 0.9F};
            case ShapeTypes.TYPE_POLYGON:
                return new float[]{1.0F, 0.65F, 0.3F};
            default:
                return new float[]{1.0F, 0.4F, 0.4F};
        }
    }

    private static void rect(Tessellator tessellator, float x1, float y1, float x2, float y2) {
        line(tessellator, x1, y1, x2, y1);
        line(tessellator, x2, y1, x2, y2);
        line(tessellator, x2, y2, x1, y2);
        line(tessellator, x1, y2, x1, y1);
    }

    private static void circle(Tessellator tessellator, float cx, float cy, float r) {
        for (int i = 0; i < 12; i++) {
            double a1 = Math.PI * 2.0D * i / 12;
            double a2 = Math.PI * 2.0D * (i + 1) / 12;
            line(tessellator, (float) (cx + r * Math.cos(a1)), (float) (cy + r * Math.sin(a1)),
                    (float) (cx + r * Math.cos(a2)), (float) (cy + r * Math.sin(a2)));
        }
    }

    /** 五边形轮廓（多边形族）。 */
    private static void polygon(Tessellator tessellator, int x, int y, int s) {
        float cx = x + s / 2.0F;
        float cy = y + s / 2.0F;
        float r = s * 0.48F;
        for (int i = 0; i < 5; i++) {
            double a1 = -Math.PI / 2.0D + Math.PI * 2.0D * i / 5;
            double a2 = -Math.PI / 2.0D + Math.PI * 2.0D * (i + 1) / 5;
            line(tessellator, (float) (cx + r * Math.cos(a1)), (float) (cy + r * Math.sin(a1)),
                    (float) (cx + r * Math.cos(a2)), (float) (cy + r * Math.sin(a2)));
        }
    }

    private static void line(Tessellator tessellator, float x1, float y1, float x2, float y2) {
        tessellator.addVertex(x1, y1, 0.0D);
        tessellator.addVertex(x2, y2, 0.0D);
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
