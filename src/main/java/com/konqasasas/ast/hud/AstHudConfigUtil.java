package com.konqasasas.ast.hud;

import com.konqasasas.ast.core.AstData;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Helpers for HUD config presets and normalization. */
public final class AstHudConfigUtil {
    private AstHudConfigUtil() {}

    /**
     * All first-install defaults in one place for easy editing.
     * NOTE: this is intentionally hard-coded (visual correctness > abstraction).
     */
    public static final class InitialDefaults {
        private InitialDefaults() {}

        public static final String DEFAULT_PRESET = "standard";
        public static final String DEFAULT_THEME = "default";

        /** A full initial HUD config template (deep-copied by {@link #newInitialHudConfig()}). */
        public static final AstData.HudConfig TEMPLATE = buildTemplate();

        private static AstData.HudConfig buildTemplate() {
            AstData.HudConfig h = new AstData.HudConfig();
            h.preset = DEFAULT_PRESET;
            h.theme = DEFAULT_THEME;
            // Apply the preset values from the bundled preset definition.
            AstHudConfigUtil.applyPreset(h, DEFAULT_PRESET, true);
            AstHudConfigUtil.applyTheme(h);
            AstHudConfigUtil.normalizeHud(h);
            return h;
        }
    }

    /** Create a deep copy of the first-install HUD config (safe to mutate). */
    public static AstData.HudConfig newInitialHudConfig() {
        AstData.HudConfig src = InitialDefaults.TEMPLATE;
        AstData.HudConfig dst = new AstData.HudConfig();
        // shallow fields (primitives/strings)
        dst.preset = src.preset;
        dst.theme = src.theme;
        dst.scale = src.scale;
        dst.timeFormat = src.timeFormat;
        dst.anchor = src.anchor;
        dst.offsetX = src.offsetX;
        dst.offsetY = src.offsetY;
        dst.splitListCount = src.splitListCount;
        dst.splitListWidth = src.splitListWidth;
        dst.splitListGap = src.splitListGap;
        dst.splitListLineGap = src.splitListLineGap;
        dst.splitPrimaryWidth = src.splitPrimaryWidth;
        dst.splitSecondaryWidth = src.splitSecondaryWidth;
        dst.comparison = src.comparison;
        dst.unit = src.unit;
        // legacy
        dst.splitColsPrimary = src.splitColsPrimary;
        dst.splitColsSecondary = src.splitColsSecondary;
        dst.colorLabel = src.colorLabel;
        dst.colorMainText = src.colorMainText;
        dst.colorSubText = src.colorSubText;
        dst.colorGood = src.colorGood;
        dst.colorBad = src.colorBad;
        dst.colorGold = src.colorGold;
        // deep fields
        if (src.toggles != null) dst.toggles = new java.util.HashMap<>(src.toggles);
        if (src.itemOrder != null) dst.itemOrder = new java.util.ArrayList<>(src.itemOrder);
        return dst;
    }

    /** Deep copy HUD config for safe reuse (courses share a global HUD). */
    public static AstData.HudConfig copyHud(AstData.HudConfig src) {
        if (src == null) return null;
        AstData.HudConfig dst = new AstData.HudConfig();
        dst.preset = src.preset;
        dst.theme = src.theme;
        dst.scale = src.scale;
        dst.timeFormat = src.timeFormat;
        dst.anchor = src.anchor;
        dst.offsetX = src.offsetX;
        dst.offsetY = src.offsetY;
        dst.splitListCount = src.splitListCount;
        dst.splitListWidth = src.splitListWidth;
        dst.splitListGap = src.splitListGap;
        dst.splitListLineGap = src.splitListLineGap;
        dst.splitPrimaryWidth = src.splitPrimaryWidth;
        dst.splitSecondaryWidth = src.splitSecondaryWidth;
        dst.comparison = src.comparison;
        dst.unit = src.unit;
        // legacy
        dst.splitColsPrimary = src.splitColsPrimary;
        dst.splitColsSecondary = src.splitColsSecondary;
        dst.colorLabel = src.colorLabel;
        dst.colorMainText = src.colorMainText;
        dst.colorSubText = src.colorSubText;
        dst.colorGood = src.colorGood;
        dst.colorBad = src.colorBad;
        dst.colorGold = src.colorGold;
        if (src.toggles != null) dst.toggles = new java.util.HashMap<>(src.toggles);
        if (src.itemOrder != null) dst.itemOrder = new java.util.ArrayList<>(src.itemOrder);
        normalizeHud(dst);
        return dst;
    }

    /**
     * Apply preset defaults to the given HUD config.
     *
     * @param keepPosition if true, preserves anchor/offsetX/offsetY.
     */
    public static void applyPreset(AstData.HudConfig hud, String presetName, boolean keepPosition) {
        if (hud == null) return;
        String preset = presetName == null ? "standard" : presetName.trim().toLowerCase(Locale.ROOT);
        if ("splitlist".equals(preset)) preset = "standard";

        // Preserve position if requested.
        String anchor = hud.anchor;
        int ox = hud.offsetX;
        int oy = hud.offsetY;
        String timeFmt = hud.timeFormat;
        String theme = hud.theme;
        // Hard reset (replace toggles map etc.)
        hud.scale = 1.0;
        // theme is NOT controlled by presets; keep current (or default in normalizeHud)
        hud.splitListCount = 4;
        hud.splitListWidth = 220;
        hud.splitListGap = 0;
        hud.splitListLineGap = 0;
        hud.splitPrimaryWidth = 10;
        hud.splitSecondaryWidth = 10;
        // SplitList: Livesplit-style
        hud.comparison = "pb";
        hud.unit = "split";
        // legacy (kept for backwards compat, but unused)
        hud.splitColsPrimary = "split";
        hud.splitColsSecondary = "none";

        if (hud.toggles != null) hud.toggles.clear();
        else hud.toggles = new HashMap<>();
        // defaults
        putToggleDefaults(hud);

        // Presets are designed to keep beginners out of "too much custom" hell.
        // They are opinionated: ON/OFF, order, split columns, and some geometry.
        switch (preset) {
            case "compact": {
                hud.preset = "compact";
                hud.scale = 0.90;
                hud.timeFormat = "seconds";
                hud.comparison = "pb";
                hud.unit = "split";
                hud.splitListCount = 4;
                hud.splitListGap = 30;
                hud.splitListLineGap = 0;
                hud.splitPrimaryWidth = 10;
                hud.splitSecondaryWidth = 10;
                hud.itemOrder = new java.util.ArrayList<>(java.util.Arrays.asList("time", "segment", "segmentTime", "bpt", "splitList", "courseName", "prevSeg", "sob", "attempt", "bestSeg", "bestSplit"));
                setTogglesAllOff(hud);
                hud.toggles.put("courseName", false);
                hud.toggles.put("segmentTime", true);
                hud.toggles.put("segment", true);
                hud.toggles.put("sob", false);
                hud.toggles.put("bpt", true);
                hud.toggles.put("bestSeg", false);
                hud.toggles.put("bestSplit", false);
                hud.toggles.put("prevSeg", false);
                hud.toggles.put("time", true);
                hud.toggles.put("attempt", false);
                hud.toggles.put("splitList", true);
                break;
            }
            case "minimal": {
                hud.preset = "minimal";
                hud.scale = 1.00;
                hud.timeFormat = "TICKS";
                hud.comparison = "pb";
                hud.unit = "split";
                hud.splitListCount = 4;
                hud.splitListGap = 40;
                hud.splitListLineGap = 0;
                hud.splitPrimaryWidth = 10;
                hud.splitSecondaryWidth = 10;
                hud.itemOrder = new java.util.ArrayList<>(java.util.Arrays.asList("courseName", "time", "segment", "segmentTime", "prevSeg", "sob", "bpt", "bestSeg", "bestSplit", "attempt", "splitList"));
                setTogglesAllOff(hud);
                hud.toggles.put("courseName", false);
                hud.toggles.put("segmentTime", true);
                hud.toggles.put("sob", false);
                hud.toggles.put("bpt", false);
                hud.toggles.put("bestSeg", false);
                hud.toggles.put("segment", true);
                hud.toggles.put("bestSplit", false);
                hud.toggles.put("prevSeg", false);
                hud.toggles.put("time", true);
                hud.toggles.put("attempt", false);
                hud.toggles.put("splitList", false);
                break;
            }
            case "practice": {
                hud.preset = "practice";
                hud.scale = 1.00;
                hud.timeFormat = "MSS";
                hud.comparison = "pb";
                hud.unit = "split";
                hud.splitListCount = 4;
                hud.splitListGap = 40;
                hud.splitListLineGap = 0;
                hud.splitPrimaryWidth = 10;
                hud.splitSecondaryWidth = 10;
                hud.itemOrder = new java.util.ArrayList<>(java.util.Arrays.asList("courseName", "attempt", "time", "segment", "segmentTime", "prevSeg", "bestSeg", "bestSplit", "bpt", "sob", "splitList"));
                setTogglesAllOff(hud);
                hud.toggles.put("courseName", true);
                hud.toggles.put("segmentTime", true);
                hud.toggles.put("segment", true);
                hud.toggles.put("sob", true);
                hud.toggles.put("bpt", true);
                hud.toggles.put("bestSeg", true);
                hud.toggles.put("bestSplit", true);
                hud.toggles.put("prevSeg", true);
                hud.toggles.put("time", true);
                hud.toggles.put("attempt", true);
                hud.toggles.put("splitList", true);
                break;
            }
            case "standard": {
                hud.preset = "standard";
                hud.scale = 1.00;
                hud.timeFormat = "seconds";
                hud.comparison = "pb";
                hud.unit = "split";
                hud.splitListCount = 4;
                hud.splitListGap = 35;
                hud.splitListLineGap = 0;
                hud.splitPrimaryWidth = 10;
                hud.splitSecondaryWidth = 10;
                hud.itemOrder = new java.util.ArrayList<>(java.util.Arrays.asList("courseName", "time", "segment", "segmentTime", "bpt", "sob", "splitList", "prevSeg", "attempt", "bestSeg", "bestSplit"));
                setTogglesAllOff(hud);
                hud.toggles.put("courseName", true);
                hud.toggles.put("segmentTime", true);
                hud.toggles.put("segment", true);
                hud.toggles.put("sob", true);
                hud.toggles.put("bpt", true);
                hud.toggles.put("bestSeg", false);
                hud.toggles.put("bestSplit", false);
                hud.toggles.put("prevSeg", false);
                hud.toggles.put("time", true);
                hud.toggles.put("attempt", false);
                hud.toggles.put("splitList", true);
                break;
            }
            default: {
                hud.preset = "standard";
                break;
            }
        }

        // If preset did not explicitly set time format, keep existing unless missing.
        if (hud.timeFormat == null || hud.timeFormat.trim().isEmpty()) {
            hud.timeFormat = (timeFmt != null && !timeFmt.trim().isEmpty()) ? timeFmt : "MSS";
        }

        if (keepPosition) {
            if (anchor != null && !anchor.trim().isEmpty()) hud.anchor = anchor;
            hud.offsetX = ox;
            hud.offsetY = oy;
        }

        if (theme != null && !theme.trim().isEmpty()) hud.theme = theme;

        normalizeHud(hud);
    }

    /** Ensure all expected fields exist and are sane. */
    public static void normalizeHud(AstData.HudConfig hud) {
        if (hud == null) return;
        if (hud.toggles == null) hud.toggles = new HashMap<>();
        // Fill missing toggles with defaults (do not overwrite existing)
        Map<String, Boolean> def = defaultToggles();
        for (Map.Entry<String, Boolean> e : def.entrySet()) {
            if (!hud.toggles.containsKey(e.getKey())) hud.toggles.put(e.getKey(), e.getValue());
        }
        if (hud.scale <= 0) hud.scale = 1.0;
        if (hud.preset == null || hud.preset.trim().isEmpty()) hud.preset = "standard";
        if (hud.theme == null || hud.theme.trim().isEmpty()) hud.theme = "default";
        // Rows: minimum 2
        if (hud.splitListCount < 2) hud.splitListCount = 2;
        if (hud.splitListWidth <= 0) hud.splitListWidth = 140;
        // colgap: 0..80
        if (hud.splitListGap < 0) hud.splitListGap = 0;
        if (hud.splitListGap > 80) hud.splitListGap = 80;
        if (hud.splitListLineGap < 0) hud.splitListLineGap = 0;
        if (hud.splitListLineGap > 12) hud.splitListLineGap = 12;
        // splitListLineGap is user-configurable
        if (hud.splitPrimaryWidth <= 0) hud.splitPrimaryWidth = 50;
        if (hud.splitSecondaryWidth <= 0) hud.splitSecondaryWidth = 40;
        // SplitList (Livesplit-style)
        // Migrate from legacy splitCols* if needed.
        if (hud.unit == null || hud.unit.trim().isEmpty()) {
            hud.unit = (hud.splitColsPrimary != null && "seg".equalsIgnoreCase(stripFormatting(hud.splitColsPrimary).trim())) ? "seg" : "split";
        }
        if (hud.comparison == null || hud.comparison.trim().isEmpty()) {
            String legacy = stripFormatting(hud.splitColsSecondary).trim().toLowerCase(java.util.Locale.ROOT);
            hud.comparison = legacy.contains("best") ? "best" : "pb";
        }
        hud.unit = canonicalUnit(hud.unit);
        hud.comparison = canonicalComparison(hud.comparison);

        // Legacy fields: keep sanitized so older JSON stays stable, but they are no longer used.
        hud.splitColsPrimary = canonicalSplitPrimary(hud.splitColsPrimary);
        hud.splitColsSecondary = canonicalSplitSecondary(hud.splitColsSecondary);
        if (hud.colorLabel == null || hud.colorLabel.trim().isEmpty()) hud.colorLabel = "7";
        if (hud.colorMainText == null || hud.colorMainText.trim().isEmpty()) hud.colorMainText = "f";
        if (hud.colorSubText == null || hud.colorSubText.trim().isEmpty()) hud.colorSubText = "8";
        if (hud.colorGood == null || hud.colorGood.trim().isEmpty()) hud.colorGood = "a";
        if (hud.colorBad == null || hud.colorBad.trim().isEmpty()) hud.colorBad = "c";
        if (hud.colorGold == null || hud.colorGold.trim().isEmpty()) hud.colorGold = "6";
        if (hud.anchor == null || hud.anchor.trim().isEmpty()) hud.anchor = "TOP_LEFT";
        if (hud.timeFormat == null || hud.timeFormat.trim().isEmpty()) hud.timeFormat = "MSS";
        hud.timeFormat = canonicalTimeFormat(hud.timeFormat);

        // Ensure item order is usable.
        ensureItemOrder(hud);

        // Keep split columns sane vs total width.
        reconcileSplitWidths(hud, false);
    }

    private static String stripFormatting(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u00a7') {
                // skip the formatting code char + its argument if present
                i++;
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    private static String canonicalSplitPrimary(String s) {
        String v = stripFormatting(s).trim().toLowerCase(java.util.Locale.ROOT);
        if (v.isEmpty()) return "split";
        if ("seg".equals(v) || "split".equals(v)) return v;
        // tolerate common typos
        if ("segment".equals(v)) return "seg";
        return "split";
    }

    private static String canonicalSplitSecondary(String s) {
        String v = stripFormatting(s).trim().toLowerCase(java.util.Locale.ROOT);
        if (v.isEmpty()) return "none";
        switch (v) {
            case "none":
            case "pbseg":
            case "pbsplit":
            case "bestseg":
            case "bestsplit":
                return v;
            // tolerate common variants
            case "pb":
            case "pbs":
                return "pbsplit";
            case "best":
                return "bestsplit";
            default:
                return "none";
        }
    }

    private static String canonicalComparison(String s) {
        String v = stripFormatting(s).trim().toLowerCase(java.util.Locale.ROOT);
        if (v.isEmpty()) return "pb";
        if ("pb".equals(v) || "personalbest".equals(v)) return "pb";
        if ("best".equals(v) || "bestsegments".equals(v) || "sob".equals(v)) return "best";
        return "pb";
    }

    private static String canonicalUnit(String s) {
        String v = stripFormatting(s).trim().toLowerCase(java.util.Locale.ROOT);
        if (v.isEmpty()) return "split";
        if ("seg".equals(v) || "segment".equals(v)) return "seg";
        if ("split".equals(v) || "cumulative".equals(v)) return "split";
        return "split";
    }

    private static String canonicalTimeFormat(String s) {
        String v = stripFormatting(s).trim().toLowerCase(java.util.Locale.ROOT);
        if (v.isEmpty()) return "MSS";
        if ("mss".equals(v) || "ms".equals(v) || "mmss".equals(v)) return "MSS";
        if ("ticks".equals(v) || "tick".equals(v)) return "TICKS";
        if ("seconds".equals(v) || "sec".equals(v) || "s".equals(v)) return "SECONDS";
        return "MSS";
    }
    /** Apply palette defaults for the current theme (does not touch toggles/geometry). */
    public static void applyTheme(AstData.HudConfig hud) {
        if (hud == null) return;
        String t = hud.theme == null ? "default" : hud.theme.trim().toLowerCase(java.util.Locale.ROOT);
        switch (t) {
            case "cute":
                hud.colorLabel = "d";
                hud.colorMainText = "e";
                hud.colorSubText = "f";
                break;
            case "dark":
                hud.colorLabel = "8";
                hud.colorMainText = "f";
                hud.colorSubText = "7";
                break;
            case "default":
            default:
                hud.colorLabel = "3";
                hud.colorMainText = "b";
                hud.colorSubText = "f";
                break;
        }

        // Always keep good/bad/gold fixed (not theme-editable).
        hud.colorGood = "a";
        hud.colorBad = "c";
        hud.colorGold = "6";
    }


    /** Known HUD item keys (PB removed). */
    public static String[] knownItems() {
        return new String[]{
                "courseName", "time", "segment", "segmentTime", "prevSeg",
                "sob", "bpt", "bestSeg", "bestSplit", "attempt", "splitList"
        };
    }

    /** Ensure hud.itemOrder contains all known items exactly once, preserving existing order when possible. */
    public static void ensureItemOrder(AstData.HudConfig hud) {
        if (hud == null) return;
        if (hud.itemOrder == null) hud.itemOrder = new java.util.ArrayList<>();
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        for (String k : hud.itemOrder) {
            if (k == null) continue;
            String kk = k.trim();
            if (kk.isEmpty()) continue;
            // skip PB legacy
            if ("pb".equalsIgnoreCase(kk)) continue;
            // keep only known
            boolean known = false;
            for (String n : knownItems()) {
                if (n.equalsIgnoreCase(kk)) { known = true; kk = n; break; }
            }
            if (known) out.add(kk);
        }
        for (String n : knownItems()) out.add(n);
        hud.itemOrder = new java.util.ArrayList<>(out);
    }

    /**
     * Adjust primary/secondary widths to fit within splitListWidth.
     * splitListWidth is the TOTAL row width, including: label + gap + primary + gap + secondary.
     * Label is flexible, so we ensure primary+secondary+2*gap <= total-20.
     */
    public static void reconcileSplitWidths(AstData.HudConfig hud, boolean preserveRatio) {
        if (hud == null) return;
        int total = Math.max(60, hud.splitListWidth);
        int gap = Math.max(0, hud.splitListGap);

        // Leave at least some room for labels.
        int labelMin = 40;
        int maxRight = Math.max(20, total - labelMin);
        int maxCols = Math.max(0, maxRight - 2 * gap);

        int p = Math.max(10, hud.splitPrimaryWidth);
        int s = Math.max(10, hud.splitSecondaryWidth);
        if (p + s > maxCols) {
            if (preserveRatio) {
                double ratio = (p <= 0 || s <= 0) ? 0.55 : (double) p / (double) (p + s);
                p = Math.max(10, (int) Math.round(maxCols * ratio));
                s = Math.max(10, maxCols - p);
            } else {
                // clamp secondary first, then primary.
                s = Math.min(s, Math.max(10, maxCols / 2));
                p = Math.min(p, Math.max(10, maxCols - s));
                if (p + s > maxCols) {
                    s = Math.max(10, maxCols - p);
                }
            }
        }
        hud.splitPrimaryWidth = p;
        hud.splitSecondaryWidth = s;
        hud.splitListWidth = total;
        hud.splitListGap = gap;
    }

    /**
     * Cycle split list column settings (GUI-friendly).
     *
     * Patterns:
     *   1) split / none
     *   2) split / pbsplit
     *   3) split / bestsplit
     *   4) seg   / none
     *   5) seg   / pbseg
     *   6) seg   / bestseg
     */
    public static void cycleSplitCols(AstData.HudConfig hud) {
        if (hud == null) return;
        String p = hud.splitColsPrimary == null ? "split" : hud.splitColsPrimary.toLowerCase(Locale.ROOT);
        String s = hud.splitColsSecondary == null ? "none" : hud.splitColsSecondary.toLowerCase(Locale.ROOT);
        String key = p + "/" + s;
        switch (key) {
            case "split/none":
                hud.splitColsPrimary = "split";
                hud.splitColsSecondary = "pbsplit";
                break;
            case "split/pbsplit":
                hud.splitColsPrimary = "split";
                hud.splitColsSecondary = "bestsplit";
                break;
            case "split/bestsplit":
                hud.splitColsPrimary = "seg";
                hud.splitColsSecondary = "none";
                break;
            case "seg/none":
                hud.splitColsPrimary = "seg";
                hud.splitColsSecondary = "pbseg";
                break;
            case "seg/pbseg":
                hud.splitColsPrimary = "seg";
                hud.splitColsSecondary = "bestseg";
                break;
            case "seg/bestseg":
            default:
                hud.splitColsPrimary = "split";
                hud.splitColsSecondary = "none";
                break;
        }
        normalizeHud(hud);
    }

    private static void putToggleDefaults(AstData.HudConfig hud) {
        Map<String, Boolean> def = defaultToggles();
        for (Map.Entry<String, Boolean> e : def.entrySet()) {
            hud.toggles.put(e.getKey(), e.getValue());
        }
    }

    /** Replace HUD draw order with the provided keys (unknown keys are ignored by normalization). */
    private static void setOrder(AstData.HudConfig hud, String... keys) {
        if (hud == null) return;
        hud.itemOrder = new java.util.ArrayList<>();
        if (keys == null) return;
        for (String k : keys) {
            if (k == null) continue;
            String kk = k.trim();
            if (!kk.isEmpty()) hud.itemOrder.add(kk);
        }
    }

    /** Set all known toggles to false (then preset can enable what it wants). */
    private static void setTogglesAllOff(AstData.HudConfig hud) {
        if (hud == null) return;
        if (hud.toggles == null) hud.toggles = new HashMap<>();
        for (String k : defaultToggles().keySet()) {
            hud.toggles.put(k, false);
        }
    }

    public static Map<String, Boolean> defaultToggles() {
        Map<String, Boolean> m = new HashMap<>();
        m.put("courseName", true);
        m.put("time", true);
        m.put("segment", true);
        m.put("segmentTime", true);
        m.put("sob", true);
        m.put("bpt", true);
        m.put("bestSeg", true);
        m.put("bestSplit", true);
        m.put("attempt", true);
        m.put("prevSeg", true);
        m.put("splitList", true);
        return m;
    }
}
