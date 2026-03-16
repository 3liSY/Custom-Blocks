package com.hangman.client.mixin;

import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;

/** Placeholder client mixin required by the mixin config. */
@Mixin(GameRenderer.class)
public class GameRendererMixin {
    // No injections needed — overlay rendering is handled via HudRenderCallback.
}
