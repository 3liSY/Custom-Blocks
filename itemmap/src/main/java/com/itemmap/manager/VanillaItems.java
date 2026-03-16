package com.itemmap.manager;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.*;

/**
 * Utilities for working with all vanilla items.
 */
public class VanillaItems {

    /** Returns the vanilla Identifier string for an item (e.g. "minecraft:diamond_sword"). */
    public static String getId(Item item) {
        return Registries.ITEM.getId(item).toString();
    }

    /** Returns all registered vanilla items sorted by their ID. */
    public static List<Item> allSorted() {
        List<Item> list = new ArrayList<>(Registries.ITEM.stream().toList());
        list.sort(Comparator.comparing(VanillaItems::getId));
        return list;
    }

    /** Friendly display name: "diamond_sword" → "Diamond Sword" */
    public static String friendlyName(Item item) {
        String path = Registries.ITEM.getId(item).getPath();
        String[] parts = path.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)));
                sb.append(part.substring(1));
                sb.append(' ');
            }
        }
        return sb.toString().trim();
    }

    /** Resolve an item by its full ID string ("minecraft:stone" or just "stone"). */
    public static Optional<Item> find(String id) {
        if (!id.contains(":")) id = "minecraft:" + id;
        Item item = Registries.ITEM.get(Identifier.of(id));
        // ITEM.get returns air for unknown - check if explicitly registered
        if (item == net.minecraft.item.Items.AIR && !id.equals("minecraft:air")) return Optional.empty();
        return Optional.of(item);
    }
}
