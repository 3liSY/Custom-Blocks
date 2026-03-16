package com.itemmap.manager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player undo/redo stack for frame edits.
 * Each entry is a snapshot of a FrameData before a change was made.
 */
public class UndoManager {

    private static final int MAX_STACK = 50;

    private record Entry(FrameData before, FrameData after) {}

    private static final Map<UUID, Deque<Entry>> UNDO = new ConcurrentHashMap<>();
    private static final Map<UUID, Deque<Entry>> REDO = new ConcurrentHashMap<>();

    /** Record an action. before = state before, after = state after. */
    public static void push(UUID player, FrameData before, FrameData after) {
        Deque<Entry> stack = UNDO.computeIfAbsent(player, k -> new ArrayDeque<>());
        stack.push(new Entry(before.copy(), after.copy()));
        if (stack.size() > MAX_STACK) {
            // Remove oldest
            Deque<Entry> trimmed = new ArrayDeque<>();
            Iterator<Entry> it = stack.iterator();
            int i = 0;
            while (it.hasNext() && i < MAX_STACK) { trimmed.addLast(it.next()); i++; }
            UNDO.put(player, trimmed);
        }
        // clear redo on new action
        REDO.remove(player);
    }

    /** Undo last action. Returns the FrameData to restore (the 'before'), or null. */
    public static FrameData undo(UUID player) {
        Deque<Entry> stack = UNDO.get(player);
        if (stack == null || stack.isEmpty()) return null;
        Entry e = stack.pop();
        REDO.computeIfAbsent(player, k -> new ArrayDeque<>()).push(e);
        return e.before().copy();
    }

    /** Redo last undone action. Returns the FrameData to restore (the 'after'), or null. */
    public static FrameData redo(UUID player) {
        Deque<Entry> stack = REDO.get(player);
        if (stack == null || stack.isEmpty()) return null;
        Entry e = stack.pop();
        UNDO.computeIfAbsent(player, k -> new ArrayDeque<>()).push(e);
        return e.after().copy();
    }

    public static boolean canUndo(UUID player) {
        Deque<Entry> s = UNDO.get(player);
        return s != null && !s.isEmpty();
    }

    public static boolean canRedo(UUID player) {
        Deque<Entry> s = REDO.get(player);
        return s != null && !s.isEmpty();
    }
}
