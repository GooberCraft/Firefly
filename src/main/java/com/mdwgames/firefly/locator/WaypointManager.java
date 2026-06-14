package com.mdwgames.firefly.locator;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.color.Color;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.world.waypoint.EmptyWaypointInfo;
import com.github.retrooper.packetevents.protocol.world.waypoint.TrackedWaypoint;
import com.github.retrooper.packetevents.protocol.world.waypoint.WaypointIcon;
import com.github.retrooper.packetevents.util.Either;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWaypoint;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWaypoint.Operation;
import com.mdwgames.firefly.data.PreferenceStore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Controls the 1.21.6+ <b>locator bar</b> per receiver: hides the dots of players who chose
 * {@code /firefly hide} and recolors dots to each transmitter's chosen color. The bar is driven by
 * the server→client {@code WAYPOINT} packet ({@link WrapperPlayServerWaypoint}), one per tracked
 * transmitter, keyed by an identifier that is the transmitter's UUID for living players, with
 * operations {@code TRACK}/{@code UPDATE}/{@code UNTRACK}. The Bukkit API offers no per-viewer
 * control here, so we filter and rewrite the packet.
 *
 * <p><b>Visibility rule</b> ({@link #shouldHide}): a transmitter's dot is hidden from a receiver
 * when the transmitter has hidden themselves <em>and</em> the receiver is not an admin with see-all
 * bypass active. A receiver never hides their own (center) dot.</p>
 *
 * <p><b>Why active reconciliation:</b> toggling hide/color or an admin flipping bypass generates no
 * vanilla packets, so merely filtering future packets would leave an already-shown dot frozen on the
 * client. This manager caches each waypoint's last real payload and, on a preference change, pushes
 * the client an explicit {@code UNTRACK} (remove a now-hidden dot), {@code TRACK} (restore a
 * now-visible one), or {@code UPDATE} (apply a new color) — a surgical resync. Steady-state packets
 * are still filtered inline.</p>
 *
 * <p><b>Threading:</b> {@link #onPacketSend} runs on netty I/O threads — it only reads the concurrent
 * snapshots and the {@link PreferenceStore}, mutates concurrent caches, and edits the in-flight
 * packet; it never calls the Bukkit API or sends packets. All snapshot rebuilds, reconciliation, and
 * packet <em>sends</em> happen on the main thread ({@link #tick()} each second and {@link #refresh()}
 * on demand from commands).</p>
 */
public final class WaypointManager extends PacketListenerAbstract {

    /** Sentinel "applied color" meaning the dot is shown with the vanilla color (no recolor). */
    private static final int VANILLA = Integer.MIN_VALUE;

    private final Plugin plugin;
    private final PreferenceStore store;

    /** All online player UUIDs — distinguishes player waypoints from mob/armour-stand/datapack ones. */
    private final Set<UUID> onlinePlayers = ConcurrentHashMap.newKeySet();
    /** receiver UUID → (transmitter UUID → last real waypoint payload, uncolored). */
    private final Map<UUID, Map<UUID, TrackedWaypoint>> lastSeen = new ConcurrentHashMap<>();
    /** receiver UUID → (transmitter UUID → applied color, or {@link #VANILLA}). Our model of the client. */
    private final Map<UUID, Map<UUID, Integer>> shownToClient = new ConcurrentHashMap<>();

    private BukkitTask tickTask;

    public WaypointManager(@NotNull final Plugin plugin, @NotNull final PreferenceStore store) {
        super(PacketListenerPriority.NORMAL);
        this.plugin = plugin;
        this.store = store;
    }

    /** Registers the packet listener and starts the per-second reconcile tick. */
    public void register() {
        PacketEvents.getAPI().getEventManager().registerListener(this);
        this.tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        // Cover a /reload: players may already be carrying now-hidden dots from before. Defer a tick
        // so the online-player set is settled, then sweep them off.
        Bukkit.getScheduler().runTask(plugin, () -> {
            refreshSnapshot();
            hideExisting();
        });
    }

    /** Unregisters the listener (so it doesn't survive a /reload) and drops all tracking. */
    public void shutdown() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        final var api = PacketEvents.getAPI();
        if (api != null && api.isInitialized()) {
            api.getEventManager().unregisterListener(this);
        }
        onlinePlayers.clear();
        lastSeen.clear();
        shownToClient.clear();
    }

    /** Rebuilds the online snapshot and reconciles now — call on the main thread after a change. */
    public void refresh() {
        refreshSnapshot();
        reconcile();
    }

    /** Drops a player's caches when they quit. */
    public void forget(@NotNull final UUID playerId) {
        onlinePlayers.remove(playerId);
        lastSeen.remove(playerId);
        shownToClient.remove(playerId);
        for (final Map<UUID, TrackedWaypoint> seen : lastSeen.values()) {
            seen.remove(playerId);
        }
        for (final Map<UUID, Integer> shown : shownToClient.values()) {
            shown.remove(playerId);
        }
    }

    // ========== Main-thread tick / reconcile ==========

    private void tick() {
        if (lastSeen.isEmpty() && onlinePlayers.size() <= 1) {
            return; // nothing tracked and nobody to filter for
        }
        refreshSnapshot();
        reconcile();
    }

    private void refreshSnapshot() {
        onlinePlayers.clear();
        for (final Player p : Bukkit.getOnlinePlayers()) {
            onlinePlayers.add(p.getUniqueId());
        }
    }

    /**
     * Brings each receiver's client into line with current preferences, using cached payloads:
     * {@code UNTRACK} for now-hidden dots still shown, {@code TRACK} for now-visible dots not shown,
     * and {@code UPDATE} for a color that changed. Prunes offline receivers/transmitters.
     */
    private void reconcile() {
        for (final UUID receiverId : List.copyOf(lastSeen.keySet())) {
            final Player receiver = Bukkit.getPlayer(receiverId);
            if (receiver == null || !receiver.isOnline()) {
                lastSeen.remove(receiverId);
                shownToClient.remove(receiverId);
                continue;
            }
            final Map<UUID, TrackedWaypoint> seen = lastSeen.get(receiverId);
            if (seen == null) {
                continue;
            }
            final Map<UUID, Integer> shown = shownFor(receiverId);
            final boolean bypassing = store.isBypassing(receiverId);

            for (final UUID transmitterId : List.copyOf(seen.keySet())) {
                if (!onlinePlayers.contains(transmitterId)) {
                    seen.remove(transmitterId);
                    shown.remove(transmitterId);
                    continue;
                }
                final TrackedWaypoint payload = seen.get(transmitterId);
                final boolean hide = shouldHide(receiverId, transmitterId,
                        store.isHidden(transmitterId), bypassing);
                final Integer shownColor = shown.get(transmitterId); // null → not currently shown

                if (hide) {
                    if (shownColor != null) {
                        send(receiver, Operation.UNTRACK, payload, VANILLA);
                        shown.remove(transmitterId);
                    }
                    continue;
                }
                final int desired = desiredColor(transmitterId);
                if (shownColor == null) {
                    send(receiver, Operation.TRACK, payload, desired);
                    shown.put(transmitterId, desired);
                } else if (shownColor != desired) {
                    send(receiver, Operation.UPDATE, payload, desired);
                    shown.put(transmitterId, desired);
                }
            }
        }
    }

    private void send(@NotNull final Player receiver, @NotNull final Operation op,
                      @NotNull final TrackedWaypoint payload, final int color) {
        final TrackedWaypoint wp = color == VANILLA ? payload : recolor(payload, color);
        PacketEvents.getAPI().getPlayerManager().sendPacket(receiver, new WrapperPlayServerWaypoint(op, wp));
    }

    /**
     * Closes the on-enable gap: after a /reload, dots for now-hidden players may already be on
     * clients without us having cached them, so reconcile can't remove them. This sweeps a synthetic
     * {@code UNTRACK} for every online (receiver, hidden-transmitter) pair the receiver can't bypass —
     * removing any such dot, harmless no-op otherwise. Main thread only; runs once on enable.
     */
    private void hideExisting() {
        for (final Player receiver : Bukkit.getOnlinePlayers()) {
            final UUID receiverId = receiver.getUniqueId();
            if (store.isBypassing(receiverId)) {
                continue; // sees everyone — nothing to hide
            }
            for (final UUID transmitterId : onlinePlayers) {
                if (transmitterId.equals(receiverId)) {
                    continue;
                }
                if (store.isHidden(transmitterId)) {
                    send(receiver, Operation.UNTRACK, syntheticWaypoint(transmitterId), VANILLA);
                    shownFor(receiverId).remove(transmitterId);
                }
            }
        }
    }

    // ========== Netty packet path ==========

    @Override
    public void onPacketSend(final PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.WAYPOINT) {
            return;
        }
        if (!(event.getPlayer() instanceof Player receiver)) {
            return;
        }

        final WrapperPlayServerWaypoint wrapper = new WrapperPlayServerWaypoint(event);
        final Either<UUID, String> identifier = wrapper.getWaypoint().getIdentifier();
        if (!identifier.isLeft()) {
            return; // string id → not a player waypoint; leave it
        }
        final UUID transmitterId = identifier.getLeft();
        final UUID receiverId = receiver.getUniqueId();
        if (transmitterId.equals(receiverId) || !onlinePlayers.contains(transmitterId)) {
            return; // your own dot, or a mob/armour-stand/unknown transmitter
        }

        if (wrapper.getOperation() == Operation.UNTRACK) {
            // Genuine server removal (out of range / quit) — forget it so reconcile won't re-track.
            final Map<UUID, TrackedWaypoint> seen = lastSeen.get(receiverId);
            if (seen != null) {
                seen.remove(transmitterId);
            }
            final Map<UUID, Integer> shown = shownToClient.get(receiverId);
            if (shown != null) {
                shown.remove(transmitterId);
            }
            return;
        }

        // TRACK / UPDATE — cache the live (uncolored) payload so reconcile can reproduce it.
        seenFor(receiverId).put(transmitterId, wrapper.getWaypoint());

        if (shouldHide(receiverId, transmitterId, store.isHidden(transmitterId), store.isBypassing(receiverId))) {
            // If the client already shows this dot, rewrite into a removal; else drop it outright.
            if (shownFor(receiverId).remove(transmitterId) != null) {
                wrapper.setOperation(Operation.UNTRACK);
                event.markForReEncode(true);
            } else {
                event.setCancelled(true);
            }
            return;
        }

        final int desired = desiredColor(transmitterId);
        if (desired != VANILLA) {
            wrapper.setWaypoint(recolor(wrapper.getWaypoint(), desired));
            event.markForReEncode(true);
        }
        shownFor(receiverId).put(transmitterId, desired);
    }

    // ========== Pure helpers (unit-tested) ==========

    /**
     * Whether to hide {@code transmitter}'s dot from {@code receiver}: only when the transmitter has
     * hidden themselves and the receiver isn't bypassing. A player never hides their own dot.
     */
    public static boolean shouldHide(@NotNull final UUID receiver, @NotNull final UUID transmitter,
                                     final boolean transmitterHidden, final boolean receiverBypassing) {
        return !receiver.equals(transmitter) && transmitterHidden && !receiverBypassing;
    }

    /**
     * Returns a copy of {@code original} with its icon recolored to {@code rgb}, preserving the
     * identifier, icon style, and location info.
     */
    public static @NotNull TrackedWaypoint recolor(@NotNull final TrackedWaypoint original, final int rgb) {
        final WaypointIcon icon = original.getIcon();
        return new TrackedWaypoint(
                original.getIdentifier(),
                new WaypointIcon(icon.getStyle(), new Color(rgb & 0xFFFFFF)),
                original.getInfo());
    }

    private int desiredColor(@NotNull final UUID transmitterId) {
        final Integer color = store.getColor(transmitterId);
        return color == null ? VANILLA : (color & 0xFFFFFF);
    }

    private static @NotNull TrackedWaypoint syntheticWaypoint(@NotNull final UUID transmitterId) {
        return new TrackedWaypoint(
                Either.createLeft(transmitterId),
                new WaypointIcon(WaypointIcon.ICON_STYLE_DEFAULT, Color.WHITE),
                EmptyWaypointInfo.EMPTY);
    }

    private Map<UUID, TrackedWaypoint> seenFor(final UUID receiverId) {
        return lastSeen.computeIfAbsent(receiverId, k -> new ConcurrentHashMap<>());
    }

    private Map<UUID, Integer> shownFor(final UUID receiverId) {
        return shownToClient.computeIfAbsent(receiverId, k -> new ConcurrentHashMap<>());
    }
}
