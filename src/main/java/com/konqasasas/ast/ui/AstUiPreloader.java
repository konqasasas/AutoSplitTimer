package com.konqasasas.ast.ui;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Prepares native UI assets a little at a time on the render thread.
 *
 * Font parsing alone is not enough: the expensive work is creating each
 * size-specific atlas, rasterizing glyphs and uploading it to the GPU. Doing
 * one atlas per client tick avoids moving that cost to the first Y press.
 */
public final class AstUiPreloader {
    private static final String JAPANESE =
            "コース編集設定未選択新しい作成現在地ラップ追加地点名判定方式範囲高さ変更削除"
                    + "読み込み共有記録リセット表示項目順外観簡易計測終了スタートゴール使用中場所ブロック";
    private static final String LATIN =
            "AUTOSPLIT TIMER START LAP GOAL HUD On Ground AABB COURSE TIME SEGMENT BEST PB px";
    private static final String NUMERIC = "0123456789+-.,:()%/tpx";

    private final Queue<Runnable> pending = new ArrayDeque<>();
    private int initialDelayTicks = 2;

    public AstUiPreloader() {
        // The first editor frame mainly uses 11/12px body copy and a 22px
        // heading. These atlases are deliberately first in the queue.
        pending.add(AstRoundedRenderer::warmUp);
        pending.add(AstIcons::warmUp);
        pending.add(() -> AstFonts.japanese().prime(JAPANESE, 12f));
        pending.add(() -> AstFonts.japaneseBold().prime(JAPANESE, 24f));
        pending.add(() -> AstFonts.sans().prime(LATIN, 13f));
        pending.add(() -> AstFonts.semibold().prime(LATIN, 12f));
        pending.add(() -> AstFonts.mono().prime(NUMERIC, 12f));

        // Remaining sizes cover menus, hints, dialogs and the HUD editor.
        addSizes(AstFonts.japanese(), JAPANESE, 9.5f, 11f, 13f, 14f, 19.5f);
        addSizes(AstFonts.japaneseBold(), JAPANESE, 11f, 12f, 13f, 14f, 19.5f);
        addSizes(AstFonts.sans(), LATIN, 9.5f, 11f, 12f, 14f, 15f, 19.5f);
        addSizes(AstFonts.semibold(), LATIN, 9.5f, 11f, 13f, 14f, 15f, 19.5f, 24f);
        addSizes(AstFonts.mono(), NUMERIC, 11f, 13f, 14f, 15f, 21.5f);
    }

    private void addSizes(AstFont font, String text, float... sizes) {
        for (float size : sizes) pending.add(() -> font.prime(text, size));
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || pending.isEmpty()) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.getTextureManager() == null) return;
        if (initialDelayTicks > 0) {
            initialDelayTicks--;
            return;
        }
        pending.remove().run();
    }
}
