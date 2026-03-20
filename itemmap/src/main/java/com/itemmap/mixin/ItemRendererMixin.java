package com.itemmap.mixin;

import com.itemmap.item.ItemMapItem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ItemRenderer.class)
public class ItemRendererMixin {

    private static final ThreadLocal<Boolean> RENDERING = ThreadLocal.withInitial(() -> false);

    // 1.21.1 Yarn signature:
    // renderItem(ItemStack, ModelTransformationMode, boolean, MatrixStack, VertexConsumerProvider, int, int, BakedModel)
    @Inject(
        method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/render/model/BakedModel;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onRenderItem(ItemStack stack, ModelTransformationMode mode,
                               boolean leftHanded, MatrixStack matrices,
                               VertexConsumerProvider vcp, int light, int overlay,
                               BakedModel model, CallbackInfo ci) {

        if (RENDERING.get()) return;
        if (!(stack.getItem() instanceof ItemMapItem)) return;
        // Let item frame renderer handle frame display
        if (mode == ModelTransformationMode.FIXED ||
            mode == ModelTransformationMode.GROUND) return;

        String targetId = ItemMapItem.getTargetId(stack);
        if (targetId == null) return;

        ci.cancel();
        RENDERING.set(true);
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null) return;

            // Step 1: render vanilla filled_map as the map background
            ItemStack mapStack = new ItemStack(Items.FILLED_MAP);
            BakedModel mapModel = mc.getItemRenderer().getModel(mapStack, mc.world, null, 0);
            mc.getItemRenderer().renderItem(mapStack, mode, leftHanded,
                matrices, vcp, light, overlay, mapModel);

            // Step 2: render the target item on top in GUI mode, scaled to fit map face
            ItemStack targetStack = new ItemStack(
                net.minecraft.registry.Registries.ITEM.get(
                    net.minecraft.util.Identifier.of(targetId)));
            if (!targetStack.isEmpty()) {
                matrices.push();
                matrices.translate(0f, 0f, 0.001f);
                matrices.scale(0.875f, 0.875f, 0.001f);
                BakedModel targetModel = mc.getItemRenderer().getModel(targetStack, mc.world, null, 0);
                mc.getItemRenderer().renderItem(targetStack, ModelTransformationMode.GUI,
                    false, matrices, vcp, light, OverlayTexture.DEFAULT_UV, targetModel);
                matrices.pop();
            }

        } finally {
            RENDERING.set(false);
        }
    }
}
