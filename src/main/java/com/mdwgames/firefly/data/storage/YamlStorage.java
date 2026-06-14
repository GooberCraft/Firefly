package com.mdwgames.firefly.data.storage;

import com.mdwgames.firefly.data.PlayerPreferences;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Flat-file backend (the default). Stores each player's preferences under
 * {@code players.<uuid>.{hidden,color,bypass}} in a YAML file and rewrites the whole file per batch.
 * Keeps its own mirror of the persisted state so a batch can be applied without re-reading the file.
 */
public final class YamlStorage implements Storage {

    private static final String PLAYERS = "players";
    private static final String HIDDEN = "hidden";
    private static final String COLOR = "color";
    private static final String BYPASS = "bypass";

    private final File file;
    private final Logger logger;
    private final Map<UUID, PlayerPreferences> mirror = new HashMap<>();

    public YamlStorage(@NotNull final File file, @NotNull final Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    @Override
    public void init() {
        final File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
    }

    @Override
    public @NotNull Map<UUID, PlayerPreferences> loadAll() {
        mirror.clear();
        if (!file.exists()) {
            return new HashMap<>();
        }
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        final ConfigurationSection players = yaml.getConfigurationSection(PLAYERS);
        if (players == null) {
            return new HashMap<>();
        }
        for (final String key : players.getKeys(false)) {
            final UUID uuid = parseUuid(key);
            if (uuid == null) {
                logger.warning("Skipping malformed UUID in " + file.getName() + ": " + key);
                continue;
            }
            final ConfigurationSection entry = players.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            final boolean hidden = entry.getBoolean(HIDDEN, false);
            final Integer color = entry.isInt(COLOR) ? (entry.getInt(COLOR) & 0xFFFFFF) : null;
            final Boolean bypass = entry.isBoolean(BYPASS) ? entry.getBoolean(BYPASS) : null;
            final PlayerPreferences prefs = new PlayerPreferences(hidden, color, bypass);
            if (!prefs.isDefault()) {
                mirror.put(uuid, prefs);
            }
        }
        return new HashMap<>(mirror);
    }

    @Override
    public void save(@NotNull final Map<UUID, PlayerPreferences> changed) throws IOException {
        for (final Map.Entry<UUID, PlayerPreferences> e : changed.entrySet()) {
            if (e.getValue().isDefault()) {
                mirror.remove(e.getKey());
            } else {
                mirror.put(e.getKey(), e.getValue());
            }
        }
        final YamlConfiguration yaml = new YamlConfiguration();
        for (final Map.Entry<UUID, PlayerPreferences> e : mirror.entrySet()) {
            final String base = PLAYERS + "." + e.getKey();
            final PlayerPreferences p = e.getValue();
            if (p.hidden()) {
                yaml.set(base + "." + HIDDEN, true);
            }
            if (p.colorRgb() != null) {
                yaml.set(base + "." + COLOR, p.colorRgb() & 0xFFFFFF);
            }
            if (p.bypass() != null) {
                yaml.set(base + "." + BYPASS, p.bypass());
            }
        }
        yaml.save(file);
    }

    @Override
    public void close() {
        // nothing to release
    }

    private static UUID parseUuid(final String raw) {
        try {
            return UUID.fromString(raw);
        } catch (final IllegalArgumentException e) {
            return null;
        }
    }
}
