package com.hangman.common.config;

import com.google.gson.*;
import com.hangman.HangmanMod;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * All customisable server/game settings, persisted to hangman.json.
 * Every field has a sensible default so the mod works out of the box.
 */
public class HangmanConfig {

    // ── singleton ─────────────────────────────────────────────────────────────
    private static HangmanConfig INSTANCE;
    public static HangmanConfig get() {
        if (INSTANCE == null) INSTANCE = new HangmanConfig();
        return INSTANCE;
    }

    // ── Game Rules ────────────────────────────────────────────────────────────
    /** Maximum wrong guesses before the hanged player loses. */
    public int maxWrongGuesses = 6;

    /** Order limbs are removed. Values: RIGHT_ARM,LEFT_ARM,RIGHT_LEG,LEFT_LEG,HEAD,TORSO */
    public List<String> limbOrder = Arrays.asList(
        "RIGHT_ARM","LEFT_ARM","RIGHT_LEG","LEFT_LEG","HEAD"
    );

    /** Who picks the secret word. "HANGED" or "HANGER" */
    public String wordChooser = "HANGED";

    /** Whether the hanged player can move. */
    public boolean hangedCanMove = false;

    /** Whether multiple simultaneous games are allowed on the server. */
    public boolean allowMultipleGames = true;

    /** Game-level timer in seconds. 0 = no timer. */
    public int timerSeconds = 0;

    /** What happens on timer expiry. "WRONG_GUESS", "HANGED_WINS", "CANCEL" */
    public String timerAction = "WRONG_GUESS";

    // ── Teleport ──────────────────────────────────────────────────────────────
    /** Whether to teleport players to the game area on start. */
    public boolean teleportOnStart = true;

    /** Whether to teleport players back when game ends. */
    public boolean teleportOnEnd = true;

    // ── Invites ───────────────────────────────────────────────────────────────
    /** How many seconds before an invite expires. 0 = never. */
    public int inviteExpirySecs = 30;

    /** Whether players can decline invites. */
    public boolean canDeclineInvite = true;

    // ── Spectators ────────────────────────────────────────────────────────────
    /** Whether spectating is allowed. */
    public boolean allowSpectators = true;

    /** Whether spectators need an invite (/hangman spectate). */
    public boolean spectateByInviteOnly = false;

    /** Whether spectators can send reactions. */
    public boolean spectatorReactions = true;

    /** Whether spectators can vote on winner. */
    public boolean spectatorVoting = false;

    // ── Hints ─────────────────────────────────────────────────────────────────
    /** Whether the hint system is active. */
    public boolean hintsEnabled = false;

    /** Maximum hints per game. */
    public int maxHints = 1;

    /** XP cost per hint. 0 = free. */
    public int hintXpCost = 0;

    /** Hint type: "RANDOM_LETTER", "CHOSEN_LETTER", "CATEGORY" */
    public String hintType = "RANDOM_LETTER";

    // ── Category ──────────────────────────────────────────────────────────────
    /** Whether the category system is enabled. */
    public boolean categoryEnabled = false;

    /** Whether category is required when submitting a word. */
    public boolean categoryRequired = false;

    // ── Rounds ────────────────────────────────────────────────────────────────
    /** Number of rounds. 1 = single round. */
    public int rounds = 1;

    // ── Rewards ───────────────────────────────────────────────────────────────
    /** XP rewarded to the winner. 0 = none. */
    public int winnerXp = 100;

    /** XP rewarded to the loser. 0 = none. */
    public int loserXp = 0;

    /** Item reward for winner. Format: "minecraft:item_id:count" or "none" */
    public String winnerItemReward = "none";

    // ── Post-game ─────────────────────────────────────────────────────────────
    /** What happens after game ends. "REMATCH_PROMPT", "END", "RETURN_POSITIONS" */
    public String postGameAction = "REMATCH_PROMPT";

    /** Seconds the rematch prompt is shown. */
    public int rematchPromptSecs = 10;

    // ── Announcements ─────────────────────────────────────────────────────────
    /** Whether to broadcast a server-wide announcement when a game starts. */
    public boolean serverWideAnnounce = false;

    /** Whether to show chat messages for game events. */
    public boolean chatMessages = true;

    /** Whether to show title screens for win/lose. */
    public boolean titleScreenEvents = true;

    /** Whether to show actionbar messages. */
    public boolean actionbarMessages = true;

    // ── Sounds ────────────────────────────────────────────────────────────────
    public boolean soundsEnabled = true;
    public float soundVolume = 1.0f;

    // ── Disconnect ────────────────────────────────────────────────────────────
    /** "FORFEIT" = disconnected player forfeits, "PAUSE" = game pauses, "CANCEL" = cancel */
    public String disconnectBehavior = "FORFEIT";

    // ── Characters ────────────────────────────────────────────────────────────
    /** Allowed character classes in words. "LETTERS","NUMBERS","SPECIAL","ALL" */
    public String allowedChars = "ALL";

    // ── Permissions ───────────────────────────────────────────────────────────
    /** Minimum OP level required to use /hangman set commands. 0 = any player. */
    public int setCommandOpLevel = 2;

    /** Minimum OP level required to use /hangman start. 0 = any player. */
    public int startCommandOpLevel = 0;

    // ── Leaderboard ───────────────────────────────────────────────────────────
    /** Whether to persist stats. */
    public boolean persistStats = true;

    // ── Lava ─────────────────────────────────────────────────────────────────
    /** Whether to place lava under the hanged player on loss. */
    public boolean placeGLavaOnLoss = true;

    // ── Persistence ──────────────────────────────────────────────────────────
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void load(Path configDir) {
        Path file = configDir.resolve("hangman.json");
        if (Files.exists(file)) {
            try (Reader r = new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8)) {
                INSTANCE = GSON.fromJson(r, HangmanConfig.class);
                if (INSTANCE == null) INSTANCE = new HangmanConfig();
            } catch (Exception e) {
                HangmanMod.LOGGER.error("[Hangman] Failed to load config: {}", e.getMessage());
                INSTANCE = new HangmanConfig();
            }
        } else {
            INSTANCE = new HangmanConfig();
            save(configDir);
        }
    }

    public static void save(Path configDir) {
        try {
            Files.createDirectories(configDir);
            Path file = configDir.resolve("hangman.json");
            try (Writer w = new OutputStreamWriter(Files.newOutputStream(file), StandardCharsets.UTF_8)) {
                GSON.toJson(get(), w);
            }
        } catch (Exception e) {
            HangmanMod.LOGGER.error("[Hangman] Failed to save config: {}", e.getMessage());
        }
    }
}
