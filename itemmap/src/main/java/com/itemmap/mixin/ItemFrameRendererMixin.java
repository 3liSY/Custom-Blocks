package com.itemmap.mixin;

import com.itemmap.item.ItemMapItem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.ItemFrameEntityRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
@Mixin(ItemFrameEntityRenderer.class)
public abstract class ItemFrameRendererMixin extends EntityRenderer<ItemFrameEntity> {

    protected ItemFrameRendererMixin(EntityRendererFactory.Context ctx) { super(ctx); }

    @Inject(
        method = "render(Lnet/minecraft/entity/Entity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onRender(Entity entity, float yaw, float tickDelta,
                          MatrixStack matrices, VertexConsumerProvider vcp,
                          int light, CallbackInfo ci) {

        if (!(entity instanceof ItemFrameEntity frame)) return;
        ItemStack held = frame.getHeldItemStack();
        if (held == null || held.isEmpty()) return;
        if (!(held.getItem() instanceof ItemMapItem)) return;

        String targetId = ItemMapItem.getTargetId(held);
        if (targetId == null) return;

        ci.cancel();

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null) return;

        matrices.push();

        // Draw wooden frame border
        drawColoredQuad(matrices, vcp, 0xFF6B4226, -0.5625f, -0.5625f, 1.125f, 1.125f, light);

        // Move slightly in front of frame face
        matrices.translate(0f, 0f, 0.078125f);

        // Draw the filled_map background
        ItemStack mapStack = new ItemStack(Items.FILLED_MAP);
        BakedModel mapModel = mc.getItemRenderer().getModel(mapStack, mc.world, null, 0);
        matrices.push();
        matrices.scale(0.5f, 0.5f, 0.5f); // map in FIXED mode is large, scale to fit frame
        mc.getItemRenderer().renderItem(mapStack, ModelTransformationMode.FIXED,
            false, matrices, vcp, light, OverlayTexture.DEFAULT_UV, mapModel);
        matrices.pop();

        // Draw target item on top of map
        ItemStack targetStack = new ItemStack(
            Registries.ITEM.get(Identifier.of(targetId)));
        if (!targetStack.isEmpty()) {
            BakedModel targetModel = mc.getItemRenderer().getModel(targetStack, mc.world, null, 0);
            matrices.push();
            matrices.translate(0f, 0f, 0.01f);
            matrices.scale(0.4f, 0.4f, 0.001f);
            mc.getItemRenderer().renderItem(targetStack, ModelTransformationMode.GUI,
                false, matrices, vcp, light, OverlayTexture.DEFAULT_UV, targetModel);
            matrices.pop();
        }

        matrices.pop();
    }

    private void drawColoredQuad(MatrixStack matrices, VertexConsumerProvider vcp,
                                  int argb, float x, float y, float w, float h, int light) {
        int a = (argb >> 24) & 0xFF; if (a == 0) return;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8)  & 0xFF;
        int b = argb         & 0xFF;
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityTranslucentCull(
            Identifier.of("minecraft", "textures/misc/white.png")));
        Matrix4f m = matrices.peek().getPositionMatrix();
        vc.vertex(m, x,   y,   0).color(r,g,b,a).texture(0,0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0,0,1);
        vc.vertex(m, x+w, y,   0).color(r,g,b,a).texture(1,0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0,0,1);
        vc.vertex(m, x+w, y+h, 0).color(r,g,b,a).texture(1,1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0,0,1);
        vc.vertex(m, x,   y+h, 0).color(r,g,b,a).texture(0,1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0,0,1);
    }
}
