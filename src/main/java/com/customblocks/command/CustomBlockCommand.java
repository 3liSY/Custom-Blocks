package com.customblocks.command;

import com.customblocks.CustomBlocksMod;
import com.customblocks.network.CustomBlockListPayload;
import com.customblocks.network.CustomBlockSyncPayload;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class CustomBlockCommand {

    private static final SuggestionProvider<ServerCommandSource> BLOCK_SUGGESTIONS =
        (ctx, builder) -> {
            for (String id : CustomBlocksMod.CUSTOM_BLOCKS.keySet())
                builder.suggest(id);
            return builder.buildFuture();
        };

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                CommandManager.literal("customblock")
                    .requires(src -> src.hasPermissionLevel(2))

                    .then(CommandManager.literal("createurl")
                        .then(CommandManager.argument("id", StringArgumentType.word())
                            .then(CommandManager.argument("name", StringArgumentType.word())
                                .then(CommandManager.argument("url", StringArgumentType.greedyString())
                                    .executes(ctx -> {
                                        String id   = StringArgumentType.getString(ctx, "id");
                                        String name = StringArgumentType.getString(ctx, "name").replace("_", " ");
                                        String url  = StringArgumentType.getString(ctx, "url").trim();
                                        return createBlock(ctx.getSource(), id, name, url);
                                    })
                                )
                            )
                        )
                    )

                    .then(CommandManager.literal("delete")
                        .executes(ctx -> {
                            if (CustomBlocksMod.CUSTOM_BLOCKS.isEmpty()) {
                                ctx.getSource().sendMessage(Text.literal("§7[CustomBlocks] No blocks to delete."));
                            } else {
                                ctx.getSource().sendError(Text.literal("§cUsage: /customblock delete <id>"));
                                ctx.getSource().sendMessage(Text.literal("§7Available: " +
                                    String.join(", ", CustomBlocksMod.CUSTOM_BLOCKS.keySet())));
                            }
                            return 0;
                        })
                        .then(CommandManager.argument("id", StringArgumentType.word())
                            .suggests(BLOCK_SUGGESTIONS)
                            .executes(ctx -> deleteBlock(ctx.getSource(),
                                StringArgumentType.getString(ctx, "id")))
                        )
                    )

                    .then(CommandManager.literal("settabicon")
                        .executes(ctx -> {
                            ctx.getSource().sendError(Text.literal("§cUsage: /customblock settabicon <url>"));
                            ctx.getSource().sendMessage(Text.literal("§7Example: /customblock settabicon https://i.ibb.co/TjNntKf/Syrian-Rubik-V5.png"));
                            return 0;
                        })
                        .then(CommandManager.argument("url", StringArgumentType.greedyString())
                            .executes(ctx -> setTabIcon(ctx.getSource(),
                                StringArgumentType.getString(ctx, "url").trim()))
                        )
                    )

                    .then(CommandManager.literal("list")
                        .executes(ctx -> listBlocks(ctx.getSource()))
                    )

                    .then(CommandManager.literal("help")
                        .executes(ctx -> showHelp(ctx.getSource()))
                    )
            );
        });
    }

    // ── DELETE ──────────────────────────────────────────────────────────────

    private static int deleteBlock(ServerCommandSource source, String rawId) {
        String blockId = rawId.toLowerCase().replaceAll("[^a-z0-9_]", "_");

        if (!CustomBlocksMod.CUSTOM_BLOCKS.containsKey(blockId)) {
            source.sendError(Text.literal("§c[CustomBlocks] Block '" + blockId + "' not found."));
            source.sendMessage(Text.literal("§7Available: " +
                String.join(", ", CustomBlocksMod.CUSTOM_BLOCKS.keySet())));
            return 0;
        }

        CustomBlocksMod.CUSTOM_BLOCKS.remove(blockId);
        CustomBlocksMod.BLOCK_TEXTURES.remove(blockId);
        deleteFolder(new File("config/customblocks/" + blockId));

        // Tell clients the updated list so they clean up their local config
        broadcastBlockList(source.getServer());

        source.sendMessage(Text.literal("§a[CustomBlocks] Deleted '" + blockId + "'."));
        source.sendMessage(Text.literal("§e Restart server + all clients for the change to take full effect."));
        return 1;
    }

    // ── CREATE URL ──────────────────────────────────────────────────────────

    private static int createBlock(ServerCommandSource source, String rawId,
                                   String displayName, String imageUrl) {
        String blockId = rawId.toLowerCase().replaceAll("[^a-z0-9_]", "_");

        if (blockId.isEmpty()) {
            source.sendError(Text.literal("[CustomBlocks] Invalid ID: " + rawId)); return 0;
        }
        if (blockId.equals(CustomBlocksMod.TAB_ICON_ID)) {
            source.sendError(Text.literal("[CustomBlocks] 'tab_icon' is reserved. Use /customblock settabicon <url>")); return 0;
        }
        if (CustomBlocksMod.CUSTOM_BLOCKS.containsKey(blockId)) {
            source.sendError(Text.literal("[CustomBlocks] Block '" + blockId + "' already exists! Delete it first."));
            return 0;
        }

        File blockFolder = new File("config/customblocks/" + blockId);
        blockFolder.mkdirs();
        try { Files.writeString(new File(blockFolder, "name.txt").toPath(), displayName); }
        catch (IOException e) { source.sendError(Text.literal("[CustomBlocks] Could not write name.txt")); return 0; }

        File textureFile = new File(blockFolder, "texture.png");
        MinecraftServer server = source.getServer();
        source.sendMessage(Text.literal("§e[CustomBlocks] Downloading texture..."));

        final String finalId = blockId, finalName = displayName;
        Thread t = new Thread(() -> {
            try {
                HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(imageUrl))
                        .header("User-Agent", "CustomBlocksMod/1.0")
                        .timeout(Duration.ofSeconds(15)).build();
                HttpResponse<byte[]> res = http.send(req, HttpResponse.BodyHandlers.ofByteArray());

                if (res.statusCode() != 200) {
                    server.execute(() -> source.sendError(Text.literal("[CustomBlocks] Download failed. HTTP " + res.statusCode())));
                    return;
                }

                byte[] bytes = res.body();
                Files.write(textureFile.toPath(), bytes);

                server.execute(() -> {
                    boolean ok = CustomBlocksMod.registerBlockDynamic(finalId, finalName, blockFolder, textureFile);
                    if (!ok) { source.sendError(Text.literal("[CustomBlocks] Failed to register block!")); return; }

                    // Send texture to all clients so they save it locally for next restart
                    CustomBlockSyncPayload syncPayload = new CustomBlockSyncPayload(finalId, finalName, bytes);
                    List<String> allIds = new ArrayList<>(CustomBlocksMod.CUSTOM_BLOCKS.keySet());
                    if (CustomBlocksMod.TAB_ICON_BLOCK != null) allIds.add(CustomBlocksMod.TAB_ICON_ID);
                    CustomBlockListPayload listPayload = new CustomBlockListPayload(allIds);

                    for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                        ServerPlayNetworking.send(player, syncPayload);
                        ServerPlayNetworking.send(player, listPayload);
                    }

                    source.sendMessage(Text.literal("§a[CustomBlocks] '" + finalName + "' created!"));
                    source.sendMessage(Text.literal("§e⚠ Restart the server AND all clients for the block to work fully (no kicks on middle-click)."));
                    source.sendMessage(Text.literal("§7ID: customblocks:" + finalId));
                });

            } catch (Exception e) {
                server.execute(() -> source.sendError(Text.literal("[CustomBlocks] Download error: " + e.getMessage())));
            }
        }, "CustomBlocks-Download");
        t.setDaemon(true);
        t.start();
        return 1;
    }

    // ── SET TAB ICON ────────────────────────────────────────────────────────

    private static int setTabIcon(ServerCommandSource source, String imageUrl) {
        source.sendMessage(Text.literal("§e[CustomBlocks] Downloading tab icon..."));
        MinecraftServer server = source.getServer();

        Thread t = new Thread(() -> {
            try {
                HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(imageUrl))
                        .header("User-Agent", "CustomBlocksMod/1.0")
                        .timeout(Duration.ofSeconds(15)).build();
                HttpResponse<byte[]> res = http.send(req, HttpResponse.BodyHandlers.ofByteArray());

                if (res.statusCode() != 200) {
                    server.execute(() -> source.sendError(Text.literal("[CustomBlocks] Download failed. HTTP " + res.statusCode())));
                    return;
                }

                byte[] bytes = res.body();
                File iconFolder = new File("config/customblocks/" + CustomBlocksMod.TAB_ICON_ID);
                iconFolder.mkdirs();
                File textureFile = new File(iconFolder, "texture.png");
                Files.write(textureFile.toPath(), bytes);
                Files.writeString(new File(iconFolder, "name.txt").toPath(), "Tab Icon");

                server.execute(() -> {
                    for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                        ServerPlayNetworking.send(player, new CustomBlockSyncPayload(
                            CustomBlocksMod.TAB_ICON_ID, "Tab Icon", bytes));
                    }
                    source.sendMessage(Text.literal("§a[CustomBlocks] Tab icon set! Restart server + clients to see it."));
                });

            } catch (Exception e) {
                server.execute(() -> source.sendError(Text.literal("[CustomBlocks] Error: " + e.getMessage())));
            }
        }, "CustomBlocks-TabIcon");
        t.setDaemon(true);
        t.start();
        return 1;
    }

    // ── HELPERS ─────────────────────────────────────────────────────────────

    private static void broadcastBlockList(MinecraftServer server) {
        List<String> allIds = new ArrayList<>(CustomBlocksMod.CUSTOM_BLOCKS.keySet());
        if (CustomBlocksMod.TAB_ICON_BLOCK != null) allIds.add(CustomBlocksMod.TAB_ICON_ID);
        CustomBlockListPayload payload = new CustomBlockListPayload(allIds);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList())
            ServerPlayNetworking.send(p, payload);
    }

    private static void deleteFolder(File folder) {
        if (!folder.exists()) return;
        File[] files = folder.listFiles();
        if (files != null) for (File f : files) f.delete();
        folder.delete();
    }

    private static int listBlocks(ServerCommandSource source) {
        if (CustomBlocksMod.CUSTOM_BLOCKS.isEmpty()) {
            source.sendMessage(Text.literal("§7[CustomBlocks] No custom blocks loaded.")); return 1;
        }
        source.sendMessage(Text.literal("§e[CustomBlocks] Loaded blocks:"));
        for (var e : CustomBlocksMod.CUSTOM_BLOCKS.entrySet())
            source.sendMessage(Text.literal("  §f- customblocks:" + e.getKey()
                + " §7(\"" + e.getValue().getCustomDisplayName() + "\")"));
        return 1;
    }

    private static int showHelp(ServerCommandSource source) {
        source.sendMessage(Text.literal("§e=== CustomBlocks ==="));
        source.sendMessage(Text.literal("§f/customblock createurl <id> <name> <url>"));
        source.sendMessage(Text.literal("§f/customblock delete <id>  §7(tab to pick)"));
        source.sendMessage(Text.literal("§f/customblock settabicon <url>"));
        source.sendMessage(Text.literal("§f/customblock list"));
        source.sendMessage(Text.literal("§eAfter creating/deleting: restart server + clients!"));
        return 1;
    }
}
