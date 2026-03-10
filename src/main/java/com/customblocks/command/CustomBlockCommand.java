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

                    .then(CommandManager.literal("list")
                        .executes(ctx -> listBlocks(ctx.getSource()))
                    )

                    .then(CommandManager.literal("help")
                        .executes(ctx -> showHelp(ctx.getSource()))
                    )
            );
        });
    }

    private static int createBlock(ServerCommandSource source, String rawId,
                                    String displayName, String imageUrl) {
        String blockId = rawId.toLowerCase().replaceAll("[^a-z0-9_]", "_");

        if (blockId.isEmpty()) {
            source.sendError(Text.literal("[CustomBlocks] Invalid block ID: " + rawId));
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

        MinecraftServer server = source.getServer();

        if (imageUrl != null) {
            source.sendMessage(Text.literal("§e[CustomBlocks] Downloading texture for '" + displayName + "'..."));
            final String finalUrl = imageUrl;
            final String finalDisplayName = displayName;

            Thread downloadThread = new Thread(() -> {
                try {
                    HttpClient http = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(10))
                            .build();
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(finalUrl))
                            .header("User-Agent", "CustomBlocksMod/1.0")
                            .timeout(Duration.ofSeconds(15))
                            .build();
                    HttpResponse<byte[]> res = http.send(req, HttpResponse.BodyHandlers.ofByteArray());

                    if (res.statusCode() != 200) {
                        server.execute(() -> source.sendError(
                            Text.literal("[CustomBlocks] Download failed. HTTP " + res.statusCode())));
                        return;
                    }

                    byte[] textureBytes = res.body();
                    File textureFile = new File(blockFolder, "texture.png");
                    Files.write(textureFile.toPath(), textureBytes);

                    server.execute(() -> finishCreation(source, server, blockId, finalDisplayName,
                                                         blockFolder, textureFile, textureBytes));

                } catch (Exception e) {
                    server.execute(() -> source.sendError(
                        Text.literal("[CustomBlocks] Download error: " + e.getMessage())));
                }
            }, "CustomBlocks-Download");
            downloadThread.setDaemon(true);
            downloadThread.start();

        } else {
            byte[] textureBytes = getPlaceholderPng();
            File textureFile = new File(blockFolder, "texture.png");
            try {
                Files.write(textureFile.toPath(), textureBytes);
            } catch (IOException e) {
                source.sendError(Text.literal("[CustomBlocks] Could not write texture."));
                return 0;
            }
            finishCreation(source, server, blockId, displayName, blockFolder, textureFile, textureBytes);
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

        if (source.getEntity() instanceof ServerPlayerEntity player) {
            player.giveItemStack(new net.minecraft.item.ItemStack(
                    CustomBlocksMod.CUSTOM_BLOCKS.get(blockId)));
        }

        source.sendMessage(Text.literal("§a[CustomBlocks] '" + displayName + "' created and given to you!"));
        source.sendMessage(Text.literal("§7ID: customblocks:" + blockId));
    }

    private static int listBlocks(ServerCommandSource source) {
        if (CustomBlocksMod.CUSTOM_BLOCKS.isEmpty()) {
            source.sendMessage(Text.literal("§7[CustomBlocks] No custom blocks loaded."));
            return 1;
        }
        source.sendMessage(Text.literal("§e[CustomBlocks] Loaded blocks:"));
        for (var entry : CustomBlocksMod.CUSTOM_BLOCKS.entrySet()) {
            source.sendMessage(Text.literal("  §f- customblocks:" + entry.getKey()
                    + " §7(\"" + entry.getValue().getCustomDisplayName() + "\")"));
        }
        return 1;
    }

    private static int showHelp(ServerCommandSource source) {
        source.sendMessage(Text.literal("§e=== CustomBlocks Commands ==="));
        source.sendMessage(Text.literal("§f/customblock create <id> <name>"));
        source.sendMessage(Text.literal("  §7Grey placeholder texture. Underscores = spaces in name."));
        source.sendMessage(Text.literal("§f/customblock createurl <id> <name> <url>"));
        source.sendMessage(Text.literal("  §7Downloads texture. URL goes LAST."));
        source.sendMessage(Text.literal("§7Example:"));
        source.sendMessage(Text.literal("§f  /customblock createurl lava_brick Lava_Brick https://i.imgur.com/abc.png"));
        source.sendMessage(Text.literal("§aNo restart needed!"));
        return 1;
    }

    private static byte[] getPlaceholderPng() {
        return new byte[]{
            (byte)0x89,0x50,0x4E,0x47,0x0D,0x0A,0x1A,0x0A,
            0x00,0x00,0x00,0x0D,0x49,0x48,0x44,0x52,
            0x00,0x00,0x00,0x01,0x00,0x00,0x00,0x01,
            0x08,0x02,0x00,0x00,0x00,
            (byte)0x90,0x77,0x53,(byte)0xDE,
            0x00,0x00,0x00,0x0C,0x49,0x44,0x41,0x54,
            0x08,(byte)0xD7,0x63,(byte)0x88,(byte)0x88,
            (byte)0x88,0x00,0x00,0x00,0x04,0x00,0x01,
            (byte)0xE2,0x21,(byte)0xBC,0x33,
            0x00,0x00,0x00,0x00,0x49,0x45,0x4E,0x44,
            (byte)0xAE,0x42,0x60,(byte)0x82
        };
    }
}
