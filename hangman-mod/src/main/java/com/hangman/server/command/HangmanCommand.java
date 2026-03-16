package com.hangman.server.command;

import com.hangman.HangmanMod;
import com.hangman.common.config.HangmanConfig;
import com.hangman.server.game.GameArea;
import com.hangman.common.config.PlayerStats;
import com.hangman.common.network.HangmanNetworking;
import com.hangman.server.game.HangmanGame;
import com.hangman.server.game.HangmanGameManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.List;
public final class HangmanCommand {

    private HangmanCommand() {}

    public static void register(CommandDispatcher<ServerCommandSource> d) {
        var root = build();
        d.register(root);
        // /hm alias
        d.register(CommandManager.literal("hm")
            .redirect(d.getRoot().getChild("hangman")));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> build() {
        return CommandManager.literal("hangman")
            .then(CommandManager.literal("gui")
                .executes(ctx -> {
                    try {
                        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                        // Send a packet to open the main GUI on the client
                        PacketByteBuf buf = new net.minecraft.network.PacketByteBuf(io.netty.buffer.Unpooled.buffer());
                        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                            player,
                            new com.hangman.common.network.HangmanPackets.HangmanPayload("s2c_open_gui", buf));
                    } catch (Exception e) {
                        ctx.getSource().sendError(Text.literal("Error: " + e.getMessage()));
                    }
                    return 1;
                }))
            // ── invite / game lifecycle ────────────────────────────────────────
            .then(CommandManager.literal("start")
                .then(CommandManager.argument("player", EntityArgumentType.player())
                    .executes(ctx -> cmdStart(ctx))))
            .then(CommandManager.literal("invite")
                .then(CommandManager.argument("player", EntityArgumentType.player())
                    .executes(ctx -> cmdStart(ctx))))
            .then(CommandManager.literal("accept")
                .executes(ctx -> cmdAccept(ctx)))
            .then(CommandManager.literal("decline")
                .executes(ctx -> cmdDecline(ctx)))
            .then(CommandManager.literal("forfeit")
                .executes(ctx -> cmdForfeit(ctx)))
            .then(CommandManager.literal("stop")
                .executes(ctx -> cmdStop(ctx)))

            // ── in-game ────────────────────────────────────────────────────────
            .then(CommandManager.literal("word")
                .then(CommandManager.argument("word", StringArgumentType.greedyString())
                    .executes(ctx -> cmdWord(ctx, ""))))
            .then(CommandManager.literal("guess")
                .then(CommandManager.argument("letter", StringArgumentType.word())
                    .executes(ctx -> cmdGuess(ctx))))
            .then(CommandManager.literal("hint")
                .executes(ctx -> cmdHint(ctx)))

            // ── gallows ────────────────────────────────────────────────────────
            .then(CommandManager.literal("spawnwood")
                .executes(ctx -> cmdSpawnWood(ctx)))

            // ── spectate ──────────────────────────────────────────────────────
            .then(CommandManager.literal("spectate")
                .then(CommandManager.argument("player", EntityArgumentType.player())
                    .executes(ctx -> cmdSpectate(ctx))))

            // ── areas ─────────────────────────────────────────────────────────
            .then(CommandManager.literal("setarea")
                .then(CommandManager.argument("name", StringArgumentType.word())
                    .executes(ctx -> cmdSetArea(ctx))))
            .then(CommandManager.literal("listarea")
                .executes(ctx -> cmdListAreas(ctx)))
            .then(CommandManager.literal("deletearea")
                .then(CommandManager.argument("name", StringArgumentType.word())
                    .executes(ctx -> cmdDeleteArea(ctx))))
            .then(CommandManager.literal("sethangedpos")
                .then(CommandManager.argument("name", StringArgumentType.word())
                    .executes(ctx -> cmdSetHangedPos(ctx))))
            .then(CommandManager.literal("sethangerpos")
                .then(CommandManager.argument("name", StringArgumentType.word())
                    .executes(ctx -> cmdSetHangerPos(ctx))))

            // ── stats / leaderboard ───────────────────────────────────────────
            .then(CommandManager.literal("stats")
                .executes(ctx -> cmdStats(ctx, null))
                .then(CommandManager.argument("player", EntityArgumentType.player())
                    .executes(ctx -> cmdStats(ctx, EntityArgumentType.getPlayer(ctx, "player")))))
            .then(CommandManager.literal("leaderboard")
                .executes(ctx -> cmdLeaderboard(ctx)))
            .then(CommandManager.literal("resetstats")
                .requires(s -> s.hasPermissionLevel(2))
                .then(CommandManager.argument("player", EntityArgumentType.player())
                    .executes(ctx -> cmdResetStats(ctx))))

            // ── settings ──────────────────────────────────────────────────────
            .then(CommandManager.literal("set")
                .requires(s -> s.hasPermissionLevel(HangmanConfig.get().setCommandOpLevel))
                .then(CommandManager.literal("maxwrong")
                    .then(CommandManager.argument("count", IntegerArgumentType.integer(1, 26))
                        .executes(ctx -> { HangmanConfig.get().maxWrongGuesses = IntegerArgumentType.getInteger(ctx, "count"); saveConfig(ctx); return 1; })))
                .then(CommandManager.literal("canmove")
                    .then(CommandManager.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> { HangmanConfig.get().hangedCanMove = BoolArgumentType.getBool(ctx, "value"); saveConfig(ctx); return 1; })))
                .then(CommandManager.literal("timer")
                    .then(CommandManager.argument("seconds", IntegerArgumentType.integer(0, 3600))
                        .executes(ctx -> { HangmanConfig.get().timerSeconds = IntegerArgumentType.getInteger(ctx, "seconds"); saveConfig(ctx); return 1; })))
                .then(CommandManager.literal("timeraction")
                    .then(CommandManager.argument("action", StringArgumentType.word())
                        .executes(ctx -> { HangmanConfig.get().timerAction = StringArgumentType.getString(ctx, "action").toUpperCase(); saveConfig(ctx); return 1; })))
                .then(CommandManager.literal("rounds")
                    .then(CommandManager.argument("count", IntegerArgumentType.integer(1, 20))
                        .executes(ctx -> { HangmanConfig.get().rounds = IntegerArgumentType.getInteger(ctx, "count"); saveConfig(ctx); return 1; })))
                .then(CommandManager.literal("wordchooser")
                    .then(CommandManager.argument("who", StringArgumentType.word())
                        .executes(ctx -> { HangmanConfig.get().wordChooser = StringArgumentType.getString(ctx, "who").toUpperCase(); saveConfig(ctx); return 1; })))
                .then(CommandManager.literal("limborder")
                    .then(CommandManager.argument("order", StringArgumentType.greedyString())
                        .executes(ctx -> { HangmanConfig.get().limbOrder = List.of(StringArgumentType.getString(ctx, "order").toUpperCase().split(",\\s*")); saveConfig(ctx); return 1; })))
                .then(CommandManager.literal("teleport")
                    .then(CommandManager.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> { HangmanConfig.get().teleportOnStart = BoolArgumentType.getBool(ctx, "value"); saveConfig(ctx); return 1; })))
                .then(CommandManager.literal("multipleGames")
                    .then(CommandManager.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> { HangmanConfig.get().allowMultipleGames = BoolArgumentType.getBool(ctx, "value"); saveConfig(ctx); return 1; })))
                .then(CommandManager.literal("spectators")
                    .then(CommandManager.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> { HangmanConfig.get().allowSpectators = BoolArgumentType.getBool(ctx, "value"); saveConfig(ctx); return 1; })))
                .then(CommandManager.literal("hints")
                    .then(CommandManager.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> { HangmanConfig.get().hintsEnabled = BoolArgumentType.getBool(ctx, "value"); saveConfig(ctx); return 1; })))
                .then(CommandManager.literal("maxhints")
                    .then(CommandManager.argument("count", IntegerArgumentType.integer(0, 10))
                        .executes(ctx -> { HangmanConfig.get().maxHints = IntegerArgumentType.getInteger(ctx, "count"); saveConfig(ctx); return 1; })))
                .then(CommandManager.literal("hintcost")
                    .then(CommandManager.argument("xp", IntegerArgumentType.integer(0, 100))
                        .executes(ctx -> { HangmanConfig.get().hintXpCost = IntegerArgumentType.getInteger(ctx, "xp"); saveConfig(ctx); return 1; })))
                .then(CommandManager.literal("winnerxp")
                    .then(CommandManager.argument("amount", IntegerArgumentType.integer(0, 10000))
                        .executes(ctx -> { HangmanConfig.get().winnerXp = IntegerArgumentType.getInteger(ctx, "amount"); saveConfig(ctx); return 1; })))
                .then(CommandManager.literal("loserxp")
                    .then(CommandManager.argument("amount", IntegerArgumentType.integer(0, 10000))
                        .executes(ctx -> { HangmanConfig.get().loserXp = IntegerArgumentType.getInteger(ctx, "amount"); saveConfig(ctx); return 1; })))
                .then(CommandManager.literal("itemreward")
                    .then(CommandManager.argument("item", StringArgumentType.greedyString())
                        .executes(ctx -> { HangmanConfig.get().winnerItemReward = StringArgumentType.getString(ctx, "item"); saveConfig(ctx); return 1; })))
                .then(CommandManager.literal("sounds")
                    .then(CommandManager.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> { HangmanConfig.get().soundsEnabled = BoolArgumentType.getBool(ctx, "value"); saveConfig(ctx); return 1; })))
                .then(CommandManager.literal("inviteexpiry")
                    .then(CommandManager.argument("seconds", IntegerArgumentType.integer(0, 300))
                        .executes(ctx -> { HangmanConfig.get().inviteExpirySecs = IntegerArgumentType.getInteger(ctx, "seconds"); saveConfig(ctx); return 1; })))
                .then(CommandManager.literal("announce")
                    .then(CommandManager.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> { HangmanConfig.get().serverWideAnnounce = BoolArgumentType.getBool(ctx, "value"); saveConfig(ctx); return 1; })))
                .then(CommandManager.literal("lava")
                    .then(CommandManager.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> { HangmanConfig.get().placeGLavaOnLoss = BoolArgumentType.getBool(ctx, "value"); saveConfig(ctx); return 1; })))
                .then(CommandManager.literal("disconnect")
                    .then(CommandManager.argument("behavior", StringArgumentType.word())
                        .executes(ctx -> { HangmanConfig.get().disconnectBehavior = StringArgumentType.getString(ctx, "behavior").toUpperCase(); saveConfig(ctx); return 1; })))
                .then(CommandManager.literal("postgame")
                    .then(CommandManager.argument("action", StringArgumentType.word())
                        .executes(ctx -> { HangmanConfig.get().postGameAction = StringArgumentType.getString(ctx, "action").toUpperCase(); saveConfig(ctx); return 1; })))
                .then(CommandManager.literal("category")
                    .then(CommandManager.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> { HangmanConfig.get().categoryEnabled = BoolArgumentType.getBool(ctx, "value"); saveConfig(ctx); return 1; })))
            )

            // ── help ──────────────────────────────────────────────────────────
            .then(CommandManager.literal("help")
                .executes(ctx -> cmdHelp(ctx)));
    }

    // ── command handlers ──────────────────────────────────────────────────────

    private static int cmdStart(CommandContext<ServerCommandSource> ctx) {
        try {
            ServerPlayerEntity sender = ctx.getSource().getPlayerOrThrow();
            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
            if (sender.getUuid().equals(target.getUuid())) {
                sender.sendMessage(Text.literal("§cYou cannot invite yourself!"), false);
                return 0;
            }
            HangmanGameManager.get().sendInvite(sender, target);
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("Error: " + e.getMessage()));
        }
        return 1;
    }

    private static int cmdAccept(CommandContext<ServerCommandSource> ctx) {
        try {
            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
            HangmanGameManager.get().acceptInvite(player, ctx.getSource().getServer());
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("Error: " + e.getMessage()));
        }
        return 1;
    }

    private static int cmdDecline(CommandContext<ServerCommandSource> ctx) {
        try {
            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
            HangmanGameManager.get().declineInvite(player, ctx.getSource().getServer());
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("Error: " + e.getMessage()));
        }
        return 1;
    }

    private static int cmdForfeit(CommandContext<ServerCommandSource> ctx) {
        try {
            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
            HangmanGame game = HangmanGameManager.get().getGame(player.getUuid());
            if (game == null) {
                player.sendMessage(Text.translatable("hangman.error.not_in_game"), false);
                return 0;
            }
            HangmanNetworking.broadcastGameMessage(ctx.getSource().getServer(), game,
                Text.translatable("hangman.game.forfeit", player.getName().getString()));
            HangmanGameManager.get().endGame(game, ctx.getSource().getServer());
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("Error: " + e.getMessage()));
        }
        return 1;
    }

    private static int cmdStop(CommandContext<ServerCommandSource> ctx) {
        try {
            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
            HangmanGame game = HangmanGameManager.get().getGame(player.getUuid());
            if (game == null) {
                player.sendMessage(Text.literal("§cYou are not in a game."), false);
                return 0;
            }
            HangmanGameManager.get().endGame(game, ctx.getSource().getServer());
            player.sendMessage(Text.literal("§aGame stopped."), false);
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("Error: " + e.getMessage()));
        }
        return 1;
    }

    private static int cmdWord(CommandContext<ServerCommandSource> ctx, String category) {
        try {
            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
            String word = StringArgumentType.getString(ctx, "word").trim();
            HangmanGame game = HangmanGameManager.get().getGame(player.getUuid());
            if (game == null || game.getPhase() != HangmanGame.Phase.WAITING_FOR_WORD) {
                player.sendMessage(Text.literal("§cNo game waiting for a word."), false);
                return 0;
            }
            HangmanGameManager.get().cancelTimer(game.getHangedId());
            game.setWord(word, category);
            ServerPlayerEntity hanged = ctx.getSource().getServer().getPlayerManager().getPlayer(game.getHangedId());
            ServerPlayerEntity hanger = ctx.getSource().getServer().getPlayerManager().getPlayer(game.getHangerId());
            if (hanged != null) HangmanNetworking.sendGameStart(hanged, game, true);
            if (hanger != null) HangmanNetworking.sendGameStart(hanger, game, false);
            HangmanGameManager.get().startTimer(game, ctx.getSource().getServer());
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("Error: " + e.getMessage()));
        }
        return 1;
    }

    private static int cmdGuess(CommandContext<ServerCommandSource> ctx) {
        try {
            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
            String arg = StringArgumentType.getString(ctx, "letter");
            if (arg.isEmpty()) return 0;
            char letter = Character.toLowerCase(arg.charAt(0));

            HangmanGame game = HangmanGameManager.get().getGame(player.getUuid());
            if (game == null) {
                player.sendMessage(Text.translatable("hangman.error.not_in_game"), false);
                return 0;
            }
            if (game.getPhase() != HangmanGame.Phase.IN_PROGRESS) {
                player.sendMessage(Text.translatable("hangman.error.word_waiting"), false);
                return 0;
            }
            // Delegate to the canonical handler so sounds, lava, stats all fire correctly
            HangmanNetworking.handleGuessLetter(player, letter, ctx.getSource().getServer());
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("Error: " + e.getMessage()));
        }
        return 1;
    }

    private static int cmdHint(CommandContext<ServerCommandSource> ctx) {
        try {
            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
            HangmanGame game = HangmanGameManager.get().getGame(player.getUuid());
            if (game == null) {
                player.sendMessage(Text.translatable("hangman.error.not_in_game"), false);
                return 0;
            }
            HangmanNetworking.broadcastGameMessage(ctx.getSource().getServer(), game,
                Text.translatable("hangman.game.hint_used"));
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("Error: " + e.getMessage()));
        }
        return 1;
    }

    private static int cmdSpawnWood(CommandContext<ServerCommandSource> ctx) {
        try {
            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
            BlockPos pos = player.getBlockPos();
            HangmanGameManager.get().spawnGallows(player, pos);
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("Error: " + e.getMessage()));
        }
        return 1;
    }

    private static int cmdSpectate(CommandContext<ServerCommandSource> ctx) {
        try {
            ServerPlayerEntity spectator = ctx.getSource().getPlayerOrThrow();
            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
            HangmanGame game = HangmanGameManager.get().getGame(target.getUuid());
            if (game == null) {
                spectator.sendMessage(Text.literal("§c" + target.getName().getString() + " is not in a Hangman game."), false);
                return 0;
            }
            if (!HangmanConfig.get().allowSpectators) {
                spectator.sendMessage(Text.literal("§cSpectating is disabled."), false);
                return 0;
            }
            HangmanGameManager.get().addSpectator(spectator.getUuid(), game);
            spectator.sendMessage(Text.literal("§aYou are now spectating a Hangman game!"), false);
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("Error: " + e.getMessage()));
        }
        return 1;
    }

    private static int cmdSetArea(CommandContext<ServerCommandSource> ctx) {
        try {
            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
            String name = StringArgumentType.getString(ctx, "name");
            var game = HangmanGameManager.get().getGame(player.getUuid());
            // Store current player pos as both hanged and hanger pos for this area
            // They can refine with sethangedpos / sethangerpos
            GameArea area = new GameArea(name, player.getWorld().getRegistryKey().getValue().toString(),
                player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch(),
                player.getX() + 3, player.getY(), player.getZ(), player.getYaw(), player.getPitch());
            GameArea.setArea(area);
            player.sendMessage(Text.literal("§aArea '§f" + name + "§a' saved. Use §f/hangman sethangedpos " + name + " §aand §f/hangman sethangerpos " + name + " §ato refine positions."), false);
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("Error: " + e.getMessage()));
        }
        return 1;
    }

    private static int cmdSetHangedPos(CommandContext<ServerCommandSource> ctx) {
        try {
            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
            String name = StringArgumentType.getString(ctx, "name");
            GameArea area = GameArea.getArea(name);
            if (area == null) { player.sendMessage(Text.literal("§cArea '" + name + "' not found."), false); return 0; }
            area.hangedX = player.getX(); area.hangedY = player.getY(); area.hangedZ = player.getZ();
            area.hangedYaw = player.getYaw(); area.hangedPitch = player.getPitch();
            area.dimension = player.getWorld().getRegistryKey().getValue().toString();
            GameArea.setArea(area);
            player.sendMessage(Text.literal("§aHanged player spawn for '§f" + name + "§a' updated."), false);
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("Error: " + e.getMessage()));
        }
        return 1;
    }

    private static int cmdSetHangerPos(CommandContext<ServerCommandSource> ctx) {
        try {
            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
            String name = StringArgumentType.getString(ctx, "name");
            GameArea area = GameArea.getArea(name);
            if (area == null) { player.sendMessage(Text.literal("§cArea '" + name + "' not found."), false); return 0; }
            area.hangerX = player.getX(); area.hangerY = player.getY(); area.hangerZ = player.getZ();
            area.hangerYaw = player.getYaw(); area.hangerPitch = player.getPitch();
            GameArea.setArea(area);
            player.sendMessage(Text.literal("§aHanger spawn for '§f" + name + "§a' updated."), false);
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("Error: " + e.getMessage()));
        }
        return 1;
    }

    private static int cmdListAreas(CommandContext<ServerCommandSource> ctx) {
        var areas = GameArea.getAllAreas();
        if (areas.isEmpty()) { ctx.getSource().sendFeedback(() -> Text.literal("§7No areas saved."), false); return 1; }
        ctx.getSource().sendFeedback(() -> Text.literal("§6=== Hangman Areas ==="), false);
        for (var a : areas) {
            ctx.getSource().sendFeedback(() -> Text.literal("§f- §e" + a.name + " §7(" + a.dimension + ")"), false);
        }
        return 1;
    }

    private static int cmdDeleteArea(CommandContext<ServerCommandSource> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        boolean ok = GameArea.deleteArea(name);
        ctx.getSource().sendFeedback(() -> Text.literal(ok ? "§aArea deleted." : "§cArea not found."), false);
        return 1;
    }

    private static int cmdStats(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity target) {
        try {
            ServerPlayerEntity player = target != null ? target : ctx.getSource().getPlayerOrThrow();
            PlayerStats s = PlayerStats.of(player.getUuid());
            String name = player.getName().getString();
            ctx.getSource().sendFeedback(() -> Text.translatable("hangman.stats.header", name), false);
            ctx.getSource().sendFeedback(() -> Text.translatable("hangman.stats.wins", s.wins), false);
            ctx.getSource().sendFeedback(() -> Text.translatable("hangman.stats.losses", s.losses), false);
            ctx.getSource().sendFeedback(() -> Text.translatable("hangman.stats.streak", s.winStreak), false);
            ctx.getSource().sendFeedback(() -> Text.translatable("hangman.stats.best_streak", s.bestStreak), false);
            ctx.getSource().sendFeedback(() -> Text.translatable("hangman.stats.games", s.gamesPlayed), false);
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("Error: " + e.getMessage()));
        }
        return 1;
    }

    private static int cmdLeaderboard(CommandContext<ServerCommandSource> ctx) {
        ctx.getSource().sendFeedback(() -> Text.translatable("hangman.leaderboard.header"), false);
        var entries = PlayerStats.leaderboard();
        int rank = 1;
        for (var e : entries) {
            if (rank > 10) break;
            PlayerStats s = e.getValue();
            String name = ctx.getSource().getServer().getPlayerManager()
                .getPlayer(e.getKey()) != null
                ? ctx.getSource().getServer().getPlayerManager().getPlayer(e.getKey()).getName().getString()
                : e.getKey().toString().substring(0, 8);
            final int r = rank++;
            ctx.getSource().sendFeedback(() -> Text.translatable("hangman.leaderboard.entry",
                r, name, s.wins, s.losses, s.winStreak), false);
        }
        return 1;
    }

    private static int cmdResetStats(CommandContext<ServerCommandSource> ctx) {
        try {
            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
            PlayerStats.of(target.getUuid()).wins = 0;
            PlayerStats.of(target.getUuid()).losses = 0;
            PlayerStats.of(target.getUuid()).winStreak = 0;
            PlayerStats.of(target.getUuid()).gamesPlayed = 0;
            PlayerStats.save();
            ctx.getSource().sendFeedback(() -> Text.literal("§aStats reset for " + target.getName().getString()), false);
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("Error: " + e.getMessage()));
        }
        return 1;
    }

    private static int cmdHelp(CommandContext<ServerCommandSource> ctx) {
        ctx.getSource().sendFeedback(() -> Text.literal("""
            §6=== Hangman Commands (/hm is an alias for /hangman) ===
            §e/hangman start <player>§r - Invite a player
            §e/hangman accept§r - Accept invite
            §e/hangman decline§r - Decline invite
            §e/hangman forfeit§r - Forfeit current game
            §e/hangman stop§r - Force-stop game (OP)
            §e/hangman word <word>§r - Submit secret word
            §e/hangman guess <letter>§r - Guess a letter
            §e/hangman hint§r - Use a hint
            §e/hangman spawnwood§r - Spawn gallows at your feet
            §e/hangman spectate <player>§r - Watch a game
            §e/hangman setarea <name>§r - Save current pos as area
            §e/hangman sethangedpos <name>§r - Set hanged spawn in area
            §e/hangman sethangerpos <name>§r - Set hanger spawn in area
            §e/hangman listarea§r - List all saved areas
            §e/hangman deletearea <name>§r - Delete a saved area
            §e/hangman stats [player]§r - View stats
            §e/hangman leaderboard§r - Top 10 players
            §e/hangman resetstats <player>§r - Reset stats (OP)
            §e/hangman set <option> <value>§r - Change settings (OP)
            §7Options: maxwrong, canmove, timer, timeraction, rounds, wordchooser,
            §7         limborder, teleport, multipleGames, spectators, hints, maxhints,
            §7         hintcost, winnerxp, loserxp, itemreward, sounds, inviteexpiry,
            §7         announce, lava, disconnect, postgame, category
            """), false);
        return 1;
    }

    private static void saveConfig(CommandContext<ServerCommandSource> ctx) {
        HangmanConfig.save(HangmanMod.CONFIG_DIR);
        ctx.getSource().sendFeedback(() -> Text.literal("§aHangman setting saved!"), false);
    }
}
