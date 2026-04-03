package com.customblocks.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * C2S: client sends image data to server.
 * action: "create" | "retexture" | "face" | "anim_frame" | "rand_variant" | "sound"
 */
public record ImageEditPayload(
        String action,
        String customId,
        String displayName,
        byte[] texture,
        String extra       // face name / frame index / sound type / etc.
) implements CustomPayload {

    public static final CustomPayload.Id<ImageEditPayload> ID =
            new CustomPayload.Id<>(Identifier.of("customblocks", "image_edit"));

    public static final PacketCodec<RegistryByteBuf, ImageEditPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeString(value.action() != null ? value.action() : "");
                buf.writeString(value.customId() != null ? value.customId() : "");
                buf.writeString(value.displayName() != null ? value.displayName() : "");
                byte[] tex = value.texture();
                if (tex != null && tex.length > 0) {
                    buf.writeBoolean(true); buf.writeInt(tex.length); buf.writeBytes(tex);
                } else { buf.writeBoolean(false); }
                buf.writeString(value.extra() != null ? value.extra() : "");
            },
            buf -> {
                String action = buf.readString();
                String cid    = buf.readString();
                String name   = buf.readString();
                byte[] tex    = null;
                if (buf.readBoolean()) {
                    int len = buf.readInt(); tex = new byte[len]; buf.readBytes(tex);
                }
                String extra = buf.readString();
                return new ImageEditPayload(action, cid, name, tex, extra);
            }
    );

    @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}
