package com.mdwgames.firefly.data;

import com.mdwgames.firefly.data.storage.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Authoritative in-memory store of player locator-bar preferences, persisted through a pluggable
 * {@link Storage} backend (flat YAML, H2, or MySQL). The in-memory collections are concurrent: read
 * on the netty packet thread by the {@link com.mdwgames.firefly.locator.WaypointManager}, mutated on
 * the main thread by commands.
 *
 * <p>Persisted: <b>hidden</b>, <b>colorRgb</b>, and <b>bypassPref</b> (the admin's explicit
 * tri-state see-all choice). The runtime <b>bypass</b> set (online admins currently bypassing — what
 * the manager checks) is derived on join from the persisted choice or the config default and is not
 * itself stored.</p>
 *
 * <p><b>All storage I/O runs on a single worker {@link ExecutorService}</b> ({@code dbWorker}) — no
 * JDBC or file I/O ever touches the main/region thread, and operations are serialized (load before
 * any save). Mutations mark a dirty UUID and submit one coalesced flush.</p>
 */
public final class PreferenceStore {

    private final Storage storage;
    private final ExecutorService dbWorker;
    private final Logger logger;

    private final Set<UUID> hidden = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<UUID, Integer> colorRgb = new ConcurrentHashMap<>();
    /** Persisted explicit bypass choices (tri-state): present = explicit true/false, absent = unset. */
    private final ConcurrentHashMap<UUID, Boolean> bypassPref = new ConcurrentHashMap<>();
    /** Runtime-only: online admins currently bypassing — what the manager checks. Not persisted. */
    private final Set<UUID> bypass = ConcurrentHashMap.newKeySet();

    /** UUIDs whose persisted prefs changed and await a flush. */
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean flushQueued = new AtomicBoolean(false);

    public PreferenceStore(@NotNull final Storage storage, @NotNull final ExecutorService dbWorker,
                           @NotNull final Logger logger) {
        this.storage = storage;
        this.dbWorker = dbWorker;
        this.logger = logger;
    }

    // ========== Lifecycle ==========

    /**
     * Initializes the backend and loads all preferences on the worker thread, then runs {@code onReady}
     * (used to reconcile already-online players once data has landed). Non-blocking.
     */
    public void load(@NotNull final Runnable onReady) {
        dbWorker.execute(() -> {
            try {
                storage.init();
                final Map<UUID, PlayerPreferences> data = storage.loadAll();
                hidden.clear();
                colorRgb.clear();
                bypassPref.clear();
                for (final Map.Entry<UUID, PlayerPreferences> e : data.entrySet()) {
                    final PlayerPreferences p = e.getValue();
                    if (p.hidden()) {
                        hidden.add(e.getKey());
                    }
                    if (p.colorRgb() != null) {
                        colorRgb.put(e.getKey(), p.colorRgb() & 0xFFFFFF);
                    }
                    if (p.bypass() != null) {
                        bypassPref.put(e.getKey(), p.bypass());
                    }
                }
            } catch (final Exception e) {
                logger.log(Level.SEVERE, "Failed to load preferences", e);
            }
            onReady.run();
        });
    }

    /** Final flush + close on the worker, then awaits it so writes are durable before shutdown. */
    public void close() {
        dbWorker.execute(() -> {
            flush();
            storage.close();
        });
        dbWorker.shutdown();
        try {
            if (!dbWorker.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.warning("Storage worker did not finish flushing within 10s.");
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ========== Persistence ==========

    private void markDirty(final UUID uuid) {
        dirty.add(uuid);
        if (flushQueued.compareAndSet(false, true)) {
            dbWorker.execute(this::flush);
        }
    }

    private void flush() {
        flushQueued.set(false);
        if (dirty.isEmpty()) {
            return;
        }
        final Set<UUID> batchKeys = new HashSet<>(dirty);
        dirty.removeAll(batchKeys);
        final Map<UUID, PlayerPreferences> batch = new HashMap<>();
        for (final UUID uuid : batchKeys) {
            batch.put(uuid, get(uuid));
        }
        try {
            storage.save(batch);
        } catch (final Exception e) {
            logger.log(Level.SEVERE, "Failed to persist preferences", e);
        }
    }

    /** Whether any persisted preference is set — a cheap gate for the packet hot path. */
    public boolean hasPreferences() {
        return !hidden.isEmpty() || !colorRgb.isEmpty();
    }

    // ========== Hidden ==========

    public boolean isHidden(@NotNull final UUID uuid) {
        return hidden.contains(uuid);
    }

    /** Sets a player's hidden state. Returns {@code true} if the value actually changed. */
    public boolean setHidden(@NotNull final UUID uuid, final boolean value) {
        final boolean changed = value ? hidden.add(uuid) : hidden.remove(uuid);
        if (changed) {
            markDirty(uuid);
        }
        return changed;
    }

    /** Snapshot of the players currently hiding their dot. */
    public @NotNull Set<UUID> hiddenPlayers() {
        return new HashSet<>(hidden);
    }

    // ========== Color ==========

    /** The player's chosen dot color (0xRRGGBB), or {@code null} for the vanilla color. */
    public @Nullable Integer getColor(@NotNull final UUID uuid) {
        return colorRgb.get(uuid);
    }

    public void setColor(@NotNull final UUID uuid, final int rgb) {
        colorRgb.put(uuid, rgb & 0xFFFFFF);
        markDirty(uuid);
    }

    /** Returns {@code true} if a color was set and has now been cleared. */
    public boolean clearColor(@NotNull final UUID uuid) {
        final boolean changed = colorRgb.remove(uuid) != null;
        if (changed) {
            markDirty(uuid);
        }
        return changed;
    }

    // ========== Bypass ==========

    public boolean isBypassing(@NotNull final UUID uuid) {
        return bypass.contains(uuid);
    }

    /** Sets an admin's bypass (runtime + persisted explicit choice). Returns whether it changed. */
    public boolean setBypass(@NotNull final UUID uuid, final boolean value) {
        final boolean changed = value ? bypass.add(uuid) : bypass.remove(uuid);
        // Persist the explicit choice even if the runtime set didn't change (e.g. re-confirming).
        bypassPref.put(uuid, value);
        markDirty(uuid);
        return changed;
    }

    /**
     * Seeds an admin's runtime bypass on login: their persisted explicit choice if any, else the
     * config default. Does <b>not</b> persist (a default-seeded value stays unset, so "default for
     * new admins only" holds). Returns the effective value.
     */
    public boolean seedBypass(@NotNull final UUID uuid, final boolean configDefault) {
        final boolean effective = bypassPref.getOrDefault(uuid, configDefault);
        if (effective) {
            bypass.add(uuid);
        } else {
            bypass.remove(uuid);
        }
        return effective;
    }

    /** Drops an admin's runtime bypass (e.g. on quit); the persisted choice is untouched. */
    public void clearBypass(@NotNull final UUID uuid) {
        bypass.remove(uuid);
    }

    // ========== Snapshot ==========

    /** Combined snapshot of a player's persisted preferences. */
    public @NotNull PlayerPreferences get(@NotNull final UUID uuid) {
        return new PlayerPreferences(hidden.contains(uuid), colorRgb.get(uuid), bypassPref.get(uuid));
    }
}
