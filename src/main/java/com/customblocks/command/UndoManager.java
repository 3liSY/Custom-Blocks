package com.customblocks.command;

import com.customblocks.SlotManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks undo/redo stacks per player.
 * Each /customblock command pushes to undo.
 * /undo pops from undo and pushes to redo.
 * /redo pops from redo and pushes back to undo.
 */
public class UndoManager {

    public enum ActionType {
        CREATE,
        DELETE,
        RENAME,
        RETEXTURE,
        SETGLOW,
        SETHARDNESS,
        SETSOUND,
        SETTABICON,
    }

    public static class UndoEntry {
        public final ActionType type;
        public final String     customId;
        public final String     displayName;
        public final byte[]     texture;
        public final int        lightLevel;
        public final float      hardness;
        public final String     soundType;
        public final int        slotIndex;
        public final byte[]     prevTabIcon;

        private UndoEntry(ActionType type, String customId, String displayName,
                          byte[] texture, int lightLevel, float hardness, String soundType,
                          int slotIndex, byte[] prevTabIcon) {
            this.type        = type;
            this.customId    = customId;
            this.displayName = displayName;
            this.texture     = texture;
            this.lightLevel  = lightLevel;
            this.hardness    = hardness;
            this.soundType   = soundType;
            this.slotIndex   = slotIndex;
            this.prevTabIcon = prevTabIcon;
        }

        // ── Factories ────────────────────────────────────────────────────────
        public static UndoEntry forCreate(String customId) {
            return new UndoEntry(ActionType.CREATE, customId, null, null, 0, 0, null, -1, null);
        }
        public static UndoEntry forDelete(SlotManager.SlotData d) {
            return new UndoEntry(ActionType.DELETE, d.customId, d.displayName,
                    d.texture, d.lightLevel, d.hardness, d.soundType, d.index, null);
        }
        public static UndoEntry forRename(String customId, String oldName) {
            return new UndoEntry(ActionType.RENAME, customId, oldName, null, 0, 0, null, -1, null);
        }
        public static UndoEntry forRetexture(String customId, byte[] oldTexture) {
            return new UndoEntry(ActionType.RETEXTURE, customId, null, oldTexture, 0, 0, null, -1, null);
        }
        public static UndoEntry forSetGlow(String customId, int oldLevel) {
            return new UndoEntry(ActionType.SETGLOW, customId, null, null, oldLevel, 0, null, -1, null);
        }
        public static UndoEntry forSetHardness(String customId, float oldHardness) {
            return new UndoEntry(ActionType.SETHARDNESS, customId, null, null, 0, oldHardness, null, -1, null);
        }
        public static UndoEntry forSetSound(String customId, String oldSound) {
            return new UndoEntry(ActionType.SETSOUND, customId, null, null, 0, 0, oldSound, -1, null);
        }
        public static UndoEntry forSetTabIcon(byte[] prevTabIcon) {
            return new UndoEntry(ActionType.SETTABICON, null, null, null, 0, 0, null, -1, prevTabIcon);
        }
    }

    // One undo entry + one redo entry per player (single-level undo/redo)
    private static final Map<UUID, UndoEntry> UNDO_STACK = new ConcurrentHashMap<>();
    private static final Map<UUID, UndoEntry> REDO_STACK = new ConcurrentHashMap<>();

    /** Record a new action — clears redo since history branched. */
    public static void record(UUID player, UndoEntry entry) {
        UNDO_STACK.put(player, entry);
        REDO_STACK.remove(player); // new action invalidates redo
    }

    /** Pop undo entry (returns null if none). */
    public static UndoEntry popUndo(UUID player) {
        return UNDO_STACK.remove(player);
    }

    /** Push an entry onto the redo stack (called after a successful undo). */
    public static void pushRedo(UUID player, UndoEntry entry) {
        REDO_STACK.put(player, entry);
    }

    /** Pop redo entry (returns null if none). */
    public static UndoEntry popRedo(UUID player) {
        return REDO_STACK.remove(player);
    }

    public static boolean hasUndo(UUID player) { return UNDO_STACK.containsKey(player); }
    public static boolean hasRedo(UUID player) { return REDO_STACK.containsKey(player); }

    // Legacy alias kept so nothing else breaks
    public static void record(UUID player, UndoEntry entry, boolean ignored) { record(player, entry); }
    public static boolean has(UUID player) { return hasUndo(player); }
    public static UndoEntry pop(UUID player) { return popUndo(player); }
}
