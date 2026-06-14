package com.mdwgames.firefly.data.storage;

import com.mdwgames.firefly.data.PlayerPreferences;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("FallbackStorage")
class FallbackStorageTest {

    private static final Logger LOG = Logger.getLogger("FallbackStorageTest");

    /** Records which backend got used. */
    private static final class Recording implements Storage {
        final boolean failInit;
        boolean closed;
        final Map<UUID, PlayerPreferences> data = new HashMap<>();

        Recording(final boolean failInit) {
            this.failInit = failInit;
        }

        @Override
        public void init() {
            if (failInit) {
                throw new IllegalStateException("simulated failure");
            }
        }

        @Override
        public @NotNull Map<UUID, PlayerPreferences> loadAll() {
            return new HashMap<>(data);
        }

        @Override
        public void save(@NotNull final Map<UUID, PlayerPreferences> changed) {
            data.putAll(changed);
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    @Test
    @DisplayName("uses the primary when its init succeeds")
    void primaryWhenHealthy() throws Exception {
        final Recording primary = new Recording(false);
        final Recording fallback = new Recording(false);
        final FallbackStorage fs = new FallbackStorage(primary, () -> fallback, LOG);

        fs.init();
        final UUID id = UUID.randomUUID();
        fs.save(Map.of(id, new PlayerPreferences(true, null, null)));

        assertTrue(primary.data.containsKey(id));
        assertTrue(fallback.data.isEmpty());
    }

    @Test
    @DisplayName("falls back when the primary init fails, and closes the half-open primary")
    void fallbackWhenPrimaryFails() throws Exception {
        final Recording primary = new Recording(true);
        final Recording fallback = new Recording(false);
        final FallbackStorage fs = new FallbackStorage(primary, () -> fallback, LOG);

        fs.init();
        final UUID id = UUID.randomUUID();
        fs.save(Map.of(id, new PlayerPreferences(false, 0x111111, null)));

        assertTrue(primary.closed);                  // half-open primary cleaned up
        assertTrue(fallback.data.containsKey(id));    // writes go to the fallback
        assertEquals(1, fs.loadAll().size());
        fs.close();
        assertTrue(fallback.closed);
        assertFalse(primary.data.containsKey(id));
    }
}
