package com.hangman.server.game;

import com.hangman.HangmanMod;
import com.hangman.common.config.HangmanConfig;
import com.hangman.common.network.HangmanNetworking;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import java.util.*;
import java.util.concurrent.*;

public class HangmanGameManager {

    // ── singleton ─────────────────────────────────────────────────────────────
    private static HangmanGameManager INSTANCE;
    public static HangmanGameManager get() {
        if (INSTANCE == null) INSTANCE = new HangmanGameManager();
        return INSTANCE;
    }

    // ── pending invites ───────────────────────────────────────────────────────
    private final Map<UUID, PendingInvite> pendingInvites = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Hangman-Scheduler");
        t.setDaemon(true);
        return t;
    });

    // ── active games ──────────────────────────────────────────────────────────
    /** Both player UUIDs map to the same game object */
    private final Map<UUID, HangmanGame> activeGames = new ConcurrentHashMap<>();

    /** Spectators: spectator UUID → game */
    private final Map<UUID, HangmanGame> spectators = new ConcurrentHashMap<>();

    // ── freeze tracking ───────────────────────────────────────────────────────
    private final Set<UUID> frozenPlayers = ConcurrentHashMap.newKeySet();

    // ── timers ────────────────────────────────────────────────────────────────
    private final Map<UUID, ScheduledFuture<?>> gameTimers = new ConcurrentHashMap<>();

    // ── invite ────────────────────────────────────────────────────────────────

    public boolean sendInvite(ServerPlayerEntity inviter, ServerPlayerEntity invitee) {
        if (getGame(inviter.getUuid()) != null) {
            inviter.sendMessage(Text.translatable("hangman.error.already_in_game"), false);
            return false;
        }
        if (getGame(invitee.getUuid()) != null) {
            inviter.sendMessage(Text.translatable("hangman.error.already_in_game"), false);
            return false;
        }
        if (!HangmanConfig.get().allowMultipleGames && !activeGames.isEmpty()) {
            inviter.sendMessage(Text.literal("§cA Hangman game is already running on this server."), false);
            return false;
        }

        int expiry = HangmanConfig.get().inviteExpirySecs;
        PendingInvite invite = new PendingInvite(inviter.getUuid(), invitee.getUuid());
        pendingInvites.put(inviter.getUuid(), invite);

        // Tell both players
        inviter.sendMessage(Text.translatable("hangman.invite.sent", invitee.getName().getString(), expiry), false);

        // Clickable invite message for invitee
        HangmanNetworking.sendInviteNotification(invitee, inviter.getName().getString());

        // Schedule expiry
        if (expiry > 0) {
            ScheduledFuture<?> future = scheduler.schedule(() -> {
                PendingInvite pi = pendingInvites.remove(inviter.getUuid());
                if (pi != null) {
                    ServerPlayerEntity inv = inviter.getServer().getPlayerManager().getPlayer(inviter.getUuid());
                    ServerPlayerEntity invt = inviter.getServer().getPlayerManager().getPlayer(invitee.getUuid());
                    if (inv != null)
                        inv.sendMessage(Text.translatable("hangman.invite.expired", invitee.getName().getString()), false);
                    if (invt != null)
                        invt.sendMessage(Text.literal("§7[Hangman] Invite from " + inviter.getName().getString() + " expired."), false);
                }
            }, expiry, TimeUnit.SECONDS);
            invite.expiryFuture = future;
        }

        // Server-wide announce?
        if (HangmanConfig.get().serverWideAnnounce) {
            inviter.getServer().getPlayerManager().broadcast(
                Text.literal("§6[Hangman] §f" + inviter.getName().getString()
                    + " §echallenged §f" + invitee.getName().getString() + " §eto Hangman!"), false);
        }
        return true;
    }

    public HangmanGame acceptInvite(ServerPlayerEntity acceptor, MinecraftServer server) {
        // Find invite where acceptor is the invitee
        PendingInvite found = null;
        UUID inviterId = null;
        for (Map.Entry<UUID, PendingInvite> e : pendingInvites.entrySet()) {
            if (e.getValue().inviteeId.equals(acceptor.getUuid())) {
                found = e.getValue();
                inviterId = e.getKey();
                break;
            }
        }
        if (found == null) {
            acceptor.sendMessage(Text.translatable("hangman.error.no_invite"), false);
            return null;
        }
        if (found.expiryFuture != null) found.expiryFuture.cancel(false);
        pendingInvites.remove(inviterId);

        ServerPlayerEntity inviter = server.getPlayerManager().getPlayer(inviterId);
        if (inviter == null) {
            acceptor.sendMessage(Text.literal("§cInviter is no longer online."), false);
            return null;
        }

        // Determine roles
        UUID hangedId, hangerId;
        String chooser = HangmanConfig.get().wordChooser;
        if ("HANGER".equalsIgnoreCase(chooser)) {
            // inviter is hanger (picks letters), acceptor is hanged
            hangedId = acceptor.getUuid();
            hangerId = inviterId;
        } else {
            // default: inviter is hanged
            hangedId = inviterId;
            hangerId = acceptor.getUuid();
        }

        HangmanGame game = new HangmanGame(hangedId, hangerId, HangmanConfig.get());
        activeGames.put(hangedId, game);
        activeGames.put(hangerId, game);

        ServerPlayerEntity hanged = server.getPlayerManager().getPlayer(hangedId);
        ServerPlayerEntity hanger = server.getPlayerManager().getPlayer(hangerId);

        if (hanged == null || hanger == null) {
            endGame(game, server);
            return null;
        }

        // Save positions for return teleport
        game.savePosition(hangedId, hanged.getX(), hanged.getY(), hanged.getZ(), hanged.getYaw(), hanged.getPitch());
        game.savePosition(hangerId, hanger.getX(), hanger.getY(), hanger.getZ(), hanger.getYaw(), hanger.getPitch());

        // Announce
        if (HangmanConfig.get().chatMessages) {
            server.getPlayerManager().broadcast(
                Text.translatable("hangman.game.started", hanged.getName().getString(), hanger.getName().getString()),
                false);
        }

        // Teleport to area if configured
        if (HangmanConfig.get().teleportOnStart) {
            // Try to find a "default" area
            GameArea area = GameArea.getArea("default");
            if (area != null) teleportToArea(hanged, hanger, area, server);
        }

        // Freeze hanged player
        freeze(hangedId);

        // Tell hanger to open word entry screen (or hanged, depending on config)
        UUID wordChooserUUID = "HANGER".equalsIgnoreCase(HangmanConfig.get().wordChooser) ? hangerId : hangedId;
        ServerPlayerEntity wordChooserPlayer = server.getPlayerManager().getPlayer(wordChooserUUID);
        if (wordChooserPlayer != null) {
            HangmanNetworking.sendOpenWordScreen(wordChooserPlayer);
        }

        // Tell both players their roles
        if (hanged != null) hanged.sendMessage(Text.literal("§cYou are the §lHANGED§r§c player! "
            + (wordChooserUUID.equals(hangedId) ? "Enter the secret word." : "The hanger will enter the word.")), false);
        if (hanger != null) hanger.sendMessage(Text.literal("§aYou are the §lHANGER§r§a! "
            + (wordChooserUUID.equals(hangerId) ? "Enter the secret word." : "Wait for the word.")), false);

        return game;
    }

    public void declineInvite(ServerPlayerEntity decliner, MinecraftServer server) {
        for (Map.Entry<UUID, PendingInvite> e : new HashMap<>(pendingInvites).entrySet()) {
            if (e.getValue().inviteeId.equals(decliner.getUuid())) {
                if (e.getValue().expiryFuture != null) e.getValue().expiryFuture.cancel(false);
                pendingInvites.remove(e.getKey());
                ServerPlayerEntity inviter = server.getPlayerManager().getPlayer(e.getKey());
                if (inviter != null)
                    inviter.sendMessage(Text.translatable("hangman.invite.declined", decliner.getName().getString()), false);
                return;
            }
        }
    }

    // ── game management ───────────────────────────────────────────────────────

    public HangmanGame getGame(UUID playerId) {
        return activeGames.get(playerId);
    }

    public HangmanGame getSpectatingGame(UUID spectatorId) {
        return spectators.get(spectatorId);
    }

    public void addSpectator(UUID spectatorId, HangmanGame game) {
        spectators.put(spectatorId, game);
    }

    public void removeSpectator(UUID spectatorId) {
        spectators.remove(spectatorId);
    }

    public Collection<UUID> getSpectatorIds(HangmanGame game) {
        List<UUID> list = new ArrayList<>();
        for (Map.Entry<UUID, HangmanGame> e : spectators.entrySet()) {
            if (e.getValue() == game) list.add(e.getKey());
        }
        return list;
    }

    public void endGame(HangmanGame game, MinecraftServer server) {
        cancelTimer(game.getHangedId());
        unfreeze(game.getHangedId());
        activeGames.remove(game.getHangedId());
        activeGames.remove(game.getHangerId());

        // Remove spectators
        spectators.entrySet().removeIf(e -> e.getValue() == game);

        // Return teleport
        if (HangmanConfig.get().teleportOnEnd) {
            returnPlayer(server, game.getHangedId(), game);
            returnPlayer(server, game.getHangerId(), game);
        }

        // Notify both players overlay off
        ServerPlayerEntity hanged = server.getPlayerManager().getPlayer(game.getHangedId());
        ServerPlayerEntity hanger = server.getPlayerManager().getPlayer(game.getHangerId());
        if (hanged != null) HangmanNetworking.sendGameOver(hanged, game);
        if (hanger != null) HangmanNetworking.sendGameOver(hanger, game);
    }

    private void returnPlayer(MinecraftServer server, UUID id, HangmanGame game) {
        double[] pos = game.getSavedPosition(id);
        if (pos == null) return;
        ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
        if (p == null) return;
        p.teleport(p.getServerWorld(), pos[0], pos[1], pos[2], (float) pos[3], (float) pos[4]);
    }

    // ── timer ─────────────────────────────────────────────────────────────────

    public void startTimer(HangmanGame game, MinecraftServer server) {
        int secs = game.getConfig().timerSeconds;
        if (secs <= 0) return;
        cancelTimer(game.getHangedId());

        ScheduledFuture<?> future = scheduler.schedule(() -> {
            server.execute(() -> HangmanNetworking.handleTimerExpiry(game, server));
        }, secs, TimeUnit.SECONDS);
        gameTimers.put(game.getHangedId(), future);
    }

    public void cancelTimer(UUID hangedId) {
        ScheduledFuture<?> f = gameTimers.remove(hangedId);
        if (f != null) f.cancel(false);
    }

    // ── freeze ────────────────────────────────────────────────────────────────

    public void freeze(UUID playerId) {
        frozenPlayers.add(playerId);
    }

    public void unfreeze(UUID playerId) {
        frozenPlayers.remove(playerId);
    }

    public boolean isFrozen(UUID playerId) {
        return frozenPlayers.contains(playerId);
    }

    // ── gallows wood spawner ─────────────────────────────────────────────────

    /**
     * Spawns a simple gallows structure centered on the given block position.
     * The structure: vertical post (4 high) + horizontal beam (3 wide) + rope (1 down).
     */
    public void spawnGallows(ServerPlayerEntity player, BlockPos origin) {
        ServerWorld world = player.getServerWorld();

        // Vertical post - 4 blocks tall
        for (int i = 0; i <= 4; i++) {
            world.setBlockState(origin.up(i), Blocks.OAK_LOG.getDefaultState());
        }
        // Horizontal beam - 3 blocks out
        for (int i = 1; i <= 3; i++) {
            world.setBlockState(origin.up(4).east(i), Blocks.OAK_LOG.getDefaultState());
        }
        // Rope - 1 block down from end of beam
        world.setBlockState(origin.up(3).east(3), Blocks.CHAIN.getDefaultState());

        player.sendMessage(Text.literal("§aGallows spawned!"), false);
    }

    // ── teleport helpers ──────────────────────────────────────────────────────

    public void teleportToArea(ServerPlayerEntity hanged, ServerPlayerEntity hanger,
                                GameArea area, MinecraftServer server) {
        ServerWorld world = server.getWorld(
            net.minecraft.registry.RegistryKey.of(
                net.minecraft.registry.RegistryKeys.WORLD,
                Identifier.of(area.dimension)));
        if (world == null) return;

        hanged.teleport(world, area.hangedX, area.hangedY, area.hangedZ, area.hangedYaw, area.hangedPitch);
        hanger.teleport(world, area.hangerX, area.hangerY, area.hangerZ, area.hangerYaw, area.hangerPitch);
    }

    // ── inner classes ─────────────────────────────────────────────────────────

    private static class PendingInvite {
        final UUID inviterId;
        final UUID inviteeId;
        ScheduledFuture<?> expiryFuture;

        PendingInvite(UUID inviterId, UUID inviteeId) {
            this.inviterId = inviterId;
            this.inviteeId = inviteeId;
        }
    }
}
