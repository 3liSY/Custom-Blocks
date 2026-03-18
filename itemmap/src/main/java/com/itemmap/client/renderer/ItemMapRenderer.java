package com.itemmap.client.renderer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.World;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public class ItemMapRenderer {

    // Filled map ItemStack used as background - reuse to avoid allocation
    private static final ItemStack MAP_STACK = new ItemStack(Items.FILLED_MAP);

    /**
     * Render an ItemMapItem in any non-frame context.
     * Called from ItemRendererMixin — re-entrancy is already guarded there.
     */
    public static void renderMapItem(ItemStack stack, String targetId, boolean is3D,
                                      ModelTransformationMode mode,
                                      MatrixStack matrices, VertexConsumerProvider vcp,
                                      World world, int light, int overlay) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;

        boolean isHeld = mode == ModelTransformationMode.FIRST_PERSON_RIGHT_HAND
                      || mode == ModelTransformationMode.FIRST_PERSON_LEFT_HAND
                      || mode == ModelTransformationMode.THIRD_PERSON_RIGHT_HAND
                      || mode == ModelTransformationMode.THIRD_PERSON_LEFT_HAND;

        if (isHeld) {
            renderHeld(targetId, is3D, mode, matrices, vcp, world, light, overlay);
        } else {
            // GUI, HEAD, NONE, etc — treat all as inventory display
            renderGui(targetId, is3D, matrices, vcp, world, light, overlay);
        }
    }

    // ── Held in hand ──────────────────────────────────────────────────────────

    private static void renderHeld(String targetId, boolean is3D,
                                    ModelTransformationMode mode,
                                    MatrixStack matrices, VertexConsumerProvider vcp,
                                    World world, int light, int overlay) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;

        // Step 1: render vanilla filled_map in hand position (handles arm transform)
        mc.getItemRenderer().renderItem(MAP_STACK, mode, light, overlay, matrices, vcp, world, 0);

        // Step 2: overlay item on map face
        // The map face in hand space sits at z~0.03, centered, scale ~0.8
        matrices.push();
        matrices.translate(0f, 0f, 0.032f);

        if (is3D) {
            float angle = FrameRenderManager.getGuiSpinAngle();
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(angle));
            matrices.scale(0.3f, 0.3f, 0.001f);
            renderItemGui(targetId, matrices, vcp, world, light, overlay);
        } else {
            matrices.scale(0.7f, 0.7f, 0.001f);
            renderItemGui(targetId, matrices, vcp, world, light, overlay);
        }
        matrices.pop();
    }

    // ── GUI / inventory / creative tab ────────────────────────────────────────

    private static void renderGui(String targetId, boolean is3D,
                                   MatrixStack matrices, VertexConsumerProvider vcp,
                                   World world, int light, int overlay) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;

        // Step 1: render vanilla filled_map as background — uses correct GUI transform
        mc.getItemRenderer().renderItem(MAP_STACK, ModelTransformationMode.GUI, light, overlay, matrices, vcp, world, 0);

        // Step 2: overlay item on top, slightly in front (z offset)
        matrices.push();

        // GUI model transform puts items at roughly ±8 units scale
        // We need to match that coordinate system
        // The filled_map GUI model spans roughly -7 to 7 units
        // Overlay at z=0.1 to appear in front
        matrices.translate(0f, 0f, 0.1f);

        if (is3D) {
            float angle = FrameRenderManager.getGuiSpinAngle();
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(angle));
            matrices.scale(0.55f, 0.55f, 0.001f);
            renderItemGui(targetId, matrices, vcp, world, light, overlay);
        } else {
            matrices.scale(0.75f, 0.75f, 0.001f);
            renderItemGui(targetId, matrices, vcp, world, light, overlay);
        }
        matrices.pop();
    }

    // ── Render the target item in GUI mode ────────────────────────────────────

    private static void renderItemGui(String targetId, MatrixStack matrices,
                                       VertexConsumerProvider vcp, World world,
                                       int light, int overlay) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;

        // Custom uploaded texture takes priority
        Identifier customTex = FrameRenderManager.getCustomTexture(targetId);
        if (customTex != null) {
            // Render custom texture as a flat quad in GUI space (-8 to 8)
            VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityTranslucentCull(customTex));
            Matrix4f m = matrices.peek().getPositionMatrix();
            vc.vertex(m, -8f, -8f, 0).color(255,255,255,255).texture(0,0)
              .overlay(overlay).light(light).normal(0,0,1);
            vc.vertex(m,  8f, -8f, 0).color(255,255,255,255).texture(1,0)
              .overlay(overlay).light(light).normal(0,0,1);
            vc.vertex(m,  8f,  8f, 0).color(255,255,255,255).texture(1,1)
              .overlay(overlay).light(light).normal(0,0,1);
            vc.vertex(m, -8f,  8f, 0).color(255,255,255,255).texture(0,1)
              .overlay(overlay).light(light).normal(0,0,1);
            return;
        }

        // Vanilla item rendered in GUI mode
        Item item = Registries.ITEM.get(Identifier.of(targetId));
        if (item == null) return;

        mc.getItemRenderer().renderItem(new ItemStack(item), ModelTransformationMode.GUI, light, overlay, matrices, vcp, world, 0);
    }
}
