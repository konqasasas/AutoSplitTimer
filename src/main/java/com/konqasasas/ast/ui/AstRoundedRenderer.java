package com.konqasasas.ast.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.awt.image.BufferedImage;

/** Anti-aliased rounded rectangles rendered from a reusable quarter-circle mask. */
final class AstRoundedRenderer {
    private static final int MASK_SIZE = 128;
    private static final int SAMPLES = 8;
    private static ResourceLocation cornerMask;

    private AstRoundedRenderer() {}

    static void warmUp() {
        ensureMask();
    }

    static void draw(float left, float top, float right, float bottom, float radius, int color) {
        if (right <= left || bottom <= top) return;
        left = pixelAligned(left);
        top = pixelAligned(top);
        right = pixelAligned(right);
        bottom = pixelAligned(bottom);
        float maxRadius = Math.min(right - left, bottom - top) / 2f;
        radius = Math.max(0f, Math.min(pixelAlignedLength(radius), maxRadius));
        if (radius < 1f / AstUiScreen.pixelScale()) {
            solidRect(left, top, right, bottom, color);
            return;
        }

        // The two rectangular spans cover the body. Only the curved corner
        // coverage comes from the filtered mask, so there are no internal seams.
        solidRect(left + radius, top, right - radius, bottom, color);
        solidRect(left, top + radius, right, bottom - radius, color);
        drawCorners(left, top, right, bottom, radius, color);
    }

    private static void drawCorners(float left, float top, float right, float bottom,
                                    float radius, int color) {
        ensureMask();
        float alpha = (color >>> 24 & 0xFF) / 255f;
        float red = (color >>> 16 & 0xFF) / 255f;
        float green = (color >>> 8 & 0xFF) / 255f;
        float blue = (color & 0xFF) / 255f;

        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        // Minecraft keeps alpha testing enabled for GUI rendering. Its cutoff
        // discards the faint edge samples that make this mask antialiased.
        GlStateManager.disableAlpha();
        GlStateManager.disableCull();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(red, green, blue, alpha);
        Minecraft.getMinecraft().getTextureManager().bindTexture(cornerMask);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        corner(buffer, left, top, left + radius, top + radius, 0, 0, 1, 1);
        corner(buffer, right - radius, top, right, top + radius, 1, 0, 0, 1);
        corner(buffer, left, bottom - radius, left + radius, bottom, 0, 1, 1, 0);
        corner(buffer, right - radius, bottom - radius, right, bottom, 1, 1, 0, 0);
        tessellator.draw();

        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.enableCull();
        GlStateManager.enableAlpha();
        GlStateManager.disableBlend();
    }

    private static void corner(BufferBuilder buffer, float left, float top, float right, float bottom,
                               float u0, float v0, float u1, float v1) {
        buffer.pos(left, bottom, 0).tex(u0, v1).endVertex();
        buffer.pos(right, bottom, 0).tex(u1, v1).endVertex();
        buffer.pos(right, top, 0).tex(u1, v0).endVertex();
        buffer.pos(left, top, 0).tex(u0, v0).endVertex();
    }

    private static void solidRect(float left, float top, float right, float bottom, int color) {
        if (right <= left || bottom <= top) return;
        float alpha = (color >>> 24 & 0xFF) / 255f;
        float red = (color >>> 16 & 0xFF) / 255f;
        float green = (color >>> 8 & 0xFF) / 255f;
        float blue = (color & 0xFF) / 255f;
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableCull();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(red, green, blue, alpha);

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(left, bottom);
        GL11.glVertex2f(right, bottom);
        GL11.glVertex2f(right, top);
        GL11.glVertex2f(left, top);
        GL11.glEnd();

        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
    }

    private static void ensureMask() {
        if (cornerMask != null) return;
        BufferedImage image = new BufferedImage(MASK_SIZE, MASK_SIZE, BufferedImage.TYPE_INT_ARGB);
        int sampleCount = SAMPLES * SAMPLES;
        for (int y = 0; y < MASK_SIZE; y++) {
            for (int x = 0; x < MASK_SIZE; x++) {
                int covered = 0;
                for (int sy = 0; sy < SAMPLES; sy++) {
                    for (int sx = 0; sx < SAMPLES; sx++) {
                        double px = (x + (sx + 0.5) / SAMPLES) / MASK_SIZE;
                        double py = (y + (sy + 0.5) / SAMPLES) / MASK_SIZE;
                        double dx = 1.0 - px;
                        double dy = 1.0 - py;
                        if (dx * dx + dy * dy <= 1.0) covered++;
                    }
                }
                int alpha = Math.round(255f * covered / sampleCount);
                image.setRGB(x, y, (alpha << 24) | 0xFFFFFF);
            }
        }
        DynamicTexture texture = new DynamicTexture(image);
        cornerMask = Minecraft.getMinecraft().getTextureManager()
                .getDynamicTextureLocation("ast-rounded-corner", texture);
        GlStateManager.bindTexture(texture.getGlTextureId());
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
    }

    private static float pixelAligned(float value) {
        float scale = AstUiScreen.pixelScale();
        return Math.round(value * scale) / scale;
    }

    private static float pixelAlignedLength(float value) {
        float scale = AstUiScreen.pixelScale();
        return Math.max(0, Math.round(value * scale) / scale);
    }
}
