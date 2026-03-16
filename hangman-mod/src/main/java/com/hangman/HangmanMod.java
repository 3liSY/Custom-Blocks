package com.hangman;

import com.hangman.common.config.HangmanConfig;
import com.hangman.server.game.GameArea;
import com.hangman.common.config.PlayerStats;
import com.hangman.common.network.HangmanNetworking;
import com.hangman.common.network.HangmanPackets;
import com.hangman.server.command.HangmanCommand;
import com.hangman.server.game.HangmanGame;
import com.hangman.server.game.HangmanGameManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class HangmanMod implements ModInitializer {

    public static final String MOD_ID = "hangman";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Config directory on the server (config/hangman/) */
    public static Path CONFIG_DIR;

    @Override
    public void onInitialize() {
        LOGGER.info("[Hangman] Initializing...");

        // ── register packets ───────────────────────────────────────────────────
        HangmanPackets.register();

        // ── register C2S receivers ─────────────────────────────────────────────
        HangmanNetworking.registerServerReceivers();

        // ── register commands ──────────────────────────────────────────────────
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) ->
            HangmanCommand.register(dispatcher));

        // ── server lifecycle ───────────────────────────────────────────────────
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            CONFIG_DIR = server.getRunDirectory().resolve("config").resolve("hangman");
            HangmanConfig.load(CONFIG_DIR);
            GameArea.load(CONFIG_DIR);
            if (HangmanConfig.get().persistStats) {
                PlayerStats.load(CONFIG_DIR);
            }
            LOGGER.info("[Hangman] Config loaded from {}", CONFIG_DIR);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (HangmanConfig.get().persistStats) {
                PlayerStats.save();
            }
            HangmanConfig.save(CONFIG_DIR);
            LOGGER.info("[Hangman] Config saved.");
        });

        // ── player disconnect handling ─────────────────────────────────────────
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.player;
            HangmanGame game = HangmanGameManager.get().getGame(player.getUuid());
            if (game == null) {
                // Check if spectator
                HangmanGameManager.get().removeSpectator(player.getUuid());
                return;
            }

            String behavior = HangmanConfig.get().disconnectBehavior;
            switch (behavior) {
                case "FORFEIT" -> {
                    HangmanNetworking.broadcastGameMessage(server, game,
                        Text.translatable("hangman.game.disconnected", player.getName().getString()));
                    HangmanGameManager.get().endGame(game, server);
                }
                case "PAUSE" -> {
                    // Cancel timer; other player waits
                    HangmanGameManager.get().cancelTimer(player.getUuid());
                    ServerPlayerEntity other = server.getPlayerManager().getPlayer(
                        player.getUuid().equals(game.getHangedId())
                            ? game.getHangerId() : game.getHangedId());
                    if (other != null) other.sendMessage(
                        Text.literal("§e[Hangman] " + player.getName().getString()
                            + " disconnected. Game paused."), false);
                }
                default -> HangmanGameManager.get().endGame(game, server);
            }
        });

        LOGGER.info("[Hangman] Initialized!");
    }
}
