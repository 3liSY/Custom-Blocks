package com.customblocks.client.gui;

import com.customblocks.client.texture.TextureCache;
import com.customblocks.network.ImageEditPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;

/**
 * Image editor screen: load image from URL or drag-drop,
 * apply crop / brightness / contrast / flip / rotate, preview live, then
 * send to server to create or retexture a block.
 */
@Environment(EnvType.CLIENT)
public class ImageEditorScreen extends Screen {

    // Caller context
    private final String action;     // "create" or "retexture"
    private final String targetId;   // existing block ID for retexture
    private final Screen parent;

    // State
    private byte[] originalBytes = null;  // loaded raw PNG
    private byte[] previewBytes  = null;  // after transforms
    private Identifier previewTex = null;

    // Sliders
    private int  outputSize   = 16;    // 16-256
    private float brightness  = 0;     // -100 to +100
    private float contrast    = 1.0f;  // 0.1 to 3.0
    private int  cropTop=0, cropBottom=0, cropLeft=0, cropRight=0;

    // Widgets
    private TextFieldWidget urlField;
    private TextFieldWidget idField, nameField;
    private ButtonWidget btnLoad, btnFlipH, btnFlipV, btnRot90, btnRot180, btnRot270;
    private ButtonWidget btnApply, btnCancel;

    // Simple slider references (brightness/contrast/size)
    private int sliderBrightY, sliderContrastY, sliderSizeY;
    private boolean draggingBrightness = false, draggingContrast = false, draggingSize = false;

    private String statusMsg = "";
    private long   statusUntil = 0;

    public ImageEditorScreen(String action, String targetId, Screen parent) {
        super(Text.literal("Image Editor — " + action));
        this.action   = action;
        this.targetId = targetId;
        this.parent   = parent;
    }

    @Override
    protected void init() {
        int cx = width / 2, cy = height / 2;
        int panelW = 400, panelH = 340;
        int px = cx - panelW / 2, py = cy - panelH / 2;

        // URL field
        urlField = new TextFieldWidget(textRenderer, px + 10, py + 30, panelW - 80, 20,
                Text.literal("Image URL or paste"));
        urlField.setMaxLength(1024);
        urlField.setPlaceholderText(Text.literal("https://example.com/texture.png"));
        addDrawableChild(urlField);

        btnLoad = ButtonWidget.builder(Text.literal("Load"), btn -> loadFromUrl())
                .dimensions(px + panelW - 65, py + 30, 60, 20).build();
        addDrawableChild(btnLoad);

        // ID / Name fields (only for create)
        if ("create".equals(action)) {
            idField = new TextFieldWidget(textRenderer, px + 10, py + 60, 140, 20, Text.literal("Block ID"));
            idField.setMaxLength(64); idField.setPlaceholderText(Text.literal("my_block"));
            addDrawableChild(idField);

            nameField = new TextFieldWidget(textRenderer, px + 160, py + 60, 140, 20, Text.literal("Display Name"));
            nameField.setMaxLength(64); nameField.setPlaceholderText(Text.literal("My Block"));
            addDrawableChild(nameField);
        }

        // Transform buttons
        int btnRow = py + 90;
        btnFlipH  = ButtonWidget.builder(Text.literal("⟺ H"), btn -> applyFlipH())  .dimensions(px+10, btnRow, 52, 18).build();
        btnFlipV  = ButtonWidget.builder(Text.literal("⟷ V"), btn -> applyFlipV())  .dimensions(px+66, btnRow, 52, 18).build();
        btnRot90  = ButtonWidget.builder(Text.literal("↻90"),  btn -> applyRot(90))  .dimensions(px+122,btnRow, 52, 18).build();
        btnRot180 = ButtonWidget.builder(Text.literal("↻180"), btn -> applyRot(180)) .dimensions(px+178,btnRow, 52, 18).build();
        btnRot270 = ButtonWidget.builder(Text.literal("↻270"), btn -> applyRot(270)) .dimensions(px+234,btnRow, 52, 18).build();
        addDrawableChild(btnFlipH); addDrawableChild(btnFlipV);
        addDrawableChild(btnRot90); addDrawableChild(btnRot180); addDrawableChild(btnRot270);

        // Store slider Y positions for manual rendering
        sliderSizeY      = py + 120;
        sliderBrightY    = py + 148;
        sliderContrastY  = py + 176;

        // Preview area: right side 128x128
        // (drawn in render())

        // Apply / Cancel
        btnApply = ButtonWidget.builder(Text.literal("✔ Apply"), btn -> doApply())
                .dimensions(cx - 85, py + panelH - 28, 80, 22).build();
        btnCancel = ButtonWidget.builder(Text.literal("✖ Cancel"), btn -> close())
                .dimensions(cx + 5, py + panelH - 28, 80, 22).build();
        addDrawableChild(btnApply);
        addDrawableChild(btnCancel);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        // Background
        ctx.fill(0, 0, width, height, 0xA0000000);
        int panelW = 400, panelH = 340;
        int px = width/2 - panelW/2, py = height/2 - panelH/2;
        ctx.fill(px, py, px+panelW, py+panelH, 0xFF1A1A2E);
        ctx.drawBorder(px, py, panelW, panelH, 0xFF5555EE);

        ctx.drawText(textRenderer, "§b✎ Image Editor", px+10, py+10, 0xFFFFFF, false);

        // Slider labels + tracks
        drawSlider(ctx, mx, my, px+10, sliderSizeY,     200, "Output Size: " + outputSize + "px");
        drawSlider(ctx, mx, my, px+10, sliderBrightY,   200, "Brightness: " + (int)brightness);
        drawSlider(ctx, mx, my, px+10, sliderContrastY, 200, "Contrast: " + String.format("%.1f", contrast));

        // Preview box
        int previewX = px + panelW - 148, previewY = py + 80;
        ctx.fill(previewX, previewY, previewX+128, previewY+128, 0xFF0E0E1A);
        ctx.drawBorder(previewX, previewY, 128, 128, 0xFF444466);
        if (previewTex != null)
            ctx.drawTexture(previewTex, previewX+4, previewY+4, 0, 0, 120, 120, 120, 120);
        else
            ctx.drawText(textRenderer, "§7Preview", previewX+40, previewY+60, 0x888888, false);

        // Status
        if (System.currentTimeMillis() < statusUntil)
            ctx.drawText(textRenderer, statusMsg, px+10, py+panelH-50, 0xFFFFAA, false);

        super.render(ctx, mx, my, delta);
    }

    private void drawSlider(DrawContext ctx, int mx, int my, int x, int y, int w, String label) {
        ctx.drawText(textRenderer, label, x, y-12, 0xAAAAAA, false);
        ctx.fill(x, y, x+w, y+8, 0xFF333355);
        int handleX = sliderHandleX(label, x, w);
        ctx.fill(handleX, y-2, handleX+6, y+10, 0xFF8888FF);
    }

    private int sliderHandleX(String label, int x, int w) {
        if (label.startsWith("Output")) return x + (int)((outputSize - 16) / 240f * (w - 6));
        if (label.startsWith("Bright")) return x + (int)((brightness + 100) / 200f * (w - 6));
        if (label.startsWith("Contr")) return x + (int)((contrast - 0.1f) / 2.9f * (w - 6));
        return x;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        // Slider drag detection
        int panelW=400, px=width/2-panelW/2;
        if (my >= sliderSizeY-2 && my <= sliderSizeY+10 && mx >= px+10 && mx <= px+210) {
            draggingSize = true; updateSliderSize((int)mx, px+10, 200); return true;
        }
        if (my >= sliderBrightY-2 && my <= sliderBrightY+10 && mx >= px+10 && mx <= px+210) {
            draggingBrightness = true; updateSliderBrightness((int)mx, px+10, 200); return true;
        }
        if (my >= sliderContrastY-2 && my <= sliderContrastY+10 && mx >= px+10 && mx <= px+210) {
            draggingContrast = true; updateSliderContrast((int)mx, px+10, 200); return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        int panelW=400, px=width/2-panelW/2;
        if (draggingSize)       { updateSliderSize((int)mx,px+10,200);       rebuildPreview(); return true; }
        if (draggingBrightness) { updateSliderBrightness((int)mx,px+10,200); rebuildPreview(); return true; }
        if (draggingContrast)   { updateSliderContrast((int)mx,px+10,200);   rebuildPreview(); return true; }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        draggingSize = draggingBrightness = draggingContrast = false;
        return super.mouseReleased(mx, my, btn);
    }

    private void updateSliderSize(int mx, int x, int w) {
        float t = Math.max(0, Math.min(1, (mx - x) / (float)(w - 6)));
        outputSize = 16 + (int)(t * 240);
        outputSize = (outputSize / 16) * 16; // snap to multiples of 16
    }

    private void updateSliderBrightness(int mx, int x, int w) {
        float t = Math.max(0, Math.min(1, (mx - x) / (float)(w - 6)));
        brightness = -100 + t * 200;
    }

    private void updateSliderContrast(int mx, int x, int w) {
        float t = Math.max(0, Math.min(1, (mx - x) / (float)(w - 6)));
        contrast = 0.1f + t * 2.9f;
    }

    // ── Actions ────────────────────────────────────────────────────────────────

    private void loadFromUrl() {
        String url = urlField.getText().trim();
        if (url.isEmpty()) return;
        status("§7Downloading…");
        Thread.ofVirtual().start(() -> {
            try {
                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                        .followRedirects(HttpClient.Redirect.NORMAL).build();
                HttpResponse<byte[]> resp = client.send(
                        HttpRequest.newBuilder().uri(URI.create(url)).GET().build(),
                        HttpResponse.BodyHandlers.ofByteArray());
                if (resp.statusCode() != 200) throw new Exception("HTTP " + resp.statusCode());
                originalBytes = resp.body();
                client().execute(this::rebuildPreview);
                status("§aImage loaded.");
            } catch (Exception e) {
                client().execute(() -> status("§cError: " + e.getMessage()));
            }
        });
    }

    /** Called when raw image bytes are dropped onto the window (from DragDropHandler). */
    public void setImageBytes(byte[] bytes) {
        originalBytes = bytes;
        rebuildPreview();
        status("§aImage loaded from drop.");
    }

    private void applyFlipH()  { if (originalBytes==null) return; try { originalBytes = flipH(originalBytes); rebuildPreview(); } catch(Exception ignored){} }
    private void applyFlipV()  { if (originalBytes==null) return; try { originalBytes = flipV(originalBytes); rebuildPreview(); } catch(Exception ignored){} }
    private void applyRot(int d){ if (originalBytes==null) return; try { originalBytes = rotate(originalBytes,d); rebuildPreview(); } catch(Exception ignored){} }

    private void rebuildPreview() {
        if (originalBytes == null) return;
        try {
            byte[] working = originalBytes;
            // Brightness / contrast
            if (brightness != 0 || contrast != 1.0f)
                working = adjustBC(working, brightness, contrast);
            // Resize to output size
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(working));
            BufferedImage scaled = new BufferedImage(outputSize, outputSize, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(img, 0, 0, outputSize, outputSize, null); g.dispose();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(scaled, "PNG", baos);
            previewBytes = baos.toByteArray();
            // Upload to texture manager
            if (previewTex != null)
                net.minecraft.client.MinecraftClient.getInstance().getTextureManager()
                        .destroyTexture(previewTex);
            previewTex = TextureCache.upload("_editor_preview_", previewBytes);
        } catch (Exception e) { status("§cPreview error: " + e.getMessage()); }
    }

    private void doApply() {
        if (previewBytes == null || previewBytes.length == 0) {
            status("§cNo image loaded."); return;
        }
        if ("create".equals(action)) {
            String id   = idField   != null ? idField.getText().trim()   : "";
            String name = nameField != null ? nameField.getText().trim() : "";
            if (id.isEmpty() || name.isEmpty()) { status("§cFill in Block ID and Name."); return; }
            ClientPlayNetworking.send(new ImageEditPayload("create", id, name, previewBytes, ""));
        } else {
            ClientPlayNetworking.send(new ImageEditPayload("retexture", targetId, null, previewBytes, ""));
        }
        client().setScreen(parent);
    }

    private void status(String msg) { statusMsg = msg; statusUntil = System.currentTimeMillis() + 4000; }
    private net.minecraft.client.MinecraftClient client() { return net.minecraft.client.MinecraftClient.getInstance(); }

    // ── Image transforms (pure AWT) ────────────────────────────────────────────

    private static byte[] flipH(byte[] png) throws Exception {
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(png));
        int w=src.getWidth(), h=src.getHeight();
        BufferedImage out = new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<h;y++) for(int x=0;x<w;x++) out.setRGB(w-1-x,y,src.getRGB(x,y));
        return toPng(out);
    }

    private static byte[] flipV(byte[] png) throws Exception {
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(png));
        int w=src.getWidth(), h=src.getHeight();
        BufferedImage out = new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<h;y++) for(int x=0;x<w;x++) out.setRGB(x,h-1-y,src.getRGB(x,y));
        return toPng(out);
    }

    private static byte[] rotate(byte[] png, int deg) throws Exception {
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(png));
        int w=src.getWidth(), h=src.getHeight();
        boolean swap=(deg==90||deg==270);
        BufferedImage out = new BufferedImage(swap?h:w, swap?w:h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.translate(out.getWidth()/2.0, out.getHeight()/2.0);
        g.rotate(Math.toRadians(deg));
        g.drawImage(src,-w/2,-h/2,null); g.dispose();
        return toPng(out);
    }

    private static byte[] adjustBC(byte[] png, float brightness, float contrast) throws Exception {
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(png));
        int w=src.getWidth(), h=src.getHeight();
        BufferedImage out = new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<h;y++) for(int x=0;x<w;x++) {
            int argb=src.getRGB(x,y), a=(argb>>24)&0xFF;
            int r=clamp((int)(((argb>>16)&0xFF-128)*contrast+128+brightness));
            int g=clamp((int)(((argb>>8)&0xFF-128)*contrast+128+brightness));
            int b=clamp((int)((argb&0xFF-128)*contrast+128+brightness));
            out.setRGB(x,y,(a<<24)|(r<<16)|(g<<8)|b);
        }
        return toPng(out);
    }

    private static byte[] toPng(BufferedImage img) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img,"PNG",baos); return baos.toByteArray();
    }

    private static int clamp(int v) { return Math.max(0,Math.min(255,v)); }

    @Override public boolean shouldPause() { return false; }
    @Override public void close() { client().setScreen(parent); }
}
