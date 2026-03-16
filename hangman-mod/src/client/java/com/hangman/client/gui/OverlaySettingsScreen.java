package com.hangman.client.gui;

import com.hangman.common.config.OverlaySettings;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.nio.file.Paths;

/**
 * In-game settings screen for all overlay customisation options.
 * Opened with J key or /hangman settings.
 */
public class OverlaySettingsScreen extends Screen {

    private int scrollY = 0;
    private static final int ENTRY_H = 24;

    public OverlaySettingsScreen() {
        super(Text.literal("Hangman Overlay Settings"));
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int y  = 40 - scrollY;
        int labelW = 160;

        addSection("§6=== Keyboard ===", cx, y); y += ENTRY_H;

        y = addSlider("Keyboard X", OverlaySettings.get().keyboardX, 0, 1, cx, y, v -> OverlaySettings.get().keyboardX = (float)v); y += ENTRY_H;
        y = addSlider("Keyboard Y", OverlaySettings.get().keyboardY, 0, 1, cx, y, v -> OverlaySettings.get().keyboardY = (float)v); y += ENTRY_H;
        y = addSlider("Keyboard Scale", OverlaySettings.get().keyboardScale, 0.3, 2.0, cx, y, v -> OverlaySettings.get().keyboardScale = (float)v); y += ENTRY_H;
        y = addSlider("Keyboard Opacity", OverlaySettings.get().keyboardOpacity, 0, 1, cx, y, v -> OverlaySettings.get().keyboardOpacity = (float)v); y += ENTRY_H;

        y = addToggle("Layout: " + OverlaySettings.get().keyboardLayout, cx, y, btn -> {
            OverlaySettings.get().keyboardLayout = "QWERTY".equals(OverlaySettings.get().keyboardLayout) ? "ALPHA" : "QWERTY";
            btn.setMessage(Text.literal("Layout: " + OverlaySettings.get().keyboardLayout));
        }); y += ENTRY_H;

        y = addToggle("Cursor Mode: " + OverlaySettings.get().cursorModeEnabled, cx, y, btn -> {
            OverlaySettings.get().cursorModeEnabled = !OverlaySettings.get().cursorModeEnabled;
            btn.setMessage(Text.literal("Cursor Mode: " + OverlaySettings.get().cursorModeEnabled));
        }); y += ENTRY_H;

        y = addToggle("Physical Keys: " + OverlaySettings.get().physicalKeysEnabled, cx, y, btn -> {
            OverlaySettings.get().physicalKeysEnabled = !OverlaySettings.get().physicalKeysEnabled;
            btn.setMessage(Text.literal("Physical Keys: " + OverlaySettings.get().physicalKeysEnabled));
        }); y += ENTRY_H;

        y = addColorEntry("Key Unguessed Color", OverlaySettings.get().colorUnguessed, cx, y); y += ENTRY_H;
        y = addColorEntry("Key Correct Color", OverlaySettings.get().colorCorrect, cx, y); y += ENTRY_H;
        y = addColorEntry("Key Wrong Color", OverlaySettings.get().colorWrong, cx, y); y += ENTRY_H;
        y = addColorEntry("Keyboard BG Color", OverlaySettings.get().colorKeyboardBg, cx, y); y += ENTRY_H;

        addSection("§6=== Masked Word ===", cx, y); y += ENTRY_H;
        y = addSlider("Word X", OverlaySettings.get().wordX, 0, 1, cx, y, v -> OverlaySettings.get().wordX = (float)v); y += ENTRY_H;
        y = addSlider("Word Y", OverlaySettings.get().wordY, 0, 1, cx, y, v -> OverlaySettings.get().wordY = (float)v); y += ENTRY_H;
        y = addSlider("Word Scale", OverlaySettings.get().wordScale, 0.5, 3.0, cx, y, v -> OverlaySettings.get().wordScale = (float)v); y += ENTRY_H;
        y = addColorEntry("Blank Letter Color", OverlaySettings.get().colorWordBlank, cx, y); y += ENTRY_H;
        y = addColorEntry("Revealed Letter Color", OverlaySettings.get().colorWordRevealed, cx, y); y += ENTRY_H;

        addSection("§6=== Stick Figure ===", cx, y); y += ENTRY_H;
        y = addSlider("Figure X", OverlaySettings.get().figureX, 0, 1, cx, y, v -> OverlaySettings.get().figureX = (float)v); y += ENTRY_H;
        y = addSlider("Figure Y", OverlaySettings.get().figureY, 0, 1, cx, y, v -> OverlaySettings.get().figureY = (float)v); y += ENTRY_H;
        y = addSlider("Figure Scale", OverlaySettings.get().figureScale, 0.3, 2.0, cx, y, v -> OverlaySettings.get().figureScale = (float)v); y += ENTRY_H;

        y = addToggle("Style: " + OverlaySettings.get().figureStyle, cx, y, btn -> {
            String[] styles = {"STICK", "DETAILED", "PIXEL"};
            int idx = 0;
            for (int i = 0; i < styles.length; i++) if (styles[i].equals(OverlaySettings.get().figureStyle)) idx = i;
            OverlaySettings.get().figureStyle = styles[(idx + 1) % styles.length];
            btn.setMessage(Text.literal("Style: " + OverlaySettings.get().figureStyle));
        }); y += ENTRY_H;

        addSection("§6=== Timer ===", cx, y); y += ENTRY_H;
        y = addSlider("Timer X", OverlaySettings.get().timerX, 0, 1, cx, y, v -> OverlaySettings.get().timerX = (float)v); y += ENTRY_H;
        y = addSlider("Timer Y", OverlaySettings.get().timerY, 0, 1, cx, y, v -> OverlaySettings.get().timerY = (float)v); y += ENTRY_H;
        y = addToggle("Show Timer Number: " + OverlaySettings.get().showTimerNumber, cx, y, btn -> {
            OverlaySettings.get().showTimerNumber = !OverlaySettings.get().showTimerNumber;
            btn.setMessage(Text.literal("Show Timer Number: " + OverlaySettings.get().showTimerNumber));
        }); y += ENTRY_H;
        y = addToggle("Show Timer Bar: " + OverlaySettings.get().showTimerBar, cx, y, btn -> {
            OverlaySettings.get().showTimerBar = !OverlaySettings.get().showTimerBar;
            btn.setMessage(Text.literal("Show Timer Bar: " + OverlaySettings.get().showTimerBar));
        }); y += ENTRY_H;

        addSection("§6=== Visibility ===", cx, y); y += ENTRY_H;
        y = addVisibilityCycle("Keyboard visible for", OverlaySettings.get().keyboardVisibleFor, cx, y, v -> OverlaySettings.get().keyboardVisibleFor = v); y += ENTRY_H;
        y = addVisibilityCycle("Figure visible for", OverlaySettings.get().figureVisibleFor, cx, y, v -> OverlaySettings.get().figureVisibleFor = v); y += ENTRY_H;
        y = addVisibilityCycle("Word visible for", OverlaySettings.get().wordVisibleFor, cx, y, v -> OverlaySettings.get().wordVisibleFor = v); y += ENTRY_H;
        y = addVisibilityCycle("Timer visible for", OverlaySettings.get().timerVisibleFor, cx, y, v -> OverlaySettings.get().timerVisibleFor = v); y += ENTRY_H;

        // Save button
        addDrawableChild(ButtonWidget.builder(Text.literal("§aSave & Close"), btn -> {
            saveSettings();
            close();
        }).dimensions(cx - 60, height - 30, 120, 20).build());
    }

    // ── widget helpers ────────────────────────────────────────────────────────

    private void addSection(String label, int cx, int y) {
        addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> {})
            .dimensions(cx - 120, y, 240, 14).build());
    }

    private int addSlider(String label, double current, double min, double max, int cx, int y, java.util.function.DoubleConsumer consumer) {
        addDrawableChild(new SliderWidget(cx - 120, y, 240, 20, Text.literal(label), (current - min) / (max - min)) {
            final double lo = min, hi = max;
            @Override protected void updateMessage() { setMessage(Text.literal(label + ": " + String.format("%.2f", value * (hi - lo) + lo))); }
            @Override protected void applyValue()   { consumer.accept(value * (hi - lo) + lo); }
        });
        return y;
    }

    private int addToggle(String label, int cx, int y, ButtonWidget.PressAction action) {
        addDrawableChild(ButtonWidget.builder(Text.literal(label), action)
            .dimensions(cx - 120, y, 240, 20).build());
        return y;
    }

    private int addColorEntry(String label, String currentHex, int cx, int y) {
        // For now just shows the current value; a full color picker is beyond scope
        addDrawableChild(ButtonWidget.builder(Text.literal(label + ": " + currentHex), b -> {})
            .dimensions(cx - 120, y, 240, 20).build());
        return y;
    }

    private int addVisibilityCycle(String label, String current, int cx, int y, java.util.function.Consumer<String> setter) {
        addDrawableChild(ButtonWidget.builder(Text.literal(label + ": " + current), btn -> {
            String[] opts = {"BOTH", "HANGER_ONLY", "HANGED_ONLY"};
            int idx = 0;
            for (int i = 0; i < opts.length; i++) if (opts[i].equals(current)) idx = i;
            String next = opts[(idx + 1) % opts.length];
            setter.accept(next);
            btn.setMessage(Text.literal(label + ": " + next));
        }).dimensions(cx - 120, y, 240, 20).build());
        return y;
    }

    private void saveSettings() {
        try {
            java.nio.file.Path dir = Paths.get(System.getProperty("user.dir"), "config");
            OverlaySettings.save(dir);
        } catch (Exception ignored) {}
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx, mx, my, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, "§6Hangman Overlay Settings", width / 2, 12, 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer, "§7Scroll to see all options", width / 2, 24, 0xAAAAAA);
        super.render(ctx, mx, my, delta);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hScroll, double vScroll) {
        scrollY = Math.max(0, (int)(scrollY - vScroll * 12));
        clearChildren();
        init();
        return true;
    }

    @Override
    public boolean shouldPause() { return false; }
}
