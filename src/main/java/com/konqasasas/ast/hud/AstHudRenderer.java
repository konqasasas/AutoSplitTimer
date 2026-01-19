package com.konqasasas.ast.hud;

import com.konqasasas.ast.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.*;

/**
 * Minimal Livesplit-like HUD.
 */
public class AstHudRenderer {

    // Simple "dirty" flag so commands/GUI can force immediate refresh if we later add caching.
    private static volatile boolean DIRTY = false;

    public static void requestRebuild() {
        DIRTY = true;
    }

    @SubscribeEvent
    public void onRenderText(RenderGameOverlayEvent.Text e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null) return;

        // When the HUD editor (or related layout screens) is open, do not draw the HUD behind it.
        if (mc.currentScreen instanceof GuiAstHudEditor
                || mc.currentScreen instanceof GuiAstLayoutList
                || mc.currentScreen instanceof GuiAstLayoutSave) {
            return;
        }

        AstData.CourseFile course = AstCourseManager.get().getActiveCourse();
        if (course == null || course.hud == null) return;

        // Allow turning HUD off by setting preset = "off"
        if ("off".equalsIgnoreCase(course.hud.preset)) return;

        renderHudAt(mc, course, course.hud, course.hud.offsetX, course.hud.offsetY,
                course.hud.scale <= 0 ? 1.0 : course.hud.scale);
    }

    /**
     * Render a HUD preview into an arbitrary screen-space rectangle. Used by GUI editors.
     * The preview uses the currently active course + runtime state.
     */
    public static void renderPreview(int x, int y, double scale) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null || mc.player == null) return;
        AstData.CourseFile course = AstCourseManager.get().getActiveCourse();
        if (course == null || course.hud == null) return;
        // Note: we intentionally do NOT check currentScreen here.
        renderHudAt(mc, course, course.hud, x, y, scale);
    }

    private static void renderHudAt(Minecraft mc, AstData.CourseFile course, AstData.HudConfig hud,
                                    int baseX, int baseY, double scale) {
        if (hud == null) return;
        FontRenderer fr = mc.fontRenderer;

        // We render using MC formatting codes (\u00a7x) so per-part colors are configurable.
        int baseColor = 0xFFFFFF;

        AstRuntime rt = AstRuntime.get();
        AstRuntime.State state = rt.getState();

        // Touch DIRTY so rebuild requests are observed even if we add caching later.
        if (DIRTY) DIRTY = false;

        String curTime = AstUtil.formatTicks(rt.getElapsedTicks(), hud.timeFormat);
        String segName = segmentName(course, rt.getNextIndex(), state);
        // When FINISHED, the "current segment" is conceptually the last completed segment.
        // elapsedTicks == lastSplitCumulative at GOAL, so elapsed-lastSplit would be 0.
        Integer prevSegTicks = rt.getLastCompletedSegmentTicks();
        int segTicks = (state == AstRuntime.State.FINISHED) ? (prevSegTicks == null ? 0 : prevSegTicks.intValue()) : currentSegmentTicks(rt);
        String segTime = AstUtil.formatTicks(segTicks, hud.timeFormat);
        DerivedStats ds = DerivedStats.from(course, rt);
        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, 1.0);

        int x = (int) (baseX / scale);
        int y = (int) (baseY / scale);

        List<String> order = (hud.itemOrder == null || hud.itemOrder.isEmpty())
                ? Arrays.asList("courseName","time","segment","segmentTime","prevSeg","sob","bpt","bestSeg","bestSplit","attempt","splitList")
                : hud.itemOrder;

        for (String key : order) {
            if (key == null) continue;
            switch (key) {
                case "courseName":
                    if (!isOn(hud, "courseName")) break;
                    draw(fr, cLabel(hud) + "Course: " + cSub(hud) + safe(course.courseName), x, y, baseColor);
                    y += fr.FONT_HEIGHT + Math.max(0, hud.splitListLineGap);
                    break;
                case "time":
                    draw(fr, cLabel(hud) + "Time: " + cMain(hud) + curTime, x, y, baseColor);
                    y += fr.FONT_HEIGHT + Math.max(0, hud.splitListLineGap);
                    break;
                case "segment":
                    draw(fr, cLabel(hud) + "Seg: " + cSub(hud) + segName, x, y, baseColor);
                    y += fr.FONT_HEIGHT + Math.max(0, hud.splitListLineGap);
                    break;
                case "segmentTime":
                    draw(fr, cLabel(hud) + "SegTime: " + cMain(hud) + segTime, x, y, baseColor);
                    y += fr.FONT_HEIGHT + Math.max(0, hud.splitListLineGap);
                    break;
                case "prevSeg":
                    if (!isOn(hud, "prevSeg")) break;
                    String prevStr = prevSegTicks == null ? "--" : AstUtil.formatTicks(prevSegTicks, hud.timeFormat);
                    draw(fr, cLabel(hud) + "Prev: " + cSub(hud) + prevStr, x, y, baseColor);
                    y += fr.FONT_HEIGHT + Math.max(0, hud.splitListLineGap);
                    break;
                case "sob":
                    if (!isOn(hud, "sob")) break;
                    draw(fr, cLabel(hud) + "SoB: " + cSub(hud) + ds.sumOfBestStr, x, y, baseColor);
                    y += fr.FONT_HEIGHT + Math.max(0, hud.splitListLineGap);
                    break;
                case "bpt":
                    if (!isOn(hud, "bpt")) break;
                    draw(fr, cLabel(hud) + "BPT: " + cSub(hud) + ds.bestPossibleStr, x, y, baseColor);
                    y += fr.FONT_HEIGHT + Math.max(0, hud.splitListLineGap);
                    break;
                case "bestSeg":
                    if (!isOn(hud, "bestSeg")) break;
                    draw(fr, cLabel(hud) + "BestSeg: " + cSub(hud) + ds.bestSegStr, x, y, baseColor);
                    y += fr.FONT_HEIGHT + Math.max(0, hud.splitListLineGap);
                    break;
                case "bestSplit":
                    if (!isOn(hud, "bestSplit")) break;
                    draw(fr, cLabel(hud) + "BestSplit: " + cSub(hud) + ds.bestSplitStr, x, y, baseColor);
                    y += fr.FONT_HEIGHT + Math.max(0, hud.splitListLineGap);
                    break;
                case "attempt":
                    if (!isOn(hud, "attempt")) break;
                    draw(fr, cLabel(hud) + "Attempts: " + cSub(hud) + course.stats.attemptCount, x, y, baseColor);
                    y += fr.FONT_HEIGHT + Math.max(0, hud.splitListLineGap);
                    break;
                case "splitList":
                    if (!isOn(hud, "splitList")) break;
                    y += 2;
                    draw(fr, cLabel(hud) + "Splits:", x, y, baseColor);
                    y += fr.FONT_HEIGHT + Math.max(0, hud.splitListLineGap);
                    y = drawSplitList(fr, course, rt, x, y, baseColor);
                    break;
                default:
                    break;
            }
        }

        GlStateManager.popMatrix();
    }

    private static boolean isOn(AstData.HudConfig hud, String key) {
        if (hud == null || hud.toggles == null) return true;
        Boolean v = hud.toggles.get(key);
        return v == null || v;
    }

    private static void draw(FontRenderer fr, String s, int x, int y, int color) {
        fr.drawStringWithShadow(s, x, y, color);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String ellipsize(FontRenderer fr, String s, int maxWidth) {
        if (s == null) return "";
        if (maxWidth <= 0) return "";
        if (fr.getStringWidth(s) <= maxWidth) return s;
        String ell = "...";
        int ellW = fr.getStringWidth(ell);
        if (ellW >= maxWidth) return ell;
        String t = s;
        while (!t.isEmpty() && fr.getStringWidth(t) + ellW > maxWidth) {
            t = t.substring(0, t.length() - 1);
        }
        return t + ell;
    }

    private static String cLabel(AstData.HudConfig hud) { return "\u00a7" + safeColor(hud != null ? hud.colorLabel : null, "7"); }
    private static String cMain(AstData.HudConfig hud) { return "\u00a7" + safeColor(hud != null ? hud.colorMainText : null, "f"); }
    private static String cSub(AstData.HudConfig hud) { return "\u00a7" + safeColor(hud != null ? hud.colorSubText : null, "8"); }
    private static String cGood(AstData.HudConfig hud) { return "\u00a7" + safeColor(hud != null ? hud.colorGood : null, "a"); }
    private static String cBad(AstData.HudConfig hud) { return "\u00a7" + safeColor(hud != null ? hud.colorBad : null, "c"); }
    private static String cGold(AstData.HudConfig hud) { return "\u00a7" + safeColor(hud != null ? hud.colorGold : null, "6"); }

    private static String safeColor(String s, String def) {
        if (s == null || s.trim().isEmpty()) return def;
        s = s.trim().toLowerCase(Locale.ROOT);
        if (s.length() == 1) return s;
        if (s.startsWith("\u00a7") && s.length() >= 2) return s.substring(1,2);
        return def;
    }

    private static int drawSplitList(FontRenderer fr, AstData.CourseFile course, AstRuntime rt, int x, int y, int baseColor) {
        AstData.HudConfig hud = course.hud;
        // colgap: label-left -> primary-left. Clamp 0..80, but ensure at least "..." fits.
        int gap = hud.splitListGap;
        if (gap < 0) gap = 0;
        if (gap > 80) gap = 80;
        int ellW = fr.getStringWidth("...");
        if (gap < ellW) gap = ellW;
        int primW = Math.max(10, hud.splitPrimaryWidth);
        int secW = Math.max(10, hud.splitSecondaryWidth);

        // total width is derived from colgap (gap) + fixed column widths.
        int totalW = gap + primW + gap + secW;

        List<Integer> order = AstUtil.sortedNonStartIndices(course);
        int n = Math.max(0, hud.splitListCount);
        if (n == 0 || order.isEmpty()) {
            draw(fr, cSub(hud) + "(none)", x, y, baseColor);
            return y + fr.FONT_HEIGHT;
        }

        int nextIndex = rt.getNextIndex();

        // Visible window (rows=n): ALWAYS end with GOAL, and slide downward near the end.
        // Example (rows=4): N-3,N-2,N-1,N (including FINISHED).
        List<Integer> show = new ArrayList<>();
        int goalIdx = order.get(order.size() - 1);
        int slots = Math.max(1, n);
        if (slots == 1) {
            show.add(goalIdx);
        } else {
            int nonGoalSlots = slots - 1;
            int lenNonGoal = Math.max(0, order.size() - 1);
            int maxStart = Math.max(0, lenNonGoal - nonGoalSlots);

            int activePos = indexOf(order, nextIndex);
            if (activePos < 0) activePos = 0;
            // Treat GOAL as the last non-goal position so the window pins to the end.
            if (activePos >= lenNonGoal) activePos = Math.max(0, lenNonGoal - 1);
            if (rt.getState() == AstRuntime.State.FINISHED) {
                activePos = Math.max(0, lenNonGoal - 1);
            }

            int start = activePos - (nonGoalSlots - 1);
            if (start < 0) start = 0;
            if (start > maxStart) start = maxStart;

            for (int i = start; i < start + nonGoalSlots && i < lenNonGoal; i++) {
                show.add(order.get(i));
            }
            show.add(goalIdx);
        }

        List<Integer> pbSeg = rt.getBaselinePbSegOrNull();
        List<Integer> pbSplit = rt.getBaselinePbSplitOrNull();
        List<Integer> bestSeg = rt.getBaselineBestSegOrNull();
        List<Integer> bestSplit = rt.getBaselineBestSplitOrNull();
        if (pbSeg == null) pbSeg = (course.stats.pb != null) ? course.stats.pb.segmentTicks : null;
        if (pbSplit == null) pbSplit = buildPbSplit(pbSeg);
        if (bestSeg == null) bestSeg = course.stats.bestSegmentsTicks;
        if (bestSplit == null) bestSplit = course.stats.bestSplitTicks;

        Map<Integer, Integer> runSeg = rt.getRunSegmentTicks();
        Map<Integer, Integer> runSplit = rt.getRunSplitCumulative();
        Set<Integer> goldSeg = rt.getGoldSegmentsThisRun();
        Set<Integer> goldSplitSet = rt.getGoldSplitsThisRun();

        int elapsed = rt.getElapsedTicks();
        int lastSplitCum = rt.getLastSplitCumulative();
        int curSegTicks = Math.max(0, elapsed - lastSplitCum);

        // Livesplit-style settings
        String cmp = (hud.comparison == null ? "pb" : hud.comparison.toLowerCase(Locale.ROOT));
        String unit = (hud.unit == null ? "split" : hud.unit.toLowerCase(Locale.ROOT));

        int nextPos = indexOf(order, nextIndex);
        if (nextPos < 0) nextPos = 0;

        for (int idx : show) {
            int p = indexOf(order, idx);
            AstData.Segment seg = AstUtil.findSegment(course, idx);
            String name = (seg != null && seg.name != null) ? seg.name : ("#" + idx);

            SegmentState ss = calcState(rt, idx, nextIndex, p, nextPos);
            boolean highlight = (rt.getState() == AstRuntime.State.FINISHED && idx == goalIdx);
            if (rt.getState() == AstRuntime.State.FINISHED) ss = SegmentState.PAST;

            // Comparison baseline for this row (shown before passing; used for delta).
            Integer base;
            if ("best".equals(cmp)) {
                base = "seg".equals(unit)
                        ? (bestSeg != null && p >= 0 && p < bestSeg.size() ? bestSeg.get(p) : null)
                        : (bestSplit != null && p >= 0 && p < bestSplit.size() ? bestSplit.get(p) : null);
            } else {
                base = "seg".equals(unit)
                        ? (pbSeg != null && p >= 0 && p < pbSeg.size() ? pbSeg.get(p) : null)
                        : (pbSplit != null && p >= 0 && p < pbSplit.size() ? pbSplit.get(p) : null);
            }

            // Actual time at pass (only meaningful for PAST rows)
            Integer actual = null;
            if (ss == SegmentState.PAST) {
                actual = "seg".equals(unit) ? runSeg.get(idx) : runSplit.get(idx);
            }

            // Secondary is always displayed:
            //  - before pass: baseline
            //  - after pass: actual
            String secondaryText;
            String secondaryColor = (ss == SegmentState.ACTIVE || highlight) ? cMain(hud) : cSub(hud);
            if (ss == SegmentState.PAST) {
                secondaryText = (actual == null) ? "--" : AstUtil.formatTicks(actual, hud.timeFormat);
                secondaryColor = (highlight ? cMain(hud) : cSub(hud));
            } else {
                secondaryText = (base == null) ? "--" : AstUtil.formatTicks(base, hud.timeFormat);
            }

            // Primary:
            //  - before pass: empty
            //  - after pass: delta vs baseline
            String primaryText = "";
            String primaryColor = (ss == SegmentState.ACTIVE || highlight) ? cMain(hud) : cSub(hud);
            if (ss == SegmentState.PAST && actual != null && base != null) {
                int d = actual - base;
                primaryText = formatDelta(d, hud.timeFormat);
                // Semantic colors ignore theme/preset.
                boolean gold = (actual < base); // strict; 0 is NOT gold
                if (gold) primaryColor = "\u00a7" + hud.colorGold;
                else if (d <= 0) primaryColor = "\u00a7" + hud.colorGood;
                else primaryColor = "\u00a7" + hud.colorBad;
            }

            // layout (left-based):
            //   labelX = x
            //   primaryX = x + gap
            //   secondaryX = primaryX + primW + gap
            int labelX = x;
            // When colgap=0, primary starts immediately after "..." (no empty space).
            int minEll = fr.getStringWidth("...");
            int primaryX = x + minEll + gap;
            int secondaryX = primaryX + primW + gap;

            // Label: only ellipsize when it would overlap primary (visual priority).
            int labelMax = primaryX - labelX;
            String labelShown = (fr.getStringWidth(name) <= labelMax)
                    ? name
                    : ellipsize(fr, name, Math.max(0, labelMax));
            if (labelShown == null) labelShown = "";

            String primDraw = primaryColor + primaryText;
            int primWpx = fr.getStringWidth(primDraw);
            int primX = primaryX + primW - primWpx; // right-align within fixed column

            draw(fr, cLabel(hud) + labelShown, x, y, baseColor);
            draw(fr, primDraw, primX, y, baseColor);

            String secDraw = secondaryColor + secondaryText;
            int secWpx = fr.getStringWidth(secDraw);
            int secX = secondaryX + secW - secWpx; // right-align within fixed column
            draw(fr, secDraw, secX, y, baseColor);
            y += fr.FONT_HEIGHT + Math.max(0, hud.splitListLineGap);
        }

        return y;
    }

    private enum SegmentState { FUTURE, ACTIVE, PAST }

    private static SegmentState calcState(AstRuntime rt, int idx, int nextIndex, int pos, int nextPos) {
        AstRuntime.State st = rt.getState();
        if (st == AstRuntime.State.FINISHED) return SegmentState.PAST;
        if (st != AstRuntime.State.RUNNING) return SegmentState.FUTURE;
        if (idx == nextIndex) return SegmentState.ACTIVE;
        if (pos >= 0 && nextPos >= 0) return (pos < nextPos) ? SegmentState.PAST : SegmentState.FUTURE;
        return SegmentState.FUTURE;
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
            int next = rt.getNextIndex();
            List<Integer> order = AstUtil.sortedNonStartIndices(course);
            int pos = indexOf(order, next);
            int last = pos - 1;
            if (last < 0 || last >= best.size()) return null;
            return best.get(last);
        }
    }
}
