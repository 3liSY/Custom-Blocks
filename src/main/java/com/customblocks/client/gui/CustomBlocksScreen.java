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
import java.util.Comparator;
import java.util.List;

@Environment(EnvType.CLIENT)
public class CustomBlocksScreen extends Screen {

    // ── Layout constants ─────────────────────────────────────────────────────
    private static final int COLS   = 5;
    private static final int CELL   = 64;
    private static final int GAP    = 6;
    private static final int GRID_W = COLS * (CELL + GAP) - GAP;
    private static final int RIGHT_W = 220;
    private static final int PAD    = 12;
    private static final int TOP_H  = 38;

    // ── Colours ───────────────────────────────────────────────────────────────
    private static final int C_BG        = 0xF2101018;
    private static final int C_PANEL     = 0xFF181828;
    private static final int C_PANEL2    = 0xFF12121E;
    private static final int C_BORDER    = 0xFF252540;
    private static final int C_BORDER_HI = 0xFF5555EE;
    private static final int C_SELECTED  = 0xFF142814;
    private static final int C_SEL_BDR   = 0xFF44FF44;
    private static final int C_HOVERED   = 0xFF1C1C36;
    private static final int C_GOLD      = 0xFFFFD700;
    private static final int C_WHITE     = 0xFFFFFFFF;
    private static final int C_GREY      = 0xFFAAAAAA;
    private static final int C_DIM       = 0xFF666688;
    private static final int C_GREEN     = 0xFF44EE44;
    private static final int C_RED       = 0xFFFF4444;
    private static final int C_YELLOW    = 0xFFFFCC00;
    private static final int C_BLUE      = 0xFF5599FF;
    private static final int C_ORANGE    = 0xFFFF8800;
    private static final int C_GLOW_CLR  = 0xFFFFEE44;

    // ── State ─────────────────────────────────────────────────────────────────
    private int px, py, pw, ph;
    private String selectedId = null;
    private int scroll = 0;
    private String search = "";
    private int sortMode = 0; // 0=Name, 1=Slot, 2=Glow, 3=Sound
    private boolean sortAsc = true;
    private final List<SlotManager.SlotData> filtered = new ArrayList<>();

    private String statusMsg = "";
    private int statusColor = C_GREEN;
    private long statusUntil = 0;

    private enum Panel { NONE, CREATE, RENAME, RETEXTURE, PROPERTIES }
    private Panel activePanel = Panel.NONE;

    // ── Right-panel action buttons ────────────────────────────────────────────
    private ButtonWidget btnCreate, btnGive, btnGiveStack, btnRename,
                         btnRetexture, btnProperties, btnDelete, btnReloadTex;

    // ── Sort buttons ──────────────────────────────────────────────────────────
    private ButtonWidget btnSortName, btnSortSlot, btnSortGlow, btnSortSound, btnSortDir;

    // ── CREATE sub-panel ──────────────────────────────────────────────────────
    private TextFieldWidget fldCreateId, fldCreateName, fldCreateUrl;
    private ButtonWidget    btnCreateOk, btnCreateCancel;

    // ── RENAME sub-panel ──────────────────────────────────────────────────────
    private TextFieldWidget fldRenameNew;
    private ButtonWidget    btnRenameOk, btnRenameCancel;

    // ── RETEXTURE sub-panel ───────────────────────────────────────────────────
    private TextFieldWidget fldRetextureUrl;
    private ButtonWidget    btnRetextureOk, btnRetextureCancel;

    // ── PROPERTIES sub-panel ─────────────────────────────────────────────────
    private ButtonWidget btnGlowMinus, btnGlowPlus;
    private ButtonWidget btnHardInstant, btnHardSoft, btnHardNormal, btnHardHard, btnHardUnbreak;
    private ButtonWidget btnSoundStone, btnSoundWood, btnSoundMetal, btnSoundGlass,
                         btnSoundGrass, btnSoundSand, btnSoundWool;
    private ButtonWidget btnPropClose;

    // ── Search ────────────────────────────────────────────────────────────────
    private TextFieldWidget fldSearch;

    public CustomBlocksScreen() { super(Text.literal("Custom Blocks")); }

    @Override
    protected void init() {
        activePanel = Panel.NONE;

        pw = GRID_W + RIGHT_W + PAD * 3;
        ph = Math.min(height - 16, 480);
        px = (width  - pw) / 2;
        py = (height - ph) / 2;

        rebuildFiltered();

        // Search bar
        fldSearch = new TextFieldWidget(textRenderer,
                px + PAD, py + PAD + 14, GRID_W - 2, 16, Text.literal(""));
        fldSearch.setPlaceholder(Text.literal("Search blocks..."));
        fldSearch.setChangedListener(s -> { search = s; scroll = 0; rebuildFiltered(); });
        addDrawableChild(fldSearch);

        // Sort buttons (tiny, row below search)
        int sy = py + PAD + 32;
        int sw = (GRID_W - 2 - 3 * 3 - 22) / 4;
        btnSortName  = mkBtn(px + PAD,              sy, sw, "Name",  b -> setSort(0));
        btnSortSlot  = mkBtn(px + PAD + sw + 3,     sy, sw, "Slot",  b -> setSort(1));
        btnSortGlow  = mkBtn(px + PAD + (sw+3)*2,   sy, sw, "Glow",  b -> setSort(2));
        btnSortSound = mkBtn(px + PAD + (sw+3)*3,   sy, sw, "Sound", b -> setSort(3));
        btnSortDir   = mkBtn(px + PAD + (sw+3)*4 + 1, sy, 18, "^",  b -> { sortAsc = !sortAsc; rebuildFiltered(); });
        for (ButtonWidget b : new ButtonWidget[]{btnSortName,btnSortSlot,btnSortGlow,btnSortSound,btnSortDir})
            addDrawableChild(b);

        // Right-panel action buttons
        int bx = px + PAD + GRID_W + PAD;
        int by = py + TOP_H + PAD + 16;
        int bw = RIGHT_W - PAD;
        btnCreate     = mkBtn(bx, by,       bw, "New Block",         b -> openPanel(Panel.CREATE));
        btnGive       = mkBtn(bx, by + 26,  bw/2 - 2, "Give x1",    b -> doGive(1));
        btnGiveStack  = mkBtn(bx + bw/2 + 2, by + 26, bw/2 - 2, "Give x64", b -> doGive(64));
        btnRename     = mkBtn(bx, by + 52,  bw, "Rename",            b -> openPanel(Panel.RENAME));
        btnRetexture  = mkBtn(bx, by + 78,  bw, "Change Texture",    b -> openPanel(Panel.RETEXTURE));
        btnProperties = mkBtn(bx, by + 104, bw, "Properties",        b -> openPanel(Panel.PROPERTIES));
        btnReloadTex  = mkBtn(bx, by + 130, bw, "Reload Textures",   b -> doReloadTextures());
        btnDelete     = mkBtn(bx, by + 156, bw, "Delete",            b -> doDelete());
        for (ButtonWidget b : new ButtonWidget[]{btnCreate,btnGive,btnGiveStack,btnRename,
                btnRetexture,btnProperties,btnReloadTex,btnDelete})
            addDrawableChild(b);
        updateButtonStates();

        // Sub-panel widgets — built now, added to children only when panel opens
        int spY = py + ph - 110;
        fldCreateId   = mkField(px + PAD, spY,      GRID_W - 2, "id (e.g. discord) — no spaces");
        fldCreateName = mkField(px + PAD, spY + 22, GRID_W - 2, "Display Name");
        fldCreateUrl  = mkField(px + PAD, spY + 44, GRID_W - 2, "https://image-url.png");
        fldCreateUrl.setMaxLength(512);
        btnCreateOk     = mkBtn(px + PAD,      spY + 66, 80, "Create", b -> doCreate());
        btnCreateCancel = mkBtn(px + PAD + 84, spY + 66, 60, "Cancel", b -> closePanel());

        fldRenameNew    = mkField(px + PAD, spY + 22, GRID_W - 2, "New display name");
        btnRenameOk     = mkBtn(px + PAD,      spY + 44, 80, "Rename", b -> doRename());
        btnRenameCancel = mkBtn(px + PAD + 84, spY + 44, 60, "Cancel", b -> closePanel());

        fldRetextureUrl    = mkField(px + PAD, spY + 22, GRID_W - 2, "https://new-image.png");
        fldRetextureUrl.setMaxLength(512);
        btnRetextureOk     = mkBtn(px + PAD,      spY + 44, 80, "Apply",  b -> doRetexture());
        btnRetextureCancel = mkBtn(px + PAD + 84, spY + 44, 60, "Cancel", b -> closePanel());

        // Properties panel buttons (shown in right panel area)
        int propY = by + 194;
        btnGlowMinus   = mkBtn(bx,      propY + 18, 20, "-", b -> adjustGlow(-1));
        btnGlowPlus    = mkBtn(bx + 140, propY + 18, 20, "+", b -> adjustGlow(+1));
        int hw = (bw - 8) / 5;
        btnHardInstant = mkBtn(bx,               propY + 44, hw, "0",    b -> setHard(0f));
        btnHardSoft    = mkBtn(bx + (hw+2),      propY + 44, hw, "Soft", b -> setHard(0.5f));
        btnHardNormal  = mkBtn(bx + (hw+2)*2,    propY + 44, hw, "Norm", b -> setHard(1.5f));
        btnHardHard    = mkBtn(bx + (hw+2)*3,    propY + 44, hw, "Hard", b -> setHard(5.0f));
        btnHardUnbreak = mkBtn(bx + (hw+2)*4,    propY + 44, hw, "Max",  b -> setHard(-1f));
        int sw2 = (bw - 12) / 7;
        btnSoundStone = mkBtn(bx,              propY + 70, sw2, "Stn",  b -> setSound("stone"));
        btnSoundWood  = mkBtn(bx + (sw2+2),   propY + 70, sw2, "Wd",   b -> setSound("wood"));
        btnSoundMetal = mkBtn(bx + (sw2+2)*2, propY + 70, sw2, "Mtl",  b -> setSound("metal"));
        btnSoundGlass = mkBtn(bx + (sw2+2)*3, propY + 70, sw2, "Gls",  b -> setSound("glass"));
        btnSoundGrass = mkBtn(bx + (sw2+2)*4, propY + 70, sw2, "Grs",  b -> setSound("grass"));
        btnSoundSand  = mkBtn(bx + (sw2+2)*5, propY + 70, sw2, "Snd",  b -> setSound("sand"));
        btnSoundWool  = mkBtn(bx + (sw2+2)*6, propY + 70, sw2, "Wl",   b -> setSound("wool"));
        btnPropClose  = mkBtn(bx, propY + 96, bw, "Done", b -> closePanel());
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx, mx, my, delta);

        // Outer glow border
        ctx.fill(px - 3, py - 3, px + pw + 3, py + ph + 3, 0x44_5555EE);
        ctx.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, C_BORDER_HI);
        ctx.fill(px, py, px + pw, py + ph, C_BG);

        // Title bar
        ctx.fillGradient(px, py, px + pw, py + 14, 0xFF1A1A40, 0xFF0E0E1E);
        ctx.drawCenteredTextWithShadow(textRenderer, "Custom Blocks", px + pw / 2, py + 3, C_GOLD);

        // Divider between grid and right panel
        int divX = px + PAD + GRID_W + PAD;
        ctx.fill(divX - 1, py + 14, divX, py + ph - 4, C_BORDER);

        // Grid column header
        ctx.drawTextWithShadow(textRenderer, "Blocks", px + PAD, py + PAD + 3, C_GREY);

        // Sort mode indicator
        String[] sortNames = {"Name", "Slot", "Glow", "Sound"};
        ctx.drawTextWithShadow(textRenderer,
            "Sort: " + sortNames[sortMode] + (sortAsc ? " A" : " Z"),
            px + PAD + GRID_W - 60, py + PAD + 3, C_DIM);

        // Slot usage bar
        int used = SlotManager.usedSlots();
        int max  = SlotManager.MAX_SLOTS;
        float pct = used / (float) max;
        int barW = GRID_W - 2;
        int barX = px + PAD;
        int barY = py + ph - 8;
        ctx.fill(barX, barY, barX + barW, barY + 4, 0xFF222233);
        int fillW = (int)(barW * pct);
        int barColor = pct < 0.6f ? 0xFF44CC44 : pct < 0.9f ? C_YELLOW : C_RED;
        ctx.fill(barX, barY, barX + fillW, barY + 4, barColor);
        ctx.drawTextWithShadow(textRenderer,
            used + " / " + max + " slots", barX, barY - 9, C_DIM);

        drawGrid(ctx, mx, my);
        drawRightPanel(ctx);

        // Status message
        if (System.currentTimeMillis() < statusUntil)
            ctx.drawCenteredTextWithShadow(textRenderer, statusMsg, px + pw / 2, py + ph + 6, statusColor);

        drawActivePanel(ctx);
        super.render(ctx, mx, my, delta);
    }

    private void drawGrid(DrawContext ctx, int mx, int my) {
        int gx = px + PAD;
        int gy = py + TOP_H + 20;   // extra 20 for sort buttons
        int gh = py + ph - 20;

        ctx.enableScissor(gx, gy, gx + GRID_W, gh);

        for (int i = 0; i < filtered.size(); i++) {
            int col = i % COLS;
            int row = i / COLS;
            int cx  = gx + col * (CELL + GAP);
            int cy  = gy + row * (CELL + GAP) - scroll;

            if (cy + CELL < gy || cy > gh) continue;

            SlotManager.SlotData data = filtered.get(i);
            boolean sel = data.customId.equals(selectedId);
            boolean hov = mx >= cx && mx < cx + CELL && my >= cy && my < cy + CELL;

            // Cell background
            ctx.fill(cx, cy, cx + CELL, cy + CELL, sel ? C_SELECTED : (hov ? C_HOVERED : C_PANEL));
            // Border — thicker for selected
            if (sel) {
                ctx.fill(cx-1, cy-1, cx+CELL+1, cy, C_SEL_BDR);
                ctx.fill(cx-1, cy+CELL, cx+CELL+1, cy+CELL+1, C_SEL_BDR);
                ctx.fill(cx-1, cy, cx, cy+CELL, C_SEL_BDR);
                ctx.fill(cx+CELL, cy, cx+CELL+1, cy+CELL, C_SEL_BDR);
            } else {
                ctx.drawBorder(cx, cy, CELL, CELL, hov ? C_BORDER_HI : C_BORDER);
            }

            // Texture — draw inside cell with small padding
            int pad = 4;
            int tw  = CELL - pad * 2 - 12; // leave 12px for label
            TextureCache.TexInfo tex = TextureCache.getOrLoad(data.customId, data.texture);
            ctx.drawTexture(tex.id(), cx + pad, cy + pad, 0.0f, 0.0f, tw, tw, tex.width(), tex.height());

            // Glow badge (top-right corner)
            if (data.lightLevel > 0) {
                ctx.fill(cx + CELL - 14, cy, cx + CELL, cy + 10, 0xCC_FFEE00);
                ctx.drawTextWithShadow(textRenderer,
                    String.valueOf(data.lightLevel), cx + CELL - 12, cy + 1, 0xFF000000);
            }

            // Hardness badge (top-left corner)
            if (data.hardness < 0) {
                ctx.fill(cx, cy, cx + 10, cy + 10, 0xCC_FF4444);
                ctx.drawTextWithShadow(textRenderer, "U", cx + 1, cy + 1, C_WHITE);
            }

            // Name label at bottom of cell
            String label = data.displayName.length() > 8
                    ? data.displayName.substring(0, 7) + "."
                    : data.displayName;
            ctx.drawCenteredTextWithShadow(textRenderer, label,
                cx + CELL / 2, cy + CELL - 10, sel ? C_GREEN : C_WHITE);
        }

        ctx.disableScissor();

        if (filtered.isEmpty()) {
            String msg = search.isEmpty()
                ? "No blocks yet — press New Block!"
                : "No match for \"" + search + "\"";
            int gy2 = py + TOP_H + 20;
            ctx.drawCenteredTextWithShadow(textRenderer, msg, gx + GRID_W / 2, gy2 + 60, C_GREY);
        }
    }

    private void drawRightPanel(DrawContext ctx) {
        int rx = px + PAD + GRID_W + PAD + 2;
        int ry = py + PAD;
        int rw = RIGHT_W - PAD - 4;

        // Header
        ctx.fill(rx - 2, ry, rx + rw + 2, ry + 12, 0xFF0E0E28);
        ctx.drawCenteredTextWithShadow(textRenderer, "Selected Block", rx + rw/2, ry + 2, C_GOLD);

        SlotManager.SlotData data = selectedId != null ? SlotManager.getById(selectedId) : null;
        if (data == null) {
            ctx.drawCenteredTextWithShadow(textRenderer, "-- none selected --", rx + rw/2, ry + 30, C_DIM);
            return;
        }

        // Large preview
        int pvSize = 72;
        int pvX = rx + (rw - pvSize) / 2;
        int pvY = ry + 16;
        ctx.fill(pvX - 2, pvY - 2, pvX + pvSize + 2, pvY + pvSize + 2, C_BORDER);
        ctx.fill(pvX - 1, pvY - 1, pvX + pvSize + 1, pvY + pvSize + 1, C_PANEL2);
        TextureCache.TexInfo tex = TextureCache.getOrLoad(data.customId, data.texture);
        ctx.drawTexture(tex.id(), pvX, pvY, 0.0f, 0.0f, pvSize, pvSize, tex.width(), tex.height());

        // Block info
        int infoY = pvY + pvSize + 6;
        ctx.drawCenteredTextWithShadow(textRenderer, data.displayName, rx + rw/2, infoY,      C_WHITE);
        ctx.drawCenteredTextWithShadow(textRenderer, "id: " + data.customId, rx + rw/2, infoY + 10, C_GREY);
        ctx.drawCenteredTextWithShadow(textRenderer, "slot " + data.index, rx + rw/2, infoY + 20, C_DIM);

        // Property tags
        int tagY = infoY + 32;
        ctx.fill(rx, tagY, rx + rw, tagY + 10, 0x33_FFFFFF);

        // Glow
        String glowStr = data.lightLevel > 0 ? "Glow: " + data.lightLevel : "Glow: off";
        int glowColor  = data.lightLevel > 0 ? C_GLOW_CLR : C_DIM;
        ctx.drawTextWithShadow(textRenderer, glowStr, rx + 2, tagY + 1, glowColor);

        // Hardness
        String hardStr = data.hardness < 0 ? "Indestructible"
                       : data.hardness == 0 ? "Instant break"
                       : data.hardness <= 0.5f ? "Soft (" + data.hardness + ")"
                       : data.hardness <= 2.5f ? "Normal (" + data.hardness + ")"
                       : "Hard (" + data.hardness + ")";
        ctx.drawTextWithShadow(textRenderer, hardStr, rx + 2, tagY + 12, C_BLUE);

        // Sound
        ctx.drawTextWithShadow(textRenderer, "Sound: " + cap(data.soundType), rx + 2, tagY + 22, C_ORANGE);

        // Properties panel overlay drawn on top of action buttons area
        if (activePanel == Panel.PROPERTIES) drawPropertiesPanel(ctx, rx, ry, rw, data);
    }

    private void drawPropertiesPanel(DrawContext ctx, int rx, int ry, int rw, SlotManager.SlotData data) {
        int bx = rx - 2;
        int by = ry + TOP_H + PAD + 16 + 194;
        int bw = rw + 4;
        ctx.fill(bx, by - 16, bx + bw, by + 110, 0xEE_0D0D22);
        ctx.fill(bx, by - 16, bx + bw, by - 15, C_BORDER_HI);
        ctx.fill(bx, by + 110, bx + bw, by + 111, C_BORDER_HI);
        ctx.fill(bx, by - 16, bx + 1, by + 111, C_BORDER_HI);
        ctx.fill(bx + bw - 1, by - 16, bx + bw, by + 111, C_BORDER_HI);
        ctx.drawCenteredTextWithShadow(textRenderer, "Properties", rx + rw/2, by - 13, 0xFFAADDFF);
        ctx.drawTextWithShadow(textRenderer, "Glow: " + data.lightLevel + " / 15", rx, by + 1, C_GLOW_CLR);
        ctx.drawTextWithShadow(textRenderer, "Hardness:", rx, by + 32, C_BLUE);
        ctx.drawTextWithShadow(textRenderer, "Sound:", rx, by + 58, C_ORANGE);
    }

    private void drawActivePanel(DrawContext ctx) {
        if (activePanel == Panel.NONE || activePanel == Panel.PROPERTIES) return;
        int spY = py + ph - 110;
        int spW = GRID_W + 2;

        switch (activePanel) {
            case CREATE -> {
                ctx.fill(px + PAD - 2, spY - 16, px + PAD + spW, py + ph - 4, 0xEE_0D0D22);
                ctx.fill(px + PAD - 2, spY - 16, px + PAD + spW, spY - 15, C_BORDER_HI);
                ctx.fill(px + PAD - 2, py + ph - 5, px + PAD + spW, py + ph - 4, C_BORDER_HI);
                ctx.drawTextWithShadow(textRenderer, "Create New Block", px + PAD, spY - 13, 0xFFAADDFF);
                ctx.drawTextWithShadow(textRenderer, "ID:",          px + PAD, spY - 2,  C_GREY);
                ctx.drawTextWithShadow(textRenderer, "Name:",        px + PAD, spY + 20, C_GREY);
                ctx.drawTextWithShadow(textRenderer, "Texture URL:", px + PAD, spY + 42, C_GREY);
            }
            case RENAME -> {
                ctx.fill(px + PAD - 2, spY + 8, px + PAD + spW, py + ph - 4, 0xEE_0D1A0D);
                ctx.fill(px + PAD - 2, spY + 8, px + PAD + spW, spY + 9, 0xFF44AA44);
                ctx.drawTextWithShadow(textRenderer, "Rename: " + selectedId, px + PAD, spY + 11, C_GREEN);
                ctx.drawTextWithShadow(textRenderer, "New name:", px + PAD, spY + 22, C_GREY);
            }
            case RETEXTURE -> {
                ctx.fill(px + PAD - 2, spY + 8, px + PAD + spW, py + ph - 4, 0xEE_1A120D);
                ctx.fill(px + PAD - 2, spY + 8, px + PAD + spW, spY + 9, C_ORANGE);
                ctx.drawTextWithShadow(textRenderer, "Change Texture: " + selectedId, px + PAD, spY + 11, C_YELLOW);
                ctx.drawTextWithShadow(textRenderer, "Image URL:", px + PAD, spY + 22, C_GREY);
            }
        }
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (activePanel == Panel.NONE || activePanel == Panel.PROPERTIES) {
            int gx = px + PAD;
            int gy = py + TOP_H + 20;
            int gh = py + ph - 20;
            if (mx >= gx && mx < gx + GRID_W && my >= gy && my < gh) {
                int col = ((int)mx - gx) / (CELL + GAP);
                int row = ((int)my - gy + scroll) / (CELL + GAP);
                int idx = row * COLS + col;
                if (col < COLS && idx >= 0 && idx < filtered.size()) {
                    String newId = filtered.get(idx).customId;
                    if (newId.equals(selectedId) && btn == 0) {
                        // Double-click-like: give on second click when already selected
                    }
                    selectedId = newId;
                    if (activePanel == Panel.PROPERTIES) closePanel();
                    updateButtonStates();
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hx, double vy) {
        int rows = (int)Math.ceil(filtered.size() / (double)COLS);
        int gy = py + TOP_H + 20;
        int gh = py + ph - 20;
        int vis = (gh - gy) / (CELL + GAP);
        int maxS = Math.max(0, rows - vis) * (CELL + GAP);
        scroll = (int)Math.max(0, Math.min(maxS, scroll - vy * (CELL + GAP)));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 256) {
            if (activePanel != Panel.NONE) { closePanel(); return true; }
            close(); return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    // ── Panel management ──────────────────────────────────────────────────────

    private void openPanel(Panel p) {
        closePanel();
        activePanel = p;
        switch (p) {
            case CREATE -> {
                fldCreateId.setText(""); fldCreateName.setText(""); fldCreateUrl.setText("");
                addDrawableChild(fldCreateId); addDrawableChild(fldCreateName);
                addDrawableChild(fldCreateUrl); addDrawableChild(btnCreateOk); addDrawableChild(btnCreateCancel);
                setFocused(fldCreateId);
            }
            case RENAME -> {
                SlotManager.SlotData d = SlotManager.getById(selectedId);
                fldRenameNew.setText(d != null ? d.displayName : "");
                addDrawableChild(fldRenameNew); addDrawableChild(btnRenameOk); addDrawableChild(btnRenameCancel);
                setFocused(fldRenameNew);
            }
            case RETEXTURE -> {
                fldRetextureUrl.setText("");
                addDrawableChild(fldRetextureUrl); addDrawableChild(btnRetextureOk); addDrawableChild(btnRetextureCancel);
                setFocused(fldRetextureUrl);
            }
            case PROPERTIES -> {
                for (ButtonWidget b : new ButtonWidget[]{
                        btnGlowMinus, btnGlowPlus,
                        btnHardInstant, btnHardSoft, btnHardNormal, btnHardHard, btnHardUnbreak,
                        btnSoundStone, btnSoundWood, btnSoundMetal, btnSoundGlass,
                        btnSoundGrass, btnSoundSand, btnSoundWool, btnPropClose})
                    addDrawableChild(b);
            }
        }
    }

    private void closePanel() {
        activePanel = Panel.NONE;
        remove(fldCreateId);     remove(fldCreateName);    remove(fldCreateUrl);
        remove(btnCreateOk);     remove(btnCreateCancel);
        remove(fldRenameNew);    remove(btnRenameOk);      remove(btnRenameCancel);
        remove(fldRetextureUrl); remove(btnRetextureOk);   remove(btnRetextureCancel);
        remove(btnGlowMinus);    remove(btnGlowPlus);
        remove(btnHardInstant);  remove(btnHardSoft);      remove(btnHardNormal);
        remove(btnHardHard);     remove(btnHardUnbreak);
        remove(btnSoundStone);   remove(btnSoundWood);     remove(btnSoundMetal);
        remove(btnSoundGlass);   remove(btnSoundGrass);    remove(btnSoundSand);
        remove(btnSoundWool);    remove(btnPropClose);
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void doCreate() {
        String id   = fldCreateId.getText().trim().toLowerCase().replaceAll("[^a-z0-9_]", "_");
        String name = fldCreateName.getText().trim();
        String url  = fldCreateUrl.getText().trim();
        if (id.isEmpty())   { status("Enter an ID!", C_RED); return; }
        if (name.isEmpty()) { status("Enter a name!", C_RED); return; }
        if (url.isEmpty())  { status("Paste a texture URL!", C_RED); return; }
        if (SlotManager.hasId(id))      { status("'" + id + "' already exists!", C_RED); return; }
        if (SlotManager.freeSlots() == 0) { status("All 64 slots are full!", C_RED); return; }
        closePanel();
        status("Downloading...", C_YELLOW);
        client.player.networkHandler.sendChatCommand("customblock createurl " + id + " " + name.replace(" ", "_") + " " + url);
    }

    private void doRename() {
        if (selectedId == null) return;
        String name = fldRenameNew.getText().trim();
        if (name.isEmpty()) { status("Enter a name!", C_RED); return; }
        closePanel();
        client.player.networkHandler.sendChatCommand("customblock rename " + selectedId + " " + name.replace(" ", "_"));
        status("Renamed!", C_GREEN);
        rebuildFiltered();
    }

    private void doRetexture() {
        if (selectedId == null) return;
        String url = fldRetextureUrl.getText().trim();
        if (url.isEmpty()) { status("Paste a URL!", C_RED); return; }
        closePanel();
        status("Downloading texture...", C_YELLOW);
        client.player.networkHandler.sendChatCommand("customblock retexture " + selectedId + " " + url);
    }

    private void doGive(int amount) {
        if (selectedId == null) return;
        if (amount == 1)
            client.player.networkHandler.sendChatCommand("customblock give " + selectedId);
        else
            client.player.networkHandler.sendChatCommand("customblock give " + selectedId + " " + amount);
        status("Given " + amount + "x '" + selectedId + "'!", C_GREEN);
    }

    private void doDelete() {
        if (selectedId == null) return;
        client.player.networkHandler.sendChatCommand("customblock delete " + selectedId);
        status("Deleted '" + selectedId + "'", C_RED);
        TextureCache.invalidate(selectedId);
        selectedId = null;
        rebuildFiltered();
        updateButtonStates();
    }

    private void doReloadTextures() {
        TextureCache.invalidateAll();
        status("Textures cleared — reloading...", C_YELLOW);
    }

    private void adjustGlow(int delta) {
        if (selectedId == null) return;
        SlotManager.SlotData d = SlotManager.getById(selectedId);
        if (d == null) return;
        int nv = Math.max(0, Math.min(15, d.lightLevel + delta));
        client.player.networkHandler.sendChatCommand("customblock setglow " + selectedId + " " + nv);
    }

    private void setHard(float val) {
        if (selectedId == null) return;
        client.player.networkHandler.sendChatCommand("customblock sethardness " + selectedId + " " + val);
    }

    private void setSound(String type) {
        if (selectedId == null) return;
        client.player.networkHandler.sendChatCommand("customblock setsound " + selectedId + " " + type);
    }

    private void setSort(int mode) {
        if (sortMode == mode) sortAsc = !sortAsc;
        else { sortMode = mode; sortAsc = true; }
        rebuildFiltered();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void rebuildFiltered() {
        filtered.clear();
        String q = search.toLowerCase();
        for (SlotManager.SlotData d : SlotManager.allSlots()) {
            if (q.isEmpty() || d.customId.contains(q) || d.displayName.toLowerCase().contains(q))
                filtered.add(d);
        }
        Comparator<SlotManager.SlotData> cmp = switch (sortMode) {
            case 1 -> Comparator.comparingInt(d -> d.index);
            case 2 -> Comparator.comparingInt((SlotManager.SlotData d) -> d.lightLevel).reversed();
            case 3 -> Comparator.comparing(d -> d.soundType);
            default -> Comparator.comparing(d -> d.displayName.toLowerCase());
        };
        if (!sortAsc) cmp = cmp.reversed();
        filtered.sort(cmp);
    }

    private void updateButtonStates() {
        boolean has = selectedId != null && SlotManager.getById(selectedId) != null;
        btnGive.active = btnGiveStack.active = btnRename.active =
        btnRetexture.active = btnProperties.active = btnDelete.active = has;
    }

    private void status(String msg, int color) {
        statusMsg = msg; statusColor = color; statusUntil = System.currentTimeMillis() + 3500;
    }

    private static String cap(String s) {
        return (s == null || s.isEmpty()) ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private ButtonWidget mkBtn(int x, int y, int w, String label, ButtonWidget.PressAction a) {
        return ButtonWidget.builder(Text.literal(label), a).dimensions(x, y, w, 20).build();
    }

    private TextFieldWidget mkField(int x, int y, int w, String placeholder) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, w, 16, Text.literal(""));
        f.setPlaceholder(Text.literal(placeholder));
        return f;
    }

    @Override public boolean shouldPause() { return false; }
}
