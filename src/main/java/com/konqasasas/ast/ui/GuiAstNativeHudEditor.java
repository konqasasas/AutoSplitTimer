package com.konqasasas.ast.ui;

import com.konqasasas.ast.core.AstCourseManager;
import com.konqasasas.ast.core.AstData;
import com.konqasasas.ast.hud.AstHudConfigUtil;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Native HUD settings screen. */
public final class GuiAstNativeHudEditor extends AstUiScreen {
    private static final List<String> ITEMS = Arrays.asList(AstHudConfigUtil.knownItems());
    private final GuiScreen parent;
    private AstData.HudConfig hud;
    private String pendingPreset;
    private boolean splitSettingsOpen = true;
    private boolean appearanceOpen;

    public GuiAstNativeHudEditor(GuiScreen parent) { this.parent = parent; }

    @Override
    public void initGui() {
        hud = AstCourseManager.get().getHudConfig();
        AstHudConfigUtil.normalizeHud(hud);
        showStorageWarning();
    }

    @Override
    protected void drawUi(int mouseX, int mouseY, float partialTicks) {
        int workspaceW = Math.min(1040, width - 48);
        int left = (width - workspaceW) / 2;
        strong("HUD設定", left, 22, 22, TEXT_STRONG);
        text("表示する情報を選び、選択した項目だけを右側で並べ替えます。", left, 51, 11, TEXT_MUTED);
        button("コース編集へ戻る", left + workspaceW - 126, 18, 126, 32, () -> mc.displayGuiScreen(parent));

        int top = 78;
        int layoutH = height - top - 22;
        int previewW = Math.max(300, Math.round(workspaceW * .39f));
        int controlsX = left + previewW;
        int controlsW = workspaceW - previewW;
        int available = layoutH - 2;
        int totalHeight = contentHeight();
        int maxScroll = Math.max(0, totalHeight - available);
        // Clamp before drawing. Clamping afterwards rendered one overscrolled
        // frame at the bottom and the following frame snapped back, which was
        // visible as flicker while the wheel kept producing events.
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        panel(left, top, workspaceW, layoutH);
        // Preserve the parent card's curved left edge instead of covering its
        // antialiased corners with a square preview background.
        roundedRect(left + 1, top + 1, previewW, layoutH - 2, 7, 0xFF111416);
        drawRect(left + 8, top + 1, controlsX, top + layoutH - 1, 0xFF111416);
        drawRect(controlsX, top + 1, controlsX + 1, top + layoutH - 1, LINE);

        beginClip(left + 1, top + 1, previewW - 1, layoutH - 2);
        strong("プレビュー", left + 18, top + 18, 9, TEXT_FAINT);
        drawPreview(left + 18, top + 55);
        endClip();

        int y = top - scroll;
        int flowStart = y;
        beginClip(controlsX + 1, top + 1, controlsW - 2, layoutH - 2);
        y = drawSelectionColumns(controlsX + 1, y, controlsW - 2);
        y = drawDisplaySettings(controlsX + 1, y, controlsW - 2);
        y = drawSplitSettings(controlsX + 1, y, controlsW - 2);
        y = drawAppearanceSettings(controlsX + 1, y, controlsW - 2);
        y = drawPresets(controlsX + 1, y, controlsW - 2);
        int drawnHeight = y - flowStart;
        endClip();

        if (maxScroll > 0) drawScrollbar(left + workspaceW - 5, top + 7, layoutH - 14, drawnHeight, available, maxScroll);
        if (pendingPreset != null) drawPresetConfirm();
    }

    private int contentHeight() {
        int rows = Math.max(ITEMS.size(), selectedItems().size());
        int height = 43 + rows * 39;
        height += 43 + 61 * 3;
        height += 46;
        if (splitSettingsOpen && enabled("splitList")) height += hud.splitColumns == 2 ? 61 * 3 : 61 * 2;
        height += 46;
        if (appearanceOpen) height += 54 * 3;
        return height + 70;
    }

    private void drawScrollbar(int x, int y, int h, int total, int visible, int maxScroll) {
        roundedRect(x, y, 3, h, 2, 0xFF1A1E21);
        int thumb = Math.max(28, Math.min(h, Math.round(h * (visible / (float) Math.max(visible, total)))));
        int travel = h - thumb;
        int thumbY = y + (maxScroll == 0 ? 0 : Math.round(travel * (scroll / (float) maxScroll)));
        roundedRect(x, thumbY, 3, thumb, 2, 0xFF687784);
    }

    private int drawSelectionColumns(int x, int y, int w) {
        List<String> selected = selectedItems();
        int half = w / 2;
        int rows = Math.max(ITEMS.size(), selected.size());
        int h = 43 + rows * 39;
        drawRect(x, y, x + w, y + h, SURFACE);
        drawRect(x, y + h - 1, x + w, y + h, LINE);
        drawRect(x + half, y, x + half + 1, y + h, LINE);
        controlHeading("表示項目", selected.size() + " / " + ITEMS.size(), x, y, half);
        controlHeading("表示順", "選択済みのみ", x + half + 1, y, w - half - 1);

        for (int i = 0; i < ITEMS.size(); i++) {
            final String key = ITEMS.get(i);
            int ry = y + 43 + i * 39;
            boolean on = enabled(key);
            if (hovered(x, ry, half, 39)) drawRect(x, ry, x + half, ry + 39, 0xFF1D2225);
            drawRect(x, ry + 38, x + half, ry + 39, 0xFF292E32);
            roundedOutline(x + 12, ry + 15, 9, 9, 2, on ? START : 0xFF14191D,
                    on ? START : 0xFF606B72);
            verticallyCenteredText(label(key), x + 29, ry, 39, 10, on ? TEXT : 0xFF788289, false);
            hit(x, ry, half, 39, () -> { hud.toggles.put(key, !enabled(key)); save(); });
        }

        if (selected.isEmpty()) text("左から表示項目を選択", x + half + 28, y + 73, 10, TEXT_FAINT);
        for (int i = 0; i < selected.size(); i++) {
            final int orderIndex = i;
            String key = selected.get(i);
            int rx = x + half + 1, rw = w - half - 1, ry = y + 43 + i * 39;
            drawRect(rx, ry + 38, rx + rw, ry + 39, 0xFF292E32);
            verticallyCenteredMono(String.format(Locale.ROOT, "%02d", i + 1), rx + 12, ry, 39, 9, 0xFF737E85);
            verticallyCenteredText(label(key), rx + 40, ry, 39, 10, TEXT, false);
            arrowButton(rx + rw - 52, ry + 7, true, i > 0, () -> moveSelected(orderIndex, -1));
            arrowButton(rx + rw - 27, ry + 7, false, i + 1 < selected.size(), () -> moveSelected(orderIndex, 1));
        }
        return y + h;
    }

    private void controlHeading(String title, String aside, int x, int y, int w) {
        drawRect(x, y + 42, x + w, y + 43, 0xFF292E32);
        strong(title, x + 15, y + 15, 12, TEXT_STRONG);
        if (aside != null) {
            int tw = textWidth(aside, 9, false);
            text(aside, x + w - 15 - tw, y + 17, 9, TEXT_FAINT);
        }
    }

    private void arrowButton(int x, int y, boolean up, boolean enabled, Runnable action) {
        if (enabled && hovered(x, y, 24, 25)) {
            roundedOutline(x, y, 24, 25, 5, CONTROL, 0xFF465159);
        }
        chevron(x + 6, y + 6, 12, up, enabled ? 0xFF9AA4AA : 0xFF41484D);
        if (enabled) hit(x, y, 24, 25, action);
    }

    private int drawDisplaySettings(int x, int y, int w) {
        controlHeading("表示設定", null, x, y, w);
        y += 43;
        int half = w / 2;
        settingStepper("大きさ", Math.round(hud.scale * 100) + "%", x, y, half,
                () -> { hud.scale = Math.max(.25, round(hud.scale - .05)); save(); },
                () -> { hud.scale = Math.min(3, round(hud.scale + .05)); save(); });
        settingStepper("横幅", hud.splitListWidth + "px", x + half, y, w - half,
                () -> { hud.splitListWidth = Math.max(160, hud.splitListWidth - 10); save(); },
                () -> { hud.splitListWidth = Math.min(420, hud.splitListWidth + 10); save(); });
        y += 61;
        settingPosition(x, y, half);
        settingStepper("スプリット行数", String.valueOf(hud.splitListCount), x + half, y, w - half,
                () -> { hud.splitListCount = Math.max(2, hud.splitListCount - 1); save(); },
                () -> { hud.splitListCount = Math.min(20, hud.splitListCount + 1); save(); });
        y += 61;
        settingToggle("デルタを表示", hud.splitColumns == 2, x, y, half,
                () -> { hud.splitColumns = hud.splitColumns == 2 ? 1 : 2; save(); });
        settingSegments("時間表記", hud.timeFormat, x + half, y, w - half,
                new String[]{"MSS", "SECONDS", "TICKS"}, new String[]{"00:42.35", "42.35", "847t"}, value -> { hud.timeFormat = value; save(); });
        return y + 61;
    }

    private int drawSplitSettings(int x, int y, int w) {
        boolean available = enabled("splitList");
        sectionToggle("スプリット一覧", x, y, w, splitSettingsOpen, available,
                () -> splitSettingsOpen = !splitSettingsOpen);
        y += 46;
        if (!splitSettingsOpen || !available) return y;
        int half = w / 2;
        settingSegments("基準記録", hud.comparison, x, y, half,
                new String[]{"pb", "best"}, new String[]{"PB", "BEST"}, value -> { hud.comparison = value; save(); });
        settingSegments("スプリット一覧の時間", hud.unit, x + half, y, w - half,
                new String[]{"split", "seg"}, new String[]{"累計", "区間"}, value -> { hud.unit = value; save(); });
        y += 61;
        if (hud.splitColumns == 2) {
            settingSegments("デルタの時間表記", hud.deltaTimeFormat, x, y, half,
                    new String[]{"MSS", "SECONDS", "TICKS"}, new String[]{"+0:00.00", "+0.00", "+0t"}, value -> { hud.deltaTimeFormat = value; save(); });
            settingStepper("デルタ位置", hud.splitDeltaOffset + "px", x + half, y, w - half,
                    () -> { hud.splitDeltaOffset = Math.max(-Math.min(80, hud.splitListWidth - 160), hud.splitDeltaOffset - 4); save(); },
                    () -> { hud.splitDeltaOffset = Math.min(0, hud.splitDeltaOffset + 4); save(); });
            y += 61;
        }
        settingSegments("行間", String.valueOf(hud.splitListLineGap), x, y, half,
                new String[]{"0", "2", "4"}, new String[]{"狭い", "標準", "広い"}, value -> { hud.splitListLineGap = Integer.parseInt(value); save(); });
        emptySettingCell(x + half, y, w - half);
        return y + 61;
    }

    private int drawAppearanceSettings(int x, int y, int w) {
        sectionToggle("外観", x, y, w, appearanceOpen, true, () -> appearanceOpen = !appearanceOpen);
        y += 46;
        if (!appearanceOpen) return y;
        int half = w / 2;
        settingColor("背景", hud.backgroundRgb, x, y, half, value -> hud.backgroundRgb = value);
        settingStepper("背景の透明度", hud.backgroundOpacity + "%", x + half, y, w - half,
                () -> { hud.backgroundOpacity = Math.max(0, hud.backgroundOpacity - 5); save(); },
                () -> { hud.backgroundOpacity = Math.min(100, hud.backgroundOpacity + 5); save(); });
        y += 54;
        settingColor("ラベル", hud.labelRgb, x, y, half, value -> hud.labelRgb = value);
        settingColor("メイン文字", hud.mainRgb, x + half, y, w - half, value -> hud.mainRgb = value);
        y += 54;
        settingColor("サブ文字", hud.subRgb, x, y, half, value -> hud.subRgb = value);
        emptySettingCell(x + half, y, w - half);
        return y + 54;
    }

    private int drawPresets(int x, int y, int w) {
        drawRect(x, y, x + w, y + 70, SURFACE);
        strong("プリセットから調整", x + 15, y + 20, 12, TEXT_STRONG);
        text("適用後に各項目を編集できます", x + 15, y + 42, 9, TEXT_FAINT);
        String[][] presets = {{"standard", "標準"}, {"compact", "コンパクト"}, {"detailed", "詳細"}, {"minimal", "最小"}};
        int bw = 55, bx = x + w - 13 - bw * 4;
        drawSegmentGroup(presets, hud.preset, bx, y + 22, bw * 4, 27,
                value -> pendingPreset = value);
        return y + 70;
    }

    private void sectionToggle(String title, int x, int y, int w, boolean open, boolean enabled, Runnable action) {
        drawRect(x, y, x + w, y + 46, SURFACE);
        drawRect(x, y + 45, x + w, y + 46, LINE);
        if (enabled && hovered(x, y, w, 46)) drawRect(x, y, x + w, y + 45, 0xFF1C2023);
        strong(title, x + 15, y + 16, 12, enabled ? TEXT_STRONG : 0xFF606970);
        chevron(x + w - 27, y + 19, 10, open, enabled ? TEXT_MUTED : 0xFF4D555B);
        if (enabled) hit(x, y, w, 46, action);
    }

    private void settingBase(String label, int x, int y, int w, int h) {
        drawRect(x, y, x + w, y + h, SURFACE);
        drawRect(x, y + h - 1, x + w, y + h, 0xFF292E32);
        drawRect(x + w - 1, y, x + w, y + h, 0xFF292E32);
        verticallyCenteredText(label, x + 15, y, h, 10, 0xFF9BA4AA, false);
    }

    private void emptySettingCell(int x, int y, int w) { settingBase("", x, y, w, 61); }

    private void settingStepper(String label, String value, int x, int y, int w, Runnable minus, Runnable plus) {
        settingBase(label, x, y, w, 61);
        int sx = x + w - 119;
        int sy = y + 17;
        roundedOutline(sx, sy, 119, 27, 6, 0xFF171B1E, 0xFF424B51);
        stepperEnd("−", sx, sy, 31, 27, true, minus);
        centeredMono(value, sx + 31, y + 17, 57, 27, 10, TEXT);
        stepperEnd("+", sx + 88, sy, 31, 27, false, plus);
        drawRect(sx + 31, sy + 1, sx + 32, sy + 26, 0xFF424B51);
        drawRect(sx + 87, sy + 1, sx + 88, sy + 26, 0xFF424B51);
    }

    private void settingSegments(String label, String selected, int x, int y, int w, String[] values, String[] captions,
                                 java.util.function.Consumer<String> setter) {
        settingBase(label, x, y, w, 61);
        int total = Math.min(174, Math.max(84, captions.length * 58));
        int bx = x + w - 13 - total;
        String[][] choices = new String[captions.length][2];
        for (int i = 0; i < captions.length; i++) {
            choices[i][0] = values[i];
            choices[i][1] = captions[i];
        }
        drawSegmentGroup(choices, selected, bx, y + 17, total, 27, setter);
    }

    private void settingToggle(String label, boolean on, int x, int y, int w, Runnable action) {
        settingBase(label, x, y, w, 61);
        int bx = x + w - 83, by = y + 17;
        boolean hover = hovered(bx, by, 70, 27);
        roundedOutline(bx, by, 70, 27, 6, hover ? CONTROL_HOVER : 0xFF171B1E,
                on ? 0xFF687983 : 0xFF424B51);
        int trackX = bx + 8, trackY = by + 6;
        roundedOutline(trackX, trackY, 27, 15, 8, on ? 0xFF275F9F : 0xFF252E35,
                on ? 0xFF4F86C7 : 0xFF44505C);
        roundedRect(on ? trackX + 14 : trackX + 3, trackY + 3, 9, 9, 5,
                on ? 0xFFF3F6F8 : 0xFFA6B0B8);
        centeredText(on ? "表示" : "非表示", bx + 37, by, 31, 27, 9,
                on ? TEXT_STRONG : TEXT_MUTED, false);
        hit(bx, by, 70, 27, action);
    }

    private void settingPosition(int x, int y, int w) {
        settingBase("HUD位置", x, y, w, 61);
        int bx = x + w - 139, by = y + 16;
        button("画面上で調整", bx, by, 126, 29,
                () -> mc.displayGuiScreen(new GuiAstHudPositionEditor(this)));
    }

    private void settingColor(String label, String value, int x, int y, int w, java.util.function.Consumer<String> setter) {
        settingBase(label, x, y, w, 54);
        int bx = x + w - 118, by = y + 12;
        roundedOutline(bx, by, 105, 29, 6, 0xFF121517, 0xFF4B565F);
        roundedRect(bx + 3, by + 3, 23, 23, 4, rgb(value, 0xFFFFFFFF));
        drawRect(bx + 28, by + 1, bx + 29, by + 28, 0xFF424B51);
        centeredMono(value.toUpperCase(Locale.ROOT), bx + 29, by, 76, 29, 9, TEXT);
        hit(bx, by, 105, 29, () -> mc.displayGuiScreen(new GuiAstTextPrompt(this, label + "の色", "#RRGGBB", value, entered -> {
            String normalized = entered.startsWith("#") ? entered : "#" + entered;
            if (normalized.matches("#[0-9a-fA-F]{6}")) { setter.accept(normalized.toUpperCase(Locale.ROOT)); save(); }
            else notice("色は #RRGGBB で入力してください");
        })));
    }

    private void stepperEnd(String caption, int x, int y, int w, int h, boolean left, Runnable action) {
        boolean hover = hovered(x, y, w, h);
        if (hover) {
            roundedRect(x + 1, y + 1, w - 2, h - 2, 5, CONTROL_HOVER);
            if (left) drawRect(x + 8, y + 1, x + w, y + h - 1, CONTROL_HOVER);
            else drawRect(x, y + 1, x + w - 8, y + h - 1, CONTROL_HOVER);
        }
        centeredText(caption, x, y, w, h, 10, hover ? TEXT_STRONG : TEXT_MUTED, false);
        hit(x, y, w, h, action);
    }

    private void drawSegmentGroup(String[][] choices, String selected, int x, int y, int w, int h,
                                  java.util.function.Consumer<String> setter) {
        roundedOutline(x, y, w, h, 6, 0xFF171B1E, 0xFF424B51);
        int each = w / choices.length;
        for (int i = 0; i < choices.length; i++) {
            final String value = choices[i][0];
            int cellX = x + i * each;
            int cellW = i + 1 == choices.length ? w - each * i : each;
            boolean active = value.equalsIgnoreCase(selected);
            boolean hover = hovered(cellX, y, cellW, h);
            if (active || hover) drawSegmentFill(cellX, y, cellW, h, i, choices.length,
                    active ? 0xFF263A4D : CONTROL_HOVER);
            if (i > 0) drawRect(cellX, y + 1, cellX + 1, y + h - 1, 0xFF424B51);
            centeredText(choices[i][1], cellX, y, cellW, h, 9,
                    active ? TEXT_STRONG : TEXT_MUTED, false);
            hit(cellX, y, cellW, h, () -> setter.accept(value));
        }
    }

    private void drawSegmentFill(int x, int y, int w, int h, int index, int count, int color) {
        if (index == 0 || index == count - 1) {
            roundedRect(x + 1, y + 1, w - 2, h - 2, 5, color);
            if (index == 0 && count > 1) drawRect(x + 7, y + 1, x + w, y + h - 1, color);
            if (index == count - 1 && count > 1) drawRect(x, y + 1, x + w - 7, y + h - 1, color);
        } else {
            drawRect(x, y + 1, x + w, y + h - 1, color);
        }
    }

    private void drawPreview(int x, int y) {
        float scale = (float) Math.max(.25, Math.min(1.30, hud.scale));
        int logicalW = Math.max(160, hud.splitListWidth);
        int logicalH = previewHeight();
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(scale, scale, 1);
        drawRect(0, 0, logicalW, logicalH, rgba(hud.backgroundRgb, hud.backgroundOpacity));
        int py = 9;
        for (String key : hud.itemOrder) {
            if (!enabled(key)) continue;
            if ("splitList".equals(key)) {
                py = drawPreviewSplits(10, py + 2, logicalW - 20);
                continue;
            }
            boolean main = "time".equals(key);
            int rowHeight = (main ? 27 : 18) + Math.max(0, hud.splitListLineGap);
            String value = previewValue(key);
            text(previewLabel(key), 10, py + (main ? 7 : 3), 8, rgb(hud.labelRgb, 0xFF8A949B));
            AstFont font = main || !"courseName".equals(key) && !"segment".equals(key)
                    ? AstFonts.mono() : AstFonts.ui(value, false);
            float size = scaledFontSize(main ? 20 : 11);
            font.draw(value, logicalW - 10 - font.width(value, size), py + (main ? 0 : 1), size,
                    main ? rgb(hud.mainRgb, 0xFFFFFFFF) : rgb(hud.subRgb, 0xFFD7DDE0));
            py += rowHeight;
        }
        GlStateManager.popMatrix();
    }

    private int previewHeight() {
        int h = 19;
        int rows = Math.min(6, Math.max(2, hud.splitListCount));
        for (String key : hud.itemOrder) if (enabled(key)) {
            if ("splitList".equals(key)) h += 2 + rows * (22 + Math.max(0, hud.splitListLineGap));
            else h += ("time".equals(key) ? 27 : 18) + Math.max(0, hud.splitListLineGap);
        }
        return h;
    }

    private int drawPreviewSplits(int x, int y, int w) {
        // START is the timer trigger, not a LiveSplit split row. The first LAP
        // after START is therefore 01 and GOAL is the final split.
        String[] names = {"01 LAP 1", "02 LAP 2", "03 LAP 3", "04 LAP 4", "05 LAP 5", "06 GOAL"};
        double[] cumulative = "best".equalsIgnoreCase(hud.comparison)
                ? new double[]{27.10, 41.35, 57.10, 70.75, 88.85, 126.15}
                : new double[]{27.65, 42.35, 58.35, 72.40, 91.00, 129.50};
        double[] segments = "best".equalsIgnoreCase(hud.comparison)
                ? new double[]{27.10, 14.25, 15.75, 13.65, 18.10, 22.35}
                : new double[]{27.65, 14.70, 16.00, 14.05, 18.60, 23.05};
        double[] deltas = {-0.10, 0.20, -0.10, 0.05, 0.30, 0.00};
        int currentIndex = names.length - 2;
        int count = Math.min(names.length, Math.max(2, hud.splitListCount));
        int start = names.length - count;
        int line = 22 + Math.max(0, hud.splitListLineGap);
        for (int i = start; i < names.length; i++) {
            boolean current = i == currentIndex;
            if (current) drawRect(x, y, x + w, y + line, 0xFF23292D);
            String time = formatSeconds("seg".equalsIgnoreCase(hud.unit) ? segments[i] : cumulative[i], hud.timeFormat, false);
            int timeWidth = monoWidth(time, 11);
            int timeX = x + w - timeWidth;
            int nameRight = timeX - 8;
            // Like LiveSplit, future rows keep their comparison time but do not
            // show a delta. The current row shows a live delta only once behind.
            boolean showDelta = i < currentIndex || (i == currentIndex && deltas[i] > 0);
            if (hud.splitColumns == 2 && showDelta) {
                String delta = formatSeconds(Math.abs(deltas[i]), hud.deltaTimeFormat, true);
                delta = (deltas[i] < 0 ? "−" : "+") + delta;
                int deltaWidth = monoWidth(delta, 11);
                int deltaX = Math.max(x + 70, timeX - 8 - deltaWidth + hud.splitDeltaOffset);
                nameRight = deltaX - 8;
                mono(delta, deltaX, y + 3, 11, deltas[i] < 0 ? 0xFF55D68B : 0xFFE05D65);
            }
            String name = ellipsizePreview(names[i], Math.max(20, nameRight - x - 2));
            strong(name, x + 2, y + 4, 10, current ? LAP : rgb(hud.labelRgb, 0xFF8A949B));
            mono(time, timeX, y + 3, 11, rgb(hud.subRgb, 0xFFD7DDE0));
            if (i + 1 < names.length) drawRect(x, y + line - 1, x + w, y + line, 0xFF282D31);
            y += line;
        }
        return y;
    }

    private String previewValue(String key) {
        switch (key) {
            case "courseName": return "練習コース";
            case "time": return formatSeconds(91.30, hud.timeFormat, false);
            case "segment": return "05 / 06  LAP 5";
            case "segmentTime": return formatSeconds(18.90, hud.timeFormat, false);
            case "prevSeg": return "+" + formatSeconds(.20, hud.deltaTimeFormat, true);
            case "sob": return formatSeconds(112.40, hud.timeFormat, false);
            case "bpt": return formatSeconds(114.10, hud.timeFormat, false);
            case "bestSeg": return formatSeconds(14.25, hud.timeFormat, false);
            case "bestSplit": return formatSeconds(42.00, hud.timeFormat, false);
            case "attempt": return "24";
            default: return "--";
        }
    }

    private static String previewLabel(String key) {
        switch (key) {
            case "courseName": return "COURSE"; case "time": return "TIME"; case "segment": return "SEGMENT";
            case "segmentTime": return "SEG TIME"; case "prevSeg": return "PREV"; case "sob": return "SUM OF BEST";
            case "bpt": return "POSSIBLE TIME"; case "bestSeg": return "BEST SEG"; case "bestSplit": return "BEST SPLIT";
            case "attempt": return "ATTEMPTS"; default: return key.toUpperCase(Locale.ROOT);
        }
    }

    private static String formatSeconds(double seconds, String format, boolean delta) {
        if ("TICKS".equalsIgnoreCase(format)) return Math.round(seconds * 20) + "t";
        if ("SECONDS".equalsIgnoreCase(format)) return String.format(Locale.ROOT, "%.2f", seconds);
        int minutes = (int) (seconds / 60);
        double rest = seconds - minutes * 60;
        String formatted = String.format(Locale.ROOT, "%d:%05.2f", minutes, rest);
        return delta ? formatted : formatted;
    }

    private String ellipsizePreview(String value, int maxWidth) {
        float size = scaledFontSize(10);
        if (AstFonts.semibold().width(value, size) <= maxWidth) return value;
        int length = value.length();
        while (length > 0 && AstFonts.semibold().width(value.substring(0, length) + "…", size) > maxWidth) length--;
        return value.substring(0, length) + "…";
    }

    private void drawPresetConfirm() {
        drawRect(0, 0, width, height, 0x99000000);
        hit(0, 0, width, height, () -> {});
        int w = 470, h = 174, x = (width - w) / 2, y = (height - h) / 2;
        panel(x, y, w, h);
        strong("プリセットを適用", x + 22, y + 20, 18, TEXT_STRONG);
        strong("現在のHUD編集内容が失われます", x + 22, y + 66, 13, TEXT_STRONG);
        text("表示項目、順番、大きさなどが置き換わります。", x + 22, y + 90, 11, TEXT_MUTED);
        quietButton("キャンセル", x + w - 194, y + h - 42, 82, 30, () -> pendingPreset = null);
        button("適用", x + w - 102, y + h - 42, 82, 30, () -> {
            AstHudConfigUtil.applyPreset(hud, pendingPreset, false); pendingPreset = null; save();
        });
    }

    private boolean enabled(String key) { return hud.toggles.get(key) == null || hud.toggles.get(key); }
    private List<String> selectedItems() {
        List<String> result = new ArrayList<>();
        for (String key : hud.itemOrder) if (enabled(key)) result.add(key);
        return result;
    }
    private void moveSelected(int selectedIndex, int direction) {
        List<String> selected = selectedItems();
        int target = selectedIndex + direction;
        if (selectedIndex < 0 || target < 0 || target >= selected.size()) return;
        String a = selected.get(selectedIndex), b = selected.get(target);
        int ai = hud.itemOrder.indexOf(a), bi = hud.itemOrder.indexOf(b);
        if (ai < 0 || bi < 0) return;
        hud.itemOrder.set(ai, b);
        hud.itemOrder.set(bi, a);
        save();
    }
    private void save() {
        AstHudConfigUtil.normalizeHud(hud);
        AstCourseManager.get().saveHudConfig(hud);
        showStorageWarning();
    }
    private void showStorageWarning() {
        String warning = AstCourseManager.get().pollWarning();
        if (warning != null) notice(warning);
    }
    private static double round(double value) { return Math.round(value * 100) / 100.0; }
    private static String cycle(String current, String... values) { int i = Arrays.asList(values).indexOf(current.toUpperCase(Locale.ROOT)); return values[(i + 1 + values.length) % values.length]; }
    private static String label(String key) {
        switch (key) {
            case "courseName": return "コース名"; case "time": return "現在タイム"; case "segment": return "現在スプリット";
            case "segmentTime": return "区間タイム"; case "prevSeg": return "前区間"; case "sob": return "ベスト区間合計";
            case "bpt": return "到達可能タイム"; case "bestSeg": return "区間ベスト"; case "bestSplit": return "ベストスプリット";
            case "attempt": return "試行回数"; case "splitList": return "スプリット一覧"; default: return key;
        }
    }
    private static int rgba(String value, int opacity) { return (Math.round(Math.max(0, Math.min(100, opacity)) * 2.55f) << 24) | (rgb(value, 0xFF0B0D0F) & 0xFFFFFF); }
    private static int rgb(String value, int fallback) { try { return 0xFF000000 | Integer.parseInt(value.replace("#", ""), 16); } catch (Exception e) { return fallback; } }
}
