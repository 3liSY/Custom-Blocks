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

    private static final Map<String, Identifier> CUSTOM_TEXTURES = new ConcurrentHashMap<>();

    public static void setCustomTexture(String itemId, byte[] png) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null) return;
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
        } catch (Exception ignored) {}
    }

    public static Identifier getCustomTexture(String itemId) {
        return CUSTOM_TEXTURES.get(itemId);
    }

    public static void invalidate(String itemId) {
        Identifier old = CUSTOM_TEXTURES.remove(itemId);
        if (old != null) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null) try { mc.getTextureManager().destroyTexture(old); } catch (Exception ignored) {}
        }
    }

    public static void clearAll() {
        MinecraftClient mc = MinecraftClient.getInstance();
        for (Identifier id : CUSTOM_TEXTURES.values()) {
            if (mc != null) try { mc.getTextureManager().destroyTexture(id); } catch (Exception ignored) {}
        }
        CUSTOM_TEXTURES.clear();
    }
}
