package com.mdwgames.firefly.data.storage;

import com.mdwgames.firefly.data.PlayerPreferences;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Wraps a primary backend (H2/MySQL) so that if {@link #init()} fails — e.g. the database is
 * unreachable or misconfigured — Firefly logs the error and transparently falls back to a YAML
 * backend instead of failing to enable. The failover happens on the storage worker thread (where
 * {@code init()} is invoked); all later calls delegate to whichever backend won.
 */
public final class FallbackStorage implements Storage {

    private final Storage primary;
    private final Supplier<Storage> fallback;
    private final Logger logger;
    private Storage active;

    public FallbackStorage(@NotNull final Storage primary, @NotNull final Supplier<Storage> fallback,
                           @NotNull final Logger logger) {
        this.primary = primary;
        this.fallback = fallback;
        this.logger = logger;
    }

    @Override
    public void init() throws Exception {
        try {
            primary.init();
            active = primary;
        } catch (final Exception e) {
            // Don't leak credentials — log the message/SQLState only.
            logger.log(Level.SEVERE, "Database storage failed to initialize ("
                    + e.getClass().getSimpleName() + ": " + e.getMessage()
                    + "); falling back to flat-file YAML storage.");
            try {
                primary.close();
            } catch (final RuntimeException ignored) {
                // best-effort cleanup of the half-open pool
            }
            final Storage fb = fallback.get();
            fb.init();
            active = fb;
        }
    }

    @Override
    public @NotNull Map<UUID, PlayerPreferences> loadAll() throws Exception {
        return active.loadAll();
    }

    @Override
    public void save(@NotNull final Map<UUID, PlayerPreferences> changed) throws Exception {
        active.save(changed);
    }

    @Override
    public void close() {
        if (active != null) {
            active.close();
        }
    }
}
