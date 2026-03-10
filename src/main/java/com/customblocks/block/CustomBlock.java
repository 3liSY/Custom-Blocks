package com.customblocks.block;

import net.minecraft.block.Block;
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
}
