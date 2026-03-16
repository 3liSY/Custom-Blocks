package com.itemmap.item;

import com.itemmap.ItemMapMod;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

/**
 * One item class for ALL item-map entries.
 * Target item ID + 3D flag stored via DataComponents (1.21.1 API).
 */
public class ItemMapItem extends Item {

    public static final String NBT_TARGET = "itemmap_target";
    public static final String NBT_IS_3D  = "itemmap_3d";

    public ItemMapItem(Settings settings) {
        super(settings);
    }

    public static ItemStack createFlat(Item targetItem) {
        return createStack(targetItem, false);
    }

    public static ItemStack create3D(Item targetItem) {
        return createStack(targetItem, true);
    }

    private static ItemStack createStack(Item targetItem, boolean is3D) {
        ItemStack stack = new ItemStack(ItemMapMod.ITEM_MAP_ITEM);
        NbtCompound nbt = new NbtCompound();
        nbt.putString(NBT_TARGET, Registries.ITEM.getId(targetItem).toString());
        nbt.putBoolean(NBT_IS_3D, is3D);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        String name = targetItem.getName().getString();
        if (is3D) name = name + " 3D";
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name));
        return stack;
    }

    public static String getTargetId(ItemStack stack) {
        if (!(stack.getItem() instanceof ItemMapItem)) return null;
        NbtComponent comp = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (comp == null) return null;
        NbtCompound nbt = comp.copyNbt();
        return nbt.contains(NBT_TARGET) ? nbt.getString(NBT_TARGET) : null;
    }

    public static boolean is3D(ItemStack stack) {
        if (!(stack.getItem() instanceof ItemMapItem)) return false;
        NbtComponent comp = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (comp == null) return false;
        return comp.copyNbt().getBoolean(NBT_IS_3D);
    }
}
