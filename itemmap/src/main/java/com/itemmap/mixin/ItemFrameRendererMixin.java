package com.itemmap.mixin;

import com.itemmap.client.renderer.FrameRenderManager;
import com.itemmap.item.ItemMapItem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.ItemFrameEntityRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
        boolean is3D = ItemMapItem.is3D(held);

        ci.cancel();

        matrices.push();

        // ── Draw wooden frame border ──────────────────────────────────────────
        // Draw behind everything (z=0, before translate)
        renderColoredQuad(matrices, vcp, 0xFF6B4226, -0.5625f, -0.5625f, 1.125f, 1.125f, light);

        // Translate slightly in front of the frame surface
        matrices.translate(0, 0, 0.078125f);

        if (is3D) {
            float angle = FrameRenderManager.getSpinAngle(frame.getId(), true);
            render3DSpin(targetId, angle, matrices, vcp, light, tickDelta);
        } else {
            renderFlat2D(targetId, matrices, vcp, light);
        }

        // Label below frame
        String label = held.getName().getString();
        renderLabel(label, matrices, vcp, light);

        matrices.pop();
    }

    // ── Flat 2D ───────────────────────────────────────────────────────────────

    private void renderFlat2D(String targetId, MatrixStack matrices,
                               VertexConsumerProvider vcp, int light) {
        // Try custom uploaded texture first
        Identifier texId = FrameRenderManager.getCustomTexture(targetId);

        // Fall back to vanilla sprite via item renderer baked model
        // This is the correct way - use the item's baked texture atlas sprite
        if (texId == null) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null) return;
            Item item = Registries.ITEM.get(Identifier.of(targetId));
            if (item == null || item == net.minecraft.item.Items.AIR) return;
            ItemStack stack = new ItemStack(item);

            matrices.push();
            matrices.scale(0.0625f, 0.0625f, 0.0625f);
            mc.getItemRenderer().renderItem(
                stack,
                ModelTransformationMode.GUI,
                light,
                OverlayTexture.DEFAULT_UV,
                matrices, vcp, mc.world, 0
            );
            matrices.pop();
            return;
        }

        renderTexturedQuad(matrices, vcp, texId, -0.5f, -0.5f, 1f, 1f, light);
    }

    // ── 3D Spin ───────────────────────────────────────────────────────────────

    private void render3DSpin(String targetId, float spinAngle,
                               MatrixStack matrices, VertexConsumerProvider vcp,
                               int light, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        Item item = Registries.ITEM.get(Identifier.of(targetId));
        if (item == null || item == net.minecraft.item.Items.AIR) return;
        ItemStack renderStack = new ItemStack(item);

        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(spinAngle));
        matrices.scale(0.5f, 0.5f, 0.5f);
        mc.getItemRenderer().renderItem(
            renderStack,
            ModelTransformationMode.GROUND,
            light,
            OverlayTexture.DEFAULT_UV,
            matrices, vcp, mc.world, 0
        );
        matrices.pop();
    }

    // ── Label ─────────────────────────────────────────────────────────────────
    // Fix: item frame faces vary direction. We need to render text BELOW the frame
    // in world space. The frame's matrix already accounts for facing direction,
    // so we render at negative Y (below center) and flip Y so text reads correctly.

    private void renderLabel(String text, MatrixStack matrices,
                              VertexConsumerProvider vcp, int light) {
        matrices.push();
        // Move below the frame
        matrices.translate(0, 0.7f, 0.01f);
        // Flip Y so text is not upside-down (frame matrix flips Y for wall-mounted frames)
        matrices.scale(0.02f, -0.02f, 0.02f);
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        if (tr == null) { matrices.pop(); return; }
        float w = tr.getWidth(text);
        tr.drawWithOutline(
            net.minecraft.text.Text.literal(text).asOrderedText(),
            -w / 2f, -4f,
            0xFFFFFFFF, 0xFF000000,
            matrices.peek().getPositionMatrix(), vcp, light
        );
        matrices.pop();
    }

    // ── Quad rendering ────────────────────────────────────────────────────────

    private void renderTexturedQuad(MatrixStack matrices, VertexConsumerProvider vcp,
                                     Identifier texture,
                                     float x, float y, float w, float h, int light) {
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityTranslucentCull(texture));
        Matrix4f m = matrices.peek().getPositionMatrix();
        // Correct winding: BL, BR, TR, TL
        vc.vertex(m, x,   y,   0).color(255,255,255,255).texture(0,0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0,0,1);
        vc.vertex(m, x+w, y,   0).color(255,255,255,255).texture(1,0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0,0,1);
        vc.vertex(m, x+w, y+h, 0).color(255,255,255,255).texture(1,1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0,0,1);
        vc.vertex(m, x,   y+h, 0).color(255,255,255,255).texture(0,1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0,0,1);
    }

    private static final Identifier WHITE_TEX =
        Identifier.of("minecraft", "textures/misc/white.png");

    private void renderColoredQuad(MatrixStack matrices, VertexConsumerProvider vcp,
                                    int argb, float x, float y, float w, float h, int light) {
        int a = (argb >> 24) & 0xFF; if (a == 0) return;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8)  & 0xFF;
        int b = argb         & 0xFF;
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityTranslucentCull(WHITE_TEX));
        Matrix4f m = matrices.peek().getPositionMatrix();
        vc.vertex(m, x,   y,   0).color(r,g,b,a).texture(0,0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0,0,1);
        vc.vertex(m, x+w, y,   0).color(r,g,b,a).texture(1,0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0,0,1);
        vc.vertex(m, x+w, y+h, 0).color(r,g,b,a).texture(1,1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0,0,1);
        vc.vertex(m, x,   y+h, 0).color(r,g,b,a).texture(0,1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0,0,1);
    }
}
