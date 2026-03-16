package com.itemmap.client.renderer;

import com.itemmap.manager.FrameData;
import com.itemmap.manager.FrameManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side: manages GPU textures for custom images and vanilla item sprites.
 * Also advances spin angles each tick.
 */
@Environment(EnvType.CLIENT)
public class FrameRenderManager {

    // imageId -> registered texture Identifier
    private static final Map<String, Identifier> IMAGE_TEXTURES = new ConcurrentHashMap<>();
    // Track what's already uploaded to GPU
    private static final Set<String> REGISTERED = ConcurrentHashMap.newKeySet();

    public static final String TEX_NAMESPACE = "itemmap_dynamic";

    /** Returns the GPU texture Identifier for a custom image, uploading it if needed. */
    public static Identifier getImageTexture(String imageId) {
        if (IMAGE_TEXTURES.containsKey(imageId)) return IMAGE_TEXTURES.get(imageId);
        byte[] png = FrameManager.getImage(imageId);
        if (png == null) return null;
        return uploadTexture(imageId, png);
    }

    /** Upload PNG bytes to GPU, register with MC texture manager. */
    private static Identifier uploadTexture(String imageId, byte[] png) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) return null;
            NativeImage img = NativeImage.read(new ByteArrayInputStream(png));
            NativeImageBackedTexture tex = new NativeImageBackedTexture(img);
            Identifier id = Identifier.of(TEX_NAMESPACE, imageId.toLowerCase()
                .replaceAll("[^a-z0-9_/.-]", "_"));
            client.getTextureManager().registerTexture(id, tex);
            IMAGE_TEXTURES.put(imageId, id);
            REGISTERED.add(imageId);
            return id;
        } catch (Exception e) {
            return null;
        }
    }

    /** Called when a new image arrives — removes stale GPU texture. */
    public static void invalidateImage(String imageId) {
        Identifier old = IMAGE_TEXTURES.remove(imageId);
        REGISTERED.remove(imageId);
        if (old != null) {
            try {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client != null) client.getTextureManager().destroyTexture(old);
            } catch (Exception ignored) {}
        }
    }

    /** Returns the vanilla item texture Identifier for an item registry path. */
    public static Identifier getVanillaItemTexture(String itemPath) {
        // Vanilla item textures live at minecraft:textures/item/<path>.png
        return Identifier.of("minecraft", "textures/item/" + itemPath + ".png");
    }

    /** Called each client tick — advance spin angles. */
    public static void tickSpins() {
        for (FrameData d : FrameManager.all()) {
            if (d.mode == FrameData.DisplayMode.SPIN_3D) {
                d.spinAngle = (d.spinAngle + d.spinSpeed) % 360f;
            }
        }
    }
}
