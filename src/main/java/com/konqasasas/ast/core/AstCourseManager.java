package com.konqasasas.ast.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.konqasasas.ast.AutoSplitTimerMod;
import com.konqasasas.ast.hud.AstHudConfigUtil;
import com.konqasasas.ast.ui.AstNativeHudRenderer;
import com.konqasasas.ast.viz.AstVizRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.text.TextComponentString;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * Loads/saves course JSON files and stores the currently selected course.
 *
 * Global by courseName (world-independent).
 */
public final class AstCourseManager {
    private static final AstCourseManager INSTANCE = new AstCourseManager();
    private static final Logger LOGGER = LogManager.getLogger(AutoSplitTimerMod.MODID);

    public static AstCourseManager get() {
        return INSTANCE;
    }

    private final Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    private final Map<String, AstData.CourseFile> cache = new HashMap<>();
    private String activeCourseName = null;
    private AstData.CourseFile temporaryCourse = null;
    private String courseBeforeTemporary = null;
    // Global HUD config shared across courses (prevents resets on course switching).
    private AstData.HudConfig globalHud = null;
    private final Deque<String> pendingWarnings = new ArrayDeque<>();

    private AstCourseManager() {}

    private File baseConfigDir() {
        return new File(net.minecraftforge.fml.common.Loader.instance().getConfigDir(), "autosplittimer");
    }

    private File globalHudFile() {
        return new File(baseConfigDir(), "hud.json");
    }

    /** Load global HUD once, migrating the last per-course HUD format when needed. */
    private synchronized void ensureGlobalHudLoaded() {
        if (globalHud != null) return;
        AstData.HudConfig loaded = loadGlobalHudSafe();
        if (loaded == null) {
            loaded = findLegacyCourseHudSafe();
        }
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
                if (hud == null) throw new IOException("HUD settings are empty");
                AstHudConfigUtil.normalizeHud(hud);
                return hud;
            }
        } catch (Exception exception) {
            backupBrokenFile(globalHudFile());
            warn("HUD設定を読み込めなかったため、初期設定を使用します", exception);
            return null;
        }
    }

    public synchronized void saveGlobalHudSafe(AstData.HudConfig hud) {
        if (hud == null) return;
        File dir = baseConfigDir();
        if (!dir.exists()) dir.mkdirs();
        File f = globalHudFile();
        try {
            writeJsonAtomically(f, hud);
        } catch (Exception exception) {
            warn("HUD設定を保存できませんでした", exception);
        }
    }

    /** Returns one startup/storage warning for display in the editor. */
    public synchronized String pollWarning() {
        return pendingWarnings.pollFirst();
    }

    public synchronized String getActiveCourseName() {
        return temporaryCourse == null ? activeCourseName : temporaryCourse.courseName;
    }

    public synchronized AstData.CourseFile getActiveCourse() {
        if (temporaryCourse != null) return temporaryCourse;
        if (activeCourseName == null) return null;
        return cache.get(activeCourseName);
    }

    public synchronized boolean isTemporaryCourseActive() {
        return temporaryCourse != null;
    }

    /** Activates an in-memory course without creating or changing any course file. */
    public synchronized void activateTemporaryCourse(AstData.CourseFile course) {
        if (course == null) return;
        if (temporaryCourse == null) courseBeforeTemporary = activeCourseName;
        temporaryCourse = course;
    }

    /** Removes the in-memory course and restores the course that was active before it. */
    public synchronized void clearTemporaryCourse() {
        temporaryCourse = null;
        activeCourseName = courseBeforeTemporary != null && cache.containsKey(courseBeforeTemporary)
                ? courseBeforeTemporary : null;
        courseBeforeTemporary = null;
        AstVizRenderer.invalidateGeometry();
    }

    /** Global HUD is editable even when no course is selected. */
    public synchronized AstData.HudConfig getHudConfig() {
        ensureGlobalHudLoaded();
        AstData.CourseFile active = getActiveCourse();
        if (active != null) {
            if (active.hud == null) active.hud = AstHudConfigUtil.copyHud(globalHud);
            return active.hud;
        }
        return globalHud;
    }

    /** Persist HUD globally and synchronize every loaded course with it. */
    public synchronized void saveHudConfig(AstData.HudConfig hud) {
        if (hud == null) return;
        AstHudConfigUtil.normalizeHud(hud);
        AstNativeHudRenderer.invalidateModelCache();
        globalHud = AstHudConfigUtil.copyHud(hud);
        saveGlobalHudSafe(globalHud);
        for (AstData.CourseFile course : cache.values()) {
            if (course != null) course.hud = AstHudConfigUtil.copyHud(globalHud);
        }
        if (activeCourseName != null && cache.containsKey(activeCourseName)) {
            try {
                saveCourse(activeCourseName, cache.get(activeCourseName));
            } catch (Exception exception) {
                warn("コースのHUD設定を保存できませんでした", exception);
            }
        }
    }

    /** Clear the active course selection ("leave course"). */
    public synchronized void clearActiveCourse() {
        temporaryCourse = null;
        courseBeforeTemporary = null;
        activeCourseName = null;
        AstVizRenderer.invalidateGeometry();
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
        temporaryCourse = null;
        courseBeforeTemporary = null;
        activeCourseName = courseName;
        return true;
    }

    public synchronized void setActiveCourse(String courseName) {
        temporaryCourse = null;
        courseBeforeTemporary = null;
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
                    String stored = n.substring(0, n.length() - 5);
                    if (stored.startsWith("u_")) {
                        try {
                            stored = new String(Base64.getUrlDecoder().decode(stored.substring(2)), StandardCharsets.UTF_8);
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                    out.add(stored);
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
            if (!f.delete()) warn("コースファイルを削除できませんでした: " + courseName, null);
        }
        if (courseName.equals(activeCourseName)) {
            activeCourseName = null;
        }
    }

    /** Rename a course while preserving its contents and records. */
    public synchronized void renameCourse(String oldName, String newName) throws IOException {
        if (oldName == null || newName == null) throw new IllegalArgumentException("Course name is required");
        oldName = oldName.trim();
        newName = newName.trim();
        if (oldName.isEmpty() || newName.isEmpty()) throw new IllegalArgumentException("Course name is required");
        if (oldName.equals(newName)) return;

        File oldFile = courseFile(oldName);
        File newFile = courseFile(newName);
        if (!oldFile.exists()) throw new FileNotFoundException("Course not found: " + oldName);
        if (newFile.exists()) throw new IOException("Course already exists: " + newName);

        AstData.CourseFile course = cache.get(oldName);
        if (course == null) course = loadCourse(oldName);

        // Write the complete new file first. The old file is removed only after that succeeds.
        saveCourse(newName, course);
        try {
            Files.delete(oldFile.toPath());
        } catch (IOException deleteFailure) {
            Files.deleteIfExists(newFile.toPath());
            course.courseName = oldName;
            throw deleteFailure;
        }

        cache.remove(oldName);
        cache.put(newName, course);
        if (oldName.equals(activeCourseName)) activeCourseName = newName;
        if (oldName.equals(courseBeforeTemporary)) courseBeforeTemporary = newName;
    }

    /** Add a validated course received from a share file and make it active. */
    public synchronized void importCourse(String courseName, AstData.CourseFile course) throws IOException {
        if (courseName == null || course == null) throw new IllegalArgumentException("Course is required");
        courseName = courseName.trim();
        if (courseName.isEmpty()) throw new IllegalArgumentException("Course name is required");
        if (courseExists(courseName)) throw new IOException("同名のコースがすでにあります");

        ensureGlobalHudLoaded();
        course.courseName = courseName;
        course.hud = AstHudConfigUtil.copyHud(globalHud);
        normalizeSegments(course);
        if (course.stats == null) course.stats = new AstData.Stats();
        normalizeStatsArrays(course);

        // saveCourse writes the complete file before the in-memory selection changes.
        saveCourse(courseName, course);
        cache.put(courseName, course);
        temporaryCourse = null;
        courseBeforeTemporary = null;
        activeCourseName = courseName;
    }

    /** Produce a collision-free import name without overwriting an existing course. */
    public synchronized String availableCourseName(String requested) {
        String base = requested == null ? "読み込んだコース" : requested.trim();
        if (base.isEmpty()) base = "読み込んだコース";
        if (!courseExists(base)) return base;
        for (int suffix = 2; suffix < 10000; suffix++) {
            String candidate = base + " (" + suffix + ")";
            if (!courseExists(candidate)) return candidate;
        }
        return base + " (" + System.currentTimeMillis() + ")";
    }

    public synchronized AstData.CourseFile loadCourseSafe(String courseName) {
        try {
            AstData.CourseFile cf = loadCourse(courseName);
            cache.put(courseName, cf);
            return cf;
        } catch (Exception exception) {
            warn("コースを読み込めませんでした: " + courseName, exception);
            return null;
        }
    }

    public void loadAllCoursesSafe() {
        try {
            for (String n : listCourseNames()) {
                loadCourseSafe(n);
            }
        } catch (Exception exception) {
            warn("コース一覧を読み込めませんでした", exception);
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
            if (cf.version > AstData.DATA_VERSION) {
                throw new IOException("This course was created by a newer AutoSplit Timer version");
            }
            int loadedVersion = cf.version;
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
            if (globalHud == null && !globalHudFile().exists()) {
                globalHud = AstHudConfigUtil.copyHud(cf.hud);
                saveGlobalHudSafe(globalHud);
            }
            if (cf.stats == null) cf.stats = new AstData.Stats();
            if (cf.segments == null) cf.segments = new ArrayList<>();
            normalizeSegments(cf);
            normalizeStatsArrays(cf);
            // overwrite with global HUD (shared across courses)
            ensureGlobalHudLoaded();
            cf.hud = AstHudConfigUtil.copyHud(globalHud);
            AstHudConfigUtil.normalizeHud(cf.hud);
            if (loadedVersion < AstData.DATA_VERSION) saveCourse(courseName, cf);
            return cf;
        } catch (JsonSyntaxException jse) {
            throw new IOException("Invalid JSON: " + jse.getMessage(), jse);
        }
    }

    public synchronized void saveActiveCourseSafe() {
        if (temporaryCourse != null) return;
        AstData.CourseFile cf = getActiveCourse();
        if (cf == null) return;
        AstVizRenderer.invalidateGeometry();
        try {
            ensureGlobalHudLoaded();
            globalHud = AstHudConfigUtil.copyHud(cf.hud);
            saveGlobalHudSafe(globalHud);
            saveCourse(activeCourseName, cf);
        } catch (Exception exception) {
            warn("コースを保存できませんでした: " + activeCourseName, exception);
        }
    }

    public synchronized void saveCourseSafe(String courseName) {
        AstData.CourseFile cf = cache.get(courseName);
        if (cf == null) return;
        try {
            saveCourse(courseName, cf);
        } catch (Exception exception) {
            warn("コースを保存できませんでした: " + courseName, exception);
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
        writeJsonAtomically(f, cf);
    }

    private File coursesDir() {
        File courses = new File(baseConfigDir(), "courses");
        if (!courses.exists()) {
            //noinspection ResultOfMethodCallIgnored
            courses.mkdirs();
        }
        return courses;
    }

    private File courseFile(String courseName) {
        String trimmed = courseName.trim();
        String safe;
        if (trimmed.matches("[a-zA-Z0-9][a-zA-Z0-9._-]*")) {
            safe = trimmed;
        } else {
            safe = "u_" + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(trimmed.getBytes(StandardCharsets.UTF_8));
        }
        return new File(coursesDir(), safe + ".json");
    }

    public static void chat(String msg) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;
        mc.player.sendMessage(new TextComponentString("\u00a73[AST]\u00a7r " + msg));
    }

    private AstData.HudConfig findLegacyCourseHudSafe() {
        File[] files = coursesDir().listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".json"));
        if (files == null) return null;
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File file : files) {
            try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                AstData.CourseFile course = gson.fromJson(reader, AstData.CourseFile.class);
                if (course != null && course.hud != null) {
                    AstHudConfigUtil.normalizeHud(course.hud);
                    return course.hud;
                }
            } catch (Exception ignored) {
                // The normal course loader reports the damaged file with its course name.
            }
        }
        return null;
    }

    private void writeJsonAtomically(File target, Object value) throws IOException {
        File parent = target.getAbsoluteFile().getParentFile();
        if (parent == null || (!parent.exists() && !parent.mkdirs())) {
            throw new IOException("Could not create config directory");
        }
        File temporary = File.createTempFile(".ast-", ".tmp", parent);
        try {
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(temporary), StandardCharsets.UTF_8)) {
                gson.toJson(value, writer);
            }
            try {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException unsupportedAtomicMove) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary.toPath());
        }
    }

    private void backupBrokenFile(File file) {
        if (file == null || !file.isFile()) return;
        File backup = new File(file.getParentFile(), file.getName() + ".broken-" + System.currentTimeMillis());
        try {
            Files.move(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            LOGGER.warn("Could not preserve invalid config file {}", file, exception);
        }
    }

    private synchronized void warn(String message, Throwable error) {
        if (pendingWarnings.size() >= 8) pendingWarnings.removeFirst();
        if (!pendingWarnings.contains(message)) pendingWarnings.addLast(message);
        if (error == null) LOGGER.warn(message);
        else LOGGER.warn(message, error);
    }

    private static void normalizeSegments(AstData.CourseFile cf) {
        // remove nulls
        cf.segments.removeIf(Objects::isNull);
        // ensure indices unique: keep last occurrence
        Map<Integer, AstData.Segment> byIndex = new HashMap<>();
        for (AstData.Segment s : cf.segments) {
            if (s.name == null) s.name = "";
            if (s.placed == null) s.placed = Boolean.TRUE;
            if (s.detection == null || (!"ground".equals(s.detection) && !"aabb".equals(s.detection))) {
                s.detection = "aabb";
            }
            if (s.groundCells == null) s.groundCells = new ArrayList<>();
            s.groundCells.removeIf(Objects::isNull);
            if (s.aabb == null) {
                // placeholder; will be fixed by commands
                s.aabb = new AstData.AabbDto(0, 0, 0, 0, 0, 0);
            }
            // keep a consistent height value; allow fractional
            if (s.height <= 0) s.height = Math.max(1e-5, s.aabb.maxY - s.aabb.minY);

            // Ensure AABB maxY matches minY + height (important after migration int->double).
            // If AABB seems more trustworthy (height was missing), height above was derived from AABB.
            s.aabb.maxY = s.aabb.minY + s.height;
            if (Boolean.TRUE.equals(s.placed) && "ground".equals(s.detection) && s.groundCells.isEmpty()) {
                s.groundCells.add(new AstData.GroundCell(
                        (int) Math.floor(s.aabb.minX), s.aabb.minY, (int) Math.floor(s.aabb.minZ)));
            }
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
