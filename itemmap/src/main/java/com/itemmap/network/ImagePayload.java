package com.itemmap.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * S2C: deliver a custom image's PNG bytes to a client.
 * Chunked if large — but 128x128 PNG is small enough for one packet.
 */
public record ImagePayload(String imageId, byte[] png) implements CustomPayload {

    public static final Id<ImagePayload> ID =
            new Id<>(Identifier.of("itemmap", "image"));

    public static final PacketCodec<PacketByteBuf, ImagePayload> CODEC = PacketCodec.of(
        (v, buf) -> {
            buf.writeString(v.imageId());
            buf.writeByteArray(v.png() != null ? v.png() : new byte[0]);
        },
        buf -> {
            String id  = buf.readString();
            byte[] png = buf.readByteArray(10_485_760);
            return new ImagePayload(id, png.length > 0 ? png : null);
        }
    );

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
