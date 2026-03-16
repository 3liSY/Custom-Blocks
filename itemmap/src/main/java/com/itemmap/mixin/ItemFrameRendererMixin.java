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

        // Get target item
        String targetId = ItemMapItem.getTargetId(held);
        if (targetId == null) return;
        boolean is3D = ItemMapItem.is3D(held);

        ci.cancel(); // Take over rendering entirely

        matrices.push();

        // Render the wooden frame border
        renderWoodenFrame(matrices, vcp, light);

        // Move slightly in front of frame face
        matrices.translate(0, 0, 0.078125f);

        // Get or create spin angle for this frame
        float spinAngle = FrameRenderManager.getSpinAngle(frame.getId(), is3D);

        if (is3D) {
            render3DSpin(targetId, spinAngle, matrices, vcp, light);
        } else {
            renderFlat2D(targetId, matrices, vcp, light);
        }

        // Label: item name (or "X 3D")
        String label = held.getName().getString();
        renderLabel(label, matrices, vcp, light);

        matrices.pop();
    }

    // ── Flat 2D: fill the frame with the item's sprite texture ────────────────

    private void renderFlat2D(String targetId, MatrixStack matrices,
                               VertexConsumerProvider vcp, int light) {
        Identifier texId = resolveItemTexture(targetId);
        if (texId == null) return;
        // Fill entire frame face (-0.5 to 0.5)
        renderTexturedQuad(matrices, vcp, texId, -0.5f, -0.5f, 1f, 1f, light);
    }

    // ── 3D Spin: render item rotating like a dropped entity ───────────────────

    private void render3DSpin(String targetId, float spinAngle,
                               MatrixStack matrices, VertexConsumerProvider vcp, int light) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;

        // Resolve item
        Item item = Registries.ITEM.get(Identifier.of(targetId));
        if (item == null) return;
        ItemStack renderStack = new ItemStack(item);

        matrices.push();
        // Spin around Y axis — same as dropped item bobbing
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(spinAngle));
        // Scale to fit nicely in frame
        matrices.scale(0.6f, 0.6f, 0.6f);
        mc.getItemRenderer().renderItem(
            renderStack,
            ModelTransformationMode.GROUND,
            light,
            OverlayTexture.DEFAULT_UV,
            matrices, vcp, mc.world, 0
        );
        matrices.pop();
    }

    // ── Frame border ──────────────────────────────────────────────────────────

    private void renderWoodenFrame(MatrixStack matrices, VertexConsumerProvider vcp, int light) {
        // Oak log brown border, slightly larger than the item area
        int brown = 0xFF6B4226;
        renderColoredQuad(matrices, vcp, brown, -0.5625f, -0.5625f, 1.125f, 1.125f, light);
        // Slightly lighter inner border
        int tan = 0xFFC8A96E;
        renderColoredQuad(matrices, vcp, tan, -0.5f, -0.5f, 1f, 1f, light);
    }

    // ── Texture resolution ────────────────────────────────────────────────────

    private Identifier resolveItemTexture(String itemId) {
        // Check for custom uploaded image first
        Identifier custom = FrameRenderManager.getCustomTexture(itemId);
        if (custom != null) return custom;
        // Vanilla item texture path
        String path = itemId.contains(":") ? itemId.split(":")[1] : itemId;
        return Identifier.of("minecraft", "textures/item/" + path + ".png");
    }

    // ── Label ─────────────────────────────────────────────────────────────────

    private void renderLabel(String text, MatrixStack matrices,
                              VertexConsumerProvider vcp, int light) {
        matrices.push();
        matrices.translate(0, -0.62f, 0.01f);
        matrices.scale(0.022f, 0.022f, 0.022f);
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
        vc.vertex(m, x,     y+h, 0).color(255,255,255,255).texture(0,1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0,0,1);
        vc.vertex(m, x+w,   y+h, 0).color(255,255,255,255).texture(1,1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0,0,1);
        vc.vertex(m, x+w,   y,   0).color(255,255,255,255).texture(1,0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0,0,1);
        vc.vertex(m, x,     y,   0).color(255,255,255,255).texture(0,0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0,0,1);
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
        vc.vertex(m, x,   y+h, 0).color(r,g,b,a).texture(0,1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0,0,1);
        vc.vertex(m, x+w, y+h, 0).color(r,g,b,a).texture(1,1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0,0,1);
        vc.vertex(m, x+w, y,   0).color(r,g,b,a).texture(1,0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0,0,1);
        vc.vertex(m, x,   y,   0).color(r,g,b,a).texture(0,0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0,0,1);
    }
}
