package com.customblocks.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server → Client for single-slot changes.
 * Actions: add | remove | rename | retexture | tabicon | setprop
 */
public record SlotUpdatePayload(
        String action,
        int    slotIndex,
        String customId,
        String displayName,
        byte[] texture,
        int    lightLevel,
        float  hardness,
        String soundType
) implements CustomPayload {

    public static final Id<SlotUpdatePayload> ID =
            new Id<>(Identifier.of("customblocks", "slot_update"));

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
            },
            buf -> {
                String action      = buf.readString();
                int    index       = buf.readVarInt();
                String id          = buf.readString();
                String name        = buf.readString();
                byte[] tex         = buf.readByteArray();
                int    lightLevel  = buf.readVarInt();
                float  hardness    = buf.readFloat();
                String soundType   = buf.readString();
                return new SlotUpdatePayload(
                        action, index,
                        id.isEmpty()   ? null : id,
                        name.isEmpty() ? null : name,
                        tex.length > 0 ? tex  : null,
                        lightLevel, hardness,
                        soundType.isEmpty() ? "stone" : soundType
                );
            }
    );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
