package com.itemmap.client.renderer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Environment(EnvType.CLIENT)
public class FrameRenderManager {

    // entityId -> current spin angle (degrees)
    private static final Map<Long, Float> SPIN_ANGLES = new ConcurrentHashMap<>();

    // imageId -> uploaded GPU texture identifier (for custom overrides)
    private static final Map<String, Identifier> CUSTOM_TEXTURES = new ConcurrentHashMap<>();

    /** Called every client tick to advance spin angles for 3D frames. */
    public static void tickSpins(float spinSpeedDeg) {
        // All spinning frames use the same speed for now
        // Per-frame speed is stored in FrameData but we advance all here
        for (Map.Entry<Long, Float> e : SPIN_ANGLES.entrySet()) {
            e.setValue((e.getValue() + spinSpeedDeg) % 360f);
        }
    }

    /** Get spin angle for a frame, creating it at 0 if new. */
    public static float getSpinAngle(long entityId, boolean is3D) {
        if (!is3D) return 0f;
        return SPIN_ANGLES.computeIfAbsent(entityId, k -> 0f);
    }

    /** Advance a specific frame's spin by the given degrees per tick. */
    public static void advanceSpin(long entityId, float degreesPerTick) {
        SPIN_ANGLES.merge(entityId, degreesPerTick, (old, add) -> (old + add) % 360f);
    }

    /** Remove spin state (frame removed). */
    public static void removeSpin(long entityId) {
        SPIN_ANGLES.remove(entityId);
    }

    /** Upload a custom PNG override for an item ID. */
    public static void setCustomTexture(String itemId, byte[] png) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null) return;

            // Remove old texture if exists
            Identifier old = CUSTOM_TEXTURES.remove(itemId);
            if (old != null) {
                try { mc.getTextureManager().destroyTexture(old); } catch (Exception ignored) {}
            }

            NativeImage img = NativeImage.read(new ByteArrayInputStream(png));
            NativeImageBackedTexture tex = new NativeImageBackedTexture(img);
            String safeId = itemId.replace(":", "_").replaceAll("[^a-z0-9_.-]", "_");
            Identifier id = Identifier.of("itemmap_custom", safeId);
            mc.getTextureManager().registerTexture(id, tex);
            CUSTOM_TEXTURES.put(itemId, id);
        } catch (Exception e) {
            // Silently ignore bad PNG bytes
        }
    }

    /** Get custom texture for an item ID, or null if none. */
    public static Identifier getCustomTexture(String itemId) {
        return CUSTOM_TEXTURES.get(itemId);
    }

    public static void invalidateCustomTexture(String itemId) {
        Identifier old = CUSTOM_TEXTURES.remove(itemId);
        if (old != null) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null) {
                try { mc.getTextureManager().destroyTexture(old); } catch (Exception ignored) {}
            }
        }
    }

    public static void clearAll() {
        SPIN_ANGLES.clear();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null) {
            for (Identifier id : CUSTOM_TEXTURES.values()) {
                try { mc.getTextureManager().destroyTexture(id); } catch (Exception ignored) {}
            }
        }
        CUSTOM_TEXTURES.clear();
    }
}
