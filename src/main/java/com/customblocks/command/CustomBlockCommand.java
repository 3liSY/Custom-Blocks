package com.customblocks.command;

import com.customblocks.CustomBlocksMod;
import com.customblocks.network.CustomBlockSyncPayload;
import com.mojang.brigadier.arguments.StringArgumentType;
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

public class CustomBlockCommand {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                CommandManager.literal("customblock")
                    .requires(src -> src.hasPermissionLevel(2))

                    .then(CommandManager.literal("create")
                        .then(CommandManager.argument("id", StringArgumentType.word())
                            .then(CommandManager.argument("name", StringArgumentType.word())
                                .executes(ctx -> {
                                    String id   = StringArgumentType.getString(ctx, "id");
                                    String name = StringArgumentType.getString(ctx, "name").replace("_", " ");
                                    return createBlock(ctx.getSource(), id, name, null);
                                })
                            )
                            .executes(ctx -> {
                                String id = StringArgumentType.getString(ctx, "id");
                                return createBlock(ctx.getSource(), id, id, null);
                            })
                        )
                    )

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

                    .then(CommandManager.literal("settabicon")
                        .executes(ctx -> {
                            ctx.getSource().sendError(Text.literal("§cUsage: /customblock settabicon <url>"));
                            ctx.getSource().sendMessage(Text.literal("§7Example: /customblock settabicon https://i.ibb.co/TjNntKf/Syrian-Rubik-V5.png"));
                            return 0;
                        })
                        .then(CommandManager.argument("url", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                String url = StringArgumentType.getString(ctx, "url").trim();
                                return setTabIcon(ctx.getSource(), url);
                            })
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
                    server.execute(() -> source.sendError(
                        Text.literal("[CustomBlocks] Download failed. HTTP " + res.statusCode())));
                    return;
                }

                byte[] bytes = res.body();
                File iconFolder = new File("config/customblocks/" + CustomBlocksMod.TAB_ICON_ID);
                iconFolder.mkdirs();
                File textureFile = new File(iconFolder, "texture.png");
                Files.write(textureFile.toPath(), bytes);
                Files.writeString(new File(iconFolder, "name.txt").toPath(), "Tab Icon");

                server.execute(() -> {
                    CustomBlockSyncPayload payload = new CustomBlockSyncPayload(
                        CustomBlocksMod.TAB_ICON_ID, "Tab Icon", bytes);
                    for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                        ServerPlayNetworking.send(player, payload);
                    }
                    source.sendMessage(Text.literal("§a[CustomBlocks] Tab icon set! Restart client to see it."));
                });

            } catch (Exception e) {
                server.execute(() -> source.sendError(
                    Text.literal("[CustomBlocks] Error: " + e.getMessage())));
            }
        }, "CustomBlocks-TabIcon");
        t.setDaemon(true);
        t.start();

        return 1;
    }

    private static int createBlock(ServerCommandSource source, String rawId,
                                   String displayName, String imageUrl) {
        String blockId = rawId.toLowerCase().replaceAll("[^a-z0-9_]", "_");

        if (blockId.isEmpty()) {
            source.sendError(Text.literal("[CustomBlocks] Invalid ID: " + rawId));
            return 0;
        }
        if (blockId.equals(CustomBlocksMod.TAB_ICON_ID)) {
            source.sendError(Text.literal("[CustomBlocks] 'tab_icon' is reserved. Use /customblock settabicon <url>"));
            return 0;
        }
        if (CustomBlocksMod.CUSTOM_BLOCKS.containsKey(blockId)) {
            source.sendError(Text.literal("[CustomBlocks] Block '" + blockId + "' already exists!"));
            return 0;
        }

        File blockFolder = new File("config/customblocks/" + blockId);
        blockFolder.mkdirs();
        try {
            Files.writeString(new File(blockFolder, "name.txt").toPath(), displayName);
        } catch (IOException e) {
            source.sendError(Text.literal("[CustomBlocks] Could not write name.txt"));
            return 0;
        }

        File textureFile = new File(blockFolder, "texture.png");
        MinecraftServer server = source.getServer();

        if (imageUrl != null) {
            source.sendMessage(Text.literal("§e[CustomBlocks] Downloading texture..."));
            final String finalUrl = imageUrl;
            final String finalName = displayName;

            Thread t = new Thread(() -> {
                try {
                    HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(finalUrl))
                            .header("User-Agent", "CustomBlocksMod/1.0")
                            .timeout(Duration.ofSeconds(15)).build();
                    HttpResponse<byte[]> res = http.send(req, HttpResponse.BodyHandlers.ofByteArray());

                    if (res.statusCode() != 200) {
                        server.execute(() -> source.sendError(
                            Text.literal("[CustomBlocks] Download failed. HTTP " + res.statusCode())));
                        return;
                    }

                    byte[] bytes = res.body();
                    Files.write(textureFile.toPath(), bytes);
                    server.execute(() -> finishCreation(source, server, blockId, finalName,
                                                         blockFolder, textureFile, bytes));
                } catch (Exception e) {
                    server.execute(() -> source.sendError(
                        Text.literal("[CustomBlocks] Download error: " + e.getMessage())));
                }
            }, "CustomBlocks-Download");
            t.setDaemon(true);
            t.start();
        } else {
            byte[] bytes = getPlaceholderPng();
            try { Files.write(textureFile.toPath(), bytes); }
            catch (IOException e) {
                source.sendError(Text.literal("[CustomBlocks] Could not write texture."));
                return 0;
            }
            finishCreation(source, server, blockId, displayName, blockFolder, textureFile, bytes);
        }

        return 1;
    }

    private static void finishCreation(ServerCommandSource source, MinecraftServer server,
                                        String blockId, String displayName,
                                        File blockFolder, File textureFile, byte[] textureBytes) {
        boolean ok = CustomBlocksMod.registerBlockDynamic(blockId, displayName, blockFolder, textureFile);
        if (!ok) {
            source.sendError(Text.literal("[CustomBlocks] Failed to register block!"));
            return;
        }

        CustomBlockSyncPayload payload = new CustomBlockSyncPayload(blockId, displayName, textureBytes);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, payload);
        }

        source.sendMessage(Text.literal("§a[CustomBlocks] '" + displayName + "' created! Check your Custom Blocks tab."));
        source.sendMessage(Text.literal("§7ID: customblocks:" + blockId));
    }

    private static int listBlocks(ServerCommandSource source) {
        if (CustomBlocksMod.CUSTOM_BLOCKS.isEmpty()) {
            source.sendMessage(Text.literal("§7[CustomBlocks] No custom blocks loaded."));
            return 1;
        }
        source.sendMessage(Text.literal("§e[CustomBlocks] Loaded blocks:"));
        for (var e : CustomBlocksMod.CUSTOM_BLOCKS.entrySet())
            source.sendMessage(Text.literal("  §f- customblocks:" + e.getKey()
                + " §7(\"" + e.getValue().getCustomDisplayName() + "\")"));
        return 1;
    }

    private static int showHelp(ServerCommandSource source) {
        source.sendMessage(Text.literal("§e=== CustomBlocks ==="));
        source.sendMessage(Text.literal("§f/customblock create <id> <name>"));
        source.sendMessage(Text.literal("§f/customblock createurl <id> <name> <url>  §7(URL last)"));
        source.sendMessage(Text.literal("§f/customblock settabicon <url>  §7(sets the tab icon)"));
        source.sendMessage(Text.literal("§f/customblock list"));
        source.sendMessage(Text.literal("§aFind blocks in Creative → Custom Blocks tab!"));
        return 1;
    }

    private static byte[] getPlaceholderPng() {
        return new byte[]{
            (byte)0x89,0x50,0x4E,0x47,0x0D,0x0A,0x1A,0x0A,0x00,0x00,0x00,0x0D,0x49,0x48,0x44,0x52,
            0x00,0x00,0x00,0x01,0x00,0x00,0x00,0x01,0x08,0x02,0x00,0x00,0x00,(byte)0x90,0x77,0x53,(byte)0xDE,
            0x00,0x00,0x00,0x0C,0x49,0x44,0x41,0x54,0x08,(byte)0xD7,0x63,(byte)0x88,(byte)0x88,(byte)0x88,
            0x00,0x00,0x00,0x04,0x00,0x01,(byte)0xE2,0x21,(byte)0xBC,0x33,0x00,0x00,0x00,0x00,
            0x49,0x45,0x4E,0x44,(byte)0xAE,0x42,0x60,(byte)0x82
        };
    }
}
