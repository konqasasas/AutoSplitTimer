package com.konqasasas.ast.hud;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * GUI open helper.
 *
 * Spec: GUI is opened only via /ast hud edit (no keybind).
 */
public class AstHudKeybinds {
    private static volatile boolean OPEN_REQUESTED = false;

    public static void requestOpen() {
        OPEN_REQUESTED = true;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) return;
        if (mc.currentScreen != null) return;
        if (!OPEN_REQUESTED) return;
        OPEN_REQUESTED = false;
        mc.displayGuiScreen(new GuiAstHudEditor());
    }
}
