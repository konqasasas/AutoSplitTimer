package com.konqasasas.ast.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.konqasasas.ast.AutoSplitTimerMod;
import com.konqasasas.ast.hud.AstHudConfigUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.util.text.TextComponentString;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Loads/saves course JSON files and stores the currently selected course.
 *
 * Global by courseName (world-independent).
 */
public final class AstCourseManager {
    private static final AstCourseManager INSTANCE = new AstCourseManager();

    public static AstCourseManager get() {
        return INSTANCE;
    }

    private final Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    private final Map<String, AstData.CourseFile> cache = new HashMap<>();
    private String activeCourseName = null;
    // Global HUD config shared across courses (prevents resets on course switching).
    private AstData.HudConfig globalHud = null;

    private AstCourseManager() {}

    private File baseConfigDir() {
        return new File(net.minecraftforge.fml.common.Loader.instance().getConfigDir(), "autosplittimer");
    }

    private File globalHudFile() {
        return new File(baseConfigDir(), "hud.json");
    }

    /** Load global HUD from disk once; if missing, create from InitialDefaults. */
    private synchronized void ensureGlobalHudLoaded() {
        if (globalHud != null) return;
        AstData.HudConfig loaded = loadGlobalHudSafe();
        if (loaded == null) {
            loaded = AstHudConfigUtil.newInitialHudConfig();
        }
        AstHudConfigUtil.normalizeHud(loaded);
        globalHud = AstHudConfigUtil.copyHud(loaded);
        // persist once so first boot is deterministic
        saveGlobalHudSafe(globalHud);
    }

    public synchronized AstData.HudConfig loadGlobalHudSafe() {
        try {
            File f = globalHudFile();
            if (!f.exists()) return null;
            try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
                AstData.HudConfig hud = gson.fromJson(r, AstData.HudConfig.class);
                if (hud == null) return null;
                AstHudConfigUtil.normalizeHud(hud);
                return hud;
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    public synchronized void saveGlobalHudSafe(AstData.HudConfig hud) {
        if (hud == null) return;
        File dir = baseConfigDir();
        if (!dir.exists()) dir.mkdirs();
        File f = globalHudFile();
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
            gson.toJson(hud, w);
        } catch (Exception ignored) {}
    }

    public synchronized String getActiveCourseName() {
        return activeCourseName;
    }

    public synchronized AstData.CourseFile getActiveCourse() {
        if (activeCourseName == null) return null;
        return cache.get(activeCourseName);
    }

    /** Clear the active course selection ("leave course"). */
    public synchronized void clearActiveCourse() {
        activeCourseName = null;
    }

    /** True if a course JSON exists on disk. */
    public synchronized boolean courseExists(String courseName) {
        if (courseName == null) return false;
        File f = courseFile(courseName.trim());
        return f.exists();
    }

    /** Load an existing course. Unlike setActiveCourse(), this does not create a new file. */
    public synchronized boolean loadExistingCourseAsActive(String courseName) {
        if (courseName == null || courseName.trim().isEmpty()) return false;
        courseName = courseName.trim();
        File f = courseFile(courseName);
        if (!f.exists()) return false;
        ensureGlobalHudLoaded();
        AstData.CourseFile cf = loadCourseSafe(courseName);
        if (cf == null) return false;
        // Always use global HUD config (prevents reset on course change)
        cf.hud = AstHudConfigUtil.copyHud(globalHud);
        AstHudConfigUtil.normalizeHud(cf.hud);
        cache.put(courseName, cf);
        activeCourseName = courseName;
        return true;
    }

    public synchronized void setActiveCourse(String courseName) {
        if (courseName == null || courseName.trim().isEmpty()) {
            activeCourseName = null;
            return;
        }
        courseName = courseName.trim();
        ensureGlobalHudLoaded();
        AstData.CourseFile cf = loadCourseSafe(courseName); // creates if missing
        if (cf != null) {
            cf.hud = AstHudConfigUtil.copyHud(globalHud);
            AstHudConfigUtil.normalizeHud(cf.hud);

            // Override per-course HUD with global HUD to avoid resets when switching courses.
            ensureGlobalHudLoaded();
            cf.hud = AstHudConfigUtil.copyHud(globalHud);
            AstHudConfigUtil.normalizeHud(cf.hud);
            cache.put(courseName, cf);
            activeCourseName = courseName;
        }
    }

    /** Load an existing course (does NOT create a new file). Returns null if missing or invalid. */
    public synchronized AstData.CourseFile loadExistingCourseSafe(String courseName) {
        if (courseName == null || courseName.trim().isEmpty()) return null;
        courseName = courseName.trim();
        File f = courseFile(courseName);
        if (!f.exists()) return null;
        return loadCourseSafe(courseName);
    }

    public synchronized List<String> listCourseNames() {
        File dir = coursesDir();
        File[] files = dir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".json"));
        List<String> out = new ArrayList<>();
        if (files != null) {
            for (File f : files) {
                String n = f.getName();
                if (n.toLowerCase(Locale.ROOT).endsWith(".json")) {
                    out.add(n.substring(0, n.length() - 5));
                }
            }
        }
        Collections.sort(out);
        return out;
    }

    public synchronized void deleteCourse(String courseName) {
        if (courseName == null) return;
        cache.remove(courseName);
        File f = courseFile(courseName);
        if (f.exists()) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
        if (courseName.equals(activeCourseName)) {
            activeCourseName = null;
        }
    }

    public synchronized AstData.CourseFile loadCourseSafe(String courseName) {
        try {
            AstData.CourseFile cf = loadCourse(courseName);
            cache.put(courseName, cf);
            return cf;
        } catch (Exception ignored) {
            return null;
        }
    }

    public void loadAllCoursesSafe() {
        try {
            for (String n : listCourseNames()) {
                loadCourseSafe(n);
            }
        } catch (Exception ignored) {
        }
    }

    private AstData.CourseFile loadCourse(String courseName) throws IOException {
        File f = courseFile(courseName);
        if (!f.exists()) {
            ensureGlobalHudLoaded();
            AstData.CourseFile cf = new AstData.CourseFile();
            cf.courseName = courseName;
            cf.hud = AstHudConfigUtil.copyHud(globalHud);
            AstHudConfigUtil.normalizeHud(cf.hud);
            // reasonable defaults: empty segments
            saveCourse(courseName, cf);
            return cf;
        }
        try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            AstData.CourseFile cf = gson.fromJson(r, AstData.CourseFile.class);
            if (cf == null) throw new IOException("Empty JSON");
            if (cf.courseName == null || cf.courseName.trim().isEmpty()) {
                cf.courseName = courseName;
            }
            // migrate/sanitize
            if (cf.hud == null) cf.hud = new AstData.HudConfig();
            if (cf.hud.toggles == null) cf.hud.toggles = new HashMap<>();
            // PB HUD item removed; keep pb record but hide toggle if old configs had it
            cf.hud.toggles.remove("pb");
            if (cf.hud.itemOrder == null) cf.hud.itemOrder = new ArrayList<>();
            if (cf.hud.itemOrder.isEmpty()) {
                // populate defaults (PB item intentionally omitted)
                cf.hud.itemOrder.add("courseName");
                cf.hud.itemOrder.add("time");
                cf.hud.itemOrder.add("segment");
                cf.hud.itemOrder.add("segmentTime");
                cf.hud.itemOrder.add("prevSeg");
                cf.hud.itemOrder.add("sob");
                cf.hud.itemOrder.add("bpt");
                cf.hud.itemOrder.add("bestSeg");
                cf.hud.itemOrder.add("bestSplit");
                cf.hud.itemOrder.add("attempt");
                cf.hud.itemOrder.add("splitList");
            }
            if (cf.hud.splitListWidth <= 0) cf.hud.splitListWidth = 140;
            if (cf.hud.splitListGap < 0) cf.hud.splitListGap = 6;
            // IMPORTANT: canonicalize HUD fields (including splitColsSecondary) on load.
            // This prevents accidental Minecraft formatting codes or invalid strings from
            // collapsing split columns to "none".
            AstHudConfigUtil.normalizeHud(cf.hud);
            if (cf.stats == null) cf.stats = new AstData.Stats();
            if (cf.segments == null) cf.segments = new ArrayList<>();
            normalizeSegments(cf);
            normalizeStatsArrays(cf);
            // overwrite with global HUD (shared across courses)
            ensureGlobalHudLoaded();
            cf.hud = AstHudConfigUtil.copyHud(globalHud);
            AstHudConfigUtil.normalizeHud(cf.hud);
            return cf;
        } catch (JsonSyntaxException jse) {
            throw new IOException("Invalid JSON: " + jse.getMessage(), jse);
        }
    }

    public synchronized void saveActiveCourseSafe() {
        AstData.CourseFile cf = getActiveCourse();
        if (cf == null) return;
        try {
            ensureGlobalHudLoaded();
            globalHud = AstHudConfigUtil.copyHud(cf.hud);
            saveGlobalHudSafe(globalHud);
            saveCourse(activeCourseName, cf);
        } catch (Exception ignored) {
        }
    }

    public synchronized void saveCourseSafe(String courseName) {
        AstData.CourseFile cf = cache.get(courseName);
        if (cf == null) return;
        try {
            saveCourse(courseName, cf);
        } catch (Exception ignored) {
        }
    }

    private void saveCourse(String courseName, AstData.CourseFile cf) throws IOException {
        File f = courseFile(courseName);
        File dir = f.getParentFile();
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        cf.version = AstData.DATA_VERSION;
        cf.courseName = courseName;
        normalizeSegments(cf);
        normalizeStatsArrays(cf);
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
            gson.toJson(cf, w);
        }
    }

    private File coursesDir() {
        File base = new File(Minecraft.getMinecraft().mcDataDir, "config" + File.separator + AutoSplitTimerMod.MODID);
        File courses = new File(base, "courses");
        if (!courses.exists()) {
            //noinspection ResultOfMethodCallIgnored
            courses.mkdirs();
        }
        return courses;
    }

    private File courseFile(String courseName) {
        String safe = courseName.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
        return new File(coursesDir(), safe + ".json");
    }

    public static void chat(String msg) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;
        mc.player.sendMessage(new TextComponentString("\u00a73[AST]\u00a7r " + msg));
    }

    private static void normalizeSegments(AstData.CourseFile cf) {
        // remove nulls
        cf.segments.removeIf(Objects::isNull);
        // ensure indices unique: keep last occurrence
        Map<Integer, AstData.Segment> byIndex = new HashMap<>();
        for (AstData.Segment s : cf.segments) {
            if (s.name == null) s.name = "";
            if (s.aabb == null) {
                // placeholder; will be fixed by commands
                s.aabb = new AstData.AabbDto(0, 0, 0, 0, 0, 0);
            }
            // keep a consistent height value; allow fractional
            if (s.height <= 0) s.height = Math.max(1e-5, s.aabb.maxY - s.aabb.minY);

            // Ensure AABB maxY matches minY + height (important after migration int->double).
            // If AABB seems more trustworthy (height was missing), height above was derived from AABB.
            s.aabb.maxY = s.aabb.minY + s.height;
            byIndex.put(s.index, s);
        }
        List<Integer> indices = new ArrayList<>(byIndex.keySet());
        Collections.sort(indices);
        List<AstData.Segment> out = new ArrayList<>();
        for (int idx : indices) out.add(byIndex.get(idx));
        cf.segments = out;
    }

    private static void normalizeStatsArrays(AstData.CourseFile cf) {
        int n = countTrackableSegments(cf);
        ensureSize(cf.stats.bestSegmentsTicks, n);
        ensureSize(cf.stats.bestSplitTicks, n);
        if (cf.stats.pb == null) cf.stats.pb = new AstData.PbRecord();
        ensureSize(cf.stats.pb.segmentTicks, n);
    }

    /** Number of trackable segments excluding start (index 0). */
    private static int countTrackableSegments(AstData.CourseFile cf) {
        int count = 0;
        for (AstData.Segment s : cf.segments) {
            if (s.index != 0) count++;
        }
        return count;
    }

    private static <T> void ensureSize(List<T> list, int n) {
        while (list.size() < n) list.add(null);
        while (list.size() > n) list.remove(list.size() - 1);
    }
}
