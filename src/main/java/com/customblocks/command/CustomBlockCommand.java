package com.customblocks.command;

import com.customblocks.CustomBlocksMod;
import com.customblocks.SlotManager;
import com.customblocks.block.SlotBlock;
import com.customblocks.network.SlotUpdatePayload;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Collection;

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
            dispatcher.register(CommandManager.literal("customblock")
                .requires(src -> src.hasPermissionLevel(2))

                // ── createurl <id> <name> <url> ──────────────────────────────
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

                // ── delete <id> ──────────────────────────────────────────────
                .then(CommandManager.literal("delete")
                    .executes(ctx -> usage(ctx.getSource(), "delete"))
                    .then(CommandManager.argument("id", StringArgumentType.word())
                        .suggests(BLOCK_SUGGESTIONS)
                        .executes(ctx -> cmdDelete(ctx.getSource(),
                            StringArgumentType.getString(ctx, "id")))
                    )
                )

                // ── rename <id> <newname> ────────────────────────────────────
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

                // ── retexture <id> <url> ─────────────────────────────────────
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

                // ── give <id> [player] ───────────────────────────────────────
                .then(CommandManager.literal("give")
                    .executes(ctx -> usage(ctx.getSource(), "give"))
                    .then(CommandManager.argument("id", StringArgumentType.word())
                        .suggests(BLOCK_SUGGESTIONS)
                        .executes(ctx -> cmdGive(ctx.getSource(),
                            StringArgumentType.getString(ctx, "id"), null))
                        .then(CommandManager.argument("player", EntityArgumentType.players())
                            .executes(ctx -> cmdGive(ctx.getSource(),
                                StringArgumentType.getString(ctx, "id"),
                                EntityArgumentType.getPlayers(ctx, "player")))
                        )
                    )
                )

                // ── setglow <id> <0-15> ──────────────────────────────────────
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

                // ── sethardness <id> <value> ─────────────────────────────────
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

                // ── setsound <id> <type> ─────────────────────────────────────
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

                // ── settabicon <url> ─────────────────────────────────────────
                .then(CommandManager.literal("settabicon")
                    .executes(ctx -> usage(ctx.getSource(), "settabicon"))
                    .then(CommandManager.argument("url", StringArgumentType.greedyString())
                        .executes(ctx -> cmdSetTabIcon(ctx.getSource(),
                            StringArgumentType.getString(ctx, "url").trim()))
                    )
                )

                // ── list ─────────────────────────────────────────────────────
                .then(CommandManager.literal("list")
                    .executes(ctx -> cmdList(ctx.getSource()))
                )

                // ── help ─────────────────────────────────────────────────────
                .then(CommandManager.literal("help")
                    .executes(ctx -> cmdHelp(ctx.getSource()))
                )
            );
        });
    }

    // ── Implementations ───────────────────────────────────────────────────────

    private static int cmdCreate(ServerCommandSource src, String rawId, String name, String url) {
        String id = sanitize(rawId);
        if (id.isEmpty()) { src.sendError(Text.literal("§cInvalid ID.")); return 0; }
        if (SlotManager.hasId(id)) {
            src.sendError(Text.literal("§c'" + id + "' already exists.")); return 0;
        }
        if (SlotManager.freeSlots() == 0) {
            src.sendError(Text.literal("§cAll 64 slots are full!")); return 0;
        }
        src.sendMessage(Text.literal("§e[CustomBlocks] Downloading..."));
        MinecraftServer server = src.getServer();
        final String fId = id, fName = name;
        thread(() -> {
            try {
                byte[] bytes = download(url);
                server.execute(() -> {
                    SlotManager.SlotData d = SlotManager.assign(fId, fName, bytes);
                    if (d == null) { src.sendError(Text.literal("§cNo free slots!")); return; }
                    SlotManager.saveAll();
                    CustomBlocksMod.broadcastUpdate(server,
                        new SlotUpdatePayload("add", d.index, fId, fName, bytes,
                                d.lightLevel, d.hardness, d.soundType));
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
        final String fId = id;
        thread(() -> {
            try {
                byte[] bytes = download(url);
                server.execute(() -> {
                    SlotManager.SlotData d = SlotManager.getById(fId);
                    if (d == null) { src.sendError(notFound(fId)); return; }
                    SlotManager.updateTexture(fId, bytes);
                    SlotManager.saveAll();
                    CustomBlocksMod.broadcastUpdate(server,
                        new SlotUpdatePayload("retexture", d.index, fId, null, bytes,
                                d.lightLevel, d.hardness, d.soundType));
                    src.sendMessage(Text.literal("§a[CustomBlocks] Texture updated for '" + fId + "'."));
                });
            } catch (Exception e) {
                server.execute(() -> src.sendError(Text.literal("§c[CustomBlocks] Download failed: " + e.getMessage())));
            }
        });
        return 1;
    }

    private static int cmdGive(ServerCommandSource src, String id,
                                Collection<ServerPlayerEntity> targets) {
        SlotManager.SlotData d = SlotManager.getById(id);
        if (d == null) { src.sendError(notFound(id)); return 0; }
        SlotBlock.SlotItem item = CustomBlocksMod.SLOT_ITEMS[d.index];
        ItemStack stack = new ItemStack(item);
        if (targets == null || targets.isEmpty()) {
            try {
                ServerPlayerEntity self = src.getPlayerOrThrow();
                self.getInventory().insertStack(stack.copy());
                src.sendMessage(Text.literal("§a[CustomBlocks] Given '" + d.displayName + "' to you."));
            } catch (Exception ex) {
                src.sendError(Text.literal("§cRun as a player or specify a target."));
            }
        } else {
            for (ServerPlayerEntity p : targets) {
                p.getInventory().insertStack(stack.copy());
                p.sendMessage(Text.literal("§a[CustomBlocks] You received '" + d.displayName + "'."));
            }
            src.sendMessage(Text.literal("§a[CustomBlocks] Gave to " + targets.size() + " player(s)."));
        }
        return 1;
    }

    private static int cmdSetGlow(ServerCommandSource src, String id, int level) {
        if (!SlotManager.hasId(id)) { src.sendError(notFound(id)); return 0; }
        SlotManager.SlotData d = SlotManager.getById(id);
        SlotManager.setLightLevel(id, level);
        SlotManager.saveAll();
        CustomBlocksMod.broadcastUpdate(src.getServer(),
            new SlotUpdatePayload("setprop", d.index, id, null, null,
                    level, d.hardness, d.soundType));
        src.sendMessage(Text.literal("§a[CustomBlocks] '" + id + "' glow set to " + level + "."));
        return 1;
    }

    private static int cmdSetHardness(ServerCommandSource src, String id, float val) {
        if (!SlotManager.hasId(id)) { src.sendError(notFound(id)); return 0; }
        SlotManager.SlotData d = SlotManager.getById(id);
        SlotManager.setHardness(id, val);
        SlotManager.saveAll();
        String label = val < 0 ? "Unbreakable" : val == 0 ? "Instant break" : String.valueOf(val);
        CustomBlocksMod.broadcastUpdate(src.getServer(),
            new SlotUpdatePayload("setprop", d.index, id, null, null,
                    d.lightLevel, val, d.soundType));
        src.sendMessage(Text.literal("§a[CustomBlocks] '" + id + "' hardness: " + label + "."));
        return 1;
    }

    private static int cmdSetSound(ServerCommandSource src, String id, String type) {
        if (!SlotManager.hasId(id)) { src.sendError(notFound(id)); return 0; }
        String[] valid = {"stone","wood","grass","metal","glass","sand","wool"};
        boolean ok = false;
        for (String v : valid) if (v.equals(type)) { ok = true; break; }
        if (!ok) {
            src.sendError(Text.literal("§cValid sounds: stone, wood, grass, metal, glass, sand, wool"));
            return 0;
        }
        SlotManager.SlotData d = SlotManager.getById(id);
        SlotManager.setSoundType(id, type);
        SlotManager.saveAll();
        CustomBlocksMod.broadcastUpdate(src.getServer(),
            new SlotUpdatePayload("setprop", d.index, id, null, null,
                    d.lightLevel, d.hardness, type));
        src.sendMessage(Text.literal("§a[CustomBlocks] '" + id + "' sound: " + type + "."));
        return 1;
    }

    private static int cmdSetTabIcon(ServerCommandSource src, String url) {
        src.sendMessage(Text.literal("§e[CustomBlocks] Downloading tab icon..."));
        MinecraftServer server = src.getServer();
        thread(() -> {
            try {
                byte[] bytes = download(url);
                server.execute(() -> {
                    // Assign or update the reserved "tab_icon" slot
                    SlotManager.setTabIconTexture(bytes);
                    if (!SlotManager.hasId("tab_icon")) {
                        SlotManager.assign("tab_icon", "Tab Icon", bytes);
                    } else {
                        SlotManager.updateTexture("tab_icon", bytes);
                    }
                    SlotManager.saveAll();
                    // Broadcast as both tabicon (for the texture) and add (for the slot)
                    SlotManager.SlotData d = SlotManager.getById("tab_icon");
                    if (d != null) {
                        CustomBlocksMod.broadcastUpdate(server,
                            new SlotUpdatePayload("add", d.index, "tab_icon", "Tab Icon",
                                bytes, 0, 1.5f, "stone"));
                    }
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

    private static int cmdList(ServerCommandSource src) {
        if (SlotManager.usedSlots() == 0) {
            src.sendMessage(Text.literal("§7[CustomBlocks] No blocks. " + SlotManager.freeSlots() + " slots free."));
            return 1;
        }
        src.sendMessage(Text.literal("§e[CustomBlocks] §f" + SlotManager.usedSlots() + " block(s) | §7" + SlotManager.freeSlots() + " free:"));
        for (SlotManager.SlotData d : SlotManager.allSlots()) {
            String glow  = d.lightLevel > 0 ? " §6*" + d.lightLevel : "";
            String hard  = d.hardness < 0 ? " §c∞" : "";
            src.sendMessage(Text.literal("  §f" + d.customId + " §7→ '" + d.displayName + "'" + glow + hard + " §8(slot " + d.index + ")"));
        }
        return 1;
    }

    private static int cmdHelp(ServerCommandSource src) {
        src.sendMessage(Text.literal("§e══ Custom Blocks Help ══"));
        src.sendMessage(Text.literal("§aPress §fB §ato open the visual GUI!"));
        src.sendMessage(Text.literal("§f/customblock createurl <id> <n> <url>  §7create from image"));
        src.sendMessage(Text.literal("§f/customblock delete <id>  §7delete a block"));
        src.sendMessage(Text.literal("§f/customblock rename <id> <newname>  §7rename"));
        src.sendMessage(Text.literal("§f/customblock retexture <id> <url>  §7change texture"));
        src.sendMessage(Text.literal("§f/customblock give <id> [player]  §7give block"));
        src.sendMessage(Text.literal("§f/customblock setglow <id> <0-15>  §7light emission"));
        src.sendMessage(Text.literal("§f/customblock sethardness <id> <val>  §7mining speed (−1=unbreakable)"));
        src.sendMessage(Text.literal("§f/customblock setsound <id> <stone|wood|metal|glass|grass|sand>"));
        src.sendMessage(Text.literal("§f/customblock settabicon <url>  §7set tab icon"));
        src.sendMessage(Text.literal("§f/customblock list  §7list all blocks"));
        src.sendMessage(Text.literal("§7No restarts needed for any command!"));
        return 1;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static int usage(ServerCommandSource src, String cmd) {
        src.sendError(Text.literal(switch (cmd) {
            case "delete"      -> "§cUsage: /customblock delete <id>";
            case "rename"      -> "§cUsage: /customblock rename <id> <newname>";
            case "retexture"   -> "§cUsage: /customblock retexture <id> <url>";
            case "give"        -> "§cUsage: /customblock give <id> [player]";
            case "setglow"     -> "§cUsage: /customblock setglow <id> <0-15>";
            case "sethardness" -> "§cUsage: /customblock sethardness <id> <-1 to 50>  (-1=unbreakable)";
            case "setsound"    -> "§cUsage: /customblock setsound <id> <stone|wood|grass|metal|glass|sand|wool>";
            case "settabicon"  -> "§cUsage: /customblock settabicon <url>";
            default -> "§cUsage: /customblock help";
        }));
        return 0;
    }

    private static Text notFound(String id) {
        return Text.literal("§c'" + id + "' not found. Use /customblock list");
    }

    private static String sanitize(String id) {
        return id.toLowerCase().replaceAll("[^a-z0-9_]", "_");
    }

    private static byte[] download(String url) throws IOException, InterruptedException {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "CustomBlocksMod/1.0")
                .timeout(Duration.ofSeconds(15)).build();
        HttpResponse<byte[]> res = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (res.statusCode() != 200)
            throw new IOException("HTTP " + res.statusCode());
        return res.body();
    }

    private static void thread(Runnable r) {
        Thread t = new Thread(r, "CB-Download");
        t.setDaemon(true);
        t.start();
    }
}
