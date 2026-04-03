package com.customblocks.block;

import com.customblocks.SlotManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;

/**
 * One of the 512 registered slot blocks.
 * Properties (hardness, luminance, sound) are read from SlotManager at runtime.
 */
public class SlotBlock extends Block {

    public final int slotIndex;

    public SlotBlock(int slotIndex, Settings settings) {
        super(settings);
        this.slotIndex = slotIndex;
    }

    @Override
    public float getHardness() {
        SlotManager.SlotData d = SlotManager.getBySlot("slot_" + slotIndex);
        if (d == null) return 1.5f;
        return d.hardness < 0 ? -1f : d.hardness;
    }

    @Override
    public float getBlastResistance() {
        SlotManager.SlotData d = SlotManager.getBySlot("slot_" + slotIndex);
        if (d == null) return 6.0f;
        return d.hardness < 0 ? 3600000f : d.hardness * 3f;
    }

    @Override
    public BlockSoundGroup getSoundGroup(BlockState state) {
        SlotManager.SlotData d = SlotManager.getBySlot("slot_" + slotIndex);
        if (d == null) return BlockSoundGroup.STONE;
        return switch (d.soundType) {
            case "wood"   -> BlockSoundGroup.WOOD;
            case "grass"  -> BlockSoundGroup.GRASS;
            case "metal"  -> BlockSoundGroup.METAL;
            case "glass"  -> BlockSoundGroup.GLASS;
            case "sand"   -> BlockSoundGroup.SAND;
            case "wool"   -> BlockSoundGroup.WOOL;
            case "gravel" -> BlockSoundGroup.GRAVEL;
            default       -> BlockSoundGroup.STONE;
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BlockItem (item form of the block)
    // ─────────────────────────────────────────────────────────────────────────

    public static class SlotItem extends BlockItem {
        public final int slotIndex;

        public SlotItem(SlotBlock block, Item.Settings settings) {
            super(block, settings);
            this.slotIndex = block.slotIndex;
        }
    }
}
