package com.customblocks.client.texture;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side cache: stores registered dynamic textures for each block slot.
 * Textures are uploaded to the texture manager as they arrive over the network.
 */
@Environment(EnvType.CLIENT)
public class TextureCache {

    private static final Logger LOGGER = LoggerFactory.getLogger("CustomBlocks/TextureCache");
    private static final Map<String, Identifier> CACHE = new ConcurrentHashMap<>();

    /**
     * Upload raw PNG bytes as a dynamic texture and cache the identifier.
     * Must be called on the render thread.
     * @param customId block custom ID
     * @param pngBytes raw PNG image bytes
     * @return the registered Identifier, or null on failure
     */
    public static Identifier upload(String customId, byte[] pngBytes) {
        if (pngBytes == null || pngBytes.length == 0) return null;
        try {
            NativeImage img = NativeImage.read(new ByteArrayInputStream(pngBytes));
            NativeImageBackedTexture tex = new NativeImageBackedTexture(img);
            Identifier id = Identifier.of("customblocks", "dynamic/block/" + customId);
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, tex);
            CACHE.put(customId, id);
            return id;
        } catch (IOException e) {
            LOGGER.error("[CB/TextureCache] Failed to upload texture for {}: {}", customId, e.getMessage());
            return null;
        }
    }

    /** Get the cached texture identifier for a block, or null if not loaded. */
    public static Identifier get(String customId) {
        return CACHE.get(customId);
    }

    /** Remove and destroy a cached texture. */
    public static void invalidate(String customId) {
        Identifier id = CACHE.remove(customId);
        if (id != null) {
            try {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc != null && mc.getTextureManager() != null)
                    mc.getTextureManager().destroyTexture(id);
            } catch (Exception ignored) {}
        }
    }

    /** Clear all cached textures (called on disconnect). */
    public static void clear() {
        CACHE.keySet().forEach(TextureCache::invalidate);
        CACHE.clear();
    }

    public static boolean has(String customId) { return CACHE.containsKey(customId); }
    public static int size() { return CACHE.size(); }
}
