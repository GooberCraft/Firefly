package com.mdwgames.firefly.data;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Authoritative in-memory store of player locator-bar preferences, backed by a flat
 * {@code playerdata.yml}. Read on the netty packet thread by the
 * {@link com.mdwgames.firefly.locator.WaypointManager} and mutated on the main thread by commands,
 * so the persisted collections are concurrent.
 *
 * <p>Two of the three pieces of state are persisted:</p>
 * <ul>
 *   <li><b>hidden</b> — players who hid their own dot ({@code /firefly hide}).</li>
 *   <li><b>colorRgb</b> — players' chosen dot colors ({@code /firefly color}).</li>
 * </ul>
 *
 * <p>The third, <b>bypass</b> (admins currently seeing everyone), is intentionally runtime-only:
 * it is seeded from config when an admin joins and never written to disk.</p>
 *
 * <p>Mutations are write-through — each change persists immediately, since the data set is tiny —
 * with a final {@link #save()} on disable as a backstop.</p>
 */
public final class PreferenceStore {

    private static final String PLAYERS = "players";
    private static final String HIDDEN = "hidden";
    private static final String COLOR = "color";

    private final File file;
    private final Logger logger;

    private final Set<UUID> hidden = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<UUID, Integer> colorRgb = new ConcurrentHashMap<>();
    /** Runtime-only: admins with see-all bypass active this session. Never persisted. */
    private final Set<UUID> bypass = ConcurrentHashMap.newKeySet();

    public PreferenceStore(@NotNull final File file, @NotNull final Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    // ========== Persistence ==========

    /** Loads hidden + color state from {@code playerdata.yml}, replacing any in-memory state. */
    public void load() {
        hidden.clear();
        colorRgb.clear();
        if (!file.exists()) {
            return;
        }
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        final ConfigurationSection players = yaml.getConfigurationSection(PLAYERS);
        if (players == null) {
            return;
        }
        for (final String key : players.getKeys(false)) {
            final UUID uuid = parseUuid(key);
            if (uuid == null) {
                logger.warning("Skipping malformed UUID in playerdata.yml: " + key);
                continue;
            }
            final ConfigurationSection entry = players.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            if (entry.getBoolean(HIDDEN, false)) {
                hidden.add(uuid);
            }
            if (entry.isInt(COLOR)) {
                colorRgb.put(uuid, entry.getInt(COLOR) & 0xFFFFFF);
            }
        }
    }

    /** Writes the current hidden + color state to {@code playerdata.yml}. */
    public synchronized void save() {
        final YamlConfiguration yaml = new YamlConfiguration();
        final Set<UUID> keys = new HashSet<>(hidden);
        keys.addAll(colorRgb.keySet());
        for (final UUID uuid : keys) {
            final String base = PLAYERS + "." + uuid;
            if (hidden.contains(uuid)) {
                yaml.set(base + "." + HIDDEN, true);
            }
            final Integer color = colorRgb.get(uuid);
            if (color != null) {
                yaml.set(base + "." + COLOR, color & 0xFFFFFF);
            }
        }
        try {
            final File parent = file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            yaml.save(file);
        } catch (final IOException e) {
            logger.log(Level.SEVERE, "Failed to save playerdata.yml", e);
        }
    }

    // ========== Hidden ==========

    public boolean isHidden(@NotNull final UUID uuid) {
        return hidden.contains(uuid);
    }

    /** Sets a player's hidden state. Returns {@code true} if the value actually changed. */
    public boolean setHidden(@NotNull final UUID uuid, final boolean value) {
        final boolean changed = value ? hidden.add(uuid) : hidden.remove(uuid);
        if (changed) {
            save();
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
        save();
    }

    /** Returns {@code true} if a color was set and has now been cleared. */
    public boolean clearColor(@NotNull final UUID uuid) {
        final boolean changed = colorRgb.remove(uuid) != null;
        if (changed) {
            save();
        }
        return changed;
    }

    // ========== Bypass (runtime-only) ==========

    public boolean isBypassing(@NotNull final UUID uuid) {
        return bypass.contains(uuid);
    }

    /** Sets an admin's runtime bypass. Returns {@code true} if the value changed. Not persisted. */
    public boolean setBypass(@NotNull final UUID uuid, final boolean value) {
        return value ? bypass.add(uuid) : bypass.remove(uuid);
    }

    /** Drops an admin's runtime bypass (e.g. on quit) without touching persisted state. */
    public void clearBypass(@NotNull final UUID uuid) {
        bypass.remove(uuid);
    }

    // ========== Snapshot ==========

    /** Combined snapshot of a player's persisted preferences. */
    public @NotNull PlayerPreferences get(@NotNull final UUID uuid) {
        return new PlayerPreferences(hidden.contains(uuid), colorRgb.get(uuid));
    }

    private static @Nullable UUID parseUuid(final String raw) {
        try {
            return UUID.fromString(raw);
        } catch (final IllegalArgumentException e) {
            return null;
        }
    }
}
