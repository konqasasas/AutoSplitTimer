package com.konqasasas.ast.ui;

import com.konqasasas.ast.core.AstCourseManager;
import com.konqasasas.ast.core.AstData;
import com.konqasasas.ast.hud.AstHudConfigUtil;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** Full-screen drag editor for the HUD position. */
public final class GuiAstHudPositionEditor extends AstUiScreen {
    private final GuiScreen parent;
    private AstData.HudConfig hud;
    private AstData.HudConfig original;
    private boolean initialized;
    private boolean dragging;
    private boolean closing;
    private int previewX;
    private int previewY;
    private int previewW;
    private int previewH;
    private int grabX;
    private int grabY;

    public GuiAstHudPositionEditor(GuiScreen parent) { this.parent = parent; }

    @Override
    public void initGui() {
        hud = AstCourseManager.get().getHudConfig();
        AstHudConfigUtil.normalizeHud(hud);
        original = AstHudConfigUtil.copyHud(hud);
    }

    @Override
    protected void drawUi(int mouseX, int mouseY, float partialTicks) {
        AstNativeHudRenderer.PositionPreview metrics = AstNativeHudRenderer.positionPreviewSize(hud);
        previewW = metrics.width;
        previewH = metrics.height;
        if (!initialized) {
            readConfiguredPosition();
            initialized = true;
        }

        if (dragging) {
            if (Mouse.isButtonDown(0)) {
                previewX = clamp(mouseX - grabX, 0, Math.max(0, width - previewW));
                previewY = clamp(mouseY - grabY, 0, Math.max(0, height - previewH));
                writeConfiguredPosition();
            } else {
                dragging = false;
            }
        }

        drawRect(width / 2, 0, width / 2 + 1, height, 0xFF1B2023);
        drawRect(0, height / 2, width, height / 2 + 1, 0xFF1B2023);

        AstNativeHudRenderer.drawPositionPreview(hud, previewX, previewY);
        outline(previewX - 2, previewY - 2, previewW + 4, previewH + 4,
                dragging ? 0xFF8FA5B3 : 0xFF53616A);
        hit(previewX - 4, previewY - 4, previewW + 8, previewH + 8, () -> {
            dragging = true;
            grabX = this.mouseX - previewX;
            grabY = this.mouseY - previewY;
        });

        int barW = Math.min(610, width - 28);
        int barX = (width - barW) / 2;
        int barY = 14;
        drawRect(barX, barY, barX + barW, barY + 52, 0xF5181C1F);
        outline(barX, barY, barW, 52, 0xFF3D464C);
        strong("HUDをドラッグして移動", barX + 16, barY + 11, 12, TEXT_STRONG);
        text("離した位置に合わせて基準辺を自動調整します", barX + 16, barY + 31, 9, TEXT_MUTED);
        quietButton("キャンセル", barX + barW - 188, barY + 10, 78, 31, this::cancel);
        button("完了", barX + barW - 98, barY + 10, 82, 31, this::complete);
    }

    private void readConfiguredPosition() {
        ScaledResolution resolution = new ScaledResolution(mc);
        double factor = Math.max(1, resolution.getScaleFactor());
        double actualW = previewW * DESIGN_ZOOM / factor;
        double actualH = previewH * DESIGN_ZOOM / factor;
        String anchor = hud.anchor == null ? "TOP_LEFT" : hud.anchor.toUpperCase(java.util.Locale.ROOT);
        double actualX;
        if (anchor.endsWith("RIGHT")) actualX = resolution.getScaledWidth_double() - actualW - hud.offsetX;
        else if (anchor.endsWith("CENTER") || "CENTER".equals(anchor))
            actualX = (resolution.getScaledWidth_double() - actualW) / 2.0 + hud.offsetX;
        else actualX = hud.offsetX;
        double actualY;
        if (anchor.startsWith("BOTTOM")) actualY = resolution.getScaledHeight_double() - actualH - hud.offsetY;
        else if (anchor.startsWith("CENTER") || "CENTER".equals(anchor))
            actualY = (resolution.getScaledHeight_double() - actualH) / 2.0 + hud.offsetY;
        else actualY = hud.offsetY;
        previewX = clamp((int) Math.round(actualX * factor / DESIGN_ZOOM), 0, Math.max(0, width - previewW));
        previewY = clamp((int) Math.round(actualY * factor / DESIGN_ZOOM), 0, Math.max(0, height - previewH));
        writeConfiguredPosition();
    }

    private void writeConfiguredPosition() {
        ScaledResolution resolution = new ScaledResolution(mc);
        double factor = Math.max(1, resolution.getScaleFactor());
        double actualX = previewX * DESIGN_ZOOM / factor;
        double actualY = previewY * DESIGN_ZOOM / factor;
        double actualW = previewW * DESIGN_ZOOM / factor;
        double actualH = previewH * DESIGN_ZOOM / factor;
        double centerX = previewX + previewW / 2.0;
        double centerY = previewY + previewH / 2.0;

        String horizontal;
        if (centerX < width / 3.0) horizontal = "LEFT";
        else if (centerX > width * 2.0 / 3.0) horizontal = "RIGHT";
        else horizontal = "CENTER";
        String vertical;
        if (centerY < height / 3.0) vertical = "TOP";
        else if (centerY > height * 2.0 / 3.0) vertical = "BOTTOM";
        else vertical = "CENTER";

        hud.anchor = "CENTER".equals(vertical) && "CENTER".equals(horizontal)
                ? "CENTER" : vertical + "_" + horizontal;
        if ("LEFT".equals(horizontal)) hud.offsetX = (int) Math.round(actualX);
        else if ("RIGHT".equals(horizontal))
            hud.offsetX = (int) Math.round(resolution.getScaledWidth_double() - actualW - actualX);
        else hud.offsetX = (int) Math.round(actualX - (resolution.getScaledWidth_double() - actualW) / 2.0);
        if ("TOP".equals(vertical)) hud.offsetY = (int) Math.round(actualY);
        else if ("BOTTOM".equals(vertical))
            hud.offsetY = (int) Math.round(resolution.getScaledHeight_double() - actualH - actualY);
        else hud.offsetY = (int) Math.round(actualY - (resolution.getScaledHeight_double() - actualH) / 2.0);
    }

    private void complete() {
        writeConfiguredPosition();
        AstCourseManager.get().saveHudConfig(hud);
        closing = true;
        mc.displayGuiScreen(parent);
    }

    private void cancel() {
        AstCourseManager.get().saveHudConfig(original);
        closing = true;
        mc.displayGuiScreen(parent);
    }

    @Override
    public void onGuiClosed() {
        if (!closing && original != null) AstCourseManager.get().saveHudConfig(original);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws java.io.IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) cancel();
        else super.keyTyped(typedChar, keyCode);
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
