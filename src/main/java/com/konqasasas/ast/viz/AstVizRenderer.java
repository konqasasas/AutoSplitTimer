package com.konqasasas.ast.viz;

import com.konqasasas.ast.core.AstCourseManager;
import com.konqasasas.ast.core.AstData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.util.Locale;

/** World visualization for all segments. */
public class AstVizRenderer {
    public enum Mode { OUTLINE, FILL, BOTH }

    private static volatile boolean ENABLED = true;
    private static volatile Mode MODE = Mode.OUTLINE;

    // Fixed per spec: radius 50 blocks => distSq <= 2500
    private static final double DIST_SQ_MAX = 2500.0;

    public static void setEnabled(boolean v) {
        ENABLED = v;
    }

    public static boolean isEnabled() {
        return ENABLED;
    }

    public static void setMode(String m) {
        if (m == null) return;
        String s = m.trim().toLowerCase(Locale.ROOT);
        switch (s) {
            case "fill":
                MODE = Mode.FILL;
                break;
            case "both":
                MODE = Mode.BOTH;
                break;
            case "outline":
            default:
                MODE = Mode.OUTLINE;
                break;
        }
    }

    public static String getModeName() {
        return MODE.name().toLowerCase(Locale.ROOT);
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent e) {
        if (!ENABLED) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null) return;

        AstData.CourseFile course = AstCourseManager.get().getActiveCourse();
        if (course == null || course.segments == null || course.segments.isEmpty()) return;

        Entity view = mc.getRenderViewEntity();
        if (view == null) view = mc.player;

        double pt = e.getPartialTicks();
        double vx = view.lastTickPosX + (view.posX - view.lastTickPosX) * pt;
        double vy = view.lastTickPosY + (view.posY - view.lastTickPosY) * pt;
        double vz = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * pt;

        Vec3d playerPos = new Vec3d(mc.player.posX, mc.player.posY, mc.player.posZ);

        GlStateManager.pushMatrix();
        GlStateManager.translate(-vx, -vy, -vz);

        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.glLineWidth(2.0f);
        GlStateManager.depthMask(false);

        int maxIndex = maxIndex(course);

        for (AstData.Segment seg : course.segments) {
            if (seg == null || seg.aabb == null) continue;
            AxisAlignedBB bb = seg.aabb.toAabb();

            // Distance check using AABB center
            Vec3d c = new Vec3d((bb.minX + bb.maxX) * 0.5, (bb.minY + bb.maxY) * 0.5, (bb.minZ + bb.maxZ) * 0.5);
            if (c.squareDistanceTo(playerPos) > DIST_SQ_MAX) continue;

            int color = colorFor(seg.index, maxIndex);
            float r = ((color >> 16) & 0xFF) / 255.0f;
            float g = ((color >> 8) & 0xFF) / 255.0f;
            float b = (color & 0xFF) / 255.0f;

            if (MODE == Mode.FILL || MODE == Mode.BOTH) {
                drawFilledAabb(bb, r, g, b, 0.20f);
            }
            if (MODE == Mode.OUTLINE || MODE == Mode.BOTH) {
                drawOutlinedAabb(bb, r, g, b, 0.85f);
            }
        }

        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

    private static int maxIndex(AstData.CourseFile course) {
        int max = 0;
        for (AstData.Segment s : course.segments) {
            if (s != null) max = Math.max(max, s.index);
        }
        return max;
    }

    private static int colorFor(int idx, int maxIdx) {
        if (idx == 0) return 0x66FF66;      // Start
        if (idx == maxIdx) return 0xFF4444; // Goal
        return 0xFFFF66;                    // Other
    }

    private static void drawOutlinedAabb(AxisAlignedBB bb, float r, float g, float b, float a) {
        Tessellator tes = Tessellator.getInstance();
        BufferBuilder buf = tes.getBuffer();
        buf.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        // bottom
        line(buf, bb.minX, bb.minY, bb.minZ, bb.maxX, bb.minY, bb.minZ, r, g, b, a);
        line(buf, bb.maxX, bb.minY, bb.minZ, bb.maxX, bb.minY, bb.maxZ, r, g, b, a);
        line(buf, bb.maxX, bb.minY, bb.maxZ, bb.minX, bb.minY, bb.maxZ, r, g, b, a);
        line(buf, bb.minX, bb.minY, bb.maxZ, bb.minX, bb.minY, bb.minZ, r, g, b, a);
        // top
        line(buf, bb.minX, bb.maxY, bb.minZ, bb.maxX, bb.maxY, bb.minZ, r, g, b, a);
        line(buf, bb.maxX, bb.maxY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ, r, g, b, a);
        line(buf, bb.maxX, bb.maxY, bb.maxZ, bb.minX, bb.maxY, bb.maxZ, r, g, b, a);
        line(buf, bb.minX, bb.maxY, bb.maxZ, bb.minX, bb.maxY, bb.minZ, r, g, b, a);
        // verticals
        line(buf, bb.minX, bb.minY, bb.minZ, bb.minX, bb.maxY, bb.minZ, r, g, b, a);
        line(buf, bb.maxX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.minZ, r, g, b, a);
        line(buf, bb.maxX, bb.minY, bb.maxZ, bb.maxX, bb.maxY, bb.maxZ, r, g, b, a);
        line(buf, bb.minX, bb.minY, bb.maxZ, bb.minX, bb.maxY, bb.maxZ, r, g, b, a);
        tes.draw();
    }

    private static void line(BufferBuilder buf, double x1, double y1, double z1, double x2, double y2, double z2,
                             float r, float g, float b, float a) {
        buf.pos(x1, y1, z1).color(r, g, b, a).endVertex();
        buf.pos(x2, y2, z2).color(r, g, b, a).endVertex();
    }

    private static void drawFilledAabb(AxisAlignedBB bb, float r, float g, float b, float a) {
        Tessellator tes = Tessellator.getInstance();
        BufferBuilder buf = tes.getBuffer();
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);

        // Bottom
        quad(buf, bb.minX, bb.minY, bb.minZ, bb.maxX, bb.minY, bb.maxZ, r, g, b, a, Face.BOTTOM);
        // Top
        quad(buf, bb.minX, bb.maxY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ, r, g, b, a, Face.TOP);
        // Sides
        quad(buf, bb.minX, bb.minY, bb.minZ, bb.minX, bb.maxY, bb.maxZ, r, g, b, a, Face.WEST);
        quad(buf, bb.maxX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ, r, g, b, a, Face.EAST);
        quad(buf, bb.minX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.minZ, r, g, b, a, Face.NORTH);
        quad(buf, bb.minX, bb.minY, bb.maxZ, bb.maxX, bb.maxY, bb.maxZ, r, g, b, a, Face.SOUTH);

        tes.draw();
    }

    private enum Face { TOP, BOTTOM, NORTH, SOUTH, EAST, WEST }

    private static void quad(BufferBuilder buf,
                             double x1, double y1, double z1,
                             double x2, double y2, double z2,
                             float r, float g, float b, float a,
                             Face face) {
        switch (face) {
            case BOTTOM:
                // (x1,y1,z1) to (x2,y1,z2)
                buf.pos(x1, y1, z1).color(r, g, b, a).endVertex();
                buf.pos(x2, y1, z1).color(r, g, b, a).endVertex();
                buf.pos(x2, y1, z2).color(r, g, b, a).endVertex();
                buf.pos(x1, y1, z2).color(r, g, b, a).endVertex();
                break;
            case TOP:
                buf.pos(x1, y1, z1).color(r, g, b, a).endVertex();
                buf.pos(x1, y1, z2).color(r, g, b, a).endVertex();
                buf.pos(x2, y1, z2).color(r, g, b, a).endVertex();
                buf.pos(x2, y1, z1).color(r, g, b, a).endVertex();
                break;
            case WEST:
                buf.pos(x1, y1, z1).color(r, g, b, a).endVertex();
                buf.pos(x1, y2, z1).color(r, g, b, a).endVertex();
                buf.pos(x1, y2, z2).color(r, g, b, a).endVertex();
                buf.pos(x1, y1, z2).color(r, g, b, a).endVertex();
                break;
            case EAST:
                buf.pos(x1, y1, z1).color(r, g, b, a).endVertex();
                buf.pos(x1, y1, z2).color(r, g, b, a).endVertex();
                buf.pos(x1, y2, z2).color(r, g, b, a).endVertex();
                buf.pos(x1, y2, z1).color(r, g, b, a).endVertex();
                break;
            case NORTH:
                buf.pos(x1, y1, z1).color(r, g, b, a).endVertex();
                buf.pos(x1, y2, z1).color(r, g, b, a).endVertex();
                buf.pos(x2, y2, z1).color(r, g, b, a).endVertex();
                buf.pos(x2, y1, z1).color(r, g, b, a).endVertex();
                break;
            case SOUTH:
                buf.pos(x1, y1, z2).color(r, g, b, a).endVertex();
                buf.pos(x2, y1, z2).color(r, g, b, a).endVertex();
                buf.pos(x2, y2, z2).color(r, g, b, a).endVertex();
                buf.pos(x1, y2, z2).color(r, g, b, a).endVertex();
                break;
        }
    }
}
