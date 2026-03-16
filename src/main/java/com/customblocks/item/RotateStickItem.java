package com.customblocks.item;

import net.minecraft.block.BlockState;
import net.minecraft.block.FacingBlock;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.PillarBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Property;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.List;

public class RotateStickItem extends Item {

    public static final String NBT_KEY = "customblocks_rotate_stick";

    public RotateStickItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(net.minecraft.item.ItemUsageContext ctx) {
        World world = ctx.getWorld();
        BlockPos pos = ctx.getBlockPos();
        PlayerEntity player = ctx.getPlayer();

        if (world.isClient() || player == null) return ActionResult.SUCCESS;
        if (!player.hasPermissionLevel(2)) {
            player.sendMessage(Text.literal("§c[CustomBlocks] You need OP to use the rotate stick."), true);
            return ActionResult.FAIL;
        }

        BlockState state = world.getBlockState(pos);
        BlockState rotated = rotate(state, player.isSneaking());

        if (rotated == state) {
            player.sendMessage(Text.literal("§7[CustomBlocks] This block can't be rotated."), true);
            return ActionResult.FAIL;
        }

        world.setBlockState(pos, rotated);
        String dir = getDirectionLabel(rotated);
        player.sendMessage(Text.literal("§a[CustomBlocks] Rotated" + (dir.isEmpty() ? "." : " → " + dir)), true);
        return ActionResult.SUCCESS;
    }

    /**
     * Tries every known direction/axis property and cycles it.
     * sneak = reverse direction
     */
    private static BlockState rotate(BlockState state, boolean reverse) {
        // 1. Try FacingBlock (full 6-way direction)
        for (Property<?> prop : state.getProperties()) {
            if (prop instanceof DirectionProperty dp) {
                Direction current = state.get(dp);
                List<Direction> values = new java.util.ArrayList<>(dp.getValues());
                int idx = values.indexOf(current);
                int next = reverse
                        ? (idx - 1 + values.size()) % values.size()
                        : (idx + 1) % values.size();
                return state.with(dp, values.get(next));
            }
        }
        // 2. Try PillarBlock (axis: X/Y/Z)
        for (Property<?> prop : state.getProperties()) {
            if (prop.getName().equals("axis") && prop instanceof EnumProperty<?> ep) {
                List<Comparable> values = new java.util.ArrayList<>(ep.getValues());
                Comparable current = state.get((EnumProperty) prop);
                int idx = values.indexOf(current);
                int next = reverse
                        ? (idx - 1 + values.size()) % values.size()
                        : (idx + 1) % values.size();
                return state.with((EnumProperty) prop, values.get(next));
            }
        }
        return state;
    }

    private static String getDirectionLabel(BlockState state) {
        for (Property<?> prop : state.getProperties()) {
            if (prop instanceof DirectionProperty dp) {
                return state.get(dp).getName().toUpperCase();
            }
            if (prop.getName().equals("axis")) {
                return "AXIS:" + state.get((EnumProperty) prop).toString().toUpperCase();
            }
        }
        return "";
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext ctx, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("§7Right-click a block to rotate it"));
        tooltip.add(Text.literal("§7Sneak + right-click to rotate backwards"));
    }
}
