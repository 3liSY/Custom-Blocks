package com.hangman.common.network;

import com.hangman.common.config.HangmanConfig;
import java.util.UUID;
import com.hangman.common.config.PlayerStats;
import com.hangman.server.game.HangmanGame;
import com.hangman.server.game.HangmanGameManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.List;

import static com.hangman.common.network.HangmanPackets.*;

public final class HangmanNetworking {

    private HangmanNetworking() {}

    // ── server-side C2S receiver registration ─────────────────────────────────

    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(HangmanPayload.ID, (payload, ctx) -> {
            String type = payload.type();
            PacketByteBuf buf = payload.data();
            ServerPlayerEntity player = ctx.player();
            MinecraftServer server = ctx.server();

            switch (type) {
                case C2S_ACCEPT_INVITE  -> server.execute(() -> handleAcceptInvite(player, server));
                case C2S_DECLINE_INVITE -> server.execute(() -> handleDecline(player, server));
                case C2S_SUBMIT_WORD    -> {
                    String word     = buf.readString(256);
                    String category = buf.readString(64);
                    server.execute(() -> handleSubmitWord(player, word, category, server));
                }
                case C2S_GUESS_LETTER -> {
                    char letter = (char) buf.readShort();
                    server.execute(() -> handleGuessLetter(player, letter, server));
                }
                case C2S_REQUEST_HINT -> server.execute(() -> handleHintRequest(player, server));
                case C2S_FORFEIT      -> server.execute(() -> handleForfeit(player, server));
                case C2S_SPAWN_GALLOWS -> {
                    int x = buf.readInt(), y = buf.readInt(), z = buf.readInt();
                    server.execute(() -> HangmanGameManager.get().spawnGallows(player, new BlockPos(x, y, z)));
                }
                case C2S_SAVE_OVERLAY -> {
                    // Overlay settings are client-only; nothing to process server-side
                }
            }
        });
    }

    // ── C2S handlers ──────────────────────────────────────────────────────────

    private static void handleAcceptInvite(ServerPlayerEntity player, MinecraftServer server) {
        HangmanGameManager.get().acceptInvite(player, server);
    }

    private static void handleDecline(ServerPlayerEntity player, MinecraftServer server) {
        HangmanGameManager.get().declineInvite(player, server);
    }

    private static void handleSubmitWord(ServerPlayerEntity player, String word, String category,
                                          MinecraftServer server) {
        HangmanGame game = HangmanGameManager.get().getGame(player.getUuid());
        if (game == null || game.getPhase() != HangmanGame.Phase.WAITING_FOR_WORD) return;

        // Validate word is not empty
        if (word.isBlank()) {
            player.sendMessage(Text.translatable("hangman.error.word_empty"), false);
            return;
        }

        game.setWord(word, category);

        ServerPlayerEntity hanged = server.getPlayerManager().getPlayer(game.getHangedId());
        ServerPlayerEntity hanger = server.getPlayerManager().getPlayer(game.getHangerId());

        // Send game start to both players
        if (hanged != null) sendGameStart(hanged, game, true);
        if (hanger != null) sendGameStart(hanger, game, false);

        // Start timer if configured
        HangmanGameManager.get().startTimer(game, server);
    }

    public static void handleGuessLetter(ServerPlayerEntity player, char letter,
                                           MinecraftServer server) {
        HangmanGame game = HangmanGameManager.get().getGame(player.getUuid());
        if (game == null || game.getPhase() != HangmanGame.Phase.IN_PROGRESS) return;
        if (!player.getUuid().equals(game.getHangerId())) {
            player.sendMessage(Text.translatable("hangman.error.not_your_turn"), false);
            return;
        }

        HangmanGame.GuessResult result = game.guess(letter);
        ServerPlayerEntity hanged = server.getPlayerManager().getPlayer(game.getHangedId());
        ServerPlayerEntity hanger = server.getPlayerManager().getPlayer(game.getHangerId());

        switch (result) {
            case ALREADY_GUESSED -> player.sendMessage(
                Text.translatable("hangman.error.already_guessed", letter), false);

            case CORRECT -> {
                broadcastGameMessage(server, game,
                    Text.translatable("hangman.game.correct", letter));
                sendGameUpdate(hanged, hanger, game);
                // Notify client: sound = correct
                sendSoundEvent(hanged, "correct");
                sendSoundEvent(hanger, "correct");
            }

            case WRONG -> {
                broadcastGameMessage(server, game,
                    Text.translatable("hangman.game.wrong", letter,
                        game.getWrongGuesses(), game.getMaxWrongGuesses()));
                sendGameUpdate(hanged, hanger, game);
                sendLimbRemoved(hanged, hanger, game);
                sendSoundEvent(hanged, "wrong");
                sendSoundEvent(hanger, "wrong");
            }

            case WIN -> {
                broadcastGameMessage(server, game,
                    Text.translatable("hangman.game.win_hanger",
                        hanger != null ? hanger.getName().getString() : "?",
                        game.getSecretWord()));
                sendGameUpdate(hanged, hanger, game);
                awardWin(server, game, true);
                finishOrNextRound(game, server);
            }

            case LOSE -> {
                broadcastGameMessage(server, game,
                    Text.translatable("hangman.game.win_hanged",
                        hanged != null ? hanged.getName().getString() : "?",
                        game.getSecretWord()));
                // Place lava
                if (HangmanConfig.get().placeGLavaOnLoss && hanged != null) {
                    hanged.getServerWorld().setBlockState(
                        hanged.getBlockPos().down(),
                        net.minecraft.block.Blocks.LAVA.getDefaultState());
                }
                sendGameUpdate(hanged, hanger, game);
                sendLimbRemoved(hanged, hanger, game);
                sendSoundEvent(hanged, "lose");
                sendSoundEvent(hanger, "lose");
                awardWin(server, game, false);
                finishOrNextRound(game, server);
            }
        }
    }

    private static void handleHintRequest(ServerPlayerEntity player, MinecraftServer server) {
        HangmanGame game = HangmanGameManager.get().getGame(player.getUuid());
        if (game == null || game.getPhase() != HangmanGame.Phase.IN_PROGRESS) return;

        // Deduct XP if required
        int cost = HangmanConfig.get().hintXpCost;
        if (cost > 0 && player.experienceLevel < cost) {
            player.sendMessage(Text.literal("§cNot enough XP for a hint! Cost: " + cost + " levels."), false);
            return;
        }
        if (cost > 0) player.addExperience(-cost);

        char revealed = game.useHint();
        if (revealed == '\0') {
            player.sendMessage(Text.literal("§cNo hints available."), false);
            return;
        }

        broadcastGameMessage(server, game, Text.translatable("hangman.game.hint_used"));
        ServerPlayerEntity hanged = server.getPlayerManager().getPlayer(game.getHangedId());
        ServerPlayerEntity hanger = server.getPlayerManager().getPlayer(game.getHangerId());
        sendGameUpdate(hanged, hanger, game);
    }

    private static void handleForfeit(ServerPlayerEntity player, MinecraftServer server) {
        HangmanGame game = HangmanGameManager.get().getGame(player.getUuid());
        if (game == null) return;
        broadcastGameMessage(server, game,
            Text.translatable("hangman.game.forfeit", player.getName().getString()));
        HangmanGameManager.get().endGame(game, server);
    }

    public static void handleTimerExpiry(HangmanGame game, MinecraftServer server) {
        if (game == null || server == null) return;
        if (game.getPhase() != HangmanGame.Phase.IN_PROGRESS) return;
        String action = HangmanConfig.get().timerAction;
        ServerPlayerEntity hanger = server.getPlayerManager().getPlayer(game.getHangerId());
        broadcastGameMessage(server, game, Text.translatable("hangman.game.timer_out"));
        switch (action) {
            case "WRONG_GUESS" -> {
                if (hanger != null) handleGuessLetter(hanger, '_', server); // forced wrong
            }
            case "HANGED_WINS" -> {
                // Hanged wins
                awardWin(server, game, false);
                HangmanGameManager.get().endGame(game, server);
            }
            case "CANCEL" -> HangmanGameManager.get().endGame(game, server);
        }
    }

    // ── reward / stats ────────────────────────────────────────────────────────

    private static void awardWin(MinecraftServer server, HangmanGame game, boolean hangerWon) {
        UUID winnerId = hangerWon ? game.getHangerId() : game.getHangedId();
        UUID loserId  = hangerWon ? game.getHangedId() : game.getHangerId();

        ServerPlayerEntity winner = server.getPlayerManager().getPlayer(winnerId);
        ServerPlayerEntity loser  = server.getPlayerManager().getPlayer(loserId);

        int winXp  = game.getConfig().winnerXp;
        int loseXp = game.getConfig().loserXp;

        if (winner != null && winXp > 0) winner.addExperience(winXp);
        if (loser  != null && loseXp > 0) loser.addExperience(loseXp);

        // Item reward
        String itemReward = game.getConfig().winnerItemReward;
        if (winner != null && itemReward != null && !itemReward.equalsIgnoreCase("none")) {
            try {
                String[] parts = itemReward.split(":");
                int count = parts.length >= 3 ? Integer.parseInt(parts[2]) : 1;
                net.minecraft.item.Item item = net.minecraft.registry.Registries.ITEM.get(
                    Identifier.of(parts[0], parts[1]));
                if (item != Items.AIR) {
                    winner.giveItemStack(new net.minecraft.item.ItemStack(item, count));
                }
            } catch (Exception ignored) {}
        }

        // Stats
        if (HangmanConfig.get().persistStats) {
            PlayerStats.of(winnerId).recordWin();
            PlayerStats.of(loserId).recordLoss();
        }

        // Sounds
        sendSoundEvent(winner, "win");
        sendSoundEvent(loser, "lose");
    }

    private static void finishOrNextRound(HangmanGame game, MinecraftServer server) {
        HangmanGameManager.get().cancelTimer(game.getHangedId());
        if (game.getCurrentRound() < game.getTotalRounds()) {
            game.nextRound();
            // Tell word chooser to enter next word
            UUID chooserId = "HANGER".equalsIgnoreCase(game.getConfig().wordChooser)
                ? game.getHangerId() : game.getHangedId();
            ServerPlayerEntity chooser = server.getPlayerManager().getPlayer(chooserId);
            if (chooser != null) sendOpenWordScreen(chooser);
        } else {
            HangmanGameManager.get().endGame(game, server);
        }
    }

    // ── S2C senders ───────────────────────────────────────────────────────────

    public static void sendInviteNotification(ServerPlayerEntity invitee, String inviterName) {
        PacketByteBuf buf = newBuf();
        buf.writeString(inviterName, 64);
        send(invitee, S2C_INVITE_NOTIFY, buf);
    }

    public static void sendOpenWordScreen(ServerPlayerEntity player) {
        send(player, S2C_OPEN_WORD_SCREEN, newBuf());
    }

    public static void sendGameStart(ServerPlayerEntity player, HangmanGame game, boolean isHanged) {
        PacketByteBuf buf = newBuf();
        buf.writeBoolean(isHanged);
        buf.writeString(game.getMaskedWord(), 512);
        buf.writeInt(game.getWrongGuesses());
        buf.writeInt(game.getMaxWrongGuesses());
        buf.writeString(game.getCategory(), 64);
        writeCharList(buf, game.getWrongLetters());
        writeStringList(buf, game.getRemovedLimbs());
        buf.writeBoolean(game.getConfig().hintsEnabled);
        buf.writeInt(game.getConfig().maxHints);
        buf.writeInt(game.getConfig().timerSeconds);
        send(player, S2C_GAME_START, buf);
    }

    public static void sendGameUpdate(ServerPlayerEntity hanged, ServerPlayerEntity hanger,
                                       HangmanGame game) {
        PacketByteBuf buf = newBuf();
        buf.writeString(game.getMaskedWord(), 512);
        buf.writeInt(game.getWrongGuesses());
        buf.writeInt(game.getMaxWrongGuesses());
        writeCharList(buf, game.getWrongLetters());
        writeCharList(buf, game.getGuessedLetters());
        writeStringList(buf, game.getRemovedLimbs());
        if (hanged != null) send(hanged, S2C_GAME_UPDATE, buf.copy());
        if (hanger != null) send(hanger, S2C_GAME_UPDATE, buf);
    }

    public static void sendLimbRemoved(ServerPlayerEntity hanged, ServerPlayerEntity hanger,
                                        HangmanGame game) {
        List<String> limbs = game.getRemovedLimbs();
        if (limbs.isEmpty()) return;
        String lastLimb = limbs.get(limbs.size() - 1);
        PacketByteBuf buf = newBuf();
        buf.writeString(lastLimb, 32);
        if (hanged != null) send(hanged, S2C_LIMB_REMOVED, buf.copy());
        if (hanger != null) send(hanger, S2C_LIMB_REMOVED, buf);
    }

    public static void sendGameOver(ServerPlayerEntity player, HangmanGame game) {
        PacketByteBuf buf = newBuf();
        buf.writeBoolean(game.isHangerWonLastRound());
        buf.writeString(game.getSecretWord(), 256);
        buf.writeInt(game.getHangerWins());
        buf.writeInt(game.getHangedWins());
        send(player, S2C_GAME_OVER, buf);
    }

    public static void sendSoundEvent(ServerPlayerEntity player, String eventType) {
        if (player == null) return;
        if (!HangmanConfig.get().soundsEnabled) return;
        PacketByteBuf buf = newBuf();
        buf.writeString(eventType, 32);
        send(player, "s2c_sound", buf);
    }

    public static void sendTimerSync(ServerPlayerEntity player, int remainingSeconds) {
        PacketByteBuf buf = newBuf();
        buf.writeInt(remainingSeconds);
        send(player, S2C_TIMER_SYNC, buf);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static void send(ServerPlayerEntity player, String type, PacketByteBuf buf) {
        if (player == null) return;
        ServerPlayNetworking.send(player, new HangmanPayload(type, buf));
    }

    public static void broadcastGameMessage(MinecraftServer server, HangmanGame game, Text msg) {
        ServerPlayerEntity hanged = server.getPlayerManager().getPlayer(game.getHangedId());
        ServerPlayerEntity hanger = server.getPlayerManager().getPlayer(game.getHangerId());
        if (hanged != null && HangmanConfig.get().chatMessages) hanged.sendMessage(msg, false);
        if (hanger != null && HangmanConfig.get().chatMessages) hanger.sendMessage(msg, false);
        // Send to spectators too
        for (UUID sid : HangmanGameManager.get().getSpectatorIds(game)) {
            ServerPlayerEntity spec = server.getPlayerManager().getPlayer(sid);
            if (spec != null) spec.sendMessage(msg, false);
        }
    }

}
