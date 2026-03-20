package com.itemmap.mixin;

import com.itemmap.item.ItemMapItem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
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

    // Target the public renderItem overload WITHOUT BakedModel
    // 1.21.1 Yarn: renderItem(ItemStack, ModelTransformationMode, int, int, MatrixStack, VCP, World, int)
    @Inject(
        method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;IILnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/world/World;I)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onRenderItem(ItemStack stack, ModelTransformationMode mode,
                               int light, int overlay, MatrixStack matrices,
                               VertexConsumerProvider vcp, World world,
                               int seed, CallbackInfo ci) {

        if (RENDERING.get()) return;
        if (!(stack.getItem() instanceof ItemMapItem)) return;
        if (mode == ModelTransformationMode.FIXED ||
            mode == ModelTransformationMode.GROUND) return;

        String targetId = ItemMapItem.getTargetId(stack);
        if (targetId == null) return;

        ci.cancel();
        RENDERING.set(true);
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null) return;

            // Render vanilla filled_map as background
            mc.getItemRenderer().renderItem(new ItemStack(Items.FILLED_MAP),
                mode, light, overlay, matrices, vcp, world, seed);

            // Render target item on top
            ItemStack targetStack = new ItemStack(
                net.minecraft.registry.Registries.ITEM.get(
                    net.minecraft.util.Identifier.of(targetId)));
            if (!targetStack.isEmpty()) {
                matrices.push();
                matrices.translate(0f, 0f, 0.001f);
                matrices.scale(0.875f, 0.875f, 0.001f);
                mc.getItemRenderer().renderItem(targetStack,
                    ModelTransformationMode.GUI,
                    light, OverlayTexture.DEFAULT_UV,
                    matrices, vcp, world, 0);
                matrices.pop();
            }
        } finally {
            RENDERING.set(false);
        }
    }
}
