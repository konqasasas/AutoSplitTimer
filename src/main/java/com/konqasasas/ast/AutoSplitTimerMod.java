package com.konqasasas.ast;

import com.konqasasas.ast.cmd.CommandAstRoot;
import com.konqasasas.ast.core.AstCourseManager;
import com.konqasasas.ast.core.AstRuntime;
import com.konqasasas.ast.hud.AstHudRenderer;
import com.konqasasas.ast.hud.AstHudKeybinds;
import com.konqasasas.ast.viz.AstVizRenderer;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@Mod(
        modid = AutoSplitTimerMod.MODID,
        name = AutoSplitTimerMod.NAME,
        version = AutoSplitTimerMod.VERSION,
        clientSideOnly = true
)
@SideOnly(Side.CLIENT)
public class AutoSplitTimerMod {
    public static final String MODID = "autosplittimer";
    public static final String NAME = "AutoSplit Timer";
    public static final String VERSION = "1.0";

    @Mod.Instance(MODID)
    public static AutoSplitTimerMod INSTANCE;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        // Load course data early so /ast works even before joining a world.
        AstCourseManager.get().loadAllCoursesSafe();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
        MinecraftForge.EVENT_BUS.register(AstRuntime.get());
        MinecraftForge.EVENT_BUS.register(new AstHudRenderer());
        MinecraftForge.EVENT_BUS.register(new AstVizRenderer());
        MinecraftForge.EVENT_BUS.register(new AstHudKeybinds());
        ClientCommandHandler.instance.registerCommand(new CommandAstRoot());
    }
}
