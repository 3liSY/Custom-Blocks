package com.itemmap.item;

import com.itemmap.ItemMapMod;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

public class ItemMapItem extends Item {

    public static final String NBT_TARGET = "itemmap_target";

    public ItemMapItem(Settings settings) {
        super(settings);
    }

    public static ItemStack createFlat(Item targetItem) {
        ItemStack stack = new ItemStack(ItemMapMod.ITEM_MAP_ITEM);
        NbtCompound nbt = new NbtCompound();
        nbt.putString(NBT_TARGET, Registries.ITEM.getId(targetItem).toString());
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        stack.set(DataComponentTypes.CUSTOM_NAME,
            Text.literal(targetItem.getName().getString()));
        return stack;
    }

    public static String getTargetId(ItemStack stack) {
        if (!(stack.getItem() instanceof ItemMapItem)) return null;
        NbtComponent comp = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (comp == null) return null;
        NbtCompound nbt = comp.copyNbt();
        return nbt.contains(NBT_TARGET) ? nbt.getString(NBT_TARGET) : null;
    }
}
