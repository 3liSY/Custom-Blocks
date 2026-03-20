package com.itemmap.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * S2C: single frame change (add / update / remove).
 */
public record FrameUpdatePayload(
    String  action,      // "update" | "remove"
    long    entityId,
    String  mode,
    float   spinSpeed,
    float   scale,
    float   padPct,
    boolean glowing,
    String  label,
    int     bgColor,
    String  customImageId,
    boolean invisible
) implements CustomPayload {

    public static final Id<FrameUpdatePayload> ID =
            new Id<>(Identifier.of("itemmap", "frame_update_v2"));

    public static final PacketCodec<PacketByteBuf, FrameUpdatePayload> CODEC = PacketCodec.of(
        (v, buf) -> {
            buf.writeString(v.action());
            buf.writeLong(v.entityId());
            buf.writeString(v.mode()          != null ? v.mode()          : "FLAT_2D");
            buf.writeFloat(v.spinSpeed());
            buf.writeFloat(v.scale());
            buf.writeFloat(v.padPct());
            buf.writeBoolean(v.glowing());
            buf.writeString(v.label()         != null ? v.label()         : "");
            buf.writeInt(v.bgColor());
            buf.writeString(v.customImageId() != null ? v.customImageId() : "");
            buf.writeBoolean(v.invisible());
        },
        buf -> new FrameUpdatePayload(
            buf.readString(),
            buf.readLong(),
            buf.readString(),
            buf.readFloat(),
            buf.readFloat(),
            buf.readFloat(),
            buf.readBoolean(),
            nullIfEmpty(buf.readString()),
            buf.readInt(),
            nullIfEmpty(buf.readString()),
            buf.readBoolean()
        )
    );

    private static String nullIfEmpty(String s) { return s.isEmpty() ? null : s; }

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
