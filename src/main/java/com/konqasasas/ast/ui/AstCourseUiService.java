package com.konqasasas.ast.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.konqasasas.ast.core.AstCourseManager;
import com.konqasasas.ast.core.AstData;
import com.konqasasas.ast.core.AstRuntime;
import com.konqasasas.ast.core.AstUtil;
import com.konqasasas.ast.edit.AstAreaEditor;
import com.konqasasas.ast.edit.AstQuickCourseEditor;
import com.konqasasas.ast.hud.AstHudConfigUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.math.MathHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Minecraft-thread service used by the native course editor. */
public final class AstCourseUiService {
    private AstCourseUiService() {}

    public static JsonObject handle(JsonObject request) {
        String type = string(request, "type", "state");
        switch (type) {
            case "state":
                return state();
            case "createCourse":
                createCourse(requiredString(request, "name"));
                return state();
            case "loadCourse":
                loadCourse(requiredString(request, "name"));
                return state();
            case "leaveCourse":
                leaveCourse();
                return state();
            case "addLapCurrent":
                addLapCurrent();
                return state();
            case "setPointCurrent":
                setPointCurrent(requiredInt(request, "index"));
                return state();
            case "updatePoint":
                updatePoint(request);
                return state();
            case "deletePoint":
                deletePoint(requiredInt(request, "index"));
                return state();
            case "movePoint":
                movePoint(requiredInt(request, "index"), requiredInt(request, "direction"));
                return state();
            case "beginAreaEdit":
                AstAreaEditor.get().begin(requiredInt(request, "index"));
                return state();
            case "beginQuickSetup":
                AstQuickCourseEditor.get().begin();
                return state();
            case "endQuickCourse":
                AstQuickCourseEditor.get().stopEditing();
                AstCourseManager.get().clearTemporaryCourse();
                AstRuntime.get().forceResetToIdle();
                AstCourseManager.chat("簡易計測を終了しました");
                return state();
            case "deleteCourse":
                deleteCourse(requiredString(request, "name"));
                return state();
            case "renameCourse":
                renameCourse(requiredString(request, "oldName"), requiredString(request, "newName"));
                return state();
            case "resetRecords":
                resetRecords(request);
                return state();
            case "updateHud":
                updateHud(request);
                return state();
            default:
                throw new IllegalArgumentException("Unknown request: " + type);
        }
    }

    private static JsonObject state() {
        AstCourseManager manager = AstCourseManager.get();
        JsonObject response = new JsonObject();
        response.addProperty("bridge", true);
        response.addProperty("playerAvailable", Minecraft.getMinecraft().player != null);
        response.addProperty("quickCourse", manager.isTemporaryCourseActive());
        String warning = manager.pollWarning();
        if (warning != null) response.addProperty("warning", warning);

        JsonArray courseNames = new JsonArray();
        for (String name : manager.listCourseNames()) courseNames.add(name);
        response.add("courseNames", courseNames);

        response.add("hud", hudState(manager.getHudConfig()));

        AstData.CourseFile course = manager.getActiveCourse();
        if (course == null) {
            response.add("activeCourse", JsonNull.INSTANCE);
            return response;
        }

        JsonObject active = new JsonObject();
        active.addProperty("name", course.courseName);
        JsonObject records = new JsonObject();
        records.addProperty("attemptCount", course.stats == null ? 0 : course.stats.attemptCount);
        records.addProperty("hasPb", course.stats != null && course.stats.pb != null && course.stats.pb.totalTicks != null);
        records.addProperty("hasBestSegments", course.stats != null && hasValue(course.stats.bestSegmentsTicks));
        records.addProperty("hasBestSplits", course.stats != null && hasValue(course.stats.bestSplitTicks));
        active.add("records", records);
        JsonArray points = new JsonArray();
        List<AstData.Segment> ordered = ordered(course);
        for (int position = 0; position < ordered.size(); position++) {
            AstData.Segment segment = ordered.get(position);
            JsonObject point = new JsonObject();
            point.addProperty("index", segment.index);
            point.addProperty("name", segment.name == null ? "" : segment.name);
            point.addProperty("role", position == 0 ? "start" : position == ordered.size() - 1 ? "goal" : "lap");
            point.addProperty("detection", "ground".equals(segment.detection) ? "ground" : "aabb");
            point.addProperty("placed", Boolean.TRUE.equals(segment.placed));
            point.addProperty("height", segment.height);
            int blocks = "ground".equals(segment.detection) && segment.groundCells != null
                    ? segment.groundCells.size() : estimateAabbBlocks(segment);
            point.addProperty("blocks", Math.max(0, blocks));
            points.add(point);
        }
        active.add("points", points);
        response.add("activeCourse", active);
        return response;
    }

    private static JsonObject hudState(AstData.HudConfig hud) {
        if (hud == null) hud = new AstData.HudConfig();
        AstHudConfigUtil.normalizeHud(hud);
        JsonObject result = new JsonObject();
        result.addProperty("preset", hud.preset);
        result.addProperty("theme", hud.theme);
        result.addProperty("scale", hud.scale);
        result.addProperty("timeFormat", hud.timeFormat);
        result.addProperty("deltaTimeFormat", hud.deltaTimeFormat);
        result.addProperty("comparison", hud.comparison);
        result.addProperty("unit", hud.unit);
        result.addProperty("splitListCount", hud.splitListCount);
        result.addProperty("splitListGap", hud.splitListGap);
        result.addProperty("splitListLineGap", hud.splitListLineGap);
        result.addProperty("splitColumns", hud.splitColumns);
        result.addProperty("splitDeltaOffset", hud.splitDeltaOffset);
        result.addProperty("splitListWidth", hud.splitListWidth);
        result.addProperty("anchor", hud.anchor);
        result.addProperty("offsetX", hud.offsetX);
        result.addProperty("offsetY", hud.offsetY);
        result.addProperty("backgroundRgb", hud.backgroundRgb);
        result.addProperty("backgroundOpacity", hud.backgroundOpacity);
        result.addProperty("labelRgb", hud.labelRgb);
        result.addProperty("mainRgb", hud.mainRgb);
        result.addProperty("subRgb", hud.subRgb);
        JsonObject toggles = new JsonObject();
        for (String key : AstHudConfigUtil.knownItems()) {
            Boolean value = hud.toggles.get(key);
            toggles.addProperty(key, value == null || value);
        }
        result.add("toggles", toggles);
        JsonArray order = new JsonArray();
        for (String key : hud.itemOrder) order.add(key);
        result.add("itemOrder", order);
        return result;
    }

    private static void createCourse(String rawName) {
        String name = cleanName(rawName, "コース名");
        AstCourseManager manager = AstCourseManager.get();
        if (manager.courseExists(name)) throw new IllegalArgumentException("同じ名前のコースがあります");

        EntityPlayerSP player = requirePlayer();
        manager.setActiveCourse(name);
        AstData.CourseFile course = requireCourse();
        course.segments.clear();

        AstData.Segment start = newSegment(0, "スタート", "ground");
        placeAtPlayer(start, player);
        AstData.Segment goal = newSegment(1, "ゴール", "ground");
        goal.placed = Boolean.FALSE;
        course.segments.add(start);
        course.segments.add(goal);
        manager.saveActiveCourseSafe();
        AstRuntime.get().forceResetToIdle();
    }

    private static void loadCourse(String name) {
        if (!AstCourseManager.get().loadExistingCourseAsActive(name)) {
            throw new IllegalArgumentException("コースを読み込めませんでした");
        }
        AstRuntime.get().forceResetToIdle();
    }

    private static void leaveCourse() {
        AstAreaEditor.get().cancelForCourseChange();
        AstQuickCourseEditor.get().stopEditing();
        AstCourseManager.get().clearActiveCourse();
        AstRuntime.get().forceResetToIdle();
    }

    private static void addLapCurrent() {
        AstData.CourseFile course = requireCourse();
        EntityPlayerSP player = requirePlayer();
        List<AstData.Segment> ordered = ordered(course);
        if (ordered.size() < 2) throw new IllegalStateException("STARTとGOALが必要です");

        AstData.Segment lap = newSegment(ordered.size() - 1, "ラップ " + (ordered.size() - 1), "ground");
        placeAtPlayer(lap, player);
        ordered.add(ordered.size() - 1, lap);
        reindex(course, ordered);
        clearTimingRecords(course);
        saveEditedCourse();
    }

    private static void setPointCurrent(int index) {
        AstData.Segment segment = requireSegment(index);
        placeAtPlayer(segment, requirePlayer());
        saveEditedCourse();
    }

    private static void updatePoint(JsonObject request) {
        AstData.Segment segment = requireSegment(requiredInt(request, "index"));
        if (request.has("name")) segment.name = cleanPointName(request.get("name").getAsString());
        if (request.has("detection")) {
            String detection = request.get("detection").getAsString().toLowerCase(Locale.ROOT);
            if (!"ground".equals(detection) && !"aabb".equals(detection)) {
                throw new IllegalArgumentException("不明な判定方式です");
            }
            segment.detection = detection;
            if ("ground".equals(detection) && Boolean.TRUE.equals(segment.placed)
                    && (segment.groundCells == null || segment.groundCells.isEmpty())) {
                segment.groundCells = new ArrayList<>();
                segment.groundCells.add(new AstData.GroundCell(
                        MathHelper.floor(segment.aabb.minX), segment.aabb.minY, MathHelper.floor(segment.aabb.minZ)));
            }
        }
        if (request.has("height")) {
            double height = request.get("height").getAsDouble();
            if (!Double.isFinite(height) || height <= 0.0 || height > 256.0) {
                throw new IllegalArgumentException("高さは0より大きく256以下で入力してください");
            }
            segment.height = height;
            if (segment.aabb != null) segment.aabb.maxY = segment.aabb.minY + height;
        }
        saveEditedCourse();
    }

    private static void deletePoint(int index) {
        AstData.CourseFile course = requireCourse();
        List<AstData.Segment> ordered = ordered(course);
        int position = -1;
        for (int i = 0; i < ordered.size(); i++) if (ordered.get(i).index == index) position = i;
        if (position <= 0 || position >= ordered.size() - 1) {
            throw new IllegalArgumentException("STARTとGOALは削除できません");
        }
        ordered.remove(position);
        reindex(course, ordered);
        clearTimingRecords(course);
        saveEditedCourse();
    }

    private static void movePoint(int index, int direction) {
        if (direction != -1 && direction != 1) throw new IllegalArgumentException("移動方向が不正です");
        AstData.CourseFile course = requireCourse();
        List<AstData.Segment> ordered = ordered(course);
        int position = -1;
        for (int i = 0; i < ordered.size(); i++) if (ordered.get(i).index == index) position = i;
        int target = position + direction;
        if (position <= 0 || position >= ordered.size() - 1 || target <= 0 || target >= ordered.size() - 1) {
            throw new IllegalArgumentException("この地点は移動できません");
        }
        Collections.swap(ordered, position, target);
        reindex(course, ordered);
        clearTimingRecords(course);
        saveEditedCourse();
    }

    private static void deleteCourse(String name) {
        AstCourseManager manager = AstCourseManager.get();
        if (!manager.courseExists(name)) throw new IllegalArgumentException("コースが見つかりません");
        manager.deleteCourse(name);
        AstRuntime.get().forceResetToIdle();
    }

    private static void renameCourse(String oldName, String rawNewName) {
        String newName = cleanName(rawNewName, "コース名");
        AstCourseManager manager = AstCourseManager.get();
        if (!oldName.equals(newName) && manager.courseExists(newName)) {
            throw new IllegalArgumentException("同じ名前のコースがあります");
        }
        try {
            manager.renameCourse(oldName, newName);
        } catch (IOException e) {
            throw new IllegalStateException("コース名を変更できませんでした");
        }
        AstRuntime.get().forceResetToIdle();
    }

    private static void resetRecords(JsonObject request) {
        AstData.CourseFile course = requireCourse();
        if (course.stats == null) course.stats = new AstData.Stats();
        String target = string(request, "target", "all");
        switch (target) {
            case "pb":
                course.stats.pb = new AstData.PbRecord();
                break;
            case "bestSegments":
                course.stats.bestSegmentsTicks.clear();
                break;
            case "bestSplits":
                course.stats.bestSplitTicks.clear();
                break;
            case "all":
                clearTimingRecords(course);
                break;
            case "selected":
                if (request.has("pb") && request.get("pb").getAsBoolean()) course.stats.pb = new AstData.PbRecord();
                if (request.has("bestSegments") && request.get("bestSegments").getAsBoolean()) course.stats.bestSegmentsTicks.clear();
                if (request.has("bestSplits") && request.get("bestSplits").getAsBoolean()) course.stats.bestSplitTicks.clear();
                break;
            default:
                throw new IllegalArgumentException("リセット対象が不正です");
        }
        saveEditedCourse();
    }

    private static void clearTimingRecords(AstData.CourseFile course) {
        if (course.stats == null) course.stats = new AstData.Stats();
        course.stats.pb = new AstData.PbRecord();
        course.stats.bestSegmentsTicks.clear();
        course.stats.bestSplitTicks.clear();
    }

    private static boolean hasValue(List<Integer> values) {
        if (values == null) return false;
        for (Integer value : values) if (value != null) return true;
        return false;
    }

    private static void updateHud(JsonObject request) {
        AstCourseManager manager = AstCourseManager.get();
        AstData.HudConfig hud = manager.getHudConfig();

        if (request.has("preset")) {
            AstHudConfigUtil.applyPreset(hud, request.get("preset").getAsString(), false);
        }
        if (request.has("theme")) {
            hud.theme = request.get("theme").getAsString();
            AstHudConfigUtil.applyTheme(hud);
        }
        if (request.has("scale")) hud.scale = clamp(request.get("scale").getAsDouble(), 0.25, 3.00);
        if (request.has("timeFormat")) hud.timeFormat = request.get("timeFormat").getAsString();
        if (request.has("deltaTimeFormat")) hud.deltaTimeFormat = request.get("deltaTimeFormat").getAsString();
        if (request.has("comparison")) hud.comparison = request.get("comparison").getAsString();
        if (request.has("unit")) hud.unit = request.get("unit").getAsString();
        if (request.has("splitListCount")) hud.splitListCount = clamp(request.get("splitListCount").getAsInt(), 2, 20);
        if (request.has("splitListGap")) hud.splitListGap = clamp(request.get("splitListGap").getAsInt(), 0, 80);
        if (request.has("splitListLineGap")) hud.splitListLineGap = clamp(request.get("splitListLineGap").getAsInt(), 0, 8);
        if (request.has("splitColumns")) hud.splitColumns = clamp(request.get("splitColumns").getAsInt(), 1, 2);
        if (request.has("splitDeltaOffset")) hud.splitDeltaOffset = clamp(request.get("splitDeltaOffset").getAsInt(), -80, 0);
        if (request.has("splitListWidth")) hud.splitListWidth = clamp(request.get("splitListWidth").getAsInt(), 160, 420);
        if (request.has("anchor")) hud.anchor = request.get("anchor").getAsString();
        if (request.has("offsetX")) hud.offsetX = clamp(request.get("offsetX").getAsInt(), -2000, 2000);
        if (request.has("offsetY")) hud.offsetY = clamp(request.get("offsetY").getAsInt(), -2000, 2000);
        if (request.has("backgroundRgb")) hud.backgroundRgb = request.get("backgroundRgb").getAsString();
        if (request.has("backgroundOpacity")) hud.backgroundOpacity = clamp(request.get("backgroundOpacity").getAsInt(), 0, 100);
        if (request.has("labelRgb")) hud.labelRgb = request.get("labelRgb").getAsString();
        if (request.has("mainRgb")) hud.mainRgb = request.get("mainRgb").getAsString();
        if (request.has("subRgb")) hud.subRgb = request.get("subRgb").getAsString();

        if (request.has("toggleKey")) {
            String key = request.get("toggleKey").getAsString();
            if (!isKnownHudItem(key)) throw new IllegalArgumentException("表示項目が見つかりません");
            hud.toggles.put(key, request.get("toggleValue").getAsBoolean());
        }
        if (request.has("itemOrder") && request.get("itemOrder").isJsonArray()) {
            List<String> order = new ArrayList<>();
            for (com.google.gson.JsonElement element : request.getAsJsonArray("itemOrder")) {
                String key = element.getAsString();
                if (isKnownHudItem(key) && !order.contains(key)) order.add(key);
            }
            hud.itemOrder = order;
        }

        AstHudConfigUtil.normalizeHud(hud);
        manager.saveHudConfig(hud);
    }

    private static boolean isKnownHudItem(String key) {
        for (String known : AstHudConfigUtil.knownItems()) if (known.equals(key)) return true;
        return false;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static AstData.Segment newSegment(int index, String name, String detection) {
        AstData.Segment segment = new AstData.Segment();
        segment.index = index;
        segment.name = name;
        segment.height = 1.0;
        segment.detection = detection;
        segment.placed = Boolean.TRUE;
        segment.aabb = new AstData.AabbDto(0, 0, 0, 0, 1, 0);
        segment.groundCells = new ArrayList<>();
        return segment;
    }

    private static void placeAtPlayer(AstData.Segment segment, EntityPlayerSP player) {
        int x = MathHelper.floor(player.posX);
        int z = MathHelper.floor(player.posZ);
        double y = player.posY;
        segment.height = 1.0;
        segment.placed = Boolean.TRUE;
        segment.aabb = new AstData.AabbDto(x, y, z, x + 1, y + 1.0, z + 1);
        segment.groundCells = new ArrayList<>();
        segment.groundCells.add(new AstData.GroundCell(x, y, z));
    }

    private static void reindex(AstData.CourseFile course, List<AstData.Segment> ordered) {
        for (int index = 0; index < ordered.size(); index++) ordered.get(index).index = index;
        course.segments = ordered;
    }

    private static void saveEditedCourse() {
        AstCourseManager.get().saveActiveCourseSafe();
        AstRuntime.get().forceResetToIdle();
    }

    private static AstData.CourseFile requireCourse() {
        AstData.CourseFile course = AstCourseManager.get().getActiveCourse();
        if (course == null) throw new IllegalStateException("コースが選択されていません");
        return course;
    }

    private static AstData.Segment requireSegment(int index) {
        AstData.Segment segment = AstUtil.findSegment(requireCourse(), index);
        if (segment == null) throw new IllegalArgumentException("地点が見つかりません");
        return segment;
    }

    private static EntityPlayerSP requirePlayer() {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player == null) throw new IllegalStateException("ワールドに入ってから操作してください");
        return player;
    }

    private static List<AstData.Segment> ordered(AstData.CourseFile course) {
        List<AstData.Segment> result = new ArrayList<>();
        if (course.segments != null) {
            for (AstData.Segment segment : course.segments) if (segment != null) result.add(segment);
        }
        result.sort(Comparator.comparingInt(segment -> segment.index));
        return result;
    }

    private static int estimateAabbBlocks(AstData.Segment segment) {
        if (segment.aabb == null || !Boolean.TRUE.equals(segment.placed)) return 0;
        int x = Math.max(1, (int) Math.ceil(segment.aabb.maxX - segment.aabb.minX));
        int z = Math.max(1, (int) Math.ceil(segment.aabb.maxZ - segment.aabb.minZ));
        return x * z;
    }

    private static String cleanName(String value, String label) {
        String cleaned = value == null ? "" : value.trim();
        if (cleaned.isEmpty()) throw new IllegalArgumentException(label + "を入力してください");
        if (cleaned.length() > 80) throw new IllegalArgumentException(label + "は80文字以内にしてください");
        return cleaned;
    }

    private static String cleanPointName(String value) {
        String cleaned = value == null ? "" : value.trim();
        if (cleaned.isEmpty()) throw new IllegalArgumentException("地点名を入力してください");
        if (cleaned.length() > 80) throw new IllegalArgumentException("地点名は80文字以内にしてください");
        return cleaned;
    }

    private static String requiredString(JsonObject request, String key) {
        if (!request.has(key) || request.get(key).isJsonNull()) throw new IllegalArgumentException(key + " is required");
        return request.get(key).getAsString();
    }

    private static int requiredInt(JsonObject request, String key) {
        if (!request.has(key) || request.get(key).isJsonNull()) throw new IllegalArgumentException(key + " is required");
        return request.get(key).getAsInt();
    }

    private static String string(JsonObject request, String key, String fallback) {
        return request.has(key) && !request.get(key).isJsonNull() ? request.get(key).getAsString() : fallback;
    }
}
