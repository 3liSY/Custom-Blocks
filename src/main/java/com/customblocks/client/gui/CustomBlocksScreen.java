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

    private static final int COLS   = 4;
    private static final int CELL   = 58;
    private static final int GAP    = 4;
    private static final int GRID_W = COLS * (CELL + GAP) - GAP;
    private static final int RIGHT_W = 180;
    private static final int PAD    = 10;
    private static final int TOP_H  = 34;

    private static final int C_BG        = 0xF0101018;
    private static final int C_PANEL     = 0xFF181828;
    private static final int C_BORDER    = 0xFF2A2A50;
    private static final int C_BORDER_HI = 0xFF5555CC;
    private static final int C_SELECTED  = 0xFF1A4020;
    private static final int C_SEL_BDR   = 0xFF44EE44;
    private static final int C_HOVERED   = 0xFF1A1A40;
    private static final int C_GOLD      = 0xFFFFD700;
    private static final int C_WHITE     = 0xFFFFFFFF;
    private static final int C_GREY      = 0xFFAAAAAA;
    private static final int C_DIM       = 0xFF777777;
    private static final int C_GREEN     = 0xFF55FF55;
    private static final int C_RED       = 0xFFFF5555;
    private static final int C_YELLOW    = 0xFFFFAA00;

    private int px, py, pw, ph;

    private String selectedId = null;
    private int    scroll     = 0;
    private String search     = "";
    private final List<SlotManager.SlotData> filtered = new ArrayList<>();

    private String statusMsg   = "";
    private int    statusColor = C_GREEN;
    private long   statusUntil = 0;

    private enum Panel { NONE, CREATE, RENAME, RETEXTURE, PROPERTIES }
    private Panel activePanel = Panel.NONE;

    private ButtonWidget btnCreate, btnGive, btnRename, btnRetexture, btnDelete, btnProperties;

    private TextFieldWidget fldCreateId, fldCreateName, fldCreateUrl;
    private ButtonWidget    btnCreateOk, btnCreateCancel;

    private TextFieldWidget fldRenameNew;
    private ButtonWidget    btnRenameOk, btnRenameCancel;

    private TextFieldWidget fldRetextureUrl;
    private ButtonWidget    btnRetextureOk, btnRetextureCancel;

    private ButtonWidget btnGlowMinus, btnGlowPlus;
    private ButtonWidget btnHardSoft, btnHardNormal, btnHardHard, btnHardUnbreak;
    private ButtonWidget btnSoundStone, btnSoundWood, btnSoundMetal, btnSoundGlass, btnSoundGrass;
    private ButtonWidget btnPropClose;

    private TextFieldWidget fldSearch;

    public CustomBlocksScreen() {
        super(Text.literal("Custom Blocks"));
    }

    @Override
    protected void init() {
        // Reset panel state on init (called on open AND on window resize)
        activePanel = Panel.NONE;
        pw = GRID_W + RIGHT_W + PAD * 3;
        ph = Math.min(height - 20, 430);
        px = (width  - pw) / 2;
        py = (height - ph) / 2;

        rebuildFiltered();

        fldSearch = new TextFieldWidget(textRenderer,
                px + PAD, py + PAD + 14, GRID_W - 2, 16, Text.literal(""));
        fldSearch.setPlaceholder(Text.literal("Search blocks..."));
        fldSearch.setChangedListener(s -> { search = s; scroll = 0; rebuildFiltered(); });
        addDrawableChild(fldSearch);

        int bx = px + PAD + GRID_W + PAD;
        int by = py + TOP_H + PAD;
        int bw = RIGHT_W - PAD;
        btnCreate     = mkBtn(bx, by,       bw, "New Block",      b -> openPanel(Panel.CREATE));
        btnGive       = mkBtn(bx, by + 26,  bw, "Give to Me",     b -> doGive());
        btnRename     = mkBtn(bx, by + 52,  bw, "Rename",         b -> openPanel(Panel.RENAME));
        btnRetexture  = mkBtn(bx, by + 78,  bw, "Change Texture", b -> openPanel(Panel.RETEXTURE));
        btnProperties = mkBtn(bx, by + 104, bw, "Properties",     b -> openPanel(Panel.PROPERTIES));
        btnDelete     = mkBtn(bx, by + 130, bw, "Delete",         b -> doDelete());
        for (ButtonWidget b : new ButtonWidget[]{btnCreate,btnGive,btnRename,btnRetexture,btnProperties,btnDelete})
            addDrawableChild(b);
        updateButtonStates();

        int spY = py + ph - 100;
        fldCreateId   = mkField(px + PAD, spY,      GRID_W - 2, "id (e.g. discord)");
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

        int propY = by + 168;
        int bwH = (RIGHT_W - PAD - 4) / 4 - 1;
        btnGlowMinus  = mkBtn(bx,       propY + 22, 22, "-", b -> adjustGlow(-1));
        btnGlowPlus   = mkBtn(bx + 118, propY + 22, 22, "+", b -> adjustGlow(+1));
        btnHardSoft   = mkBtn(bx,              propY + 52, bwH, "Soft",   b -> setHard(0.3f));
        btnHardNormal = mkBtn(bx + bwH + 2,    propY + 52, bwH, "Normal", b -> setHard(1.5f));
        btnHardHard   = mkBtn(bx + (bwH+2)*2,  propY + 52, bwH, "Hard",  b -> setHard(5.0f));
        btnHardUnbreak= mkBtn(bx + (bwH+2)*3,  propY + 52, bwH, "Max",   b -> setHard(-1f));
        int bwS = (RIGHT_W - PAD - 8) / 5;
        btnSoundStone = mkBtn(bx,              propY + 78, bwS, "Stone", b -> setSound("stone"));
        btnSoundWood  = mkBtn(bx + (bwS+2),   propY + 78, bwS, "Wood",  b -> setSound("wood"));
        btnSoundMetal = mkBtn(bx + (bwS+2)*2, propY + 78, bwS, "Metal", b -> setSound("metal"));
        btnSoundGlass = mkBtn(bx + (bwS+2)*3, propY + 78, bwS, "Glass", b -> setSound("glass"));
        btnSoundGrass = mkBtn(bx + (bwS+2)*4, propY + 78, bwS, "Grass", b -> setSound("grass"));
        btnPropClose  = mkBtn(bx, propY + 104, RIGHT_W - PAD, "Done", b -> closePanel());
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx, mx, my, delta);

        ctx.fill(px - 2, py - 2, px + pw + 2, py + ph + 2, C_BORDER);
        ctx.fill(px, py, px + pw, py + ph, C_BG);

        ctx.fillGradient(px, py, px + pw, py + 12, 0xFF1A1A3A, 0xFF101018);
        ctx.drawCenteredTextWithShadow(textRenderer, "Custom Blocks", px + pw / 2, py + 2, C_GOLD);

        int divX = px + PAD + GRID_W + PAD;
        ctx.fill(divX - 1, py + 12, divX, py + ph - 4, C_BORDER);

        ctx.drawTextWithShadow(textRenderer,
                SlotManager.usedSlots() + " / " + SlotManager.MAX_SLOTS + " slots",
                px + PAD, py + ph - 11, C_DIM);

        drawGrid(ctx, mx, my);
        drawRightPanel(ctx);

        if (System.currentTimeMillis() < statusUntil)
            ctx.drawCenteredTextWithShadow(textRenderer, statusMsg, px + pw / 2, py + ph + 4, statusColor);

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
            boolean sel = data.customId.equals(selectedId);
            boolean hov = mx >= cx && mx < cx + CELL && my >= cy && my < cy + CELL;

            ctx.fill(cx, cy, cx + CELL, cy + CELL, sel ? C_SELECTED : (hov ? C_HOVERED : C_PANEL));
            ctx.drawBorder(cx, cy, CELL, CELL, sel ? C_SEL_BDR : (hov ? C_BORDER_HI : C_BORDER));

            // Texture — draw full image scaled to fill the cell
            int pad = 4;
            int tw  = CELL - pad * 2 - 10;
            TextureCache.TexInfo tex = TextureCache.getOrLoad(data.customId, data.texture);
            // Use tw,tw as both region AND sheet size so the whole texture is drawn scaled
            ctx.drawTexture(tex.id(), cx + pad, cy + pad, 0.0f, 0.0f, tw, tw, tex.width(), tex.height());

            if (data.lightLevel > 0)
                ctx.drawTextWithShadow(textRenderer, "*", cx + CELL - 10, cy + 2, 0xFFFFDD00);

            String label = data.displayName.length() > 7
                    ? data.displayName.substring(0, 6) + "."
                    : data.displayName;
            ctx.drawCenteredTextWithShadow(textRenderer, label, cx + CELL / 2, cy + CELL - 10, C_WHITE);
        }

        ctx.disableScissor();

        if (filtered.isEmpty()) {
            String msg = search.isEmpty() ? "No blocks yet. Click New Block" : "No match for \"" + search + "\"";
            ctx.drawCenteredTextWithShadow(textRenderer, msg, gx + GRID_W / 2, gy + 50, C_GREY);
        }
    }

    private void drawRightPanel(DrawContext ctx) {
        int rx = px + PAD + GRID_W + PAD + 2;
        int ry = py + TOP_H + PAD;
        int rw = RIGHT_W - PAD - 4;

        ctx.drawCenteredTextWithShadow(textRenderer, "Selected Block", rx + rw / 2, ry - 10, C_GOLD);

        SlotManager.SlotData data = selectedId != null ? SlotManager.getById(selectedId) : null;
        if (data == null) {
            ctx.drawCenteredTextWithShadow(textRenderer, "-- select a block --", rx + rw / 2, ry + 40, C_GREY);
            return;
        }

        int previewSize = 60;
        int pvX = rx + (rw - previewSize) / 2;
        int pvY = ry + 30;
        ctx.fill(pvX - 2, pvY - 2, pvX + previewSize + 2, pvY + previewSize + 2, C_BORDER);
        TextureCache.TexInfo tex = TextureCache.getOrLoad(data.customId, data.texture);
        ctx.drawTexture(tex.id(), pvX, pvY, 0.0f, 0.0f, previewSize, previewSize, tex.width(), tex.height());

        int infoY = pvY + previewSize + 8;
        ctx.drawCenteredTextWithShadow(textRenderer, data.displayName,        rx + rw / 2, infoY,      C_WHITE);
        ctx.drawCenteredTextWithShadow(textRenderer, "ID: " + data.customId,  rx + rw / 2, infoY + 11, C_GREY);
        ctx.drawCenteredTextWithShadow(textRenderer, "Slot " + data.index,    rx + rw / 2, infoY + 22, C_DIM);

        int tagY = infoY + 36;
        if (data.lightLevel > 0) {
            ctx.fill(rx, tagY, rx + rw, tagY + 12, 0x55FFDD00);
            ctx.drawCenteredTextWithShadow(textRenderer, "Glow: " + data.lightLevel, rx + rw / 2, tagY + 2, 0xFFFFDD44);
            tagY += 16;
        }
        String hardLabel = data.hardness < 0 ? "Unbreakable" : data.hardness == 0 ? "Instant" :
                data.hardness <= 0.5f ? "Soft" : data.hardness <= 2.5f ? "Normal" : "Hard";
        ctx.drawCenteredTextWithShadow(textRenderer, hardLabel + "  |  " + cap(data.soundType), rx + rw / 2, tagY + 2, C_DIM);

        if (activePanel == Panel.PROPERTIES) drawPropertiesPanel(ctx, rx, ry, rw);
    }

    private void drawPropertiesPanel(DrawContext ctx, int rx, int ry, int rw) {
        if (selectedId == null) return;
        SlotManager.SlotData data = SlotManager.getById(selectedId);
        if (data == null) return;
        int propY = ry + 168;
        ctx.fill(rx - 2, propY - 14, rx + rw + 2, propY + 120, 0xEE111128);
        ctx.drawBorder(rx - 2, propY - 14, rw + 4, 134, C_BORDER_HI);
        ctx.drawCenteredTextWithShadow(textRenderer, "Properties", rx + rw / 2, propY - 11, 0xFFAADDFF);
        ctx.drawTextWithShadow(textRenderer, "Glow: " + data.lightLevel, rx, propY + 6, C_GOLD);
        ctx.drawTextWithShadow(textRenderer, "Hardness:", rx, propY + 40, C_GOLD);
        ctx.drawTextWithShadow(textRenderer, "Sound:", rx, propY + 66, C_GOLD);
    }

    private void drawActivePanel(DrawContext ctx) {
        if (activePanel == Panel.NONE || activePanel == Panel.PROPERTIES) return;
        int spY = py + ph - 100;
        int spW = GRID_W + 2;
        switch (activePanel) {
            case CREATE -> {
                ctx.fill(px + PAD - 2, spY - 14, px + PAD + spW, py + ph - 4, 0xEE111128);
                ctx.drawBorder(px + PAD - 2, spY - 14, spW + 2, 100, C_BORDER_HI);
                ctx.drawTextWithShadow(textRenderer, "Create New Block", px + PAD, spY - 11, 0xFFAADDFF);
                ctx.drawTextWithShadow(textRenderer, "ID:", px + PAD, spY - 2, C_GREY);
                ctx.drawTextWithShadow(textRenderer, "Name:", px + PAD, spY + 20, C_GREY);
                ctx.drawTextWithShadow(textRenderer, "Texture URL:", px + PAD, spY + 42, C_GREY);
            }
            case RENAME -> {
                ctx.fill(px + PAD - 2, spY + 8, px + PAD + spW, py + ph - 4, 0xEE111828);
                ctx.drawBorder(px + PAD - 2, spY + 8, spW + 2, 66, 0xFF44AA44);
                ctx.drawTextWithShadow(textRenderer, "Rename: " + selectedId, px + PAD, spY + 11, C_GREEN);
                ctx.drawTextWithShadow(textRenderer, "New name:", px + PAD, spY + 22, C_GREY);
            }
            case RETEXTURE -> {
                ctx.fill(px + PAD - 2, spY + 8, px + PAD + spW, py + ph - 4, 0xEE181108);
                ctx.drawBorder(px + PAD - 2, spY + 8, spW + 2, 66, 0xFFAA6622);
                ctx.drawTextWithShadow(textRenderer, "New Texture: " + selectedId, px + PAD, spY + 11, C_YELLOW);
                ctx.drawTextWithShadow(textRenderer, "Paste image URL:", px + PAD, spY + 22, C_GREY);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (activePanel == Panel.NONE || activePanel == Panel.PROPERTIES) {
            int gx = px + PAD, gy = py + TOP_H, gh = py + ph - 16;
            if (mx >= gx && mx < gx + GRID_W && my >= gy && my < gh) {
                int col = ((int) mx - gx) / (CELL + GAP);
                int row = ((int) my - gy + scroll) / (CELL + GAP);
                int idx = row * COLS + col;
                if (col < COLS && idx >= 0 && idx < filtered.size()) {
                    selectedId = filtered.get(idx).customId;
                    updateButtonStates();
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hx, double vy) {
        int rows = (int) Math.ceil(filtered.size() / (double) COLS);
        int vis  = (ph - TOP_H - 20) / (CELL + GAP);
        int maxS = Math.max(0, rows - vis) * (CELL + GAP);
        scroll = (int) Math.max(0, Math.min(maxS, scroll - vy * (CELL + GAP)));
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

    private void openPanel(Panel p) {
        closePanel(); activePanel = p;
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
                for (ButtonWidget b : new ButtonWidget[]{btnGlowMinus,btnGlowPlus,btnHardSoft,btnHardNormal,
                        btnHardHard,btnHardUnbreak,btnSoundStone,btnSoundWood,btnSoundMetal,
                        btnSoundGlass,btnSoundGrass,btnPropClose})
                    addDrawableChild(b);
            }
        }
    }

    private void closePanel() {
        activePanel = Panel.NONE;
        // Remove each widget explicitly — Screen.remove() takes Drawable, not Element
        remove(fldCreateId);    remove(fldCreateName);   remove(fldCreateUrl);
        remove(btnCreateOk);    remove(btnCreateCancel);
        remove(fldRenameNew);   remove(btnRenameOk);     remove(btnRenameCancel);
        remove(fldRetextureUrl);remove(btnRetextureOk);  remove(btnRetextureCancel);
        remove(btnGlowMinus);   remove(btnGlowPlus);
        remove(btnHardSoft);    remove(btnHardNormal);   remove(btnHardHard); remove(btnHardUnbreak);
        remove(btnSoundStone);  remove(btnSoundWood);    remove(btnSoundMetal);
        remove(btnSoundGlass);  remove(btnSoundGrass);   remove(btnPropClose);
    }

    private void doCreate() {
        String id   = fldCreateId.getText().trim().toLowerCase().replaceAll("[^a-z0-9_]", "_");
        String name = fldCreateName.getText().trim();
        String url  = fldCreateUrl.getText().trim();
        if (id.isEmpty())   { status("Enter an ID!", C_RED); return; }
        if (name.isEmpty()) { status("Enter a name!", C_RED); return; }
        if (url.isEmpty())  { status("Paste a URL!", C_RED); return; }
        if (SlotManager.hasId(id)) { status("'" + id + "' already exists!", C_RED); return; }
        if (SlotManager.freeSlots() == 0) { status("All 64 slots are full!", C_RED); return; }
        closePanel();
        status("Downloading...", C_YELLOW);
        sendCmd("customblock createurl " + id + " " + name.replace(" ", "_") + " " + url);
        // Don't set selectedId here — wait for server confirmation via SlotUpdatePayload
    }

    private void doRename() {
        if (selectedId == null) return;
        String name = fldRenameNew.getText().trim();
        if (name.isEmpty()) { status("Enter a name!", C_RED); return; }
        closePanel();
        sendCmd("customblock rename " + selectedId + " " + name.replace(" ", "_"));
        status("Renamed!", C_GREEN);
        rebuildFiltered();
    }

    private void doRetexture() {
        if (selectedId == null) return;
        String url = fldRetextureUrl.getText().trim();
        if (url.isEmpty()) { status("Paste a URL!", C_RED); return; }
        closePanel();
        status("Downloading...", C_YELLOW);
        sendCmd("customblock retexture " + selectedId + " " + url);
    }

    private void doGive() {
        if (selectedId == null) return;
        sendCmd("customblock give " + selectedId);
        status("Given to you!", C_GREEN);
    }

    private void doDelete() {
        if (selectedId == null) return;
        sendCmd("customblock delete " + selectedId);
        status("Deleted '" + selectedId + "'", C_GREEN);
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
        sendCmd("customblock setglow " + selectedId + " " + newLevel);
    }

    private void setHard(float val) {
        if (selectedId == null) return;
        sendCmd("customblock sethardness " + selectedId + " " + val);
    }

    private void setSound(String type) {
        if (selectedId == null) return;
        sendCmd("customblock setsound " + selectedId + " " + type);
    }

    private void sendCmd(String cmd) {
        if (client == null || client.player == null) return;
        client.player.networkHandler.sendChatCommand(cmd);
    }

    private void rebuildFiltered() {
        filtered.clear();
        String q = search.toLowerCase();
        for (SlotManager.SlotData d : SlotManager.allSlots()) {
            if (q.isEmpty() || d.customId.contains(q) || d.displayName.toLowerCase().contains(q))
                filtered.add(d);
        }
    }

    private void updateButtonStates() {
        boolean has = selectedId != null && SlotManager.getById(selectedId) != null;
        btnGive.active = btnRename.active = btnRetexture.active = btnProperties.active = btnDelete.active = has;
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
