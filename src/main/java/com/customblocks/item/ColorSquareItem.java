package com.customblocks.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Rarity;

/**
 * Special color-square items that right-click a custom block to apply a color variant.
 */
public class ColorSquareItem extends Item {

    public final String colorKey;
    public final String colorLabel;

    public ColorSquareItem(String colorKey, String colorLabel, Settings settings) {
        super(settings.rarity(Rarity.UNCOMMON));
        this.colorKey   = colorKey;
        this.colorLabel = colorLabel;
    }

    @Override
    public Text getName(ItemStack stack) {
        return Text.literal(colorLabel + " Color Square");
    }
}
