package com.customblocks.client.gui;

import com.customblocks.client.ClientSlotData;
import com.customblocks.client.texture.TextureCache;
import com.customblocks.network.ImageEditPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Main Custom Blocks GUI (B Screen):
 * - Left: vertical tabs (A-Z / Emoji / Colors / Favorites / Recycle / Templates / Special)
 * - Center: block grid with search
 * - Right: MegaPanel (create/edit/properties)
 * - Bottom: DropZone for dragging images
 */
@Environment(EnvType.CLIENT)
public class CustomBlocksScreen extends Screen {

    // ── Colors ────────────────────────────────────────────────────────────────
    private static final int C_BG     = 0xF20C0C14;
    private static final int C_PANEL  = 0xFF141420;
    private static final int C_BORDER = 0xFF222238;
    private static final int C_HI     = 0xFF5555EE;
    private static final int C_SEL    = 0xFF0E2010;
    private static final int C_SELBDR = 0xFF44FF44;
    private static final int C_TEXT   = 0xFFFFFFFF;
    private static final int C_GREY   = 0xFFAAAAAA;
    private static final int C_GOLD   = 0xFFFFD700;
    private static final int C_GREEN  = 0xFF44FF44;
    private static final int C_RED    = 0xFFFF4444;
    private static final int C_BLUE   = 0xFF66AAFF;
    private static final int C_GLOW   = 0xFFFFE840;

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int CELL = 68, GAP = 5, PAD = 10;
    private static final int TAB_W = 70, RIGHT_W = 220;
    private int cols = 4;

    // ── State ─────────────────────────────────────────────────────────────────
    private String selectedId = null;
    private int scroll = 0;
    private String search = "";
    private final List<ClientSlotData> filtered = new ArrayList<>();
    private int activeTab = 0; // 0=All, 1=Favs, 2=Recycle, 3=Templates, 4=Special

    private static final String[] TAB_LABELS = {"All","⭐ Favs","🗑 Recycle","📋 Templates","✨ Special"};

    private enum RightPanel { NONE, CREATE, RETEXTURE, PROPERTIES }
    private RightPanel panel = RightPanel.NONE;

    // Widgets
    private TextFieldWidget searchField;
    private TextFieldWidget fldCreateId, fldCreateName, fldCreateUrl;
    private ButtonWidget btnCreate, btnGive1, btnGive64, btnDelete, btnRetexture,
                         btnProperties, btnFavorite, btnEditor, btnChestBrowse,
                         btnCreateOk, btnCreateCancel,
                         btnRetexUrl, btnRetexOk, btnRetexCancel;
    private TextFieldWidget fldRetexUrl;
    // Properties panel
    private ButtonWidget btnGlowM, btnGlowP;
    private ButtonWidget[] btnHardness = new ButtonWidget[5];
    private ButtonWidget[] btnSound    = new ButtonWidget[8];
    private ButtonWidget btnPropOk;

    // Status toast
    private String statusMsg = "";
    private long   statusUntil = 0;

    // Drop zone state
    private boolean dropHovered = false;

    public CustomBlocksScreen() { super(Text.literal("Custom Blocks v10")); }

    @Override
    protected void init() {
        buildLayout();
        refreshFiltered();
    }

    private void buildLayout() {
        clearChildren();
        int gridX = PAD + TAB_W + GAP;
        int gridY = 28;
        int gridW = width - gridX - RIGHT_W - GAP - PAD;

        // Search bar
        searchField = new TextFieldWidget(textRenderer, gridX, 6, gridW - 60, 18, Text.literal(""));
        searchField.setMaxLength(128);
        searchField.setPlaceholderText(Text.literal("🔍 Search blocks…"));
        searchField.setChangedListener(s -> { search = s; scroll = 0; refreshFiltered(); });
        addDrawableChild(searchField);

        // Cols ± buttons
        addDrawableChild(ButtonWidget.builder(Text.literal("-"), b -> { if(cols>1){cols--;} }).dimensions(width-RIGHT_W-GAP-PAD-30, 6, 14, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> { if(cols<7){cols++;} }).dimensions(width-RIGHT_W-GAP-PAD-14, 6, 14, 18).build());

        // Tab buttons (left column)
        for (int i = 0; i < TAB_LABELS.length; i++) {
            final int ti = i;
            addDrawableChild(ButtonWidget.builder(Text.literal(TAB_LABELS[i]),
                    b -> { activeTab = ti; scroll = 0; refreshFiltered(); })
                    .dimensions(PAD, 28 + i * 22, TAB_W, 20).build());
        }

        // Right panel: create button
        int rp = width - RIGHT_W - PAD;
        btnCreate = ButtonWidget.builder(Text.literal("§a+ Create"),
                b -> { panel = (panel == RightPanel.CREATE ? RightPanel.NONE : RightPanel.CREATE); buildRightPanel(); })
                .dimensions(rp, 28, RIGHT_W, 20).build();
        addDrawableChild(btnCreate);

        // Chest Browse
        btnChestBrowse = ButtonWidget.builder(Text.literal("📦 Chest View"),
                b -> MinecraftClient.getInstance().setScreen(new ChestBrowseScreen(this)))
                .dimensions(rp, 52, RIGHT_W, 18).build();
        addDrawableChild(btnChestBrowse);

        buildRightPanel();
    }

    private void buildRightPanel() {
        // Remove old right-panel widgets and re-add
        // (simple approach: clear + rebuild all)
        // The main widgets persist; we just show/hide by re-adding panel widgets
        int rp = width - RIGHT_W - PAD;
        int ry = 74;

        // Selection-dependent buttons
        if (selectedId != null) {
            btnGive1  = ButtonWidget.builder(Text.literal("Give ×1"),   b -> give(1))  .dimensions(rp, ry,     RIGHT_W/2-1, 18).build();
            btnGive64 = ButtonWidget.builder(Text.literal("Give ×64"),  b -> give(64)) .dimensions(rp+RIGHT_W/2+1, ry, RIGHT_W/2-1, 18).build();
            addDrawableChild(btnGive1); addDrawableChild(btnGive64);
            ry += 22;
            btnRetexture = ButtonWidget.builder(Text.literal("🖼 Retexture"), b -> {
                panel = RightPanel.RETEXTURE; buildRightPanel(); })
                    .dimensions(rp, ry, RIGHT_W, 18).build();
            addDrawableChild(btnRetexture); ry += 22;
            btnProperties = ButtonWidget.builder(Text.literal("⚙ Properties"), b -> {
                panel = RightPanel.PROPERTIES; buildRightPanel(); })
                    .dimensions(rp, ry, RIGHT_W, 18).build();
            addDrawableChild(btnProperties); ry += 22;
            btnEditor = ButtonWidget.builder(Text.literal("✎ Pic Editor"), b ->
                    MinecraftClient.getInstance().setScreen(new ImageEditorScreen("retexture", selectedId, this)))
                    .dimensions(rp, ry, RIGHT_W/2-1, 18).build();
            addDrawableChild(btnEditor);
            btnFavorite = ButtonWidget.builder(
                    Text.literal(ClientSlotData.isFavorite(selectedId) ? "★ Unfavorite" : "☆ Favorite"),
                    b -> { ClientSlotData.toggleFavorite(selectedId); buildRightPanel(); })
                    .dimensions(rp+RIGHT_W/2+1, ry, RIGHT_W/2-1, 18).build();
            addDrawableChild(btnFavorite); ry += 22;
            btnDelete = ButtonWidget.builder(Text.literal("§c✖ Delete"), b -> doDelete())
                    .dimensions(rp, ry, RIGHT_W, 18).build();
            addDrawableChild(btnDelete); ry += 26;
        }

        if (panel == RightPanel.CREATE) buildCreatePanel(rp, ry);
        else if (panel == RightPanel.RETEXTURE && selectedId != null) buildRetexPanel(rp, ry);
        else if (panel == RightPanel.PROPERTIES && selectedId != null) buildPropPanel(rp, ry);
    }

    private void buildCreatePanel(int rp, int ry) {
        fldCreateId = new TextFieldWidget(textRenderer, rp, ry, RIGHT_W, 18, Text.literal(""));
        fldCreateId.setPlaceholderText(Text.literal("block_id")); fldCreateId.setMaxLength(64);
        addDrawableChild(fldCreateId); ry += 22;
        fldCreateName = new TextFieldWidget(textRenderer, rp, ry, RIGHT_W, 18, Text.literal(""));
        fldCreateName.setPlaceholderText(Text.literal("Display Name")); fldCreateName.setMaxLength(64);
        addDrawableChild(fldCreateName); ry += 22;
        fldCreateUrl = new TextFieldWidget(textRenderer, rp, ry, RIGHT_W, 18, Text.literal(""));
        fldCreateUrl.setPlaceholderText(Text.literal("https://…/texture.png")); fldCreateUrl.setMaxLength(512);
        addDrawableChild(fldCreateUrl); ry += 22;
        btnCreateOk = ButtonWidget.builder(Text.literal("§a✔ Create"), b -> doCreate())
                .dimensions(rp, ry, RIGHT_W/2-1, 18).build();
        btnCreateCancel = ButtonWidget.builder(Text.literal("§c✖"), b -> { panel = RightPanel.NONE; buildRightPanel(); })
                .dimensions(rp+RIGHT_W/2+1, ry, RIGHT_W/2-1, 18).build();
        addDrawableChild(btnCreateOk); addDrawableChild(btnCreateCancel);
        // Open in Pic Editor
        ry += 22;
        addDrawableChild(ButtonWidget.builder(Text.literal("✎ Open Pic Editor"), b ->
                MinecraftClient.getInstance().setScreen(new ImageEditorScreen("create", null, this)))
                .dimensions(rp, ry, RIGHT_W, 18).build());
    }

    private void buildRetexPanel(int rp, int ry) {
        fldRetexUrl = new TextFieldWidget(textRenderer, rp, ry, RIGHT_W, 18, Text.literal(""));
        fldRetexUrl.setPlaceholderText(Text.literal("New texture URL")); fldRetexUrl.setMaxLength(512);
        addDrawableChild(fldRetexUrl); ry += 22;
        btnRetexOk = ButtonWidget.builder(Text.literal("§a✔ Apply"), b -> doRetexture())
                .dimensions(rp, ry, RIGHT_W/2-1, 18).build();
        btnRetexCancel = ButtonWidget.builder(Text.literal("§c✖"), b -> { panel = RightPanel.NONE; buildRightPanel(); })
                .dimensions(rp+RIGHT_W/2+1, ry, RIGHT_W/2-1, 18).build();
        addDrawableChild(btnRetexOk); addDrawableChild(btnRetexCancel);
    }

    private void buildPropPanel(int rp, int ry) {
        ClientSlotData d = ClientSlotData.getById(selectedId);
        if (d == null) return;
        // Glow sliders
        btnGlowM = ButtonWidget.builder(Text.literal("-"), b -> adjustGlow(-1)).dimensions(rp, ry, 20, 18).build();
        btnGlowP = ButtonWidget.builder(Text.literal("+"), b -> adjustGlow(+1)).dimensions(rp+22, ry, 20, 18).build();
        addDrawableChild(btnGlowM); addDrawableChild(btnGlowP); ry += 22;
        // Hardness buttons
        String[] hLabels = {"Soft","Norm","Hard","Max","Unbr"};
        for (int i = 0; i < 5; i++) {
            final int level = i+1;
            btnHardness[i] = ButtonWidget.builder(Text.literal(hLabels[i]),
                    b -> sendCmd("hardness " + selectedId + " " + level))
                    .dimensions(rp + i*(RIGHT_W/5), ry, RIGHT_W/5, 16).build();
            addDrawableChild(btnHardness[i]);
        }
        ry += 20;
        // Sound buttons
        String[] sLabels = SlotManager.SOUND_KEYS.toArray(new String[0]);
        for (int i = 0; i < Math.min(sLabels.length,8); i++) {
            final String snd = sLabels[i];
            btnSound[i] = ButtonWidget.builder(Text.literal(snd),
                    b -> sendCmd("sound " + selectedId + " " + snd))
                    .dimensions(rp + (i%4)*(RIGHT_W/4), ry + (i/4)*18, RIGHT_W/4, 16).build();
            addDrawableChild(btnSound[i]);
        }
        ry += 40;
        btnPropOk = ButtonWidget.builder(Text.literal("§aDone"), b -> { panel = RightPanel.NONE; buildRightPanel(); })
                .dimensions(rp, ry, RIGHT_W, 18).build();
        addDrawableChild(btnPropOk);
    }

    // Need reference to SOUND_KEYS — borrow from server side or duplicate list here
    private static final List<String> SOUND_KEYS = List.of("stone","wood","grass","metal","glass","sand","wool","gravel");
    private static class SlotManager { static final List<String> SOUND_KEYS = CustomBlocksScreen.SOUND_KEYS; }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, width, height, C_BG);

        // Left tab column background
        ctx.fill(PAD, 26, PAD + TAB_W, height - 30, C_PANEL);
        ctx.drawBorder(PAD, 26, TAB_W, height - 56, C_BORDER);

        // Grid area
        int gridX = PAD + TAB_W + GAP, gridY = 28;
        int gridW = width - gridX - RIGHT_W - GAP - PAD;
        ctx.fill(gridX, gridY, gridX + gridW, height - 30, C_PANEL);
        ctx.drawBorder(gridX, gridY, gridW, height - 58, C_BORDER);

        // Right panel background
        int rp = width - RIGHT_W - PAD;
        ctx.fill(rp, 26, rp + RIGHT_W, height - 30, C_PANEL);
        ctx.drawBorder(rp, 26, RIGHT_W, height - 56, C_BORDER);

        // Render grid items
        renderGrid(ctx, mx, my, gridX + 4, gridY + 4, gridW - 8);

        // Drop zone at bottom
        int dzY = height - 28;
        boolean dz = my >= dzY;
        ctx.fill(PAD + TAB_W + GAP, dzY, width - PAD - RIGHT_W - GAP, height - 4,
                dz ? 0xFF1A1A3A : 0xFF0E0E1A);
        ctx.drawBorder(PAD + TAB_W + GAP, dzY, width - PAD - RIGHT_W - GAP - (PAD + TAB_W + GAP), 24, dz ? C_HI : C_BORDER);
        ctx.drawText(textRenderer, dz ? "§b⬇ Drop image here to create block" : "§7Drop zone: drag image here",
                PAD + TAB_W + GAP + 4, dzY + 8, dz ? 0x6666FF : 0x555577, false);

        // Status toast
        if (System.currentTimeMillis() < statusUntil) {
            int tw = textRenderer.getWidth(statusMsg);
            ctx.fill(width/2 - tw/2 - 4, height - 60, width/2 + tw/2 + 4, height - 46, 0xCC000000);
            ctx.drawText(textRenderer, statusMsg, width/2 - tw/2, height - 57, C_GREEN, false);
        }

        // Right panel header
        if (selectedId != null) {
            ClientSlotData d = ClientSlotData.getById(selectedId);
            if (d != null) {
                ctx.drawText(textRenderer, "§f" + d.displayName, rp + 4, 28, C_TEXT, false);
                ctx.drawText(textRenderer, "§7" + d.customId, rp + 4, 38, C_GREY, false);
                if (d.lightLevel > 0)
                    ctx.drawText(textRenderer, "§e✦ Glow " + d.lightLevel, rp + 4, 48, C_GLOW, false);
            }
        }

        if (panel == RightPanel.PROPERTIES && selectedId != null) {
            ClientSlotData d = ClientSlotData.getById(selectedId);
            if (d != null) {
                int rph = propHeaderY(rp);
                ctx.drawText(textRenderer, "§7Glow: §e" + d.lightLevel, rp + 44, rph, C_GREY, false);
                ctx.drawText(textRenderer, "§7Hardness:", rp + 4, rph + 20, C_GREY, false);
                ctx.drawText(textRenderer, "§7Sound:", rp + 4, rph + 38, C_GREY, false);
            }
        }

        super.render(ctx, mx, my, delta);
    }

    private int propHeaderY(int rp) {
        // Same calculation as in buildPropPanel — must match
        int baseRy = 74;
        if (selectedId != null) baseRy += 5 * 22 + 4; // selection buttons
        return baseRy;
    }

    private void renderGrid(DrawContext ctx, int mx, int my, int gx, int gy, int gw) {
        int cellTotal = CELL + GAP;
        int col = 0, row = 0;
        int startIdx = scroll * cols;

        for (int i = startIdx; i < filtered.size() && row < (height / cellTotal + 1); i++) {
            ClientSlotData d = filtered.get(i);
            int x = gx + col * cellTotal;
            int y = gy + row * cellTotal;

            if (y + CELL > height - 32) break;

            boolean sel     = d.customId.equals(selectedId);
            boolean hovered = mx >= x && mx < x + CELL && my >= y && my < y + CELL;

            ctx.fill(x, y, x + CELL, y + CELL, sel ? C_SEL : (hovered ? 0xFF1A1A36 : 0xFF0E0E1A));
            ctx.drawBorder(x, y, CELL, CELL, sel ? C_SELBDR : (hovered ? C_HI : C_BORDER));

            // Texture
            Identifier tex = TextureCache.get(d.customId);
            int imgSize = CELL - 18;
            int imgX = x + (CELL - imgSize) / 2, imgY = y + 4;
            if (tex != null) ctx.drawTexture(tex, imgX, imgY, 0, 0, imgSize, imgSize, imgSize, imgSize);
            else { ctx.fill(imgX, imgY, imgX + imgSize, imgY + imgSize, 0xFF333355);
                   ctx.drawText(textRenderer, "?", imgX + imgSize/2 - 3, imgY + imgSize/2 - 4, C_GREY, false); }

            // Name
            String name = d.displayName.length() > 9 ? d.displayName.substring(0,8)+"…" : d.displayName;
            ctx.drawText(textRenderer, name, x + 2, y + CELL - 12, hovered ? C_TEXT : C_GREY, false);

            // Glow badge
            if (d.lightLevel > 0) ctx.fill(x + CELL - 8, y + 2, x + CELL - 2, y + 8, C_GLOW);
            // Fav star
            if (ClientSlotData.isFavorite(d.customId))
                ctx.drawText(textRenderer, "★", x + 2, y + 2, C_GOLD, false);

            if (++col >= cols) { col = 0; row++; }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int gx = PAD + TAB_W + GAP + 4, gy = 28 + 4;
        int gw = width - gx - RIGHT_W - GAP - PAD - 8;
        int cellTotal = CELL + GAP;
        int startIdx = scroll * cols;

        for (int i = startIdx; i < filtered.size(); i++) {
            int rel = i - startIdx;
            int col = rel % cols, row = rel / cols;
            int x = gx + col * cellTotal, y = gy + row * cellTotal;
            if (y + CELL > height - 32) break;
            if (mx >= x && mx < x + CELL && my >= y && my < y + CELL) {
                selectedId = filtered.get(i).customId;
                panel = RightPanel.NONE;
                buildRightPanel();
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hx, double vy) {
        int maxRows = (filtered.size() + cols - 1) / cols;
        int visRows = (height - 60) / (CELL + GAP);
        int maxScroll = Math.max(0, maxRows - visRows);
        scroll = Math.max(0, Math.min(maxScroll, scroll + (vy < 0 ? 1 : -1)));
        return true;
    }

    // ── Actions ────────────────────────────────────────────────────────────────

    private void refreshFiltered() {
        List<ClientSlotData> all = new ArrayList<>(ClientSlotData.all());
        all.sort(Comparator.comparing(d -> d.displayName));
        filtered.clear();
        filtered.addAll(all.stream().filter(d -> {
            if (!search.isEmpty() && !d.displayName.toLowerCase().contains(search.toLowerCase())
                    && !d.customId.contains(search.toLowerCase())) return false;
            return switch (activeTab) {
                case 1 -> ClientSlotData.isFavorite(d.customId);
                default -> true;
            };
        }).collect(Collectors.toList()));
    }

    private void give(int count) {
        if (selectedId == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null)
            mc.player.networkHandler.sendChatCommand("cb give " + selectedId + " " + mc.player.getName().getString() + " " + count);
        toast("§aGave " + count + "× " + selectedId);
    }

    private void doDelete() {
        if (selectedId == null) return;
        sendCmd("delete " + selectedId);
        toast("§cDeleted " + selectedId);
        selectedId = null; panel = RightPanel.NONE; buildRightPanel();
    }

    private void doCreate() {
        if (fldCreateId == null || fldCreateName == null || fldCreateUrl == null) return;
        String id  = fldCreateId.getText().trim();
        String nm  = fldCreateName.getText().trim();
        String url = fldCreateUrl.getText().trim();
        if (id.isEmpty() || nm.isEmpty() || url.isEmpty()) { toast("§cFill in all fields."); return; }
        sendCmd("createurl " + id + " " + nm.replace(" ","_") + " " + url);
        toast("§7Creating '" + nm + "'…");
        panel = RightPanel.NONE; buildRightPanel();
    }

    private void doRetexture() {
        if (selectedId == null || fldRetexUrl == null) return;
        String url = fldRetexUrl.getText().trim();
        if (url.isEmpty()) { toast("§cEnter a URL."); return; }
        sendCmd("createurl _retex_ _retex_ " + url); // server handles via retexture action
        // Actually send retexture command
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null)
            mc.player.networkHandler.sendChatCommand("cb createurl " + selectedId + " retex " + url);
        toast("§7Retexturing…");
        panel = RightPanel.NONE; buildRightPanel();
    }

    private void adjustGlow(int delta) {
        if (selectedId == null) return;
        ClientSlotData d = ClientSlotData.getById(selectedId);
        if (d == null) return;
        int newGlow = Math.max(0, Math.min(15, d.lightLevel + delta));
        sendCmd("glow " + selectedId + " " + newGlow);
    }

    private void sendCmd(String cmd) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.player.networkHandler.sendChatCommand("cb " + cmd);
    }

    private void toast(String msg) { statusMsg = msg; statusUntil = System.currentTimeMillis() + 4000; }

    @Override public boolean shouldPause() { return false; }
    @Override public void close() { MinecraftClient.getInstance().setScreen(null); }
}
