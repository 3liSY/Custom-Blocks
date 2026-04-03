package com.customblocks.client.gui;

import com.customblocks.client.ClientSlotData;
import com.customblocks.client.texture.TextureCache;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Chest-style browse GUI: 9 tabs × 45 slots per page, search bar.
 * Click = ×1, Shift+Click = ×64, Ctrl+Click = full stack, Alt+Click = all in category.
 */
@Environment(EnvType.CLIENT)
public class ChestBrowseScreen extends Screen {

    private static final int SLOT_SIZE = 18;
    private static final int COLS = 9;
    private static final int ROWS = 5;
    private static final int SLOTS_PER_PAGE = COLS * ROWS; // 45

    private static final String[] TABS = {"A-E","F-J","K-O","P-T","U-Z","0-9","⭐Favs","🔍All","🗑Recycle"};
    private int activeTab  = 7; // default: All
    private int scrollPage = 0;
    private String search  = "";

    private TextFieldWidget searchField;
    private List<ClientSlotData> displayed = new ArrayList<>();

    private final Screen parent;

    public ChestBrowseScreen(Screen parent) {
        super(Text.literal("Custom Blocks — Browse"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // Search bar
        searchField = new TextFieldWidget(textRenderer, width/2 - 80, height/2 - 130, 160, 18,
                Text.literal("Search…"));
        searchField.setMaxLength(64);
        searchField.setChangedListener(s -> { search = s; scrollPage = 0; refresh(); });
        addDrawableChild(searchField);
        refresh();
    }

    private void refresh() {
        String tab = TABS[activeTab];
        List<ClientSlotData> all = new ArrayList<>(ClientSlotData.all());
        all.sort(Comparator.comparing(d -> d.displayName));

        displayed = all.stream().filter(d -> {
            if (!search.isEmpty() && !d.displayName.toLowerCase().contains(search.toLowerCase())
                    && !d.customId.toLowerCase().contains(search.toLowerCase())) return false;
            if (tab.equals("🔍All"))     return true;
            if (tab.equals("⭐Favs"))    return ClientSlotData.isFavorite(d.customId);
            if (tab.equals("🗑Recycle")) return false; // recycle is separate
            if (tab.equals("0-9"))       return !d.displayName.isEmpty() && Character.isDigit(d.displayName.charAt(0));
            // Letter ranges
            char first = Character.toUpperCase(d.displayName.isEmpty() ? '?' : d.displayName.charAt(0));
            return switch (tab) {
                case "A-E" -> first >= 'A' && first <= 'E';
                case "F-J" -> first >= 'F' && first <= 'J';
                case "K-O" -> first >= 'K' && first <= 'O';
                case "P-T" -> first >= 'P' && first <= 'T';
                case "U-Z" -> first >= 'U' && first <= 'Z';
                default    -> true;
            };
        }).collect(Collectors.toList());
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, width, height, 0xA0000000);

        int chestW = COLS * SLOT_SIZE + 20;
        int chestH = ROWS * SLOT_SIZE + 60;
        int cx = width/2 - chestW/2, cy = height/2 - chestH/2;

        // Chest background
        ctx.fill(cx, cy, cx+chestW, cy+chestH, 0xFF2D2D3D);
        ctx.drawBorder(cx, cy, chestW, chestH, 0xFF5555AA);

        // Tabs (top row)
        for (int i = 0; i < TABS.length; i++) {
            int tx = cx + i * (chestW / TABS.length);
            int tw = chestW / TABS.length;
            boolean active = i == activeTab;
            ctx.fill(tx, cy-16, tx+tw, cy, active ? 0xFF3A3A6A : 0xFF252535);
            ctx.drawBorder(tx, cy-16, tw, 16, active ? 0xFF7777FF : 0xFF333355);
            String label = TABS[i].length() > 5 ? TABS[i].substring(0,5) : TABS[i];
            ctx.drawText(textRenderer, label, tx+2, cy-12, active ? 0xFFFFFF : 0xAAAAAA, false);
        }

        // Slots
        int start = scrollPage * SLOTS_PER_PAGE;
        for (int i = 0; i < SLOTS_PER_PAGE && (start + i) < displayed.size(); i++) {
            int col = i % COLS, row = i / COLS;
            int sx = cx + 10 + col * SLOT_SIZE;
            int sy = cy + 30 + row * SLOT_SIZE;
            ClientSlotData d = displayed.get(start + i);

            boolean hovered = mx >= sx && mx < sx+SLOT_SIZE && my >= sy && my < sy+SLOT_SIZE;
            ctx.fill(sx, sy, sx+SLOT_SIZE, sy+SLOT_SIZE, hovered ? 0xFF3A3A6A : 0xFF222232);
            ctx.drawBorder(sx, sy, SLOT_SIZE, SLOT_SIZE, 0xFF444466);

            // Texture
            Identifier tex = TextureCache.get(d.customId);
            if (tex != null) ctx.drawTexture(tex, sx+1, sy+1, 0, 0, 16, 16, 16, 16);
            else ctx.fill(sx+4, sy+4, sx+14, sy+14, 0xFF666688);

            // Glow indicator
            if (d.lightLevel > 0) ctx.fill(sx+13, sy+1, sx+16, sy+4, 0xFFFFFF00);

            // Fav star
            if (ClientSlotData.isFavorite(d.customId))
                ctx.drawText(textRenderer, "★", sx+1, sy+1, 0xFFD700, false);

            // Tooltip on hover
            if (hovered) {
                ctx.drawTooltip(textRenderer, Text.literal("§f" + d.displayName + "\n§7" + d.customId + "\n§7Glow: " + d.lightLevel + " | Sound: " + d.soundType), mx, my);
            }
        }

        // Page indicator
        int totalPages = Math.max(1, (displayed.size() + SLOTS_PER_PAGE - 1) / SLOTS_PER_PAGE);
        ctx.drawText(textRenderer, "Page " + (scrollPage+1) + "/" + totalPages,
                cx + chestW/2 - 20, cy + chestH - 12, 0xAAAAAA, false);

        // Scroll hints
        ctx.drawText(textRenderer, "[ Scroll to page ]", cx+2, cy+chestH-12, 0x666688, false);
        ctx.drawText(textRenderer, displayed.size() + " blocks", cx+chestW-60, cy+chestH-12, 0x666688, false);

        super.render(ctx, mx, my, delta);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        // Tab click
        int chestW = COLS * SLOT_SIZE + 20;
        int cx = width/2 - chestW/2, cy = height/2 - (ROWS*SLOT_SIZE+60)/2;
        if (my >= cy-16 && my < cy) {
            int tab = (int)((mx - cx) / (chestW / (double)TABS.length));
            if (tab >= 0 && tab < TABS.length) { activeTab = tab; scrollPage = 0; refresh(); return true; }
        }

        // Slot click
        int start = scrollPage * SLOTS_PER_PAGE;
        for (int i = 0; i < SLOTS_PER_PAGE && (start + i) < displayed.size(); i++) {
            int col = i % COLS, row = i / COLS;
            int sx = cx + 10 + col * SLOT_SIZE, sy = cy + 30 + row * SLOT_SIZE;
            if (mx >= sx && mx < sx+SLOT_SIZE && my >= sy && my < sy+SLOT_SIZE) {
                ClientSlotData d = displayed.get(start + i);
                handleSlotClick(d, btn, hasShiftDown(), hasControlDown(),
                        net.minecraft.client.option.KeyBinding.isKeyPressed(net.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT));
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    private void handleSlotClick(ClientSlotData d, int btn, boolean shift, boolean ctrl, boolean alt) {
        if (btn == 1) { // Right click = favorite toggle
            ClientSlotData.toggleFavorite(d.customId); return;
        }
        int count = shift ? 64 : (ctrl ? 64 : 1); // ctrl gives max stack
        // Send give command via chat
        String cmd = "/cb give " + d.customId + " @s " + count;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.player.networkHandler.sendChatCommand(
                "cb give " + d.customId + " " + mc.player.getName().getString() + " " + count);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hx, double vy) {
        int totalPages = Math.max(1, (displayed.size() + SLOTS_PER_PAGE - 1) / SLOTS_PER_PAGE);
        if (vy < 0 && scrollPage < totalPages - 1) scrollPage++;
        else if (vy > 0 && scrollPage > 0) scrollPage--;
        return true;
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void close() { MinecraftClient.getInstance().setScreen(parent); }
}
