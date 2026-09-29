package com.konqasasas.ast.hud;

import com.konqasasas.ast.ui.GuiAstNativeEditor;
import com.konqasasas.ast.ui.GuiAstNativeHudEditor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

/**
 * Opens the native course editor from a configurable in-game key or command.
 */
public class AstHudKeybinds {
    private static volatile int pendingScreen;
    private static final KeyBinding OPEN_GUI = new KeyBinding(
            "key.autosplittimer.open_gui", Keyboard.KEY_Y, "key.categories.autosplittimer");

    public AstHudKeybinds() {
        ClientRegistry.registerKeyBinding(OPEN_GUI);
    }

    public static void requestCourseEditor() { pendingScreen = 1; }
    public static void requestHudEditor() { pendingScreen = 2; }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (OPEN_GUI.isPressed() && mc.player != null && mc.world != null && mc.currentScreen == null) {
            mc.displayGuiScreen(new GuiAstNativeEditor());
            return;
        }
        // Client commands execute while the chat screen is still closing. Waiting
        // for currentScreen to become null prevents the requested editor being
        // immediately replaced by GuiChat.
        int requested = pendingScreen;
        if (requested == 0 || mc.currentScreen != null) return;
        pendingScreen = 0;
        if (requested == 2) mc.displayGuiScreen(new GuiAstNativeHudEditor(new GuiAstNativeEditor()));
        else mc.displayGuiScreen(new GuiAstNativeEditor());
    }
}
