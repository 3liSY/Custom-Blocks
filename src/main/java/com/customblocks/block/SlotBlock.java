package com.customblocks.block;

import com.customblocks.SlotManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSoundGroup;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;

public class SlotBlock extends Block {

    private final int slotIndex;

    public SlotBlock(int slotIndex, Settings settings) {
        super(settings);
        this.slotIndex = slotIndex;
    }

    public int    getSlotIndex() { return slotIndex; }
    public String getSlotKey()   { return "slot_" + slotIndex; }

    @Override
    public MutableText getName() {
        String name = SlotManager.getDisplayName(getSlotKey());
        return Text.literal(name != null ? name : "Custom Block " + slotIndex);
    }

    /** Dynamic sound group — reads from SlotManager at runtime */
    @Override
    public BlockSoundGroup getSoundGroup(BlockState state) {
        SlotManager.SlotData d = SlotManager.getBySlot(getSlotKey());
        if (d == null) return BlockSoundGroup.STONE;
        return switch (d.soundType) {
            case "wood"  -> BlockSoundGroup.WOOD;
            case "grass" -> BlockSoundGroup.GRASS;
            case "metal" -> BlockSoundGroup.METAL;
            case "glass" -> BlockSoundGroup.GLASS;
            case "sand"  -> BlockSoundGroup.SAND;
            case "wool"  -> BlockSoundGroup.WOOL;
            default      -> BlockSoundGroup.STONE;
        };
    }

    /** Dynamic hardness — reads from SlotManager at runtime */
    @Override
    public float calcBlockBreakingDelta(BlockState state, PlayerEntity player, BlockView world, BlockPos pos) {
        SlotManager.SlotData d = SlotManager.getBySlot(getSlotKey());
        float hardness = d != null ? d.hardness : 1.5f;
        if (hardness < 0) return 0f; // unbreakable
        if (hardness == 0) return 1f; // instant break
        float speed = player.getBlockBreakingSpeed(state);
        // Rough match of vanilla formula
        boolean correctTool = speed > 1.0f;
        return (correctTool ? speed / hardness / 30f : 1f / hardness / 100f);
    }

    // ── Inner item class ──────────────────────────────────────────────────────

    public static class SlotItem extends BlockItem {
        private final int slotIndex;

        public SlotItem(SlotBlock block, Item.Settings settings) {
            super(block, settings);
            this.slotIndex = block.getSlotIndex();
        }

        private String getSlotKey() { return "slot_" + slotIndex; }

        @Override
        public Text getName() {
            String name = SlotManager.getDisplayName(getSlotKey());
            return Text.literal(name != null ? name : "Custom Block");
        }

        @Override
        public Text getName(ItemStack stack) { return getName(); }
    }
}
