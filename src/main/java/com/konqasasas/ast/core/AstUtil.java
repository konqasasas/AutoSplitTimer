package com.konqasasas.ast.core;

import com.konqasasas.ast.hud.AstHudConfigUtil;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

public final class AstUtil {
    private AstUtil() {}

    /**
     * Backward-compat shim.
     *
     * Some earlier revisions of the mod called this from commands.
     * Newer code moved preset logic to {@link AstHudConfigUtil}.
     */
    public static void applyHudPreset(AstData.HudConfig hud, String preset) {
        AstHudConfigUtil.applyPreset(hud, preset, true);
    }

    public static boolean contains(AxisAlignedBB bb, Vec3d p) {
        // Use half-open intervals to match block-grid expectations
        return p.x >= bb.minX && p.x < bb.maxX
                && p.y >= bb.minY && p.y < bb.maxY
                && p.z >= bb.minZ && p.z < bb.maxZ;
    }

    public static AstData.Segment findSegment(AstData.CourseFile course, int index) {
        if (course == null || course.segments == null) return null;
        for (AstData.Segment s : course.segments) {
            if (s != null && s.index == index) return s;
        }
        return null;
    }

    public static List<Integer> sortedNonStartIndices(AstData.CourseFile course) {
        List<Integer> out = new ArrayList<>();
        if (course == null || course.segments == null) return out;
        for (AstData.Segment s : course.segments) {
            if (s == null) continue;
            if (s.index != 0) out.add(s.index);
        }
        Collections.sort(out);
        return out;
    }

    public static int nextExistingIndex(List<Integer> sortedNonStart, int current) {
        for (int idx : sortedNonStart) {
            if (idx > current) return idx;
        }
        return Integer.MAX_VALUE;
    }

    public static String formatTicks(int ticks, String timeFormat) {
        if (timeFormat == null) timeFormat = "MSS";
        switch (timeFormat.toUpperCase(Locale.ROOT)) {
            case "TICKS":
                // Tick-based timing (client timebase). Suffix helps readability.
                return Integer.toString(ticks) + "t";
            case "SECONDS":
                // 1 tick = 0.05s
                double sec = ticks / 20.0;
                // keep 2 decimals
                return String.format(Locale.ROOT, "%.2f", sec);
            case "MSS":
            default: {
                int totalCs = (int) Math.round(ticks * 5); // 0.05s = 5 centiseconds
                int minutes = totalCs / 6000;
                int secCs = totalCs % 6000;
                int seconds = secCs / 100;
                int centis = secCs % 100;
                return String.format(Locale.ROOT, "%d:%02d.%02d", minutes, seconds, centis);
            }
        }
    }



    /**
     * Format a double for user-facing UI/chat without rounding.
     *
     * Policy:
     *  - truncate to 1e-5 (5 decimal places)
     *  - strip trailing zeros and trailing dot
     *  - avoid scientific notation
     */
    public static String formatDoubleTrunc5(double v) {
        if (Double.isNaN(v)) return "NaN";
        if (Double.isInfinite(v)) return (v > 0 ? "Infinity" : "-Infinity");
        BigDecimal bd = BigDecimal.valueOf(v)
                // RoundingMode.DOWN truncates toward zero.
                .setScale(5, RoundingMode.DOWN)
                .stripTrailingZeros();
        String s = bd.toPlainString();
        // stripTrailingZeros can leave a trailing dot (e.g., "2.")
        if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        // stripTrailingZeros can produce "-0" in rare cases.
        if ("-0".equals(s) || "-0.0".equals(s)) return "0";
        return s;
    }

    /** Clear all records (PB, best segments/splits, attempts) for a course. */
    public static void clearStats(AstData.CourseFile course) {
        if (course == null) return;
        if (course.stats == null) course.stats = new AstData.Stats();
        course.stats.pb = null;
        course.stats.bestSegmentsTicks = new ArrayList<>();
        course.stats.bestSplitTicks = new ArrayList<>();
        course.stats.attemptCount = 0;
    }
}
