package com.itemmap.network;

import com.itemmap.manager.FrameData;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C: sent on join. Full list of all FrameData + all custom image IDs.
 */
public record FrameSyncPayload(List<FrameEntry> frames) implements CustomPayload {

    public static final Id<FrameSyncPayload> ID =
            new Id<>(Identifier.of("itemmap", "frame_sync"));

    public record FrameEntry(
        long   entityId,
        String mode,
        float  spinSpeed,
        float  scale,
        float  padPct,
        boolean glowing,
        String label,
        int    bgColor,
        String customImageId,
        boolean invisible
    ) {}

    public static final PacketCodec<PacketByteBuf, FrameSyncPayload> CODEC = PacketCodec.of(
        (value, buf) -> {
            buf.writeVarInt(value.frames().size());
            for (FrameEntry e : value.frames()) {
                buf.writeLong(e.entityId());
                buf.writeString(e.mode());
                buf.writeFloat(e.spinSpeed());
                buf.writeFloat(e.scale());
                buf.writeFloat(e.padPct());
                buf.writeBoolean(e.glowing());
                buf.writeString(e.label() != null ? e.label() : "");
                buf.writeInt(e.bgColor());
                buf.writeString(e.customImageId() != null ? e.customImageId() : "");
                buf.writeBoolean(e.invisible());
            }
        },
        buf -> {
            int size = buf.readVarInt();
            List<FrameEntry> list = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                long    eid     = buf.readLong();
                String  mode    = buf.readString();
                float   spd     = buf.readFloat();
                float   scale   = buf.readFloat();
                float   pad     = buf.readFloat();
                boolean glow    = buf.readBoolean();
                String  lbl     = buf.readString();
                int     bg      = buf.readInt();
                String  imgId   = buf.readString();
                boolean inv     = buf.readBoolean();
                list.add(new FrameEntry(eid, mode, spd, scale, pad, glow,
                    lbl.isEmpty() ? null : lbl,
                    bg, imgId.isEmpty() ? null : imgId, inv));
            }
            return new FrameSyncPayload(list);
        }
    );

    @Override public Id<? extends CustomPayload> getId() { return ID; }

    public static FrameEntry fromData(FrameData d) {
        return new FrameEntry(
            d.entityId, d.mode.name(), d.spinSpeed, d.scale, d.padPct,
            d.glowing, d.label, d.bgColor, d.customImageId, d.invisible
        );
    }
}
