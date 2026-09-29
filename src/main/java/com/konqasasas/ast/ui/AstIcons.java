package com.konqasasas.ast.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/** High-resolution anti-aliased icons for the native interface. */
final class AstIcons {
    private static final int CELL = 64;
    private static final int ATLAS_W = CELL * 2;
    private static final int ATLAS_H = CELL;
    private static ResourceLocation textureLocation;

    private AstIcons() {}

    static void drawChevron(int x, int y, int size, boolean up, int argb) {
        ensureTexture();
        float u0 = up ? 0f : .5f;
        float u1 = up ? .5f : 1f;

        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        float alpha = ((argb >>> 24) & 255) / 255f;
        GlStateManager.color(((argb >>> 16) & 255) / 255f, ((argb >>> 8) & 255) / 255f,
                (argb & 255) / 255f, alpha);
        Minecraft.getMinecraft().getTextureManager().bindTexture(textureLocation);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(x, y + size, 0).tex(u0, 1).endVertex();
        buffer.pos(x + size, y + size, 0).tex(u1, 1).endVertex();
        buffer.pos(x + size, y, 0).tex(u1, 0).endVertex();
        buffer.pos(x, y, 0).tex(u0, 0).endVertex();
        tessellator.draw();
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.enableAlpha();
    }

    private static void ensureTexture() {
        if (textureLocation != null) return;
        BufferedImage image = new BufferedImage(ATLAS_W, ATLAS_H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setColor(Color.WHITE);
        graphics.setStroke(new BasicStroke(8f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
        // Each icon occupies a full square cell. Large source geometry is
        // reduced with linear filtering, avoiding jagged pixel stair-steps.
        graphics.drawPolyline(new int[]{8, 32, 56}, new int[]{44, 20, 44}, 3);
        graphics.drawPolyline(new int[]{72, 96, 120}, new int[]{20, 44, 20}, 3);
        graphics.dispose();

        DynamicTexture texture = new DynamicTexture(ATLAS_W, ATLAS_H);
        image.getRGB(0, 0, ATLAS_W, ATLAS_H, texture.getTextureData(), 0, ATLAS_W);
        texture.updateDynamicTexture();
        textureLocation = Minecraft.getMinecraft().getTextureManager()
                .getDynamicTextureLocation("ast-ui-icons", texture);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        Minecraft.getMinecraft().getTextureManager().bindTexture(textureLocation);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GlStateManager.bindTexture(previousTexture);
    }
}
