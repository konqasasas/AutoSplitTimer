package com.konqasasas.ast.ui;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Shared visual language for the dependency-free native interface. */
public abstract class AstUiScreen extends GuiScreen {
    /** UI density kept stable across Minecraft GUI scales. */
    static final float DESIGN_ZOOM = 1.25f;
    private static final float UI_FONT_SCALE = 1.08f;
    protected static final int CANVAS = 0xFF0E1115;
    protected static final int TOPBAR = 0xFF101419;
    protected static final int SURFACE = 0xFF151A20;
    protected static final int SURFACE_SUBTLE = 0xFF12171C;
    protected static final int CONTROL = 0xFF1A2027;
    protected static final int CONTROL_HOVER = 0xFF222A33;
    protected static final int CONTROL_ACTIVE = 0xFF202832;
    protected static final int LINE = 0xFF2B343F;
    protected static final int LINE_STRONG = 0xFF3B4754;
    protected static final int LINE_CONTROL = 0xFF526170;
    protected static final int TEXT = 0xFFDCE3E8;
    protected static final int TEXT_STRONG = 0xFFEEF2F5;
    protected static final int TEXT_MUTED = 0xFF98A4AF;
    protected static final int TEXT_FAINT = 0xFF6F7B86;
    protected static final int ACCENT = 0xFF6EA8FE;
    protected static final int ACCENT_HOVER = 0xFF8BB8FF;
    protected static final int START = 0xFF6FCF87;
    protected static final int LAP = 0xFFD2AA4F;
    protected static final int GOAL = 0xFFE26D74;
    protected static final int DANGER = 0xFFE26D74;

    private final List<Hit> hits = new ArrayList<>();
    protected int mouseX;
    protected int mouseY;
    protected int scroll;
    protected String notice;
    protected long noticeUntil;
    private boolean clipActive;
    private int clipX, clipY, clipW, clipH;
    private float inputToDesignScale = 1f;

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        int minecraftWidth = width;
        int minecraftHeight = height;
        int minecraftScale = Math.max(1, new ScaledResolution(mc).getScaleFactor());
        float designToMinecraftScale = DESIGN_ZOOM / minecraftScale;
        inputToDesignScale = 1f / designToMinecraftScale;

        // GuiScreen coordinates normally grow with Minecraft's GUI Scale. The AST
        // interface was designed in screen pixels (like the former browser UI), so
        // use a pixel-sized virtual canvas and cancel Minecraft's scale once here.
        width = Math.round(minecraftWidth * inputToDesignScale);
        height = Math.round(minecraftHeight * inputToDesignScale);
        int virtualMouseX = Math.round(mouseX * inputToDesignScale);
        int virtualMouseY = Math.round(mouseY * inputToDesignScale);
        this.mouseX = virtualMouseX;
        this.mouseY = virtualMouseY;
        hits.clear();
        AstFonts.beginFrame();

        GlStateManager.pushMatrix();
        GlStateManager.scale(designToMinecraftScale, designToMinecraftScale, 1f);
        try {
            drawRect(0, 0, width, height, CANVAS);
            drawUi(virtualMouseX, virtualMouseY, partialTicks);
            if (notice != null && System.currentTimeMillis() < noticeUntil) drawNotice(notice);
            else notice = null;
        } finally {
            if (clipActive) endClip();
            AstFonts.endFrame();
            GlStateManager.popMatrix();
            width = minecraftWidth;
            height = minecraftHeight;
        }
    }

    protected abstract void drawUi(int mouseX, int mouseY, float partialTicks);

    protected void hit(int x, int y, int w, int h, Runnable action) {
        if (clipActive) {
            int right = Math.min(x + w, clipX + clipW);
            int bottom = Math.min(y + h, clipY + clipH);
            x = Math.max(x, clipX);
            y = Math.max(y, clipY);
            w = right - x;
            h = bottom - y;
            if (w <= 0 || h <= 0) return;
        }
        hits.add(new Hit(x, y, w, h, action));
    }

    protected void beginClip(int x, int y, int w, int h) {
        clipActive = true;
        clipX = x; clipY = y; clipW = Math.max(0, w); clipH = Math.max(0, h);
        double sx = mc.displayWidth / (double) Math.max(1, width);
        double sy = mc.displayHeight / (double) Math.max(1, height);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor((int) Math.floor(x * sx), (int) Math.floor((height - y - h) * sy),
                (int) Math.ceil(w * sx), (int) Math.ceil(h * sy));
    }

    protected void endClip() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        clipActive = false;
    }

    protected boolean hovered(int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    protected void panel(int x, int y, int w, int h) {
        softShadow(x, y, w, h, 8);
        roundedOutline(x, y, w, h, 8, SURFACE, LINE);
    }

    protected void outline(int x, int y, int w, int h, int color) {
        drawRect(x, y, x + w, y + 1, color);
        drawRect(x, y + h - 1, x + w, y + h, color);
        drawRect(x, y, x + 1, y + h, color);
        drawRect(x + w - 1, y, x + w, y + h, color);
    }

    /** Rounded fill backed by a supersampled alpha mask. */
    protected void roundedRect(int x, int y, int w, int h, int radius, int color) {
        AstRoundedRenderer.draw(x, y, x + w, y + h, radius, color);
    }

    protected void roundedOutline(int x, int y, int w, int h, int radius, int fill, int border) {
        AstRoundedRenderer.draw(x, y, x + w, y + h, radius, border);
        float inset = 1f / DESIGN_ZOOM;
        if (w > inset * 2 && h > inset * 2) {
            AstRoundedRenderer.draw(x + inset, y + inset, x + w - inset, y + h - inset,
                    Math.max(0, radius - inset), fill);
        }
    }

    protected void inputField(int x, int y, int w, int h, boolean focused) {
        roundedOutline(x, y, w, h, 6, 0xFF12171C, focused ? ACCENT : LINE_CONTROL);
    }

    private void softShadow(int x, int y, int w, int h, int radius) {
        roundedRect(x - 3, y + 4, w + 6, h + 3, radius + 3, 0x18000000);
        roundedRect(x - 2, y + 3, w + 4, h + 2, radius + 2, 0x22000000);
        roundedRect(x - 1, y + 2, w + 2, h + 1, radius + 1, 0x2B000000);
    }

    /** Font-independent disclosure mark, so missing Unicode glyphs never show boxes. */
    protected void chevron(int x, int y, int size, boolean up, int color) {
        AstIcons.drawChevron(x, y, size, up, color);
    }

    protected void text(String value, float x, float y, float size, int color) {
        drawUiText(value, x, y, scaledFontSize(size), color, false);
    }

    protected void strong(String value, float x, float y, float size, int color) {
        drawUiText(value, x, y, scaledFontSize(size), color, true);
    }

    protected void mono(String value, float x, float y, float size, int color) {
        AstFonts.mono().draw(value, snap(x), snap(y), scaledFontSize(size), color);
    }

    protected int textWidth(String value, float size, boolean bold) {
        if (value == null || value.isEmpty()) return 0;
        size = scaledFontSize(size);
        int width = 0;
        int start = 0;
        AstFont current = AstFonts.ui(value.charAt(0), bold);
        for (int i = 1; i <= value.length(); i++) {
            AstFont next = i < value.length() ? AstFonts.ui(value.charAt(i), bold) : null;
            if (next != current) {
                width += current.width(value.substring(start, i), size);
                start = i;
                current = next;
            }
        }
        return width;
    }

    protected int monoWidth(String value, float size) {
        return AstFonts.mono().width(value, scaledFontSize(size));
    }

    protected void centeredText(String value, int x, int y, int w, int h, float size, int color, boolean bold) {
        float actualSize = scaledFontSize(size);
        int tw = textWidth(value, size, bold);
        drawUiTextAtVisualCenter(value, x + (w - tw) / 2f, y + h / 2f, actualSize, color, bold);
    }

    protected void centeredMono(String value, int x, int y, int w, int h, float size, int color) {
        float actualSize = scaledFontSize(size);
        int tw = AstFonts.mono().width(value, actualSize);
        float inkTop = AstFonts.mono().visualTop(value, actualSize);
        float inkHeight = AstFonts.mono().visualHeight(value, actualSize);
        float drawY = y + (h - inkHeight) / 2f - inkTop;
        AstFonts.mono().draw(value, snap(x + (w - tw) / 2f), snapHalf(drawY), actualSize, color);
    }

    protected void verticallyCenteredText(String value, float x, int y, int h, float size, int color, boolean bold) {
        float actualSize = scaledFontSize(size);
        drawUiTextAtVisualCenter(value, x, y + h / 2f, actualSize, color, bold);
    }

    protected void verticallyCenteredMono(String value, float x, int y, int h, float size, int color) {
        float actualSize = scaledFontSize(size);
        float inkTop = AstFonts.mono().visualTop(value, actualSize);
        float inkHeight = AstFonts.mono().visualHeight(value, actualSize);
        AstFonts.mono().draw(value, snap(x), snapHalf(y + (h - inkHeight) / 2f - inkTop), actualSize, color);
    }

    protected float scaledFontSize(float size) {
        return Math.round(size * UI_FONT_SCALE * 2f) / 2f;
    }

    /** Draw every fallback-font run on one shared optical center line. */
    private void drawUiTextAtVisualCenter(String value, float x, float centerY, float actualSize,
                                          int color, boolean bold) {
        if (value == null || value.isEmpty()) return;
        float pen = snap(x);
        int start = 0;
        AstFont current = AstFonts.ui(value.charAt(0), bold);
        for (int i = 1; i <= value.length(); i++) {
            AstFont next = i < value.length() ? AstFonts.ui(value.charAt(i), bold) : null;
            if (next != current) {
                String run = value.substring(start, i);
                float runTop = current.visualTop(run, actualSize);
                float runHeight = current.visualHeight(run, actualSize);
                float drawY = centerY - runTop - runHeight / 2f;
                current.draw(run, pen, snapHalf(drawY), actualSize, color);
                pen += current.width(run, actualSize);
                start = i;
                current = next;
            }
        }
    }

    private void drawUiText(String value, float x, float y, float size, int color, boolean bold) {
        if (value == null || value.isEmpty()) return;

        // A mixed Japanese/Latin string is split across BIZ UDPGothic and IBM
        // Plex. Their atlas origins, ascenders and visible ink boxes differ, so
        // sending the same raw y to both fonts makes Latin text sit lower. Use
        // the tallest visible run as the line's optical reference and center
        // every fallback run on that shared axis. Single-font text keeps its
        // original position exactly.
        AstFont firstFont = AstFonts.ui(value.charAt(0), bold);
        boolean mixedFonts = false;
        float referenceTop = 0f;
        float referenceHeight = -1f;
        int measureStart = 0;
        AstFont measureFont = firstFont;
        for (int i = 1; i <= value.length(); i++) {
            AstFont next = i < value.length() ? AstFonts.ui(value.charAt(i), bold) : null;
            if (next != measureFont) {
                String run = value.substring(measureStart, i);
                float runHeight = measureFont.visualHeight(run, size);
                if (runHeight > referenceHeight) {
                    referenceTop = measureFont.visualTop(run, size);
                    referenceHeight = runHeight;
                }
                if (next != null && next != firstFont) mixedFonts = true;
                measureStart = i;
                measureFont = next;
            }
        }
        if (!mixedFonts) {
            firstFont.draw(value, snap(x), snap(y), size, color);
            return;
        }

        float visualCenter = y + referenceTop + referenceHeight / 2f;
        float pen = snap(x);
        int start = 0;
        AstFont current = firstFont;
        for (int i = 1; i <= value.length(); i++) {
            AstFont next = i < value.length() ? AstFonts.ui(value.charAt(i), bold) : null;
            if (next != current) {
                String run = value.substring(start, i);
                float runTop = current.visualTop(run, size);
                float runHeight = current.visualHeight(run, size);
                float drawY = visualCenter - runTop - runHeight / 2f;
                current.draw(run, pen, snapHalf(drawY), size, color);
                pen += current.width(run, size);
                start = i;
                current = next;
            }
        }
    }

    private static float snap(float value) {
        return Math.round(value * DESIGN_ZOOM) / DESIGN_ZOOM;
    }

    private static float snapHalf(float value) {
        return Math.round(value * DESIGN_ZOOM * 2f) / (DESIGN_ZOOM * 2f);
    }

    protected void button(String label, int x, int y, int w, int h, Runnable action) {
        boolean hover = hovered(x, y, w, h);
        roundedOutline(x, y, w, h, 6, hover ? CONTROL_HOVER : CONTROL,
                hover ? 0xFF657585 : LINE_CONTROL);
        centeredText(label, x, y, w, h, 11, hover ? TEXT_STRONG : TEXT, false);
        hit(x, y, w, h, action);
    }

    protected void primaryButton(String label, int x, int y, int w, int h, Runnable action) {
        boolean hover = hovered(x, y, w, h);
        roundedOutline(x, y, w, h, 6, hover ? 0xFF3976C9 : 0xFF2E66B8,
                hover ? ACCENT_HOVER : ACCENT);
        centeredText(label, x, y, w, h, 11, TEXT_STRONG, true);
        hit(x, y, w, h, action);
    }

    protected void quietButton(String label, int x, int y, int w, int h, Runnable action) {
        boolean hover = hovered(x, y, w, h);
        roundedOutline(x, y, w, h, 6, hover ? CONTROL_HOVER : CONTROL,
                hover ? 0xFF657585 : LINE_CONTROL);
        centeredText(label, x, y, w, h, 11, hover ? TEXT_STRONG : TEXT_MUTED, false);
        hit(x, y, w, h, action);
    }

    protected void notice(String message) {
        notice = message;
        noticeUntil = System.currentTimeMillis() + 2800L;
    }

    private void drawNotice(String message) {
        int w = Math.max(180, textWidth(message, 11, false) + 34);
        int x = width - w - 16;
        int y = height - 42;
        softShadow(x, y, w, 30, 7);
        roundedOutline(x, y, w, 30, 7, 0xF01A2027, LINE_CONTROL);
        drawRect(x + 10, y + 12, x + 16, y + 18, START);
        text(message, x + 23, y + 8, 11, TEXT);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 0) {
            int virtualMouseX = Math.round(mouseX * inputToDesignScale);
            int virtualMouseY = Math.round(mouseY * inputToDesignScale);
            for (int index = hits.size() - 1; index >= 0; index--) {
                Hit hit = hits.get(index);
                if (hit.contains(virtualMouseX, virtualMouseY)) {
                    hit.action.run();
                    return;
                }
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            scroll = Math.max(0, scroll + (wheel < 0 ? 28 : -28));
        }
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }

    private static final class Hit {
        final int x, y, w, h;
        final Runnable action;
        Hit(int x, int y, int w, int h, Runnable action) { this.x = x; this.y = y; this.w = w; this.h = h; this.action = action; }
        boolean contains(int px, int py) { return px >= x && px < x + w && py >= y && py < y + h; }
    }
}
