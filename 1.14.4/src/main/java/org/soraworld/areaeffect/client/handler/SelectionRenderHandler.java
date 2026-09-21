package org.soraworld.areaeffect.client.handler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.Util;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
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
 *
 * <p>1.13 的两处渲染 API 变化：
 * <ul>
 *   <li>{@code ScaledResolution} 已移除，屏幕逻辑尺寸改从 {@code Minecraft#mainWindow} 取
 *       （{@code RenderGameOverlayEvent} 也不再携带分辨率）；</li>
 *   <li>GL 状态改写统一走 {@link GlStateManager}：它与裸 {@code GL11} 落在同一套 GL 状态上，
 *       额外维护 MC 自己的状态缓存与矩阵栈记账，避免与 vanilla 的 translate/rotate 错位。</li>
 * </ul>
 */
public class SelectionRenderHandler {

    private final ClientProxy proxy;
    private final Minecraft mc = Minecraft.getInstance();

    public SelectionRenderHandler(ClientProxy proxy) {
        this.proxy = proxy;
    }

    /**
     * 区域管理界面为全透明背景，HUD 准星会透出来；界面打开期间取消准星渲染。
     */
    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Pre event) {
        if (event.getType() == RenderGameOverlayEvent.ElementType.CROSSHAIRS
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
        if (type == null || mc.currentScreen != null || mc.player == null) {
            return;
        }
        long elapsed = Util.milliTime() - proxy.getOverlayLastAction();
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

        int cx = mc.mainWindow.getScaledWidth() / 2;
        int cy = mc.mainWindow.getScaledHeight() / 2 - 44;
        String label = I18n.format("gui.areaeffect.shape.now")
                + I18n.format("gui.areaeffect.shape." + type);
        int textWidth = mc.fontRenderer.getStringWidth(label);
        int iconSize = 12;
        int gap = 6;
        // 图标+文字作为整体水平居中，避免不同长度的文字提示视觉偏移
        int iconX = cx - (iconSize + gap + textWidth) / 2;
        int textX = iconX + iconSize + gap;
        int iconY = cy - 2; // 图标竖向中心与文字中心对齐（文字可视高约 8px）

        drawOverlayIcon(type, iconX, iconY, iconSize, alpha);
        GlStateManager.enableTexture();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        mc.fontRenderer.drawStringWithShadow(label, textX, cy,
                0xFFFFFF | ((int) (alpha * 255.0F) << 24));
    }

    /** 绘制 overlay 图标：半透明底板 + 按形状族绘制的白色线框图形。 */
    private void drawOverlayIcon(String type, int x, int y, int size, float alpha) {
        GlStateManager.disableTexture();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        buffer.color(0.0F, 0.0F, 0.0F, 0.45F * alpha);
        buffer.pos(x, y + size, 0.0D).endVertex();
        buffer.pos(x + size, y + size, 0.0D).endVertex();
        buffer.pos(x + size, y, 0.0D).endVertex();
        buffer.pos(x, y, 0.0D).endVertex();
        tessellator.draw();

        float[] rgb = iconColor(type);
        GlStateManager.lineWidth(2.0F);
        buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        buffer.color(rgb[0], rgb[1], rgb[2], alpha);
        iconGlyph(buffer, type, x, y, size);
        tessellator.draw();
    }

    /** 各形状族的图标线框。 */
    private static void iconGlyph(BufferBuilder buffer, String type, int x, int y, int s) {
        float f = s;
        float cx = x + f / 2.0F;
        float cy = y + f / 2.0F;
        float r = f / 2.0F;
        switch (type) {
            case ShapeTypes.TYPE_BOX:
                rect(buffer, x, y, x + f, y + f);
                break;
            case ShapeTypes.TYPE_SQUARE_PILLAR:
                rect(buffer, x + f * 0.2F, y, x + f * 0.8F, y + f);
                break;
            case ShapeTypes.TYPE_CYLINDER:
                circle(buffer, cx, cy, r);
                break;
            case ShapeTypes.TYPE_ROUND_PILLAR:
                circle(buffer, cx, cy, r);
                line(buffer, cx, y + f * 0.15F, cx, y + f * 0.85F);
                break;
            case ShapeTypes.TYPE_SPHERE:
                circle(buffer, cx, cy, r);
                line(buffer, x, cy, x + f, cy);
                line(buffer, cx, y, cx, y + f);
                break;
            case ShapeTypes.TYPE_POLYGON:
                polygon(buffer, x, y, s);
                break;
            case ShapeTypes.TYPE_DIMENSION:
                // 维度：外框 + 十字平分线，示意"覆盖整个维度"
                rect(buffer, x, y, x + f, y + f);
                line(buffer, cx, y + f * 0.15F, cx, y + f * 0.85F);
                line(buffer, x + f * 0.15F, cy, x + f * 0.85F, cy);
                break;
            default: // polygon_pillar
                polygon(buffer, x, y, s);
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
            case ShapeTypes.TYPE_DIMENSION:
                return new float[]{0.55F, 0.4F, 1.0F};
            default:
                return new float[]{1.0F, 0.4F, 0.4F};
        }
    }

    private static void rect(BufferBuilder buffer, float x1, float y1, float x2, float y2) {
        line(buffer, x1, y1, x2, y1);
        line(buffer, x2, y1, x2, y2);
        line(buffer, x2, y2, x1, y2);
        line(buffer, x1, y2, x1, y1);
    }

    private static void circle(BufferBuilder buffer, float cx, float cy, float r) {
        for (int i = 0; i < 12; i++) {
            double a1 = Math.PI * 2.0D * i / 12;
            double a2 = Math.PI * 2.0D * (i + 1) / 12;
            line(buffer, (float) (cx + r * Math.cos(a1)), (float) (cy + r * Math.sin(a1)),
                    (float) (cx + r * Math.cos(a2)), (float) (cy + r * Math.sin(a2)));
        }
    }

    /** 五边形轮廓（多边形族）。 */
    private static void polygon(BufferBuilder buffer, int x, int y, int s) {
        float cx = x + s / 2.0F;
        float cy = y + s / 2.0F;
        float r = s * 0.48F;
        for (int i = 0; i < 5; i++) {
            double a1 = -Math.PI / 2.0D + Math.PI * 2.0D * i / 5;
            double a2 = -Math.PI / 2.0D + Math.PI * 2.0D * (i + 1) / 5;
            line(buffer, (float) (cx + r * Math.cos(a1)), (float) (cy + r * Math.sin(a1)),
                    (float) (cx + r * Math.cos(a2)), (float) (cy + r * Math.sin(a2)));
        }
    }

    private static void line(BufferBuilder buffer, float x1, float y1, float x2, float y2) {
        buffer.pos(x1, y1, 0.0D).endVertex();
        buffer.pos(x2, y2, 0.0D).endVertex();
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (mc.world == null || mc.player == null) {
            return;
        }
        Selection sel = proxy.getLocalSelection();
        boolean drawSelection = proxy.isShowSelection() && sel != null && !sel.anchors.isEmpty();
        // 1.13 的维度是 DimensionType，区域集合仍按维度 id 分表
        List<Area> visibleAreas = proxy.getVisibleAreas(mc.player.dimension.getId());
        if (!drawSelection && visibleAreas.isEmpty()) {
            return;
        }

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.depthMask(false);

        // 1.14 移除了 RenderManager 上的 viewerPos* 字段，相机世界坐标改从 ActiveRenderInfo 取
        Vec3d camera = mc.gameRenderer.getActiveRenderInfo().getProjectedView();
        double px = camera.x;
        double py = camera.y;
        double pz = camera.z;
        GlStateManager.translated(-px, -py, -pz);

        if (drawSelection) {
            // 多边形选区：选点过程中按围栏方式逐边显示，创建时自动闭合。
            // 有界 polygon 与通天 polygon_pillar 的高度语义不同，由 sel.shapeType 区分。
            if (sel.isPolygon()) {
                drawPolygonPartial(sel);
            } else {
                AreaShape preview = ShapeTypes.build(sel);
                if (preview != null) {
                    drawShapeEdges(preview, 0.0F, 1.0F, 0.0F, 2.0F);
                }
            }
            // 锚点标记：锚点0红、锚点1蓝、多边形其余顶点白
            for (int i = 0; i < sel.anchors.size(); i++) {
                Vec3i anchor = sel.anchors.get(i);
                if (anchor == null) {
                    continue;
                }
                if (i == 0) {
                    drawMarker(anchor.x, anchor.y, anchor.z, 1.0F, 0.2F, 0.2F);
                } else if (i == 1) {
                    drawMarker(anchor.x, anchor.y, anchor.z, 0.2F, 0.5F, 1.0F);
                } else {
                    drawMarker(anchor.x, anchor.y, anchor.z, 1.0F, 1.0F, 1.0F);
                }
            }
        }

        // 已开启显示的区域线框（黄色）
        for (Area area : visibleAreas) {
            drawAreaShape(area);
        }

        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        GlStateManager.enableTexture();
        GlStateManager.popMatrix();
    }

    /** 以指定颜色/线宽绘制形状的全部边缘线段。 */
    private void drawShapeEdges(AreaShape shape, float r, float g, float b, float width) {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        GlStateManager.lineWidth(width);
        buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        buffer.color(r, g, b, 1.0F);
        for (AreaShape.Edge edge : shape.edges()) {
            buffer.pos(edge.x1, edge.y1, edge.z1).endVertex();
            buffer.pos(edge.x2, edge.y2, edge.z2).endVertex();
        }
        tessellator.draw();
    }

    /** 绘制已保存区域的边界线框（黄色，按形状边缘）。 */
    private void drawAreaShape(Area area) {
        drawShapeEdges(area.shape(), 1.0F, 0.85F, 0.2F, 2.5F);
    }

    /**
     * 多边形选点预览：每个最新点自动与第一点连线闭合，无需手动闭合动作；
     * 顶点画贯穿竖棱。高度语义与创建后一致：
     * 有界 {@code polygon} 用全部顶点 Y 的 min/max 画上下围栏与该高度段的竖棱，
     * 通天 {@code polygon_pillar} 才每 64 格画一圈围栏并贯穿全高。撤回顶点后自动更新。
     */
    private void drawPolygonPartial(Selection sel) {
        List<Vec3i> anchors = sel.anchors;
        if (anchors.size() < 2) {
            return;
        }
        boolean boundHigh = ShapeTypes.TYPE_POLYGON.equals(sel.shapeType);
        int minY = 0;
        int maxY = AreaShape.FULL_MAX_Y;
        if (boundHigh) {
            minY = Integer.MAX_VALUE;
            maxY = Integer.MIN_VALUE;
            for (Vec3i v : anchors) {
                if (v != null) {
                    minY = Math.min(minY, v.y);
                    maxY = Math.max(maxY, v.y);
                }
            }
            if (minY > maxY) {
                minY = 0;
            }
        }
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        GlStateManager.lineWidth(2.0F);
        buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        buffer.color(0.0F, 1.0F, 0.0F, 1.0F);
        int n = anchors.size();
        // 相邻边：有界时画 min/max 两道围栏，通天时每 64 格加圈
        for (int i = 0; i < n - 1; i++) {
            Vec3i a = anchors.get(i);
            Vec3i b = anchors.get(i + 1);
            if (a == null || b == null) {
                continue;
            }
            for (int y = minY; y <= maxY; y += 64) {
                line(buffer, a.x, y, a.z, b.x, y, b.z);
            }
        }
        // 自动闭合：最新点 → 第一点（≥3 顶点时形成闭合多边形）
        if (n >= 3) {
            Vec3i a = anchors.get(n - 1);
            Vec3i b = anchors.get(0);
            if (a != null && b != null) {
                for (int y = minY; y <= maxY; y += 64) {
                    line(buffer, a.x, y, a.z, b.x, y, b.z);
                }
            }
        }
        tessellator.draw();
        // 顶点竖棱：有界时只画 [minY, maxY+1]，通天时贯穿全高
        GlStateManager.lineWidth(1.5F);
        buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        buffer.color(0.0F, 1.0F, 0.0F, 0.6F);
        int top = maxY + 1;
        for (Vec3i v : anchors) {
            if (v != null) {
                line(buffer, v.x, minY, v.z, v.x, top, v.z);
            }
        }
        tessellator.draw();
    }

    private void line(BufferBuilder buffer, double x1, double y1, double z1, double x2, double y2, double z2) {
        buffer.pos(x1, y1, z1).endVertex();
        buffer.pos(x2, y2, z2).endVertex();
    }

    /** 在指定方块格子处绘制单格线框标记。 */
    private void drawMarker(int bx, int by, int bz, float r, float g, float b) {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        GlStateManager.lineWidth(3.0F);
        buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        buffer.color(r, g, b, 1.0F);
        double x = bx;
        double y = by;
        double z = bz;
        box(buffer, x, y, z, x + 1.0D, y + 1.0D, z + 1.0D);
        tessellator.draw();
    }

    /** 绘制一个长方体线框的 12 条边。 */
    private void box(BufferBuilder buffer, double x1, double y1, double z1, double x2, double y2, double z2) {
        line(buffer, x1, y1, z1, x2, y1, z1);
        line(buffer, x2, y1, z1, x2, y1, z2);
        line(buffer, x2, y1, z2, x1, y1, z2);
        line(buffer, x1, y1, z2, x1, y1, z1);
        line(buffer, x1, y2, z1, x2, y2, z1);
        line(buffer, x2, y2, z1, x2, y2, z2);
        line(buffer, x2, y2, z2, x1, y2, z2);
        line(buffer, x1, y2, z2, x1, y2, z1);
        line(buffer, x1, y1, z1, x1, y2, z1);
        line(buffer, x2, y1, z1, x2, y2, z1);
        line(buffer, x2, y1, z2, x2, y2, z2);
        line(buffer, x1, y1, z2, x1, y2, z2);
    }
}
