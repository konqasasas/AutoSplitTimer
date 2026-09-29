package com.konqasasas.ast.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.konqasasas.ast.core.AstCourseManager;
import com.konqasasas.ast.core.AstData;
import net.minecraft.client.Minecraft;

import java.awt.FileDialog;
import java.awt.Frame;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Versioned .astcourse import/export with native OS file dialogs. */
public final class AstCourseShareService {
    private static final String FORMAT = "autosplittimer-course";
    private static final int FORMAT_VERSION = 1;
    private static final long MAX_FILE_BYTES = 2L * 1024L * 1024L;
    private static final int MAX_SEGMENTS = 512;
    private static final int MAX_GROUND_CELLS = 65536;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    private static volatile File lastDirectory;

    private AstCourseShareService() {}

    public static final class ImportPreview {
        private final File source;
        private final AstData.CourseFile course;
        private final boolean includesRecords;
        private final int groundCount;
        private final int aabbCount;

        private ImportPreview(File source, AstData.CourseFile course, boolean includesRecords,
                              int groundCount, int aabbCount) {
            this.source = source;
            this.course = course;
            this.includesRecords = includesRecords;
            this.groundCount = groundCount;
            this.aabbCount = aabbCount;
        }

        public String getCourseName() { return course.courseName; }
        public int getPointCount() { return course.segments == null ? 0 : course.segments.size(); }
        public int getGroundCount() { return groundCount; }
        public int getAabbCount() { return aabbCount; }
        public boolean includesRecords() { return includesRecords; }
        public String getFileName() { return source.getName(); }
    }

    private static final class SharedCourse {
        String format = FORMAT;
        int formatVersion = FORMAT_VERSION;
        String courseName;
        List<AstData.Segment> segments = new ArrayList<>();
        boolean includesRecords;
        AstData.Stats records;
    }

    public static void chooseExport(AstData.CourseFile course, boolean includeRecords,
                                    Consumer<File> success, Consumer<String> failure, Runnable cancelled) {
        if (course == null) {
            failure.accept("共有するコースがありません");
            return;
        }
        final SharedCourse shared;
        try {
            shared = createSharedCourse(course, includeRecords);
        } catch (RuntimeException exception) {
            failure.accept(message(exception, "共有データを作成できませんでした"));
            return;
        }
        runDialog("AST Course Export", () -> {
            FileDialog dialog = new FileDialog((Frame) null, "コースを書き出す", FileDialog.SAVE);
            dialog.setDirectory(defaultDirectory().getAbsolutePath());
            dialog.setFile(safeFileName(shared.courseName) + ".astcourse");
            dialog.setVisible(true);
            File selected = selectedFile(dialog);
            dialog.dispose();
            if (selected == null) {
                deliver(cancelled);
                return;
            }
            if (!selected.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".astcourse")) {
                selected = new File(selected.getParentFile(), selected.getName() + ".astcourse");
            }
            final File output = selected;
            try {
                writeAtomically(output, GSON.toJson(shared));
                lastDirectory = output.getParentFile();
                deliver(() -> success.accept(output));
            } catch (Exception exception) {
                deliver(() -> failure.accept(message(exception, "書き出しに失敗しました")));
            }
        }, failure);
    }

    public static void chooseImport(Consumer<ImportPreview> success, Consumer<String> failure, Runnable cancelled) {
        runDialog("AST Course Import", () -> {
            FileDialog dialog = new FileDialog((Frame) null, "コースを読み込む", FileDialog.LOAD);
            dialog.setDirectory(defaultDirectory().getAbsolutePath());
            dialog.setFile("*.astcourse");
            dialog.setVisible(true);
            File selected = selectedFile(dialog);
            dialog.dispose();
            if (selected == null) {
                deliver(cancelled);
                return;
            }
            try {
                ImportPreview preview = readPreview(selected);
                lastDirectory = selected.getParentFile();
                deliver(() -> success.accept(preview));
            } catch (Exception exception) {
                deliver(() -> failure.accept(message(exception, "コースを読み込めませんでした")));
            }
        }, failure);
    }

    public static void importCourse(ImportPreview preview, String requestedName) throws IOException {
        if (preview == null) throw new IllegalArgumentException("読み込みデータがありません");
        String name = requestedName == null ? "" : requestedName.trim();
        if (name.isEmpty()) throw new IllegalArgumentException("コース名を入力してください");
        if (name.length() > 64) throw new IllegalArgumentException("コース名は64文字以内にしてください");
        AstData.CourseFile copy = GSON.fromJson(GSON.toJson(preview.course), AstData.CourseFile.class);
        AstCourseManager.get().importCourse(name, copy);
    }

    private static SharedCourse createSharedCourse(AstData.CourseFile course, boolean includeRecords) {
        AstData.CourseFile copy = GSON.fromJson(GSON.toJson(course), AstData.CourseFile.class);
        SharedCourse shared = new SharedCourse();
        shared.courseName = copy.courseName;
        shared.segments = copy.segments;
        shared.includesRecords = includeRecords;
        shared.records = includeRecords ? copy.stats : null;
        validate(shared);
        return shared;
    }

    private static ImportPreview readPreview(File file) throws IOException {
        if (file == null || !file.isFile()) throw new IOException("ファイルが見つかりません");
        if (file.length() <= 0 || file.length() > MAX_FILE_BYTES) throw new IOException("共有ファイルのサイズが不正です");
        SharedCourse shared;
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            shared = GSON.fromJson(reader, SharedCourse.class);
        } catch (JsonParseException exception) {
            throw new IOException("共有ファイルの内容が壊れています", exception);
        }
        validate(shared);

        AstData.CourseFile course = new AstData.CourseFile();
        course.courseName = shared.courseName.trim();
        course.segments = GSON.fromJson(GSON.toJson(shared.segments),
                new com.google.gson.reflect.TypeToken<List<AstData.Segment>>() {}.getType());
        course.stats = shared.includesRecords && shared.records != null
                ? GSON.fromJson(GSON.toJson(shared.records), AstData.Stats.class)
                : new AstData.Stats();
        int ground = 0;
        int aabb = 0;
        for (AstData.Segment segment : course.segments) {
            if ("ground".equals(segment.detection)) ground++;
            else aabb++;
        }
        return new ImportPreview(file, course, shared.includesRecords, ground, aabb);
    }

    private static void validate(SharedCourse shared) {
        if (shared == null || !FORMAT.equals(shared.format) || shared.formatVersion != FORMAT_VERSION) {
            throw new IllegalArgumentException("AutoSplit Timerの共有ファイルではありません");
        }
        if (shared.courseName == null || shared.courseName.trim().isEmpty() || shared.courseName.trim().length() > 64) {
            throw new IllegalArgumentException("コース名が不正です");
        }
        if (shared.segments == null || shared.segments.size() < 2 || shared.segments.size() > MAX_SEGMENTS) {
            throw new IllegalArgumentException("STARTとGOALを含むコースではありません");
        }
        Set<Integer> indices = new HashSet<>();
        boolean hasStart = false;
        int nonStart = 0;
        for (AstData.Segment segment : shared.segments) {
            if (segment == null || segment.index < 0 || !indices.add(segment.index)) {
                throw new IllegalArgumentException("地点の順番が不正です");
            }
            if (segment.index == 0) hasStart = true;
            else nonStart++;
            validateSegment(segment);
        }
        if (!hasStart || nonStart < 1) throw new IllegalArgumentException("STARTまたはGOALがありません");
        if (shared.includesRecords) validateRecords(shared.records, nonStart);
    }

    private static void validateSegment(AstData.Segment segment) {
        if (segment.name != null && segment.name.length() > 128) throw new IllegalArgumentException("地点名が長すぎます");
        if (!"ground".equals(segment.detection) && !"aabb".equals(segment.detection)) {
            throw new IllegalArgumentException("判定方式が不正です");
        }
        if (!Boolean.TRUE.equals(segment.placed)) return;
        if (segment.aabb == null || !finite(segment.aabb.minX) || !finite(segment.aabb.minY)
                || !finite(segment.aabb.minZ) || !finite(segment.aabb.maxX)
                || !finite(segment.aabb.maxY) || !finite(segment.aabb.maxZ)
                || segment.aabb.maxX <= segment.aabb.minX || segment.aabb.maxZ <= segment.aabb.minZ
                || !finite(segment.height) || segment.height <= 0 || segment.height > 256) {
            throw new IllegalArgumentException("地点の範囲が不正です");
        }
        if ("ground".equals(segment.detection)) {
            if (segment.groundCells == null || segment.groundCells.isEmpty() || segment.groundCells.size() > MAX_GROUND_CELLS) {
                throw new IllegalArgumentException("On Groundの範囲が不正です");
            }
            for (AstData.GroundCell cell : segment.groundCells) {
                if (cell == null || !finite(cell.y) || Math.abs((long) cell.x) > 30000000L
                        || Math.abs((long) cell.z) > 30000000L) {
                    throw new IllegalArgumentException("On Groundの座標が不正です");
                }
            }
        }
    }

    private static void validateRecords(AstData.Stats stats, int expected) {
        if (stats == null || stats.attemptCount < 0) throw new IllegalArgumentException("記録が不正です");
        if (stats.pb != null) {
            validateTick(stats.pb.totalTicks);
            validateTicks(stats.pb.segmentTicks, expected);
        }
        validateTicks(stats.bestSegmentsTicks, expected);
        validateTicks(stats.bestSplitTicks, expected);
    }

    private static void validateTicks(List<Integer> values, int expected) {
        if (values == null || values.size() > expected) throw new IllegalArgumentException("記録の区間数が不正です");
        for (Integer value : values) validateTick(value);
    }

    private static void validateTick(Integer value) {
        if (value != null && value < 0) throw new IllegalArgumentException("記録の時間が不正です");
    }

    private static boolean finite(double value) { return !Double.isNaN(value) && !Double.isInfinite(value); }

    private static void writeAtomically(File target, String json) throws IOException {
        File parent = target.getAbsoluteFile().getParentFile();
        if (parent == null || (!parent.exists() && !parent.mkdirs())) throw new IOException("保存先を作成できません");
        File temporary = File.createTempFile(".ast-course-", ".tmp", parent);
        try {
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(temporary), StandardCharsets.UTF_8)) {
                writer.write(json);
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

    private static void runDialog(String threadName, Runnable action, Consumer<String> failure) {
        Thread thread = new Thread(() -> {
            try { action.run(); }
            catch (Throwable throwable) {
                deliver(() -> failure.accept(message(throwable, "ファイル選択画面を開けませんでした")));
            }
        }, threadName);
        thread.setDaemon(true);
        thread.start();
    }

    private static File selectedFile(FileDialog dialog) {
        return dialog.getFile() == null ? null : new File(dialog.getDirectory(), dialog.getFile());
    }

    private static File defaultDirectory() {
        File remembered = lastDirectory;
        if (remembered != null && remembered.isDirectory()) return remembered;
        File directory = new File(Minecraft.getMinecraft().mcDataDir, "autosplittimer-share");
        if (!directory.exists()) directory.mkdirs();
        return directory;
    }

    private static String safeFileName(String value) {
        String safe = value == null ? "course" : value.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        return safe.isEmpty() ? "course" : safe;
    }

    private static void deliver(Runnable action) {
        Minecraft.getMinecraft().addScheduledTask(action);
    }

    private static String message(Throwable throwable, String fallback) {
        String value = throwable == null ? null : throwable.getMessage();
        return value == null || value.trim().isEmpty() ? fallback : value;
    }
}
