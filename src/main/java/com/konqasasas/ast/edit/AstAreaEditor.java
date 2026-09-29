package com.konqasasas.ast.edit;

import com.konqasasas.ast.core.AstCourseManager;
import com.konqasasas.ast.core.AstData;
import com.konqasasas.ast.core.AstRuntime;
import com.konqasasas.ast.core.AstUtil;
import com.konqasasas.ast.viz.AstVizRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.List;

/** Direct, in-world editor for On Ground cells and AABB corners. */
public final class AstAreaEditor {
    private static final AstAreaEditor INSTANCE = new AstAreaEditor();
    private static final double Y_EPSILON = 0.0001;

    public static AstAreaEditor get() {
        return INSTANCE;
    }

    private boolean active;
    private boolean worldInputReady;
    private int segmentIndex;
    private AstData.AabbDto originalAabb;
    private List<AstData.GroundCell> originalGroundCells;
    private double originalHeight;
    private BlockPos firstAabbCorner;
    private double aabbBaseY;
    private double aabbHeight = 1.0;
    private boolean groundBrushActive;
    private int groundBrushButton = -1;
    private boolean groundBrushChanged;
    private String lastFeedback = "";
    private long lastFeedbackAt;

    private AstAreaEditor() {}

    public boolean isActive() {
        return active;
    }

    public void begin(int index) {
        if (Minecraft.getMinecraft().player == null || Minecraft.getMinecraft().world == null) {
            throw new IllegalStateException("ワールドに入ってから範囲を編集してください");
        }
        AstData.Segment segment = AstUtil.findSegment(requireCourse(), index);
        if (segment == null || !Boolean.TRUE.equals(segment.placed)) {
            throw new IllegalArgumentException("先に地点を現在地へ設定してください");
        }

        active = true;
        worldInputReady = false;
        segmentIndex = index;
        originalAabb = copy(segment.aabb);
        originalGroundCells = copyCells(segment.groundCells);
        originalHeight = segment.height;
        firstAabbCorner = null;
        groundBrushActive = false;
        groundBrushButton = -1;
        groundBrushChanged = false;
        if (segment.aabb != null) {
            aabbBaseY = segment.aabb.minY;
            aabbHeight = Math.max(0.00001, segment.aabb.maxY - segment.aabb.minY);
        } else {
            aabbBaseY = Minecraft.getMinecraft().player.posY;
            aabbHeight = Math.max(0.00001, segment.height);
        }
        AstCourseManager.chat("範囲編集: " + roleName(segment) + " / " + segment.name);
        if ("ground".equals(segment.detection)) {
            AstCourseManager.chat("左ボタンで追加 / 右ボタンで削除");
            AstCourseManager.chat("複数ブロックはボタンを押したまま上面をなぞってください");
        } else {
            AstCourseManager.chat("左クリックで底面の2点を指定 / 右クリック: 底面を1ブロックに設定");
            AstCourseManager.chat("高さはコース編集画面で変更できます");
        }
        AstRuntime.get().forceResetToIdle();
    }

    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        if (!active || (event.getButton() != 0 && event.getButton() != 1)) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null) return;

        AstData.Segment segment = currentSegment();
        if (segment == null) {
            cancelIncomplete();
            return;
        }

        if ("ground".equals(segment.detection)) {
            event.setCanceled(true);
            if (event.isButtonstate()) {
                groundBrushActive = true;
                groundBrushButton = event.getButton();
                paintGroundUnderCrosshair();
            } else if (groundBrushActive && event.getButton() == groundBrushButton) {
                finishGroundBrush();
            }
            return;
        }

        if (!event.isButtonstate()) return;
        event.setCanceled(true);
        RayTraceResult hit = mc.objectMouseOver;
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK) {
            feedback("ブロックを狙って操作してください");
            return;
        }
        editAabb(segment, hit, event.getButton() == 0);
        AstRuntime.get().forceResetToIdle();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (!active || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null) {
            cancelIncomplete();
            return;
        }
        if (mc.currentScreen != null) {
            if (worldInputReady) cancelIncomplete();
            return;
        }
        worldInputReady = true;

        AstData.Segment segment = currentSegment();
        if (segment != null && "ground".equals(segment.detection) && groundBrushActive) {
            if (!Mouse.isButtonDown(groundBrushButton)) {
                finishGroundBrush();
            } else {
                paintGroundUnderCrosshair();
            }
        }
    }

    private void paintGroundUnderCrosshair() {
        AstData.Segment segment = currentSegment();
        RayTraceResult hit = Minecraft.getMinecraft().objectMouseOver;
        if (segment == null || hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK) return;
        editGround(segment, hit, groundBrushButton == 0);
    }

    private void editGround(AstData.Segment segment, RayTraceResult hit, boolean add) {
        if (hit.sideHit != EnumFacing.UP) {
            feedback("ブロックの上面をクリックしてください");
            return;
        }
        BlockPos pos = hit.getBlockPos();
        double surfaceY = hit.hitVec == null ? pos.getY() + 1.0 : hit.hitVec.y;
        AstData.GroundCell target = new AstData.GroundCell(pos.getX(), surfaceY, pos.getZ());
        if (segment.groundCells == null) segment.groundCells = new ArrayList<>();
        int existing = findCell(segment.groundCells, target);

        if (add) {
            if (existing >= 0) return;
            if (!segment.groundCells.isEmpty() && !isAdjacent(segment.groundCells, target)) {
                feedback("選択済みの範囲に隣接するブロックを選んでください");
                return;
            }
            segment.groundCells.add(target);
            updateAabbFromGround(segment);
            groundBrushChanged = true;
            AstVizRenderer.invalidateGeometry();
        } else {
            if (existing < 0) return;
            if (segment.groundCells.size() <= 1) {
                feedback("判定範囲には1ブロック以上必要です");
                return;
            }
            segment.groundCells.remove(existing);
            updateAabbFromGround(segment);
            groundBrushChanged = true;
            AstVizRenderer.invalidateGeometry();
        }
    }

    private void finishGroundBrush() {
        if (!active) return;
        groundBrushActive = false;
        groundBrushButton = -1;
        if (groundBrushChanged) {
            saveImmediate();
        }
        active = false;
        groundBrushChanged = false;
        AstCourseManager.chat("範囲編集を終了しました");
    }

    private void editAabb(AstData.Segment segment, RayTraceResult hit, boolean setCorner) {
        if (hit.sideHit != EnumFacing.UP) {
            feedback("底面にするブロックの上面をクリックしてください");
            return;
        }
        BlockPos target = hit.getBlockPos();
        double surfaceY = hit.hitVec == null ? target.getY() + 1.0 : hit.hitVec.y;
        if (!setCorner) {
            firstAabbCorner = null;
            aabbBaseY = surfaceY;
            applyAabbFootprint(segment, target, target, aabbBaseY, aabbHeight);
            finishAabb("底面を1ブロックに設定し、範囲編集を終了しました");
            return;
        }
        if (firstAabbCorner == null) {
            firstAabbCorner = target;
            aabbBaseY = surfaceY;
            feedback("底面の1点目を設定しました。反対側をクリックしてください");
        } else {
            applyAabbFootprint(segment, firstAabbCorner, target, aabbBaseY, aabbHeight);
            firstAabbCorner = null;
            finishAabb("底面を設定し、範囲編集を終了しました");
        }
    }

    private void saveImmediate() {
        AstCourseManager.get().saveActiveCourseSafe();
        AstRuntime.get().forceResetToIdle();
    }

    private void finishAabb(String message) {
        saveImmediate();
        active = false;
        firstAabbCorner = null;
        AstCourseManager.chat(message);
    }

    private void cancelIncomplete() {
        if (!active) return;
        AstData.Segment segment = currentSegment();
        if (segment != null) {
            segment.aabb = copy(originalAabb);
            segment.groundCells = copyCells(originalGroundCells);
            segment.height = originalHeight;
        }
        active = false;
        firstAabbCorner = null;
        groundBrushActive = false;
        groundBrushButton = -1;
        groundBrushChanged = false;
        AstRuntime.get().forceResetToIdle();
        AstCourseManager.chat("未完成の範囲編集を中止しました");
    }

    /** Stop an in-world edit before changing or leaving the active course. */
    public void cancelForCourseChange() {
        if (active) cancelIncomplete();
    }

    /** Segment being edited. Used by the world renderer to emphasize only that range. */
    public int getSegmentIndex() {
        return segmentIndex;
    }

    private AstData.Segment currentSegment() {
        AstData.CourseFile course = AstCourseManager.get().getActiveCourse();
        return course == null ? null : AstUtil.findSegment(course, segmentIndex);
    }

    private static AstData.CourseFile requireCourse() {
        AstData.CourseFile course = AstCourseManager.get().getActiveCourse();
        if (course == null) throw new IllegalStateException("コースが選択されていません");
        return course;
    }

    private static int findCell(List<AstData.GroundCell> cells, AstData.GroundCell target) {
        for (int i = 0; i < cells.size(); i++) {
            AstData.GroundCell cell = cells.get(i);
            if (cell.x == target.x && cell.z == target.z && Math.abs(cell.y - target.y) < Y_EPSILON) return i;
        }
        return -1;
    }

    private static boolean isAdjacent(List<AstData.GroundCell> cells, AstData.GroundCell target) {
        for (AstData.GroundCell cell : cells) {
            if (Math.abs(cell.x - target.x) + Math.abs(cell.z - target.z) == 1
                    && Math.abs(cell.y - target.y) <= 1.001) return true;
        }
        return false;
    }

    private static void updateAabbFromGround(AstData.Segment segment) {
        if (segment.groundCells == null || segment.groundCells.isEmpty()) return;
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (AstData.GroundCell cell : segment.groundCells) {
            minX = Math.min(minX, cell.x);
            minY = Math.min(minY, cell.y);
            minZ = Math.min(minZ, cell.z);
            maxX = Math.max(maxX, cell.x + 1.0);
            maxY = Math.max(maxY, cell.y + 1.0);
            maxZ = Math.max(maxZ, cell.z + 1.0);
        }
        segment.aabb = new AstData.AabbDto(minX, minY, minZ, maxX, maxY, maxZ);
        segment.height = Math.max(0.00001, maxY - minY);
    }

    private static void applyAabbFootprint(AstData.Segment segment, BlockPos a, BlockPos b,
                                           double baseY, double height) {
        AxisAlignedBB box = footprintAabb(a, b, baseY, height);
        segment.aabb = new AstData.AabbDto(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
        segment.height = box.maxY - box.minY;
    }

    private static AxisAlignedBB footprintAabb(BlockPos a, BlockPos b, double baseY, double height) {
        double minX = Math.min(a.getX(), b.getX());
        double minZ = Math.min(a.getZ(), b.getZ());
        double maxX = Math.max(a.getX(), b.getX()) + 1.0;
        double maxZ = Math.max(a.getZ(), b.getZ()) + 1.0;
        return new AxisAlignedBB(minX, baseY, minZ, maxX, baseY + Math.max(0.00001, height), maxZ);
    }

    private static AstData.AabbDto copy(AstData.AabbDto value) {
        return value == null ? null : new AstData.AabbDto(
                value.minX, value.minY, value.minZ, value.maxX, value.maxY, value.maxZ);
    }

    private static List<AstData.GroundCell> copyCells(List<AstData.GroundCell> values) {
        List<AstData.GroundCell> result = new ArrayList<>();
        if (values != null) {
            for (AstData.GroundCell value : values) {
                if (value != null) result.add(new AstData.GroundCell(value.x, value.y, value.z));
            }
        }
        return result;
    }

    private static String roleName(AstData.Segment segment) {
        AstData.CourseFile course = AstCourseManager.get().getActiveCourse();
        int max = segment.index;
        if (course != null) for (AstData.Segment item : course.segments) max = Math.max(max, item.index);
        if (segment.index == 0) return "START";
        if (segment.index == max) return "GOAL";
        return "LAP";
    }

    private void feedback(String message) {
        long now = System.currentTimeMillis();
        if (message.equals(lastFeedback) && now - lastFeedbackAt < 1000L) return;
        lastFeedback = message;
        lastFeedbackAt = now;
        AstCourseManager.chat(message);
    }
}
