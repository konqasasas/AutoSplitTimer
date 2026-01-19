package com.konqasasas.ast.hud;

import com.konqasasas.ast.core.AstCourseManager;
import com.konqasasas.ast.core.AstData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSlot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Simple layout picker (MVP). */
public class GuiAstLayoutList extends GuiScreen {
    private final GuiScreen parent;
    private LayoutSlot slot;
    private List<String> names = new ArrayList<>();
    private int selected = -1;

    public GuiAstLayoutList(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        names = AstLayoutManager.listLayouts();
        slot = new LayoutSlot(Minecraft.getMinecraft(), this.width, this.height, 32, this.height - 48, 18);

        int bx = this.width / 2 - 110;
        int by = this.height - 40;
        buttonList.add(new GuiButton(1, bx, by, 70, 20, "Load"));
        buttonList.add(new GuiButton(2, bx + 74, by, 70, 20, "Save As"));
        buttonList.add(new GuiButton(3, bx + 148, by, 70, 20, "Back"));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case 1:
                loadSelected();
                return;
            case 2:
                mc.displayGuiScreen(new GuiAstLayoutSave(this));
                return;
            case 3:
                mc.displayGuiScreen(parent);
                return;
            default:
                break;
        }
        super.actionPerformed(button);
    }

    private void loadSelected() {
        if (selected < 0 || selected >= names.size()) return;
        String name = names.get(selected);
        AstData.HudConfig loaded = AstLayoutManager.loadLayoutSafe(name);
        if (loaded == null) return;

        AstData.CourseFile cf = AstCourseManager.get().getActiveCourse();
        if (cf == null) return;

        // Keep current preset name (presets are still a concept), but replace actual HUD settings.
        String keepPreset = cf.hud != null ? cf.hud.preset : loaded.preset;
        cf.hud = loaded;
        cf.hud.preset = keepPreset;
        AstHudConfigUtil.normalizeHud(cf.hud);
        AstCourseManager.get().saveActiveCourseSafe();
        AstHudRenderer.requestRebuild();

        // Return to editor and let it refresh fields.
        mc.displayGuiScreen(parent);
        if (parent instanceof GuiAstHudEditor) {
            ((GuiAstHudEditor) parent).refreshFromModel();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRenderer, "AST Layouts", this.width / 2, 12, 0xFFFFFF);
        if (slot != null) slot.drawScreen(mouseX, mouseY, partialTicks);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        if (slot != null) slot.handleMouseInput();
    }



    private class LayoutSlot extends GuiSlot {
        LayoutSlot(Minecraft mcIn, int widthIn, int heightIn, int topIn, int bottomIn, int slotHeightIn) {
            super(mcIn, widthIn, heightIn, topIn, bottomIn, slotHeightIn);
        }

        @Override
        protected int getSize() {
            return names == null ? 0 : names.size();
        }

        @Override
        protected void elementClicked(int index, boolean doubleClick, int mouseX, int mouseY) {
            selected = index;
            if (doubleClick) loadSelected();
        }

        @Override
        protected boolean isSelected(int index) {
            return index == selected;
        }

        @Override
        protected void drawBackground() {
            // handled by parent
        }

        @Override
        protected void drawSlot(int entryID, int insideLeft, int yPos, int heightIn, int mouseX, int mouseY, float partialTicks) {
            String n = names.get(entryID);
            fontRenderer.drawString(n, insideLeft + 2, yPos + 2, 0xFFFFFF);
        }
    }


}
