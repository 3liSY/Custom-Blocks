package com.customblocks;

import net.minecraft.registry.Registries;
import net.minecraft.registry.SimpleRegistry;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class RegistryUtils {

    private static final Field FROZEN_FIELD;
    private static final Field INTRUSIVE_CACHE_FIELD;

    static {
        SimpleRegistry<?> probe = (SimpleRegistry<?>) Registries.BLOCK;
        Field frozenField = null;
        for (Field f : SimpleRegistry.class.getDeclaredFields()) {
            if (f.getType() == boolean.class) {
                f.setAccessible(true);
                try {
                    if (f.getBoolean(probe)) { frozenField = f; break; }
                } catch (IllegalAccessException ignored) {}
            }
        }
        if (frozenField == null)
            throw new RuntimeException("[CustomBlocks] Could not find frozen field in SimpleRegistry");
        FROZEN_FIELD = frozenField;
        CustomBlocksMod.LOGGER.info("[CustomBlocks] Frozen field: {}", frozenField.getName());

        Field cacheField = null;
        for (Field f : SimpleRegistry.class.getDeclaredFields()) {
            if (Map.class.isAssignableFrom(f.getType())) {
                f.setAccessible(true);
                try {
                    if (f.get(probe) == null) { cacheField = f; break; }
                } catch (IllegalAccessException ignored) {}
            }
        }
        INTRUSIVE_CACHE_FIELD = cacheField;
        if (cacheField != null)
            CustomBlocksMod.LOGGER.info("[CustomBlocks] Intrusive cache field: {}", cacheField.getName());
    }

    public static void unfreeze(SimpleRegistry<?> registry) {
        try {
            FROZEN_FIELD.setBoolean(registry, false);
            if (INTRUSIVE_CACHE_FIELD != null && INTRUSIVE_CACHE_FIELD.get(registry) == null) {
                INTRUSIVE_CACHE_FIELD.set(registry, new HashMap<>());
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException("[CustomBlocks] Cannot unfreeze registry", e);
        }
    }

    public static void freeze(SimpleRegistry<?> registry) {
        try {
            FROZEN_FIELD.setBoolean(registry, true);
            if (INTRUSIVE_CACHE_FIELD != null) {
                INTRUSIVE_CACHE_FIELD.set(registry, null);
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException("[CustomBlocks] Cannot refreeze registry", e);
        }
    }
}
