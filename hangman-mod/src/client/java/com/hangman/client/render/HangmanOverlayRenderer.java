package com.hangman.client.render;

import com.hangman.client.ClientGameState;
import com.hangman.client.ClientGameState.LimbParticle;
import com.hangman.common.config.OverlaySettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.*;

/**
 * Draws all Hangman HUD overlays:
 * - Keyboard (hanger sees it)
 * - Masked word
 * - Stick / detailed figure (both players)
 * - Wrong letters list
 * - Timer bar + number
 * - Limb removal particles
 */
public final class HangmanOverlayRenderer {

    private HangmanOverlayRenderer() {}

    // ── QWERTY rows ───────────────────────────────────────────────────────────
    private static final String[] QWERTY = {"QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM"};
    private static final String[] ALPHA  = {"ABCDEFGHIJ", "KLMNOPQRST", "UVWXYZ"};

    public static void render(DrawContext ctx, RenderTickCounter ticker) {
        if (!ClientGameState.inGame) return;
        if (!ClientGameState.overlayVisible) return;

        OverlaySettings s = OverlaySettings.get();
        MinecraftClient mc = MinecraftClient.getInstance();
        int sw = ctx.getScaledWindowWidth();
        int sh = ctx.getScaledWindowHeight();

        // ── Masked word ─────────────────────────────────────────────────────
        if (shouldShow(s.wordVisibleFor)) {
            renderMaskedWord(ctx, mc, sw, sh, s);
        }

        // ── Keyboard (hanger only by default) ──────────────────────────────
        if (shouldShow(s.keyboardVisibleFor)) {
            renderKeyboard(ctx, mc, sw, sh, s);
        }

        // ── Wrong letters list ──────────────────────────────────────────────
        if (shouldShow(s.wrongLettersVisibleFor) && s.showWrongLettersList) {
            renderWrongLettersList(ctx, mc, sw, sh, s);
        }

        // ── Stick figure ────────────────────────────────────────────────────
        if (shouldShow(s.figureVisibleFor)) {
            renderFigure(ctx, mc, sw, sh, s);
        }

        // ── Timer ────────────────────────────────────────────────────────────
        if (shouldShow(s.timerVisibleFor) && ClientGameState.timerTotalSeconds > 0) {
            renderTimer(ctx, mc, sw, sh, s);
        }

        // ── Limb particles ───────────────────────────────────────────────────
        renderParticles(ctx, ticker.getTickDelta(true));
    }

    // ── masked word ───────────────────────────────────────────────────────────

    private static void renderMaskedWord(DrawContext ctx, MinecraftClient mc, int sw, int sh, OverlaySettings s) {
        String word = ClientGameState.maskedWord;
        if (word.isEmpty()) return;

        int wx = (int)(s.wordX * sw);
        int wy = (int)(s.wordY * sh);
        float scale = s.wordScale;

        // Build colored text – revealed letters brighter
        int bgColor  = applyOpacity(OverlaySettings.parseColor(s.colorWordBg), s.wordOpacity);
        int revealed = OverlaySettings.parseColor(s.colorWordRevealed);
        int blank    = OverlaySettings.parseColor(s.colorWordBlank);

        // Draw background
        if (s.wordBgVisible) {
            int textW = (int)(mc.textRenderer.getWidth(word) * scale) + 12;
            int textH = (int)(mc.textRenderer.fontHeight * scale) + 8;
            ctx.fill(wx - textW/2 - 2, wy - 4, wx + textW/2 + 2, wy + textH, bgColor);
        }

        // Draw letter by letter with colors
        ctx.getMatrices().push();
        ctx.getMatrices().translate(wx, wy, 0);
        ctx.getMatrices().scale(scale, scale, 1);

        int charW = (int)(mc.textRenderer.getWidth("W") * scale) + 2;
        String[] letters = word.split(" ");
        int xOff = -(letters.length * charW) / 2;
        for (String letter : letters) {
            int color = letter.equals("_") ? blank : revealed;
            ctx.drawText(mc.textRenderer, letter, xOff, 0, color, true);
            xOff += charW;
        }
        ctx.getMatrices().pop();

        // Category label
        if (!ClientGameState.category.isEmpty()) {
            String cat = "Category: " + ClientGameState.category;
            int catX = wx - mc.textRenderer.getWidth(cat) / 2;
            ctx.drawText(mc.textRenderer, cat, catX, wy - 12, 0xFFAAAAAA, true);
        }
    }

    // ── keyboard ──────────────────────────────────────────────────────────────

    private static void renderKeyboard(DrawContext ctx, MinecraftClient mc, int sw, int sh, OverlaySettings s) {
        float kx = s.keyboardX * sw;
        float ky = s.keyboardY * sh;
        float scale = s.keyboardScale;

        int keyW = (int)(28 * scale);
        int keyH = (int)(18 * scale);
        int gap  = (int)(3  * scale);

        String[] rows = "QWERTY".equals(s.keyboardLayout) ? QWERTY : ALPHA;

        // Measure total width for centering
        int maxRowW = 0;
        for (String row : rows) {
            // Add SPACE key equivalent length
            int rw = (row.length() + 1) * (keyW + gap);
            if (rw > maxRowW) maxRowW = rw;
        }

        int totalH = rows.length * (keyH + gap) + keyH; // +1 for space row

        int bgColor = applyOpacity(OverlaySettings.parseColor(s.colorKeyboardBg), s.keyboardOpacity);
        ctx.fill((int)(kx - maxRowW/2 - 6), (int)(ky - 6),
                 (int)(kx + maxRowW/2 + 6), (int)(ky + totalH + 6), bgColor);

        // Draw rows
        int rowY = (int) ky;
        for (String row : rows) {
            int startX = (int)(kx - (row.length() * (keyW + gap)) / 2);
            for (int i = 0; i < row.length(); i++) {
                char c = row.charAt(i);
                int bx = startX + i * (keyW + gap);
                drawKey(ctx, mc, c, bx, rowY, keyW, keyH, scale, s);
            }
            rowY += keyH + gap;
        }

        // SPACE key (blank key for multi-word)
        int spaceW = (int)(80 * scale);
        int spaceX = (int)(kx - spaceW / 2);
        drawSpaceKey(ctx, mc, spaceX, rowY, spaceW, keyH, scale, s);
    }

    private static void drawKey(DrawContext ctx, MinecraftClient mc, char c,
                                  int x, int y, int w, int h, float scale, OverlaySettings s) {
        char lc = Character.toLowerCase(c);
        boolean guessed = ClientGameState.guessedLetters.contains(lc);
        boolean wrong   = ClientGameState.wrongLetters.contains(lc);
        boolean correct = guessed && !wrong;

        int bgColor;
        if (correct)      bgColor = OverlaySettings.parseColor(s.colorCorrect);
        else if (wrong)   bgColor = OverlaySettings.parseColor(s.colorWrong);
        else              bgColor = OverlaySettings.parseColor(s.colorUnguessed);

        // Key background
        ctx.fill(x, y, x + w, y + h, bgColor);
        // Key border
        ctx.fill(x, y, x + w, y + 1, 0x44FFFFFF);
        ctx.fill(x, y + h - 1, x + w, y + h, 0x44000000);

        // Letter centered in key
        int textColor = OverlaySettings.parseColor(s.colorKeyText);
        if (wrong) {
            // Strike-through: draw X
            ctx.fill(x + 4, y + h/2, x + w - 4, y + h/2 + 1, 0x88FF0000);
        }
        String letter = String.valueOf(c);
        int tx = x + (w - mc.textRenderer.getWidth(letter)) / 2;
        int ty = y + (h - mc.textRenderer.fontHeight) / 2;
        ctx.drawText(mc.textRenderer, letter, tx, ty, textColor, true);
    }

    private static void drawSpaceKey(DrawContext ctx, MinecraftClient mc,
                                      int x, int y, int w, int h, float scale, OverlaySettings s) {
        int bgColor = OverlaySettings.parseColor(s.colorUnguessed);
        ctx.fill(x, y, x + w, y + h, bgColor);
        ctx.fill(x, y, x + w, y + 1, 0x44FFFFFF);
        ctx.fill(x, y + h - 1, x + w, y + h, 0x44000000);

        String label = "SPACE (blank)";
        int textColor = OverlaySettings.parseColor(s.colorKeyText);
        int tx = x + (w - mc.textRenderer.getWidth(label)) / 2;
        int ty = y + (h - mc.textRenderer.fontHeight) / 2;
        ctx.drawText(mc.textRenderer, label, tx, ty, textColor, true);
    }

    // ── wrong letters list ────────────────────────────────────────────────────

    private static void renderWrongLettersList(DrawContext ctx, MinecraftClient mc, int sw, int sh, OverlaySettings s) {
        if (ClientGameState.wrongLetters.isEmpty()) return;

        int wx = (int)(s.wrongLettersX * sw);
        int wy = (int)(s.wrongLettersY * sh);
        int listColor = OverlaySettings.parseColor(s.colorWrongList);

        ctx.drawText(mc.textRenderer, "§cWrong:", wx, wy, listColor, true);
        StringBuilder sb = new StringBuilder();
        for (char c : ClientGameState.wrongLetters) {
            sb.append(Character.toUpperCase(c)).append(' ');
        }
        ctx.drawText(mc.textRenderer, sb.toString().trim(), wx, wy + 12, listColor, true);
        ctx.drawText(mc.textRenderer, ClientGameState.wrongGuesses + "/" + ClientGameState.maxWrongGuesses, wx, wy + 24, 0xFFAAAAAA, true);
    }

    // ── stick figure ──────────────────────────────────────────────────────────

    private static void renderFigure(DrawContext ctx, MinecraftClient mc, int sw, int sh, OverlaySettings s) {
        int cx = (int)(s.figureX * sw);
        int cy = (int)(s.figureY * sh);
        float scale = s.figureScale;
        int alpha = (int)(s.figureOpacity * 255);

        int lineColor  = applyAlpha(OverlaySettings.parseColor(s.colorFigureLines), alpha);
        int bgColor    = applyOpacity(OverlaySettings.parseColor(s.colorFigureBg), s.figureOpacity);

        // Background box
        if (s.figureBgVisible) {
            ctx.fill(cx - (int)(45*scale), cy - (int)(10*scale),
                     cx + (int)(45*scale), cy + (int)(90*scale), bgColor);
        }

        List<String> removed = ClientGameState.removedLimbs;

        // Gallows frame (always shown)
        int gx = cx - (int)(30 * scale);
        int gy = cy + (int)(5 * scale);
        // Vertical post
        ctx.fill(gx, gy, gx + (int)(4*scale), gy + (int)(80*scale), lineColor);
        // Horizontal beam
        ctx.fill(gx, gy, gx + (int)(50*scale), gy + (int)(4*scale), lineColor);
        // Rope
        int ropeX = gx + (int)(40*scale);
        ctx.fill(ropeX, gy + (int)(4*scale), ropeX + (int)(3*scale), gy + (int)(18*scale), lineColor);

        // Figure origin
        int fx = ropeX + (int)(1*scale);
        int headTop = gy + (int)(18*scale);

        // HEAD
        if (!removed.contains("HEAD")) {
            if ("DETAILED".equals(s.figureStyle) || "PIXEL".equals(s.figureStyle)) {
                drawCircle(ctx, fx, headTop + (int)(8*scale), (int)(8*scale), lineColor);
            } else {
                ctx.fill(fx - (int)(8*scale), headTop, fx + (int)(8*scale), headTop + (int)(16*scale), lineColor);
            }
        }

        int bodyTop    = headTop + (int)(16*scale);
        int bodyBottom = bodyTop + (int)(24*scale);
        int bodyMid    = (bodyTop + bodyBottom) / 2;

        // BODY (torso) — always shown unless removed
        if (!removed.contains("TORSO")) {
            ctx.fill(fx - (int)(2*scale), bodyTop, fx + (int)(2*scale), bodyBottom, lineColor);
        }

        // RIGHT_ARM
        if (!removed.contains("RIGHT_ARM")) {
            ctx.fill(fx, bodyMid - (int)(2*scale), fx + (int)(20*scale), bodyMid + (int)(2*scale), lineColor);
            ctx.fill(fx + (int)(18*scale), bodyMid, fx + (int)(22*scale), bodyMid + (int)(16*scale), lineColor);
        }

        // LEFT_ARM
        if (!removed.contains("LEFT_ARM")) {
            ctx.fill(fx - (int)(20*scale), bodyMid - (int)(2*scale), fx, bodyMid + (int)(2*scale), lineColor);
            ctx.fill(fx - (int)(22*scale), bodyMid, fx - (int)(18*scale), bodyMid + (int)(16*scale), lineColor);
        }

        // RIGHT_LEG
        if (!removed.contains("RIGHT_LEG")) {
            ctx.fill(fx, bodyBottom, fx + (int)(3*scale), bodyBottom + (int)(24*scale), lineColor);
            ctx.fill(fx + (int)(1*scale), bodyBottom + (int)(22*scale), fx + (int)(16*scale), bodyBottom + (int)(26*scale), lineColor);
        }

        // LEFT_LEG
        if (!removed.contains("LEFT_LEG")) {
            ctx.fill(fx - (int)(3*scale), bodyBottom, fx, bodyBottom + (int)(24*scale), lineColor);
            ctx.fill(fx - (int)(16*scale), bodyBottom + (int)(22*scale), fx - (int)(1*scale), bodyBottom + (int)(26*scale), lineColor);
        }
    }

    /** Draws an approximate circle outline using horizontal lines. */
    private static void drawCircle(DrawContext ctx, int cx, int cy, int r, int color) {
        for (int y = -r; y <= r; y++) {
            int x = (int) Math.sqrt(r * r - y * y);
            ctx.fill(cx - x, cy + y, cx - x + 2, cy + y + 2, color);
            ctx.fill(cx + x - 2, cy + y, cx + x, cy + y + 2, color);
        }
    }

    // ── timer ─────────────────────────────────────────────────────────────────

    private static void renderTimer(DrawContext ctx, MinecraftClient mc, int sw, int sh, OverlaySettings s) {
        // Compute remaining from last sync
        long now = System.currentTimeMillis();
        long elapsed = (now - ClientGameState.timerLastUpdateMs) / 1000;
        int remaining = Math.max(0, ClientGameState.timerRemainingSecs - (int) elapsed);
        float frac = ClientGameState.timerTotalSeconds > 0
            ? (float) remaining / ClientGameState.timerTotalSeconds : 0;

        int tx = (int)(s.timerX * sw);
        int ty = (int)(s.timerY * sh);

        int color;
        if (frac > 0.5f)      color = OverlaySettings.parseColor(s.colorTimerNormal);
        else if (frac > 0.25f) color = OverlaySettings.parseColor(s.colorTimerWarning);
        else                   color = OverlaySettings.parseColor(s.colorTimerCritical);

        if (s.showTimerNumber) {
            String text = "⏱ " + remaining + "s";
            int textX = tx - mc.textRenderer.getWidth(text) / 2;
            ctx.drawText(mc.textRenderer, text, textX, ty, color, true);
        }

        if (s.showTimerBar) {
            int barW = 100;
            int barH = 6;
            int bx = tx - barW / 2;
            int by = ty + (s.showTimerNumber ? 14 : 0);
            ctx.fill(bx, by, bx + barW, by + barH, 0x88000000);
            ctx.fill(bx, by, bx + (int)(barW * frac), by + barH, color);
        }
    }

    // ── limb particles ────────────────────────────────────────────────────────

    private static void renderParticles(DrawContext ctx, float delta) {
        List<LimbParticle> dead = new ArrayList<>();
        for (LimbParticle p : ClientGameState.limbParticles) {
            p.x  += p.vx * delta;
            p.y  += p.vy * delta;
            p.vy += 0.4f * delta; // gravity
            p.life -= 0.03f * delta;
            if (p.life <= 0) { dead.add(p); continue; }

            int a = (int)(p.life * 255);
            int col = (a << 24) | (p.color & 0x00FFFFFF);
            ctx.fill((int)p.x, (int)p.y, (int)p.x + 3, (int)p.y + 3, col);
        }
        ClientGameState.limbParticles.removeAll(dead);
    }

    /**
     * Call when a limb is removed to spawn a burst of particles at the
     * figure position. figureX/figureY from settings are used.
     */
    public static void spawnLimbParticles(int sw, int sh) {
        OverlaySettings s = OverlaySettings.get();
        int cx = (int)(s.figureX * sw);
        int cy = (int)(s.figureY * sh);

        Random rng = new Random();
        int particleColor = 0xFF3333; // red/blood

        for (int i = 0; i < 20; i++) {
            float vx = (rng.nextFloat() - 0.5f) * 6;
            float vy = (rng.nextFloat() - 1.0f) * 4;
            ClientGameState.limbParticles.add(new LimbParticle(cx, cy + 30, vx, vy, particleColor));
        }
    }

    // ── key click detection ───────────────────────────────────────────────────

    /**
     * Returns the keyboard character at screen coords (mx, my), or '\0' if no key there.
     * Also returns ' ' for the space key.
     */
    public static char getKeyAt(int mx, int my, int sw, int sh) {
        OverlaySettings s = OverlaySettings.get();
        float kx = s.keyboardX * sw;
        float ky = s.keyboardY * sh;
        float scale = s.keyboardScale;

        int keyW = (int)(28 * scale);
        int keyH = (int)(18 * scale);
        int gap  = (int)(3  * scale);

        String[] rows = "QWERTY".equals(s.keyboardLayout) ? QWERTY : ALPHA;

        int rowY = (int) ky;
        for (String row : rows) {
            int startX = (int)(kx - (row.length() * (keyW + gap)) / 2);
            if (my >= rowY && my < rowY + keyH) {
                for (int i = 0; i < row.length(); i++) {
                    int bx = startX + i * (keyW + gap);
                    if (mx >= bx && mx < bx + keyW) {
                        return Character.toLowerCase(row.charAt(i));
                    }
                }
            }
            rowY += keyH + gap;
        }

        // Check SPACE key
        int spaceW = (int)(80 * scale);
        int spaceX = (int)(kx - spaceW / 2);
        if (mx >= spaceX && mx < spaceX + spaceW && my >= rowY && my < rowY + keyH) {
            return ' ';
        }

        return '\0';
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static boolean shouldShow(String visibility) {
        return switch (visibility) {
            case "HANGER_ONLY" -> !ClientGameState.isHanged;
            case "HANGED_ONLY" ->  ClientGameState.isHanged;
            default            -> true; // "BOTH"
        };
    }

    private static int applyOpacity(int argb, float opacity) {
        int a = (int)((argb >>> 24) * opacity) & 0xFF;
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    private static int applyAlpha(int argb, int alpha) {
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }
}
