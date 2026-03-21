package com.customblocks.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Separate payload for face texture operations only.
 * Using a dedicated channel means clients without this payload registered
 * will silently ignore it instead of crashing with "extra bytes" errors.
 *
 * Actions: setface | clearface | clearallfaces
 */
public record FaceUpdatePayload(
        String              action,
        int                 slotIndex,
        String              customId,
        Map<String, byte[]> faceTextures
) implements CustomPayload {

    public static final Id<FaceUpdatePayload> ID =
            new Id<>(Identifier.of("customblocks", "face_update_v2"));

    public static final PacketCodec<PacketByteBuf, FaceUpdatePayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeString(value.action());
                buf.writeVarInt(value.slotIndex());
                buf.writeString(value.customId() != null ? value.customId() : "");
                Map<String, byte[]> faces = value.faceTextures();
                buf.writeVarInt(faces.size());
                for (Map.Entry<String, byte[]> e : faces.entrySet()) {
                    buf.writeString(e.getKey());
                    buf.writeByteArray(e.getValue() != null ? e.getValue() : new byte[0]);
                }
            },
            buf -> {
                String action   = buf.readString();
                int    index    = buf.readVarInt();
                String customId = buf.readString();
                int    count    = buf.readVarInt();
                Map<String, byte[]> faces = new HashMap<>(count);
                for (int i = 0; i < count; i++) {
                    String key  = buf.readString();
                    byte[] data = buf.readByteArray(10_485_760);
                    faces.put(key, data);
                }
                if (buf.readableBytes() > 0) buf.skipBytes(buf.readableBytes());
                return new FaceUpdatePayload(action, index,
                        customId.isEmpty() ? null : customId, faces);
            }
    );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
