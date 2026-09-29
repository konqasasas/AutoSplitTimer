package com.konqasasas.ast.edit;

import com.konqasasas.ast.core.AstCourseManager;
import com.konqasasas.ast.core.AstData;
import com.konqasasas.ast.core.AstRuntime;
import com.konqasasas.ast.hud.AstHudConfigUtil;
import com.konqasasas.ast.viz.AstVizRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Two-click setup for an in-memory START/GOAL course. */
public final class AstQuickCourseEditor {
    private static final AstQuickCourseEditor INSTANCE = new AstQuickCourseEditor();

    public static AstQuickCourseEditor get() {
        return INSTANCE;
    }

    private boolean active;
    private boolean worldInputReady;
    private String lastFeedback = "";
    private long lastFeedbackAt;

    private AstQuickCourseEditor() {}

    public boolean isActive() {
        return active;
    }

    /** Stop intercepting world input when the active course is cleared. */
    public void stopEditing() {
        active = false;
        worldInputReady = false;
    }

    public void begin() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) {
            throw new IllegalStateException("ワールドに入ってから簡易計測を開始してください");
        }

        AstCourseManager manager = AstCourseManager.get();
        AstData.CourseFile course = new AstData.CourseFile();
        course.courseName = "簡易計測";
        course.hud = AstHudConfigUtil.copyHud(manager.getHudConfig());
        course.segments.add(segment(0, "スタート", true));
        course.segments.add(segment(1, "ゴール", false));
        course.stats.bestSegmentsTicks.add(null);
        course.stats.bestSplitTicks.add(null);
        course.stats.pb.segmentTicks.add(null);
        placeAtPlayer(course.segments.get(0), mc.player);

        manager.activateTemporaryCourse(course);
        AstRuntime.get().forceResetToIdle();
        active = true;
        worldInputReady = false;
        AstCourseManager.chat("簡易計測設定");
        AstCourseManager.chat("STARTは現在地です。GOALの上面を右クリックしてください");
        AstCourseManager.chat("左クリック: START変更");
    }

    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        if (!active || !event.isButtonstate() || (event.getButton() != 0 && event.getButton() != 1)) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null) return;
        event.setCanceled(true);

        RayTraceResult hit = mc.objectMouseOver;
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK || hit.sideHit != EnumFacing.UP) {
            feedback("ブロックの上面をクリックしてください");
            return;
        }
        AstData.CourseFile course = AstCourseManager.get().getActiveCourse();
        if (course == null || course.segments.size() < 2) return;

        AstData.Segment target = course.segments.get(event.getButton() == 0 ? 0 : 1);
        placeOnSurface(target, hit);
        AstRuntime.get().forceResetToIdle();
        if (event.getButton() == 0) {
            feedback("STARTを変更しました");
        } else {
            active = false;
            AstCourseManager.chat("GOALを設定しました。STARTへ戻るとタイマーが始まります");
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (AstCourseManager.get().isTemporaryCourseActive() && mc.world == null) {
            active = false;
            AstCourseManager.get().clearTemporaryCourse();
            AstRuntime.get().forceResetToIdle();
            return;
        }
        if (!active) return;
        if (mc.currentScreen == null) {
            worldInputReady = true;
        } else if (worldInputReady) {
            active = false;
            AstCourseManager.get().clearTemporaryCourse();
            AstRuntime.get().forceResetToIdle();
            AstCourseManager.chat("簡易計測の設定を中止しました");
        }
    }

    private static AstData.Segment segment(int index, String name, boolean placed) {
        AstData.Segment segment = new AstData.Segment();
        segment.index = index;
        segment.name = name;
        segment.detection = "ground";
        segment.height = 1.0;
        segment.placed = placed;
        segment.aabb = new AstData.AabbDto(0, 0, 0, 1, 1, 1);
        return segment;
    }

    private static void placeAtPlayer(AstData.Segment segment, EntityPlayerSP player) {
        int x = MathHelper.floor(player.posX);
        int z = MathHelper.floor(player.posZ);
        setGroundCell(segment, x, player.posY, z);
    }

    private static void placeOnSurface(AstData.Segment segment, RayTraceResult hit) {
        BlockPos pos = hit.getBlockPos();
        double y = hit.hitVec == null ? pos.getY() + 1.0 : hit.hitVec.y;
        setGroundCell(segment, pos.getX(), y, pos.getZ());
    }

    private static void setGroundCell(AstData.Segment segment, int x, double y, int z) {
        segment.placed = Boolean.TRUE;
        segment.height = 1.0;
        segment.groundCells.clear();
        segment.groundCells.add(new AstData.GroundCell(x, y, z));
        segment.aabb = new AstData.AabbDto(x, y, z, x + 1.0, y + 1.0, z + 1.0);
        AstVizRenderer.invalidateGeometry();
    }

    private void feedback(String message) {
        long now = System.currentTimeMillis();
        if (message.equals(lastFeedback) && now - lastFeedbackAt < 1000L) return;
        lastFeedback = message;
        lastFeedbackAt = now;
        AstCourseManager.chat(message);
    }
}
