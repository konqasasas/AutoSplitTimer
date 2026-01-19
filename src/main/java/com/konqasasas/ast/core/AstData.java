package com.konqasasas.ast.core;

import net.minecraft.util.math.AxisAlignedBB;

import java.util.*;

/**
 * Plain data structures (Gson-serializable) for AutoSplit Timer.
 * Keep fields public for easy serialization.
 */
public final class AstData {
    private AstData() {}

    public static final int DATA_VERSION = 1;

    public static class AabbDto {
        public double minX, minY, minZ;
        public double maxX, maxY, maxZ;

        public AabbDto() {}

        public AabbDto(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }

        public AxisAlignedBB toAabb() {
            return new AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ);
        }

        public static AabbDto fromAabb(AxisAlignedBB bb) {
            return new AabbDto(bb.minX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ);
        }
    }

    public static class Segment {
        public int index;
        public String name;
        public AabbDto aabb;
        /** Height (Y size) in blocks. Allows fractions (e.g., 2.5). */
        public double height;

        public Segment() {}
    }

    public static class PbRecord {
        public Integer totalTicks; // nullable
        public List<Integer> segmentTicks = new ArrayList<>(); // nullable entries allowed

        public PbRecord() {}
    }

    public static class Stats {
        public int attemptCount = 0;
        public PbRecord pb = new PbRecord();
        public List<Integer> bestSegmentsTicks = new ArrayList<>(); // nullable entries allowed
        public List<Integer> bestSplitTicks = new ArrayList<>();    // nullable entries allowed

        public Stats() {}
    }

    public static class HudConfig {
        public String preset = "standard";
        public double scale = 1.0;
        public String theme = "default";
        public int splitListCount = 8;

        /** Split list total width (pixels, before scaling). */
        public int splitListWidth = 140;
        /** Split list column gap (pixels, before scaling). */
        public int splitListGap = 6;

        /** Split list extra line gap (pixels, before scaling). */
        public int splitListLineGap = 0;

        /** Split list: width of the primary column (right-aligned). */
        public int splitPrimaryWidth = 50;
        /** Split list: width of the secondary column (right-aligned). */
        public int splitSecondaryWidth = 40;

        /**
         * SplitList comparison target for Primary delta:
         *   "pb"   = vs PB
         *   "best" = vs Best
         */
        public String comparison = "pb";

        /**
         * SplitList time unit for Secondary (actual time) and the baseline used for delta.
         *   "split" = cumulative
         *   "seg"   = segment
         */
        public String unit = "split";

        // Legacy (pre-2026-01): splitColsPrimary/splitColsSecondary.
        // Kept only for backward-compat in JSON load; renderer/editor no longer uses these.
        public String splitColsPrimary = "split";
        public String splitColsSecondary = "none";

        // 16-color palette chars (0-f). These are embedded as § codes in rendered strings.
        // Theme-controlled (default/cool/cute). Only these three are user-editable.
        public String colorLabel = "8";
        public String colorMainText = "f";
        public String colorSubText = "7";
        public String colorGood = "a";      // delta negative (faster)
        public String colorBad = "c";       // delta positive (slower)
        public String colorGold = "6";

        public Map<String, Boolean> toggles = new HashMap<>();

        /** HUD item draw order. Items not present fall back to defaults. */
        public List<String> itemOrder = new ArrayList<>();

        // anchor and offsets (screen space) – simple and robust
        public String anchor = "TOP_LEFT";
        public int offsetX = 6;
        public int offsetY = 6;

        // time format: "MSS" (mm:ss.xx), "TICKS", "SECONDS"
        public String timeFormat = "MSS";

        public HudConfig() {
            // defaults
            toggles.put("sob", true);
            toggles.put("bpt", true);
            toggles.put("bestSeg", true);
            toggles.put("bestSplit", true);
            toggles.put("attempt", true);
            toggles.put("prevSeg", true);
            toggles.put("splitList", true);
            toggles.put("courseName", true);

            // Default order (PB item intentionally omitted per spec).
            itemOrder.add("courseName");
            itemOrder.add("time");
            itemOrder.add("segment");
            itemOrder.add("segmentTime");
            itemOrder.add("prevSeg");
            itemOrder.add("sob");
            itemOrder.add("bpt");
            itemOrder.add("bestSeg");
            itemOrder.add("bestSplit");
            itemOrder.add("attempt");
            itemOrder.add("splitList");
        }
    }

    public static class CourseFile {
        public int version = DATA_VERSION;
        public String courseName;
        public List<Segment> segments = new ArrayList<>();
        public Stats stats = new Stats();
        public HudConfig hud = new HudConfig();

        public CourseFile() {}
    }
}
