package com.hangman.client.keybind;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public final class HangmanKeybinds {

    public static KeyBinding TOGGLE_OVERLAY;
    public static KeyBinding OPEN_GUI;
    public static KeyBinding OPEN_SETTINGS;
    public static KeyBinding FORFEIT;
    public static KeyBinding TOGGLE_CURSOR;

    public static void register() {
        TOGGLE_OVERLAY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.hangman.toggle_overlay",
            InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H,
            "key.categories.hangman"));

        OPEN_GUI = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.hangman.open_gui",
            InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G,
            "key.categories.hangman"));

        OPEN_SETTINGS = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.hangman.open_settings",
            InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J,
            "key.categories.hangman"));

        FORFEIT = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.hangman.forfeit",
            InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.hangman"));

        TOGGLE_CURSOR = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.hangman.toggle_cursor",
            InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT,
            "key.categories.hangman"));
    }
}
