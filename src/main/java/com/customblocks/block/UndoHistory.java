package com.customblocks.block;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player undo/redo stack for color-square swaps.
 * /cb undo  — reverts last swap
 * /cb redo  — re-applies last undone swap
 */
public class UndoHistory {

    private static final int MAX = 50;

    private record Entry(BlockPos pos, String fromSlot, String toSlot) {}

    private static final Map<UUID, Deque<Entry>> UNDO = new ConcurrentHashMap<>();
    private static final Map<UUID, Deque<Entry>> REDO = new ConcurrentHashMap<>();

    public static void push(ServerPlayerEntity player, BlockPos pos, String fromSlot, String toSlot) {
        UUID id = player.getUuid();
        Deque<Entry> undo = UNDO.computeIfAbsent(id, k -> new ArrayDeque<>());
        undo.push(new Entry(pos, fromSlot, toSlot));
        if (undo.size() > MAX) undo.removeLast();
        REDO.computeIfAbsent(id, k -> new ArrayDeque<>()).clear();
    }

    public static boolean undo(ServerPlayerEntity player) {
        Deque<Entry> undo = UNDO.getOrDefault(player.getUuid(), new ArrayDeque<>());
        if (undo.isEmpty()) return false;
        Entry e = undo.pop();
        apply(player, e.pos(), e.fromSlot());
        REDO.computeIfAbsent(player.getUuid(), k -> new ArrayDeque<>()).push(e);
        return true;
    }

    public static boolean redo(ServerPlayerEntity player) {
        Deque<Entry> redo = REDO.getOrDefault(player.getUuid(), new ArrayDeque<>());
        if (redo.isEmpty()) return false;
        Entry e = redo.pop();
        apply(player, e.pos(), e.toSlot());
        UNDO.computeIfAbsent(player.getUuid(), k -> new ArrayDeque<>()).push(e);
        return true;
    }

    private static void apply(ServerPlayerEntity player, BlockPos pos, String slotKey) {
        var world = player.getWorld();
        if (world == null) return;
        for (var block : com.customblocks.CustomBlocksMod.SLOT_BLOCKS) {
            if (block != null && block.getSlotKey().equals(slotKey)) {
                world.setBlockState(pos, block.getDefaultState(), 3);
                return;
            }
        }
    }
}
