package com.customblocks;

import net.minecraft.registry.SimpleRegistry;

import java.lang.reflect.Field;

/**
 * Uses reflection to temporarily unfreeze a Minecraft registry so we can
 * register new blocks/items after startup.
 */
public class RegistryUtils {

    private static final Field FROZEN_FIELD;

    static {
        try {
            FROZEN_FIELD = SimpleRegistry.class.getDeclaredField("frozen");
            FROZEN_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(
                "[CustomBlocks] Could not find 'frozen' field in SimpleRegistry. " +
                "Possible Minecraft version mismatch.", e);
        }
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
