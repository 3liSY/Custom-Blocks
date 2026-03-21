package com.customblocks.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Server → Client for single-slot changes.
 * Actions: add | remove | rename | retexture | tabicon | setprop | setface | clearface | clearallfaces
 *
 * faceTextures — only populated for setface / clearface / add actions that have face overrides.
 *   For clearface the map contains the face key mapped to an empty byte[] (sentinel = remove).
 */
public record SlotUpdatePayload(
        String               action,
        int                  slotIndex,
        String               customId,
        String               displayName,
        byte[]               texture,
        int                  lightLevel,
        float                hardness,
        String               soundType,
        Map<String, byte[]>  faceTextures   // never null in decoded payloads
) implements CustomPayload {

    public static final Id<SlotUpdatePayload> ID =
            new Id<>(Identifier.of("customblocks", "slot_update"));

    // ── Codec ─────────────────────────────────────────────────────────────────

    public static final PacketCodec<PacketByteBuf, SlotUpdatePayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeString(value.action());
                buf.writeVarInt(value.slotIndex());
                buf.writeString(value.customId()    != null ? value.customId()    : "");
                buf.writeString(value.displayName() != null ? value.displayName() : "");
                buf.writeByteArray(value.texture()  != null ? value.texture()     : new byte[0]);
                buf.writeVarInt(value.lightLevel());
                buf.writeFloat(value.hardness());
                buf.writeString(value.soundType()   != null ? value.soundType()   : "stone");
                // Face textures map: count then key/value pairs
                Map<String, byte[]> faces = value.faceTextures();
                buf.writeVarInt(faces.size());
                for (Map.Entry<String, byte[]> e : faces.entrySet()) {
                    buf.writeString(e.getKey());
                    buf.writeByteArray(e.getValue() != null ? e.getValue() : new byte[0]);
                }
            },
            buf -> {
                String action      = buf.readString();
                int    index       = buf.readVarInt();
                String id          = buf.readString();
                String name        = buf.readString();
                byte[] tex         = buf.readByteArray(10_485_760);
                int    lightLevel  = buf.readVarInt();
                float  hardness    = buf.readFloat();
                String soundType   = buf.readString();
                // Read face textures defensively — older clients that don't have this
                // field will simply get an empty map instead of crashing
                Map<String, byte[]> faces = new HashMap<>();
                try {
                    if (buf.readableBytes() > 0) {
                        int faceCount = buf.readVarInt();
                        for (int i = 0; i < faceCount; i++) {
                            String faceKey  = buf.readString();
                            byte[] faceData = buf.readByteArray(10_485_760);
                            faces.put(faceKey, faceData);
                        }
                    }
                } catch (Exception ignored) { /* old codec — no face data */ }
                return new SlotUpdatePayload(
                        action, index,
                        id.isEmpty()   ? null : id,
                        name.isEmpty() ? null : name,
                        tex.length > 0 ? tex  : null,
                        lightLevel, hardness,
                        soundType.isEmpty() ? "stone" : soundType,
                        faces
                );
            }
    );

    // ── Convenience constructors (no face textures) ───────────────────────────

    public SlotUpdatePayload(String action, int slotIndex, String customId, String displayName,
                              byte[] texture, int lightLevel, float hardness, String soundType) {
        this(action, slotIndex, customId, displayName, texture, lightLevel, hardness, soundType,
                Collections.emptyMap());
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
