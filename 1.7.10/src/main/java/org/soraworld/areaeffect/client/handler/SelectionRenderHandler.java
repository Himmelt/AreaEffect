package org.soraworld.areaeffect.client.handler;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.opengl.GL11;
import org.soraworld.areaeffect.client.ClientProxy;
import org.soraworld.areaeffect.common.network.Area;
import org.soraworld.areaeffect.common.util.Vec3i;

import java.util.List;

/**
 * 客户端自绘选区线框，替代原 WECUI 依赖。
 */
public class SelectionRenderHandler {

    private final ClientProxy proxy;
    private final Minecraft mc = Minecraft.getMinecraft();

    public SelectionRenderHandler(ClientProxy proxy) {
        this.proxy = proxy;
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (mc.theWorld == null || mc.thePlayer == null) {
            return;
        }
        Vec3i p1 = proxy.getSelPos1();
        Vec3i p2 = proxy.getSelPos2();
        boolean drawSelection = proxy.isShowSelection() && p1 != null && p2 != null;
        List<Area> visibleAreas = proxy.getVisibleAreas(mc.thePlayer.dimension);
        if (!drawSelection && visibleAreas.isEmpty()) {
            return;
        }
        // 两个角取最小/最大，框住整个长方体内包含的方块
        double minX = drawSelection ? Math.min(p1.x, p2.x) : 0.0D;
        double minY = drawSelection ? Math.min(p1.y, p2.y) : 0.0D;
        double minZ = drawSelection ? Math.min(p1.z, p2.z) : 0.0D;
        double maxX = drawSelection ? Math.max(p1.x, p2.x) + 1.0D : 0.0D;
        double maxY = drawSelection ? Math.max(p1.y, p2.y) + 1.0D : 0.0D;
        double maxZ = drawSelection ? Math.max(p1.z, p2.z) + 1.0D : 0.0D;

        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glLineWidth(2.0F);
        GL11.glColor4f(0.0F, 1.0F, 0.0F, 1.0F);
        GL11.glDepthMask(false);

        double px = net.minecraft.client.renderer.entity.RenderManager.instance.viewerPosX;
        double py = net.minecraft.client.renderer.entity.RenderManager.instance.viewerPosY;
        double pz = net.minecraft.client.renderer.entity.RenderManager.instance.viewerPosZ;
        GL11.glTranslated(-px, -py, -pz);

        Tessellator tessellator = Tessellator.instance;
        if (drawSelection) {
            GL11.glLineWidth(2.0F);
            GL11.glColor4f(0.0F, 1.0F, 0.0F, 1.0F);
            tessellator.startDrawing(GL11.GL_LINES);
            // 底面四点 (minY)
            line(tessellator, minX, minY, minZ, maxX, minY, minZ);
            line(tessellator, maxX, minY, minZ, maxX, minY, maxZ);
            line(tessellator, maxX, minY, maxZ, minX, minY, maxZ);
            line(tessellator, minX, minY, maxZ, minX, minY, minZ);
            // 顶面四点 (maxY)
            line(tessellator, minX, maxY, minZ, maxX, maxY, minZ);
            line(tessellator, maxX, maxY, minZ, maxX, maxY, maxZ);
            line(tessellator, maxX, maxY, maxZ, minX, maxY, maxZ);
            line(tessellator, minX, maxY, maxZ, minX, maxY, minZ);
            // 四条竖棱
            line(tessellator, minX, minY, minZ, minX, maxY, minZ);
            line(tessellator, maxX, minY, minZ, maxX, maxY, minZ);
            line(tessellator, maxX, minY, maxZ, maxX, maxY, maxZ);
            line(tessellator, minX, minY, maxZ, minX, maxY, maxZ);
            tessellator.draw();

            // 起点(pos1)红色、终点(pos2)蓝色单格标记
            drawMarker(tessellator, p1.x, p1.y, p1.z, 1.0F, 0.2F, 0.2F);
            drawMarker(tessellator, p2.x, p2.y, p2.z, 0.2F, 0.5F, 1.0F);
        }

        // 已开启显示的区域线框（黄色）
        for (Area area : visibleAreas) {
            drawAreaBox(tessellator, area);
        }

        GL11.glDepthMask(true);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glPopMatrix();
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

    /** 绘制已保存区域的边界线框（黄色，盖住整个区域）。 */
    private void drawAreaBox(Tessellator tessellator, Area area) {
        GL11.glLineWidth(2.5F);
        GL11.glColor4f(1.0F, 0.85F, 0.2F, 1.0F);
        tessellator.startDrawing(GL11.GL_LINES);
        box(tessellator, area.x1, area.y1, area.z1, area.x2 + 1.0D, area.y2 + 1.0D, area.z2 + 1.0D);
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