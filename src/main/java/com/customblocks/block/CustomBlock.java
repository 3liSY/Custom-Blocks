package com.customblocks.block;

import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

public class CustomBlock extends Block {

    private final String displayName;

    public CustomBlock(String displayName, Settings settings) {
        super(settings);
        this.displayName = displayName;
    }

    @Override
    public MutableText getName() {
        return Text.literal(displayName);
    }

    public String getCustomDisplayName() {
        return displayName;
    }

    public static class CustomBlockItem extends BlockItem {
        private final String displayName;

        public CustomBlockItem(CustomBlock block, Item.Settings settings) {
            super(block, settings);
            this.displayName = block.getCustomDisplayName();
        }

        @Override
        public Text getName() {
            return Text.literal(displayName);
        }

        @Override
        public Text getName(ItemStack stack) {
            return Text.literal(displayName);
        }
    }
}