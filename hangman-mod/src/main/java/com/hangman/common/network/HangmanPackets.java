package com.hangman.common.network;

import com.hangman.HangmanMod;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** All custom packet payloads used by the mod, multiplexed through one channel. */
public final class HangmanPackets {

    private HangmanPackets() {}

    // ── single mux payload ────────────────────────────────────────────────────

    public record HangmanPayload(String type, PacketByteBuf data) implements CustomPayload {

        public static final CustomPayload.Id<HangmanPayload> ID =
            new CustomPayload.Id<>(Identifier.of(HangmanMod.MOD_ID, "mux"));

        public static final PacketCodec<PacketByteBuf, HangmanPayload> CODEC =
            PacketCodec.of(HangmanPayload::write, HangmanPayload::read);

        private static void write(HangmanPayload p, PacketByteBuf buf) {
            buf.writeString(p.type, 64);
            byte[] bytes = new byte[p.data.readableBytes()];
            p.data.getBytes(p.data.readerIndex(), bytes);
            buf.writeByteArray(bytes);
        }

        private static HangmanPayload read(PacketByteBuf buf) {
            String type = buf.readString(64);
            byte[] bytes = buf.readByteArray();
            PacketByteBuf inner = new net.minecraft.network.PacketByteBuf(io.netty.buffer.Unpooled.buffer());
            inner.writeBytes(bytes);
            return new HangmanPayload(type, inner);
        }

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    // ── type constants ─────────────────────────────────────────────────────────
    // S2C
    public static final String S2C_INVITE_NOTIFY     = "s2c_invite";
    public static final String S2C_OPEN_WORD_SCREEN  = "s2c_word_screen";
    public static final String S2C_GAME_START        = "s2c_start";
    public static final String S2C_GAME_UPDATE       = "s2c_update";
    public static final String S2C_GAME_OVER         = "s2c_over";
    public static final String S2C_LIMB_REMOVED      = "s2c_limb";
    public static final String S2C_TIMER_SYNC        = "s2c_timer";
    public static final String S2C_HINT_RESULT       = "s2c_hint";
    public static final String S2C_SETTINGS_SYNC     = "s2c_settings";

    // C2S
    public static final String C2S_ACCEPT_INVITE     = "c2s_accept";
    public static final String C2S_DECLINE_INVITE    = "c2s_decline";
    public static final String C2S_SUBMIT_WORD       = "c2s_word";
    public static final String C2S_GUESS_LETTER      = "c2s_guess";
    public static final String C2S_REQUEST_HINT      = "c2s_hint";
    public static final String C2S_FORFEIT           = "c2s_forfeit";
    public static final String C2S_SPAWN_GALLOWS     = "c2s_gallows";
    public static final String C2S_SAVE_OVERLAY      = "c2s_overlay";

    // ── helpers ───────────────────────────────────────────────────────────────

    public static PacketByteBuf newBuf() {
        return new net.minecraft.network.PacketByteBuf(io.netty.buffer.Unpooled.buffer());
    }

    public static void writeCharList(PacketByteBuf buf, Iterable<Character> chars) {
        List<Character> list = new ArrayList<>();
        for (char c : chars) list.add(c);
        buf.writeInt(list.size());
        for (char c : list) buf.writeShort(c);
    }

    public static List<Character> readCharList(PacketByteBuf buf) {
        int n = buf.readInt();
        List<Character> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) list.add((char) buf.readShort());
        return list;
    }

    public static void writeStringList(PacketByteBuf buf, List<String> strs) {
        buf.writeInt(strs.size());
        for (String s : strs) buf.writeString(s, 64);
    }

    public static List<String> readStringList(PacketByteBuf buf) {
        int n = buf.readInt();
        List<String> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) list.add(buf.readString(64));
        return list;
    }

    /** Register both S2C and C2S channel. Call once at startup. */
    public static void register() {
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C()
            .register(HangmanPayload.ID, HangmanPayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S()
            .register(HangmanPayload.ID, HangmanPayload.CODEC);
    }
}
