package com.konqasasas.ast.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.Locale;

/** Native course editor. */
public final class GuiAstNativeEditor extends AstUiScreen {
    private JsonObject state;
    private int openIndex = -1;
    private boolean courseMenu;
    private boolean toolsMenu;
    private int heightEditIndex = -1;
    private String heightInput = "";
    private boolean replaceHeightInput;

    @Override
    public void initGui() {
        refresh();
    }

    @Override
    protected void drawUi(int mouseX, int mouseY, float partialTicks) {
        drawTopbar();
        JsonObject active = active();
        if (active == null) {
            drawEmpty();
            if (courseMenu) drawCourseMenu();
            return;
        }
        int workspaceWidth = Math.min(720, width - 48);
        int left = (width - workspaceWidth) / 2;
        int top = 67;
        strong("コース編集", left, top, 22, TEXT_STRONG);
        quietButton("コース設定", left + 145, top - 5, 104, 28, () -> toolsMenu = !toolsMenu);
        chevron(left + 235, top + 6, 7, toolsMenu, TEXT_FAINT);
        int lapButtonX = left + workspaceWidth - 198;
        primaryButton("+ 現在地をラップとして追加", lapButtonX, top - 5, 198, 34,
                () -> action(request("addLapCurrent"), "現在地にラップを追加しました"));

        int listTop = top + 55;
        int listBottom = height - 22;
        JsonArray points = active.getAsJsonArray("points");
        int totalHeight = 0;
        for (int i = 0; i < points.size(); i++) {
            JsonObject point = points.get(i).getAsJsonObject();
            boolean open = openIndex == point.get("index").getAsInt();
            totalHeight += pointHeight(point, open);
        }
        int visibleHeight = Math.max(1, listBottom - listTop);
        int maxScroll = Math.max(0, totalHeight - visibleHeight);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        int cardHeight = Math.min(totalHeight, visibleHeight);
        if (cardHeight > 0) roundedOutline(left, listTop, workspaceWidth, cardHeight, 7, SURFACE, LINE_STRONG);
        int y = listTop - scroll;
        beginClip(left, listTop, workspaceWidth, Math.max(1, listBottom - listTop));
        for (int i = 0; i < points.size(); i++) {
            JsonObject point = points.get(i).getAsJsonObject();
            int index = point.get("index").getAsInt();
            boolean open = openIndex == index;
            int rowHeight = pointHeight(point, open);
            if (y + rowHeight >= 45 && y < height) drawPoint(point, i, points.size(), left, y, workspaceWidth, rowHeight, open);
            y += rowHeight;
        }
        endClip();
        if (maxScroll > 0) drawListScrollbar(left + workspaceWidth - 3, listTop + 4, listBottom - listTop - 8, totalHeight, visibleHeight, maxScroll);
        if (toolsMenu) drawToolsMenu(left + 145, top + 26);
        if (courseMenu) drawCourseMenu();
    }

    private void drawListScrollbar(int x, int y, int h, int total, int visible, int maxScroll) {
        roundedRect(x, y, 3, h, 2, 0xFF181E24);
        int thumb = Math.max(24, Math.min(h, Math.round(h * (visible / (float) Math.max(visible, total)))));
        int travel = h - thumb;
        int thumbY = y + Math.round(travel * (scroll / (float) maxScroll));
        roundedRect(x, thumbY, 3, thumb, 2, 0xFF617181);
    }

    private void drawTopbar() {
        drawRect(0, 0, width, 44, TOPBAR);
        drawRect(0, 43, width, 44, LINE_STRONG);
        drawRect(16, 15, 24, 20, START);
        drawRect(16, 20, 24, 24, LAP);
        drawRect(16, 24, 24, 29, GOAL);
        strong("AUTOSPLIT TIMER", 32, 15, 10, 0xFFCBD1D5);
        drawRect(176, 0, 177, 44, LINE);
        String course = active() == null ? "コース未選択" : string(active(), "name", "コース未選択");
        boolean hover = hovered(177, 0, 190, 44);
        if (hover || courseMenu) drawRect(177, 0, 367, 44, CONTROL);
        text(fit(course, 137, 12), 192, 14, 12, TEXT);
        chevron(344, 18, 8, courseMenu, TEXT_FAINT);
        hit(177, 0, 190, 44, () -> courseMenu = !courseMenu);
        boolean quick = state != null && state.has("quickCourse") && state.get("quickCourse").getAsBoolean();
        quietButton(quick ? "簡易計測を終了" : "簡易START / GOAL", width - 226, 8, 132, 28, () -> {
            if (quick) action(request("endQuickCourse"), "簡易計測を終了しました");
            else {
                action(request("beginQuickSetup"), null);
                mc.displayGuiScreen(null);
            }
        });
        quietButton("HUD設定", width - 86, 8, 72, 28, () -> mc.displayGuiScreen(new GuiAstNativeHudEditor(this)));
    }

    private void drawEmpty() {
        int cx = width / 2;
        strong("コースがありません", cx - 86, height / 2 - 32, 18, TEXT_STRONG);
        text("新しいコースを作成すると、現在地にSTARTが置かれます", cx - 174, height / 2 - 4, 11, TEXT_MUTED);
        primaryButton("新規コース", cx - 122, height / 2 + 28, 112, 34, this::promptCreate);
        quietButton("コースを読み込む", cx + 2, height / 2 + 28, 120, 34, this::chooseImport);
    }

    private void drawCourseMenu() {
        int x = 177, y = 44, w = 214;
        JsonArray names = state == null ? new JsonArray() : state.getAsJsonArray("courseNames");
        int h = names.size() * 38 + 78;
        panel(x, y, w, h);
        String current = active() == null ? "" : string(active(), "name", "");
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i).getAsString();
            int rowY = y + i * 38;
            if (hovered(x, rowY, w, 38)) roundedRect(x + 5, rowY + 3, w - 10, 32, 5, CONTROL_HOVER);
            text(name, x + 13, rowY + 12, 11, name.equals(current) ? TEXT_STRONG : 0xFFBDC4C8);
            if (name.equals(current)) text("使用中", x + w - 47, rowY + 13, 9, START);
            final String selected = name;
            hit(x, rowY, w, 38, () -> { action(request("loadCourse", "name", selected), null); courseMenu = false; openIndex = -1; });
            drawRect(x + 1, rowY + 37, x + w - 1, rowY + 38, 0xFF292E32);
        }
        int createY = y + names.size() * 38;
        text("+ 新規コース", x + 13, createY + 12, 11, TEXT_STRONG);
        hit(x, createY, w, 39, () -> { courseMenu = false; promptCreate(); });
        drawRect(x + 1, createY + 38, x + w - 1, createY + 39, 0xFF292E32);
        text("コースを読み込む", x + 13, createY + 51, 11, 0xFFBDC4C8);
        hit(x, createY + 39, w, 39, () -> { courseMenu = false; chooseImport(); });
    }

    private void drawToolsMenu(int x, int y) {
        int w = 154, h = 144;
        panel(x, y, w, h);
        menuItem("コース名を変更", x, y, w, () -> {
            toolsMenu = false;
            JsonObject active = active();
            if (active == null) return;
            String old = string(active, "name", "");
            mc.displayGuiScreen(new GuiAstTextPrompt(this, "コース名を変更", "新しいコース名", old,
                    value -> action(request("renameCourse", "oldName", old, "newName", value), "コース名を変更しました")));
        }, false);
        menuItem("コースを共有", x, y + 36, w, () -> {
            toolsMenu = false;
            mc.displayGuiScreen(new GuiAstCourseExport(this));
        }, false);
        menuItem("記録をリセット", x, y + 72, w, () -> {
            toolsMenu = false;
            mc.displayGuiScreen(new GuiAstConfirm(this, "記録をリセット", "計測タイムを削除します", "試行回数は残ります。", "リセット", false,
                    () -> action(request("resetRecords", "target", "all"), "記録をリセットしました")));
        }, false);
        menuItem("コースを削除", x, y + 108, w, () -> {
            toolsMenu = false;
            JsonObject active = active();
            if (active != null) {
                String courseName = string(active, "name", "");
                mc.displayGuiScreen(new GuiAstConfirm(this, "コースを削除", courseName, "地点と記録がすべて削除されます。", "削除", true,
                        () -> action(request("deleteCourse", "name", courseName), "コースを削除しました")));
            }
        }, true);
    }

    private void menuItem(String label, int x, int y, int w, Runnable action, boolean danger) {
        if (hovered(x, y, w, 36)) drawRect(x + 1, y + 1, x + w - 1, y + 35, danger ? 0xFF2B1E21 : CONTROL_HOVER);
        verticallyCenteredText(label, x + 12, y, 36, 10, danger ? 0xFFEFC2C5 : 0xFFBDC4C8, false);
        hit(x, y, w, 36, action);
    }

    private void drawPoint(JsonObject point, int position, int total, int x, int y, int w, int h, boolean open) {
        String role = string(point, "role", "lap");
        int roleColor = "start".equals(role) ? START : "goal".equals(role) ? GOAL : LAP;
        drawPointBackground(position, total, x, y, w, h, open ? CONTROL_ACTIVE : SURFACE);
        verticallyCenteredMono(String.format(Locale.ROOT, "%02d", position + 1), x + 15, y, 48, 11, 0xFF727C84);
        drawRect(x + 44, y + 20, x + 51, y + 27, roleColor);
        verticallyCenteredText(role.toUpperCase(Locale.ROOT), x + 61, y, 48, 10, roleColor, true);
        String name = string(point, "name", "");
        verticallyCenteredText(name, x + 115, y, 48, 12, 0xFFDCE1E4, false);
        boolean placed = point.get("placed").getAsBoolean();
        String meta = placed ? ("ground".equals(string(point, "detection", "ground")) ? "On Ground" : "AABB")
                + "  ·  " + point.get("blocks").getAsInt() + "ブロック" : "場所未設定";
        int metaWidth = textWidth(meta, 10, false);
        verticallyCenteredText(meta, x + w - 76 - metaWidth, y, 48, 10, placed ? 0xFF929BA2 : 0xFFD18A90, false);
        chevron(x + w - 27, y + 20, 8, open, TEXT_FAINT);
        int index = point.get("index").getAsInt();
        hit(x, y, w, 48, () -> {
            heightEditIndex = -1;
            openIndex = open ? -1 : index;
        });

        if (!open) return;
        int editorY = y + 48;
        drawRect(x + 1, editorY, x + w - 1, y + h - 1, SURFACE_SUBTLE);
        drawRect(x + 1, editorY, x + w - 1, editorY + 1, 0xFF292E32);
        if (!placed) {
            text("ゴール地点に立って設定", x + 70, editorY + 25, 11, TEXT_MUTED);
            button("現在地をゴールに設定", x + w - 181, editorY + 14, 166, 32,
                    () -> action(request("setPointCurrent", "index", index), "GOALを現在地に設定しました"));
            return;
        }
        text("地点名", x + 70, editorY + 10, 9, TEXT_MUTED);
        int fieldY = editorY + 25;
        inputField(x + 70, fieldY, 180, 30, false);
        text(name, x + 79, fieldY + 8, 11, TEXT);
        hit(x + 70, fieldY, 180, 30, () -> mc.displayGuiScreen(new GuiAstTextPrompt(this, "地点名を変更", "新しい地点名", name,
                value -> action(request("updatePoint", "index", index, "name", value), "地点名を変更しました"))));
        text("判定方式", x + 268, editorY + 10, 9, TEXT_MUTED);
        segmentButton("On Ground", x + 268, fieldY, 72, 30, "ground".equals(string(point, "detection", "ground")),
                () -> action(request("updatePoint", "index", index, "detection", "ground"), null));
        segmentButton("AABB", x + 340, fieldY, 55, 30, "aabb".equals(string(point, "detection", "ground")),
                () -> action(request("updatePoint", "index", index, "detection", "aabb"), null));
        int actionX = x + w - 289;
        button("現在地に変更", actionX, fieldY, 91, 30, () -> action(request("setPointCurrent", "index", index), "現在地に変更しました"));
        button("範囲を編集", actionX + 97, fieldY, 84, 30, () -> {
            action(request("beginAreaEdit", "index", index), null);
            mc.displayGuiScreen(null);
        });
        if ("lap".equals(role)) quietButton("削除", actionX + 187, fieldY, 48, 30,
                () -> action(request("deletePoint", "index", index), "ラップを削除しました"));
        if ("lap".equals(role)) {
            directionButton(x + w - 76, y + 8, true, () -> action(request("movePoint", "index", index, "direction", -1), "順番を変更しました"));
            directionButton(x + w - 53, y + 8, false, () -> action(request("movePoint", "index", index, "direction", 1), "順番を変更しました"));
        }
        if ("aabb".equals(string(point, "detection", "ground"))) {
            double currentHeight = point.get("height").getAsDouble();
            text("高さ", x + 70, fieldY + 44, 9, TEXT_MUTED);
            quietButton("-", x + 108, fieldY + 36, 28, 28,
                    () -> setHeight(index, Math.max(.05, currentHeight - .25)));
            boolean editing = heightEditIndex == index;
            inputField(x + 140, fieldY + 36, 64, 28, editing);
            String shownHeight = editing ? heightInput : formatHeight(currentHeight);
            centeredMono(shownHeight, x + 140, fieldY + 36, 64, 28, 10, TEXT);
            if (editing && (System.currentTimeMillis() / 500L) % 2 == 0) {
                int caretX = Math.min(x + 197, x + 145 + monoWidth(shownHeight, 10));
                drawRect(caretX, fieldY + 43, caretX + 1, fieldY + 57, TEXT);
            }
            hit(x + 140, fieldY + 36, 64, 28, () -> beginHeightEdit(index, currentHeight));
            quietButton("+", x + 208, fieldY + 36, 28, 28,
                    () -> setHeight(index, Math.min(256, currentHeight + .25)));
        }
    }

    private int pointHeight(JsonObject point, boolean open) {
        if (!open) return 48;
        if (!point.get("placed").getAsBoolean()) return 96;
        return "aabb".equals(string(point, "detection", "ground")) ? 146 : 116;
    }

    private void drawPointBackground(int position, int total, int x, int y, int w, int h, int color) {
        int innerX = x + 1;
        int innerW = w - 2;
        boolean first = position == 0;
        boolean last = position == total - 1;
        if (first || last) {
            roundedRect(innerX, y + (first ? 1 : 0), innerW, h - (first ? 1 : 0) - (last ? 1 : 0), 6, color);
            if (first && !last) drawRect(innerX, y + 7, innerX + innerW, y + h, color);
            if (last && !first) drawRect(innerX, y, innerX + innerW, y + h - 7, color);
        } else {
            drawRect(innerX, y, innerX + innerW, y + h, color);
        }
        if (!last) drawRect(x + 1, y + h - 1, x + w - 1, y + h, LINE_STRONG);
    }

    private void beginHeightEdit(int index, double currentHeight) {
        if (heightEditIndex != index) {
            heightEditIndex = index;
            heightInput = formatHeight(currentHeight);
            replaceHeightInput = true;
        }
    }

    private void setHeight(int index, double value) {
        heightEditIndex = index;
        heightInput = formatHeight(value);
        replaceHeightInput = false;
        action(request("updatePoint", "index", index, "height", value), null);
    }

    private void updateHeightFromInput() {
        if (heightEditIndex < 0 || heightInput.isEmpty() || ".".equals(heightInput)) return;
        try {
            double value = Double.parseDouble(heightInput);
            if (Double.isFinite(value) && value > 0.0 && value <= 256.0) {
                action(request("updatePoint", "index", heightEditIndex, "height", value), null);
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private static String formatHeight(double value) {
        String formatted = String.format(Locale.ROOT, "%.2f", value);
        while (formatted.endsWith("0")) formatted = formatted.substring(0, formatted.length() - 1);
        return formatted.endsWith(".") ? formatted.substring(0, formatted.length() - 1) : formatted;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (heightEditIndex >= 0) {
            if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                updateHeightFromInput();
                heightEditIndex = -1;
                return;
            }
            if (keyCode == Keyboard.KEY_BACK) {
                if (replaceHeightInput) heightInput = "";
                else if (!heightInput.isEmpty()) heightInput = heightInput.substring(0, heightInput.length() - 1);
                replaceHeightInput = false;
                updateHeightFromInput();
                return;
            }
            if ((typedChar >= '0' && typedChar <= '9') || typedChar == '.') {
                String next = replaceHeightInput ? String.valueOf(typedChar) : heightInput + typedChar;
                if (next.length() <= 7 && count(next, '.') <= 1) {
                    heightInput = next;
                    replaceHeightInput = false;
                    updateHeightFromInput();
                }
                return;
            }
        }
        super.keyTyped(typedChar, keyCode);
    }

    private static int count(String value, char character) {
        int count = 0;
        for (int i = 0; i < value.length(); i++) if (value.charAt(i) == character) count++;
        return count;
    }

    private void segmentButton(String label, int x, int y, int w, int h, boolean selected, Runnable action) {
        boolean hover = hovered(x, y, w, h);
        roundedOutline(x, y, w, h, 5, selected ? 0xFF24364A : hover ? CONTROL_HOVER : CONTROL,
                selected ? ACCENT : LINE_CONTROL);
        centeredText(label, x, y, w, h, 10, selected ? TEXT_STRONG : 0xFF89939A, false);
        hit(x, y, w, h, action);
    }

    private void directionButton(int x, int y, boolean up, Runnable action) {
        boolean hover = hovered(x, y, 22, 30);
        if (hover) {
            roundedOutline(x, y, 22, 30, 5, CONTROL_HOVER, LINE_CONTROL);
        }
        chevron(x + 5, y + 9, 12, up, hover ? TEXT_STRONG : 0xFFADB5BB);
        hit(x, y, 22, 30, action);
    }

    private String fit(String value, int maxWidth, float size) {
        if (textWidth(value, size, false) <= maxWidth) return value;
        int length = value.length();
        while (length > 0 && textWidth(value.substring(0, length) + "...", size, false) > maxWidth) length--;
        return value.substring(0, length) + "...";
    }

    private void promptCreate() {
        String base = "新しいコース";
        mc.displayGuiScreen(new GuiAstTextPrompt(this, "新規コース", "コース名", base,
                value -> action(request("createCourse", "name", value), "現在地にSTARTを作成しました")));
    }

    private void chooseImport() {
        AstCourseShareService.chooseImport(
                preview -> mc.displayGuiScreen(new GuiAstCourseImport(this, preview)),
                this::notice,
                () -> {});
    }

    void importCompleted(String name) {
        refresh();
        openIndex = -1;
        courseMenu = false;
        toolsMenu = false;
        notice("「" + name + "」を読み込みました");
    }

    private void refresh() {
        try {
            state = AstCourseUiService.handle(request("state"));
            showStateWarning();
        }
        catch (RuntimeException exception) { notice(exception.getMessage() == null ? "読み込みに失敗しました" : exception.getMessage()); }
    }

    private void action(JsonObject request, String success) {
        try {
            state = AstCourseUiService.handle(request);
            if (!showStateWarning() && success != null) notice(success);
        } catch (RuntimeException exception) {
            notice(exception.getMessage() == null ? "操作に失敗しました" : exception.getMessage());
        }
    }

    private boolean showStateWarning() {
        if (state != null && state.has("warning") && !state.get("warning").isJsonNull()) {
            notice(state.get("warning").getAsString());
            return true;
        }
        return false;
    }

    private JsonObject active() {
        if (state == null || !state.has("activeCourse") || state.get("activeCourse").isJsonNull()) return null;
        return state.getAsJsonObject("activeCourse");
    }

    private static JsonObject request(String type, Object... entries) {
        JsonObject object = new JsonObject();
        object.addProperty("type", type);
        for (int i = 0; i + 1 < entries.length; i += 2) {
            String key = String.valueOf(entries[i]);
            Object value = entries[i + 1];
            if (value instanceof Number) object.addProperty(key, (Number) value);
            else if (value instanceof Boolean) object.addProperty(key, (Boolean) value);
            else object.addProperty(key, String.valueOf(value));
        }
        return object;
    }

    private static String string(JsonObject object, String key, String fallback) {
        return object != null && object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : fallback;
    }
}
