package com.itemmap.mixin;

import com.itemmap.client.renderer.FrameRenderManager;
import com.itemmap.manager.FrameData;
import com.itemmap.manager.FrameManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.ItemFrameEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
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
public abstract class ItemFrameRendererMixin
        extends EntityRenderer<ItemFrameEntity> {

    protected ItemFrameRendererMixin(EntityRendererFactory.Context ctx) { super(ctx); }

    /**
     * Inject BEFORE the vanilla item render inside the frame.
     * If we have FrameData for this frame, we render our custom version and cancel vanilla.
     */
    @Inject(
        method = "render(Lnet/minecraft/entity/Entity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onRender(net.minecraft.entity.Entity entity, float yaw, float tickDelta,
                          MatrixStack matrices, VertexConsumerProvider vcp,
                          int light, CallbackInfo ci) {

        if (!(entity instanceof ItemFrameEntity frame)) return;
        ItemStack held = frame.getHeldItemStack();
        if (held == null || held.isEmpty()) return;

        FrameData data = FrameManager.get(frame.getId());
        if (data == null) return; // No ItemMap data — let vanilla render

        ci.cancel(); // We're taking over rendering

        matrices.push();

        // Handle invisible frame — don't render the frame itself
        if (!data.invisible) {
            renderFrameBase(frame, matrices, vcp, light);
        }

        // Translate slightly in front of frame face
        matrices.translate(0, 0, 0.0625f);

        // Apply scale
        float s = data.scale;
        matrices.scale(s, s, s);

        // Apply padding
        if (data.padPct > 0) {
            float p = 1f - (data.padPct / 100f);
            matrices.scale(p, p, p);
        }

        switch (data.mode) {
            case FLAT_2D  -> renderFlat2D(frame, held, data, matrices, vcp, light);
            case RENDER_3D -> renderStatic3D(frame, held, data, matrices, vcp, light, tickDelta);
            case SPIN_3D   -> renderSpin3D(frame, held, data, matrices, vcp, light, tickDelta);
        }

        // Render label if set
        if (data.label != null && !data.label.isEmpty()) {
            renderLabel(data.label, matrices, vcp, light);
        } else {
            // Use item's translation key name
            String name = held.getName().getString();
            renderLabel(name, matrices, vcp, light);
        }

        // Glow outline
        if (data.glowing) {
            renderGlowOutline(frame, held, data, matrices, vcp);
        }

        matrices.pop();
    }

    // ── Render methods ────────────────────────────────────────────────────────

    private void renderFlat2D(ItemFrameEntity frame, ItemStack held, FrameData data,
                               MatrixStack matrices, VertexConsumerProvider vcp, int light) {
        Identifier texId = resolveTexture(held, data);
        if (texId == null) {
            // Fallback: render vanilla item
            renderVanillaItem(held, matrices, vcp, light);
            return;
        }

        // Render background if set
        if ((data.bgColor >>> 24) > 0) {
            renderColoredQuad(matrices, vcp, data.bgColor, -0.5f, -0.5f, 1f, 1f, light);
        }

        // Render the 2D texture filling the frame
        renderTexturedQuad(matrices, vcp, texId, -0.5f, -0.5f, 1f, 1f, light);
    }

    private void renderStatic3D(ItemFrameEntity frame, ItemStack held, FrameData data,
                                 MatrixStack matrices, VertexConsumerProvider vcp,
                                 int light, float tickDelta) {
        matrices.scale(0.5f, 0.5f, 0.5f);
        renderVanillaItem(held, matrices, vcp, light);
    }

    private void renderSpin3D(ItemFrameEntity frame, ItemStack held, FrameData data,
                               MatrixStack matrices, VertexConsumerProvider vcp,
                               int light, float tickDelta) {
        // Smooth spin angle interpolation
        float angle = data.spinAngle + data.spinSpeed * tickDelta;
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(angle));
        matrices.scale(0.5f, 0.5f, 0.5f);
        renderVanillaItem(held, matrices, vcp, light);
    }

    private void renderVanillaItem(ItemStack held, MatrixStack matrices,
                                    VertexConsumerProvider vcp, int light) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getItemRenderer() == null) return;
        mc.getItemRenderer().renderItem(held,
            net.minecraft.client.render.model.json.ModelTransformationMode.FIXED,
            light, OverlayTexture.DEFAULT_UV, matrices, vcp, mc.world, 0);
    }

    private void renderFrameBase(ItemFrameEntity frame, MatrixStack matrices,
                                  VertexConsumerProvider vcp, int light) {
        // Let vanilla render just the frame part (not the item)
        // We call super but that would cause recursion, so we just skip —
        // the frame block model is rendered by the block renderer, not entity renderer.
        // Item frames in MC are entities whose renderer handles BOTH the frame AND the item.
        // We'll render a simple dark border quad as the frame.
        renderColoredQuad(matrices, vcp, 0xFF5A3A1A, -0.5625f, -0.5625f, 1.125f, 1.125f, light);
    }

    // ── Texture resolution ────────────────────────────────────────────────────

    private Identifier resolveTexture(ItemStack held, FrameData data) {
        // 1. Custom uploaded image
        if (data.customImageId != null) {
            Identifier id = FrameRenderManager.getImageTexture(data.customImageId);
            if (id != null) return id;
        }
        // 2. Vanilla item texture
        String path = Registries.ITEM.getId(held.getItem()).getPath();
        return Identifier.of("minecraft", "textures/item/" + path + ".png");
    }

    // ── Low-level quad rendering ──────────────────────────────────────────────

    private void renderTexturedQuad(MatrixStack matrices, VertexConsumerProvider vcp,
                                     Identifier texture,
                                     float x, float y, float w, float h, int light) {
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityTranslucentCull(texture));
        Matrix4f m = matrices.peek().getPositionMatrix();
        // Two triangles forming a quad (CCW winding)
        // TL, BL, BR, TR
        vc.vertex(m, x,     y + h, 0).color(255,255,255,255).texture(0,1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0,0,1);
        vc.vertex(m, x + w, y + h, 0).color(255,255,255,255).texture(1,1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0,0,1);
        vc.vertex(m, x + w, y,     0).color(255,255,255,255).texture(1,0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0,0,1);
        vc.vertex(m, x,     y,     0).color(255,255,255,255).texture(0,0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0,0,1);
    }

    private static final Identifier WHITE_TEX = Identifier.of("minecraft", "textures/misc/white.png");

    private void renderColoredQuad(MatrixStack matrices, VertexConsumerProvider vcp,
                                    int argb, float x, float y, float w, float h, int light) {
        int a = (argb >> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8)  & 0xFF;
        int b = argb         & 0xFF;
        if (a == 0) return;
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityTranslucentCull(WHITE_TEX));
        Matrix4f m = matrices.peek().getPositionMatrix();
        vc.vertex(m, x,     y + h, 0).color(r,g,b,a).texture(0,1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0,0,1);
        vc.vertex(m, x + w, y + h, 0).color(r,g,b,a).texture(1,1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0,0,1);
        vc.vertex(m, x + w, y,     0).color(r,g,b,a).texture(1,0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0,0,1);
        vc.vertex(m, x,     y,     0).color(r,g,b,a).texture(0,0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0,0,1);
    }

    private void renderGlowOutline(ItemFrameEntity frame, ItemStack held, FrameData data,
                                    MatrixStack matrices, VertexConsumerProvider vcp) {
        // Render a slightly larger bright quad as glow
        renderColoredQuad(matrices, vcp, 0x66FFFF00, -0.52f, -0.52f, 1.04f, 1.04f,
            LightmapTextureManager.MAX_LIGHT_COORDINATE);
    }

    private void renderLabel(String text, MatrixStack matrices,
                              VertexConsumerProvider vcp, int light) {
        matrices.push();
        matrices.translate(0, -0.6f, 0.01f);
        matrices.scale(0.025f, 0.025f, 0.025f);
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        if (tr == null) { matrices.pop(); return; }
        int w = tr.getWidth(text);
        // Background
        tr.drawWithOutline(
            net.minecraft.text.Text.literal(text).asOrderedText(),
            -w / 2f, -4f,
            0xFFFFFFFF, 0xFF000000,
            matrices.peek().getPositionMatrix(), vcp, light
        );
        matrices.pop();
    }
}
