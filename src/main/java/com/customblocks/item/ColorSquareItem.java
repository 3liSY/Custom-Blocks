package com.customblocks.item;

import com.customblocks.CustomBlocksMod;
import com.customblocks.SlotManager;
import com.customblocks.block.SlotBlock;
import com.customblocks.block.UndoHistory;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Collection;

/**
 * Right-click on any CustomBlock to swap it to the color variant of the same block.
 * Handles color tokens in any position: prefix, suffix, or middle.
 * Example: yellow square + 21_black_u  →  world block swaps to 21_yellow_u
 */
public class ColorSquareItem extends Item {

    private static final String[] COLORS = {
        "black", "yellow", "green", "white", "red", "blue", "purple", "orange", "pink", "gray"
    };

    private final String colorToken; // e.g. "yellow"
    private final String colorName;  // e.g. "Yellow"

    public ColorSquareItem(String colorToken, String colorName, Settings settings) {
        super(settings);
        this.colorToken = colorToken;
        this.colorName  = colorName;
    }

    @Override
    public Text getName() { return Text.literal(colorName + " Square"); }

    @Override
    public Text getName(ItemStack stack) { return getName(); }

    @Override
    public ActionResult useOnBlock(ItemUsageContext ctx) {
        World world   = ctx.getWorld();
        BlockPos pos  = ctx.getBlockPos();
        PlayerEntity player = ctx.getPlayer();

        if (world.isClient) return ActionResult.PASS;

        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof SlotBlock sb)) return ActionResult.PASS;

        if (player != null && !player.hasPermissionLevel(2)) {
            player.sendMessage(Text.literal("§c[CustomBlocks] You need OP (level 2) to use color squares."), true);
            return ActionResult.FAIL;
        }

        SlotManager.SlotData current = SlotManager.getBySlot(sb.getSlotKey());
        if (current == null) return ActionResult.PASS;

        String currentId    = current.customId;
        String currentColor = colorOf(currentId);

        // Already this color?
        if (colorToken.equals(currentColor)) {
            if (player != null)
                player.sendMessage(Text.literal("§7[CustomBlocks] Already " + colorName + "."), true);
            return ActionResult.SUCCESS;
        }

        // Find the best matching variant by scanning all registered blocks
        SlotManager.SlotData target = findColorVariant(currentId, currentColor, colorToken);
        if (target == null) {
            if (player != null)
                player.sendMessage(
                    Text.literal("§c[CustomBlocks] No " + colorName + " variant found for '" + currentId + "'."), true);
            return ActionResult.FAIL;
        }

        // Push undo before swapping
        if (player instanceof net.minecraft.server.network.ServerPlayerEntity sp)
            UndoHistory.push(sp, pos, sb.getSlotKey(), CustomBlocksMod.SLOT_BLOCKS[target.index].getSlotKey());

        world.setBlockState(pos, CustomBlocksMod.SLOT_BLOCKS[target.index].getDefaultState(), 3);

        if (player != null)
            player.sendMessage(
                Text.literal("§a[CustomBlocks] Swapped to §f" + target.displayName + "§a!"), true);

        return ActionResult.SUCCESS;
    }

    // ── Color matching helpers ────────────────────────────────────────────────

    /**
     * Scan all registered blocks to find one whose ID, when its color token
     * is replaced with currentColor, equals currentId. This handles any naming
     * pattern (prefix, suffix, middle) without guessing.
     */
    private static SlotManager.SlotData findColorVariant(String currentId, String currentColor, String newColor) {
        // Strategy 1: direct replace if current color is known
        if (currentColor != null) {
            String candidate = replaceColor(currentId, currentColor, newColor);
            SlotManager.SlotData d = SlotManager.getById(candidate);
            if (d != null) return d;
        }

        // Strategy 2: scan all blocks — find one whose base (color stripped) matches ours
        String currentBase = currentColor != null ? stripColor(currentId, currentColor) : currentId;
        for (SlotManager.SlotData d : SlotManager.allSlots()) {
            String c = colorOf(d.customId);
            if (c == null || !c.equals(newColor)) continue;
            if (stripColor(d.customId, c).equals(currentBase)) return d;
        }
        return null;
    }

    /** Extract color token from ID, or null if none found. */
    public static String colorOf(String id) {
        for (String c : COLORS) {
            if (id.equals(c))              return c;
            if (id.startsWith(c + "_"))    return c;
            if (id.endsWith("_" + c))      return c;
            if (id.contains("_" + c + "_")) return c;
        }
        return null;
    }

    /** Strip color token from ID, leaving the base without leading/trailing underscores. */
    private static String stripColor(String id, String color) {
        if (id.equals(color))                return "";
        if (id.startsWith(color + "_"))      return id.substring(color.length() + 1);
        if (id.endsWith("_" + color))        return id.substring(0, id.length() - color.length() - 1);
        String mid = "_" + color + "_";
        int idx = id.indexOf(mid);
        if (idx >= 0) return id.substring(0, idx) + id.substring(idx + color.length() + 1);
        return id;
    }

    /** Replace one color token with another, preserving position. */
    public static String replaceColor(String id, String oldColor, String newColor) {
        if (id.equals(oldColor))            return newColor;
        if (id.startsWith(oldColor + "_"))  return newColor + id.substring(oldColor.length());
        if (id.endsWith("_" + oldColor))    return id.substring(0, id.length() - oldColor.length()) + newColor;
        String mid = "_" + oldColor + "_";
        int idx = id.indexOf(mid);
        if (idx >= 0) return id.substring(0, idx + 1) + newColor + id.substring(idx + 1 + oldColor.length());
        return id;
    }
}
