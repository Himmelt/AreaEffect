package org.soraworld.areaeffect.client.handler;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.opengl.GL11;
import org.soraworld.areaeffect.client.ClientProxy;
import org.soraworld.areaeffect.common.util.Vec3i;

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
        Vec3i p1 = proxy.getSelPos1();
        Vec3i p2 = proxy.getSelPos2();
        if (p1 == null || p2 == null || mc.theWorld == null || mc.thePlayer == null) {
            return;
        }
        // 两个角取最小/最大，框住整个长方体内包含的方块
        double minX = Math.min(p1.x, p2.x);
        double minY = Math.min(p1.y, p2.y);
        double minZ = Math.min(p1.z, p2.z);
        double maxX = Math.max(p1.x, p2.x) + 1.0D;
        double maxY = Math.max(p1.y, p2.y) + 1.0D;
        double maxZ = Math.max(p1.z, p2.z) + 1.0D;

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

        GL11.glDepthMask(true);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glPopMatrix();
    }

    private void line(Tessellator tessellator, double x1, double y1, double z1, double x2, double y2, double z2) {
        tessellator.addVertex(x1, y1, z1);
        tessellator.addVertex(x2, y2, z2);
    }
}