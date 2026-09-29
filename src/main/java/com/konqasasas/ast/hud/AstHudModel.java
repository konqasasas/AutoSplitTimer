package com.konqasasas.ast.hud;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.konqasasas.ast.core.AstData;
import com.konqasasas.ast.core.AstRuntime;
import com.konqasasas.ast.core.AstUtil;

import java.util.*;

/** Builds the calculated data model consumed by the native HUD renderer. */
public final class AstHudModel {
    private AstHudModel() {}

    public static JsonObject buildSnapshot(AstData.CourseFile course, AstData.HudConfig hud) {
        JsonObject root = new JsonObject();
        root.add("hud", hudConfigSnapshot(hud));
        root.addProperty("courseName", safe(course.courseName));
        // 1.35 is the visual baseline established by the approved compact HUD.
        root.addProperty("renderScale", Math.max(0.25, Math.min(3.0, hud.scale)) * 1.35);

        AstRuntime rt = AstRuntime.get();
        AstRuntime.State state = rt.getState();
        Integer previous = rt.getLastCompletedSegmentTicks();
        int segmentTicks = state == AstRuntime.State.FINISHED
                ? (previous == null ? 0 : previous)
                : currentSegmentTicks(rt);
        DerivedStats stats = DerivedStats.from(course, rt);

        JsonObject values = new JsonObject();
        values.addProperty("courseName", safe(course.courseName));
        values.addProperty("time", AstUtil.formatTicks(rt.getElapsedTicks(), hud.timeFormat));
        values.addProperty("segment", webSegmentName(course, rt));
        values.addProperty("segmentTime", AstUtil.formatTicks(segmentTicks, hud.timeFormat));
        values.addProperty("prevSeg", previous == null ? "--" : AstUtil.formatTicks(previous, hud.timeFormat));
        values.addProperty("sob", stats.sumOfBestStr);
        values.addProperty("bpt", stats.bestPossibleStr);
        values.addProperty("bestSeg", stats.bestSegStr);
        values.addProperty("bestSplit", stats.bestSplitStr);
        values.addProperty("attempt", String.valueOf(course.stats.attemptCount));
        root.add("values", values);
        root.add("splits", webSplitRows(course, rt, hud));
        return root;
    }

    private static JsonObject hudConfigSnapshot(AstData.HudConfig hud) {
        JsonObject out = new JsonObject();
        out.addProperty("preset", hud.preset);
        out.addProperty("theme", hud.theme);
        out.addProperty("scale", hud.scale);
        out.addProperty("timeFormat", hud.timeFormat);
        out.addProperty("deltaTimeFormat", hud.deltaTimeFormat);
        out.addProperty("comparison", hud.comparison);
        out.addProperty("unit", hud.unit);
        out.addProperty("splitListCount", hud.splitListCount);
        out.addProperty("splitListGap", hud.splitListGap);
        out.addProperty("splitListLineGap", hud.splitListLineGap);
        out.addProperty("splitColumns", hud.splitColumns);
        out.addProperty("splitListWidth", hud.splitListWidth);
        out.addProperty("splitDeltaOffset", hud.splitDeltaOffset);
        out.addProperty("anchor", hud.anchor);
        out.addProperty("offsetX", hud.offsetX);
        out.addProperty("offsetY", hud.offsetY);
        out.addProperty("backgroundRgb", hud.backgroundRgb);
        out.addProperty("backgroundOpacity", hud.backgroundOpacity);
        out.addProperty("labelRgb", hud.labelRgb);
        out.addProperty("mainRgb", hud.mainRgb);
        out.addProperty("subRgb", hud.subRgb);
        JsonObject toggles = new JsonObject();
        for (String key : AstHudConfigUtil.knownItems()) toggles.addProperty(key, isOn(hud, key));
        out.add("toggles", toggles);
        JsonArray order = new JsonArray();
        if (hud.itemOrder != null) for (String key : hud.itemOrder) order.add(key);
        out.add("itemOrder", order);
        return out;
    }

    private static JsonArray webSplitRows(AstData.CourseFile course, AstRuntime rt, AstData.HudConfig hud) {
        JsonArray rows = new JsonArray();
        List<Integer> order = AstUtil.sortedNonStartIndices(course);
        if (order.isEmpty()) return rows;

        List<Integer> visible = visibleSplitIndices(order, rt, Math.max(0, hud.splitListCount));
        List<Integer> pbSeg = rt.getBaselinePbSegOrNull();
        List<Integer> pbSplit = rt.getBaselinePbSplitOrNull();
        List<Integer> bestSeg = rt.getBaselineBestSegOrNull();
        List<Integer> bestSplit = rt.getBaselineBestSplitOrNull();
        if (pbSeg == null) pbSeg = course.stats.pb == null ? null : course.stats.pb.segmentTicks;
        if (pbSplit == null) pbSplit = buildPbSplit(pbSeg);
        if (bestSeg == null) bestSeg = course.stats.bestSegmentsTicks;
        if (bestSplit == null) bestSplit = course.stats.bestSplitTicks;

        Map<Integer, Integer> runSeg = rt.getRunSegmentTicks();
        Map<Integer, Integer> runSplit = rt.getRunSplitCumulative();
        Set<Integer> goldSeg = rt.getGoldSegmentsThisRun();
        int elapsed = rt.getElapsedTicks();
        int curSegTicks = Math.max(0, elapsed - rt.getLastSplitCumulative());
        String comparison = hud.comparison == null ? "pb" : hud.comparison.toLowerCase(Locale.ROOT);
        String unit = hud.unit == null ? "split" : hud.unit.toLowerCase(Locale.ROOT);
        int currentPos = currentSplitPosition(rt, order);

        for (int idx : visible) {
            int pos = indexOf(order, idx);
            AstData.Segment segment = AstUtil.findSegment(course, idx);
            String name = segment == null || segment.name == null ? "#" + idx : segment.name;
            SegmentState segmentState = calcState(rt, pos, currentPos);
            Integer baseline;
            if ("best".equals(comparison)) {
                baseline = "seg".equals(unit) ? valueAt(bestSeg, pos) : valueAt(bestSplit, pos);
            } else {
                baseline = "seg".equals(unit) ? valueAt(pbSeg, pos) : valueAt(pbSplit, pos);
            }
            Integer actual = segmentState == SegmentState.PAST
                    ? ("seg".equals(unit) ? runSeg.get(idx) : runSplit.get(idx)) : null;
            String secondary = segmentState == SegmentState.PAST
                    ? (actual == null ? "--" : AstUtil.formatTicks(actual, hud.timeFormat))
                    : (baseline == null ? "--" : AstUtil.formatTicks(baseline, hud.timeFormat));
            String primary = "";
            String tone = "neutral";
            if (segmentState == SegmentState.PAST && actual != null && baseline != null) {
                int delta = actual - baseline;
                primary = formatDelta(delta, hud.deltaTimeFormat);
                Integer completed = runSeg.get(idx);
                Integer previousBest = valueAt(bestSeg, pos);
                boolean gold = goldSeg.contains(idx) || (completed != null && previousBest == null);
                if (gold) tone = "gold";
                else if (delta < 0) tone = "good";
                else if (delta > 0) tone = "bad";
            } else if (segmentState == SegmentState.ACTIVE && baseline != null) {
                int liveActual = "seg".equals(unit) ? curSegTicks : elapsed;
                if (liveActual > baseline) {
                    primary = formatDelta(liveActual - baseline, hud.deltaTimeFormat);
                    tone = "bad";
                }
            }

            JsonObject row = new JsonObject();
            row.addProperty("id", idx);
            // START only arms the timer. LiveSplit numbers the first checkpoint
            // reached after the start as split 01, so use the split's position in
            // the non-START order rather than the course segment index.
            row.addProperty("name", String.format(Locale.ROOT, "%02d %s", pos + 1, name));
            row.addProperty("primary", primary);
            row.addProperty("secondary", secondary);
            row.addProperty("state", segmentState.name().toLowerCase(Locale.ROOT));
            row.addProperty("tone", tone);
            rows.add(row);
        }
        return rows;
    }

    private static List<Integer> visibleSplitIndices(List<Integer> order, AstRuntime rt, int count) {
        List<Integer> visible = new ArrayList<>();
        if (count <= 0 || order.isEmpty()) return visible;
        int goal = order.get(order.size() - 1);
        if (count == 1) {
            visible.add(goal);
            return visible;
        }
        int nonGoalSlots = count - 1;
        int nonGoalCount = Math.max(0, order.size() - 1);
        int maxStart = Math.max(0, nonGoalCount - nonGoalSlots);
        int activePos = currentSplitPosition(rt, order);
        if (activePos < 0) activePos = 0;
        if (activePos >= nonGoalCount) activePos = Math.max(0, nonGoalCount - 1);
        int start = Math.max(0, Math.min(maxStart, activePos - (nonGoalSlots - 1)));
        for (int i = start; i < start + nonGoalSlots && i < nonGoalCount; i++) visible.add(order.get(i));
        visible.add(goal);
        return visible;
    }

    private static Integer valueAt(List<Integer> values, int index) {
        return values != null && index >= 0 && index < values.size() ? values.get(index) : null;
    }

    private static String webSegmentName(AstData.CourseFile course, AstRuntime rt) {
        if (rt.getState() == AstRuntime.State.FINISHED) return "FINISHED";
        if (rt.getState() == AstRuntime.State.IDLE) return "--";
        List<Integer> order = AstUtil.sortedNonStartIndices(course);
        int currentPos = currentSplitPosition(rt, order);
        if (currentPos < 0 || currentPos >= order.size()) return "--";
        AstData.Segment segment = AstUtil.findSegment(course, order.get(currentPos));
        if (segment == null) return "--";
        return String.format(Locale.ROOT, "%02d / %02d  %s", currentPos + 1, order.size(), safe(segment.name));
    }

    private static boolean isOn(AstData.HudConfig hud, String key) {
        if (hud == null || hud.toggles == null) return true;
        Boolean value = hud.toggles.get(key);
        return value == null || value;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
    private enum SegmentState { FUTURE, ACTIVE, PAST }

    private static SegmentState calcState(AstRuntime rt, int pos, int currentPos) {
        AstRuntime.State st = rt.getState();
        if (st == AstRuntime.State.FINISHED) return SegmentState.PAST;
        if (st != AstRuntime.State.RUNNING) return SegmentState.FUTURE;
        if (pos == currentPos) return SegmentState.ACTIVE;
        if (pos >= 0 && currentPos >= 0) return (pos < currentPos) ? SegmentState.PAST : SegmentState.FUTURE;
        return SegmentState.FUTURE;
    }

    /** LiveSplit-compatible split position: -1 idle, 0..n-1 running, n finished. */
    private static int currentSplitPosition(AstRuntime rt, List<Integer> order) {
        if (rt.getState() == AstRuntime.State.IDLE) return -1;
        if (rt.getState() == AstRuntime.State.FINISHED) return order.size();
        return indexOf(order, rt.getNextIndex());
    }

    private static int indexOf(List<Integer> list, int value) {
        for (int i = 0; i < list.size(); i++) if (list.get(i) == value) return i;
        return -1;
    }

    private static List<Integer> buildPbSplit(List<Integer> pbSeg) {
        if (pbSeg == null) return null;
        List<Integer> out = new ArrayList<>(pbSeg.size());
        int s = 0;
        for (Integer t : pbSeg) {
            if (t == null) { out.add(null); }
            else { s += t; out.add(s); }
        }
        return out;
    }

    private static Integer baselineAt(String mode, int pos, List<Integer> pbSeg, List<Integer> pbSplit, List<Integer> bestSeg, List<Integer> bestSplit) {
        switch (mode) {
            case "pbseg":
                return (pbSeg != null && pos < pbSeg.size()) ? pbSeg.get(pos) : null;
            case "pbsplit":
                return (pbSplit != null && pos < pbSplit.size()) ? pbSplit.get(pos) : null;
            case "bestseg":
                return (bestSeg != null && pos < bestSeg.size()) ? bestSeg.get(pos) : null;
            case "bestsplit":
                return (bestSplit != null && pos < bestSplit.size()) ? bestSplit.get(pos) : null;
            default:
                return null;
        }
    }

    private static Integer actualFor(String mode, int idx, Map<Integer, Integer> runSeg, Map<Integer, Integer> runSplit) {
        switch (mode) {
            case "pbseg":
            case "bestseg":
                return runSeg.get(idx);
            case "pbsplit":
            case "bestsplit":
                return runSplit.get(idx);
            default:
                return null;
        }
    }

    private static String formatDelta(int deltaTicks, String timeFormat) {
        String sign = deltaTicks < 0 ? "-" : "+";
        int abs = Math.abs(deltaTicks);
        return sign + AstUtil.formatTicks(abs, timeFormat);
    }

    private static String segmentName(AstData.CourseFile course, int nextIndex, AstRuntime.State state) {
        if (state == AstRuntime.State.FINISHED) return "Finished";
        if (state == AstRuntime.State.IDLE) return "Idle";
        AstData.Segment seg = AstUtil.findSegment(course, nextIndex);
        if (seg == null) return "(no segment)";
        return seg.name == null ? "" : seg.name;
    }

    private static int currentSegmentTicks(AstRuntime rt) {
        return Math.max(0, rt.getElapsedTicks() - rt.getLastSplitCumulative());
    }

    private static final class DerivedStats {
        final String pbStr;
        final String sumOfBestStr;
        final String bestPossibleStr;
        final String bestSegStr;
        final String bestSplitStr;

        private DerivedStats(String pbStr, String sob, String bpt, String bestSegStr, String bestSplitStr) {
            this.pbStr = pbStr;
            this.sumOfBestStr = sob;
            this.bestPossibleStr = bpt;
            this.bestSegStr = bestSegStr;
            this.bestSplitStr = bestSplitStr;
        }

        static DerivedStats from(AstData.CourseFile course, AstRuntime rt) {
            String tf = course.hud.timeFormat;

            String pb = course.stats.pb != null && course.stats.pb.totalTicks != null
                    ? AstUtil.formatTicks(course.stats.pb.totalTicks, tf)
                    : "--";

            String sob = "--";
            Integer sobTicks = sumIfComplete(course.stats.bestSegmentsTicks);
            if (sobTicks != null) sob = AstUtil.formatTicks(sobTicks, tf);

            String bpt = "--";
            Integer bptTicks = bestPossibleTicks(course, rt);
            if (bptTicks != null) bpt = AstUtil.formatTicks(bptTicks, tf);

            String bestSeg = "--";
            Integer bestSegTicks = bestSegAtNext(course, rt);
            if (bestSegTicks != null) bestSeg = AstUtil.formatTicks(bestSegTicks, tf);

            String bestSplit = "--";
            Integer bestSplitTicks = bestSplitAtLastCompleted(course, rt);
            if (bestSplitTicks != null) bestSplit = AstUtil.formatTicks(bestSplitTicks, tf);

            return new DerivedStats(pb, sob, bpt, bestSeg, bestSplit);
        }

        private static Integer sumIfComplete(List<Integer> segTicks) {
            if (segTicks == null || segTicks.isEmpty()) return null;
            int s = 0;
            for (Integer t : segTicks) {
                if (t == null) return null;
                s += t;
            }
            return s;
        }

        private static Integer bestPossibleTicks(AstData.CourseFile course, AstRuntime rt) {
            List<Integer> best = course.stats.bestSegmentsTicks;
            if (best == null || best.isEmpty()) return null;
            List<Integer> order = AstUtil.sortedNonStartIndices(course);
            if (order.isEmpty()) return null;

            // Livesplit-style BPT:
            //  - Use time up to last completed split (stable)
            //  - Add best possible remainder (finish current seg in its best, plus future best segs)
            int base = rt.getLastSplitCumulative();
            int next = rt.getNextIndex();
            int pos = indexOf(order, next);
            if (pos < 0) {
                // If nextIndex is unknown, fall back to SOB.
                return sumIfComplete(best);
            }

            // current segment time so far (0 if none yet)
            int curSoFar = Math.max(0, rt.getElapsedTicks() - base);
            Integer bestCur = (pos < best.size()) ? best.get(pos) : null;
            if (bestCur == null) return null;
            int sum = rt.getElapsedTicks() + Math.max(0, bestCur - curSoFar);

            // future segments
            for (int i = pos + 1; i < best.size(); i++) {
                Integer t = best.get(i);
                if (t == null) return null;
                sum += t;
            }
            return sum;
        }

        private static Integer bestSegAtNext(AstData.CourseFile course, AstRuntime rt) {
            List<Integer> best = course.stats.bestSegmentsTicks;
            if (best == null || best.isEmpty()) return null;
            int next = rt.getNextIndex();
            List<Integer> order = AstUtil.sortedNonStartIndices(course);
            int pos = indexOf(order, next);
            if (pos < 0 || pos >= best.size()) return null;
            return best.get(pos);
        }

        private static Integer bestSplitAtLastCompleted(AstData.CourseFile course, AstRuntime rt) {
            List<Integer> best = course.stats.bestSplitTicks;
            if (best == null || best.isEmpty()) return null;
            List<Integer> order = AstUtil.sortedNonStartIndices(course);
            int last;
            if (rt.getState() == AstRuntime.State.FINISHED) {
                last = order.size() - 1;
            } else {
                int pos = indexOf(order, rt.getNextIndex());
                last = pos - 1;
            }
            if (last < 0 || last >= best.size()) return null;
            return best.get(last);
        }
    }
}
