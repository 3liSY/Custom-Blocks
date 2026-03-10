package com.customblocks;

import net.minecraft.registry.Registries;
import net.minecraft.registry.SimpleRegistry;

import java.lang.reflect.Field;

public class RegistryUtils {

    private static final Field FROZEN_FIELD;

    static {
        SimpleRegistry<?> probe = (SimpleRegistry<?>) Registries.BLOCK;
        Field found = null;
        for (Field f : SimpleRegistry.class.getDeclaredFields()) {
            if (f.getType() == boolean.class) {
                f.setAccessible(true);
                try {
                    if (f.getBoolean(probe)) {
                        found = f;
                        break;
                    }
                } catch (IllegalAccessException ignored) {}
            }
        }
        if (found == null) {
            throw new RuntimeException("[CustomBlocks] Could not detect frozen field in SimpleRegistry.");
        }
        FROZEN_FIELD = found;
        CustomBlocksMod.LOGGER.info("[CustomBlocks] Detected registry frozen field: {}", found.getName());
    }

    public static void unfreeze(SimpleRegistry<?> registry) {
        try { FROZEN_FIELD.setBoolean(registry, false); }
        catch (IllegalAccessException e) { throw new RuntimeException("[CustomBlocks] Cannot unfreeze registry", e); }
    }

    public static void freeze(SimpleRegistry<?> registry) {
        try { FROZEN_FIELD.setBoolean(registry, true); }
        catch (IllegalAccessException e) { throw new RuntimeException("[CustomBlocks] Cannot refreeze registry", e); }
    }
}
