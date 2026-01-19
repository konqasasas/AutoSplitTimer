package com.konqasasas.ast.hud;

import com.konqasasas.ast.core.AstCourseManager;
import com.konqasasas.ast.core.AstData;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * HUD editor with two tabs: Layout and Style.
 *
 * - Layout: mini check + up/down + Save Layout + Layout save/load/delete (final UI)
 * - Style: Preset / Theme / TimeFormat / SplitCols / gaps / scale / colors / (legacy layouts section)
 *
 * Uses custom mini buttons (no GuiButton).
 */
public class GuiAstHudEditor extends GuiScreen {

    private enum Tab { LAYOUT, STYLE }

    private Tab tab = Tab.STYLE;

    // Drag HUD position inside preview.
    private boolean dragging = false;
    private int dragOffX = 0;
    private int dragOffY = 0;

    // Layout scroll.
    private int scroll = 0;

    // Layout save/load name field (used in BOTH tabs; Layout tab is the “final UI”)
    private GuiTextField layoutNameField;
    private int layoutSelect = 0;
    private java.util.List<String> layoutNamesCache = new java.util.ArrayList<>();

    // UI constants.
    private static final int MINI = 12;
    // Row height for our mini-button UI (12px buttons + 1px top/bottom padding).
    private static final int ROW_H = 14;
    private static final int PAD = 8;

    // Tabs: width is text-dependent, group-centered
    private static final int TAB_H = 14;
    private static final int TAB_PAD_X = 10;
    private static final int TAB_GAP = 6;
    private static final int TAB_MIN_W = 56;

    private static final int PANEL_W = 230;

    // Visual nudge (hard-coded, no config).
    static final int TEXT_NUDGE_X = +1;         // horizontal nudge (shared for all text)
    static final int TEXT_NUDGE_Y = +1;        // normal text (labels/values/buttons)

    // Arrows: split into 3 visual groups
    //  - group1: < > + -
    //  - group2: ^
    //  - group3: v
    static final int ARROW_G1_NUDGE_Y = +2;
    static final int ARROW_UP_NUDGE_Y = +3;
    static final int ARROW_DN_NUDGE_Y = +1;
    static final int CHECK_X_NUDGE_Y = +1;     // checkbox mark x
    static final int CHECK_DASH_NUDGE_Y = +2;  // checkbox mark -

    private static final List<String> PRESET_ORDER = Arrays.asList(
            "standard", "compact", "practice", "minimal"
    );

    private static final List<String> THEME_ORDER = Arrays.asList(
            "default", "dark", "cute"
    );

    private AstData.HudConfig hud() {
        AstData.CourseFile c = AstCourseManager.get().getActiveCourse();
        if (c == null) return null;
        if (c.hud == null) c.hud = new AstData.HudConfig();
        AstHudConfigUtil.normalizeHud(c.hud);
        return c.hud;
    }

    private void saveCourse() {
        AstCourseManager.get().saveActiveCourseSafe();
        AstHudRenderer.requestRebuild();
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        // Layout name input (used in both tabs). Position is set during draw.
        this.layoutNameField = new GuiTextField(9001, this.fontRenderer, 0, 0, 120, MINI);
        this.layoutNameField.setMaxStringLength(32);
        this.layoutNameField.setEnableBackgroundDrawing(true);
        this.layoutNameField.setFocused(false);
        refreshLayoutNames();
        clampScroll();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        super.onGuiClosed();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    // ---------------------------
    // Rendering
    // ---------------------------

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        AstData.HudConfig hud = hud();

        // dim background
        drawDefaultBackground();

        // Top tabs (group-centered)
        int tabY = 6;
        int tabX = tabGroupX();
        int wLayout = tabWidth("Layout");
        int wStyle = tabWidth("Style");

        drawMiniTab(tabX, tabY, wLayout, TAB_H, "Layout", tab == Tab.LAYOUT);
        drawMiniTab(tabX + wLayout + TAB_GAP, tabY, wStyle, TAB_H, "Style", tab == Tab.STYLE);

        // Right panel background
        int px = this.width - PANEL_W - 10;
        int py = 24;
        int ph = this.height - py - 10;
        drawPanel(px, py, PANEL_W, ph);

        // No title (per final UI spec)

        if (hud == null) {
            drawString(fontRenderer, "No active course.", 10, 42, 0xFF6666);
            super.drawScreen(mouseX, mouseY, partialTicks);
            return;
        }

        // Preview: draw a subtle frame and render the HUD inside the GUI (real HUD is suppressed).
        drawPreviewFrame(hud);
        AstHudRenderer.renderPreview(hud.offsetX, hud.offsetY, hud.scale <= 0 ? 1.0 : hud.scale);

        // Panel contents
        if (tab == Tab.LAYOUT) {
            drawLayoutPanel(hud, px, py, PANEL_W, ph, mouseX, mouseY);
        } else {
            drawStylePanel(hud, px, py, PANEL_W, ph, mouseX, mouseY);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private int tabWidth(String text) {
        int w = fontRenderer.getStringWidth(text) + TAB_PAD_X * 2;
        if (w < TAB_MIN_W) w = TAB_MIN_W;
        return w;
    }

    private int tabGroupX() {
        int wLayout = tabWidth("Layout");
        int wStyle = tabWidth("Style");
        int groupW = wLayout + TAB_GAP + wStyle;
        return (this.width - groupW) / 2;
    }

    private void drawPanel(int x, int y, int w, int h) {
        // opaque-ish
        int bg = 0xCC000000;
        int border = 0xAAFFFFFF;
        drawRect(x, y, x + w, y + h, bg);
        drawHorizontalLine(x, x + w, y, border);
        drawHorizontalLine(x, x + w, y + h, border);
        drawVerticalLine(x, y, y + h, border);
        drawVerticalLine(x + w, y, y + h, border);
    }

    private void drawMiniTab(int x, int y, int w, int h, String text, boolean selected) {
        int fill = selected ? 0xFF2A2A2A : 0xFF151515;
        int border = selected ? 0xFFFFFFFF : 0x88FFFFFF;
        drawRect(x, y, x + w, y + h, fill);
        drawHorizontalLine(x, x + w, y, border);
        drawHorizontalLine(x, x + w, y + h, border);
        drawVerticalLine(x, y, y + h, border);
        drawVerticalLine(x + w, y, y + h, border);
        int tx = x + (w - fontRenderer.getStringWidth(text)) / 2 + TEXT_NUDGE_X;
        int ty = y + (h - fontRenderer.FONT_HEIGHT) / 2 + TEXT_NUDGE_Y;
        drawString(fontRenderer, text, tx, ty, selected ? 0xFFFFFF : 0xCCCCCC);
    }


    private void drawMiniButton(int x, int y, int w, int h, String text, boolean enabled, boolean selected) {
        int fill;
        if (!enabled) fill = 0xFF101010;
        else fill = selected ? 0xFF2E2E2E : 0xFF1B1B1B;
        int border = enabled ? 0xAAFFFFFF : 0x55FFFFFF;
        drawRect(x, y, x + w, y + h, fill);
        drawHorizontalLine(x, x + w, y, border);
        drawHorizontalLine(x, x + w, y + h, border);
        drawVerticalLine(x, y, y + h, border);
        drawVerticalLine(x + w, y, y + h, border);
        if (text != null && !text.isEmpty()) {
            int tx = x + (w - fontRenderer.getStringWidth(text)) / 2 + TEXT_NUDGE_X;
            int nudge = arrowNudgeY(text);
            int ty = y + (h - fontRenderer.FONT_HEIGHT) / 2 + nudge;
            int col = enabled ? 0xFFFFFF : 0x888888;
            drawString(fontRenderer, text, tx, ty, col);
        }
    }

    private static int arrowNudgeY(String text) {
        if (text == null || text.length() != 1) return TEXT_NUDGE_Y;
        char c = text.charAt(0);
        // group1: < > + -
        if (c == '<' || c == '>' || c == '+' || c == '-') return ARROW_G1_NUDGE_Y;
        // group2/3: vertical arrows
        if (c == '^') return ARROW_UP_NUDGE_Y;
        if (c == 'v') return ARROW_DN_NUDGE_Y;
        return TEXT_NUDGE_Y;
    }
    private static String formatLabel(String timeFormat) {
        if (timeFormat == null) return "m:ss";
        if ("seconds".equalsIgnoreCase(timeFormat) || "SECONDS".equalsIgnoreCase(timeFormat)) return "sec";
        return "TICKS".equalsIgnoreCase(timeFormat) ? "tick" : "m:ss";
    }

    private static String comparisonLabel(String v) {
        if (v == null) return "vs PB";
        return "best".equalsIgnoreCase(v) ? "vs Best" : "vs PB";
    }

    private static String unitLabel(String v) {
        if (v == null) return "Split";
        return "seg".equalsIgnoreCase(v) ? "Seg" : "Split";
    }

    private void drawCheck(int x, int y, boolean on, boolean enabled) {
        int fill;
        if (!enabled) fill = 0xFF101010;
        else fill = on ? 0xFF2E2E2E : 0xFF1B1B1B;
        int border = enabled ? 0xAAFFFFFF : 0x55FFFFFF;
        drawRect(x, y, x + MINI, y + MINI, fill);
        drawHorizontalLine(x, x + MINI, y, border);
        drawHorizontalLine(x, x + MINI, y + MINI, border);
        drawVerticalLine(x, y, y + MINI, border);
        drawVerticalLine(x + MINI, y, y + MINI, border);
        String mark = null;
        int col = enabled ? 0xFFFFFF : 0x666666;
        if (!enabled) {
            mark = "-"; // locked
        } else if (on) {
            mark = "x";
        }
        if (mark != null) {
            int tx = x + (MINI - fontRenderer.getStringWidth(mark)) / 2 + TEXT_NUDGE_X;
            int nudge = "x".equals(mark) ? CHECK_X_NUDGE_Y : CHECK_DASH_NUDGE_Y;
            int ty = y + (MINI - fontRenderer.FONT_HEIGHT) / 2 + nudge;
            drawString(fontRenderer, mark, tx, ty, col);
        }
    }

    private void drawPreviewFrame(AstData.HudConfig hud) {
        int[] r = previewRect(hud);
        int border = 0x88FFFFFF;
        int fill = 0x22000000;
        drawRect(r[0], r[1], r[2], r[3], fill);
        drawHorizontalLine(r[0], r[2], r[1], border);
        drawHorizontalLine(r[0], r[2], r[3], border);
        drawVerticalLine(r[0], r[1], r[3], border);
        drawVerticalLine(r[2], r[1], r[3], border);
        // (text intentionally omitted: it overlaps with HUD preview and reduces readability)
    }

    private int[] previewRect(AstData.HudConfig hud) {
        // Estimate HUD bounds (used only for drag region).
        // Must reflect split rows so drag area grows/shrinks when rows changes.
        FontRenderer fr = fontRenderer;

        int gap = clampInt(hud.splitListGap, 0, 80);
        int minEllipsis = fr.getStringWidth("...");
        if (gap < minEllipsis) gap = minEllipsis;
        int primW = Math.max(10, hud.splitPrimaryWidth);
        int secW = Math.max(10, hud.splitSecondaryWidth);
        int splitW = gap + primW + gap + secW;
        int w = Math.max(60, splitW);

        // Count enabled lines
        AstHudConfigUtil.ensureItemOrder(hud);
        int lines = 0;
        for (String k : hud.itemOrder) {
            if (!hud.toggles.getOrDefault(k, false)) continue;
            if ("splitlist".equals(k)) {
                // Renderer draws a "Splits:" header line + rows.
                lines += 1 + Math.max(0, hud.splitListCount);
            } else {
                lines += 1;
            }
        }
        if (lines < 1) lines = 1;
        int yStep = fr.FONT_HEIGHT + Math.max(0, hud.splitListLineGap);
        int h = 6 + lines * yStep;
        int x = hud.offsetX;
        int y = hud.offsetY;
        double sc = Math.max(0.2, hud.scale <= 0 ? 1.0 : hud.scale);
        int x2 = x + (int) (w * sc);
        int y2 = y + (int) (h * sc);
        return new int[]{x, y, x2, y2};
    }

    // ---------------------------
    // Layout Tab
    // ---------------------------

    private void drawLayoutPanel(AstData.HudConfig hud, int px, int py, int pw, int ph, int mouseX, int mouseY) {
        FontRenderer fr = fontRenderer;
        int x = px + PAD;
        int y = py + PAD;

        drawString(fr, "Layout", x, y, 0xFFFFFF);
        y += 18;

        // Bottom UI (final spec): Select + Name + SaveAs/Load/Delete
        int bottomButtonsRowY = py + ph - PAD - ROW_H;          // row origin
        int bottomNameRowY = bottomButtonsRowY - ROW_H;         // row origin
        int bottomSelectRowY = bottomNameRowY - ROW_H;          // row origin

        // list area
        int listTop = y;
        int listBottom = bottomSelectRowY - 6;
        int visibleRows = Math.max(1, (listBottom - listTop) / ROW_H);

        AstHudConfigUtil.ensureItemOrder(hud);
        List<String> order = hud.itemOrder;
        int maxScroll = Math.max(0, order.size() - visibleRows);
        if (scroll > maxScroll) scroll = maxScroll;
        if (scroll < 0) scroll = 0;

        int rowX = x;
        int rowW = pw - PAD * 2;

        for (int i = 0; i < visibleRows; i++) {
            int idx = scroll + i;
            if (idx >= order.size()) break;
            String key = order.get(idx);
            boolean on = hud.toggles.get(key) == null || hud.toggles.get(key);
            // Locked items: cannot be turned off in Layout.
            boolean canToggle = !("time".equals(key) || "segment".equals(key) || "segmentTime".equals(key));

            int ry = listTop + i * ROW_H;

            // checkbox (vertically centered inside ROW_H)
            drawCheck(rowX, miniY(ry), on, canToggle);

            // label
            String label = itemLabel(key);
            int labelX = rowX + MINI + 6;

            // up/down buttons on right
            int btnDnX = rowX + rowW - MINI;
            int btnUpX = rowX + rowW - MINI * 2 - 2;
            boolean canUp = (idx > 0);
            boolean canDn = (idx < order.size() - 1);

            drawMiniButton(btnUpX, miniY(ry), MINI, MINI, "^", canUp, false);
            drawMiniButton(btnDnX, miniY(ry), MINI, MINI, "v", canDn, false);

            int labelMax = btnUpX - 6 - labelX;
            String shown = ellipsize(fr, label, Math.max(0, labelMax));
            int ty = ry + (ROW_H - fr.FONT_HEIGHT) / 2 + TEXT_NUDGE_Y;
            drawString(fr, shown, labelX + TEXT_NUDGE_X, ty, 0xE0E0E0);
        }

        int saveW = pw - PAD * 2;

        // Select row (cycles through existing saved layouts)
        refreshLayoutNames();
        String selected = layoutNamesCache.isEmpty() ? "" : layoutNamesCache.get(clampIndex(layoutSelect, layoutNamesCache.size()));
        int selTy = bottomSelectRowY + (ROW_H - fr.FONT_HEIGHT) / 2 + TEXT_NUDGE_Y;
        drawString(fr, "Select:", x + TEXT_NUDGE_X, selTy, 0xCCCCCC);
        int arrowY = miniY(bottomSelectRowY);
        int arrowL = x + saveW - MINI * 2 - 2;
        int arrowR = x + saveW - MINI;
        boolean has = !layoutNamesCache.isEmpty();
        drawMiniButton(arrowL, arrowY, MINI, MINI, "<", has, false);
        drawMiniButton(arrowR, arrowY, MINI, MINI, ">", has, false);
        int vxRight = arrowL - 6;
        int vx = vxRight - fr.getStringWidth(selected);
        if (vx < x + 60) vx = x + 60;
        drawString(fr, selected, vx + TEXT_NUDGE_X, selTy, 0xFFFFFF);

        // Layout management UI (final spec)
        // Name: [__________]
        int nameTy = bottomNameRowY + (ROW_H - fr.FONT_HEIGHT) / 2 + TEXT_NUDGE_Y;
        drawString(fr, "Name:", x + TEXT_NUDGE_X, nameTy, 0xCCCCCC);
        int fieldX = x + 44;
        int fieldW = Math.max(90, saveW - 44);
        int fieldY = miniY(bottomNameRowY);
        if (layoutNameField != null) {
            layoutNameField.x = fieldX;
            layoutNameField.y = fieldY;
            layoutNameField.width = fieldW;
            layoutNameField.height = MINI;
            layoutNameField.drawTextBox();
        }

        // [ Save As ] [ Load ] [ Delete ]
        int gap = 6;
        int btnW = (saveW - gap * 2) / 3;
        int by = miniY(bottomButtonsRowY);
        drawMiniButton(x, by, btnW, MINI, "Save As", true, false);
        drawMiniButton(x + btnW + gap, by, btnW, MINI, "Load", true, false);
        drawMiniButton(x + (btnW + gap) * 2, by, btnW, MINI, "Delete", true, false);

        // (no scroll indicator; panel is packed to avoid needing scroll)
    }

    // ---------------------------
    // Style Tab
    // ---------------------------

    private void drawStylePanel(AstData.HudConfig hud, int px, int py, int pw, int ph, int mouseX, int mouseY) {
        FontRenderer fr = fontRenderer;
        int x = px + PAD;
        int y = py + PAD;
        int w = pw - PAD * 2;

        drawString(fr, "Style", x, y, 0xFFFFFF);
        y += 18;

        // Preset / Theme / Format
        y = drawStyleRow(fr, x, y, w, "Preset", hud.preset, true);
        y = drawStyleRow(fr, x, y, w, "Theme", hud.theme, true);
        y = drawStyleRow(fr, x, y, w, "Format", formatLabel(hud.timeFormat), true);

        // SplitList (Livesplit-style)
        y = drawStyleRow(fr, x, y, w, "Comparison", comparisonLabel(hud.comparison), true);
        y = drawStyleRow(fr, x, y, w, "Unit", unitLabel(hud.unit), true);

        // SplitList rows & gaps
        y = drawIntAdjustRow(fr, x, y, w, "Rows", hud.splitListCount, "");
        y = drawIntAdjustRow(fr, x, y, w, "Line Gap", hud.splitListLineGap, "px");
        y = drawIntAdjustRow(fr, x, y, w, "Col Gap", hud.splitListGap, "px");

        // Scale
        y = drawScaleRow(fr, x, y, w, hud.scale);

        // Colors (Label/Main/Sub only)
        y = drawColorRow(fr, x, y, w, "Label", hud.colorLabel);
        y = drawColorRow(fr, x, y, w, "Main", hud.colorMainText);
        y = drawColorRow(fr, x, y, w, "Sub", hud.colorSubText);

        // Layout selection/management lives in the Layout tab.
    }

    private int drawStyleRow(FontRenderer fr, int x, int y, int w, String label, String value, boolean arrows) {
        int rowY = y;
        int ty = rowY + (ROW_H - fr.FONT_HEIGHT) / 2 + TEXT_NUDGE_Y;
        drawString(fr, label, x, ty, 0xCCCCCC);

        int arrowY = miniY(rowY);
        int arrowL = x + w - MINI * 2 - 2;
        int arrowR = x + w - MINI;

        drawMiniButton(arrowL, arrowY, MINI, MINI, "<", arrows, false);
        drawMiniButton(arrowR, arrowY, MINI, MINI, ">", arrows, false);

        String v = (value == null ? "" : value);
        int vxRight = arrowL - 6;
        int vx = vxRight - fr.getStringWidth(v);
        if (vx < x + 60) vx = x + 60;
        drawString(fr, v, vx, ty, 0xFFFFFF);

        return y + ROW_H;
    }

    private int drawFormatRow(FontRenderer fr, int x, int y, int w, String timeFormat) {
        int ty = y + (ROW_H - fr.FONT_HEIGHT) / 2 + TEXT_NUDGE_Y;
        drawString(fr, "Format", x, ty, 0xCCCCCC);

        boolean isTick = "TICKS".equalsIgnoreCase(timeFormat);
        int bx = x + 60;
        int by = miniY(y);
        drawMiniButton(bx, by, 56, MINI, "m:ss", true, !isTick);
        drawMiniButton(bx + 60, by, 56, MINI, "tick", true, isTick);
        return y + ROW_H;
    }

    private int drawIntAdjustRow(FontRenderer fr, int x, int y, int w, String label, int value, String suffix) {
        int ty = y + (ROW_H - fr.FONT_HEIGHT) / 2 + TEXT_NUDGE_Y;
        drawString(fr, label, x, ty, 0xCCCCCC);
        int arrowL = x + w - MINI * 2 - 2;
        int arrowR = x + w - MINI;
        drawMiniButton(arrowL, miniY(y), MINI, MINI, "-", true, false);
        drawMiniButton(arrowR, miniY(y), MINI, MINI, "+", true, false);
        String v = value + (suffix == null ? "" : suffix);
        int vxRight = arrowL - 6;
        int vx = vxRight - fr.getStringWidth(v);
        if (vx < x + 60) vx = x + 60;
        drawString(fr, v, vx, ty, 0xFFFFFF);
        return y + ROW_H;
    }

    private int drawScaleRow(FontRenderer fr, int x, int y, int w, double scale) {
        int ty = y + (ROW_H - fr.FONT_HEIGHT) / 2 + TEXT_NUDGE_Y;
        drawString(fr, "Scale", x, ty, 0xCCCCCC);
        int arrowL = x + w - MINI * 2 - 2;
        int arrowR = x + w - MINI;
        drawMiniButton(arrowL, miniY(y), MINI, MINI, "-", true, false);
        drawMiniButton(arrowR, miniY(y), MINI, MINI, "+", true, false);
        String v = String.format(java.util.Locale.ROOT, "%.2f", scale);
        int vxRight = arrowL - 6;
        int vx = vxRight - fr.getStringWidth(v);
        if (vx < x + 60) vx = x + 60;
        drawString(fr, v, vx, ty, 0xFFFFFF);
        return y + ROW_H;
    }

    private int drawColorRow(FontRenderer fr, int x, int y, int w, String label, String colorCode) {
        int ty = y + (ROW_H - fr.FONT_HEIGHT) / 2 + TEXT_NUDGE_Y;
        drawString(fr, label, x, ty, 0xCCCCCC);
        int arrowL = x + w - MINI * 2 - 2;
        int arrowR = x + w - MINI;
        drawMiniButton(arrowL, miniY(y), MINI, MINI, "<", true, false);
        drawMiniButton(arrowR, miniY(y), MINI, MINI, ">", true, false);

        String cc = (colorCode == null || colorCode.trim().isEmpty()) ? "f" : colorCode.trim();
        if (cc.startsWith("§") && cc.length() >= 2) cc = cc.substring(1, 2);
        if (cc.length() > 1) cc = cc.substring(0, 1);
        cc = cc.toLowerCase(java.util.Locale.ROOT);
        int vxRight = arrowL - 6;
        int chip = 10;
        int chipX = Math.max(x + 60, vxRight - chip - 16);
        int chipY = miniY(y);
        int rgb = rgbFromCode(cc.charAt(0));
        drawRect(chipX, chipY, chipX + chip, chipY + chip, 0xFF000000 | rgb);
        drawHorizontalLine(chipX, chipX + chip, chipY, 0xAAFFFFFF);
        drawHorizontalLine(chipX, chipX + chip, chipY + chip, 0xAAFFFFFF);
        drawVerticalLine(chipX, chipY, chipY + chip, 0xAAFFFFFF);
        drawVerticalLine(chipX + chip, chipY, chipY + chip, 0xAAFFFFFF);
        drawString(fr, cc, chipX + chip + 6, ty, 0xFFFFFF);
        return y + ROW_H;
    }

    private int drawLayoutsRow(FontRenderer fr, int x, int y, int w) {
        // Selection (cycle through existing layouts)
        if (layoutNamesCache == null) layoutNamesCache = new java.util.ArrayList<>();
        String selected = layoutNamesCache.isEmpty() ? "" : layoutNamesCache.get(clampIndex(layoutSelect, layoutNamesCache.size()));

        int ty = y + (ROW_H - fr.FONT_HEIGHT) / 2 + TEXT_NUDGE_Y;
        drawString(fr, "Select", x, ty, 0xCCCCCC);

        int arrowL = x + w - MINI * 2 - 2;
        int arrowR = x + w - MINI;
        drawMiniButton(arrowL, miniY(y), MINI, MINI, "<", !layoutNamesCache.isEmpty(), false);
        drawMiniButton(arrowR, miniY(y), MINI, MINI, ">", !layoutNamesCache.isEmpty(), false);

        String v = selected;
        int vxRight = arrowL - 6;
        int vx = vxRight - fr.getStringWidth(v);
        if (vx < x + 60) vx = x + 60;
        drawString(fr, v, vx, ty, 0xFFFFFF);
        y += ROW_H;

        // Name field
        drawString(fr, "Name", x, y + (ROW_H - fr.FONT_HEIGHT) / 2 + TEXT_NUDGE_Y, 0xCCCCCC);
        int fieldX = x + 60;
        int fieldW = Math.max(90, w - 60);
        int fieldY = miniY(y);
        if (layoutNameField != null) {
            layoutNameField.x = fieldX;
            layoutNameField.y = fieldY;
            layoutNameField.width = fieldW;
            layoutNameField.height = MINI;
            layoutNameField.drawTextBox();
        }
        y += ROW_H;

        // Buttons
        int bx = x;
        drawMiniButton(bx, miniY(y), 60, MINI, "Save As", true, false);
        drawMiniButton(bx + 64, miniY(y), 60, MINI, "Load", true, false);
        drawMiniButton(bx + 128, miniY(y), 60, MINI, "Delete", true, false);
        return y + ROW_H;
    }

    private void refreshLayoutNames() {
        try {
            this.layoutNamesCache = AstLayoutManager.listLayouts();
            if (layoutSelect < 0) layoutSelect = 0;
            if (layoutSelect >= layoutNamesCache.size()) layoutSelect = Math.max(0, layoutNamesCache.size() - 1);
        } catch (Exception ignored) {
            this.layoutNamesCache = new java.util.ArrayList<>();
            this.layoutSelect = 0;
        }
    }

    private static int clampIndex(int idx, int size) {
        if (size <= 0) return 0;
        if (idx < 0) return 0;
        if (idx >= size) return size - 1;
        return idx;
    }

    private static int rgbFromCode(char c) {
        // Minecraft 16-color palette (approx).
        switch (Character.toLowerCase(c)) {
            case '0': return 0x000000;
            case '1': return 0x0000AA;
            case '2': return 0x00AA00;
            case '3': return 0x00AAAA;
            case '4': return 0xAA0000;
            case '5': return 0xAA00AA;
            case '6': return 0xFFAA00;
            case '7': return 0xAAAAAA;
            case '8': return 0x555555;
            case '9': return 0x5555FF;
            case 'a': return 0x55FF55;
            case 'b': return 0x55FFFF;
            case 'c': return 0xFF5555;
            case 'd': return 0xFF55FF;
            case 'e': return 0xFFFF55;
            case 'f':
            default:  return 0xFFFFFF;
        }
    }

    // ---------------------------
    // Input
    // ---------------------------

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        // Name field is used in both tabs.
        if (layoutNameField != null && layoutNameField.textboxKeyTyped(typedChar, keyCode)) {
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);

        AstData.HudConfig hud = hud();
        if (hud == null) return;

        // Name field click (both tabs)
        if (layoutNameField != null) {
            layoutNameField.mouseClicked(mouseX, mouseY, mouseButton);
        }

        // Tabs (highest priority)
        int tabY = 6;
        int tabX = tabGroupX();
        int wLayout = tabWidth("Layout");
        int wStyle = tabWidth("Style");
        if (hit(mouseX, mouseY, tabX, tabY, wLayout, TAB_H)) {
            playClick();
            tab = Tab.LAYOUT;
            return;
        }
        if (hit(mouseX, mouseY, tabX + wLayout + TAB_GAP, tabY, wStyle, TAB_H)) {
            playClick();
            tab = Tab.STYLE;
            return;
        }

        // Panel click routing (higher priority than preview drag)
        int px = this.width - PANEL_W - 10;
        int py = 24;
        int ph = this.height - py - 10;
        boolean consumed;
        if (tab == Tab.LAYOUT) {
            consumed = handleLayoutClick(hud, px, py, PANEL_W, ph, mouseX, mouseY);
        } else {
            consumed = handleStyleClick(hud, px, py, PANEL_W, ph, mouseX, mouseY);
        }
        if (consumed) return;

        // Dragging: click inside preview rect (only if no UI consumed the click)
        int[] r = previewRect(hud);
        if (hit(mouseX, mouseY, r[0], r[1], r[2] - r[0], r[3] - r[1])) {
            dragging = true;
            dragOffX = mouseX - hud.offsetX;
            dragOffY = mouseY - hud.offsetY;
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        dragging = false;
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        AstData.HudConfig hud = hud();
        if (hud == null) return;
        if (!dragging) return;
        hud.offsetX = mouseX - dragOffX;
        hud.offsetY = mouseY - dragOffY;
        saveCourse();
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0 && tab == Tab.LAYOUT) {
            if (wheel > 0) scroll -= 1;
            else scroll += 1;
            clampScroll();
        }
    }

    private void clampScroll() {
        AstData.HudConfig hud = hud();
        if (hud == null) { scroll = 0; return; }

        int px = this.width - PANEL_W - 10;
        int py = 24;
        int ph = this.height - py - 10;

        int listTop = py + PAD + 18;

        int bottomButtonsRowY = py + ph - PAD - ROW_H;
        int bottomNameRowY = bottomButtonsRowY - ROW_H;
        int bottomSelectRowY = bottomNameRowY - ROW_H;

        int listBottom = bottomSelectRowY - 6;
        int visibleRows = Math.max(1, (listBottom - listTop) / ROW_H);

        AstHudConfigUtil.ensureItemOrder(hud);
        int maxScroll = Math.max(0, hud.itemOrder.size() - visibleRows);
        if (scroll < 0) scroll = 0;
        if (scroll > maxScroll) scroll = maxScroll;
    }

    private boolean handleLayoutClick(AstData.HudConfig hud, int px, int py, int pw, int ph, int mouseX, int mouseY) {
        int x = px + PAD;
        int y = py + PAD + 18; // after title
        int w = pw - PAD * 2;

        int bottomButtonsRowY = py + ph - PAD - ROW_H;
        int bottomNameRowY = bottomButtonsRowY - ROW_H;
        int bottomSelectRowY = bottomNameRowY - ROW_H;

        int listBottom = bottomSelectRowY - 6;
        int visibleRows = Math.max(1, (listBottom - y) / ROW_H);

        AstHudConfigUtil.ensureItemOrder(hud);
        List<String> order = hud.itemOrder;
        int maxScroll = Math.max(0, order.size() - visibleRows);
        if (scroll > maxScroll) scroll = maxScroll;
        if (scroll < 0) scroll = 0;

        // rows
        for (int i = 0; i < visibleRows; i++) {
            int idx = scroll + i;
            if (idx >= order.size()) break;
            String key = order.get(idx);

            int ry = y + i * ROW_H;

            // checkbox
            if (hit(mouseX, mouseY, x, miniY(ry), MINI, MINI)) {
                // Time is mandatory.
                if ("time".equals(key)) {
                    playClick();
                    return true;
                }
                boolean cur = hud.toggles.get(key) == null || hud.toggles.get(key);
                hud.toggles.put(key, !cur);
                playClick();
                saveCourse();
                return true;
            }

            // up/down
            int btnDnX = x + w - MINI;
            int btnUpX = x + w - MINI * 2 - 2;
            if (hit(mouseX, mouseY, btnUpX, miniY(ry), MINI, MINI) && idx > 0) {
                String tmp = order.get(idx - 1);
                order.set(idx - 1, order.get(idx));
                order.set(idx, tmp);
                playClick();
                saveCourse();
                return true;
            }
            if (hit(mouseX, mouseY, btnDnX, miniY(ry), MINI, MINI) && idx < order.size() - 1) {
                String tmp = order.get(idx + 1);
                order.set(idx + 1, order.get(idx));
                order.set(idx, tmp);
                playClick();
                saveCourse();
                return true;
            }
        }

        // Select row: cycle names; also sync the name field to the selected layout
        refreshLayoutNames();
        int arrowL = x + w - MINI * 2 - 2;
        int arrowR = x + w - MINI;
        if (!layoutNamesCache.isEmpty()) {
            if (hit(mouseX, mouseY, arrowL, miniY(bottomSelectRowY), MINI, MINI)) {
                playClick();
                layoutSelect = clampIndex(layoutSelect - 1, layoutNamesCache.size());
                if (layoutNameField != null) layoutNameField.setText(layoutNamesCache.get(layoutSelect));
                return true;
            }
            if (hit(mouseX, mouseY, arrowR, miniY(bottomSelectRowY), MINI, MINI)) {
                playClick();
                layoutSelect = clampIndex(layoutSelect + 1, layoutNamesCache.size());
                if (layoutNameField != null) layoutNameField.setText(layoutNamesCache.get(layoutSelect));
                return true;
            }
        }

        // Final layout management buttons (Name field itself is handled by GuiTextField)
        int gap = 6;
        int btnW = (w - gap * 2) / 3;
        int by = miniY(bottomButtonsRowY);

        // Save As
        if (hit(mouseX, mouseY, x, by, btnW, MINI)) {
            playClick();
            String name = layoutNameField == null ? "" : layoutNameField.getText().trim();
            if (!name.isEmpty()) {
                AstLayoutManager.saveLayoutSafe(name, hud);
                refreshLayoutNames();
                layoutSelect = layoutNamesCache.indexOf(name);
                if (layoutSelect < 0) layoutSelect = 0;
            }
            return true;
        }

        // Load
        if (hit(mouseX, mouseY, x + btnW + gap, by, btnW, MINI)) {
            playClick();
            String name = layoutNameField == null ? "" : layoutNameField.getText().trim();
            if (!name.isEmpty()) {
                AstLayoutManager.loadLayoutToHudSafe(name, hud);
                saveCourse();
                clampScroll();
                if (layoutNameField != null) layoutNameField.setText(name);
            }
            return true;
        }

        // Delete
        if (hit(mouseX, mouseY, x + (btnW + gap) * 2, by, btnW, MINI)) {
            playClick();
            String name = layoutNameField == null ? "" : layoutNameField.getText().trim();
            if (!name.isEmpty()) {
                AstLayoutManager.deleteLayoutSafe(name);
                refreshLayoutNames();
                if (layoutNameField != null) layoutNameField.setText("");
            }
            return true;
        }

        return false;
    }

    private boolean handleStyleClick(AstData.HudConfig hud, int px, int py, int pw, int ph, int mouseX, int mouseY) {
        int x = px + PAD;
        int y = py + PAD + 18;
        int w = pw - PAD * 2;

        // Preset row
        int arrowL = x + w - MINI * 2 - 2;
        int arrowR = x + w - MINI;
        if (hit(mouseX, mouseY, arrowL, miniY(y), MINI, MINI)) { playClick(); cyclePreset(hud, -1); saveCourse(); return true; }
        if (hit(mouseX, mouseY, arrowR, miniY(y), MINI, MINI)) { playClick(); cyclePreset(hud, +1); saveCourse(); return true; }
        y += ROW_H;

        // Theme row
        arrowL = x + w - MINI * 2 - 2;
        arrowR = x + w - MINI;
        if (hit(mouseX, mouseY, arrowL, miniY(y), MINI, MINI)) { playClick(); cycleTheme(hud, -1); saveCourse(); return true; }
        if (hit(mouseX, mouseY, arrowR, miniY(y), MINI, MINI)) { playClick(); cycleTheme(hud, +1); saveCourse(); return true; }
        y += ROW_H;

        // Format row (< >)
        arrowL = x + w - MINI * 2 - 2;
        arrowR = x + w - MINI;
        if (hit(mouseX, mouseY, arrowL, miniY(y), MINI, MINI) || hit(mouseX, mouseY, arrowR, miniY(y), MINI, MINI)) {
            playClick();
            String curFmt = hud.timeFormat == null ? "MSS" : hud.timeFormat.trim();
            if ("seconds".equalsIgnoreCase(curFmt)) hud.timeFormat = "MSS";
            else if ("MSS".equalsIgnoreCase(curFmt)) hud.timeFormat = "TICKS";
            else hud.timeFormat = "seconds";
            saveCourse();
            return true;
        }
        y += ROW_H;

        // Comparison row (< >)
        arrowL = x + w - MINI * 2 - 2;
        arrowR = x + w - MINI;
        if (hit(mouseX, mouseY, arrowL, miniY(y), MINI, MINI) || hit(mouseX, mouseY, arrowR, miniY(y), MINI, MINI)) {
            playClick();
            hud.comparison = ("best".equalsIgnoreCase(hud.comparison) ? "pb" : "best");
            AstHudConfigUtil.normalizeHud(hud);
            saveCourse();
            return true;
        }
        y += ROW_H;

        // Unit row (< >)
        arrowL = x + w - MINI * 2 - 2;
        arrowR = x + w - MINI;
        if (hit(mouseX, mouseY, arrowL, miniY(y), MINI, MINI) || hit(mouseX, mouseY, arrowR, miniY(y), MINI, MINI)) {
            playClick();
            hud.unit = ("seg".equalsIgnoreCase(hud.unit) ? "split" : "seg");
            AstHudConfigUtil.normalizeHud(hud);
            saveCourse();
            return true;
        }
        y += ROW_H;

        // Rows +/-
        arrowL = x + w - MINI * 2 - 2;
        arrowR = x + w - MINI;
        if (hit(mouseX, mouseY, arrowL, miniY(y), MINI, MINI)) { playClick(); hud.splitListCount = clampInt(hud.splitListCount - 1, 2, 20); saveCourse(); return true; }
        if (hit(mouseX, mouseY, arrowR, miniY(y), MINI, MINI)) { playClick(); hud.splitListCount = clampInt(hud.splitListCount + 1, 2, 20); saveCourse(); return true; }
        y += ROW_H;

        // Line gap +/-
        arrowL = x + w - MINI * 2 - 2;
        arrowR = x + w - MINI;
        if (hit(mouseX, mouseY, arrowL, miniY(y), MINI, MINI)) { playClick(); hud.splitListLineGap = clampInt(hud.splitListLineGap - 1, 0, 8); saveCourse(); return true; }
        if (hit(mouseX, mouseY, arrowR, miniY(y), MINI, MINI)) { playClick(); hud.splitListLineGap = clampInt(hud.splitListLineGap + 1, 0, 8); saveCourse(); return true; }
        y += ROW_H;

        // Column gap +/- (SplitList only)
        arrowL = x + w - MINI * 2 - 2;
        arrowR = x + w - MINI;
        // Allow 0. Renderer clamps effective gap to at least the width of "...".
        if (hit(mouseX, mouseY, arrowL, miniY(y), MINI, MINI)) {
            playClick();
            hud.splitListGap = clampInt(hud.splitListGap - 1, 0, 80);
            saveCourse();
            return true;
        }
        if (hit(mouseX, mouseY, arrowR, miniY(y), MINI, MINI)) {
            playClick();
            hud.splitListGap = clampInt(hud.splitListGap + 1, 0, 80);
            saveCourse();
            return true;
        }
        y += ROW_H;

        // Scale +/- (0.05 steps)
        arrowL = x + w - MINI * 2 - 2;
        arrowR = x + w - MINI;
        if (hit(mouseX, mouseY, arrowL, miniY(y), MINI, MINI)) { playClick(); hud.scale = clampScale(hud.scale - 0.05); saveCourse(); return true; }
        if (hit(mouseX, mouseY, arrowR, miniY(y), MINI, MINI)) { playClick(); hud.scale = clampScale(hud.scale + 0.05); saveCourse(); return true; }
        y += ROW_H;

        // Colors: label/main/sub only
        if (handleColorRowClick(hud, x, y, w, mouseX, mouseY, "label")) return true;
        y += ROW_H;
        if (handleColorRowClick(hud, x, y, w, mouseX, mouseY, "main")) return true;
        y += ROW_H;
        if (handleColorRowClick(hud, x, y, w, mouseX, mouseY, "sub")) return true;

        // Layout selection/management is handled in the Layout tab.
        return false;
    }

    private void cyclePreset(AstData.HudConfig hud, int dir) {
        String cur = hud.preset == null ? "standard" : hud.preset.trim().toLowerCase(Locale.ROOT);
        int i = PRESET_ORDER.indexOf(cur);
        if (i < 0) i = 0;
        int j = (i + dir) % PRESET_ORDER.size();
        if (j < 0) j += PRESET_ORDER.size();
        String next = PRESET_ORDER.get(j);

        // Apply preset (keep position)
        AstHudConfigUtil.applyPreset(hud, next, true);
        AstHudConfigUtil.applyTheme(hud);
        AstHudConfigUtil.normalizeHud(hud);
        clampScroll();
    }

    private void cycleTheme(AstData.HudConfig hud, int dir) {
        String cur = hud.theme == null ? "default" : hud.theme.trim().toLowerCase(Locale.ROOT);
        int i = THEME_ORDER.indexOf(cur);
        if (i < 0) i = 0;
        int j = (i + dir) % THEME_ORDER.size();
        if (j < 0) j += THEME_ORDER.size();
        hud.theme = THEME_ORDER.get(j);
        AstHudConfigUtil.applyTheme(hud);
        AstHudConfigUtil.normalizeHud(hud);
    }

    private void playClick() {
        try {
            net.minecraft.client.Minecraft.getMinecraft().getSoundHandler()
                    .playSound(net.minecraft.client.audio.PositionedSoundRecord.getMasterRecord(
                            net.minecraft.init.SoundEvents.UI_BUTTON_CLICK, 1.0F));
        } catch (Exception ignored) {}
    }

    private static int miniY(int rowY) {
        return rowY + (ROW_H - MINI) / 2;
    }

    private static int clampInt(int v, int lo, int hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    private static double clampScale(double s) {
        if (s < 0.50) return 0.50;
        if (s > 1.50) return 1.50;
        // snap to 0.05
        double snapped = Math.round(s / 0.05) * 0.05;
        if (snapped < 0.50) snapped = 0.50;
        if (snapped > 1.50) snapped = 1.50;
        return snapped;
    }

    private static final String[] SPLIT_PRIMARY_ORDER = {"seg", "split"};
    private static final String[] SPLIT_SECONDARY_ORDER = {"none", "pbseg", "pbsplit", "bestseg", "bestsplit"};
    private static final String[] COLOR_ORDER = {"0","1","2","3","4","5","6","7","8","9","a","b","c","d","e","f"};

    private void cycleSplitColsPrimary(AstData.HudConfig hud, int dir) {
        String cur = hud.splitColsPrimary == null ? "split" : hud.splitColsPrimary.trim().toLowerCase(Locale.ROOT);
        int i = indexOf(SPLIT_PRIMARY_ORDER, cur);
        int j = (i + dir) % SPLIT_PRIMARY_ORDER.length;
        if (j < 0) j += SPLIT_PRIMARY_ORDER.length;
        hud.splitColsPrimary = SPLIT_PRIMARY_ORDER[j];
    }

    private void cycleSplitColsSecondary(AstData.HudConfig hud, int dir) {
        String cur = hud.splitColsSecondary == null ? "none" : hud.splitColsSecondary.trim().toLowerCase(Locale.ROOT);
        int i = indexOf(SPLIT_SECONDARY_ORDER, cur);
        int j = (i + dir) % SPLIT_SECONDARY_ORDER.length;
        if (j < 0) j += SPLIT_SECONDARY_ORDER.length;
        hud.splitColsSecondary = SPLIT_SECONDARY_ORDER[j];
    }

    private boolean handleColorRowClick(AstData.HudConfig hud, int x, int y, int w, int mouseX, int mouseY, String which) {
        int arrowL = x + w - MINI * 2 - 2;
        int arrowR = x + w - MINI;
        if (hit(mouseX, mouseY, arrowL, miniY(y), MINI, MINI)) {
            playClick();
            setColorField(hud, which, cycleColor(getColorField(hud, which), -1));
            saveCourse();
            return true;
        }
        if (hit(mouseX, mouseY, arrowR, miniY(y), MINI, MINI)) {
            playClick();
            setColorField(hud, which, cycleColor(getColorField(hud, which), +1));
            saveCourse();
            return true;
        }
        return false;
    }

    private static String getColorField(AstData.HudConfig hud, String which) {
        if (hud == null) return "f";
        switch (which) {
            case "label": return hud.colorLabel;
            case "main": return hud.colorMainText;
            case "sub": return hud.colorSubText;
            default: return "f";
        }
    }

    private static void setColorField(AstData.HudConfig hud, String which, String val) {
        if (hud == null) return;
        if (val == null || val.trim().isEmpty()) val = "f";
        switch (which) {
            case "label": hud.colorLabel = val; break;
            case "main": hud.colorMainText = val; break;
            case "sub": hud.colorSubText = val; break;
        }
    }

    private static String cycleColor(String cur, int dir) {
        String c = (cur == null || cur.trim().isEmpty()) ? "f" : cur.trim();
        if (c.startsWith("§") && c.length() >= 2) c = c.substring(1,2);
        if (c.length() > 1) c = c.substring(0,1);
        c = c.toLowerCase(Locale.ROOT);
        int i = indexOf(COLOR_ORDER, c);
        int j = (i + dir) % COLOR_ORDER.length;
        if (j < 0) j += COLOR_ORDER.length;
        return COLOR_ORDER[j];
    }

    private static int indexOf(String[] arr, String v) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i].equals(v)) return i;
        }
        return 0;
    }

    private static boolean hit(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    private static String itemLabel(String key) {
        if (key == null) return "";
        String k = key.toLowerCase(Locale.ROOT);
        switch (k) {
            case "coursename": return "Course Name";
            case "time": return "Time";
            case "segment": return "Segment";
            case "segmenttime": return "Segment Time";
            case "prevseg": return "Prev Segment";
            case "sob": return "Sum of Best";
            case "bpt": return "Best Possible Time";
            case "bestseg": return "Best Segment";
            case "bestsplit": return "Best Split";
            case "attempt": return "Attempts";
            case "splitlist": return "Split List";
            default: return key;
        }
    }

    private static String ellipsize(FontRenderer fr, String s, int maxWidth) {
        if (s == null) return "";
        if (maxWidth <= 0) return "";
        if (fr.getStringWidth(s) <= maxWidth) return s;
        String ell = "...";
        int ellW = fr.getStringWidth(ell);
        if (ellW >= maxWidth) return ell;
        String t = s;
        while (!t.isEmpty() && fr.getStringWidth(t) + ellW > maxWidth) {
            t = t.substring(0, t.length() - 1);
        }
        return t + ell;
    }

    public void refreshFromModel() {
        clampScroll();
    }

}
