package com.customblocks.command;

import com.customblocks.CustomBlocksMod;
import com.customblocks.SlotManager;
import com.customblocks.block.SlotBlock;
import com.customblocks.item.RotateStickItem;
import com.customblocks.network.FullSyncPayload;
import com.customblocks.network.SlotUpdatePayload;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Property;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class CustomBlockCommand {

    private static final SuggestionProvider<ServerCommandSource> BLOCK_SUGGESTIONS =
            (ctx, builder) -> {
                for (String id : SlotManager.allCustomIds()) builder.suggest(id);
                return builder.buildFuture();
            };

    private static final SuggestionProvider<ServerCommandSource> SOUND_SUGGESTIONS =
            (ctx, builder) -> {
                for (String s : new String[]{"stone","wood","grass","metal","glass","sand","wool"})
                    builder.suggest(s);
                return builder.buildFuture();
            };

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, reg, env) -> {
            // Build the command tree once, then register under both /customblock and /cb
            LiteralArgumentBuilder<ServerCommandSource> tree = buildTree("customblock");
            LiteralArgumentBuilder<ServerCommandSource> alias = buildTree("cb");
            dispatcher.register(tree);
            dispatcher.register(alias);
        });
    }

    /** Builds the full command tree under a given root literal name. */
    private static LiteralArgumentBuilder<ServerCommandSource> buildTree(String root) {
        return CommandManager.literal(root)
            .requires(src -> src.hasPermissionLevel(2))

            .then(CommandManager.literal("createurl")
                .then(CommandManager.argument("id", StringArgumentType.word())
                    .then(CommandManager.argument("name", StringArgumentType.word())
                        .then(CommandManager.argument("url", StringArgumentType.greedyString())
                            .executes(ctx -> cmdCreate(ctx.getSource(),
                                StringArgumentType.getString(ctx, "id"),
                                StringArgumentType.getString(ctx, "name").replace("_", " "),
                                StringArgumentType.getString(ctx, "url").trim()))
                        )
                    )
                )
            )

            .then(CommandManager.literal("delete")
                .executes(ctx -> usage(ctx.getSource(), "delete"))
                .then(CommandManager.argument("id", StringArgumentType.word())
                    .suggests(BLOCK_SUGGESTIONS)
                    .executes(ctx -> cmdDelete(ctx.getSource(),
                        StringArgumentType.getString(ctx, "id")))
                )
            )

            .then(CommandManager.literal("rename")
                .executes(ctx -> usage(ctx.getSource(), "rename"))
                .then(CommandManager.argument("id", StringArgumentType.word())
                    .suggests(BLOCK_SUGGESTIONS)
                    .then(CommandManager.argument("newname", StringArgumentType.greedyString())
                        .executes(ctx -> cmdRename(ctx.getSource(),
                            StringArgumentType.getString(ctx, "id"),
                            StringArgumentType.getString(ctx, "newname").replace("_", " ")))
                    )
                )
            )

            .then(CommandManager.literal("retexture")
                .executes(ctx -> usage(ctx.getSource(), "retexture"))
                .then(CommandManager.argument("id", StringArgumentType.word())
                    .suggests(BLOCK_SUGGESTIONS)
                    .then(CommandManager.argument("url", StringArgumentType.greedyString())
                        .executes(ctx -> cmdRetexture(ctx.getSource(),
                            StringArgumentType.getString(ctx, "id"),
                            StringArgumentType.getString(ctx, "url").trim()))
                    )
                )
            )

            .then(CommandManager.literal("give")
                .executes(ctx -> usage(ctx.getSource(), "give"))
                .then(CommandManager.argument("id", StringArgumentType.word())
                    .suggests(BLOCK_SUGGESTIONS)
                    .executes(ctx -> cmdGive(ctx.getSource(),
                        StringArgumentType.getString(ctx, "id"), 1, null))
                    .then(CommandManager.argument("amount", IntegerArgumentType.integer(1, 64))
                        .executes(ctx -> cmdGive(ctx.getSource(),
                            StringArgumentType.getString(ctx, "id"),
                            IntegerArgumentType.getInteger(ctx, "amount"), null))
                        .then(CommandManager.argument("player", EntityArgumentType.players())
                            .executes(ctx -> cmdGive(ctx.getSource(),
                                StringArgumentType.getString(ctx, "id"),
                                IntegerArgumentType.getInteger(ctx, "amount"),
                                EntityArgumentType.getPlayers(ctx, "player"))))
                    )
                    .then(CommandManager.argument("player", EntityArgumentType.players())
                        .executes(ctx -> cmdGive(ctx.getSource(),
                            StringArgumentType.getString(ctx, "id"), 1,
                            EntityArgumentType.getPlayers(ctx, "player")))
                    )
                )
            )

            .then(CommandManager.literal("setglow")
                .executes(ctx -> usage(ctx.getSource(), "setglow"))
                .then(CommandManager.argument("id", StringArgumentType.word())
                    .suggests(BLOCK_SUGGESTIONS)
                    .then(CommandManager.argument("level", IntegerArgumentType.integer(0, 15))
                        .executes(ctx -> cmdSetGlow(ctx.getSource(),
                            StringArgumentType.getString(ctx, "id"),
                            IntegerArgumentType.getInteger(ctx, "level")))
                    )
                )
            )

            .then(CommandManager.literal("sethardness")
                .executes(ctx -> usage(ctx.getSource(), "sethardness"))
                .then(CommandManager.argument("id", StringArgumentType.word())
                    .suggests(BLOCK_SUGGESTIONS)
                    .then(CommandManager.argument("hardness", FloatArgumentType.floatArg(-1f, 50f))
                        .executes(ctx -> cmdSetHardness(ctx.getSource(),
                            StringArgumentType.getString(ctx, "id"),
                            FloatArgumentType.getFloat(ctx, "hardness")))
                    )
                )
            )

            .then(CommandManager.literal("setsound")
                .executes(ctx -> usage(ctx.getSource(), "setsound"))
                .then(CommandManager.argument("id", StringArgumentType.word())
                    .suggests(BLOCK_SUGGESTIONS)
                    .then(CommandManager.argument("type", StringArgumentType.word())
                        .suggests(SOUND_SUGGESTIONS)
                        .executes(ctx -> cmdSetSound(ctx.getSource(),
                            StringArgumentType.getString(ctx, "id"),
                            StringArgumentType.getString(ctx, "type")))
                    )
                )
            )

            .then(CommandManager.literal("settabicon")
                .executes(ctx -> usage(ctx.getSource(), "settabicon"))
                .then(CommandManager.argument("url", StringArgumentType.greedyString())
                    .executes(ctx -> cmdSetTabIcon(ctx.getSource(),
                        StringArgumentType.getString(ctx, "url").trim()))
                )
            )

            .then(CommandManager.literal("export")
                .executes(ctx -> cmdExport(ctx.getSource()))
            )

            .then(CommandManager.literal("importfolder")
                .executes(ctx -> cmdImportFolder(ctx.getSource()))
            )

            .then(CommandManager.literal("list")
                .executes(ctx -> cmdList(ctx.getSource()))
            )

            .then(CommandManager.literal("reload")
                .executes(ctx -> cmdReload(ctx.getSource()))
            )

            .then(CommandManager.literal("undo")
                .executes(ctx -> cmdUndo(ctx.getSource()))
            )

            .then(CommandManager.literal("redo")
                .executes(ctx -> cmdRedo(ctx.getSource()))
            )

            .then(CommandManager.literal("debugstick")
                .executes(ctx -> cmdDebugStick(ctx.getSource(), null))
                .then(CommandManager.argument("player", EntityArgumentType.players())
                    .executes(ctx -> cmdDebugStick(ctx.getSource(),
                        EntityArgumentType.getPlayers(ctx, "player")))
                )
            )

            .then(CommandManager.literal("rotate")
                .executes(ctx -> cmdRotate(ctx.getSource(), false))
                .then(CommandManager.literal("reverse")
                    .executes(ctx -> cmdRotate(ctx.getSource(), true))
                )
            )

            .then(CommandManager.literal("help")
                .executes(ctx -> cmdHelp(ctx.getSource()))
            );
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Command implementations
    // ═══════════════════════════════════════════════════════════════════════════

    private static int cmdCreate(ServerCommandSource src, String rawId, String name, String url) {
        String id = sanitize(rawId);
        if (id.isEmpty()) { src.sendError(Text.literal("§cInvalid ID.")); return 0; }
        if (SlotManager.hasId(id)) { src.sendError(Text.literal("§c'" + id + "' already exists.")); return 0; }
        if (SlotManager.freeSlots() == 0) { src.sendError(Text.literal("§cAll " + SlotManager.MAX_SLOTS + " slots are full!")); return 0; }
        src.sendMessage(Text.literal("§e[CustomBlocks] Downloading..."));
        MinecraftServer server = src.getServer();
        UUID uuid = playerUuid(src);
        final String fId = id, fName = name;
        thread(() -> {
            try {
                byte[] bytes = download(url);
                server.execute(() -> {
                    SlotManager.SlotData d = SlotManager.assign(fId, fName, bytes);
                    if (d == null) { src.sendError(Text.literal("§cNo free slots!")); return; }
                    SlotManager.saveAll();
                    CustomBlocksMod.broadcastUpdate(server,
                        new SlotUpdatePayload("add", d.index, fId, fName, bytes, d.lightLevel, d.hardness, d.soundType));
                    if (uuid != null) UndoManager.record(uuid, UndoManager.UndoEntry.forCreate(fId));
                    src.sendMessage(Text.literal("§a[CustomBlocks] '" + fName + "' created! §7(slot " + d.index + ")"));
                });
            } catch (Exception e) {
                server.execute(() -> src.sendError(Text.literal("§c[CustomBlocks] Download failed: " + e.getMessage())));
            }
        });
        return 1;
    }

    private static int cmdDelete(ServerCommandSource src, String id) {
        if (!SlotManager.hasId(id)) { src.sendError(notFound(id)); return 0; }
        SlotManager.SlotData d = SlotManager.getById(id);
        UUID uuid = playerUuid(src);
        if (uuid != null) UndoManager.record(uuid, UndoManager.UndoEntry.forDelete(d));
        SlotManager.remove(id);
        SlotManager.saveAll();
        CustomBlocksMod.broadcastUpdate(src.getServer(),
            new SlotUpdatePayload("remove", d.index, id, null, null, 0, 0, "stone"));
        src.sendMessage(Text.literal("§a[CustomBlocks] '" + id + "' deleted."));
        return 1;
    }

    private static int cmdRename(ServerCommandSource src, String id, String newName) {
        if (!SlotManager.hasId(id)) { src.sendError(notFound(id)); return 0; }
        SlotManager.SlotData d = SlotManager.getById(id);
        UUID uuid = playerUuid(src);
        if (uuid != null) UndoManager.record(uuid, UndoManager.UndoEntry.forRename(id, d.displayName));
        SlotManager.rename(id, newName);
        SlotManager.saveAll();
        CustomBlocksMod.broadcastUpdate(src.getServer(),
            new SlotUpdatePayload("rename", d.index, id, newName, null, 0, 0, "stone"));
        src.sendMessage(Text.literal("§a[CustomBlocks] Renamed to '" + newName + "'."));
        return 1;
    }

    private static int cmdRetexture(ServerCommandSource src, String id, String url) {
        if (!SlotManager.hasId(id)) { src.sendError(notFound(id)); return 0; }
        src.sendMessage(Text.literal("§e[CustomBlocks] Downloading texture..."));
        MinecraftServer server = src.getServer();
        UUID uuid = playerUuid(src);
        final String fId = id;
        thread(() -> {
            try {
                byte[] bytes = download(url);
                server.execute(() -> {
                    SlotManager.SlotData d = SlotManager.getById(fId);
                    if (d == null) { src.sendError(notFound(fId)); return; }
                    if (uuid != null) UndoManager.record(uuid, UndoManager.UndoEntry.forRetexture(fId, d.texture));
                    SlotManager.updateTexture(fId, bytes);
                    SlotManager.saveAll();
                    CustomBlocksMod.broadcastUpdate(server,
                        new SlotUpdatePayload("retexture", d.index, fId, null, bytes, d.lightLevel, d.hardness, d.soundType));
                    src.sendMessage(Text.literal("§a[CustomBlocks] Texture updated for '" + fId + "'."));
                });
            } catch (Exception e) {
                server.execute(() -> src.sendError(Text.literal("§c[CustomBlocks] Download failed: " + e.getMessage())));
            }
        });
        return 1;
    }

    private static int cmdGive(ServerCommandSource src, String id, int amount,
                                Collection<ServerPlayerEntity> targets) {
        SlotManager.SlotData d = SlotManager.getById(id);
        if (d == null) { src.sendError(notFound(id)); return 0; }
        SlotBlock.SlotItem item = CustomBlocksMod.SLOT_ITEMS[d.index];
        ItemStack stack = new ItemStack(item, Math.max(1, Math.min(64, amount)));
        if (targets == null || targets.isEmpty()) {
            try {
                ServerPlayerEntity self = src.getPlayerOrThrow();
                self.getInventory().insertStack(stack.copy());
                src.sendMessage(Text.literal("§a[CustomBlocks] Given " + amount + "x '" + d.displayName + "' to you."));
            } catch (Exception ex) {
                src.sendError(Text.literal("§cRun as a player or specify a target."));
            }
        } else {
            for (ServerPlayerEntity p : targets) {
                p.getInventory().insertStack(stack.copy());
                p.sendMessage(Text.literal("§a[CustomBlocks] You received " + amount + "x '" + d.displayName + "'."));
            }
            src.sendMessage(Text.literal("§a[CustomBlocks] Gave " + amount + "x to " + targets.size() + " player(s)."));
        }
        return 1;
    }

    private static int cmdSetGlow(ServerCommandSource src, String id, int level) {
        if (!SlotManager.hasId(id)) { src.sendError(notFound(id)); return 0; }
        SlotManager.SlotData d = SlotManager.getById(id);
        UUID uuid = playerUuid(src);
        if (uuid != null) UndoManager.record(uuid, UndoManager.UndoEntry.forSetGlow(id, d.lightLevel));
        SlotManager.setLightLevel(id, level);
        SlotManager.saveAll();
        CustomBlocksMod.broadcastUpdate(src.getServer(),
            new SlotUpdatePayload("setprop", d.index, id, null, null, level, d.hardness, d.soundType));
        src.sendMessage(Text.literal("§a[CustomBlocks] '" + id + "' glow set to " + level + "."));
        return 1;
    }

    private static int cmdSetHardness(ServerCommandSource src, String id, float val) {
        if (!SlotManager.hasId(id)) { src.sendError(notFound(id)); return 0; }
        SlotManager.SlotData d = SlotManager.getById(id);
        UUID uuid = playerUuid(src);
        if (uuid != null) UndoManager.record(uuid, UndoManager.UndoEntry.forSetHardness(id, d.hardness));
        SlotManager.setHardness(id, val);
        SlotManager.saveAll();
        String label = val < 0 ? "Unbreakable" : val == 0 ? "Instant break" : String.valueOf(val);
        CustomBlocksMod.broadcastUpdate(src.getServer(),
            new SlotUpdatePayload("setprop", d.index, id, null, null, d.lightLevel, val, d.soundType));
        src.sendMessage(Text.literal("§a[CustomBlocks] '" + id + "' hardness: " + label + "."));
        return 1;
    }

    private static int cmdSetSound(ServerCommandSource src, String id, String type) {
        if (!SlotManager.hasId(id)) { src.sendError(notFound(id)); return 0; }
        String[] valid = {"stone","wood","grass","metal","glass","sand","wool"};
        boolean ok = false;
        for (String v : valid) if (v.equals(type)) { ok = true; break; }
        if (!ok) { src.sendError(Text.literal("§cValid sounds: stone, wood, grass, metal, glass, sand, wool")); return 0; }
        SlotManager.SlotData d = SlotManager.getById(id);
        UUID uuid = playerUuid(src);
        if (uuid != null) UndoManager.record(uuid, UndoManager.UndoEntry.forSetSound(id, d.soundType));
        SlotManager.setSoundType(id, type);
        SlotManager.saveAll();
        CustomBlocksMod.broadcastUpdate(src.getServer(),
            new SlotUpdatePayload("setprop", d.index, id, null, null, d.lightLevel, d.hardness, type));
        src.sendMessage(Text.literal("§a[CustomBlocks] '" + id + "' sound: " + type + "."));
        return 1;
    }

    private static int cmdSetTabIcon(ServerCommandSource src, String url) {
        src.sendMessage(Text.literal("§e[CustomBlocks] Downloading tab icon..."));
        MinecraftServer server = src.getServer();
        UUID uuid = playerUuid(src);
        thread(() -> {
            try {
                byte[] bytes = download(url);
                server.execute(() -> {
                    if (uuid != null)
                        UndoManager.record(uuid, UndoManager.UndoEntry.forSetTabIcon(SlotManager.getTabIconTexture()));
                    SlotManager.setTabIconTexture(bytes);
                    if (!SlotManager.hasId("tab_icon")) SlotManager.assign("tab_icon", "Tab Icon", bytes);
                    else SlotManager.updateTexture("tab_icon", bytes);
                    SlotManager.saveAll();
                    SlotManager.SlotData d = SlotManager.getById("tab_icon");
                    if (d != null)
                        CustomBlocksMod.broadcastUpdate(server,
                            new SlotUpdatePayload("add", d.index, "tab_icon", "Tab Icon", bytes, 0, 1.5f, "stone"));
                    CustomBlocksMod.broadcastUpdate(server,
                        new SlotUpdatePayload("tabicon", -1, null, null, bytes, 0, 0, "stone"));
                    src.sendMessage(Text.literal("§a[CustomBlocks] Tab icon updated!"));
                });
            } catch (Exception e) {
                server.execute(() -> src.sendError(Text.literal("§c[CustomBlocks] Download failed: " + e.getMessage())));
            }
        });
        return 1;
    }

    // ── /customblock reload ──────────────────────────────────────────────────
    /**
     * Full reload:
     *  1. Re-reads slots.json from disk
     *  2. Scans config/customblocks/ for any PNG files not yet tracked and imports them
     *  3. Heals textures that are in memory but missing from disk
     *  4. Sends a fresh FullSyncPayload to every online player (metadata)
     *  5. Re-queues all texture packets so every client gets every image
     */
    private static int cmdReload(ServerCommandSource src) {
        src.sendMessage(Text.literal("§e[CustomBlocks] Reloading — rescanning config, syncing all images to all players..."));
        MinecraftServer server = src.getServer();
        thread(() -> {
            try {
                File configDir = new File("config/customblocks");
                configDir.mkdirs();

                // ── Step 1: heal textures in memory that are missing from disk ──
                int healed = 0;
                for (SlotManager.SlotData d : SlotManager.allSlots()) {
                    File f = new File(configDir, d.slotKey() + ".png");
                    if (!f.exists() && d.texture != null && d.texture.length > 0) {
                        try { Files.write(f.toPath(), d.texture); healed++; }
                        catch (IOException ignored) {}
                    }
                }

                // ── Step 2: reload slots.json from disk ──
                final int finalHealed = healed;
                server.execute(() -> {
                    SlotManager.loadAll();

                    // ── Step 3: scan for loose PNGs in config dir not yet in slots ──
                    File[] pngFiles = configDir.listFiles((dir, n) ->
                            n.toLowerCase().endsWith(".png") &&
                            !n.equals("tab_icon.png") &&
                            !n.startsWith("slot_") &&
                            !n.equals("export.json"));
                    int autoImportedCount = 0;
                    if (pngFiles != null) {
                        Arrays.sort(pngFiles, Comparator.comparing(File::getName));
                        for (File png : pngFiles) {
                            String raw   = png.getName().replaceAll("(?i)\\.png$", "");
                            String id    = raw.toLowerCase().replaceAll("[^a-z0-9_]", "_");
                            String dname = Arrays.stream(raw.replace("_", " ").split(" "))
                                .map(w -> w.isEmpty() ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1).toLowerCase())
                                .collect(java.util.stream.Collectors.joining(" "));
                            if (SlotManager.hasId(id) || SlotManager.freeSlots() == 0) continue;
                            try {
                                byte[] b = Files.readAllBytes(png.toPath());
                                SlotManager.SlotData d = SlotManager.assign(id, dname, b);
                                if (d != null) autoImportedCount++;
                            } catch (IOException ignored) {}
                        }
                        if (autoImportedCount > 0) SlotManager.saveAll();
                    }
                    final int autoImported = autoImportedCount;

                    // ── Step 4: build FullSyncPayload (metadata only, no textures) ──
                    List<FullSyncPayload.SlotEntry> meta = new ArrayList<>();
                    for (SlotManager.SlotData d : SlotManager.allSlots()) {
                        meta.add(new FullSyncPayload.SlotEntry(
                                d.index, d.customId, d.displayName, null,
                                d.lightLevel, d.hardness, d.soundType));
                    }
                    FullSyncPayload syncPkt = new FullSyncPayload(meta, SlotManager.getTabIconTexture());

                    // ── Step 5: re-sync every online player ──
                    for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                        // Send metadata immediately
                        ServerPlayNetworking.send(player, syncPkt);
                        // Re-queue ALL textures so client gets every image fresh
                        ConcurrentLinkedQueue<SlotUpdatePayload> q = new ConcurrentLinkedQueue<>();
                        SlotManager.allSlots().stream()
                                .filter(d -> d.texture != null && d.texture.length > 0)
                                .sorted(Comparator.comparingInt(d -> d.index))
                                .forEach(d -> q.add(new SlotUpdatePayload("add", d.index, d.customId,
                                        d.displayName, d.texture, d.lightLevel, d.hardness, d.soundType)));
                        // Also re-send tab icon if present
                        if (SlotManager.getTabIconTexture() != null) {
                            SlotManager.SlotData iconData = SlotManager.getById("tab_icon");
                            if (iconData != null)
                                q.add(new SlotUpdatePayload("tabicon", -1, null, null,
                                        SlotManager.getTabIconTexture(), 0, 0, "stone"));
                        }
                        CustomBlocksMod.queueTexturesForPlayer(player, q);
                    }

                    // ── Report ──
                    StringBuilder msg = new StringBuilder("§a[CustomBlocks] Reload complete — §f")
                            .append(SlotManager.usedSlots()).append(" block(s) loaded");
                    if (finalHealed  > 0) msg.append("§e, ").append(finalHealed).append(" texture(s) restored to disk");
                    if (autoImported > 0) msg.append("§b, ").append(autoImported).append(" new PNG(s) auto-imported");
                    msg.append("§7. All players re-synced with all images.");
                    src.sendMessage(Text.literal(msg.toString()));
                });
            } catch (Exception e) {
                server.execute(() -> src.sendError(Text.literal("§c[CustomBlocks] Reload error: " + e.getMessage())));
            }
        });
        return 1;
    }

    // ── /customblock undo ────────────────────────────────────────────────────
    private static int cmdUndo(ServerCommandSource src) {
        UUID uuid = playerUuid(src);
        if (uuid == null) { src.sendError(Text.literal("§cUndo must be run as a player.")); return 0; }
        if (!UndoManager.hasUndo(uuid)) { src.sendError(Text.literal("§cNothing to undo.")); return 0; }

        UndoManager.UndoEntry e = UndoManager.popUndo(uuid);
        MinecraftServer server = src.getServer();

        // Compute the "redo" snapshot BEFORE we apply the undo
        UndoManager.UndoEntry redoEntry = buildRedoEntry(e);

        boolean applied = applyUndoEntry(e, src, server);
        if (applied && redoEntry != null)
            UndoManager.pushRedo(uuid, redoEntry);
        return applied ? 1 : 0;
    }

    // ── /customblock redo ────────────────────────────────────────────────────
    private static int cmdRedo(ServerCommandSource src) {
        UUID uuid = playerUuid(src);
        if (uuid == null) { src.sendError(Text.literal("§cRedo must be run as a player.")); return 0; }
        if (!UndoManager.hasRedo(uuid)) { src.sendError(Text.literal("§cNothing to redo.")); return 0; }

        UndoManager.UndoEntry e = UndoManager.popRedo(uuid);
        MinecraftServer server = src.getServer();

        // Redo is just re-applying the inverse (same logic as undo, but we push back to undo)
        UndoManager.UndoEntry newUndoEntry = buildRedoEntry(e);

        boolean applied = applyUndoEntry(e, src, server);
        if (applied && newUndoEntry != null)
            UndoManager.record(uuid, newUndoEntry); // push to undo so you can undo the redo
        return applied ? 1 : 0;
    }

    /**
     * Given an undo entry about to be applied, snapshot the CURRENT state
     * so we can push it as a redo (or new undo after redo).
     */
    private static UndoManager.UndoEntry buildRedoEntry(UndoManager.UndoEntry e) {
        return switch (e.type) {
            case CREATE -> {
                // After undo-create (= delete), redo = re-create. Save current slot data.
                SlotManager.SlotData d = SlotManager.getById(e.customId);
                yield d != null ? UndoManager.UndoEntry.forDelete(d) : null;
            }
            case DELETE -> {
                // After undo-delete (= restore), redo = delete again.
                yield UndoManager.UndoEntry.forCreate(e.customId);
            }
            case RENAME -> {
                SlotManager.SlotData d = SlotManager.getById(e.customId);
                yield d != null ? UndoManager.UndoEntry.forRename(e.customId, d.displayName) : null;
            }
            case RETEXTURE -> {
                SlotManager.SlotData d = SlotManager.getById(e.customId);
                yield d != null ? UndoManager.UndoEntry.forRetexture(e.customId, d.texture) : null;
            }
            case SETGLOW -> {
                SlotManager.SlotData d = SlotManager.getById(e.customId);
                yield d != null ? UndoManager.UndoEntry.forSetGlow(e.customId, d.lightLevel) : null;
            }
            case SETHARDNESS -> {
                SlotManager.SlotData d = SlotManager.getById(e.customId);
                yield d != null ? UndoManager.UndoEntry.forSetHardness(e.customId, d.hardness) : null;
            }
            case SETSOUND -> {
                SlotManager.SlotData d = SlotManager.getById(e.customId);
                yield d != null ? UndoManager.UndoEntry.forSetSound(e.customId, d.soundType) : null;
            }
            case SETTABICON -> UndoManager.UndoEntry.forSetTabIcon(SlotManager.getTabIconTexture());
        };
    }

    /** Shared logic for both undo and redo. Returns true if successful. */
    private static boolean applyUndoEntry(UndoManager.UndoEntry e,
                                           ServerCommandSource src, MinecraftServer server) {
        switch (e.type) {
            case CREATE -> {
                if (!SlotManager.hasId(e.customId)) {
                    src.sendError(Text.literal("§cCan't undo/redo: '" + e.customId + "' no longer exists.")); return false;
                }
                SlotManager.SlotData d = SlotManager.getById(e.customId);
                SlotManager.remove(e.customId);
                SlotManager.saveAll();
                CustomBlocksMod.broadcastUpdate(server,
                    new SlotUpdatePayload("remove", d.index, e.customId, null, null, 0, 0, "stone"));
                src.sendMessage(Text.literal("§a[CustomBlocks] Undone: deleted '" + e.customId + "'."));
                return true;
            }
            case DELETE -> {
                if (SlotManager.hasId(e.customId)) {
                    src.sendError(Text.literal("§cCan't undo/redo: '" + e.customId + "' already exists.")); return false;
                }
                SlotManager.SlotData d = SlotManager.assignAtIndex(e.slotIndex, e.customId, e.displayName, e.texture);
                if (d == null) {
                    src.sendError(Text.literal("§cCan't undo/redo: slot " + e.slotIndex + " is occupied.")); return false;
                }
                SlotManager.setProperties(e.customId, e.lightLevel, e.hardness, e.soundType);
                SlotManager.saveAll();
                CustomBlocksMod.broadcastUpdate(server,
                    new SlotUpdatePayload("add", d.index, e.customId, e.displayName,
                            e.texture, e.lightLevel, e.hardness, e.soundType));
                src.sendMessage(Text.literal("§a[CustomBlocks] Undone: restored '" + e.customId + "'."));
                return true;
            }
            case RENAME -> {
                if (!SlotManager.hasId(e.customId)) { src.sendError(notFound(e.customId)); return false; }
                SlotManager.SlotData d = SlotManager.getById(e.customId);
                SlotManager.rename(e.customId, e.displayName);
                SlotManager.saveAll();
                CustomBlocksMod.broadcastUpdate(server,
                    new SlotUpdatePayload("rename", d.index, e.customId, e.displayName, null, 0, 0, "stone"));
                src.sendMessage(Text.literal("§a[CustomBlocks] Undone: name restored to '" + e.displayName + "'."));
                return true;
            }
            case RETEXTURE -> {
                if (!SlotManager.hasId(e.customId)) { src.sendError(notFound(e.customId)); return false; }
                SlotManager.SlotData d = SlotManager.getById(e.customId);
                SlotManager.updateTexture(e.customId, e.texture);
                SlotManager.saveAll();
                CustomBlocksMod.broadcastUpdate(server,
                    new SlotUpdatePayload("retexture", d.index, e.customId, null,
                            e.texture, d.lightLevel, d.hardness, d.soundType));
                src.sendMessage(Text.literal("§a[CustomBlocks] Undone: texture restored for '" + e.customId + "'."));
                return true;
            }
            case SETGLOW -> {
                if (!SlotManager.hasId(e.customId)) { src.sendError(notFound(e.customId)); return false; }
                SlotManager.SlotData d = SlotManager.getById(e.customId);
                SlotManager.setLightLevel(e.customId, e.lightLevel);
                SlotManager.saveAll();
                CustomBlocksMod.broadcastUpdate(server,
                    new SlotUpdatePayload("setprop", d.index, e.customId, null, null,
                            e.lightLevel, d.hardness, d.soundType));
                src.sendMessage(Text.literal("§a[CustomBlocks] Undone: glow restored to " + e.lightLevel + "."));
                return true;
            }
            case SETHARDNESS -> {
                if (!SlotManager.hasId(e.customId)) { src.sendError(notFound(e.customId)); return false; }
                SlotManager.SlotData d = SlotManager.getById(e.customId);
                SlotManager.setHardness(e.customId, e.hardness);
                SlotManager.saveAll();
                CustomBlocksMod.broadcastUpdate(server,
                    new SlotUpdatePayload("setprop", d.index, e.customId, null, null,
                            d.lightLevel, e.hardness, d.soundType));
                src.sendMessage(Text.literal("§a[CustomBlocks] Undone: hardness restored to " + e.hardness + "."));
                return true;
            }
            case SETSOUND -> {
                if (!SlotManager.hasId(e.customId)) { src.sendError(notFound(e.customId)); return false; }
                SlotManager.SlotData d = SlotManager.getById(e.customId);
                SlotManager.setSoundType(e.customId, e.soundType);
                SlotManager.saveAll();
                CustomBlocksMod.broadcastUpdate(server,
                    new SlotUpdatePayload("setprop", d.index, e.customId, null, null,
                            d.lightLevel, d.hardness, e.soundType));
                src.sendMessage(Text.literal("§a[CustomBlocks] Undone: sound restored to " + e.soundType + "."));
                return true;
            }
            case SETTABICON -> {
                SlotManager.setTabIconTexture(e.prevTabIcon);
                if (SlotManager.hasId("tab_icon") && e.prevTabIcon != null)
                    SlotManager.updateTexture("tab_icon", e.prevTabIcon);
                SlotManager.saveAll();
                if (e.prevTabIcon != null)
                    CustomBlocksMod.broadcastUpdate(server,
                        new SlotUpdatePayload("tabicon", -1, null, null, e.prevTabIcon, 0, 0, "stone"));
                src.sendMessage(Text.literal("§a[CustomBlocks] Undone: tab icon restored."));
                return true;
            }
        }
        return false;
    }

    // ── /customblock debugstick ──────────────────────────────────────────────
    private static int cmdDebugStick(ServerCommandSource src, Collection<ServerPlayerEntity> targets) {
        ItemStack stick = new ItemStack(Items.STICK);

        // 1.21.1 uses DataComponents instead of raw NBT
        // Mark as rotate stick via CUSTOM_DATA component
        NbtCompound markerNbt = new NbtCompound();
        markerNbt.putBoolean(RotateStickItem.NBT_KEY, true);
        stick.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,
                net.minecraft.component.type.NbtComponent.of(markerNbt));

        // Custom name via CUSTOM_NAME component
        stick.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,
                Text.literal("§6§lRotate Stick §r§7[CustomBlocks]"));

        // Glint via ENCHANTMENT_GLINT_OVERRIDE component (no fake enchant needed)
        stick.set(net.minecraft.component.DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);

        if (targets == null || targets.isEmpty()) {
            try {
                ServerPlayerEntity self = src.getPlayerOrThrow();
                self.getInventory().insertStack(stick.copy());
                src.sendMessage(Text.literal("§a[CustomBlocks] Rotate Stick given! §7Right-click block = rotate. §8Sneak+right-click = reverse."));
            } catch (Exception ex) {
                src.sendError(Text.literal("§cRun as a player or specify a target."));
                return 0;
            }
        } else {
            for (ServerPlayerEntity p : targets) {
                p.getInventory().insertStack(stick.copy());
                p.sendMessage(Text.literal("§a[CustomBlocks] You received a Rotate Stick. §7Right-click any block to rotate it."));
            }
            src.sendMessage(Text.literal("§a[CustomBlocks] Rotate Stick given to " + targets.size() + " player(s)."));
        }
        return 1;
    }

    // ── /customblock rotate ──────────────────────────────────────────────────
    private static int cmdRotate(ServerCommandSource src, boolean reverse) {
        ServerPlayerEntity player;
        try { player = src.getPlayerOrThrow(); }
        catch (Exception e) { src.sendError(Text.literal("§cMust be run as a player.")); return 0; }

        if (!(player.getCameraEntity().raycast(5.0, 0f, false) instanceof BlockHitResult hit)
                || hit.getType() == HitResult.Type.MISS) {
            src.sendError(Text.literal("§cNot looking at a block (within 5 blocks)."));
            return 0;
        }

        BlockPos pos = hit.getBlockPos();
        ServerWorld world = player.getServerWorld();
        BlockState state = world.getBlockState(pos);
        BlockState rotated = rotateState(state, reverse);

        if (rotated == state) {
            src.sendError(Text.literal("§cThis block has no rotatable property."));
            return 0;
        }

        world.setBlockState(pos, rotated);
        String dir = getDirectionLabel(rotated);
        src.sendMessage(Text.literal("§a[CustomBlocks] Rotated" + (dir.isEmpty() ? "." : " → §f" + dir)));
        return 1;
    }

    // ── importfolder ─────────────────────────────────────────────────────────
    private static int cmdImportFolder(ServerCommandSource src) {
        File importDir = new File("config/customblocks/import");
        if (!importDir.exists()) {
            importDir.mkdirs();
            src.sendMessage(Text.literal("§e[CustomBlocks] Created config/customblocks/import/ — drop PNGs in there, then run again."));
            return 1;
        }
        File[] allPngs = importDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".png"));
        if (allPngs == null || allPngs.length == 0) {
            src.sendMessage(Text.literal("§c[CustomBlocks] No PNGs in config/customblocks/import/")); return 0;
        }
        Arrays.sort(allPngs, Comparator.comparing(File::getName));
        int free = SlotManager.freeSlots();
        if (free == 0) { src.sendError(Text.literal("§cAll " + SlotManager.MAX_SLOTS + " slots full!")); return 0; }
        src.sendMessage(Text.literal("§e[CustomBlocks] " + allPngs.length + " PNG(s) found, " + free + " free. Importing..."));
        MinecraftServer server = src.getServer();
        thread(() -> {
            List<String[]> toAdd = new ArrayList<>();
            List<byte[]> toBytes = new ArrayList<>();
            List<String> skipped = new ArrayList<>(), failed = new ArrayList<>();
            for (File png : allPngs) {
                String raw = png.getName().replaceAll("(?i)\\.png$", "");
                String id  = raw.toLowerCase().replaceAll("[^a-z0-9_]", "_");
                String dn  = Arrays.stream(raw.replace("_", " ").split(" "))
                    .map(w -> w.isEmpty() ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1).toLowerCase())
                    .collect(java.util.stream.Collectors.joining(" "));
                if (SlotManager.hasId(id)) { skipped.add(id); continue; }
                if (toAdd.size() >= free)  { failed.add(id + "(no slot)"); continue; }
                try { toAdd.add(new String[]{id, dn}); toBytes.add(Files.readAllBytes(png.toPath())); }
                catch (Exception ex) { failed.add(id + "(err)"); }
            }
            server.execute(() -> {
                int created = 0;
                for (int i = 0; i < toAdd.size(); i++) {
                    SlotManager.SlotData d = SlotManager.assign(toAdd.get(i)[0], toAdd.get(i)[1], toBytes.get(i));
                    if (d == null) { failed.add(toAdd.get(i)[0] + "(full)"); continue; }
                    CustomBlocksMod.broadcastUpdate(server, new SlotUpdatePayload("add", d.index, d.customId,
                            d.displayName, toBytes.get(i), d.lightLevel, d.hardness, d.soundType));
                    created++;
                }
                if (created > 0) SlotManager.saveAll();
                StringBuilder msg = new StringBuilder("§a[CustomBlocks] Done! §f" + created + " created");
                if (!skipped.isEmpty()) msg.append("§7, ").append(skipped.size()).append(" skipped");
                if (!failed.isEmpty())  msg.append("§c, ").append(failed.size()).append(" failed");
                src.sendMessage(Text.literal(msg.toString()));
                src.sendMessage(Text.literal("§7Slots: " + SlotManager.usedSlots() + "/" + SlotManager.MAX_SLOTS));
            });
        });
        return 1;
    }

    // ── export ────────────────────────────────────────────────────────────────
    private static int cmdExport(ServerCommandSource src) {
        File out = new File("config/customblocks/export.json");
        out.getParentFile().mkdirs();
        try {
            com.google.gson.JsonObject root = new com.google.gson.JsonObject();
            com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
            for (SlotManager.SlotData d : SlotManager.allSlots()) {
                com.google.gson.JsonObject e = new com.google.gson.JsonObject();
                e.addProperty("id", d.customId); e.addProperty("displayName", d.displayName);
                e.addProperty("slot", d.index); e.addProperty("lightLevel", d.lightLevel);
                e.addProperty("hardness", d.hardness); e.addProperty("soundType", d.soundType);
                arr.add(e);
            }
            root.add("blocks", arr);
            root.addProperty("totalBlocks", SlotManager.usedSlots());
            root.addProperty("freeSlots", SlotManager.freeSlots());
            try (java.io.FileWriter fw = new java.io.FileWriter(out, java.nio.charset.StandardCharsets.UTF_8)) {
                new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root, fw);
            }
            src.sendMessage(Text.literal("§a[CustomBlocks] Exported " + SlotManager.usedSlots() + " blocks to export.json"));
        } catch (Exception e) { src.sendError(Text.literal("§cExport failed: " + e.getMessage())); }
        return 1;
    }

    // ── list ──────────────────────────────────────────────────────────────────
    private static int cmdList(ServerCommandSource src) {
        if (SlotManager.usedSlots() == 0) {
            src.sendMessage(Text.literal("§7[CustomBlocks] No blocks. " + SlotManager.freeSlots() + " slots free.")); return 1;
        }
        src.sendMessage(Text.literal("§e[CustomBlocks] §f" + SlotManager.usedSlots() + " block(s) | §7" + SlotManager.freeSlots() + " free:"));
        for (SlotManager.SlotData d : SlotManager.allSlots()) {
            String g = d.lightLevel > 0 ? " §6*" + d.lightLevel : "";
            String h = d.hardness < 0 ? " §c∞" : "";
            src.sendMessage(Text.literal("  §f" + d.customId + " §7→ '" + d.displayName + "'" + g + h + " §8(slot " + d.index + ")"));
        }
        return 1;
    }

    // ── help ──────────────────────────────────────────────────────────────────
    private static int cmdHelp(ServerCommandSource src) {
        src.sendMessage(Text.literal("§e══ Custom Blocks Help ══ §8(also usable as /cb)"));
        src.sendMessage(Text.literal("§aPress §fB §ato open the GUI!"));
        src.sendMessage(Text.literal("§f/cb createurl <id> <name> <url>"));
        src.sendMessage(Text.literal("§f/cb delete <id>  §f/cb rename <id> <name>  §f/cb retexture <id> <url>"));
        src.sendMessage(Text.literal("§f/cb give <id> [amount] [player]"));
        src.sendMessage(Text.literal("§f/cb setglow <id> <0-15>  §f/cb sethardness <id> <val>  §f/cb setsound <id> <type>"));
        src.sendMessage(Text.literal("§f/cb settabicon <url>"));
        src.sendMessage(Text.literal("§f/cb reload  §7— rescan config, restore textures, re-sync all players"));
        src.sendMessage(Text.literal("§f/cb undo  §f/cb redo"));
        src.sendMessage(Text.literal("§f/cb debugstick [player]  §f/cb rotate [reverse]"));
        src.sendMessage(Text.literal("§f/cb importfolder  §f/cb export  §f/cb list"));
        return 1;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Rotation helpers (public — used by RotateStickItem via event in CustomBlocksMod)
    // ═══════════════════════════════════════════════════════════════════════════

    public static BlockState rotateState(BlockState state, boolean reverse) {
        for (Property<?> prop : state.getProperties()) {
            if (prop instanceof DirectionProperty dp) {
                List<Direction> vals = new ArrayList<>(dp.getValues());
                Direction cur = state.get(dp);
                int idx = vals.indexOf(cur);
                int next = reverse ? (idx - 1 + vals.size()) % vals.size() : (idx + 1) % vals.size();
                return state.with(dp, vals.get(next));
            }
        }
        for (Property<?> prop : state.getProperties()) {
            if (prop.getName().equals("axis") && prop instanceof EnumProperty<?> ep) {
                @SuppressWarnings("unchecked")
                EnumProperty<Direction.Axis> axProp = (EnumProperty<Direction.Axis>) ep;
                List<Direction.Axis> vals = new ArrayList<>(axProp.getValues());
                Direction.Axis cur = state.get(axProp);
                int idx = vals.indexOf(cur);
                int next = reverse ? (idx - 1 + vals.size()) % vals.size() : (idx + 1) % vals.size();
                return state.with(axProp, vals.get(next));
            }
        }
        return state;
    }

    public static String getDirectionLabel(BlockState state) {
        for (Property<?> prop : state.getProperties()) {
            if (prop instanceof DirectionProperty dp) return state.get(dp).getName().toUpperCase();
            if (prop.getName().equals("axis")) return "AXIS:" + state.get((EnumProperty<?>) prop).toString().toUpperCase();
        }
        return "";
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Misc helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private static int usage(ServerCommandSource src, String cmd) {
        src.sendError(Text.literal(switch (cmd) {
            case "delete"      -> "§cUsage: /cb delete <id>";
            case "rename"      -> "§cUsage: /cb rename <id> <newname>";
            case "retexture"   -> "§cUsage: /cb retexture <id> <url>";
            case "give"        -> "§cUsage: /cb give <id> [amount 1-64] [player]";
            case "setglow"     -> "§cUsage: /cb setglow <id> <0-15>";
            case "sethardness" -> "§cUsage: /cb sethardness <id> <-1 to 50>  (-1=unbreakable)";
            case "setsound"    -> "§cUsage: /cb setsound <id> <stone|wood|grass|metal|glass|sand|wool>";
            case "settabicon"  -> "§cUsage: /cb settabicon <url>";
            default -> "§cUsage: /cb help";
        }));
        return 0;
    }

    private static Text notFound(String id) { return Text.literal("§c'" + id + "' not found. Use /cb list"); }
    private static String sanitize(String id) { return id.toLowerCase().replaceAll("[^a-z0-9_]", "_"); }
    private static UUID playerUuid(ServerCommandSource src) {
        try { return src.getPlayerOrThrow().getUuid(); } catch (Exception e) { return null; }
    }

    private static final java.net.http.HttpClient HTTP_CLIENT =
            java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private static byte[] download(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url))
                .header("User-Agent", "CustomBlocksMod/1.0").timeout(Duration.ofSeconds(15)).build();
        HttpResponse<byte[]> res = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (res.statusCode() != 200) throw new IOException("HTTP " + res.statusCode());
        byte[] body = res.body();
        if (body.length > 10_485_760) throw new IOException("Image too large (max 10MB)");
        return body;
    }

    private static void thread(Runnable r) { Thread t = new Thread(r, "CB-Worker"); t.setDaemon(true); t.start(); }
}
