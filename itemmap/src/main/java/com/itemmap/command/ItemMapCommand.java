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
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ItemMapCommand {

    private static final SuggestionProvider<ServerCommandSource> FRAME_SUGGESTIONS =
        (ctx, builder) -> {
            // Suggest all known frame entity IDs
            for (long id : FrameManager.allIds())
                builder.suggest(String.valueOf(id));
            return builder.buildFuture();
        };

    private static final SuggestionProvider<ServerCommandSource> IMAGE_SUGGESTIONS =
        (ctx, builder) -> {
            for (String id : FrameManager.allImageIds()) builder.suggest(id);
            return builder.buildFuture();
        };

    private static final SuggestionProvider<ServerCommandSource> MODE_SUGGESTIONS =
        (ctx, builder) -> {
            for (String m : new String[]{"flat2d","render3d","spin3d"}) builder.suggest(m);
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

            // ── /im set <mode> [frameId] ──────────────────────────────────────
            .then(CommandManager.literal("set")
                .then(CommandManager.literal("mode")
                    .then(CommandManager.argument("mode", StringArgumentType.word())
                        .suggests(MODE_SUGGESTIONS)
                        .executes(ctx -> cmdSetMode(ctx.getSource(),
                            StringArgumentType.getString(ctx, "mode"), -1))
                        .then(CommandManager.argument("frameId", StringArgumentType.word())
                            .executes(ctx -> cmdSetMode(ctx.getSource(),
                                StringArgumentType.getString(ctx, "mode"),
                                parseLong(StringArgumentType.getString(ctx, "frameId")))))
                    )
                )
                .then(CommandManager.literal("spinspeed")
                    .then(CommandManager.argument("speed", FloatArgumentType.floatArg(0.1f, 100f))
                        .executes(ctx -> cmdSetFloat(ctx.getSource(), "spinSpeed",
                            FloatArgumentType.getFloat(ctx, "speed"), -1))
                        .then(CommandManager.argument("frameId", StringArgumentType.word())
                            .executes(ctx -> cmdSetFloat(ctx.getSource(), "spinSpeed",
                                FloatArgumentType.getFloat(ctx, "speed"),
                                parseLong(StringArgumentType.getString(ctx, "frameId")))))
                    )
                )
                .then(CommandManager.literal("scale")
                    .then(CommandManager.argument("scale", FloatArgumentType.floatArg(0.1f, 2.0f))
                        .executes(ctx -> cmdSetFloat(ctx.getSource(), "scale",
                            FloatArgumentType.getFloat(ctx, "scale"), -1))
                        .then(CommandManager.argument("frameId", StringArgumentType.word())
                            .executes(ctx -> cmdSetFloat(ctx.getSource(), "scale",
                                FloatArgumentType.getFloat(ctx, "scale"),
                                parseLong(StringArgumentType.getString(ctx, "frameId")))))
                    )
                )
                .then(CommandManager.literal("padding")
                    .then(CommandManager.argument("pct", FloatArgumentType.floatArg(0f, 50f))
                        .executes(ctx -> cmdSetFloat(ctx.getSource(), "padPct",
                            FloatArgumentType.getFloat(ctx, "pct"), -1))
                        .then(CommandManager.argument("frameId", StringArgumentType.word())
                            .executes(ctx -> cmdSetFloat(ctx.getSource(), "padPct",
                                FloatArgumentType.getFloat(ctx, "pct"),
                                parseLong(StringArgumentType.getString(ctx, "frameId")))))
                    )
                )
                .then(CommandManager.literal("glow")
                    .then(CommandManager.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> cmdSetBool(ctx.getSource(), "glowing",
                            BoolArgumentType.getBool(ctx, "value"), -1))
                        .then(CommandManager.argument("frameId", StringArgumentType.word())
                            .executes(ctx -> cmdSetBool(ctx.getSource(), "glowing",
                                BoolArgumentType.getBool(ctx, "value"),
                                parseLong(StringArgumentType.getString(ctx, "frameId")))))
                    )
                )
                .then(CommandManager.literal("invisible")
                    .then(CommandManager.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> cmdSetBool(ctx.getSource(), "invisible",
                            BoolArgumentType.getBool(ctx, "value"), -1))
                        .then(CommandManager.argument("frameId", StringArgumentType.word())
                            .executes(ctx -> cmdSetBool(ctx.getSource(), "invisible",
                                BoolArgumentType.getBool(ctx, "value"),
                                parseLong(StringArgumentType.getString(ctx, "frameId")))))
                    )
                )
                .then(CommandManager.literal("label")
                    .then(CommandManager.argument("text", StringArgumentType.greedyString())
                        .executes(ctx -> cmdSetLabel(ctx.getSource(),
                            StringArgumentType.getString(ctx, "text"), -1))
                        .then(CommandManager.argument("frameId", StringArgumentType.word())
                            .executes(ctx -> cmdSetLabel(ctx.getSource(),
                                StringArgumentType.getString(ctx, "text"),
                                parseLong(StringArgumentType.getString(ctx, "frameId")))))
                    )
                )
                .then(CommandManager.literal("bgcolor")
                    .then(CommandManager.argument("argb_hex", StringArgumentType.word())
                        .executes(ctx -> cmdSetBgColor(ctx.getSource(),
                            StringArgumentType.getString(ctx, "argb_hex"), -1))
                        .then(CommandManager.argument("frameId", StringArgumentType.word())
                            .executes(ctx -> cmdSetBgColor(ctx.getSource(),
                                StringArgumentType.getString(ctx, "argb_hex"),
                                parseLong(StringArgumentType.getString(ctx, "frameId")))))
                    )
                )
                .then(CommandManager.literal("image")
                    .then(CommandManager.argument("imageId", StringArgumentType.word())
                        .suggests(IMAGE_SUGGESTIONS)
                        .executes(ctx -> cmdSetImage(ctx.getSource(),
                            StringArgumentType.getString(ctx, "imageId"), -1))
                        .then(CommandManager.argument("frameId", StringArgumentType.word())
                            .executes(ctx -> cmdSetImage(ctx.getSource(),
                                StringArgumentType.getString(ctx, "imageId"),
                                parseLong(StringArgumentType.getString(ctx, "frameId")))))
                    )
                )
            )

            // ── /im upload <imageId> <url> ────────────────────────────────────
            .then(CommandManager.literal("upload")
                .then(CommandManager.argument("imageId", StringArgumentType.word())
                    .then(CommandManager.argument("url", StringArgumentType.greedyString())
                        .executes(ctx -> cmdUpload(ctx.getSource(),
                            StringArgumentType.getString(ctx, "imageId"),
                            StringArgumentType.getString(ctx, "url").trim()))
                    )
                )
            )

            // ── /im reset [frameId] ───────────────────────────────────────────
            .then(CommandManager.literal("reset")
                .executes(ctx -> cmdReset(ctx.getSource(), -1))
                .then(CommandManager.argument("frameId", StringArgumentType.word())
                    .executes(ctx -> cmdReset(ctx.getSource(),
                        parseLong(StringArgumentType.getString(ctx, "frameId"))))
                )
            )

            // ── /im remove [frameId] ──────────────────────────────────────────
            .then(CommandManager.literal("remove")
                .executes(ctx -> cmdRemove(ctx.getSource(), -1))
                .then(CommandManager.argument("frameId", StringArgumentType.word())
                    .executes(ctx -> cmdRemove(ctx.getSource(),
                        parseLong(StringArgumentType.getString(ctx, "frameId"))))
                )
            )

            // ── /im list ──────────────────────────────────────────────────────
            .then(CommandManager.literal("list")
                .executes(ctx -> cmdList(ctx.getSource()))
            )

            // ── /im images ────────────────────────────────────────────────────
            .then(CommandManager.literal("images")
                .executes(ctx -> cmdImages(ctx.getSource()))
            )

            // ── /im info [frameId] ────────────────────────────────────────────
            .then(CommandManager.literal("info")
                .executes(ctx -> cmdInfo(ctx.getSource(), -1))
                .then(CommandManager.argument("frameId", StringArgumentType.word())
                    .executes(ctx -> cmdInfo(ctx.getSource(),
                        parseLong(StringArgumentType.getString(ctx, "frameId"))))
                )
            )

            // ── /im undo ──────────────────────────────────────────────────────
            .then(CommandManager.literal("undo")
                .executes(ctx -> cmdUndo(ctx.getSource()))
            )

            // ── /im redo ──────────────────────────────────────────────────────
            .then(CommandManager.literal("redo")
                .executes(ctx -> cmdRedo(ctx.getSource()))
            )

            // ── /im reload ────────────────────────────────────────────────────
            .then(CommandManager.literal("reload")
                .executes(ctx -> cmdReload(ctx.getSource()))
            )

            // ── /im help ──────────────────────────────────────────────────────
            .then(CommandManager.literal("help")
                .executes(ctx -> cmdHelp(ctx.getSource()))
            );
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static long parseLong(String s) {
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return -1; }
    }

    /** Resolve the target frame: either by explicit frameId, or by what the player is looking at. */
    private static FrameData resolveFrame(ServerCommandSource src, long frameId) {
        if (frameId > 0) {
            FrameData d = FrameManager.get(frameId);
            if (d == null) {
                // Auto-create if we got a valid-looking ID
                return FrameManager.getOrCreate(frameId);
            }
            return d;
        }
        // Look at what the player is pointing at
        if (!(src.getEntity() instanceof ServerPlayerEntity player)) {
            src.sendError(Text.literal("§c[ItemMap] Must specify a frameId or look at a frame."));
            return null;
        }
        // Cast a ray to find the item frame entity the player is looking at
        net.minecraft.util.math.Vec3d eyePos = player.getEyePos();
        net.minecraft.util.math.Vec3d lookVec = player.getRotationVec(1.0f);
        net.minecraft.util.math.Vec3d reach = eyePos.add(lookVec.multiply(6.0));
        net.minecraft.util.math.Box searchBox = player.getBoundingBox()
            .stretch(lookVec.multiply(6.0)).expand(1.0);
        ItemFrameEntity closest = null;
        double closest_dist = 7.0;
        for (net.minecraft.entity.Entity e : player.getWorld().getOtherEntities(player, searchBox)) {
            if (!(e instanceof ItemFrameEntity ife)) continue;
            net.minecraft.util.math.Box bb = ife.getBoundingBox().expand(0.3);
            java.util.Optional<net.minecraft.util.math.Vec3d> inter = bb.raycast(eyePos, reach);
            if (inter.isPresent()) {
                double d = eyePos.squaredDistanceTo(inter.get());
                if (d < closest_dist) { closest_dist = d; closest = ife; }
            }
        }
        if (closest == null) {
            src.sendError(Text.literal("§c[ItemMap] Look at an item frame or provide a frameId."));
            return null;
        }
        return FrameManager.getOrCreate(closest.getId());
    }

    // ── Command implementations ───────────────────────────────────────────────

    private static int cmdSetMode(ServerCommandSource src, String modeStr, long frameId) {
        FrameData frame = resolveFrame(src, frameId);
        if (frame == null) return 0;

        FrameData before = frame.copy();
        FrameData.DisplayMode mode;
        switch (modeStr.toLowerCase()) {
            case "flat2d"   -> mode = FrameData.DisplayMode.FLAT_2D;
            case "render3d" -> mode = FrameData.DisplayMode.RENDER_3D;
            case "spin3d"   -> mode = FrameData.DisplayMode.SPIN_3D;
            default -> {
                src.sendError(Text.literal("§c[ItemMap] Invalid mode. Use: flat2d, render3d, spin3d"));
                return 0;
            }
        }
        frame.mode = mode;
        FrameManager.put(frame);
        FrameManager.saveAll();
        if (src.getEntity() instanceof ServerPlayerEntity p)
            UndoManager.push(p.getUuid(), before, frame);
        ItemMapMod.broadcastFrameUpdate(src.getServer(), frame);
        src.sendFeedback(() -> Text.literal("§a[ItemMap] Frame " + frame.entityId + " mode → §f" + mode.name()), false);
        return 1;
    }

    private static int cmdSetFloat(ServerCommandSource src, String field, float value, long frameId) {
        FrameData frame = resolveFrame(src, frameId);
        if (frame == null) return 0;
        FrameData before = frame.copy();
        switch (field) {
            case "spinSpeed" -> frame.spinSpeed = value;
            case "scale"     -> frame.scale     = value;
            case "padPct"    -> frame.padPct    = value;
        }
        FrameManager.put(frame);
        FrameManager.saveAll();
        if (src.getEntity() instanceof ServerPlayerEntity p)
            UndoManager.push(p.getUuid(), before, frame);
        ItemMapMod.broadcastFrameUpdate(src.getServer(), frame);
        src.sendFeedback(() -> Text.literal("§a[ItemMap] Frame " + frame.entityId + " " + field + " → §f" + value), false);
        return 1;
    }

    private static int cmdSetBool(ServerCommandSource src, String field, boolean value, long frameId) {
        FrameData frame = resolveFrame(src, frameId);
        if (frame == null) return 0;
        FrameData before = frame.copy();
        switch (field) {
            case "glowing"   -> frame.glowing   = value;
            case "invisible" -> frame.invisible  = value;
        }
        FrameManager.put(frame);
        FrameManager.saveAll();
        if (src.getEntity() instanceof ServerPlayerEntity p)
            UndoManager.push(p.getUuid(), before, frame);
        ItemMapMod.broadcastFrameUpdate(src.getServer(), frame);
        src.sendFeedback(() -> Text.literal("§a[ItemMap] Frame " + frame.entityId + " " + field + " → §f" + value), false);
        return 1;
    }

    private static int cmdSetLabel(ServerCommandSource src, String text, long frameId) {
        FrameData frame = resolveFrame(src, frameId);
        if (frame == null) return 0;
        FrameData before = frame.copy();
        frame.label = text.equalsIgnoreCase("none") ? null : text.replace("_", " ");
        FrameManager.put(frame);
        FrameManager.saveAll();
        if (src.getEntity() instanceof ServerPlayerEntity p)
            UndoManager.push(p.getUuid(), before, frame);
        ItemMapMod.broadcastFrameUpdate(src.getServer(), frame);
        src.sendFeedback(() -> Text.literal("§a[ItemMap] Label → §f" + frame.label), false);
        return 1;
    }

    private static int cmdSetBgColor(ServerCommandSource src, String hex, long frameId) {
        FrameData frame = resolveFrame(src, frameId);
        if (frame == null) return 0;
        FrameData before = frame.copy();
        try {
            String clean = hex.startsWith("#") ? hex.substring(1) : hex;
            frame.bgColor = (int) Long.parseLong(clean, 16);
        } catch (NumberFormatException e) {
            src.sendError(Text.literal("§c[ItemMap] Invalid hex color. Example: FF000000 or #80FF0000"));
            return 0;
        }
        FrameManager.put(frame);
        FrameManager.saveAll();
        if (src.getEntity() instanceof ServerPlayerEntity p)
            UndoManager.push(p.getUuid(), before, frame);
        ItemMapMod.broadcastFrameUpdate(src.getServer(), frame);
        src.sendFeedback(() -> Text.literal("§a[ItemMap] Background color set."), false);
        return 1;
    }

    private static int cmdSetImage(ServerCommandSource src, String imageId, long frameId) {
        FrameData frame = resolveFrame(src, frameId);
        if (frame == null) return 0;
        if (!imageId.equalsIgnoreCase("none") && FrameManager.getImage(imageId) == null) {
            src.sendError(Text.literal("§c[ItemMap] Image '" + imageId + "' not found. Use /im upload first."));
            return 0;
        }
        FrameData before = frame.copy();
        frame.customImageId = imageId.equalsIgnoreCase("none") ? null : imageId;
        FrameManager.put(frame);
        FrameManager.saveAll();
        if (src.getEntity() instanceof ServerPlayerEntity p)
            UndoManager.push(p.getUuid(), before, frame);
        ItemMapMod.broadcastFrameUpdate(src.getServer(), frame);
        src.sendFeedback(() -> Text.literal("§a[ItemMap] Custom image → §f" + frame.customImageId), false);
        return 1;
    }

    private static int cmdUpload(ServerCommandSource src, String imageId, String url) {
        src.sendFeedback(() -> Text.literal("§e[ItemMap] Downloading image '" + imageId + "'..."), false);
        Thread t = new Thread(() -> {
            try {
                HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10)).build();
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET().build();
                HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
                if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                    src.sendFeedback(() -> Text.literal("§c[ItemMap] HTTP " + resp.statusCode()), false);
                    return;
                }
                byte[] png = resp.body();
                if (png == null || png.length == 0) {
                    src.sendFeedback(() -> Text.literal("§c[ItemMap] Empty response from URL."), false);
                    return;
                }
                FrameManager.putImage(imageId, png);
                FrameManager.saveAll();
                ItemMapMod.broadcastImage(src.getServer(), imageId, png);
                src.sendFeedback(() -> Text.literal("§a[ItemMap] Image '" + imageId + "' uploaded and synced (" + png.length / 1024 + " KB)."), false);
            } catch (Exception e) {
                src.sendFeedback(() -> Text.literal("§c[ItemMap] Failed: " + e.getMessage()), false);
            }
        }, "ItemMap-Upload");
        t.setDaemon(true);
        t.start();
        return 1;
    }

    private static int cmdReset(ServerCommandSource src, long frameId) {
        FrameData frame = resolveFrame(src, frameId);
        if (frame == null) return 0;
        FrameData before = frame.copy();
        // Reset to defaults
        frame.mode          = FrameData.DisplayMode.FLAT_2D;
        frame.spinSpeed     = 2.0f;
        frame.scale         = 1.0f;
        frame.padPct        = 0f;
        frame.glowing       = false;
        frame.label         = null;
        frame.bgColor       = 0;
        frame.customImageId = null;
        frame.invisible     = false;
        FrameManager.put(frame);
        FrameManager.saveAll();
        if (src.getEntity() instanceof ServerPlayerEntity p)
            UndoManager.push(p.getUuid(), before, frame);
        ItemMapMod.broadcastFrameUpdate(src.getServer(), frame);
        src.sendFeedback(() -> Text.literal("§a[ItemMap] Frame " + frame.entityId + " reset to defaults."), false);
        return 1;
    }

    private static int cmdRemove(ServerCommandSource src, long frameId) {
        long targetId = frameId;
        if (targetId <= 0) {
            FrameData frame = resolveFrame(src, -1);
            if (frame == null) return 0;
            targetId = frame.entityId;
        }
        final long fid = targetId;
        boolean removed = FrameManager.remove(fid);
        if (!removed) {
            src.sendError(Text.literal("§c[ItemMap] No ItemMap data for frame " + fid));
            return 0;
        }
        FrameManager.saveAll();
        ItemMapMod.broadcastFrameRemove(src.getServer(), fid);
        src.sendFeedback(() -> Text.literal("§a[ItemMap] Removed ItemMap data for frame " + fid + "."), false);
        return 1;
    }

    private static int cmdList(ServerCommandSource src) {
        Collection<FrameData> all = FrameManager.all();
        if (all.isEmpty()) {
            src.sendFeedback(() -> Text.literal("§7[ItemMap] No frames configured."), false);
            return 1;
        }
        src.sendFeedback(() -> Text.literal("§6[ItemMap] §e" + all.size() + " frame(s):"), false);
        for (FrameData d : all) {
            final FrameData fd = d;
            src.sendFeedback(() -> Text.literal(
                "§7  #" + fd.entityId + " §f" + fd.mode.name()
                + " §7scale=" + fd.scale + " spin=" + fd.spinSpeed
                + (fd.label != null ? " label=§f" + fd.label : "")
                + (fd.customImageId != null ? " §bimg=" + fd.customImageId : "")), false);
        }
        return 1;
    }

    private static int cmdImages(ServerCommandSource src) {
        Set<String> ids = FrameManager.allImageIds();
        if (ids.isEmpty()) {
            src.sendFeedback(() -> Text.literal("§7[ItemMap] No custom images uploaded."), false);
            return 1;
        }
        src.sendFeedback(() -> Text.literal("§6[ItemMap] §e" + ids.size() + " image(s):"), false);
        for (String id : ids) {
            byte[] png = FrameManager.getImage(id);
            int kb = png != null ? png.length / 1024 : 0;
            src.sendFeedback(() -> Text.literal("§7  §f" + id + " §7(" + kb + " KB)"), false);
        }
        return 1;
    }

    private static int cmdInfo(ServerCommandSource src, long frameId) {
        FrameData frame = resolveFrame(src, frameId);
        if (frame == null) return 0;
        final FrameData fd = frame;
        src.sendFeedback(() -> Text.literal(
            "§6[ItemMap] Frame §e" + fd.entityId + "\n" +
            "§7  Mode: §f"       + fd.mode.name()    + "\n" +
            "§7  SpinSpeed: §f"  + fd.spinSpeed       + "\n" +
            "§7  Scale: §f"      + fd.scale           + "\n" +
            "§7  Padding: §f"    + fd.padPct + "%"   + "\n" +
            "§7  Glow: §f"       + fd.glowing         + "\n" +
            "§7  Invisible: §f"  + fd.invisible        + "\n" +
            "§7  Label: §f"      + (fd.label != null ? fd.label : "(item name)") + "\n" +
            "§7  BgColor: §f"    + String.format("#%08X", fd.bgColor) + "\n" +
            "§7  Image: §f"      + (fd.customImageId != null ? fd.customImageId : "(vanilla)")
        ), false);
        return 1;
    }

    private static int cmdUndo(ServerCommandSource src) {
        if (!(src.getEntity() instanceof ServerPlayerEntity player)) {
            src.sendError(Text.literal("§c[ItemMap] Must be a player."));
            return 0;
        }
        FrameData restored = UndoManager.undo(player.getUuid());
        if (restored == null) {
            src.sendError(Text.literal("§c[ItemMap] Nothing to undo."));
            return 0;
        }
        FrameManager.put(restored);
        FrameManager.saveAll();
        ItemMapMod.broadcastFrameUpdate(src.getServer(), restored);
        src.sendFeedback(() -> Text.literal("§a[ItemMap] Undone."), false);
        return 1;
    }

    private static int cmdRedo(ServerCommandSource src) {
        if (!(src.getEntity() instanceof ServerPlayerEntity player)) {
            src.sendError(Text.literal("§c[ItemMap] Must be a player."));
            return 0;
        }
        FrameData restored = UndoManager.redo(player.getUuid());
        if (restored == null) {
            src.sendError(Text.literal("§c[ItemMap] Nothing to redo."));
            return 0;
        }
        FrameManager.put(restored);
        FrameManager.saveAll();
        ItemMapMod.broadcastFrameUpdate(src.getServer(), restored);
        src.sendFeedback(() -> Text.literal("§a[ItemMap] Redone."), false);
        return 1;
    }

    private static int cmdReload(ServerCommandSource src) {
        src.sendFeedback(() -> Text.literal("§e[ItemMap] Reloading..."), false);
        FrameManager.clearAll();
        FrameManager.loadAll();

        // Re-sync all online players
        List<FrameSyncPayload.FrameEntry> entries = new ArrayList<>();
        for (FrameData d : FrameManager.all())
            entries.add(FrameSyncPayload.fromData(d));
        FrameSyncPayload syncPkt = new FrameSyncPayload(entries);

        for (ServerPlayerEntity p : src.getServer().getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(p, syncPkt);
            // Re-queue all images
            ConcurrentLinkedQueue<ImagePayload> queue = new ConcurrentLinkedQueue<>();
            for (String imgId : FrameManager.allImageIds()) {
                byte[] png = FrameManager.getImage(imgId);
                if (png != null) queue.add(new ImagePayload(imgId, png));
            }
            ItemMapMod.queueImagesForPlayer(p, queue);
        }

        src.sendFeedback(() -> Text.literal("§a[ItemMap] Reloaded. " +
            FrameManager.all().size() + " frame(s), " +
            FrameManager.allImageIds().size() + " image(s)."), false);
        return 1;
    }

    private static int cmdHelp(ServerCommandSource src) {
        src.sendFeedback(() -> Text.literal(
            "§6§l[ItemMap] Commands\n" +
            "§e/im set mode <flat2d|render3d|spin3d> §7[frameId]\n" +
            "§e/im set spinspeed <0.1-100> §7[frameId]\n" +
            "§e/im set scale <0.1-2.0> §7[frameId]\n" +
            "§e/im set padding <0-50> §7[frameId]\n" +
            "§e/im set glow <true|false> §7[frameId]\n" +
            "§e/im set invisible <true|false> §7[frameId]\n" +
            "§e/im set label <text|none> §7[frameId]\n" +
            "§e/im set bgcolor <AARRGGBB> §7[frameId]\n" +
            "§e/im set image <imageId|none> §7[frameId]\n" +
            "§e/im upload <imageId> <url>\n" +
            "§e/im reset §7[frameId] — reset to defaults\n" +
            "§e/im remove §7[frameId]\n" +
            "§e/im list §7— list all frames\n" +
            "§e/im images §7— list uploaded images\n" +
            "§e/im info §7[frameId]\n" +
            "§e/im undo §7/ §e/im redo\n" +
            "§e/im reload\n" +
            "§7Aliases: /itemmap, /im\n" +
            "§7Tip: Omit frameId to target the frame you're looking at."
        ), false);
        return 1;
    }
}
