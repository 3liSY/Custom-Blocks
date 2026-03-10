package com.customblocks;

import net.minecraft.registry.SimpleRegistry;

import java.lang.reflect.Field;

public class RegistryUtils {

    private static final Field FROZEN_FIELD;

    static {
        Field found = null;
        for (Field f : SimpleRegistry.class.getDeclaredFields()) {
            if (f.getType() == boolean.class) {
                f.setAccessible(true);
                found = f;
                break;
            }
        }
        if (found == null) {
            throw new RuntimeException(
                "[CustomBlocks] Could not find boolean field in SimpleRegistry. " +
                "Please report this with your Minecraft version.");
        }
        FROZEN_FIELD = found;
        CustomBlocksMod.LOGGER.info("[CustomBlocks] Found registry frozen field: {}", found.getName());
    }

    public static void unfreeze(SimpleRegistry<?> registry) {
        try {
            FROZEN_FIELD.setBoolean(registry, false);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("[CustomBlocks] Could not unfreeze registry", e);
        }
    }

    public static void freeze(SimpleRegistry<?> registry) {
        try {
            FROZEN_FIELD.setBoolean(registry, true);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("[CustomBlocks] Could not refreeze registry", e);
        }
    }
}
