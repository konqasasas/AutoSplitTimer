package com.konqasasas.ast.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.konqasasas.ast.AutoSplitTimerMod;
import com.konqasasas.ast.core.AstData;
import net.minecraft.client.Minecraft;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Saves/loads HUD layouts independent of a course.
 *
 * Path: config/autosplittimer/layouts/<name>.json
 */
public final class AstLayoutManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

    private AstLayoutManager() {}

    /** File format: layout only (order + toggles). */
    private static class LayoutFile {
        public int version = 2;
        public List<String> itemOrder = new ArrayList<>();
        public Map<String, Boolean> toggles = new HashMap<>();

        // Layout-owned settings: "position, colors, format, preset, theme" are excluded.
        public double scale = 1.0;
        public String timeFormat = "MSS";
        public String splitColsPrimary = "seg";
        public String splitColsSecondary = "none";
        public int splitListCount = 4;
        public int splitListGap = 8;
        public int splitListLineGap = 1;
        public int splitPrimaryWidth = 42;
        public int splitSecondaryWidth = 42;
    }

    public static void saveLayoutSafe(String name, AstData.HudConfig hud) {
        if (hud == null) return;
        try { saveLayout(name, hud); } catch (Exception ignored) {}
    }

    /** Load layout and apply it onto an existing HUD config (does not touch style/geometry/position). */
    public static boolean loadLayoutIntoHudSafe(String name, AstData.HudConfig dstHud) {
        if (dstHud == null) return false;
        try {
            LayoutFile lf = loadLayout(name);
            if (lf == null) return false;
            applyLayout(dstHud, lf);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean deleteLayoutSafe(String name) {
        try {
            String safe = safeName(name);
            if (safe.isEmpty()) return false;
            File f = new File(layoutsDir(), safe + ".json");
            return f.exists() && f.delete();
        } catch (Exception ignored) {
            return false;
        }
    }

    public static List<String> listLayouts() {
        File dir = layoutsDir();
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

    private static void saveLayout(String name, AstData.HudConfig hud) throws IOException {
        String safe = safeName(name);
        if (safe.isEmpty()) throw new IOException("Invalid layout name");
        File f = new File(layoutsDir(), safe + ".json");
        File parent = f.getParentFile();
        if (!parent.exists()) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }
        AstHudConfigUtil.normalizeHud(hud);
        LayoutFile out = new LayoutFile();
        out.itemOrder = new ArrayList<>(hud.itemOrder);
        out.toggles = new HashMap<>(hud.toggles);

        // Layout-owned settings
        out.scale = hud.scale;
        out.timeFormat = hud.timeFormat;
        out.splitColsPrimary = hud.splitColsPrimary;
        out.splitColsSecondary = hud.splitColsSecondary;
        out.splitListCount = hud.splitListCount;
        out.splitListGap = hud.splitListGap;
        out.splitListLineGap = hud.splitListLineGap;
        out.splitPrimaryWidth = hud.splitPrimaryWidth;
        out.splitSecondaryWidth = hud.splitSecondaryWidth;
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
            GSON.toJson(out, w);
        }
    }

    private static LayoutFile loadLayout(String name) throws IOException {
        String safe = safeName(name);
        if (safe.isEmpty()) return null;
        File f = new File(layoutsDir(), safe + ".json");
        if (!f.exists()) return null;
        try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            LayoutFile lf = GSON.fromJson(r, LayoutFile.class);
            return lf;
        } catch (JsonSyntaxException jse) {
            throw new IOException("Invalid JSON: " + jse.getMessage(), jse);
        }
    }

    private static void applyLayout(AstData.HudConfig dst, LayoutFile lf) {
        if (dst == null || lf == null) return;
        if (lf.toggles == null) lf.toggles = new HashMap<>();
        if (lf.itemOrder == null) lf.itemOrder = new ArrayList<>();

        dst.toggles = new HashMap<>(lf.toggles);
        dst.itemOrder = new ArrayList<>(lf.itemOrder);

        // Layout-owned settings (be defensive for old files)
        if (lf.scale > 0) dst.scale = lf.scale;
        if (lf.timeFormat != null && !lf.timeFormat.trim().isEmpty()) dst.timeFormat = lf.timeFormat;
        if (lf.splitColsPrimary != null && !lf.splitColsPrimary.trim().isEmpty()) dst.splitColsPrimary = lf.splitColsPrimary;
        if (lf.splitColsSecondary != null && !lf.splitColsSecondary.trim().isEmpty()) dst.splitColsSecondary = lf.splitColsSecondary;
        if (lf.splitListCount > 0) dst.splitListCount = lf.splitListCount;
        dst.splitListGap = lf.splitListGap;
        dst.splitListLineGap = lf.splitListLineGap;
        if (lf.splitPrimaryWidth > 0) dst.splitPrimaryWidth = lf.splitPrimaryWidth;
        if (lf.splitSecondaryWidth > 0) dst.splitSecondaryWidth = lf.splitSecondaryWidth;
        AstHudConfigUtil.normalizeHud(dst);
    }

    private static File layoutsDir() {
        File base = new File(Minecraft.getMinecraft().mcDataDir, "config" + File.separator + AutoSplitTimerMod.MODID);
        File layouts = new File(base, "layouts");
        if (!layouts.exists()) {
            //noinspection ResultOfMethodCallIgnored
            layouts.mkdirs();
        }
        return layouts;
    }

    private static String safeName(String name) {
        if (name == null) return "";
        return name.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    // No deep copy helper needed: layouts store only order + toggles.

    // --- compat helpers (used by GUI/commands) ---

    /**
     * Loads a saved layout by name into a HudConfig (order + toggles only).
     * Returns null if not found or failed.
     */
    public static AstData.HudConfig loadLayoutSafe(String name) {
        try {
            LayoutFile lf = loadLayout(name); // private method in this class
            if (lf == null) return null;

            AstData.HudConfig hud = new AstData.HudConfig();
            if (lf.itemOrder != null) hud.itemOrder.addAll(lf.itemOrder);
            if (lf.toggles != null) hud.toggles.putAll(lf.toggles);
            return hud;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Loads layout by name and applies it to the given hud config safely.
     * Returns true on success.
     */
    public static boolean loadLayoutToHudSafe(String name, AstData.HudConfig hud) {
        AstData.HudConfig loaded = loadLayoutSafe(name);
        if (loaded == null || hud == null) return false;

        hud.itemOrder.clear();
        hud.itemOrder.addAll(loaded.itemOrder);

        hud.toggles.clear();
        hud.toggles.putAll(loaded.toggles);

        return true;
    }

}
