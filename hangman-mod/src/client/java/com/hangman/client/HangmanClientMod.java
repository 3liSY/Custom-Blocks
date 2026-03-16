package com.hangman.client;

import com.hangman.HangmanMod;
import com.hangman.client.gui.HangmanMainGui;
import com.hangman.client.gui.OverlaySettingsScreen;
import com.hangman.client.gui.WordEntryScreen;
import com.hangman.client.keybind.HangmanKeybinds;
import com.hangman.client.render.HangmanOverlayRenderer;
import com.hangman.common.config.OverlaySettings;
import com.hangman.common.network.HangmanPackets;
import com.hangman.common.network.HangmanPackets.HangmanPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.nio.file.Paths;
import java.util.List;

import static com.hangman.common.network.HangmanPackets.*;

@Environment(EnvType.CLIENT)
public class HangmanClientMod implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        HangmanMod.LOGGER.info("[Hangman] Client initializing...");

        // Load overlay settings
        try {
            java.nio.file.Path dir = Paths.get(System.getProperty("user.dir"), "config");
            OverlaySettings.load(dir);
        } catch (Exception ignored) {}

        // Register keybinds
        HangmanKeybinds.register();

        // Register S2C packet receiver
        ClientPlayNetworking.registerGlobalReceiver(HangmanPayload.ID, (payload, ctx) -> {
            String type = payload.type();
            PacketByteBuf buf = payload.data();
            ctx.client().execute(() -> handleS2C(type, buf, ctx.client()));
        });

        // HUD overlay
        HudRenderCallback.EVENT.register((drawCtx, tickCounter) -> {
            HangmanOverlayRenderer.render(drawCtx, tickCounter);
        });

        // Keybind tick
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (HangmanKeybinds.TOGGLE_OVERLAY.wasPressed()) {
                ClientGameState.overlayVisible = !ClientGameState.overlayVisible;
            }
            if (HangmanKeybinds.OPEN_GUI.wasPressed()) {
                if (client.currentScreen == null)
                    client.setScreen(new HangmanMainGui());
            }
            if (HangmanKeybinds.OPEN_SETTINGS.wasPressed()) {
                if (client.currentScreen == null)
                    client.setScreen(new OverlaySettingsScreen());
            }
            if (HangmanKeybinds.FORFEIT.wasPressed()) {
                if (ClientGameState.inGame) {
                    ClientPlayNetworking.send(new HangmanPayload(C2S_FORFEIT, newBuf()));
                }
            }
            if (HangmanKeybinds.TOGGLE_CURSOR.wasPressed()) {
                if (ClientGameState.inGame) {
                    ClientGameState.cursorMode = !ClientGameState.cursorMode;
                }
            }

            // Physical keyboard support: if physical keys enabled and inGame,
            // detect letter key presses and treat them as guesses
            if (ClientGameState.inGame && !ClientGameState.isHanged
                    && OverlaySettings.get().physicalKeysEnabled
                    && client.currentScreen == null) {
                checkPhysicalKeyPresses(client);
            }
        });

        // Mouse click handler for overlay keyboard
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (!ClientGameState.inGame || ClientGameState.isHanged) return;
            if (!ClientGameState.overlayVisible) return;
            if (client.currentScreen != null) return;

            // Poll left mouse button via GLFW
            long window = client.getWindow().getHandle();
            boolean pressed = org.lwjgl.glfw.GLFW.glfwGetMouseButton(window,
                org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;

            if (pressed && !lastMousePressed) {
                // Get cursor position
                double[] xPos = new double[1];
                double[] yPos = new double[1];
                org.lwjgl.glfw.GLFW.glfwGetCursorPos(window, xPos, yPos);

                // Scale to GUI coordinates
                double guiScale = client.getWindow().getScaleFactor();
                int guiX = (int)(xPos[0] / guiScale);
                int guiY = (int)(yPos[0] / guiScale);

                handleOverlayClick(client, guiX, guiY);
            }
            lastMousePressed = pressed;
        });

        HangmanMod.LOGGER.info("[Hangman] Client initialized!");
    }

    private static boolean lastMousePressed = false;

    /** Check physical keyboard letter presses for guessing without clicking. */
    private static void checkPhysicalKeyPresses(MinecraftClient client) {
        long window = client.getWindow().getHandle();
        for (char c = 'a'; c <= 'z'; c++) {
            int glfwKey = org.lwjgl.glfw.GLFW.GLFW_KEY_A + (c - 'a');
            boolean pressed = org.lwjgl.glfw.GLFW.glfwGetKey(window, glfwKey)
                == org.lwjgl.glfw.GLFW.GLFW_PRESS;
            if (pressed && !physicalKeyState[c - 'a']) {
                // Only fire once per press
                if (!ClientGameState.guessedLetters.contains(c)) {
                    PacketByteBuf buf = newBuf();
                    buf.writeShort(c);
                    ClientPlayNetworking.send(new HangmanPayload(C2S_GUESS_LETTER, buf));
                }
            }
            physicalKeyState[c - 'a'] = pressed;
        }
        // Space key for blank/space guess
        boolean spacePressed = org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE)
            == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        if (spacePressed && !spaceKeyState) {
            PacketByteBuf buf = newBuf();
            buf.writeShort(' ');
            ClientPlayNetworking.send(new HangmanPayload(C2S_GUESS_LETTER, buf));
        }
        spaceKeyState = spacePressed;
    }

    private static final boolean[] physicalKeyState = new boolean[26];
    private static boolean spaceKeyState = false;

    // ── S2C packet handling ───────────────────────────────────────────────────

    private void handleS2C(String type, PacketByteBuf buf, MinecraftClient client) {
        switch (type) {

            case "s2c_open_gui" -> {
                if (client.currentScreen == null)
                    client.setScreen(new HangmanMainGui());
            }

            case S2C_INVITE_NOTIFY -> {                String inviterName = buf.readString(64);
                // Show clickable invite in chat
                if (client.player != null) {
                    client.player.sendMessage(
                        Text.translatable("hangman.invite.received", inviterName), false);
                }
            }

            case S2C_OPEN_WORD_SCREEN -> {
                ClientGameState.wordScreenOpen = true;
                client.setScreen(new WordEntryScreen());
            }

            case S2C_GAME_START -> {
                ClientGameState.isHanged          = buf.readBoolean();
                ClientGameState.maskedWord        = buf.readString(512);
                ClientGameState.wrongGuesses      = buf.readInt();
                ClientGameState.maxWrongGuesses   = buf.readInt();
                ClientGameState.category          = buf.readString(64);
                ClientGameState.wrongLetters.clear();
                ClientGameState.wrongLetters.addAll(readCharList(buf));
                ClientGameState.guessedLetters.clear();
                ClientGameState.removedLimbs.clear();
                ClientGameState.removedLimbs.addAll(readStringList(buf));
                ClientGameState.hintsEnabled      = buf.readBoolean();
                ClientGameState.maxHints          = buf.readInt();
                ClientGameState.timerTotalSeconds = buf.readInt();
                ClientGameState.timerRemainingSecs = ClientGameState.timerTotalSeconds;
                ClientGameState.timerLastUpdateMs  = System.currentTimeMillis();
                ClientGameState.inGame            = true;
                ClientGameState.overlayVisible    = true;
            }

            case S2C_GAME_UPDATE -> {
                ClientGameState.maskedWord       = buf.readString(512);
                ClientGameState.wrongGuesses     = buf.readInt();
                ClientGameState.maxWrongGuesses  = buf.readInt();
                List<Character> wrong = readCharList(buf);
                ClientGameState.wrongLetters.clear();
                ClientGameState.wrongLetters.addAll(wrong);
                List<Character> guessed = readCharList(buf);
                ClientGameState.guessedLetters.clear();
                ClientGameState.guessedLetters.addAll(guessed);
                ClientGameState.removedLimbs.clear();
                ClientGameState.removedLimbs.addAll(readStringList(buf));
            }

            case S2C_LIMB_REMOVED -> {
                String limb = buf.readString(32);
                ClientGameState.lastRemovedLimb = limb;
                ClientGameState.lastRemovedTime = System.currentTimeMillis();
                // Spawn particles at figure position
                if (client.getWindow() != null) {
                    HangmanOverlayRenderer.spawnLimbParticles(
                        client.getWindow().getScaledWidth(),
                        client.getWindow().getScaledHeight());
                }
            }

            case S2C_TIMER_SYNC -> {
                ClientGameState.timerRemainingSecs = buf.readInt();
                ClientGameState.timerLastUpdateMs  = System.currentTimeMillis();
            }

            case S2C_GAME_OVER -> {
                boolean hangerWon  = buf.readBoolean();
                String secretWord  = buf.readString(256);
                int hangerWins     = buf.readInt();
                int hangedWins     = buf.readInt();
                if (client.player != null) {
                    client.player.sendMessage(Text.literal(
                        hangerWon
                            ? "§6[Hangman] §aGuesser won! The word was: §e" + secretWord
                            : "§6[Hangman] §cHanged player survived! The word was: §e" + secretWord
                    ), false);
                    if (ClientGameState.timerTotalSeconds > 0) {
                        client.player.sendMessage(Text.literal("§7Score — Guesser: " + hangerWins + " | Hanged: " + hangedWins), false);
                    }
                }
                ClientGameState.reset();
            }

            case "s2c_sound" -> {
                String eventType = buf.readString(32);
                playSound(client, eventType);
            }
        }
    }

    // ── sound ─────────────────────────────────────────────────────────────────

    private void playSound(MinecraftClient client, String eventType) {
        if (!com.hangman.common.config.HangmanConfig.get().soundsEnabled) return;
        Identifier soundId = switch (eventType) {
            case "correct" -> Identifier.of(HangmanMod.MOD_ID, "correct");
            case "wrong"   -> Identifier.of(HangmanMod.MOD_ID, "wrong");
            case "win"     -> Identifier.of(HangmanMod.MOD_ID, "win");
            case "lose"    -> Identifier.of(HangmanMod.MOD_ID, "lose");
            case "tick"    -> Identifier.of(HangmanMod.MOD_ID, "tick");
            case "key"     -> Identifier.of(HangmanMod.MOD_ID, "key");
            default        -> null;
        };
        if (soundId == null) return;
        float vol = com.hangman.common.config.HangmanConfig.get().soundVolume;
        client.getSoundManager().play(
            PositionedSoundInstance.master(SoundEvent.of(soundId), vol, 1.0f));
    }

    // ── overlay mouse click handling ──────────────────────────────────────────

    /**
     * Called from a mixin or event when the player left-clicks while in a game.
     * Checks if the click hit a keyboard key and sends the guess to the server.
     */
    public static void handleOverlayClick(MinecraftClient client, double mx, double my) {
        if (!ClientGameState.inGame) return;
        if (ClientGameState.isHanged) return; // hanged player can't click keyboard
        if (!ClientGameState.overlayVisible) return;

        int sw = client.getWindow().getScaledWidth();
        int sh = client.getWindow().getScaledHeight();

        char key = HangmanOverlayRenderer.getKeyAt((int) mx, (int) my, sw, sh);
        if (key == '\0') return;

        // Don't resend already guessed letters (except space)
        if (key != ' ' && ClientGameState.guessedLetters.contains(key)) return;

        // Send guess to server
        PacketByteBuf buf = newBuf();
        buf.writeShort(key);
        ClientPlayNetworking.send(new HangmanPayload(C2S_GUESS_LETTER, buf));

        // Play key sound
        client.getSoundManager().play(
            PositionedSoundInstance.master(
                SoundEvent.of(Identifier.of(HangmanMod.MOD_ID, "key")),
                com.hangman.common.config.HangmanConfig.get().soundVolume, 1.0f));
    }
}
