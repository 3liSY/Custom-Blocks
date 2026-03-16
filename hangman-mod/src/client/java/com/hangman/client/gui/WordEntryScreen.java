package com.hangman.client.gui;

import com.hangman.client.ClientGameState;
import com.hangman.common.config.HangmanConfig;
import com.hangman.common.network.HangmanPackets;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;

import static com.hangman.common.network.HangmanPackets.*;

/**
 * Full-screen word entry for the word-chooser player.
 * Their input is NOT shown in chat — it's sent privately to the server.
 */
public class WordEntryScreen extends Screen {

    private TextFieldWidget wordField;
    private TextFieldWidget categoryField;
    private String errorMsg = "";

    public WordEntryScreen() {
        super(Text.literal("Enter Secret Word"));
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int cy = height / 2;

        // Word input
        wordField = new TextFieldWidget(textRenderer, cx - 120, cy - 40, 240, 20,
            Text.literal("Secret Word"));
        wordField.setMaxLength(128);
        wordField.setPlaceholder(Text.literal("Type the secret word here..."));
        addDrawableChild(wordField);
        setInitialFocus(wordField);

        // Category input (if enabled)
        if (HangmanConfig.get().categoryEnabled) {
            categoryField = new TextFieldWidget(textRenderer, cx - 120, cy - 10, 240, 20,
                Text.literal("Category (optional)"));
            categoryField.setMaxLength(32);
            categoryField.setPlaceholder(Text.literal("Category (e.g. Animal, Movie, City)"));
            addDrawableChild(categoryField);
        }

        // Submit button
        addDrawableChild(ButtonWidget.builder(Text.literal("Submit Word"), btn -> submitWord())
            .dimensions(cx - 60, cy + 30, 120, 20).build());

        // Cancel button
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), btn -> close())
            .dimensions(cx - 60, cy + 56, 120, 20).build());
    }

    private void submitWord() {
        String word = wordField.getText().trim();
        if (word.isEmpty()) {
            errorMsg = "Word cannot be empty!";
            return;
        }
        String category = categoryField != null ? categoryField.getText().trim() : "";

        // Send to server
        PacketByteBuf buf = HangmanPackets.newBuf();
        buf.writeString(word, 256);
        buf.writeString(category, 64);

        if (client == null || client.getNetworkHandler() == null) return;
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
            new HangmanPackets.HangmanPayload(C2S_SUBMIT_WORD, buf));

        ClientGameState.wordScreenOpen = false;
        close();
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx, mx, my, delta);

        int cx = width / 2;
        int cy = height / 2;

        // Title
        ctx.drawCenteredTextWithShadow(textRenderer, "§6Enter the Secret Word", cx, cy - 80, 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer,
            "§7The other player will try to guess it!", cx, cy - 65, 0xAAAAAA);

        // Word label
        ctx.drawTextWithShadow(textRenderer, "§fSecret Word:", cx - 120, cy - 55, 0xFFFFFF);

        if (HangmanConfig.get().categoryEnabled) {
            ctx.drawTextWithShadow(textRenderer, "§fCategory:", cx - 120, cy - 25, 0xFFFFFF);
        }

        // Error
        if (!errorMsg.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, "§c" + errorMsg, cx, cy + 20, 0xFF5555);
        }

        super.render(ctx, mx, my, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // ENTER or NUMPAD_ENTER
            submitWord();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public boolean shouldCloseOnEsc() { return true; }
}
