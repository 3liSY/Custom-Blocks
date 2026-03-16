package com.hangman.client;

import java.util.*;

/**
 * Holds all client-side Hangman game state needed to render overlays.
 * Updated by incoming S2C packets.
 */
public final class ClientGameState {

    private ClientGameState() {}

    // ── in-game flags ─────────────────────────────────────────────────────────
    public static boolean inGame      = false;
    public static boolean isHanged    = false; // false = hanger (the one clicking letters)

    // ── word state ────────────────────────────────────────────────────────────
    public static String maskedWord   = "";
    public static String category     = "";
    public static int wrongGuesses    = 0;
    public static int maxWrongGuesses = 6;

    // ── letters ───────────────────────────────────────────────────────────────
    public static final Set<Character> guessedLetters = new LinkedHashSet<>();
    public static final List<Character> wrongLetters  = new ArrayList<>();

    // ── limbs ─────────────────────────────────────────────────────────────────
    public static final List<String> removedLimbs     = new ArrayList<>();
    /** The most recently removed limb — triggers particle animation. */
    public static String lastRemovedLimb = null;
    public static long   lastRemovedTime = 0;

    // ── hints ─────────────────────────────────────────────────────────────────
    public static boolean hintsEnabled = false;
    public static int maxHints         = 0;
    public static int hintsUsed        = 0;

    // ── timer ─────────────────────────────────────────────────────────────────
    public static int timerTotalSeconds    = 0;
    public static int timerRemainingSecs   = 0;
    public static long timerLastUpdateMs   = 0;

    // ── overlay toggles ───────────────────────────────────────────────────────
    public static boolean overlayVisible  = true;
    public static boolean cursorMode      = false; // true = cursor shown for clicking

    // ── word screen ───────────────────────────────────────────────────────────
    public static boolean wordScreenOpen  = false;

    // ── dragging state ────────────────────────────────────────────────────────
    public static boolean draggingKeyboard = false;
    public static boolean draggingWord     = false;
    public static boolean draggingFigure   = false;
    public static float   dragOffsetX, dragOffsetY;

    // ── particle animation ────────────────────────────────────────────────────
    /** Stores active limb-removal particle bursts for rendering */
    public static final List<LimbParticle> limbParticles = new ArrayList<>();

    public static void reset() {
        inGame = false; isHanged = false;
        maskedWord = ""; category = ""; wrongGuesses = 0; maxWrongGuesses = 6;
        guessedLetters.clear(); wrongLetters.clear(); removedLimbs.clear();
        lastRemovedLimb = null; lastRemovedTime = 0;
        hintsEnabled = false; maxHints = 0; hintsUsed = 0;
        timerTotalSeconds = 0; timerRemainingSecs = 0; timerLastUpdateMs = 0;
        overlayVisible = true; cursorMode = false; wordScreenOpen = false;
        limbParticles.clear();
    }

    /** A single fake 2D particle dot from a limb removal. */
    public static class LimbParticle {
        public float x, y;       // screen position (pixels)
        public float vx, vy;     // velocity
        public float life;       // 0..1 remaining
        public int color;        // ARGB

        public LimbParticle(float x, float y, float vx, float vy, int color) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy;
            this.life = 1.0f; this.color = color;
        }
    }
}
