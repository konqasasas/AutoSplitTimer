package com.konqasasas.ast.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.konqasasas.ast.core.AstCourseManager;
import com.konqasasas.ast.core.AstData;
import com.konqasasas.ast.core.AstUtil;
import com.konqasasas.ast.hud.AstHudConfigUtil;
import com.konqasasas.ast.hud.AstHudModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Dependency-free HUD used in game and by the native settings preview. */
public final class AstNativeHudRenderer {
    private static final List<String> DEFAULT_ORDER = Arrays.asList(
            "courseName", "time", "segment", "segmentTime", "bpt", "sob",
            "splitList", "prevSeg", "attempt", "bestSeg", "bestSplit");

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.world == null || minecraft.player == null || minecraft.gameSettings.hideGUI) return;
        AstData.CourseFile course = AstCourseManager.get().getActiveCourse();
        if (course == null || course.hud == null || "off".equalsIgnoreCase(course.hud.preset)) return;

        AstData.HudConfig hud = course.hud;
        AstHudConfigUtil.normalizeHud(hud);
        JsonObject model = AstHudModel.buildSnapshot(course, hud);
        JsonObject values = model.getAsJsonObject("values");
        JsonArray splits = model.getAsJsonArray("splits");
        int panelWidth = Math.max(160, hud.splitListWidth);
        int panelHeight = measureHeight(hud, splits.size());
        ScaledResolution resolution = new ScaledResolution(minecraft);
        double renderScale = Math.max(.25, Math.min(3.0, hud.scale)) * 1.35
                / Math.max(1, resolution.getScaleFactor());
        double shownWidth = panelWidth * renderScale;
        double shownHeight = panelHeight * renderScale;
        double x = anchoredX(hud, resolution, shownWidth);
        double y = anchoredY(hud, resolution, shownHeight);

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(renderScale, renderScale, 1);
        drawPanel(hud, values, splits, panelWidth, panelHeight);
        GlStateManager.popMatrix();
    }

    private static void drawPanel(AstData.HudConfig hud, JsonObject values, JsonArray splits, int width, int height) {
        Gui.drawRect(0, 0, width, height, rgba(hud.backgroundRgb, hud.backgroundOpacity));
        int y = 9;
        List<String> order = hud.itemOrder == null || hud.itemOrder.isEmpty() ? DEFAULT_ORDER : hud.itemOrder;
        for (String key : order) {
            if (!enabled(hud, key)) continue;
            if ("splitList".equals(key)) {
                y = drawSplits(hud, splits, 10, y + 2, width - 20);
            } else {
                int rowHeight = 18 + Math.max(0, hud.splitListLineGap);
                String label = label(key);
                String value = value(values, key);
                float valueSize = "time".equals(key) ? 20f : 11f;
                AstFonts.semibold().draw(label, 10, y + ("time".equals(key) ? 7 : 3), 8, rgb(hud.labelRgb, 0xFF8A949B));
                AstFont valueFont = "time".equals(key) || isNumericKey(key) ? AstFonts.mono() : AstFonts.ui(value, false);
                int valueWidth = valueFont.width(value, valueSize);
                valueFont.draw(value, width - 10 - valueWidth, y + ("time".equals(key) ? 0 : 1), valueSize,
                        "time".equals(key) ? rgb(hud.mainRgb, 0xFFFFFFFF) : rgb(hud.subRgb, 0xFFD7DDE0));
                y += "time".equals(key) ? 27 + Math.max(0, hud.splitListLineGap) : rowHeight;
            }
        }
    }

    private static int drawSplits(AstData.HudConfig hud, JsonArray splits, int x, int y, int width) {
        int line = 22 + Math.max(0, hud.splitListLineGap);
        int labelColor = rgb(hud.labelRgb, 0xFF8A949B);
        int subColor = rgb(hud.subRgb, 0xFFD7DDE0);
        for (int index = 0; index < splits.size(); index++) {
            JsonObject row = splits.get(index).getAsJsonObject();
            boolean active = "active".equals(value(row, "state"));
            if (active) Gui.drawRect(x, y, x + width, y + line, 0xFF23292D);
            String name = value(row, "name");
            String primary = value(row, "primary");
            String secondary = value(row, "secondary");
            int secondaryWidth = AstFonts.mono().width(secondary, 11);
            int secondaryX = x + width - secondaryWidth;
            int primaryWidth = AstFonts.mono().width(primary, 11);
            int primaryX = Math.max(x + 70, secondaryX - 8 - primaryWidth + hud.splitDeltaOffset);
            int nameRight = hud.splitColumns == 2 ? primaryX - 8 : secondaryX - 8;
            name = ellipsize(name, Math.max(22, nameRight - x));
            AstFonts.ui(name, true).draw(name, x + 2, y + 4, 10, active ? 0xFFC8A348 : labelColor);
            if (hud.splitColumns == 2 && !primary.isEmpty()) {
                AstFonts.mono().draw(primary, primaryX, y + 3, 11, toneColor(value(row, "tone"), subColor));
            }
            AstFonts.mono().draw(secondary, secondaryX, y + 3, 11, subColor);
            if (index < splits.size() - 1) Gui.drawRect(x, y + line - 1, x + width, y + line, 0xFF282D31);
            y += line;
        }
        return y;
    }

    private static int measureHeight(AstData.HudConfig hud, int splitCount) {
        int height = 19;
        List<String> order = hud.itemOrder == null || hud.itemOrder.isEmpty() ? DEFAULT_ORDER : hud.itemOrder;
        for (String key : order) {
            if (!enabled(hud, key)) continue;
            if ("splitList".equals(key)) height += 2 + splitCount * (22 + Math.max(0, hud.splitListLineGap));
            else if ("time".equals(key)) height += 27 + Math.max(0, hud.splitListLineGap);
            else height += 18 + Math.max(0, hud.splitListLineGap);
        }
        return height;
    }

    /** Size and draw helper used by the free-position HUD editor. */
    public static final class PositionPreview {
        public final int width;
        public final int height;

        private PositionPreview(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }

    public static PositionPreview drawPositionPreview(AstData.HudConfig hud, int x, int y) {
        AstData.CourseFile course = AstCourseManager.get().getActiveCourse();
        if (course == null) course = sampleCourse();
        JsonObject model = AstHudModel.buildSnapshot(course, hud);
        JsonObject values = model.getAsJsonObject("values");
        JsonArray splits = model.getAsJsonArray("splits");
        int panelWidth = Math.max(160, hud.splitListWidth);
        int panelHeight = measureHeight(hud, splits.size());
        double scale = Math.max(.25, Math.min(3.0, hud.scale)) * 1.35 / AstUiScreen.DESIGN_ZOOM;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(scale, scale, 1);
        drawPanel(hud, values, splits, panelWidth, panelHeight);
        GlStateManager.popMatrix();
        return new PositionPreview((int) Math.ceil(panelWidth * scale), (int) Math.ceil(panelHeight * scale));
    }

    public static PositionPreview positionPreviewSize(AstData.HudConfig hud) {
        AstData.CourseFile course = AstCourseManager.get().getActiveCourse();
        int availableSplits = course == null ? 6 : AstUtil.sortedNonStartIndices(course).size();
        int shownSplits = Math.min(availableSplits, Math.max(0, hud.splitListCount));
        int panelWidth = Math.max(160, hud.splitListWidth);
        int panelHeight = measureHeight(hud, shownSplits);
        double scale = Math.max(.25, Math.min(3.0, hud.scale)) * 1.35 / AstUiScreen.DESIGN_ZOOM;
        return new PositionPreview((int) Math.ceil(panelWidth * scale), (int) Math.ceil(panelHeight * scale));
    }

    private static AstData.CourseFile sampleCourse() {
        AstData.CourseFile course = new AstData.CourseFile();
        course.courseName = "練習コース";
        for (int i = 0; i <= 6; i++) {
            AstData.Segment segment = new AstData.Segment();
            segment.index = i;
            segment.name = i == 0 ? "スタート" : i == 6 ? "ゴール" : "ラップ " + i;
            segment.placed = Boolean.FALSE;
            course.segments.add(segment);
        }
        course.stats.attemptCount = 24;
        course.stats.pb.totalTicks = 2590;
        course.stats.pb.segmentTicks = new java.util.ArrayList<>(Arrays.asList(553, 294, 320, 281, 372, 770));
        course.stats.bestSegmentsTicks = new java.util.ArrayList<>(Arrays.asList(542, 285, 315, 273, 362, 747));
        course.stats.bestSplitTicks = new java.util.ArrayList<>(Arrays.asList(542, 827, 1142, 1415, 1777, 2524));
        return course;
    }

    private static boolean enabled(AstData.HudConfig hud, String key) {
        return hud.toggles == null || hud.toggles.get(key) == null || hud.toggles.get(key);
    }

    private static boolean isNumericKey(String key) {
        return !"courseName".equals(key) && !"segment".equals(key);
    }

    private static String label(String key) {
        switch (key) {
            case "courseName": return "COURSE";
            case "time": return "TIME";
            case "segment": return "SEGMENT";
            case "segmentTime": return "SEG TIME";
            case "prevSeg": return "PREV";
            case "sob": return "SUM OF BEST";
            case "bpt": return "POSSIBLE TIME";
            case "bestSeg": return "BEST SEG";
            case "bestSplit": return "BEST SPLIT";
            case "attempt": return "ATTEMPTS";
            default: return key.toUpperCase(Locale.ROOT);
        }
    }

    private static String value(JsonObject object, String key) {
        return object != null && object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "--";
    }

    private static String ellipsize(String value, int maxWidth) {
        AstFont font = AstFonts.ui(value, true);
        if (font.width(value, 10) <= maxWidth) return value;
        String suffix = "…";
        int length = value.length();
        while (length > 0 && AstFonts.ui(value.substring(0, length) + suffix, true)
                .width(value.substring(0, length) + suffix, 10) > maxWidth) length--;
        return value.substring(0, length) + suffix;
    }

    private static int toneColor(String tone, int fallback) {
        if ("good".equals(tone)) return 0xFF55D68B;
        if ("bad".equals(tone)) return 0xFFE05D65;
        if ("gold".equals(tone)) return 0xFFC8A348;
        return fallback;
    }

    private static int rgba(String color, int opacityPercent) {
        return (Math.max(0, Math.min(255, Math.round(opacityPercent * 2.55f))) << 24) | (rgb(color, 0xFF0B0D0F) & 0xFFFFFF);
    }

    private static int rgb(String color, int fallback) {
        if (color == null) return fallback;
        try { return 0xFF000000 | Integer.parseInt(color.replace("#", ""), 16); }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static double anchoredX(AstData.HudConfig hud, ScaledResolution resolution, double width) {
        String anchor = hud.anchor == null ? "TOP_LEFT" : hud.anchor.toUpperCase(Locale.ROOT);
        if (anchor.endsWith("RIGHT")) return resolution.getScaledWidth_double() - width - hud.offsetX;
        if (anchor.endsWith("CENTER") || "CENTER".equals(anchor)) return (resolution.getScaledWidth_double() - width) / 2 + hud.offsetX;
        return hud.offsetX;
    }

    private static double anchoredY(AstData.HudConfig hud, ScaledResolution resolution, double height) {
        String anchor = hud.anchor == null ? "TOP_LEFT" : hud.anchor.toUpperCase(Locale.ROOT);
        if (anchor.startsWith("BOTTOM")) return resolution.getScaledHeight_double() - height - hud.offsetY;
        if (anchor.startsWith("CENTER") || "CENTER".equals(anchor)) return (resolution.getScaledHeight_double() - height) / 2 + hud.offsetY;
        return hud.offsetY;
    }
}
