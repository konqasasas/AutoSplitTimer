package com.konqasasas.ast.ui;

import com.konqasasas.ast.core.AstCourseManager;
import org.lwjgl.input.Keyboard;

/** Review screen shown after selecting an .astcourse file. */
public final class GuiAstCourseImport extends AstUiScreen {
    private final GuiAstNativeEditor parent;
    private final AstCourseShareService.ImportPreview preview;
    private String courseName;

    public GuiAstCourseImport(GuiAstNativeEditor parent, AstCourseShareService.ImportPreview preview) {
        this.parent = parent;
        this.preview = preview;
        this.courseName = AstCourseManager.get().availableCourseName(preview.getCourseName());
    }

    @Override
    protected void drawUi(int mouseX, int mouseY, float partialTicks) {
        int w = Math.min(600, width - 40);
        int h = 356;
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        panel(x, y, w, h);
        drawRect(x, y + 53, x + w, y + 54, LINE);
        strong("コースを読み込む", x + 22, y + 17, 18, TEXT_STRONG);

        text("ファイル", x + 22, y + 72, 9, TEXT_FAINT);
        text(preview.getFileName(), x + 22, y + 91, 10, TEXT_MUTED);

        text("読み込み後のコース名", x + 22, y + 122, 9, TEXT_MUTED);
        int inputY = y + 140;
        drawRect(x + 22, inputY, x + w - 22, inputY + 34, 0xFF191D20);
        outline(x + 22, inputY, w - 44, 34, 0xFF5D6B75);
        verticallyCenteredText(courseName, x + 33, inputY, 34, 12, TEXT, false);
        hit(x + 22, inputY, w - 44, 34, () -> mc.displayGuiScreen(new GuiAstTextPrompt(this,
                "読み込み後のコース名", "既存コースは上書きしません", courseName, value -> courseName = value)));

        int infoY = y + 198;
        int infoGap = 8;
        int infoWidth = Math.max(70, (w - 44 - infoGap * 3) / 4);
        info("地点", preview.getPointCount() + "地点", x + 22, infoY, infoWidth);
        info("On Ground", preview.getGroundCount() + "地点", x + 22 + (infoWidth + infoGap), infoY, infoWidth);
        info("AABB", preview.getAabbCount() + "地点", x + 22 + (infoWidth + infoGap) * 2, infoY, infoWidth);
        info("記録", preview.includesRecords() ? "記録あり" : "記録なし",
                x + 22 + (infoWidth + infoGap) * 3, infoY, infoWidth);
        text("地点座標を含むため、作成元と同じマップで使用してください。", x + 22, y + 268, 10, TEXT_MUTED);

        quietButton("キャンセル", x + w - 212, y + h - 49, 88, 32, () -> mc.displayGuiScreen(parent));
        button("読み込む", x + w - 112, y + h - 49, 90, 32, this::importCourse);
    }

    private void info(String label, String value, int x, int y, int w) {
        drawRect(x, y, x + w, y + 50, 0xFF14181B);
        outline(x, y, w, 50, 0xFF353C41);
        text(label, x + 11, y + 9, 8, TEXT_FAINT);
        strong(value, x + 11, y + 27, 10, TEXT);
    }

    private void importCourse() {
        try {
            AstCourseShareService.importCourse(preview, courseName);
            parent.importCompleted(courseName);
            mc.displayGuiScreen(parent);
        } catch (Exception exception) {
            String message = exception.getMessage();
            notice(message == null || message.trim().isEmpty() ? "読み込みに失敗しました" : message);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws java.io.IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) mc.displayGuiScreen(parent);
        else super.keyTyped(typedChar, keyCode);
    }
}
