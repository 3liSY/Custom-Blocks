package com.customblocks.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public record CustomBlockListPayload(List<String> blockIds) implements CustomPayload {

    public static final CustomPayload.Id<CustomBlockListPayload> ID =
            new CustomPayload.Id<>(Identifier.of("customblocks", "block_list"));

    public static final PacketCodec<PacketByteBuf, CustomBlockListPayload> CODEC =
            PacketCodec.of(
                    (value, buf) -> {
                        buf.writeVarInt(value.blockIds().size());
                        for (String id : value.blockIds()) buf.writeString(id);
                    },
                    buf -> {
                        int size = buf.readVarInt();
                        List<String> ids = new ArrayList<>();
                        for (int i = 0; i < size; i++) ids.add(buf.readString());
                        return new CustomBlockListPayload(ids);
                    }
            );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
