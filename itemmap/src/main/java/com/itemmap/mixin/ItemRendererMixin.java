package com.itemmap.mixin;

import com.itemmap.client.renderer.ItemMapRenderer;
import com.itemmap.item.ItemMapItem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ItemRenderer.class)
public class ItemRendererMixin {

    // Re-entrancy guard: prevents infinite recursion when our renderer
    // calls renderItem internally for the target item or map model
    private static final ThreadLocal<Boolean> RENDERING = ThreadLocal.withInitial(() -> false);

    @Inject(
        method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;IILnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/world/World;I)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onRenderItem(ItemStack stack, ModelTransformationMode mode,
                               int light, int overlay, MatrixStack matrices,
                               VertexConsumerProvider vcp, World world,
                               int seed, CallbackInfo ci) {

        // Prevent recursion
        if (RENDERING.get()) return;

        if (!(stack.getItem() instanceof ItemMapItem)) return;

        // Let item frame renderer handle these modes — we don't touch them
        if (mode == ModelTransformationMode.FIXED ||
            mode == ModelTransformationMode.GROUND) return;

        String targetId = ItemMapItem.getTargetId(stack);
        if (targetId == null) return;
        boolean is3D = ItemMapItem.is3D(stack);

        ci.cancel();

        RENDERING.set(true);
        try {
            ItemMapRenderer.renderMapItem(stack, targetId, is3D, mode,
                matrices, vcp, world, light, overlay);
        } finally {
            RENDERING.set(false);
        }
    }
}
