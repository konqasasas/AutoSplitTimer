package com.konqasasas.ast.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.GlyphVector;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Anti-aliased native UI font.
 *
 * Each displayed size has its own atlas, rasterized at twice its final physical
 * size. Scaling one fixed atlas made small copy lose stems and large headings
 * blurry; browsers likewise rasterize each CSS font size independently.
 */
public final class AstFont {
    private static final float SUPERSAMPLE = 2f;
    private static final float RASTER_SCALE = AstUiScreen.DESIGN_ZOOM * SUPERSAMPLE;

    private final Font baseFont;
    private final int atlasSize;
    private final float apparentSizeFactor;
    private final Map<Integer, SizeAtlas> atlases = new HashMap<>();

    public AstFont(String resourcePath) {
        try (InputStream stream = AstFont.class.getResourceAsStream(resourcePath)) {
            if (stream == null) throw new IllegalStateException("Missing font " + resourcePath);
            baseFont = Font.createFont(Font.TRUETYPE_FONT, stream);
            // Japanese screens can use more unique glyphs; Latin and numeric
            // atlases stay smaller so their first upload is only 1 MB.
            atlasSize = resourcePath.contains("BIZUDPGothic") ? 768 : 512;
            // IBM Plex has a substantially smaller cap height than BIZ UDPGothic
            // at the same nominal point size. This is the native equivalent of
            // CSS font-size-adjust: match apparent glyph size before alignment,
            // instead of applying fragile per-control y nudges.
            apparentSizeFactor = resourcePath.contains("IBMPlex") ? 1.18f : 1f;
        } catch (Exception exception) {
            throw new IllegalStateException("Could not load font " + resourcePath, exception);
        }
    }

    public int width(String text, float size) {
        if (text == null || text.isEmpty()) return 0;
        SizeAtlas atlas = atlas(size);
        float width = 0f;
        for (int i = 0; i < text.length(); i++) width += atlas.glyph(text.charAt(i)).advance;
        return Math.round(width);
    }

    public int lineHeight(float size) {
        return Math.max(1, Math.round(atlas(size).lineHeight));
    }

    /** Top of the visible glyph ink relative to the draw y coordinate. */
    public float visualTop(String text, float size) {
        return atlas(size).visualBounds(text).top;
    }

    /** Height of the visible glyph ink, excluding the font's invisible line padding. */
    public float visualHeight(String text, float size) {
        return atlas(size).visualBounds(text).height;
    }

    public void draw(String text, float x, float y, float size, int argb) {
        if (text == null || text.isEmpty()) return;
        atlas(size).draw(text, x, y, argb);
    }

    /** Upload all glyphs collected during the current UI frame in one pass. */
    void flush() {
        for (SizeAtlas atlas : atlases.values()) if (atlas.dirty) atlas.upload();
    }

    private SizeAtlas atlas(float size) {
        int key = Math.max(100, Math.round(size * 100f));
        SizeAtlas cached = atlases.get(key);
        if (cached != null) return cached;
        SizeAtlas created = new SizeAtlas(key / 100f);
        atlases.put(key, created);
        return created;
    }

    private final class SizeAtlas {
        private final float designSize;
        private final Font font;
        private final BufferedImage image = new BufferedImage(atlasSize, atlasSize, BufferedImage.TYPE_INT_ARGB);
        private final Graphics2D graphics = image.createGraphics();
        private final DynamicTexture texture = new DynamicTexture(atlasSize, atlasSize);
        private final ResourceLocation textureLocation;
        private final Map<Character, Glyph> glyphs = new HashMap<>();
        private final int ascent;
        private final int rawLineHeight;
        private final int padding;
        private final float lineHeight;
        private int cursorX = 1;
        private int cursorY = 1;
        private int rowHeight;
        private boolean dirty;

        private SizeAtlas(float designSize) {
            this.designSize = designSize;
            font = baseFont.deriveFont(Math.max(1f, designSize * RASTER_SCALE * apparentSizeFactor));
            graphics.setComposite(AlphaComposite.SrcOver);
            graphics.setColor(Color.WHITE);
            graphics.setFont(font);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            FontMetrics metrics = graphics.getFontMetrics();
            ascent = metrics.getAscent();
            rawLineHeight = metrics.getAscent() + metrics.getDescent();
            padding = Math.max(3, Math.round(2f * RASTER_SCALE));
            lineHeight = rawLineHeight / RASTER_SCALE;
            textureLocation = Minecraft.getMinecraft().getTextureManager()
                    .getDynamicTextureLocation("ast-font-" + Math.round(designSize * 100f), texture);
            applyLinearFiltering();
        }

        private void draw(String text, float x, float y, int argb) {
            for (int i = 0; i < text.length(); i++) glyph(text.charAt(i));
            // Native screens collect every missing glyph for the whole frame and
            // upload once at the end. Without this, each Japanese label caused a
            // complete 1024x1024 texture upload while opening a screen.
            if (dirty && !AstFonts.isBatching()) upload();

            float pen = x;
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            float alpha = ((argb >>> 24) & 255) / 255f;
            if (alpha == 0f) alpha = 1f;
            GlStateManager.color(((argb >>> 16) & 255) / 255f, ((argb >>> 8) & 255) / 255f,
                    (argb & 255) / 255f, alpha);
            Minecraft.getMinecraft().getTextureManager().bindTexture(textureLocation);

            float logicalPadding = padding / RASTER_SCALE;
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
            for (int i = 0; i < text.length(); i++) {
                Glyph glyph = glyph(text.charAt(i));
                float left = pen - logicalPadding;
                float top = y - logicalPadding;
                float width = glyph.width / RASTER_SCALE;
                float height = glyph.height / RASTER_SCALE;
                float u0 = glyph.x / (float) atlasSize;
                float v0 = glyph.y / (float) atlasSize;
                float u1 = (glyph.x + glyph.width) / (float) atlasSize;
                float v1 = (glyph.y + glyph.height) / (float) atlasSize;
                buffer.pos(left, top + height, 0).tex(u0, v1).endVertex();
                buffer.pos(left + width, top + height, 0).tex(u1, v1).endVertex();
                buffer.pos(left + width, top, 0).tex(u1, v0).endVertex();
                buffer.pos(left, top, 0).tex(u0, v0).endVertex();
                pen += glyph.advance;
            }
            tessellator.draw();
            GlStateManager.color(1, 1, 1, 1);
        }

        private Glyph glyph(char character) {
            Glyph cached = glyphs.get(character);
            if (cached != null) return cached;

            String value = String.valueOf(character);
            Rectangle2D bounds = font.getStringBounds(value, graphics.getFontRenderContext());
            float rawAdvance = (float) bounds.getWidth();
            int contentWidth = Math.max(1, (int) Math.ceil(Math.max(rawAdvance, bounds.getMaxX())));
            int width = contentWidth + padding * 2;
            int height = rawLineHeight + padding * 2;
            if (cursorX + width >= atlasSize) {
                cursorX = 1;
                cursorY += rowHeight + 1;
                rowHeight = 0;
            }
            if (cursorY + height >= atlasSize) {
                if (character == '?') throw new IllegalStateException("AST font atlas is full at " + designSize + "px");
                return glyph('?');
            }

            graphics.setColor(Color.WHITE);
            graphics.drawString(value, cursorX + padding, cursorY + padding + ascent);
            if (designSize <= 12f) strengthenSmallGlyph(cursorX, cursorY, width, height);

            Glyph glyph = new Glyph(cursorX, cursorY, width, height, rawAdvance / RASTER_SCALE);
            glyphs.put(character, glyph);
            cursorX += width + 1;
            rowHeight = Math.max(rowHeight, height);
            dirty = true;
            return glyph;
        }

        private VisualBounds visualBounds(String text) {
            if (text == null || text.isEmpty()) return new VisualBounds(0, 0);
            GlyphVector vector = font.createGlyphVector(graphics.getFontRenderContext(), text);
            Rectangle bounds = vector.getPixelBounds(graphics.getFontRenderContext(), 0, 0);
            if (bounds.height <= 0) return new VisualBounds(0, lineHeight);
            return new VisualBounds((ascent + bounds.y) / RASTER_SCALE,
                    bounds.height / RASTER_SCALE);
        }

        /** Adds browser-like stem strength at small sizes without changing metrics. */
        private void strengthenSmallGlyph(int x, int y, int width, int height) {
            int[] alpha = new int[width * height];
            for (int py = 0; py < height; py++) {
                for (int px = 0; px < width; px++) {
                    alpha[py * width + px] = (image.getRGB(x + px, y + py) >>> 24) & 255;
                }
            }
            for (int py = 0; py < height; py++) {
                for (int px = 0; px < width; px++) {
                    int index = py * width + px;
                    int own = alpha[index];
                    int neighbor = 0;
                    if (px > 0) neighbor = Math.max(neighbor, alpha[index - 1]);
                    if (px + 1 < width) neighbor = Math.max(neighbor, alpha[index + 1]);
                    int combined = Math.max(own, Math.round(neighbor * 0.18f));
                    int corrected = Math.min(255,
                            Math.round(255f * (float) Math.pow(combined / 255f, 0.86)));
                    image.setRGB(x + px, y + py, corrected == 0 ? 0 : (corrected << 24) | 0x00FFFFFF);
                }
            }
        }

        private void upload() {
            int[] pixels = texture.getTextureData();
            image.getRGB(0, 0, atlasSize, atlasSize, pixels, 0, atlasSize);
            texture.updateDynamicTexture();
            applyLinearFiltering();
            dirty = false;
        }

        private void applyLinearFiltering() {
            int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            Minecraft.getMinecraft().getTextureManager().bindTexture(textureLocation);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GlStateManager.bindTexture(previousTexture);
        }
    }

    private static final class Glyph {
        final int x;
        final int y;
        final int width;
        final int height;
        final float advance;

        private Glyph(int x, int y, int width, int height, float advance) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.advance = advance;
        }
    }

    private static final class VisualBounds {
        final float top;
        final float height;

        private VisualBounds(float top, float height) {
            this.top = top;
            this.height = height;
        }
    }
}
