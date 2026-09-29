package com.konqasasas.ast.ui;

import com.konqasasas.ast.core.AstCourseManager;
import com.konqasasas.ast.core.AstData;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

import java.io.File;

/** Course share export screen. */
public final class GuiAstCourseExport extends AstUiScreen {
    private final GuiScreen parent;
    private boolean includeRecords;
    private boolean busy;
    private File savedFile;

    public GuiAstCourseExport(GuiScreen parent) { this.parent = parent; }

    @Override
    protected void drawUi(int mouseX, int mouseY, float partialTicks) {
        AstData.CourseFile course = AstCourseManager.get().getActiveCourse();
        int w = Math.min(580, width - 40);
        int h = 326;
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        panel(x, y, w, h);
        dialogHeader(x, y, w, 54);
        strong("コースを共有", x + 22, y + 17, 18, TEXT_STRONG);

        String name = course == null || course.courseName == null ? "--" : course.courseName;
        text("共有するコース", x + 22, y + 73, 9, TEXT_FAINT);
        strong(name, x + 22, y + 93, 14, TEXT_STRONG);
        text("地点座標を含むため、同じマップ向けのファイルです。", x + 22, y + 120, 10, TEXT_MUTED);

        option("コースのみ", "地点と判定範囲を共有", false, x + 22, y + 150, (w - 54) / 2, 58);
        option("記録を含める", "試行回数・PB・ベスト記録も共有", true,
                x + 32 + (w - 54) / 2, y + 150, (w - 54) / 2, 58);

        if (savedFile != null) {
            text("書き出しました: " + fit(savedFile.getAbsolutePath(), w - 44), x + 22, y + 226, 10, START);
        } else if (busy) {
            text("保存先を選択しています…", x + 22, y + 226, 10, TEXT_MUTED);
        }

        quietButton(savedFile == null ? "キャンセル" : "戻る", x + w - 252, y + h - 48, 92, 32,
                () -> mc.displayGuiScreen(parent));
        if (!busy) button(savedFile == null ? "ファイルを書き出す" : "もう一度書き出す",
                x + w - 150, y + h - 48, 128, 32, this::export);
    }

    private void option(String title, String detail, boolean records, int x, int y, int w, int h) {
        boolean selected = includeRecords == records;
        boolean hover = !busy && hovered(x, y, w, h);
        roundedOutline(x, y, w, h, 7, selected ? 0xFF222B30 : hover ? CONTROL_HOVER : 0xFF15191C,
                selected ? 0xFF71818C : 0xFF3D454B);
        roundedOutline(x + 14, y + 17, 9, 9, 2, selected ? START : 0xFF14191D,
                selected ? START : 0xFF657078);
        strong(title, x + 33, y + 11, 11, selected ? TEXT_STRONG : TEXT);
        text(detail, x + 33, y + 33, 9, TEXT_MUTED);
        if (!busy) hit(x, y, w, h, () -> includeRecords = records);
    }

    private void export() {
        AstData.CourseFile course = AstCourseManager.get().getActiveCourse();
        if (course == null) { notice("共有するコースがありません"); return; }
        busy = true;
        savedFile = null;
        AstCourseShareService.chooseExport(course, includeRecords,
                file -> { busy = false; savedFile = file; notice("コースを書き出しました"); },
                message -> { busy = false; notice(message); },
                () -> busy = false);
    }

    private String fit(String value, int maxWidth) {
        if (textWidth(value, 10, false) <= maxWidth) return value;
        String suffix = "…";
        int start = 0;
        while (start < value.length() && textWidth(suffix + value.substring(start), 10, false) > maxWidth) start++;
        return suffix + value.substring(start);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws java.io.IOException {
        if (keyCode == Keyboard.KEY_ESCAPE && !busy) mc.displayGuiScreen(parent);
        else super.keyTyped(typedChar, keyCode);
    }
}
