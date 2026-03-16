package com.hangman.common.config;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/**
 * Per-player CLIENT-SIDE overlay customization.
 * Saved to .minecraft/config/hangman_overlay.json
 */
public class OverlaySettings {

    private static OverlaySettings INSTANCE;
    public static OverlaySettings get() {
        if (INSTANCE == null) INSTANCE = new OverlaySettings();
        return INSTANCE;
    }

    // ── Keyboard overlay ─────────────────────────────────────────────────────
    public float keyboardX = 0.5f;     // 0-1 normalized screen X
    public float keyboardY = 0.85f;    // 0-1 normalized screen Y
    public float keyboardScale = 1.0f; // 0.5 - 2.0
    public float keyboardOpacity = 0.85f;

    /** "QWERTY" or "ALPHA" */
    public String keyboardLayout = "QWERTY";

    /** Color for unguessed keys (ARGB hex string) */
    public String colorUnguessed = "#FF888888";
    /** Color for correct keys */
    public String colorCorrect = "#FF55FF55";
    /** Color for wrong keys */
    public String colorWrong = "#FFFF5555";
    /** Color for key text */
    public String colorKeyText = "#FFFFFFFF";
    /** Color for keyboard background panel */
    public String colorKeyboardBg = "#CC000000";

    public boolean keyboardVisible = true;
    public boolean physicalKeysEnabled = true;  // can also use physical keyboard
    public boolean cursorModeEnabled = true;     // cursor appears when clicking

    // ── Masked word display ───────────────────────────────────────────────────
    public float wordX = 0.5f;
    public float wordY = 0.15f;
    public float wordScale = 1.5f;
    public float wordOpacity = 1.0f;
    public String colorWordBlank = "#FFAAAAAA";
    public String colorWordRevealed = "#FFFFFFFF";
    public String colorWordBg = "#AA000000";
    public boolean wordBgVisible = true;

    // ── Wrong letters display ─────────────────────────────────────────────────
    public float wrongLettersX = 0.02f;
    public float wrongLettersY = 0.5f;
    public boolean showWrongLettersList = true;
    public boolean showWrongOnKeyboard = true;  // cross out on keyboard too
    public String colorWrongList = "#FFFF4444";

    // ── Stick figure / hanged figure ──────────────────────────────────────────
    public float figureX = 0.85f;
    public float figureY = 0.3f;
    public float figureScale = 1.0f;
    public float figureOpacity = 1.0f;
    public String colorFigureLines = "#FFFFFFFF";
    public String colorFigureBg = "#AA000000";
    public boolean figureBgVisible = true;

    /** "STICK" or "DETAILED" or "PIXEL" */
    public String figureStyle = "DETAILED";

    // ── Timer display ─────────────────────────────────────────────────────────
    public float timerX = 0.5f;
    public float timerY = 0.08f;
    public boolean showTimerNumber = true;
    public boolean showTimerBar = true;
    public String colorTimerNormal = "#FF55FFFF";
    public String colorTimerWarning = "#FFFF5500";
    public String colorTimerCritical = "#FFFF0000";

    // ── General ───────────────────────────────────────────────────────────────
    public boolean overlayEnabled = true;

    /** Role-specific visibility: "BOTH", "HANGER_ONLY", "HANGED_ONLY" */
    public String keyboardVisibleFor = "HANGER_ONLY";
    public String figureVisibleFor = "BOTH";
    public String wordVisibleFor = "BOTH";
    public String wrongLettersVisibleFor = "BOTH";
    public String timerVisibleFor = "BOTH";

    // ── Persistence ──────────────────────────────────────────────────────────
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void load(Path configDir) {
        Path file = configDir.resolve("hangman_overlay.json");
        if (Files.exists(file)) {
            try (Reader r = new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8)) {
                INSTANCE = GSON.fromJson(r, OverlaySettings.class);
                if (INSTANCE == null) INSTANCE = new OverlaySettings();
            } catch (Exception e) {
                INSTANCE = new OverlaySettings();
            }
        } else {
            INSTANCE = new OverlaySettings();
            save(configDir);
        }
    }

    public static void save(Path configDir) {
        try {
            Files.createDirectories(configDir);
            Path file = configDir.resolve("hangman_overlay.json");
            try (Writer w = new OutputStreamWriter(Files.newOutputStream(file), StandardCharsets.UTF_8)) {
                GSON.toJson(get(), w);
            }
        } catch (Exception ignored) {}
    }

    /** Parse "#AARRGGBB" or "#RRGGBB" to int ARGB */
    public static int parseColor(String hex) {
        try {
            String s = hex.startsWith("#") ? hex.substring(1) : hex;
            if (s.length() == 6) s = "FF" + s;
            return (int) Long.parseLong(s, 16);
        } catch (NumberFormatException e) {
            return 0xFFFFFFFF;
        }
    }
}
