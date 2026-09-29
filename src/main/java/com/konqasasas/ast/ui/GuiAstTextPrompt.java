package com.konqasasas.ast.ui;

import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.function.Consumer;

/** Native text prompt with clipboard support for Japanese input. */
public final class GuiAstTextPrompt extends AstUiScreen {
    private final GuiScreen parent;
    private final String title;
    private final String description;
    private final Consumer<String> submit;
    private String value;
    private int cursor;

    public GuiAstTextPrompt(GuiScreen parent, String title, String description, String initial, Consumer<String> submit) {
        this.parent = parent;
        this.title = title;
        this.description = description;
        this.value = initial == null ? "" : initial;
        this.cursor = this.value.length();
        this.submit = submit;
    }

    @Override
    public void initGui() { Keyboard.enableRepeatEvents(true); }

    @Override
    public void onGuiClosed() { Keyboard.enableRepeatEvents(false); }

    @Override
    protected void drawUi(int mouseX, int mouseY, float partialTicks) {
        int w = Math.min(520, width - 40);
        int h = 194;
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        panel(x, y, w, h);
        drawRect(x, y, x + w, y + 54, 0xFF171A1D);
        drawRect(x, y + 53, x + w, y + 54, LINE);
        strong(title, x + 20, y + 17, 18, TEXT_STRONG);
        text(description, x + 20, y + 72, 11, TEXT_MUTED);
        text("日本語は Ctrl+V で貼り付け", x + 20, y + 91, 9, TEXT_FAINT);
        int inputY = y + 111;
        drawRect(x + 20, inputY, x + w - 20, inputY + 34, 0xFF191D20);
        outline(x + 20, inputY, w - 40, 34, 0xFF6F8290);
        String shown = fitFromEnd(value, w - 58);
        verticallyCenteredText(shown, x + 29, inputY, 34, 12, TEXT, false);
        if ((System.currentTimeMillis() / 500L) % 2 == 0) {
            int caretX = x + 29 + textWidth(shown, 12, false);
            drawRect(caretX, inputY + 7, caretX + 1, inputY + 27, 0xFFDCE1E4);
        }
        quietButton("キャンセル", x + w - 194, y + h - 39, 78, 28, () -> mc.displayGuiScreen(parent));
        button("確定", x + w - 106, y + h - 39, 86, 28, this::commit);
    }

    private String fitFromEnd(String source, int maxWidth) {
        int start = 0;
        while (start < source.length() && textWidth(source.substring(start), 12, false) > maxWidth) start++;
        return source.substring(start);
    }

    private void commit() {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return;
        submit.accept(trimmed);
        mc.displayGuiScreen(parent);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) { mc.displayGuiScreen(parent); return; }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) { commit(); return; }
        if (isCtrlKeyDown() && keyCode == Keyboard.KEY_V) {
            String clip = getClipboardString();
            if (clip != null) insert(clip.replace("\r", "").replace("\n", ""));
            return;
        }
        if (keyCode == Keyboard.KEY_BACK && cursor > 0) {
            value = value.substring(0, cursor - 1) + value.substring(cursor--);
        } else if (keyCode == Keyboard.KEY_DELETE && cursor < value.length()) {
            value = value.substring(0, cursor) + value.substring(cursor + 1);
        } else if (keyCode == Keyboard.KEY_LEFT) cursor = Math.max(0, cursor - 1);
        else if (keyCode == Keyboard.KEY_RIGHT) cursor = Math.min(value.length(), cursor + 1);
        else if (keyCode == Keyboard.KEY_HOME) cursor = 0;
        else if (keyCode == Keyboard.KEY_END) cursor = value.length();
        else if (typedChar >= 32 && typedChar != 127 && value.length() < 64) insert(String.valueOf(typedChar));
    }

    private void insert(String text) {
        if (text == null || text.isEmpty()) return;
        int allowed = Math.min(text.length(), 64 - value.length());
        if (allowed <= 0) return;
        String part = text.substring(0, allowed);
        value = value.substring(0, cursor) + part + value.substring(cursor);
        cursor += part.length();
    }
}
