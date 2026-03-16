package com.hangman.client.gui;

import com.hangman.client.ClientGameState;
import com.hangman.common.config.HangmanConfig;
import com.hangman.common.config.OverlaySettings;
import com.hangman.common.network.HangmanPackets;
import com.hangman.common.network.HangmanPackets.HangmanPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;

import java.nio.file.Files;
import java.nio.file.Paths;

import static com.hangman.common.network.HangmanPackets.*;

/**
 * Main Hangman GUI — opened with G key (or /hangman gui).
 * Has 5 tabs: PLAY  |  GAME SETUP  |  OVERLAY  |  STATS  |  HELP
 */
public class HangmanMainGui extends Screen {

    // ── tabs ──────────────────────────────────────────────────────────────────
    private enum Tab { PLAY, SETUP, OVERLAY, STATS, HELP }
    private Tab activeTab = Tab.PLAY;

    // ── PLAY tab widgets ──────────────────────────────────────────────────────
    private TextFieldWidget invitePlayerField;
    private TextFieldWidget wordInputField;
    private TextFieldWidget categoryInputField;
    private TextFieldWidget guessLetterField;
    private TextFieldWidget areaNameField;

    // ── SETUP tab widgets ─────────────────────────────────────────────────────
    private TextFieldWidget maxWrongField;
    private TextFieldWidget timerField;
    private TextFieldWidget roundsField;
    private TextFieldWidget winnerXpField;
    private TextFieldWidget itemRewardField;

    // ── color pickers (OVERLAY tab) ───────────────────────────────────────────
    private TextFieldWidget colorCorrectField;
    private TextFieldWidget colorWrongField;
    private TextFieldWidget colorBgField;
    private TextFieldWidget colorWordField;

    // ── feedback message ──────────────────────────────────────────────────────
    private String feedbackMsg = "";
    private int    feedbackColor = 0xFF55FF55;
    private long   feedbackUntil = 0;

    public HangmanMainGui() {
        super(Text.literal("Hangman"));
    }

    // ── layout ────────────────────────────────────────────────────────────────
    private static final int PANEL_W  = 340;
    private static final int PANEL_H  = 320;
    private static final int TAB_H    = 22;
    private static final int FIELD_H  = 18;
    private static final int BTN_H    = 20;
    private static final int ROW_GAP  = 24;

    @Override
    protected void init() {
        clearChildren();
        int px = (width  - PANEL_W) / 2;
        int py = (height - PANEL_H) / 2;

        // ── tab buttons ───────────────────────────────────────────────────────
        String[] labels = {"▶ Play", "⚙ Setup", "🎨 Overlay", "📊 Stats", "❓ Help"};
        Tab[]    tabs   = Tab.values();
        int tabW = PANEL_W / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            final Tab t = tabs[i];
            int tx = px + i * tabW;
            addDrawableChild(ButtonWidget.builder(Text.literal(labels[i]), b -> {
                activeTab = t;
                init();
            }).dimensions(tx, py, tabW, TAB_H).build());
        }

        int contentY = py + TAB_H + 8;

        switch (activeTab) {
            case PLAY    -> buildPlayTab   (px, contentY);
            case SETUP   -> buildSetupTab  (px, contentY);
            case OVERLAY -> buildOverlayTab(px, contentY);
            case STATS   -> buildStatsTab  (px, contentY);
            case HELP    -> buildHelpTab   (px, contentY);
        }

        // Close button always at bottom
        addDrawableChild(ButtonWidget.builder(Text.literal("✕ Close"), b -> close())
            .dimensions(px + PANEL_W - 60, py + PANEL_H - 24, 58, 20).build());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PLAY TAB
    // ══════════════════════════════════════════════════════════════════════════
    private void buildPlayTab(int px, int y) {

        // ── section: INVITE ───────────────────────────────────────────────────
        section("── Invite Player ──", px, y); y += 14;

        invitePlayerField = field("Player name...", px, y, PANEL_W - 90);
        addDrawableChild(invitePlayerField);
        addDrawableChild(btn("Invite", px + PANEL_W - 86, y, 84, () -> {
            String name = invitePlayerField.getText().trim();
            if (name.isEmpty()) { feedback("§cEnter a player name!", false); return; }
            sendCmd("/hangman start " + name);
            feedback("§aInvite sent to " + name + "!", true);
        })); y += ROW_GAP;

        addDrawableChild(btn("✔ Accept Invite", px, y, 100, () -> {
            ClientPlayNetworking.send(new HangmanPayload(C2S_ACCEPT_INVITE, newBuf()));
            feedback("§aAccepted!", true);
        }));
        addDrawableChild(btn("✘ Decline Invite", px + 104, y, 104, () -> {
            ClientPlayNetworking.send(new HangmanPayload(C2S_DECLINE_INVITE, newBuf()));
            feedback("§cDeclined.", true);
        }));
        addDrawableChild(btn("⚑ Forfeit Game", px + 212, y, 100, () -> {
            ClientPlayNetworking.send(new HangmanPayload(C2S_FORFEIT, newBuf()));
            feedback("§6You forfeited.", true);
        })); y += ROW_GAP + 4;

        // ── section: WORD ─────────────────────────────────────────────────────
        section("── Enter Secret Word ──", px, y); y += 14;

        wordInputField = field("Secret word (supports spaces)...", px, y, PANEL_W - 90);
        addDrawableChild(wordInputField);
        wordInputField.setMaxLength(128);

        categoryInputField = field("Category (optional)...", px, y + ROW_GAP, PANEL_W - 90);
        if (HangmanConfig.get().categoryEnabled) addDrawableChild(categoryInputField);

        addDrawableChild(btn("Submit", px + PANEL_W - 86, y, 84, () -> {
            String word = wordInputField.getText().trim();
            if (word.isEmpty()) { feedback("§cWord cannot be empty!", false); return; }
            String cat  = categoryInputField != null ? categoryInputField.getText().trim() : "";
            PacketByteBuf buf = newBuf();
            buf.writeString(word, 256);
            buf.writeString(cat,  64);
            ClientPlayNetworking.send(new HangmanPayload(C2S_SUBMIT_WORD, buf));
            feedback("§aWord submitted!", true);
            wordInputField.setText("");
        })); y += HangmanConfig.get().categoryEnabled ? ROW_GAP * 2 : ROW_GAP;
        y += 4;

        // ── section: HINT & GALLOWS ───────────────────────────────────────────
        section("── In-Game Actions ──", px, y); y += 14;

        addDrawableChild(btn("💡 Use Hint", px, y, 100, () -> {
            ClientPlayNetworking.send(new HangmanPayload(C2S_REQUEST_HINT, newBuf()));
            feedback("§eHint requested!", true);
        }));
        addDrawableChild(btn("🪵 Spawn Gallows", px + 104, y, 120, () -> {
            sendCmd("/hangman spawnwood");
            feedback("§aGallows spawned at your feet!", true);
        })); y += ROW_GAP + 4;

        // ── section: AREAS ────────────────────────────────────────────────────
        section("── Game Areas ──", px, y); y += 14;

        areaNameField = field("Area name...", px, y, PANEL_W - 90);
        addDrawableChild(areaNameField);

        addDrawableChild(btn("Save Area", px + PANEL_W - 86, y, 84, () -> {
            String name = areaNameField.getText().trim();
            if (name.isEmpty()) { feedback("§cEnter an area name!", false); return; }
            sendCmd("/hangman setarea " + name);
            feedback("§aArea '" + name + "' saved! Use /hangman sethangedpos and sethangerpos to refine.", true);
        })); y += ROW_GAP;

        addDrawableChild(btn("Set My Pos → Hanged", px, y, 150, () -> {
            String name = areaNameField.getText().trim();
            if (name.isEmpty()) { feedback("§cEnter an area name first!", false); return; }
            sendCmd("/hangman sethangedpos " + name);
            feedback("§aHanged spawn set!", true);
        }));
        addDrawableChild(btn("Set My Pos → Hanger", px + 154, y, 150, () -> {
            String name = areaNameField.getText().trim();
            if (name.isEmpty()) { feedback("§cEnter an area name first!", false); return; }
            sendCmd("/hangman sethangerpos " + name);
            feedback("§aHanger spawn set!", true);
        }));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SETUP TAB
    // ══════════════════════════════════════════════════════════════════════════
    private void buildSetupTab(int px, int y) {
        HangmanConfig cfg = HangmanConfig.get();

        section("── Game Rules ──", px, y); y += 14;

        // Max wrong guesses
        label("Max Wrong Guesses (1-26):", px, y);
        maxWrongField = numField(String.valueOf(cfg.maxWrongGuesses), px + 170, y, 60);
        addDrawableChild(maxWrongField);
        addDrawableChild(btn("Set", px + 234, y, 40, () -> {
            try {
                int v = Integer.parseInt(maxWrongField.getText().trim());
                if (v < 1 || v > 26) throw new NumberFormatException();
                cfg.maxWrongGuesses = v;
                saveAndFeedback("Max wrong guesses = " + v);
            } catch (NumberFormatException e) { feedback("§cEnter 1-26!", false); }
        })); y += ROW_GAP;

        // Timer
        label("Timer Seconds (0=off):", px, y);
        timerField = numField(String.valueOf(cfg.timerSeconds), px + 170, y, 60);
        addDrawableChild(timerField);
        addDrawableChild(btn("Set", px + 234, y, 40, () -> {
            try {
                int v = Integer.parseInt(timerField.getText().trim());
                cfg.timerSeconds = Math.max(0, v);
                saveAndFeedback("Timer = " + cfg.timerSeconds + "s");
            } catch (NumberFormatException e) { feedback("§cNumbers only!", false); }
        })); y += ROW_GAP;

        // Timer action
        label("Timer Timeout Action:", px, y);
        addDrawableChild(btn(cfg.timerAction, px + 170, y, 110, () -> {
            String[] opts = {"WRONG_GUESS", "HANGED_WINS", "CANCEL"};
            int idx = 0;
            for (int i = 0; i < opts.length; i++) if (opts[i].equals(cfg.timerAction)) idx = i;
            cfg.timerAction = opts[(idx + 1) % opts.length];
            saveAndFeedback("Timer action = " + cfg.timerAction);
            init();
        })); y += ROW_GAP;

        // Rounds
        label("Rounds (1-20):", px, y);
        roundsField = numField(String.valueOf(cfg.rounds), px + 170, y, 60);
        addDrawableChild(roundsField);
        addDrawableChild(btn("Set", px + 234, y, 40, () -> {
            try {
                int v = Integer.parseInt(roundsField.getText().trim());
                cfg.rounds = Math.max(1, Math.min(20, v));
                saveAndFeedback("Rounds = " + cfg.rounds);
            } catch (NumberFormatException e) { feedback("§cNumbers only!", false); }
        })); y += ROW_GAP;

        // Word chooser
        label("Word Chooser:", px, y);
        addDrawableChild(btn(cfg.wordChooser, px + 170, y, 110, () -> {
            cfg.wordChooser = "HANGED".equals(cfg.wordChooser) ? "HANGER" : "HANGED";
            saveAndFeedback("Word chooser = " + cfg.wordChooser);
            init();
        })); y += ROW_GAP;

        section("── Movement & Freeze ──", px, y); y += 14;

        // Hanged can move
        label("Hanged Can Move:", px, y);
        addDrawableChild(btn(cfg.hangedCanMove ? "YES" : "NO", px + 170, y, 60, () -> {
            cfg.hangedCanMove = !cfg.hangedCanMove;
            saveAndFeedback("Hanged can move = " + cfg.hangedCanMove);
            init();
        })); y += ROW_GAP;

        section("── Rewards ──", px, y); y += 14;

        // Winner XP
        label("Winner XP:", px, y);
        winnerXpField = numField(String.valueOf(cfg.winnerXp), px + 170, y, 60);
        addDrawableChild(winnerXpField);
        addDrawableChild(btn("Set", px + 234, y, 40, () -> {
            try {
                cfg.winnerXp = Math.max(0, Integer.parseInt(winnerXpField.getText().trim()));
                saveAndFeedback("Winner XP = " + cfg.winnerXp);
            } catch (NumberFormatException e) { feedback("§cNumbers only!", false); }
        })); y += ROW_GAP;

        // Item reward
        label("Item Reward:", px, y);
        itemRewardField = field(cfg.winnerItemReward, px + 170, y, 130);
        itemRewardField.setMaxLength(64);
        addDrawableChild(itemRewardField);
        addDrawableChild(btn("Set", px + 304, y, 34, () -> {
            cfg.winnerItemReward = itemRewardField.getText().trim();
            saveAndFeedback("Item reward = " + cfg.winnerItemReward);
        })); y += ROW_GAP;

        section("── Toggles ──", px, y); y += 14;

        // Row of toggle buttons
        addDrawableChild(toggleBtn("Lava on Loss", cfg.placeGLavaOnLoss, px, y, () -> {
            cfg.placeGLavaOnLoss = !cfg.placeGLavaOnLoss; saveAndFeedback("Lava on loss = " + cfg.placeGLavaOnLoss); init();
        }));
        addDrawableChild(toggleBtn("Sounds", cfg.soundsEnabled, px + 85, y, () -> {
            cfg.soundsEnabled = !cfg.soundsEnabled; saveAndFeedback("Sounds = " + cfg.soundsEnabled); init();
        }));
        addDrawableChild(toggleBtn("Hints", cfg.hintsEnabled, px + 170, y, () -> {
            cfg.hintsEnabled = !cfg.hintsEnabled; saveAndFeedback("Hints = " + cfg.hintsEnabled); init();
        }));
        addDrawableChild(toggleBtn("Categories", cfg.categoryEnabled, px + 255, y, () -> {
            cfg.categoryEnabled = !cfg.categoryEnabled; saveAndFeedback("Categories = " + cfg.categoryEnabled); init();
        })); y += ROW_GAP;

        addDrawableChild(toggleBtn("Multi-Games", cfg.allowMultipleGames, px, y, () -> {
            cfg.allowMultipleGames = !cfg.allowMultipleGames; saveAndFeedback("Multi-games = " + cfg.allowMultipleGames); init();
        }));
        addDrawableChild(toggleBtn("Spectators", cfg.allowSpectators, px + 85, y, () -> {
            cfg.allowSpectators = !cfg.allowSpectators; saveAndFeedback("Spectators = " + cfg.allowSpectators); init();
        }));
        addDrawableChild(toggleBtn("Announce", cfg.serverWideAnnounce, px + 170, y, () -> {
            cfg.serverWideAnnounce = !cfg.serverWideAnnounce; saveAndFeedback("Announce = " + cfg.serverWideAnnounce); init();
        }));
        addDrawableChild(toggleBtn("TP On Start", cfg.teleportOnStart, px + 255, y, () -> {
            cfg.teleportOnStart = !cfg.teleportOnStart; saveAndFeedback("TP on start = " + cfg.teleportOnStart); init();
        }));

        y += ROW_GAP;

        // Limb order
        section("── Limb Removal Order ──", px, y); y += 14;

        String currentOrder = String.join(", ", cfg.limbOrder);
        label(currentOrder.length() > 38 ? currentOrder.substring(0, 38) + "…" : currentOrder, px, y); y += 12;

        String[] presets = {
            "RIGHT_ARM,LEFT_ARM,RIGHT_LEG,LEFT_LEG,HEAD",
            "HEAD,RIGHT_ARM,LEFT_ARM,RIGHT_LEG,LEFT_LEG",
            "RIGHT_LEG,LEFT_LEG,RIGHT_ARM,LEFT_ARM,HEAD"
        };
        String[] presetNames = {"Arms First", "Head First", "Legs First"};
        for (int i = 0; i < presets.length; i++) {
            final String preset = presets[i];
            final String pname  = presetNames[i];
            addDrawableChild(btn(pname, px + i * 100, y, 96, () -> {
                cfg.limbOrder = java.util.Arrays.asList(preset.split(","));
                saveAndFeedback("Limb order = " + pname);
            }));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // OVERLAY TAB
    // ══════════════════════════════════════════════════════════════════════════
    private void buildOverlayTab(int px, int y) {
        OverlaySettings s = OverlaySettings.get();

        section("── Keyboard ──", px, y); y += 14;

        addSliderRow("X Position", s.keyboardX, 0, 1, px, y,
            v -> { s.keyboardX = v; saveOverlay(); }); y += ROW_GAP;
        addSliderRow("Y Position", s.keyboardY, 0, 1, px, y,
            v -> { s.keyboardY = v; saveOverlay(); }); y += ROW_GAP;
        addSliderRow("Scale", s.keyboardScale, 0.3f, 2.0f, px, y,
            v -> { s.keyboardScale = v; saveOverlay(); }); y += ROW_GAP;
        addSliderRow("Opacity", s.keyboardOpacity, 0, 1, px, y,
            v -> { s.keyboardOpacity = v; saveOverlay(); }); y += ROW_GAP;

        addDrawableChild(btn("Layout: " + s.keyboardLayout, px, y, 130, () -> {
            s.keyboardLayout = "QWERTY".equals(s.keyboardLayout) ? "ALPHA" : "QWERTY";
            saveOverlay(); init();
        }));
        addDrawableChild(toggleBtn("Cursor Mode", s.cursorModeEnabled, px + 134, y, () -> {
            s.cursorModeEnabled = !s.cursorModeEnabled; saveOverlay(); init();
        }));
        addDrawableChild(toggleBtn("Physical Keys", s.physicalKeysEnabled, px + 224, y, () -> {
            s.physicalKeysEnabled = !s.physicalKeysEnabled; saveOverlay(); init();
        })); y += ROW_GAP;

        section("── Colors ──", px, y); y += 14;

        colorCorrectField = colorField(s.colorCorrect, px, y, 100);
        addDrawableChild(colorCorrectField);
        addDrawableChild(btn("✔ Correct", px + 104, y, 80, () -> {
            s.colorCorrect = colorCorrectField.getText().trim(); saveOverlay();
            feedback("§aCorrect key color set!", true);
        })); y += ROW_GAP;

        colorWrongField = colorField(s.colorWrong, px, y, 100);
        addDrawableChild(colorWrongField);
        addDrawableChild(btn("✘ Wrong", px + 104, y, 80, () -> {
            s.colorWrong = colorWrongField.getText().trim(); saveOverlay();
            feedback("§cWrong key color set!", true);
        })); y += ROW_GAP;

        colorBgField = colorField(s.colorKeyboardBg, px, y, 100);
        addDrawableChild(colorBgField);
        addDrawableChild(btn("⬛ BG Color", px + 104, y, 80, () -> {
            s.colorKeyboardBg = colorBgField.getText().trim(); saveOverlay();
            feedback("§7BG color set!", true);
        })); y += ROW_GAP;

        colorWordField = colorField(s.colorWordRevealed, px, y, 100);
        addDrawableChild(colorWordField);
        addDrawableChild(btn("🔤 Word Color", px + 104, y, 80, () -> {
            s.colorWordRevealed = colorWordField.getText().trim(); saveOverlay();
            feedback("§fWord color set!", true);
        })); y += ROW_GAP;

        section("── Figure & Word ──", px, y); y += 14;

        addSliderRow("Figure X", s.figureX, 0, 1, px, y,
            v -> { s.figureX = v; saveOverlay(); }); y += ROW_GAP;
        addSliderRow("Figure Y", s.figureY, 0, 1, px, y,
            v -> { s.figureY = v; saveOverlay(); }); y += ROW_GAP;
        addSliderRow("Figure Scale", s.figureScale, 0.3f, 2.0f, px, y,
            v -> { s.figureScale = v; saveOverlay(); }); y += ROW_GAP;

        addDrawableChild(btn("Style: " + s.figureStyle, px, y, 130, () -> {
            String[] styles = {"STICK", "DETAILED", "PIXEL"};
            int idx = 0;
            for (int i = 0; i < styles.length; i++) if (styles[i].equals(s.figureStyle)) idx = i;
            s.figureStyle = styles[(idx + 1) % styles.length];
            saveOverlay(); init();
        }));

        section("── Visibility Per Role ──", px, y + ROW_GAP); y += ROW_GAP * 2;

        String[] visLabels = {"Keyboard", "Figure", "Word", "Timer"};
        String[] visVals   = {s.keyboardVisibleFor, s.figureVisibleFor,
                               s.wordVisibleFor, s.timerVisibleFor};
        for (int i = 0; i < visLabels.length; i++) {
            final int idx2 = i;
            addDrawableChild(btn(visLabels[i] + ": " + visVals[i], px + i * 84, y, 82, () -> {
                String[] opts = {"BOTH", "HANGER_ONLY", "HANGED_ONLY"};
                String cur = switch(idx2) {
                    case 0 -> s.keyboardVisibleFor;
                    case 1 -> s.figureVisibleFor;
                    case 2 -> s.wordVisibleFor;
                    default -> s.timerVisibleFor;
                };
                int ni = 0;
                for (int j = 0; j < opts.length; j++) if (opts[j].equals(cur)) ni = j;
                String next = opts[(ni + 1) % opts.length];
                switch (idx2) {
                    case 0 -> s.keyboardVisibleFor = next;
                    case 1 -> s.figureVisibleFor   = next;
                    case 2 -> s.wordVisibleFor      = next;
                    default -> s.timerVisibleFor   = next;
                }
                saveOverlay(); init();
            }));
        }

        y += ROW_GAP;
        addDrawableChild(btn("💾 Save All Overlay Settings", px, y, PANEL_W - 4, () -> {
            saveOverlay();
            feedback("§a✔ Overlay settings saved!", true);
        }));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // STATS TAB
    // ══════════════════════════════════════════════════════════════════════════
    private void buildStatsTab(int px, int y) {
        section("── Your Stats ──", px, y); y += 14;

        addDrawableChild(btn("📊 View My Stats", px, y, 150, () -> {
            sendCmd("/hangman stats");
            close();
        }));
        addDrawableChild(btn("🏆 Leaderboard", px + 154, y, 150, () -> {
            sendCmd("/hangman leaderboard");
            close();
        })); y += ROW_GAP + 4;

        section("── View Other Player Stats ──", px, y); y += 14;

        TextFieldWidget statsPlayerField = field("Player name...", px, y, PANEL_W - 90);
        addDrawableChild(statsPlayerField);
        addDrawableChild(btn("View", px + PANEL_W - 86, y, 84, () -> {
            String name = statsPlayerField.getText().trim();
            if (name.isEmpty()) { feedback("§cEnter a name!", false); return; }
            sendCmd("/hangman stats " + name);
            close();
        })); y += ROW_GAP + 4;

        section("── Spectate ──", px, y); y += 14;

        TextFieldWidget spectateField = field("Player to spectate...", px, y, PANEL_W - 90);
        addDrawableChild(spectateField);
        addDrawableChild(btn("Watch", px + PANEL_W - 86, y, 84, () -> {
            String name = spectateField.getText().trim();
            if (name.isEmpty()) { feedback("§cEnter a name!", false); return; }
            sendCmd("/hangman spectate " + name);
            feedback("§aNow spectating " + name + "!", true);
            close();
        })); y += ROW_GAP + 4;

        if (ClientGameState.inGame) {
            section("── Current Game ──", px, y); y += 14;
            label("§aYou are currently in a game!", px, y); y += 14;
            label("Role: " + (ClientGameState.isHanged ? "§cHANGED" : "§aHANGER"), px, y); y += 12;
            label("Wrong: §c" + ClientGameState.wrongGuesses + "§r/" + ClientGameState.maxWrongGuesses, px, y); y += 12;
            label("Word: §e" + ClientGameState.maskedWord, px, y); y += 12;
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HELP TAB
    // ══════════════════════════════════════════════════════════════════════════
    private void buildHelpTab(int px, int y) {
        String[] lines = {
            "§6=== How to Play Hangman ===",
            "",
            "§e1. §fInvite a player with §b/hangman start <name>",
            "   §7or use the §bPlay tab§7 above.",
            "",
            "§e2. §fThe §cHanged player§f enters the secret word.",
            "   §7They submit it privately — no one else sees it.",
            "",
            "§e3. §fThe §aHanger§f sees a keyboard overlay on screen.",
            "   §7Click letters or type them to guess.",
            "   §7Use the §bSPACE key§7 for multi-word phrases!",
            "",
            "§e4. §fWrong guesses remove limbs from the stick figure.",
            "   §7Too many wrong guesses → §cLava§7 drops on the hanged player!",
            "",
            "§6=== Key Bindings ===",
            "§eH §7= Toggle overlay on/off",
            "§eG §7= Open this GUI",
            "§eJ §7= Overlay position/color settings",
            "§eAlt §7= Toggle cursor mode for clicking",
            "",
            "§6=== Tips ===",
            "§7• Set a game area: §b/hangman setarea default",
            "§7• Spawn gallows at your feet: §bPlay tab → Spawn Gallows",
            "§7• Multi-word secrets? Use the §bSpace§7 key on the keyboard!",
            "§7• Customize limb order, timer, rounds in the §bSetup§7 tab.",
        };

        int lineY = y;
        for (String line : lines) {
            if (lineY > y + PANEL_H - 50) break; // clip to panel
            if (client != null)
                addDrawableChild(ButtonWidget.builder(Text.literal(line), b -> {})
                    .dimensions(px, lineY, PANEL_W - 4, 10).build());
            lineY += 11;
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // RENDERING
    // ══════════════════════════════════════════════════════════════════════════
    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx, mx, my, delta);

        int px = (width  - PANEL_W) / 2;
        int py = (height - PANEL_H) / 2;

        // Panel background
        ctx.fill(px - 4, py - 4, px + PANEL_W + 4, py + PANEL_H + 4, 0xCC000000);
        ctx.fill(px - 2, py - 2, px + PANEL_W + 2, py + PANEL_H + 2, 0xFF1A1A2E);

        // Title bar
        ctx.fill(px - 2, py - 2, px + PANEL_W + 2, py + 16, 0xFF16213E);
        ctx.drawCenteredTextWithShadow(textRenderer, "§6⚔ §lHangman§r §6⚔", width / 2, py + 4, 0xFFFFFF);

        // Active tab highlight
        int tabW = PANEL_W / Tab.values().length;
        int tabIdx = activeTab.ordinal();
        ctx.fill(px + tabIdx * tabW, py + 20, px + (tabIdx + 1) * tabW, py + 20 + TAB_H, 0x44FFAA00);

        // Feedback message
        if (feedbackUntil > System.currentTimeMillis() && !feedbackMsg.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, feedbackMsg,
                width / 2, py + PANEL_H - 36, feedbackColor);
        }

        super.render(ctx, mx, my, delta);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // WIDGET HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    private ButtonWidget btn(String label, int x, int y, int w, Runnable action) {
        return ButtonWidget.builder(Text.literal(label), b -> action.run())
            .dimensions(x, y, w, BTN_H).build();
    }

    private ButtonWidget toggleBtn(String label, boolean on, int x, int y, Runnable action) {
        String prefix = on ? "§a✔ " : "§c✘ ";
        return ButtonWidget.builder(Text.literal(prefix + label), b -> action.run())
            .dimensions(x, y, 83, BTN_H).build();
    }

    private TextFieldWidget field(String placeholder, int x, int y, int w) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, w, FIELD_H, Text.literal(placeholder));
        f.setMaxLength(128);
        f.setPlaceholder(Text.literal("§7" + placeholder));
        return f;
    }

    private TextFieldWidget numField(String value, int x, int y, int w) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, w, FIELD_H, Text.literal(value));
        f.setMaxLength(8);
        f.setText(value);
        return f;
    }

    private TextFieldWidget colorField(String hex, int x, int y, int w) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, w, FIELD_H, Text.literal(hex));
        f.setMaxLength(10);
        f.setText(hex);
        return f;
    }

    private void addSliderRow(String label, float current, float min, float max, int px, int y,
                               java.util.function.Consumer<Float> setter) {
        // Simple -/+ buttons since proper slider needs more setup
        addDrawableChild(ButtonWidget.builder(Text.literal("◀"), b -> {
            setter.accept(Math.max(min, current - 0.05f)); init();
        }).dimensions(px, y, 18, BTN_H).build());

        String val = String.format("%-20s %.2f", label, current);
        addDrawableChild(ButtonWidget.builder(Text.literal(val), b -> {})
            .dimensions(px + 20, y, 260, BTN_H).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("▶"), b -> {
            setter.accept(Math.min(max, current + 0.05f)); init();
        }).dimensions(px + 282, y, 18, BTN_H).build());
    }

    private void section(String text, int px, int y) {
        addDrawableChild(ButtonWidget.builder(Text.literal("§7" + text), b -> {})
            .dimensions(px, y, PANEL_W - 4, 12).build());
    }

    private void label(String text, int px, int y) {
        addDrawableChild(ButtonWidget.builder(Text.literal(text), b -> {})
            .dimensions(px, y, PANEL_W - 4, 11).build());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // UTILITIES
    // ══════════════════════════════════════════════════════════════════════════

    private void sendCmd(String cmd) {
        if (client != null && client.player != null) {
            client.player.networkHandler.sendChatCommand(cmd.startsWith("/") ? cmd.substring(1) : cmd);
        }
    }

    private void feedback(String msg, boolean good) {
        feedbackMsg   = msg;
        feedbackColor = good ? 0xFF55FF55 : 0xFFFF5555;
        feedbackUntil = System.currentTimeMillis() + 3000;
    }

    private void saveAndFeedback(String what) {
        try {
            // Use CONFIG_DIR if available (server-side), otherwise fall back to working dir
            java.nio.file.Path dir = com.hangman.HangmanMod.CONFIG_DIR != null
                ? com.hangman.HangmanMod.CONFIG_DIR
                : Paths.get(System.getProperty("user.dir"), "config", "hangman");
            java.nio.file.Files.createDirectories(dir);
            HangmanConfig.save(dir);
            feedback("§a✔ Saved: " + what, true);
        } catch (Exception e) {
            feedback("§c✘ Save failed: " + e.getMessage(), false);
        }
    }

    private void saveOverlay() {
        try {
            java.nio.file.Path dir = Paths.get(System.getProperty("user.dir"), "config");
            OverlaySettings.save(dir);
        } catch (Exception ignored) {}
    }

    @Override public boolean shouldPause()       { return false; }
    @Override public boolean shouldCloseOnEsc()  { return true; }
}
