package com.konqasasas.ast.hud;

import com.konqasasas.ast.core.AstCourseManager;
import com.konqasasas.ast.core.AstData;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

/** Simple "save layout" prompt (MVP). */
public class GuiAstLayoutSave extends GuiScreen {
    private final GuiScreen parent;
    private GuiTextField tfName;

    public GuiAstLayoutSave(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        Keyboard.enableRepeatEvents(true);
        int cx = this.width / 2;
        int cy = this.height / 2;
        tfName = new GuiTextField(1, fontRenderer, cx - 100, cy - 10, 200, 20);
        tfName.setFocused(true);
        tfName.setText("my_layout");
        buttonList.add(new GuiButton(2, cx - 110, cy + 20, 90, 20, "Save"));
        buttonList.add(new GuiButton(3, cx + 20, cy + 20, 90, 20, "Cancel"));
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 2) {
            String name = tfName != null ? tfName.getText() : "";
            name = name == null ? "" : name.trim();
            if (!name.isEmpty()) {
                AstData.CourseFile cf = AstCourseManager.get().getActiveCourse();
                if (cf != null && cf.hud != null) {
                    AstLayoutManager.saveLayoutSafe(name, cf.hud);
                }
            }
            mc.displayGuiScreen(parent);
            return;
        }
        if (button.id == 3) {
            mc.displayGuiScreen(parent);
            return;
        }
        super.actionPerformed(button);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1) { // ESC
            mc.displayGuiScreen(parent);
            return;
        }
        if (tfName != null && tfName.textboxKeyTyped(typedChar, keyCode)) return;
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (tfName != null) tfName.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRenderer, "Save HUD layout as...", this.width / 2, this.height / 2 - 40, 0xFFFFFF);
        if (tfName != null) tfName.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
