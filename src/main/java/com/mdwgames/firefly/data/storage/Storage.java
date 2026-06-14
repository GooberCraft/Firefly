package com.mdwgames.firefly.data.storage;

import com.mdwgames.firefly.data.PlayerPreferences;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;

/**
 * Persistence backend for player locator-bar preferences. Implementations are flat-file YAML or a
 * pooled SQL database (H2 / MySQL). Every method is invoked on Firefly's single storage worker
 * thread (never the main/region thread), so implementations need not be thread-safe themselves.
 */
public interface Storage {

    /** Opens the backend: ensures the file/folder, or creates the pool and the schema. */
    void init() throws Exception;

    /** Reads every persisted player's preferences. Called once at startup. */
    @NotNull
    Map<UUID, PlayerPreferences> loadAll() throws Exception;

    /**
     * Persists a batch of changed players. For each entry, upsert the row, or remove it when
     * {@link PlayerPreferences#isDefault()} (a player back to defaults keeps no row).
     */
    void save(@NotNull Map<UUID, PlayerPreferences> changed) throws Exception;

    /** Releases resources (closes the connection pool). */
    void close();
}
