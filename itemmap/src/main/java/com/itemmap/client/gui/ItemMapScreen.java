package com.itemmap.client.gui;

import com.itemmap.ItemMapMod;
import com.itemmap.manager.FrameData;
import com.itemmap.manager.FrameManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.*;

/**
 * In-game GUI for configuring item frame display.
 * Open with M key (all frames list) or by right-clicking a configured frame.
 */
@Environment(EnvType.CLIENT)
public class ItemMapScreen extends Screen {

    private final long targetFrameId;
    private FrameData editing;

    // UI widgets
    private TextFieldWidget labelField;
    private TextFieldWidget scaleField;
    private TextFieldWidget spinSpeedField;
    private TextFieldWidget paddingField;
    private TextFieldWidget bgColorField;
    private TextFieldWidget imageIdField;

    private static final int PANEL_W = 320;
    private static final int PANEL_H = 340;

    public ItemMapScreen(long frameId) {
        super(Text.literal("ItemMap Settings"));
        this.targetFrameId = frameId;
    }

    @Override
    protected void init() {
        super.init();

        int cx = width  / 2;
        int cy = height / 2;
        int px = cx - PANEL_W / 2;
        int py = cy - PANEL_H / 2;

        // Load the editing data
        if (targetFrameId >= 0) {
            editing = FrameManager.has(targetFrameId)
                ? FrameManager.get(targetFrameId).copy()
                : new FrameData(targetFrameId);
        } else {
            // No specific frame — pick first or null
            Collection<FrameData> all = FrameManager.all();
            editing = all.isEmpty() ? null : all.iterator().next().copy();
        }

        if (editing == null) {
            // Show "no frames" state with just a close button
            addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> close())
                .dimensions(cx - 40, cy, 80, 20).build());
            return;
        }

        int y = py + 30;
        int fieldX = px + 130;
        int fieldW = PANEL_W - 140;

        // ── Mode buttons ──────────────────────────────────────────────────────
        addDrawableChild(ButtonWidget.builder(Text.literal("2D Flat"),
            b -> { editing.mode = FrameData.DisplayMode.FLAT_2D; applyAndRebuild(); })
            .dimensions(px + 10, y, 85, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("3D Static"),
            b -> { editing.mode = FrameData.DisplayMode.RENDER_3D; applyAndRebuild(); })
            .dimensions(px + 100, y, 85, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("3D Spin"),
            b -> { editing.mode = FrameData.DisplayMode.SPIN_3D; applyAndRebuild(); })
            .dimensions(px + 190, y, 85, 18).build());
        y += 28;

        // ── Label ─────────────────────────────────────────────────────────────
        labelField = new TextFieldWidget(textRenderer, fieldX, y, fieldW, 16,
            Text.literal("Label"));
        labelField.setText(editing.label != null ? editing.label : "");
        labelField.setMaxLength(64);
        addDrawableChild(labelField);
        y += 22;

        // ── Scale ─────────────────────────────────────────────────────────────
        scaleField = new TextFieldWidget(textRenderer, fieldX, y, fieldW, 16,
            Text.literal("Scale"));
        scaleField.setText(String.valueOf(editing.scale));
        scaleField.setMaxLength(8);
        addDrawableChild(scaleField);
        y += 22;

        // ── Spin Speed ────────────────────────────────────────────────────────
        spinSpeedField = new TextFieldWidget(textRenderer, fieldX, y, fieldW, 16,
            Text.literal("Spin Speed"));
        spinSpeedField.setText(String.valueOf(editing.spinSpeed));
        spinSpeedField.setMaxLength(8);
        addDrawableChild(spinSpeedField);
        y += 22;

        // ── Padding ───────────────────────────────────────────────────────────
        paddingField = new TextFieldWidget(textRenderer, fieldX, y, fieldW, 16,
            Text.literal("Padding %"));
        paddingField.setText(String.valueOf(editing.padPct));
        paddingField.setMaxLength(6);
        addDrawableChild(paddingField);
        y += 22;

        // ── BG Color ──────────────────────────────────────────────────────────
        bgColorField = new TextFieldWidget(textRenderer, fieldX, y, fieldW, 16,
            Text.literal("BG Color"));
        bgColorField.setText(String.format("%08X", editing.bgColor));
        bgColorField.setMaxLength(8);
        addDrawableChild(bgColorField);
        y += 22;

        // ── Custom Image ID ───────────────────────────────────────────────────
        imageIdField = new TextFieldWidget(textRenderer, fieldX, y, fieldW, 16,
            Text.literal("Image ID"));
        imageIdField.setText(editing.customImageId != null ? editing.customImageId : "");
        imageIdField.setMaxLength(64);
        addDrawableChild(imageIdField);
        y += 22;

        // ── Toggle buttons ────────────────────────────────────────────────────
        addDrawableChild(ButtonWidget.builder(
            Text.literal("Glow: " + (editing.glowing ? "ON" : "OFF")),
            b -> { editing.glowing = !editing.glowing; applyAndRebuild(); })
            .dimensions(px + 10, y, 100, 18).build());
        addDrawableChild(ButtonWidget.builder(
            Text.literal("Invisible: " + (editing.invisible ? "ON" : "OFF")),
            b -> { editing.invisible = !editing.invisible; applyAndRebuild(); })
            .dimensions(px + 120, y, 120, 18).build());
        y += 28;

        // ── Bottom buttons ────────────────────────────────────────────────────
        addDrawableChild(ButtonWidget.builder(Text.literal("Apply"),
            b -> applyChanges())
            .dimensions(px + 10, py + PANEL_H - 28, 70, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Reset"),
            b -> sendCommand("reset " + editing.entityId))
            .dimensions(px + 90, py + PANEL_H - 28, 70, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Undo"),
            b -> sendCommand("undo"))
            .dimensions(px + 170, py + PANEL_H - 28, 50, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Redo"),
            b -> sendCommand("redo"))
            .dimensions(px + 228, py + PANEL_H - 28, 50, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("✕"),
            b -> close())
            .dimensions(px + PANEL_W - 20, py + 4, 16, 16).build());
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx, mx, my, delta);

        int cx = width  / 2;
        int cy = height / 2;
        int px = cx - PANEL_W / 2;
        int py = cy - PANEL_H / 2;

        // Panel background
        ctx.fill(px, py, px + PANEL_W, py + PANEL_H, 0xDD1A1A2E);
        ctx.fill(px, py, px + PANEL_W, py + 22,       0xDD2D2D5E);

        // Title
        ctx.drawCenteredTextWithShadow(textRenderer,
            "ItemMap - Frame " + (editing != null ? editing.entityId : "none"),
            cx, py + 7, 0xFFE0A020);

        if (editing == null) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                "No item frames configured.", cx, cy - 10, 0xFFAAAAAA);
            super.render(ctx, mx, my, delta);
            return;
        }

        // Labels
        int y = py + 30;
        y += 28;
        drawLabel(ctx, px + 10, y + 2,  "Label:");         y += 22;
        drawLabel(ctx, px + 10, y + 2,  "Scale:");         y += 22;
        drawLabel(ctx, px + 10, y + 2,  "Spin Speed:");    y += 22;
        drawLabel(ctx, px + 10, y + 2,  "Padding %:");     y += 22;
        drawLabel(ctx, px + 10, y + 2,  "BG Color:");      y += 22;
        drawLabel(ctx, px + 10, y + 2,  "Custom Image:");  y += 22;

        // Mode indicator
        String modeStr = editing != null ? "Mode: §a" + editing.mode.name() : "";
        ctx.drawTextWithShadow(textRenderer, modeStr, px + 10, py + 24, 0xFFFFFFFF);

        super.render(ctx, mx, my, delta);
    }

    private void drawLabel(DrawContext ctx, int x, int y, String text) {
        ctx.drawTextWithShadow(textRenderer, text, x, y, 0xFFCCCCCC);
    }

    private void applyAndRebuild() {
        // Rebuild screen to update button labels
        init(client, width, height);
    }

    private void applyChanges() {
        if (editing == null) return;
        // Read fields
        try { editing.scale = Float.parseFloat(scaleField.getText()); } catch (Exception ignored) {}
        try { editing.spinSpeed = Float.parseFloat(spinSpeedField.getText()); } catch (Exception ignored) {}
        try { editing.padPct = Float.parseFloat(paddingField.getText()); } catch (Exception ignored) {}
        try { editing.bgColor = (int) Long.parseLong(bgColorField.getText(), 16); } catch (Exception ignored) {}

        String lbl = labelField.getText().trim();
        editing.label = lbl.isEmpty() ? null : lbl;
        String imgId = imageIdField.getText().trim();
        editing.customImageId = imgId.isEmpty() ? null : imgId;

        // Clamp values
        editing.scale     = Math.max(0.1f, Math.min(2.0f, editing.scale));
        editing.spinSpeed = Math.max(0.1f, Math.min(100f, editing.spinSpeed));
        editing.padPct    = Math.max(0f,   Math.min(50f,  editing.padPct));

        // Send commands to server
        long eid = editing.entityId;
        sendCommand("set mode "      + editing.mode.name().toLowerCase().replace("_","") + " " + eid);
        sendCommand("set scale "     + editing.scale     + " " + eid);
        sendCommand("set spinspeed " + editing.spinSpeed  + " " + eid);
        sendCommand("set padding "   + editing.padPct     + " " + eid);
        sendCommand("set bgcolor "   + String.format("%08X", editing.bgColor) + " " + eid);
        sendCommand("set glow "      + editing.glowing    + " " + eid);
        sendCommand("set invisible " + editing.invisible  + " " + eid);
        if (editing.label != null)
            sendCommand("set label " + editing.label.replace(" ", "_") + " " + eid);
        if (editing.customImageId != null)
            sendCommand("set image " + editing.customImageId + " " + eid);

        close();
    }

    private void sendCommand(String sub) {
        if (client == null || client.player == null) return;
        client.player.networkHandler.sendChatCommand("im " + sub);
    }

    @Override
    public boolean shouldPause() { return false; }
}
