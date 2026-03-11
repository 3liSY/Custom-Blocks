package com.customblocks.client.gui;

import com.customblocks.SlotManager;
import com.customblocks.client.texture.TextureCache;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class CustomBlocksScreen extends Screen {

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int COLS      = 4;
    private static final int CELL      = 56;   // cell size (px)
    private static final int GAP       = 4;
    private static final int GRID_W    = COLS * (CELL + GAP) - GAP;
    private static final int RIGHT_W   = 188;
    private static final int PAD       = 10;
    private static final int TOP_H     = 32;   // title + search bar

    // Colours
    private static final int COL_BG         = 0xF0101018;
    private static final int COL_PANEL      = 0xFF181828;
    private static final int COL_BORDER     = 0xFF2A2A50;
    private static final int COL_BORDER_HI  = 0xFF5555CC;
    private static final int COL_SELECTED   = 0xFF1A4020;
    private static final int COL_SEL_BORDER = 0xFF44EE44;
    private static final int COL_HOVERED    = 0xFF1A1A40;
    private static final int COL_GOLD       = 0xFFFFD700;
    private static final int COL_WHITE      = 0xFFFFFFFF;
    private static final int COL_GREY       = 0xFFAAAAAA;
    private static final int COL_DIM        = 0xFF777777;
    private static final int COL_GREEN      = 0xFF55FF55;
    private static final int COL_RED        = 0xFFFF5555;
    private static final int COL_YELLOW     = 0xFFFFAA00;

    // Panel geometry (computed in init)
    private int px, py, pw, ph;

    // State
    private String selectedId  = null;
    private int    scroll      = 0;
    private String search      = "";
    private List<SlotManager.SlotData> filtered = new ArrayList<>();

    // Status bar
    private String  statusMsg   = "";
    private int     statusColor = COL_GREEN;
    private long    statusUntil = 0;

    // Panels
    private enum Panel { NONE, CREATE, RENAME, RETEXTURE, PROPERTIES }
    private Panel activePanel = Panel.NONE;

    // Widgets — right panel
    private ButtonWidget btnCreate, btnGive, btnRename, btnRetexture, btnDelete, btnProperties;

    // Sub-panel widgets (create)
    private TextFieldWidget fldCreateId, fldCreateName, fldCreateUrl;
    private ButtonWidget    btnCreateOk, btnCreateCancel;

    // Sub-panel widgets (rename)
    private TextFieldWidget fldRenameNew;
    private ButtonWidget    btnRenameOk, btnRenameCancel;

    // Sub-panel widgets (retexture)
    private TextFieldWidget fldRetextureUrl;
    private ButtonWidget    btnRetextureOk, btnRetextureCancel;

    // Sub-panel widgets (properties)
    private ButtonWidget btnGlowMinus, btnGlowPlus;
    private ButtonWidget btnHardSoft, btnHardNormal, btnHardHard, btnHardUnbreak;
    private ButtonWidget btnSoundStone, btnSoundWood, btnSoundMetal, btnSoundGlass, btnSoundGrass;
    private ButtonWidget btnPropClose;

    // Search widget
    private TextFieldWidget fldSearch;

    public CustomBlocksScreen() {
        super(Text.literal("Custom Blocks"));
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        pw = GRID_W + RIGHT_W + PAD * 3;
        ph = Math.min(height - 20, 430);
        px = (width  - pw) / 2;
        py = (height - ph) / 2;

        rebuildFiltered();

        // Search field
        fldSearch = new TextFieldWidget(textRenderer,
                px + PAD, py + PAD + 12, GRID_W - 2, 16, Text.literal(""));
        fldSearch.setPlaceholder(Text.literal("Search blocks..."));
        fldSearch.setChangedListener(s -> { search = s; scroll = 0; rebuildFiltered(); });
        addDrawableChild(fldSearch);

        // Right panel buttons
        int bx = px + PAD + GRID_W + PAD;
        int by = py + TOP_H + PAD;
        int bw = RIGHT_W - PAD;
        btnCreate     = mkBtn(bx, by,      bw, "  ➕  New Block",      b -> openPanel(Panel.CREATE));
        btnGive       = mkBtn(bx, by + 26, bw, "  🎁  Give to Me",     b -> doGive());
        btnRename     = mkBtn(bx, by + 52, bw, "  ✏️  Rename",          b -> openPanel(Panel.RENAME));
        btnRetexture  = mkBtn(bx, by + 78, bw, "  🖼️  Change Texture",  b -> openPanel(Panel.RETEXTURE));
        btnProperties = mkBtn(bx, by +104, bw, "  ⚙️  Properties",      b -> openPanel(Panel.PROPERTIES));
        btnDelete     = mkBtn(bx, by +130, bw, "  🗑️  Delete",          b -> doDelete());

        addAllButtons(btnCreate, btnGive, btnRename, btnRetexture, btnProperties, btnDelete);
        updateButtonStates();

        // Sub-panel — Create
        int spY = py + ph - 100;
        fldCreateId  = mkField(px + PAD, spY,      GRID_W - 2, "id (e.g. discord)");
        fldCreateName= mkField(px + PAD, spY + 22, GRID_W - 2, "Display Name");
        fldCreateUrl = mkField(px + PAD, spY + 44, GRID_W - 2, "https://image-url.png");
        fldCreateUrl.setMaxLength(512);
        btnCreateOk  = mkBtn(px + PAD,      spY + 66, 80, "Create", b -> doCreate());
        btnCreateCancel = mkBtn(px + PAD + 84, spY + 66, 60, "Cancel", b -> closePanel());

        // Sub-panel — Rename
        fldRenameNew   = mkField(px + PAD, spY + 22, GRID_W - 2, "New display name");
        btnRenameOk    = mkBtn(px + PAD,      spY + 44, 80, "Rename", b -> doRename());
        btnRenameCancel= mkBtn(px + PAD + 84, spY + 44, 60, "Cancel", b -> closePanel());

        // Sub-panel — Retexture
        fldRetextureUrl   = mkField(px + PAD, spY + 22, GRID_W - 2, "https://new-image.png");
        fldRetextureUrl.setMaxLength(512);
        btnRetextureOk    = mkBtn(px + PAD,      spY + 44, 80, "Apply", b -> doRetexture());
        btnRetextureCancel= mkBtn(px + PAD + 84, spY + 44, 60, "Cancel", b -> closePanel());

        // Sub-panel — Properties  (right side lower)
        int propY = by + 168;
        int bwH = (RIGHT_W - PAD - 4) / 4 - 1;
        btnGlowMinus = mkBtn(bx,       propY + 22, 22, "−", b -> adjustGlow(-1));
        btnGlowPlus  = mkBtn(bx + 118, propY + 22, 22, "+", b -> adjustGlow(+1));

        btnHardSoft   = mkBtn(bx,           propY + 52, bwH, "Soft",     b -> setHard(0.3f));
        btnHardNormal = mkBtn(bx + bwH + 2, propY + 52, bwH, "Normal",   b -> setHard(1.5f));
        btnHardHard   = mkBtn(bx + (bwH+2)*2, propY+52, bwH, "Hard",     b -> setHard(5.0f));
        btnHardUnbreak= mkBtn(bx + (bwH+2)*3, propY+52, bwH, "∞",        b -> setHard(-1f));

        int bwS = (RIGHT_W - PAD - 8) / 5;
        btnSoundStone = mkBtn(bx,              propY + 78, bwS, "Stone", b -> setSound("stone"));
        btnSoundWood  = mkBtn(bx + (bwS+2),   propY + 78, bwS, "Wood",  b -> setSound("wood"));
        btnSoundMetal = mkBtn(bx + (bwS+2)*2, propY + 78, bwS, "Metal", b -> setSound("metal"));
        btnSoundGlass = mkBtn(bx + (bwS+2)*3, propY + 78, bwS, "Glass", b -> setSound("glass"));
        btnSoundGrass = mkBtn(bx + (bwS+2)*4, propY + 78, bwS, "Grass", b -> setSound("grass"));

        btnPropClose = mkBtn(bx, propY + 104, RIGHT_W - PAD, "Done ✓", b -> closePanel());
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx, mx, my, delta);

        // Main background
        ctx.fill(px - 2, py - 2, px + pw + 2, py + ph + 2, COL_BORDER);
        ctx.fill(px, py, px + pw, py + ph, COL_BG);

        // Title bar gradient
        ctx.fillGradient(px, py, px + pw, py + 12, 0xFF1A1A3A, 0xFF101018);
        ctx.drawCenteredTextWithShadow(textRenderer, "✦  Custom Blocks  ✦", px + pw / 2, py + 2, COL_GOLD);

        // Divider
        int divX = px + PAD + GRID_W + PAD;
        ctx.fill(divX - 1, py + 12, divX, py + ph - 4, COL_BORDER);

        // Slot counter bottom-left
        ctx.drawTextWithShadow(textRenderer,
                "§7" + SlotManager.usedSlots() + " / " + SlotManager.MAX_SLOTS + " slots",
                px + PAD, py + ph - 11, COL_DIM);

        // Block grid
        drawGrid(ctx, mx, my);

        // Right panel
        drawRightPanel(ctx);

        // Status
        if (System.currentTimeMillis() < statusUntil) {
            ctx.drawCenteredTextWithShadow(textRenderer, statusMsg, px + pw / 2, py + ph + 4, statusColor);
        }

        // Active sub-panel overlay
        drawActivePanel(ctx);

        super.render(ctx, mx, my, delta);
    }

    private void drawGrid(DrawContext ctx, int mx, int my) {
        int gx = px + PAD;
        int gy = py + TOP_H;
        int gh = py + ph - 16;

        ctx.enableScissor(gx, gy, gx + GRID_W, gh);

        for (int i = 0; i < filtered.size(); i++) {
            int col = i % COLS;
            int row = i / COLS;
            int cx  = gx + col * (CELL + GAP);
            int cy  = gy + row * (CELL + GAP) - scroll;

            if (cy + CELL < gy || cy > gh) continue;

            SlotManager.SlotData data = filtered.get(i);
            boolean sel  = data.customId.equals(selectedId);
            boolean hov  = mx >= cx && mx < cx + CELL && my >= cy && my < cy + CELL;

            // Cell bg
            ctx.fill(cx, cy, cx + CELL, cy + CELL, sel ? COL_SELECTED : (hov ? COL_HOVERED : COL_PANEL));
            ctx.drawBorder(cx, cy, CELL, CELL, sel ? COL_SEL_BORDER : (hov ? COL_BORDER_HI : COL_BORDER));

            // Texture
            int pad = 4;
            int tw  = CELL - pad * 2 - 10; // leave room for label
            TextureCache.TexInfo tex = TextureCache.getOrLoad(data.customId, data.texture);
            ctx.drawTexture(tex.id(), cx + pad, cy + pad, 0, 0, tw, tw, tex.width(), tex.height());

            // Glow badge
            if (data.lightLevel > 0) {
                ctx.drawTextWithShadow(textRenderer, "✦", cx + CELL - 10, cy + 2, 0xFFFFDD00);
            }

            // Name label
            String label = data.displayName.length() > 7
                    ? data.displayName.substring(0, 6) + "…"
                    : data.displayName;
            ctx.drawCenteredTextWithShadow(textRenderer, "§f" + label,
                    cx + CELL / 2, cy + CELL - 10, COL_WHITE);
        }

        ctx.disableScissor();

        // Empty state
        if (filtered.isEmpty()) {
            String msg = search.isEmpty()
                    ? "No blocks yet! Click ➕ New Block"
                    : "No match for \"" + search + "\"";
            ctx.drawCenteredTextWithShadow(textRenderer, "§7" + msg,
                    gx + GRID_W / 2, gy + 50, COL_GREY);
        }
    }

    private void drawRightPanel(DrawContext ctx) {
        int rx = px + PAD + GRID_W + PAD + 2;
        int ry = py + TOP_H + PAD;
        int rw = RIGHT_W - PAD - 4;

        // "Custom Blocks" section header
        ctx.drawCenteredTextWithShadow(textRenderer, "§e● Selected Block", rx + rw / 2, ry - 10, COL_GOLD);

        SlotManager.SlotData data = selectedId != null ? SlotManager.getById(selectedId) : null;

        if (data == null) {
            ctx.drawCenteredTextWithShadow(textRenderer, "§7← Click a block",
                    rx + rw / 2, ry + 40, COL_GREY);
            return;
        }

        // Big preview
        int previewSize = 60;
        int pvX = rx + (rw - previewSize) / 2;
        int pvY = ry + 30;
        ctx.fill(pvX - 2, pvY - 2, pvX + previewSize + 2, pvY + previewSize + 2, COL_BORDER);
        TextureCache.TexInfo tex = TextureCache.getOrLoad(data.customId, data.texture);
        ctx.drawTexture(tex.id(), pvX, pvY, 0, 0, previewSize, previewSize, tex.width(), tex.height());

        // Info rows
        int infoY = pvY + previewSize + 8;
        ctx.drawCenteredTextWithShadow(textRenderer, "§f" + data.displayName, rx + rw / 2, infoY,     COL_WHITE);
        ctx.drawCenteredTextWithShadow(textRenderer, "§7ID: §f" + data.customId, rx + rw / 2, infoY + 11, COL_GREY);
        ctx.drawCenteredTextWithShadow(textRenderer, "§8Slot " + data.index,     rx + rw / 2, infoY + 22, 0xFF555577);

        // Property tags
        int tagY = infoY + 36;
        if (data.lightLevel > 0) {
            ctx.fill(rx, tagY, rx + rw, tagY + 12, 0x55FFDD00);
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "§6✦ Glow Level " + data.lightLevel, rx + rw / 2, tagY + 2, 0xFFFFDD44);
            tagY += 16;
        }
        // Hardness label
        String hardLabel = data.hardness < 0 ? "Unbreakable" :
                           data.hardness == 0 ? "Instant" :
                           data.hardness <= 0.5f ? "Soft" :
                           data.hardness <= 2.5f ? "Normal" : "Hard";
        ctx.drawCenteredTextWithShadow(textRenderer,
                "§8⚒ " + hardLabel + "  🔊 " + cap(data.soundType), rx + rw / 2, tagY + 2, COL_DIM);

        // Properties panel overlay (draws over info)
        if (activePanel == Panel.PROPERTIES) {
            drawPropertiesPanel(ctx, rx, ry, rw);
        }
    }

    private void drawPropertiesPanel(DrawContext ctx, int rx, int ry, int rw) {
        if (selectedId == null) return;
        SlotManager.SlotData data = SlotManager.getById(selectedId);
        if (data == null) return;

        int propY = ry + 168;
        ctx.fill(rx - 2, propY - 14, rx + rw + 2, propY + 120, 0xEE111128);
        ctx.drawBorder(rx - 2, propY - 14, rw + 4, 134, COL_BORDER_HI);
        ctx.drawCenteredTextWithShadow(textRenderer, "§b⚙ Properties", rx + rw / 2, propY - 11, 0xFFAADDFF);

        // Glow row
        ctx.drawTextWithShadow(textRenderer, "§eGlow: §f" + data.lightLevel,
                rx, propY + 6, COL_GOLD);
        // Render glow +/- buttons (added in openPanel)

        // Hardness row
        ctx.drawTextWithShadow(textRenderer, "§eHardness:", rx, propY + 40, COL_GOLD);

        // Sound row
        ctx.drawTextWithShadow(textRenderer, "§eSound:", rx, propY + 66, COL_GOLD);
    }

    private void drawActivePanel(DrawContext ctx) {
        if (activePanel == Panel.NONE || activePanel == Panel.PROPERTIES) return;

        int spY = py + ph - 100;
        int spW = GRID_W + 2;

        switch (activePanel) {
            case CREATE -> {
                ctx.fill(px + PAD - 2, spY - 14, px + PAD + spW, py + ph - 4, 0xEE111128);
                ctx.drawBorder(px + PAD - 2, spY - 14, spW + 2, 100, COL_BORDER_HI);
                ctx.drawTextWithShadow(textRenderer, "§b➕ Create New Block",
                        px + PAD, spY - 11, 0xFFAADDFF);
                ctx.drawTextWithShadow(textRenderer, "§8ID:", px + PAD, spY - 2, COL_GREY);
                ctx.drawTextWithShadow(textRenderer, "§8Name:", px + PAD, spY + 20, COL_GREY);
                ctx.drawTextWithShadow(textRenderer, "§8Texture URL:", px + PAD, spY + 42, COL_GREY);
                fldCreateId.render(ctx, 0, 0, 0);
                fldCreateName.render(ctx, 0, 0, 0);
                fldCreateUrl.render(ctx, 0, 0, 0);
                btnCreateOk.render(ctx, 0, 0, 0);
                btnCreateCancel.render(ctx, 0, 0, 0);
            }
            case RENAME -> {
                ctx.fill(px + PAD - 2, spY + 8, px + PAD + spW, py + ph - 4, 0xEE111828);
                ctx.drawBorder(px + PAD - 2, spY + 8, spW + 2, 66, 0xFF44AA44);
                ctx.drawTextWithShadow(textRenderer, "§aRename: §f" + selectedId,
                        px + PAD, spY + 11, COL_GREEN);
                ctx.drawTextWithShadow(textRenderer, "§8New name:", px + PAD, spY + 22, COL_GREY);
                fldRenameNew.render(ctx, 0, 0, 0);
                btnRenameOk.render(ctx, 0, 0, 0);
                btnRenameCancel.render(ctx, 0, 0, 0);
            }
            case RETEXTURE -> {
                ctx.fill(px + PAD - 2, spY + 8, px + PAD + spW, py + ph - 4, 0xEE181108);
                ctx.drawBorder(px + PAD - 2, spY + 8, spW + 2, 66, 0xFFAA6622);
                ctx.drawTextWithShadow(textRenderer, "§6🖼 New Texture: §f" + selectedId,
                        px + PAD, spY + 11, COL_YELLOW);
                ctx.drawTextWithShadow(textRenderer, "§8Paste image URL:", px + PAD, spY + 22, COL_GREY);
                fldRetextureUrl.render(ctx, 0, 0, 0);
                btnRetextureOk.render(ctx, 0, 0, 0);
                btnRetextureCancel.render(ctx, 0, 0, 0);
            }
        }
    }

    // ── Mouse events ──────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        // Grid click
        if (activePanel == Panel.NONE || activePanel == Panel.PROPERTIES) {
            int gx = px + PAD;
            int gy = py + TOP_H;
            int gh = py + ph - 16;

            if (mx >= gx && mx < gx + GRID_W && my >= gy && my < gh) {
                int col = ((int) mx - gx) / (CELL + GAP);
                int row = ((int) my - gy + scroll) / (CELL + GAP);
                int idx = row * COLS + col;
                if (col < COLS && idx >= 0 && idx < filtered.size()) {
                    selectedId = filtered.get(idx).customId;
                    updateButtonStates();
                    if (activePanel == Panel.PROPERTIES) refreshPropertiesWidgets();
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hx, double vy) {
        int rows       = (int) Math.ceil(filtered.size() / (double) COLS);
        int visibleRows= (ph - TOP_H - 20) / (CELL + GAP);
        int maxScroll  = Math.max(0, rows - visibleRows) * (CELL + GAP);
        scroll = (int) Math.max(0, Math.min(maxScroll, scroll - vy * (CELL + GAP)));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 256) { // ESC
            if (activePanel != Panel.NONE) { closePanel(); return true; }
            close();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    // ── Panel management ──────────────────────────────────────────────────────

    private void openPanel(Panel p) {
        closePanel();
        activePanel = p;
        switch (p) {
            case CREATE -> {
                fldCreateId.setText("");
                fldCreateName.setText("");
                fldCreateUrl.setText("");
                addDrawableChild(fldCreateId);
                addDrawableChild(fldCreateName);
                addDrawableChild(fldCreateUrl);
                addDrawableChild(btnCreateOk);
                addDrawableChild(btnCreateCancel);
                setFocused(fldCreateId);
            }
            case RENAME -> {
                SlotManager.SlotData d = SlotManager.getById(selectedId);
                fldRenameNew.setText(d != null ? d.displayName : "");
                addDrawableChild(fldRenameNew);
                addDrawableChild(btnRenameOk);
                addDrawableChild(btnRenameCancel);
                setFocused(fldRenameNew);
            }
            case RETEXTURE -> {
                fldRetextureUrl.setText("");
                addDrawableChild(fldRetextureUrl);
                addDrawableChild(btnRetextureOk);
                addDrawableChild(btnRetextureCancel);
                setFocused(fldRetextureUrl);
            }
            case PROPERTIES -> {
                addDrawableChild(btnGlowMinus);
                addDrawableChild(btnGlowPlus);
                addDrawableChild(btnHardSoft);
                addDrawableChild(btnHardNormal);
                addDrawableChild(btnHardHard);
                addDrawableChild(btnHardUnbreak);
                addDrawableChild(btnSoundStone);
                addDrawableChild(btnSoundWood);
                addDrawableChild(btnSoundMetal);
                addDrawableChild(btnSoundGlass);
                addDrawableChild(btnSoundGrass);
                addDrawableChild(btnPropClose);
            }
        }
    }

    private void closePanel() {
        activePanel = Panel.NONE;
        remove(fldCreateId); remove(fldCreateName); remove(fldCreateUrl);
        remove(btnCreateOk); remove(btnCreateCancel);
        remove(fldRenameNew); remove(btnRenameOk); remove(btnRenameCancel);
        remove(fldRetextureUrl); remove(btnRetextureOk); remove(btnRetextureCancel);
        remove(btnGlowMinus); remove(btnGlowPlus);
        remove(btnHardSoft); remove(btnHardNormal); remove(btnHardHard); remove(btnHardUnbreak);
        remove(btnSoundStone); remove(btnSoundWood); remove(btnSoundMetal);
        remove(btnSoundGlass); remove(btnSoundGrass);
        remove(btnPropClose);
    }

    private void refreshPropertiesWidgets() {
        // no-op — properties panel reads SlotManager live
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void doCreate() {
        String id   = fldCreateId.getText().trim().toLowerCase().replaceAll("[^a-z0-9_]", "_");
        String name = fldCreateName.getText().trim();
        String url  = fldCreateUrl.getText().trim();
        if (id.isEmpty())   { status("§cEnter an ID!", COL_RED); return; }
        if (name.isEmpty()) { status("§cEnter a name!", COL_RED); return; }
        if (url.isEmpty())  { status("§cPaste a URL!", COL_RED); return; }
        if (SlotManager.hasId(id)) { status("§c'" + id + "' already exists!", COL_RED); return; }
        if (SlotManager.freeSlots() == 0) { status("§cAll 64 slots are full!", COL_RED); return; }
        closePanel();
        status("§eDownloading...", COL_YELLOW);
        assert client != null;
        assert client.player != null;
        client.player.networkHandler.sendChatCommand(
                "customblock createurl " + id + " " + name.replace(" ", "_") + " " + url);
        selectedId = id;
    }

    private void doRename() {
        if (selectedId == null) return;
        String name = fldRenameNew.getText().trim();
        if (name.isEmpty()) { status("§cEnter a name!", COL_RED); return; }
        closePanel();
        assert client != null;
        assert client.player != null;
        client.player.networkHandler.sendChatCommand(
                "customblock rename " + selectedId + " " + name.replace(" ", "_"));
        status("§aRenamed!", COL_GREEN);
        rebuildFiltered();
    }

    private void doRetexture() {
        if (selectedId == null) return;
        String url = fldRetextureUrl.getText().trim();
        if (url.isEmpty()) { status("§cPaste a URL!", COL_RED); return; }
        closePanel();
        status("§eDownloading texture...", COL_YELLOW);
        assert client != null;
        assert client.player != null;
        client.player.networkHandler.sendChatCommand("customblock retexture " + selectedId + " " + url);
    }

    private void doGive() {
        if (selectedId == null) return;
        assert client != null;
        assert client.player != null;
        client.player.networkHandler.sendChatCommand("customblock give " + selectedId);
        status("§a✔ Given to you!", COL_GREEN);
    }

    private void doDelete() {
        if (selectedId == null) return;
        assert client != null;
        assert client.player != null;
        client.player.networkHandler.sendChatCommand("customblock delete " + selectedId);
        status("§aDeleted '" + selectedId + "'", COL_GREEN);
        TextureCache.invalidate(selectedId);
        selectedId = null;
        rebuildFiltered();
        updateButtonStates();
    }

    private void adjustGlow(int delta) {
        if (selectedId == null) return;
        SlotManager.SlotData d = SlotManager.getById(selectedId);
        if (d == null) return;
        int newLevel = Math.max(0, Math.min(15, d.lightLevel + delta));
        assert client != null;
        assert client.player != null;
        client.player.networkHandler.sendChatCommand("customblock setglow " + selectedId + " " + newLevel);
    }

    private void setHard(float val) {
        if (selectedId == null) return;
        assert client != null;
        assert client.player != null;
        client.player.networkHandler.sendChatCommand("customblock sethardness " + selectedId + " " + val);
    }

    private void setSound(String type) {
        if (selectedId == null) return;
        assert client != null;
        assert client.player != null;
        client.player.networkHandler.sendChatCommand("customblock setsound " + selectedId + " " + type);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void rebuildFiltered() {
        filtered.clear();
        String q = search.toLowerCase();
        for (SlotManager.SlotData d : SlotManager.allSlots()) {
            if (q.isEmpty()
                    || d.customId.contains(q)
                    || d.displayName.toLowerCase().contains(q)) {
                filtered.add(d);
            }
        }
    }

    private void updateButtonStates() {
        boolean has = selectedId != null && SlotManager.getById(selectedId) != null;
        btnGive.active       = has;
        btnRename.active     = has;
        btnRetexture.active  = has;
        btnProperties.active = has;
        btnDelete.active     = has;
    }

    private void status(String msg, int color) {
        statusMsg   = msg;
        statusColor = color;
        statusUntil = System.currentTimeMillis() + 3500;
    }

    private static String cap(String s) {
        if (s == null || s.isEmpty()) return "";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private ButtonWidget mkBtn(int x, int y, int w, String label, ButtonWidget.PressAction action) {
        return ButtonWidget.builder(Text.literal(label), action).dimensions(x, y, w, 20).build();
    }

    private TextFieldWidget mkField(int x, int y, int w, String placeholder) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, w, 16, Text.literal(""));
        f.setPlaceholder(Text.literal(placeholder));
        return f;
    }

    private void addAllButtons(ButtonWidget... buttons) {
        for (ButtonWidget b : buttons) addDrawableChild(b);
    }

    @Override
    public boolean shouldPause() { return false; }
}
