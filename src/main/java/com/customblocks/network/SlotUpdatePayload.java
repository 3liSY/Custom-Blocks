package com.customblocks.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * S2C: server tells clients about a single block slot change.
 * action: "add" | "retexture" | "delete" | "rename" | "update"
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

    public static final CustomPayload.Id<SlotUpdatePayload> ID =
            new CustomPayload.Id<>(Identifier.of("customblocks", "slot_update"));

    public static final PacketCodec<RegistryByteBuf, SlotUpdatePayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeString(value.action());
                buf.writeInt(value.slotIndex());
                buf.writeString(value.customId() != null ? value.customId() : "");
                buf.writeString(value.displayName() != null ? value.displayName() : "");
                byte[] tex = value.texture();
                if (tex != null && tex.length > 0) {
                    buf.writeBoolean(true);
                    buf.writeInt(tex.length);
                    buf.writeBytes(tex);
                } else {
                    buf.writeBoolean(false);
                }
                buf.writeInt(value.lightLevel());
                buf.writeFloat(value.hardness());
                buf.writeString(value.soundType() != null ? value.soundType() : "stone");
            },
            buf -> {
                String action = buf.readString();
                int slot = buf.readInt();
                String cid  = buf.readString();
                String name = buf.readString();
                byte[] tex  = null;
                if (buf.readBoolean()) {
                    int len = buf.readInt();
                    tex = new byte[len];
                    buf.readBytes(tex);
                }
                int light = buf.readInt();
                float hard = buf.readFloat();
                String snd = buf.readString();
                return new SlotUpdatePayload(action, slot, cid, name, tex, light, hard, snd);
            }
    );

    @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}
