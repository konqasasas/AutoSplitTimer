package com.konqasasas.ast.core;

import net.minecraft.client.Minecraft;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.*;

/**
 * Client-side runtime: ticks, auto start/splits, and in-memory current run state.
 */
public final class AstRuntime {
    public enum State { IDLE, RUNNING, FINISHED }

    private static final AstRuntime INSTANCE = new AstRuntime();
    public static AstRuntime get() { return INSTANCE; }

    // runtime
    private State state = State.IDLE;
    private int elapsedTicks = 0;
    private int nextIndex = Integer.MAX_VALUE;
    private int lastSplitCumulative = 0;
    private Integer lastCompletedSegmentTicks = null;

    // per segment inside tracking (index -> insidePrev)
    private final Map<Integer, Boolean> insidePrev = new HashMap<>();

    // Start latch: while the player remains inside Start, do not re-trigger.
    private boolean startLatched = false;

    // segments already triggered this run (excluding start)
    private final Set<Integer> usedSegments = new HashSet<>();

    // per run result storage by segment index (excluding start, ordered)
    private final Map<Integer, Integer> runSegmentTicks = new HashMap<>(); // null = skipped (absence)
    private final Map<Integer, Integer> runSplitCumulative = new HashMap<>();
    private final Set<Integer> goldSegmentsThisRun = new HashSet<>();
    private final Set<Integer> goldSplitsThisRun = new HashSet<>();

    // Snapshot baselines at attempt start so Δ and gold are stable within the run
    private List<Integer> baselinePbSeg = null;
    private List<Integer> baselinePbSplit = null;
    private List<Integer> baselineBestSeg = null;
    private List<Integer> baselineBestSplit = null;


    private AstRuntime() {}

    public synchronized State getState() { return state; }
    public synchronized int getElapsedTicks() {
        // Inclusive timing: once START triggers, time is treated as already +1 tick.
        // This fixes the "always 1 tick short" user-observed issue.
        if (state == State.RUNNING) return elapsedTicks + 1;
        return elapsedTicks;
    }
    public synchronized Integer getLastCompletedSegmentTicks() { return lastCompletedSegmentTicks; }
    public synchronized int getNextIndex() { return nextIndex; }
    public synchronized int getLastSplitCumulative() { return lastSplitCumulative; }
    public synchronized Set<Integer> getGoldSegmentsThisRun() { return new HashSet<>(goldSegmentsThisRun); }
    public synchronized Set<Integer> getGoldSplitsThisRun() { return new HashSet<>(goldSplitsThisRun); }
    public synchronized Map<Integer, Integer> getRunSegmentTicks() { return new HashMap<>(runSegmentTicks); }
    public synchronized Map<Integer, Integer> getRunSplitCumulative() { return new HashMap<>(runSplitCumulative); }

    /** Split history lines for HUD: list of (index,name,segmentTicks,gold). */
    public synchronized List<AstHudLine> buildSplitHistory(int maxCount) {
        AstData.CourseFile course = AstCourseManager.get().getActiveCourse();
        if (course == null) return Collections.emptyList();
        List<Integer> order = AstUtil.sortedNonStartIndices(course);
        List<AstHudLine> out = new ArrayList<>();
        for (int idx : order) {
            if (idx >= nextIndex) break; // not reached (or skipped?)
            Integer segTicks = runSegmentTicks.get(idx);
            // show only triggered segments (skip doesn't have ticks and wasn't triggered)
            if (segTicks == null) continue;
            AstData.Segment seg = AstUtil.findSegment(course, idx);
            if (seg == null) continue;
            out.add(new AstHudLine(idx, seg.name, segTicks, goldSegmentsThisRun.contains(idx)));
        }
        // keep last N
        if (out.size() > maxCount) {
            out = out.subList(out.size() - maxCount, out.size());
        }
        return out;
    }

    public static final class AstHudLine {
        public final int index;
        public final String name;
        public final int segmentTicks;
        public final boolean gold;
        public AstHudLine(int index, String name, int segmentTicks, boolean gold) {
            this.index = index;
            this.name = name;
            this.segmentTicks = segmentTicks;
            this.gold = gold;
        }
    }

    public synchronized void forceResetToIdle() {
        resetRuntimeOnly();
        // If the player is standing on Start and issues /ast run reset, we must NOT
        // immediately auto-start again. Keep it latched until they leave Start.
        startLatched = true;
        state = State.IDLE;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null) {
            // world unloaded
            resetRuntimeOnly();
            startLatched = false;
            state = State.IDLE;
            return;
        }
        if (mc.isGamePaused()) return;

        AstData.CourseFile course = AstCourseManager.get().getActiveCourse();
        if (course == null) return;
        if (course.segments == null || course.segments.isEmpty()) return;

        // Gather entered indices (OUT->IN)
        Vec3d feet = new Vec3d(mc.player.posX, mc.player.posY, mc.player.posZ);
        List<Integer> entered = new ArrayList<>();

        // Start latch works off "inside now" instead of insidePrev (because reset clears insidePrev).
        boolean startInsideNow = false;
        AstData.Segment startSeg = AstUtil.findSegment(course, 0);
        if (startSeg != null && startSeg.aabb != null) {
            startInsideNow = AstUtil.contains(startSeg.aabb.toAabb(), feet);
        }
        if (!startInsideNow) {
            startLatched = false;
        }

        for (AstData.Segment seg : course.segments) {
            if (seg == null || seg.aabb == null) continue;
            int idx = seg.index;
            if (idx == 0) {
                // handled by start latch above
                continue;
            }
            AxisAlignedBB bb = seg.aabb.toAabb();
            boolean insideNow = AstUtil.contains(bb, feet);
            boolean insideBefore = insidePrev.getOrDefault(idx, false);
            if (!insideBefore && insideNow) {
                entered.add(idx);
            }
            insidePrev.put(idx, insideNow);
        }

        // Start: fire only on first entry until player exits Start.
        if (startInsideNow && !startLatched) {
            startLatched = true;
            startNewAttempt(course);
            return;
        }

        if (!entered.isEmpty()) {
            int hit = Collections.max(entered); // back priority
            if (state == State.RUNNING) {
                // Determine the next indices list to support skipping.
                List<Integer> order = AstUtil.sortedNonStartIndices(course);
                if (order.isEmpty()) {
                    // no non-start segments: treat as nothing
                } else {
                    if (hit >= nextIndex) {
                        // record this split
                        recordSplit(course, hit);

                        // advance nextIndex to the next higher existing index
                        nextIndex = AstUtil.nextExistingIndex(order, hit);

                        // finish check: if hit is the last segment index
                        int lastIdx = order.get(order.size() - 1);
                        if (hit == lastIdx) {
                            finishAndUpdate(course);
                            state = State.FINISHED;
                        }
                    }
                }
            }
        }

        if (state == State.RUNNING) {
            elapsedTicks++;
        }
    }

    private void startNewAttempt(AstData.CourseFile course) {
        // increment attempt count (global)
        course.stats.attemptCount += 1;
        AstCourseManager.get().saveActiveCourseSafe();

        // Snapshot baselines BEFORE any stats are modified by this attempt.
        // These are used for pbseg/pbsplit/bestseg/bestsplit deltas and for gold previews.
        snapshotBaselines(course);

        // reset runtime state
        resetRuntimeOnly(false);
        state = State.RUNNING;
        elapsedTicks = 0;
        lastSplitCumulative = 0;
        lastCompletedSegmentTicks = null;
        usedSegments.clear();
        runSegmentTicks.clear();
        runSplitCumulative.clear();
        goldSegmentsThisRun.clear();
        goldSplitsThisRun.clear();


        List<Integer> order = AstUtil.sortedNonStartIndices(course);
        nextIndex = order.isEmpty() ? Integer.MAX_VALUE : order.get(0);
    }

    private void recordSplit(AstData.CourseFile course, int hitIndex) {
        if (usedSegments.contains(hitIndex)) return;
        usedSegments.add(hitIndex);

        // Inclusive timing (see getElapsedTicks())
        int cumulative = getElapsedTicks();
        int segTicks = cumulative - lastSplitCumulative;
        lastSplitCumulative = cumulative;
        lastCompletedSegmentTicks = segTicks;

        runSegmentTicks.put(hitIndex, segTicks);
        runSplitCumulative.put(hitIndex, cumulative);

        // Gold preview: compare vs stored bests, but DO NOT write to stats unless the run finishes.
        try {
            List<Integer> order = AstUtil.sortedNonStartIndices(course);
            int pos = order.indexOf(hitIndex);
            if (pos >= 0) {
                java.util.List<Integer> bestSegList = (baselineBestSeg != null) ? baselineBestSeg : course.stats.bestSegmentsTicks;
                Integer bestSeg = (bestSegList != null && pos < bestSegList.size()) ? bestSegList.get(pos) : null;
                if (bestSeg != null && segTicks < bestSeg) {
                    goldSegmentsThisRun.add(hitIndex);
                }
                java.util.List<Integer> bestSplitList = (baselineBestSplit != null) ? baselineBestSplit : course.stats.bestSplitTicks;
                Integer bestSplit = (bestSplitList != null && pos < bestSplitList.size()) ? bestSplitList.get(pos) : null;
                if (bestSplit != null && cumulative < bestSplit) {
                    goldSplitsThisRun.add(hitIndex);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void finishAndUpdate(AstData.CourseFile course) {
        // Build ordered arrays aligned to sortedNonStartIndices
        List<Integer> order = AstUtil.sortedNonStartIndices(course);
        if (order.isEmpty()) return;

        // total time is inclusive at finish moment
        int total = getElapsedTicks();
        // freeze elapsedTicks to the finished total (so getters stay stable in FINISHED)
        elapsedTicks = total;

        // segmentTicks list aligned to order; null if segment was not reached (skipped)
        List<Integer> segTicksList = new ArrayList<>();
        List<Integer> splitCumList = new ArrayList<>();
        for (int idx : order) {
            segTicksList.add(runSegmentTicks.get(idx));
            splitCumList.add(runSplitCumulative.get(idx));
        }

        // PB update
        AstData.PbRecord pb = course.stats.pb;
        if (pb.totalTicks == null || total < pb.totalTicks) {
            pb.totalTicks = total;
            pb.segmentTicks = segTicksList;
        }

        // BestSegments update (and gold)
        for (int i = 0; i < order.size(); i++) {
            Integer segTicks = segTicksList.get(i);
            if (segTicks == null) continue; // skipped
            Integer best = course.stats.bestSegmentsTicks.get(i);
            if (best == null || segTicks < best) {
                course.stats.bestSegmentsTicks.set(i, segTicks);
                goldSegmentsThisRun.add(order.get(i));
            }
        }

        // BestSplit update
        for (int i = 0; i < order.size(); i++) {
            Integer cum = splitCumList.get(i);
            if (cum == null) continue;
            Integer best = course.stats.bestSplitTicks.get(i);
            if (best == null || cum < best) {
                course.stats.bestSplitTicks.set(i, cum);
                goldSplitsThisRun.add(order.get(i));
            }
        }

        AstCourseManager.get().saveActiveCourseSafe();
    }



    private void snapshotBaselines(AstData.CourseFile course) {
        try {
            // PB seg / split
            if (course != null && course.stats != null) {
                if (course.stats.pb != null && course.stats.pb.segmentTicks != null) {
                    baselinePbSeg = new ArrayList<>(course.stats.pb.segmentTicks);
                    // Build PB split cumulative (same length)
                    baselinePbSplit = new ArrayList<>(baselinePbSeg.size());
                    int sCum = 0;
                    for (Integer t : baselinePbSeg) {
                        if (t == null) {
                            baselinePbSplit.add(null);
                        } else {
                            sCum += t;
                            baselinePbSplit.add(sCum);
                        }
                    }
                }
                if (course.stats.bestSegmentsTicks != null) {
                    baselineBestSeg = new ArrayList<>(course.stats.bestSegmentsTicks);
                }
                if (course.stats.bestSplitTicks != null) {
                    baselineBestSplit = new ArrayList<>(course.stats.bestSplitTicks);
                }
            }
        } catch (Exception ignored) {
            // If anything fails, we fall back to live stats.
            baselinePbSeg = null;
            baselinePbSplit = null;
            baselineBestSeg = null;
            baselineBestSplit = null;
        }
    }

    public synchronized java.util.List<Integer> getBaselinePbSegOrNull() {
        return baselinePbSeg == null ? null : new ArrayList<>(baselinePbSeg);
    }

    public synchronized java.util.List<Integer> getBaselinePbSplitOrNull() {
        return baselinePbSplit == null ? null : new ArrayList<>(baselinePbSplit);
    }

    public synchronized java.util.List<Integer> getBaselineBestSegOrNull() {
        return baselineBestSeg == null ? null : new ArrayList<>(baselineBestSeg);
    }

    public synchronized java.util.List<Integer> getBaselineBestSplitOrNull() {
        return baselineBestSplit == null ? null : new ArrayList<>(baselineBestSplit);
    }

    private void resetRuntimeOnly() {
        resetRuntimeOnly(true);
    }

    private void resetRuntimeOnly(boolean clearBaselines) {
        elapsedTicks = 0;
        nextIndex = Integer.MAX_VALUE;
        lastSplitCumulative = 0;
        lastCompletedSegmentTicks = null;
        insidePrev.clear();
        // IMPORTANT: do NOT reset startLatched here.
        // startLatched must be released only when the player actually leaves the Start region,
        // otherwise staying on Start would re-trigger every tick.
        usedSegments.clear();
        runSegmentTicks.clear();
        runSplitCumulative.clear();
        goldSegmentsThisRun.clear();
        goldSplitsThisRun.clear();
        if (clearBaselines) {
            baselinePbSeg = null;
            baselinePbSplit = null;
            baselineBestSeg = null;
            baselineBestSplit = null;
        }

    }
}
