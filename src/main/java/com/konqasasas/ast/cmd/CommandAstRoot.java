package com.konqasasas.ast.cmd;

import com.konqasasas.ast.core.*;
import com.konqasasas.ast.hud.AstHudConfigUtil;
import com.konqasasas.ast.hud.AstHudRenderer;
import com.konqasasas.ast.hud.AstHudKeybinds;
import com.konqasasas.ast.hud.AstLayoutManager;
import com.konqasasas.ast.viz.AstVizRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.IClientCommand;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.*;

@SideOnly(Side.CLIENT)
public class CommandAstRoot extends CommandBase implements IClientCommand {
    private static final double MIN_HEIGHT = 1e-5;

    @Override
    public String getName() {
        return "ast";
    }

    @Override
    public List<String> getAliases() {
        return Collections.singletonList("autosplit");
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/ast <course|seg|run|hud|viz|record> ...";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    /**
     * Forge client-command hook.
     * We keep the default behavior (must be typed with '/') to avoid collisions.
     */
    @Override
    public boolean allowUsageWithoutPrefix(ICommandSender sender, String message) {
        return false;
    }

    @Override
    public void execute(net.minecraft.server.MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            help(sender);
            return;
        }
        String top = args[0].toLowerCase(Locale.ROOT);
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        switch (top) {
            case "course":
                cmdCourse(sender, rest);
                break;
            case "seg":
            case "segment":
                cmdSeg(sender, rest);
                break;
            case "run":
                cmdRun(sender, rest);
                break;
            case "hud":
                cmdHud(sender, rest);
                break;
            case "viz":
                cmdViz(sender, rest);
                break;
            case "record":
                cmdRecord(sender, rest);
                break;
            default:
                help(sender);
        }
    }

    private static void help(ICommandSender sender) {
        msg(sender, "AutoSplit Timer (/ast) commands:");
        msg(sender, "  /ast course set <name>     (creates if missing)");
        msg(sender, "  /ast course load <name>    (existing only)");
        msg(sender, "  /ast course leave");
        msg(sender, "  /ast course info | list | delete <name>");
        msg(sender, "  /ast seg add <index> \"<name>\" height <h>  (h allows decimals, e.g. 2.5)");
        msg(sender, "  /ast seg delete <index> | list | rename <index> \"<name>\"");
        msg(sender, "  /ast run reset");
        msg(sender, "  /ast record clear <pb|bestseg|bestsplit|all>");
        msg(sender, "  /ast hud edit   (open GUI editor)");
        msg(sender, "  /ast viz on|off | mode outline|fill|both");
    }

    private static void cmdCourse(ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            msg(sender, "Usage: /ast course set <name> | load <name> | leave | info | list | delete <name>");
            return;
        }
        AstCourseManager cm = AstCourseManager.get();
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "set": {
                if (args.length < 2) {
                    msg(sender, "Usage: /ast course set <name>");
                    return;
                }
                String name = joinTail(args, 1);
                cm.setActiveCourse(name);
                msg(sender, "Active course: " + cm.getActiveCourseName());
                break;
            }
            case "load": {
                if (args.length < 2) {
                    msg(sender, "Usage: /ast course load <name>");
                    return;
                }
                String name = joinTail(args, 1);
                boolean ok = cm.loadExistingCourseAsActive(name);
                if (!ok) {
                    msg(sender, "Course not found: " + name);
                    return;
                }
                // reset runtime view when switching
                AstRuntime.get().forceResetToIdle();
                msg(sender, "Active course: " + cm.getActiveCourseName());
                break;
            }
            case "leave": {
                cm.clearActiveCourse();
                AstRuntime.get().forceResetToIdle();
                msg(sender, "Left course.");
                break;
            }
            case "info": {
                AstData.CourseFile c = cm.getActiveCourse();
                if (c == null) {
                    msg(sender, "No active course. Use /ast course set <name>.");
                    return;
                }
                msg(sender, "Course: " + c.courseName + " (segments=" + c.segments.size() + ")");
                msg(sender, "Attempts: " + c.stats.attemptCount + ", PB: " + (c.stats.pb.totalTicks == null ? "--" : c.stats.pb.totalTicks + "t"));
                break;
            }
            case "list": {
                List<String> names = cm.listCourseNames();
                msg(sender, "Courses (" + names.size() + "): " + String.join(", ", names));
                break;
            }
            case "delete": {
                if (args.length < 2) {
                    msg(sender, "Usage: /ast course delete <name>");
                    return;
                }
                String name = joinTail(args, 1);
                cm.deleteCourse(name);
                msg(sender, "Deleted course: " + name);
                break;
            }
            default:
                msg(sender, "Unknown course subcommand.");
        }
    }

    private static void cmdSeg(ICommandSender sender, String[] args) throws CommandException {
        AstCourseManager cm = AstCourseManager.get();
        AstData.CourseFile c = cm.getActiveCourse();
        if (c == null) {
            msg(sender, "No active course. Use /ast course set <name> first.");
            return;
        }
        if (args.length == 0) {
            msg(sender, "Usage: /ast seg add/delete/list/rename ...");
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list": {
                if (c.segments.isEmpty()) {
                    msg(sender, "No segments.");
                    return;
                }
                msg(sender, "Segments:");
                for (AstData.Segment s : c.segments) {
                    msg(sender, "  [" + s.index + "] " + s.name
                            + " h=" + AstUtil.formatDoubleTrunc5(s.height)
                            + " aabb=(" + AstUtil.formatDoubleTrunc5(s.aabb.minX)
                            + "," + AstUtil.formatDoubleTrunc5(s.aabb.minY)
                            + "," + AstUtil.formatDoubleTrunc5(s.aabb.minZ)
                            + ")..(" + AstUtil.formatDoubleTrunc5(s.aabb.maxX)
                            + "," + AstUtil.formatDoubleTrunc5(s.aabb.maxY)
                            + "," + AstUtil.formatDoubleTrunc5(s.aabb.maxZ)
                            + ")");
                }
                break;
            }
            case "add": {
                if (args.length < 5) {
                    msg(sender, "Usage: /ast seg add <index> \"<name>\" height <h>");
                    return;
                }
                int index = parseIntOrThrow(args[1]);
                String name = parseQuotedOrTail(args, 2);
                double height = parseHeightDouble(args);
                EntityPlayer p = Minecraft.getMinecraft().player;
                if (p == null) {
                    msg(sender, "Player not available.");
                    return;
                }
                AstData.Segment seg = new AstData.Segment();
                seg.index = index;
                seg.name = name;
                seg.height = Math.max(MIN_HEIGHT, height);

                int bx = (int) Math.floor(p.posX);
                // IMPORTANT: do NOT align/snap Y.
                // On slabs/half blocks, floor(posY) breaks region placement.
                double by = p.posY;
                int bz = (int) Math.floor(p.posZ);
                AxisAlignedBB bb = new AxisAlignedBB(bx, by, bz, bx + 1, by + seg.height, bz + 1);
                seg.aabb = AstData.AabbDto.fromAabb(bb);

                // replace if same index exists
                c.segments.removeIf(s -> s != null && s.index == index);
                c.segments.add(seg);
                // normalize & save
                cm.saveActiveCourseSafe();
                msg(sender, "Set seg [" + index + "] '" + name + "' height=" + AstUtil.formatDoubleTrunc5(seg.height)
                        + " at (" + bx + "," + AstUtil.formatDoubleTrunc5(by) + "," + bz + ")");
                break;
            }
            case "delete": {
                if (args.length < 2) {
                    msg(sender, "Usage: /ast seg delete <index>");
                    return;
                }
                int index = parseIntOrThrow(args[1]);
                boolean removed = c.segments.removeIf(s -> s != null && s.index == index);
                cm.saveActiveCourseSafe();
                msg(sender, removed ? "Deleted seg " + index : "No seg " + index);
                break;
            }
            case "rename": {
                if (args.length < 3) {
                    msg(sender, "Usage: /ast seg rename <index> \"<name>\"");
                    return;
                }
                int index = parseIntOrThrow(args[1]);
                String name = parseQuotedOrTail(args, 2);
                AstData.Segment seg = AstUtil.findSegment(c, index);
                if (seg == null) {
                    msg(sender, "No seg " + index);
                    return;
                }
                seg.name = name;
                cm.saveActiveCourseSafe();
                msg(sender, "Renamed seg " + index + " to '" + name + "'");
                break;
            }
            default:
                msg(sender, "Unknown seg subcommand.");
        }
    }

    private static void cmdRun(ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0 || !"reset".equalsIgnoreCase(args[0])) {
            msg(sender, "Usage: /ast run reset");
            return;
        }
        AstRuntime.get().forceResetToIdle();
        msg(sender, "Run reset (IDLE)");
    }

    private static void cmdRecord(ICommandSender sender, String[] args) throws CommandException {
        AstCourseManager cm = AstCourseManager.get();
        AstData.CourseFile c = cm.getActiveCourse();
        if (c == null) {
            msg(sender, "No active course.");
            return;
        }
        if (args.length < 2 || !"clear".equalsIgnoreCase(args[0])) {
            msg(sender, "Usage: /ast record clear <pb|bestseg|bestsplit|all>");
            return;
        }
        String t = args[1].toLowerCase(Locale.ROOT);
        switch (t) {
            case "pb":
                c.stats.pb = new AstData.PbRecord();
                break;
            case "bestseg":
                c.stats.bestSegmentsTicks.clear();
                break;
            case "bestsplit":
                c.stats.bestSplitTicks.clear();
                break;
            case "all":
                c.stats.pb = new AstData.PbRecord();
                c.stats.bestSegmentsTicks.clear();
                c.stats.bestSplitTicks.clear();
                break;
            default:
                msg(sender, "Unknown target: " + t);
                return;
        }
        cm.saveActiveCourseSafe();
        msg(sender, "Cleared records: " + t);
    }

    private static void cmdHud(ICommandSender sender, String[] args) throws CommandException {
        AstData.CourseFile c = AstCourseManager.get().getActiveCourse();
        if (c == null) {
            msg(sender, "No active course. Use /ast course set <name> first.");
            return;
        }
        if (c.hud == null) c.hud = new AstData.HudConfig();
        if (args.length == 0) {
            msg(sender, "Usage: /ast hud preset/scale/theme/splits/splitwidth/splitgap/toggle/timefmt/layout/edit ...");
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "preset": {
                if (args.length < 2) {
                    msg(sender, "Usage: /ast hud preset <standard|compact|practice|splitlist|minimal|off>");
                    return;
                }
                String preset = args[1];
                c.hud.preset = preset;
                AstHudConfigUtil.applyPreset(c.hud, preset, true);
                AstCourseManager.get().saveActiveCourseSafe();
                AstHudRenderer.requestRebuild();
                msg(sender, "HUD preset applied: " + c.hud.preset);
                break;
            }
            case "scale": {
                if (args.length < 2) {
                    msg(sender, "Usage: /ast hud scale <num>");
                    return;
                }
                try {
                    c.hud.scale = Double.parseDouble(args[1]);
                } catch (NumberFormatException nfe) {
                    msg(sender, "Invalid number.");
                    return;
                }
                AstCourseManager.get().saveActiveCourseSafe();
                AstHudRenderer.requestRebuild();
                msg(sender, "HUD scale=" + c.hud.scale);
                break;
            }
            case "theme": {
                if (args.length < 2) {
                    msg(sender, "Usage: /ast hud theme <dark|light|mono>");
                    return;
                }
                c.hud.theme = args[1];
                AstCourseManager.get().saveActiveCourseSafe();
                AstHudRenderer.requestRebuild();
                msg(sender, "HUD theme=" + c.hud.theme);
                break;
            }
            case "splits": {
                if (args.length < 2) {
                    msg(sender, "Usage: /ast hud splits <N>");
                    return;
                }
                c.hud.splitListCount = Math.max(0, parseIntOrThrow(args[1]));
                AstCourseManager.get().saveActiveCourseSafe();
                AstHudRenderer.requestRebuild();
                msg(sender, "HUD splitListCount=" + c.hud.splitListCount);
                break;
            }
            case "splitwidth": {
                if (args.length < 2) {
                    msg(sender, "Usage: /ast hud splitwidth <N>");
                    return;
                }
                c.hud.splitListWidth = Math.max(60, parseIntOrThrow(args[1]));
                // keep the user's primary/secondary ratio when the total width changes
                AstHudConfigUtil.reconcileSplitWidths(c.hud, true);
                AstCourseManager.get().saveActiveCourseSafe();
                AstHudRenderer.requestRebuild();
                msg(sender, "HUD splitListWidth=" + c.hud.splitListWidth);
                break;
            }
            case "splitpwidth": {
                if (args.length < 2) {
                    msg(sender, "Usage: /ast hud splitpwidth <N>");
                    return;
                }
                c.hud.splitPrimaryWidth = Math.max(10, parseIntOrThrow(args[1]));
                AstHudConfigUtil.reconcileSplitWidths(c.hud, false);
                AstCourseManager.get().saveActiveCourseSafe();
                AstHudRenderer.requestRebuild();
                msg(sender, "HUD splitPrimaryWidth=" + c.hud.splitPrimaryWidth);
                break;
            }
            case "splitswidth": {
                if (args.length < 2) {
                    msg(sender, "Usage: /ast hud splitswidth <N>");
                    return;
                }
                c.hud.splitSecondaryWidth = Math.max(10, parseIntOrThrow(args[1]));
                AstHudConfigUtil.reconcileSplitWidths(c.hud, false);
                AstCourseManager.get().saveActiveCourseSafe();
                AstHudRenderer.requestRebuild();
                msg(sender, "HUD splitSecondaryWidth=" + c.hud.splitSecondaryWidth);
                break;
            }
            case "splitgap": {
                if (args.length < 2) {
                    msg(sender, "Usage: /ast hud splitgap <N>");
                    return;
                }
                c.hud.splitListGap = Math.max(0, parseIntOrThrow(args[1]));
                AstHudConfigUtil.reconcileSplitWidths(c.hud, false);
                AstCourseManager.get().saveActiveCourseSafe();
                AstHudRenderer.requestRebuild();
                msg(sender, "HUD splitListGap=" + c.hud.splitListGap);
                break;
            }
            case "splitcols": {
                // Kept for backwards compatibility.
                // New SplitList style:
                //   /ast hud splitcols              (cycle comparison pb<->best)
                //   /ast hud splitcols <split|seg> <pb|best>
                if (args.length == 1) {
                    c.hud.comparison = "best".equalsIgnoreCase(c.hud.comparison) ? "pb" : "best";
                    AstCourseManager.get().saveActiveCourseSafe();
                    AstHudRenderer.requestRebuild();
                    msg(sender, "HUD compare=" + c.hud.comparison + " unit=" + c.hud.unit);
                    return;
                }
                if (args.length < 3) {
                    msg(sender, "Usage: /ast hud splitcols <split|seg> <pb|best>");
                    return;
                }
                c.hud.unit = args[1];
                c.hud.comparison = args[2];
                AstHudConfigUtil.normalizeHud(c.hud);
                AstCourseManager.get().saveActiveCourseSafe();
                AstHudRenderer.requestRebuild();
                msg(sender, "HUD compare=" + c.hud.comparison + " unit=" + c.hud.unit);
                break;
            }
            case "color": {
                // /ast hud color <label|main|sub|good|bad|gold> <0-f>
                if (args.length < 3) {
                    msg(sender, "Usage: /ast hud color <label|main|sub|good|bad|gold> <0-f>");
                    return;
                }
                String part = args[1].toLowerCase(Locale.ROOT);
                String v = args[2].toLowerCase(Locale.ROOT);
                if (v.length() != 1) {
                    msg(sender, "Color must be a single hex digit 0-f.");
                    return;
                }
                switch (part) {
                    case "label": c.hud.colorLabel = v; break;
                    case "main": c.hud.colorMainText = v; break;
                    case "sub": c.hud.colorSubText = v; break;
                    case "good": c.hud.colorGood = v; break;
                    case "bad": c.hud.colorBad = v; break;
                    case "gold": c.hud.colorGold = v; break;
                    default:
                        msg(sender, "Unknown part: " + part);
                        return;
                }
                AstHudConfigUtil.normalizeHud(c.hud);
                AstCourseManager.get().saveActiveCourseSafe();
                AstHudRenderer.requestRebuild();
                msg(sender, "HUD color updated: " + part + "=" + v);
                break;
            }
            case "toggle": {
                if (args.length < 3) {
                    msg(sender, "Usage: /ast hud toggle <item> <on|off>");
                    return;
                }
                String key = args[1];
                String val = args[2].toLowerCase(Locale.ROOT);
                boolean on = "on".equals(val) || "true".equals(val) || "1".equals(val);
                c.hud.toggles.put(key, on);
                AstCourseManager.get().saveActiveCourseSafe();
                AstHudRenderer.requestRebuild();
                msg(sender, "HUD toggle " + key + "=" + on);
                break;
            }
            case "timefmt": {
                if (args.length < 2) {
                    msg(sender, "Usage: /ast hud timefmt <MSS|TICKS|SECONDS>");
                    return;
                }
                c.hud.timeFormat = args[1].toUpperCase(Locale.ROOT);
                AstCourseManager.get().saveActiveCourseSafe();
                AstHudRenderer.requestRebuild();
                msg(sender, "HUD timeFormat=" + c.hud.timeFormat);
                break;
            }
            case "layout": {
                cmdHudLayout(sender, c, Arrays.copyOfRange(args, 1, args.length));
                break;
            }
            case "edit": {
                // Use the same open path as the keybind (reliable).
                msg(sender, "Opening HUD editor GUI...");
                AstHudKeybinds.requestOpen();
                break;
            }
            default:
                msg(sender, "Unknown hud subcommand.");
        }
    }

    private static void cmdHudLayout(ICommandSender sender, AstData.CourseFile c, String[] args) {
        if (args.length == 0) {
            msg(sender, "Usage: /ast hud layout save <name> | load <name> | list");
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "save": {
                if (args.length < 2) {
                    msg(sender, "Usage: /ast hud layout save <name>");
                    return;
                }
                String name = joinTail(args, 1);
                AstLayoutManager.saveLayoutSafe(name, c.hud);
                msg(sender, "Saved layout: " + name);
                break;
            }
            case "load": {
                if (args.length < 2) {
                    msg(sender, "Usage: /ast hud layout load <name>");
                    return;
                }
                String name = joinTail(args, 1);
                AstData.HudConfig loaded = AstLayoutManager.loadLayoutSafe(name);
                if (loaded == null) {
                    msg(sender, "Layout not found: " + name);
                    return;
                }
                // Replace current HUD config with loaded layout (keep preset name as-is).
                String preset = c.hud.preset;
                c.hud = loaded;
                c.hud.preset = preset;
                AstCourseManager.get().saveActiveCourseSafe();
                AstHudRenderer.requestRebuild();
                msg(sender, "Loaded layout: " + name);
                break;
            }
            case "list": {
                List<String> names = AstLayoutManager.listLayouts();
                msg(sender, "Layouts (" + names.size() + "): " + String.join(", ", names));
                break;
            }
            default:
                msg(sender, "Unknown layout subcommand.");
        }
    }

    private static void cmdViz(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            msg(sender, "Usage: /ast viz on|off | mode outline|fill|both");
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "on":
                AstVizRenderer.setEnabled(true);
                msg(sender, "Viz enabled");
                break;
            case "off":
                AstVizRenderer.setEnabled(false);
                msg(sender, "Viz disabled");
                break;
            case "mode":
                if (args.length < 2) {
                    msg(sender, "Usage: /ast viz mode outline|fill|both");
                    return;
                }
                AstVizRenderer.setMode(args[1]);
                msg(sender, "Viz mode=" + AstVizRenderer.getModeName());
                break;
            default:
                msg(sender, "Unknown viz subcommand.");
        }
    }

    /** Parse integer or throw a command-friendly error. Named to avoid clashing with CommandBase.parseInt. */
    public static int parseIntOrThrow(String s) throws CommandException {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException nfe) {
            throw new CommandException("Invalid number.");
        }
    }

    private static boolean isHexDigit(String s) {
        if (s == null || s.isEmpty()) return false;
        char c = Character.toLowerCase(s.charAt(0));
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
    }

    private static String normalizeHexDigit(String s) {
        if (s == null || s.isEmpty()) return "f";
        return String.valueOf(Character.toLowerCase(s.charAt(0)));
    }

    private static double parseHeightDouble(String[] args) throws CommandException {
        for (int i = 0; i < args.length - 1; i++) {
            if ("height".equalsIgnoreCase(args[i])) {
                try {
                    return Double.parseDouble(args[i + 1]);
                } catch (NumberFormatException nfe) {
                    throw new CommandException("Invalid height.");
                }
            }
        }
        // fallback: last arg
        try {
            return Double.parseDouble(args[args.length - 1]);
        } catch (NumberFormatException nfe) {
            throw new CommandException("Invalid height.");
        }
    }

    private static String joinTail(String[] args, int start) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (i > start) sb.append(' ');
            sb.append(args[i]);
        }
        return sb.toString();
    }

    private static String parseQuotedOrTail(String[] args, int start) {
        // In vanilla command parsing, quoted strings remain as a single arg. We'll just join until we hit "height".
        int end = args.length;
        for (int i = start; i < args.length; i++) {
            if ("height".equalsIgnoreCase(args[i])) {
                end = i;
                break;
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end; i++) {
            if (i > start) sb.append(' ');
            sb.append(args[i]);
        }
        return sb.toString();
    }

    private static void msg(ICommandSender sender, String text) {
        // Dark aqua prefix for readability.
        sender.sendMessage(new TextComponentString("\u00a73[AST]\u00a7r " + text));
    }
}
