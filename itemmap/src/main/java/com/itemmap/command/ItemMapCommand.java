package com.itemmap.command;

import com.itemmap.ItemMapMod;
import com.itemmap.manager.FrameData;
import com.itemmap.manager.FrameManager;
import com.itemmap.manager.UndoManager;
import com.itemmap.network.FrameSyncPayload;
import com.itemmap.network.FrameUpdatePayload;
import com.itemmap.network.ImagePayload;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.io.File;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ItemMapCommand {

    private static final SuggestionProvider<ServerCommandSource> IMAGE_SUGGESTIONS =
        (ctx, builder) -> {
            for (String id : FrameManager.allImageIds()) builder.suggest(id);
            return builder.buildFuture();
        };

    private static final SuggestionProvider<ServerCommandSource> MODE_SUGGESTIONS =
        (ctx, builder) -> {
            for (String m : new String[]{"flat2d","spin3d","render3d"}) builder.suggest(m);
            return builder.buildFuture();
        };

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, reg, env) -> {
            dispatcher.register(build("itemmap"));
            dispatcher.register(build("im"));
        });
    }

    private static LiteralArgumentBuilder<ServerCommandSource> build(String root) {
        return CommandManager.literal(root)
            .requires(src -> src.hasPermissionLevel(2))
            .executes(ctx -> cmdHelp(ctx.getSource()))

            // /im mode <flat2d|spin3d|render3d> [frameId]
            .then(CommandManager.literal("mode")
                .then(CommandManager.argument("mode", StringArgumentType.word())
                    .suggests(MODE_SUGGESTIONS)
                    .executes(ctx -> cmdMode(ctx.getSource(),
                        StringArgumentType.getString(ctx, "mode"), -1))
                    .then(CommandManager.argument("frameId", StringArgumentType.word())
                        .executes(ctx -> cmdMode(ctx.getSource(),
                            StringArgumentType.getString(ctx, "mode"),
                            parseLong(StringArgumentType.getString(ctx, "frameId")))))
                )
            )

            // /im spin <speed>   (0.1 - 100, default 2)
            .then(CommandManager.literal("spin")
                .then(CommandManager.argument("speed", FloatArgumentType.floatArg(0.1f, 100f))
                    .executes(ctx -> cmdSpin(ctx.getSource(),
                        FloatArgumentType.getFloat(ctx, "speed")))
                )
            )

            // /im scale <size>   (0.1 - 2.0)
            .then(CommandManager.literal("scale")
                .then(CommandManager.argument("size", FloatArgumentType.floatArg(0.1f, 2.0f))
                    .executes(ctx -> cmdScale(ctx.getSource(),
                        FloatArgumentType.getFloat(ctx, "size")))
                )
            )

            // /im padding <percent>  (0 - 50)
            .then(CommandManager.literal("padding")
                .then(CommandManager.argument("pct", FloatArgumentType.floatArg(0f, 50f))
                    .executes(ctx -> cmdPadding(ctx.getSource(),
                        FloatArgumentType.getFloat(ctx, "pct")))
                )
            )

            // /im glow <on|off>
            .then(CommandManager.literal("glow")
                .then(CommandManager.argument("value", BoolArgumentType.bool())
                    .executes(ctx -> cmdGlow(ctx.getSource(),
                        BoolArgumentType.getBool(ctx, "value")))
                )
            )

            // /im invisible <on|off>
            .then(CommandManager.literal("invisible")
                .then(CommandManager.argument("value", BoolArgumentType.bool())
                    .executes(ctx -> cmdInvisible(ctx.getSource(),
                        BoolArgumentType.getBool(ctx, "value")))
                )
            )

            // /im label <text>   (use _ for spaces, "none" to remove)
            .then(CommandManager.literal("label")
                .then(CommandManager.argument("text", StringArgumentType.greedyString())
                    .executes(ctx -> cmdLabel(ctx.getSource(),
                        StringArgumentType.getString(ctx, "text")))
                )
            )

            // /im bg <AARRGGBB hex>  (e.g. 80FF0000 = semi red, 00000000 = none)
            .then(CommandManager.literal("bg")
                .then(CommandManager.argument("color", StringArgumentType.word())
                    .executes(ctx -> cmdBg(ctx.getSource(),
                        StringArgumentType.getString(ctx, "color")))
                )
            )

            // /im image <imageId>  or  /im image none
            .then(CommandManager.literal("image")
                .then(CommandManager.argument("id", StringArgumentType.word())
                    .suggests(IMAGE_SUGGESTIONS)
                    .executes(ctx -> cmdImage(ctx.getSource(),
                        StringArgumentType.getString(ctx, "id")))
                )
            )

            // /im upload <imageId> <url>
            .then(CommandManager.literal("upload")
                .then(CommandManager.argument("imageId", StringArgumentType.word())
                    .then(CommandManager.argument("url", StringArgumentType.greedyString())
                        .executes(ctx -> cmdUpload(ctx.getSource(),
                            StringArgumentType.getString(ctx, "imageId"),
                            StringArgumentType.getString(ctx, "url").trim()))
                    )
                )
            )

            // /im reset
            .then(CommandManager.literal("reset")
                .executes(ctx -> cmdReset(ctx.getSource()))
            )

            // /im remove
            .then(CommandManager.literal("remove")
                .executes(ctx -> cmdRemove(ctx.getSource()))
            )

            // /im info
            .then(CommandManager.literal("info")
                .executes(ctx -> cmdInfo(ctx.getSource()))
            )

            // /im list
            .then(CommandManager.literal("list")
                .executes(ctx -> cmdList(ctx.getSource()))
            )

            // /im images
            .then(CommandManager.literal("images")
                .executes(ctx -> cmdImages(ctx.getSource()))
            )

            // /im undo
            .then(CommandManager.literal("undo")
                .executes(ctx -> cmdUndo(ctx.getSource()))
            )

            // /im redo
            .then(CommandManager.literal("redo")
                .executes(ctx -> cmdRedo(ctx.getSource()))
            )

            // /im reload
            .then(CommandManager.literal("reload")
                .executes(ctx -> cmdReload(ctx.getSource()))
            )

            // /im gui - open the settings GUI
            .then(CommandManager.literal("gui")
                .executes(ctx -> cmdGui(ctx.getSource()))
            )

            // /im color <hex> - change text/letter color on frame
            .then(CommandManager.literal("color")
                .then(CommandManager.argument("hex", StringArgumentType.word())
                    .executes(ctx -> cmdColor(ctx.getSource(),
                        StringArgumentType.getString(ctx, "hex")))
                )
            )

            // /im changebg <hex> - change background color on frame
            .then(CommandManager.literal("changebg")
                .then(CommandManager.argument("hex", StringArgumentType.word())
                    .executes(ctx -> cmdChangeBg(ctx.getSource(),
                        StringArgumentType.getString(ctx, "hex")))
                )
            )

            // /im importfolder - import all PNGs from config/itemmap/import/
            .then(CommandManager.literal("importfolder")
                .executes(ctx -> cmdImportFolder(ctx.getSource()))
            )

            // /im tutorial
            .then(CommandManager.literal("tutorial")
                .executes(ctx -> cmdTutorial(ctx.getSource()))
            )

            // /im help
            .then(CommandManager.literal("help")
                .executes(ctx -> cmdHelp(ctx.getSource()))
            );
    }

    private static long parseLong(String s) {
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return -1; }
    }

    // __ Frame resolver: always target the frame you are looking at ────────────

    private static FrameData lookAtFrame(ServerCommandSource src) {
        return lookAtFrame(src, -1);
    }

    private static FrameData lookAtFrame(ServerCommandSource src, long explicitId) {
        // If explicit ID given, use it directly
        if (explicitId > 0) {
            return FrameManager.getOrCreate(explicitId);
        }
        if (!(src.getEntity() instanceof ServerPlayerEntity player)) {
            src.sendError(Text.literal("[ItemMap] Must be a player."));
            return null;
        }
        Vec3d eye   = player.getEyePos();
        Vec3d look  = player.getRotationVec(1.0f);
        Vec3d reach = eye.add(look.multiply(8.0));
        // Large search box - item frames are tiny entities
        Box search  = new Box(eye, reach).expand(1.5);

        ItemFrameEntity closest = null;
        double bestDist = Double.MAX_VALUE;
        for (net.minecraft.entity.Entity e : player.getWorld().getOtherEntities(player, search)) {
            if (!(e instanceof ItemFrameEntity ife)) continue;
            // Expand hitbox significantly - item frames are 1 pixel thick
            java.util.Optional<Vec3d> hit = ife.getBoundingBox().expand(0.5).raycast(eye, reach);
            if (hit.isPresent()) {
                double d = eye.squaredDistanceTo(hit.get());
                if (d < bestDist) { bestDist = d; closest = ife; }
            }
        }
        // Fallback: just pick closest item frame within 6 blocks if raycast missed
        if (closest == null) {
            Box fallback = new Box(eye, reach).expand(0.5);
            for (net.minecraft.entity.Entity e : player.getWorld().getOtherEntities(player, fallback)) {
                if (!(e instanceof ItemFrameEntity ife)) continue;
                double d = eye.squaredDistanceTo(ife.getPos());
                if (d < bestDist) { bestDist = d; closest = ife; }
            }
        }
        if (closest == null) {
            src.sendError(Text.literal("[ItemMap] Look at an item frame first."));
            return null;
        }
        return FrameManager.getOrCreate(closest.getId());
    }

    private static void save(ServerCommandSource src, FrameData before, FrameData after) {
        FrameManager.put(after);
        FrameManager.saveAll();
        if (src.getEntity() instanceof ServerPlayerEntity p)
            UndoManager.push(p.getUuid(), before, after);
        ItemMapMod.broadcastFrameUpdate(src.getServer(), after);
    }

    // ── Commands ──────────────────────────────────────────────────────────────

    private static int cmdMode(ServerCommandSource src, String modeStr) {
        return cmdMode(src, modeStr, -1);
    }
    private static int cmdMode(ServerCommandSource src, String modeStr, long frameId) {
        FrameData f = lookAtFrame(src, frameId); if (f == null) return 0;
        FrameData before = f.copy();
        switch (modeStr.toLowerCase()) {
            case "flat2d"   -> f.mode = FrameData.DisplayMode.FLAT_2D;
            case "spin3d"   -> f.mode = FrameData.DisplayMode.SPIN_3D;
            case "render3d" -> f.mode = FrameData.DisplayMode.RENDER_3D;
            default -> { src.sendError(Text.literal("[ItemMap] Use: flat2d, spin3d, render3d")); return 0; }
        }
        save(src, before, f);
        src.sendFeedback(() -> Text.literal("[ItemMap] Mode -> " + f.mode.name()), false);
        return 1;
    }

    private static int cmdSpin(ServerCommandSource src, float speed) {
        return cmdSpin(src, speed, -1);
    }
    private static int cmdSpin(ServerCommandSource src, float speed, long frameId) {
        FrameData f = lookAtFrame(src, frameId); if (f == null) return 0;
        FrameData before = f.copy();
        f.spinSpeed = speed;
        save(src, before, f);
        src.sendFeedback(() -> Text.literal("[ItemMap] Spin speed -> " + speed), false);
        return 1;
    }

    private static int cmdScale(ServerCommandSource src, float size) {
        return cmdScale(src, size, -1);
    }
    private static int cmdScale(ServerCommandSource src, float size, long frameId) {
        FrameData f = lookAtFrame(src, frameId); if (f == null) return 0;
        FrameData before = f.copy();
        f.scale = size;
        save(src, before, f);
        src.sendFeedback(() -> Text.literal("[ItemMap] Scale -> " + size), false);
        return 1;
    }

    private static int cmdPadding(ServerCommandSource src, float pct) {
        return cmdPadding(src, pct, -1);
    }
    private static int cmdPadding(ServerCommandSource src, float pct, long frameId) {
        FrameData f = lookAtFrame(src, frameId); if (f == null) return 0;
        FrameData before = f.copy();
        f.padPct = pct;
        save(src, before, f);
        src.sendFeedback(() -> Text.literal("[ItemMap] Padding -> " + pct + "%"), false);
        return 1;
    }

    private static int cmdGlow(ServerCommandSource src, boolean on) {
        return cmdGlow(src, on, -1);
    }
    private static int cmdGlow(ServerCommandSource src, boolean on, long frameId) {
        FrameData f = lookAtFrame(src, frameId); if (f == null) return 0;
        FrameData before = f.copy();
        f.glowing = on;
        save(src, before, f);
        src.sendFeedback(() -> Text.literal("[ItemMap] Glow -> " + (on ? "ON" : "OFF")), false);
        return 1;
    }

    private static int cmdInvisible(ServerCommandSource src, boolean on) {
        return cmdInvisible(src, on, -1);
    }
    private static int cmdInvisible(ServerCommandSource src, boolean on, long frameId) {
        FrameData f = lookAtFrame(src, frameId); if (f == null) return 0;
        FrameData before = f.copy();
        f.invisible = on;
        save(src, before, f);
        src.sendFeedback(() -> Text.literal("[ItemMap] Invisible frame -> " + (on ? "ON" : "OFF")), false);
        return 1;
    }

    private static int cmdLabel(ServerCommandSource src, String text) {
        return cmdLabel(src, text, -1);
    }
    private static int cmdLabel(ServerCommandSource src, String text, long frameId) {
        FrameData f = lookAtFrame(src, frameId); if (f == null) return 0;
        FrameData before = f.copy();
        f.label = text.equalsIgnoreCase("none") ? null : text.replace("_", " ");
        save(src, before, f);
        final String display = f.label != null ? f.label : "(removed)";
        src.sendFeedback(() -> Text.literal("[ItemMap] Label -> " + display), false);
        return 1;
    }

    private static int cmdBg(ServerCommandSource src, String hex) {
        return cmdBg(src, hex, -1);
    }
    private static int cmdBg(ServerCommandSource src, String hex, long frameId) {
        FrameData f = lookAtFrame(src, frameId); if (f == null) return 0;
        FrameData before = f.copy();
        try {
            f.bgColor = (int) Long.parseLong(hex.replace("#",""), 16);
        } catch (NumberFormatException e) {
            src.sendError(Text.literal("[ItemMap] Bad color. Example: 80FF0000 (semi red) or 00000000 (none)"));
            return 0;
        }
        save(src, before, f);
        src.sendFeedback(() -> Text.literal("[ItemMap] Background color set."), false);
        return 1;
    }

    private static int cmdImage(ServerCommandSource src, String imageId) {
        return cmdImage(src, imageId, -1);
    }
    private static int cmdImage(ServerCommandSource src, String imageId, long frameId) {
        FrameData f = lookAtFrame(src, frameId); if (f == null) return 0;
        if (!imageId.equalsIgnoreCase("none") && FrameManager.getImage(imageId) == null) {
            src.sendError(Text.literal("[ItemMap] Image '" + imageId + "' not found. Use /im upload first."));
            return 0;
        }
        FrameData before = f.copy();
        f.customImageId = imageId.equalsIgnoreCase("none") ? null : imageId;
        save(src, before, f);
        src.sendFeedback(() -> Text.literal("[ItemMap] Image -> " + (f.customImageId != null ? f.customImageId : "vanilla")), false);
        return 1;
    }

    private static int cmdUpload(ServerCommandSource src, String imageId, String url) {
        src.sendFeedback(() -> Text.literal("[ItemMap] Downloading '" + imageId + "'..."), false);
        Thread t = new Thread(() -> {
            try {
                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url)).timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "Mozilla/5.0").GET().build();
                HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
                if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                    src.sendFeedback(() -> Text.literal("[ItemMap] HTTP error: " + resp.statusCode()), false);
                    return;
                }
                byte[] png = resp.body();
                if (png == null || png.length == 0) {
                    src.sendFeedback(() -> Text.literal("[ItemMap] Empty response."), false);
                    return;
                }
                FrameManager.putImage(imageId, png);
                FrameManager.saveAll();
                ItemMapMod.broadcastImage(src.getServer(), imageId, png);
                src.sendFeedback(() -> Text.literal("[ItemMap] Uploaded '" + imageId + "' (" + png.length/1024 + " KB)."), false);
            } catch (Exception e) {
                src.sendFeedback(() -> Text.literal("[ItemMap] Failed: " + e.getMessage()), false);
            }
        }, "ItemMap-Upload");
        t.setDaemon(true);
        t.start();
        return 1;
    }

    private static int cmdReset(ServerCommandSource src) {
        return cmdReset(src, -1);
    }
    private static int cmdReset(ServerCommandSource src, long frameId) {
        FrameData f = lookAtFrame(src, frameId); if (f == null) return 0;
        FrameData before = f.copy();
        f.mode = FrameData.DisplayMode.FLAT_2D;
        f.spinSpeed = 2f; f.scale = 1f; f.padPct = 0f;
        f.glowing = false; f.invisible = false;
        f.label = null; f.bgColor = 0; f.customImageId = null;
        save(src, before, f);
        src.sendFeedback(() -> Text.literal("[ItemMap] Frame reset to defaults."), false);
        return 1;
    }

    private static int cmdRemove(ServerCommandSource src) {
        return cmdRemove(src, -1);
    }
    private static int cmdRemove(ServerCommandSource src, long frameId) {
        FrameData f = lookAtFrame(src, frameId); if (f == null) return 0;
        long eid = f.entityId;
        FrameManager.remove(eid);
        FrameManager.saveAll();
        ItemMapMod.broadcastFrameRemove(src.getServer(), eid);
        src.sendFeedback(() -> Text.literal("[ItemMap] Removed frame data."), false);
        return 1;
    }

    private static int cmdInfo(ServerCommandSource src) {
        return cmdInfo(src, -1);
    }
    private static int cmdInfo(ServerCommandSource src, long frameId) {
        FrameData f = lookAtFrame(src, frameId); if (f == null) return 0;
        src.sendFeedback(() -> Text.literal(
            "[ItemMap] Frame " + f.entityId + "\n" +
            "  Mode:      " + f.mode.name() + "\n" +
            "  Spin:      " + f.spinSpeed + "\n" +
            "  Scale:     " + f.scale + "\n" +
            "  Padding:   " + f.padPct + "%\n" +
            "  Glow:      " + f.glowing + "\n" +
            "  Invisible: " + f.invisible + "\n" +
            "  Label:     " + (f.label != null ? f.label : "(item name)") + "\n" +
            "  BG Color:  " + String.format("#%08X", f.bgColor) + "\n" +
            "  Image:     " + (f.customImageId != null ? f.customImageId : "(vanilla)")
        ), false);
        return 1;
    }

    private static int cmdList(ServerCommandSource src) {
        Collection<FrameData> all = FrameManager.all();
        if (all.isEmpty()) { src.sendFeedback(() -> Text.literal("[ItemMap] No frames configured."), false); return 1; }
        src.sendFeedback(() -> Text.literal("[ItemMap] " + all.size() + " frame(s):"), false);
        for (FrameData d : all) {
            src.sendFeedback(() -> Text.literal(
                "  #" + d.entityId + " " + d.mode.name() +
                " scale=" + d.scale + " spin=" + d.spinSpeed +
                (d.label != null ? " label=" + d.label : "") +
                (d.customImageId != null ? " img=" + d.customImageId : "")), false);
        }
        return 1;
    }

    private static int cmdImages(ServerCommandSource src) {
        Set<String> ids = FrameManager.allImageIds();
        if (ids.isEmpty()) { src.sendFeedback(() -> Text.literal("[ItemMap] No images uploaded."), false); return 1; }
        src.sendFeedback(() -> Text.literal("[ItemMap] " + ids.size() + " image(s):"), false);
        for (String id : ids) {
            byte[] png = FrameManager.getImage(id);
            int kb = png != null ? png.length / 1024 : 0;
            src.sendFeedback(() -> Text.literal("  " + id + " (" + kb + " KB)"), false);
        }
        return 1;
    }

    private static int cmdUndo(ServerCommandSource src) {
        if (!(src.getEntity() instanceof ServerPlayerEntity player)) return 0;
        FrameData r = UndoManager.undo(player.getUuid());
        if (r == null) { src.sendError(Text.literal("[ItemMap] Nothing to undo.")); return 0; }
        FrameManager.put(r); FrameManager.saveAll();
        ItemMapMod.broadcastFrameUpdate(src.getServer(), r);
        src.sendFeedback(() -> Text.literal("[ItemMap] Undone."), false);
        return 1;
    }

    private static int cmdRedo(ServerCommandSource src) {
        if (!(src.getEntity() instanceof ServerPlayerEntity player)) return 0;
        FrameData r = UndoManager.redo(player.getUuid());
        if (r == null) { src.sendError(Text.literal("[ItemMap] Nothing to redo.")); return 0; }
        FrameManager.put(r); FrameManager.saveAll();
        ItemMapMod.broadcastFrameUpdate(src.getServer(), r);
        src.sendFeedback(() -> Text.literal("[ItemMap] Redone."), false);
        return 1;
    }

    private static int cmdReload(ServerCommandSource src) {
        FrameManager.clearAll();
        FrameManager.loadAll();
        List<FrameSyncPayload.FrameEntry> entries = new ArrayList<>();
        for (FrameData d : FrameManager.all()) entries.add(FrameSyncPayload.fromData(d));
        FrameSyncPayload pkt = new FrameSyncPayload(entries);
        for (ServerPlayerEntity p : src.getServer().getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(p, pkt);
            ConcurrentLinkedQueue<ImagePayload> queue = new ConcurrentLinkedQueue<>();
            for (String id : FrameManager.allImageIds()) {
                byte[] png = FrameManager.getImage(id);
                if (png != null) queue.add(new ImagePayload(id, png));
            }
            ItemMapMod.queueImagesForPlayer(p, queue);
        }
        src.sendFeedback(() -> Text.literal("[ItemMap] Reloaded. " +
            FrameManager.all().size() + " frames, " +
            FrameManager.allImageIds().size() + " images."), false);
        return 1;
    }

    private static int cmdColor(ServerCommandSource src, String hex) {
        FrameData f = lookAtFrame(src); if (f == null) return 0;
        FrameData before = f.copy();
        try {
            f.bgColor = (f.bgColor & 0xFF000000) | (int)(Long.parseLong(hex.replace("#",""), 16) & 0x00FFFFFF);
        } catch (NumberFormatException e) {
            src.sendError(Text.literal("[ItemMap] Bad hex. Example: FF0000 = red, 000000 = black"));
            return 0;
        }
        save(src, before, f);
        src.sendFeedback(() -> Text.literal("[ItemMap] Color set."), false);
        return 1;
    }

    private static int cmdChangeBg(ServerCommandSource src, String hex) {
        FrameData f = lookAtFrame(src); if (f == null) return 0;
        FrameData before = f.copy();
        try {
            String clean = hex.replace("#","");
            // Support both RRGGBB (fully opaque) and AARRGGBB
            if (clean.length() == 6) clean = "FF" + clean;
            f.bgColor = (int) Long.parseLong(clean, 16);
        } catch (NumberFormatException e) {
            src.sendError(Text.literal("[ItemMap] Bad hex. Example: FF0000 = red bg, 00000000 = no bg"));
            return 0;
        }
        save(src, before, f);
        src.sendFeedback(() -> Text.literal("[ItemMap] Background color set."), false);
        return 1;
    }

    private static int cmdImportFolder(ServerCommandSource src) {
        File dir = new File("config/itemmap/import");
        if (!dir.exists()) {
            dir.mkdirs();
            src.sendFeedback(() -> Text.literal(
                "[ItemMap] Created config/itemmap/import/ - drop PNG files there and run /im importfolder again."), false);
            return 1;
        }
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".png"));
        if (files == null || files.length == 0) {
            src.sendFeedback(() -> Text.literal(
                "[ItemMap] No PNG files found in config/itemmap/import/"), false);
            return 1;
        }
        int imported = 0;
        int failed = 0;
        for (File f : files) {
            String imageId = f.getName().replace(".png", "").replace(".PNG", "");
            try {
                byte[] png = java.nio.file.Files.readAllBytes(f.toPath());
                FrameManager.putImage(imageId, png);
                ItemMapMod.broadcastImage(src.getServer(), imageId, png);
                imported++;
            } catch (Exception e) {
                failed++;
                ItemMapMod.LOGGER.error("[ItemMap] Failed to import {}: {}", f.getName(), e.getMessage());
            }
        }
        if (imported > 0) FrameManager.saveAll();
        final int imp = imported, fail = failed;
        src.sendFeedback(() -> Text.literal(
            "[ItemMap] Imported " + imp + " image(s)" + (fail > 0 ? ", " + fail + " failed." : ".")), false);
        return 1;
    }

    private static int cmdGui(ServerCommandSource src) {
        if (!(src.getEntity() instanceof ServerPlayerEntity player)) {
            src.sendError(Text.literal("[ItemMap] Must be a player."));
            return 0;
        }
        // Send packet to open GUI on client - reuse FrameUpdatePayload with open_gui action
        // Look at current frame if any, otherwise open general GUI
        FrameData f = lookAtFrame(src);
        long frameId = f != null ? f.entityId : -1;
        FrameUpdatePayload pkt = new FrameUpdatePayload(
            "open_gui", frameId, "FLAT_2D", 2f, 1f, 0f, false, null, 0, null, false);
        ServerPlayNetworking.send(player, pkt);
        return 1;
    }

    private static int cmdTutorial(ServerCommandSource src) {
        src.sendFeedback(() -> Text.literal(
            "[ItemMap] ====== Tutorial ======\n" +
            "1. Place an item frame on a wall.\n" +
            "2. Open Creative -> Item Maps tab.\n" +
            "3. Find the item you want, e.g. Diamond Sword (flat) or Diamond Sword 3D (spinning).\n" +
            "4. Put it in the frame. Done - it shows the item texture.\n" +
            "5. Look at the frame and use /im commands to customize it.\n" +
            "Type /im help for all commands."
        ), false);
        return 1;
    }

    private static int cmdHelp(ServerCommandSource src) {
        src.sendFeedback(() -> Text.literal(
            "[ItemMap] ====== Commands ======\n" +
            "All commands: look at a frame first, then run the command.\n" +
            "\n" +
            "/im mode <flat2d|spin3d|render3d>\n" +
            "  flat2d   = item texture fills the whole frame (default)\n" +
            "  spin3d   = item spins like a dropped item\n" +
            "  render3d = item shown as 3D model, static\n" +
            "\n" +
            "/im spin <speed>    - how fast it spins (0.1=slow, 2=default, 10=fast)\n" +
            "/im scale <size>    - item size in frame (0.5=small, 1=default, 2=big)\n" +
            "/im padding <0-50>  - space around the item in percent (0=fills frame)\n" +
            "/im glow <true|false>      - adds a glow outline around the frame\n" +
            "/im invisible <true|false> - hides the wooden frame border\n" +
            "/im label <text>    - custom text below the item (use _ for spaces)\n" +
            "/im label none      - remove the label\n" +
            "/im bg <AARRGGBB>   - background color, e.g. 80FF0000 = semi-red\n" +
            "/im bg 00000000     - remove background\n" +
            "/im image <id>      - use a custom uploaded image instead of vanilla\n" +
            "/im image none      - go back to vanilla item texture\n" +
            "\n" +
            "/im upload <id> <url>  - download a PNG and save it as <id>\n" +
            "/im images             - list all uploaded images\n" +
            "\n" +
            "/im color <RRGGBB>    - set letter/item color on the frame\n" +
            "/im changebg <RRGGBB> - set background color (add AA prefix for transparency)\n" +
            "/im reset   - reset frame to defaults\n" +
            "/im remove  - delete all ItemMap data from this frame\n" +
            "/im info    - show current settings for this frame\n" +
            "/im list    - list all configured frames\n" +
            "/im undo    - undo last change\n" +
            "/im redo    - redo last undone change\n" +
            "/im reload  - reload from disk and re-sync all players\n" +
            "/im gui     - open the settings GUI for the frame you are looking at\n" +
            "/im tutorial - beginner guide\n" +
            "\n" +
            "Aliases: /im and /itemmap both work."
        ), false);
        return 1;
    }
}
