package com.customblocks;

import net.minecraft.registry.Registries;
import net.minecraft.registry.SimpleRegistry;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Unfreezes/refreezes Minecraft's SimpleRegistry at runtime.
 * Also restores intrusiveHolderCache so blocks can be constructed
 * without the "can't create intrusive holders" crash.
 */
public class RegistryUtils {

    private static final Field FROZEN_FIELD;
    private static final Field INTRUSIVE_CACHE_FIELD;

    static {
        // Find the boolean "frozen" field — it's TRUE on a frozen registry
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

        // Find the intrusiveHolderCache field — it's a Map that is NULL when frozen
        Field cacheField = null;
        for (Field f : SimpleRegistry.class.getDeclaredFields()) {
            if (Map.class.isAssignableFrom(f.getType())) {
                f.setAccessible(true);
                try {
                    if (f.get(probe) == null) { cacheField = f; break; }
                } catch (IllegalAccessException ignored) {}
            }
        }
        // cacheField may be null on some versions — that's OK, we'll check before using
        INTRUSIVE_CACHE_FIELD = cacheField;
        if (cacheField != null)
            CustomBlocksMod.LOGGER.info("[CustomBlocks] Intrusive cache field: {}", cacheField.getName());
        else
            CustomBlocksMod.LOGGER.warn("[CustomBlocks] intrusiveHolderCache field not found — may crash on block creation");
    }

    public static void unfreeze(SimpleRegistry<?> registry) {
        try {
            FROZEN_FIELD.setBoolean(registry, false);
            // Restore the intrusive holder cache so Block constructor works
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
            // Clear the cache again (as vanilla does on freeze)
            if (INTRUSIVE_CACHE_FIELD != null) {
                INTRUSIVE_CACHE_FIELD.set(registry, null);
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException("[CustomBlocks] Cannot refreeze registry", e);
        }
    }
}
