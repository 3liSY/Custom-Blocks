package com.customblocks.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C: full metadata sync on player join (no textures — those drip-feed via SlotUpdatePayload).
 */
public record FullSyncPayload(List<SlotEntry> slots, byte[] tabIconTexture)
        implements CustomPayload {

    public record SlotEntry(int index, String customId, String displayName, byte[] texture,
                            int lightLevel, float hardness, String soundType) {}

    public static final CustomPayload.Id<FullSyncPayload> ID =
            new CustomPayload.Id<>(Identifier.of("customblocks", "full_sync"));

    public static final PacketCodec<RegistryByteBuf, FullSyncPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeInt(value.slots().size());
                for (SlotEntry e : value.slots()) {
                    buf.writeInt(e.index());
                    buf.writeString(e.customId());
                    buf.writeString(e.displayName());
                    buf.writeInt(e.lightLevel());
                    buf.writeFloat(e.hardness());
                    buf.writeString(e.soundType() != null ? e.soundType() : "stone");
                }
                byte[] icon = value.tabIconTexture();
                if (icon != null && icon.length > 0) {
                    buf.writeBoolean(true); buf.writeInt(icon.length); buf.writeBytes(icon);
                } else { buf.writeBoolean(false); }
            },
            buf -> {
                int count = buf.readInt();
                List<SlotEntry> list = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    int idx  = buf.readInt();
                    String cid  = buf.readString();
                    String name = buf.readString();
                    int light   = buf.readInt();
                    float hard  = buf.readFloat();
                    String snd  = buf.readString();
                    list.add(new SlotEntry(idx, cid, name, null, light, hard, snd));
                }
                byte[] icon = null;
                if (buf.readBoolean()) {
                    int len = buf.readInt(); icon = new byte[len]; buf.readBytes(icon);
                }
                return new FullSyncPayload(list, icon);
            }
    );

    @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}
