package com.customblocks.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server → Client packet that tells the client to register a new custom block,
 * write its texture, regenerate the resource pack, and reload resources.
 */
public record CustomBlockSyncPayload(
        String blockId,
        String displayName,
        byte[] textureData
) implements CustomPayload {

    public static final CustomPayload.Id<CustomBlockSyncPayload> ID =
            new CustomPayload.Id<>(Identifier.of("customblocks", "sync_block"));

    // Manual codec — avoids relying on PacketCodecs.BYTE_ARRAY which may vary
    public static final PacketCodec<PacketByteBuf, CustomBlockSyncPayload> CODEC =
            PacketCodec.of(
                    (value, buf) -> {
                        buf.writeString(value.blockId());
                        buf.writeString(value.displayName());
                        buf.writeVarInt(value.textureData().length);
                        buf.writeBytes(value.textureData());
                    },
                    buf -> {
                        String blockId     = buf.readString();
                        String displayName = buf.readString();
                        int    len         = buf.readVarInt();
                        byte[] texture     = new byte[len];
                        buf.readBytes(texture);
                        return new CustomBlockSyncPayload(blockId, displayName, texture);
                    }
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
