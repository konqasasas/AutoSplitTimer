package com.konqasasas.ast.ui;

import net.minecraft.client.gui.GuiScreen;

/** Angular confirmation dialog for destructive and replacing operations. */
public final class GuiAstConfirm extends AstUiScreen {
    private final GuiScreen parent;
    private final String title;
    private final String primary;
    private final String detail;
    private final String confirmLabel;
    private final boolean danger;
    private final Runnable onConfirm;

    public GuiAstConfirm(GuiScreen parent, String title, String primary, String detail,
                         String confirmLabel, boolean danger, Runnable onConfirm) {
        this.parent = parent;
        this.title = title;
        this.primary = primary;
        this.detail = detail;
        this.confirmLabel = confirmLabel;
        this.danger = danger;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void drawUi(int mouseX, int mouseY, float partialTicks) {
        drawRect(0, 0, width, height, CANVAS);
        int w = Math.min(500, width - 40);
        int h = 190;
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        panel(x, y, w, h);
        drawRect(x, y + 48, x + w, y + 49, LINE);
        strong(title, x + 22, y + 17, 18, TEXT_STRONG);
        strong(primary, x + 22, y + 72, 13, TEXT_STRONG);
        text(detail, x + 22, y + 98, 11, TEXT_MUTED);
        quietButton("キャンセル", x + w - 206, y + h - 48, 88, 32, () -> mc.displayGuiScreen(parent));
        if (danger) dangerButton(confirmLabel, x + w - 106, y + h - 48, 84, 32);
        else button(confirmLabel, x + w - 106, y + h - 48, 84, 32, this::confirm);
    }

    private void dangerButton(String label, int x, int y, int w, int h) {
        boolean hover = hovered(x, y, w, h);
        drawRect(x, y, x + w, y + h, hover ? 0xFF4A292E : 0xFF342126);
        outline(x, y, w, h, hover ? 0xFFD98288 : 0xFF865159);
        centeredText(label, x, y, w, h, 11, 0xFFF0C7CA, false);
        hit(x, y, w, h, this::confirm);
    }

    private void confirm() {
        onConfirm.run();
        mc.displayGuiScreen(parent);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws java.io.IOException {
        if (keyCode == 1) mc.displayGuiScreen(parent);
        else super.keyTyped(typedChar, keyCode);
    }
}
