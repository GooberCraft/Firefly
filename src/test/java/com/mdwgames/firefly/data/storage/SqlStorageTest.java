package com.mdwgames.firefly.data.storage;

import com.mdwgames.firefly.data.PlayerPreferences;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link SqlStorage} against in-memory H2 in MySQL-compatibility mode — the exact SQL
 * (schema, upsert, delete) that runs against a real MySQL in production.
 */
@DisplayName("SqlStorage (H2 in MySQL mode)")
class SqlStorageTest {

    private SqlStorage storage;

    @BeforeEach
    void setUp() throws Exception {
        // Unique DB per test; DB_CLOSE_DELAY=-1 keeps the in-memory DB alive across pooled connections.
        final HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl("jdbc:h2:mem:firefly_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        hc.setMaximumPoolSize(2);
        storage = new SqlStorage(hc);
        storage.init();
    }

    @AfterEach
    void tearDown() {
        storage.close();
    }

    @Test
    @DisplayName("upsert + loadAll round-trips all fields, including nulls and tri-state bypass")
    void roundTrip() throws Exception {
        final UUID a = UUID.randomUUID();
        final UUID b = UUID.randomUUID();
        final UUID c = UUID.randomUUID();
        final Map<UUID, PlayerPreferences> batch = new HashMap<>();
        batch.put(a, new PlayerPreferences(true, 0x123456, Boolean.TRUE));
        batch.put(b, new PlayerPreferences(true, null, null));        // hidden only
        batch.put(c, new PlayerPreferences(false, null, Boolean.FALSE)); // explicit bypass off
        storage.save(batch);

        final Map<UUID, PlayerPreferences> loaded = storage.loadAll();
        assertEquals(3, loaded.size());
        assertEquals(new PlayerPreferences(true, 0x123456, Boolean.TRUE), loaded.get(a));
        assertTrue(loaded.get(b).hidden());
        assertNull(loaded.get(b).colorRgb());
        assertNull(loaded.get(b).bypass());
        assertEquals(Boolean.FALSE, loaded.get(c).bypass());
    }

    @Test
    @DisplayName("a second upsert updates the existing row")
    void update() throws Exception {
        final UUID id = UUID.randomUUID();
        storage.save(Map.of(id, new PlayerPreferences(true, 0x111111, null)));
        storage.save(Map.of(id, new PlayerPreferences(false, 0x222222, Boolean.TRUE)));

        final PlayerPreferences loaded = storage.loadAll().get(id);
        assertFalse(loaded.hidden());
        assertEquals(0x222222, loaded.colorRgb());
        assertEquals(Boolean.TRUE, loaded.bypass());
    }

    @Test
    @DisplayName("re-init (e.g. /firefly reload) closes the previous pool instead of leaking it")
    void reinitClosesOldPool() throws Exception {
        final HikariDataSource first = storage.dataSource(); // opened by setUp's init()
        assertFalse(first.isClosed());

        storage.init(); // simulate load() running again on reload
        final HikariDataSource second = storage.dataSource();

        assertNotSame(first, second, "re-init should create a new pool");
        assertTrue(first.isClosed(), "the previous pool must be closed, not leaked");
        assertFalse(second.isClosed());

        // the re-initialized backend is still fully functional
        final UUID id = UUID.randomUUID();
        storage.save(Map.of(id, new PlayerPreferences(true, null, null)));
        assertTrue(storage.loadAll().containsKey(id));
    }

    @Test
    @DisplayName("saving a default deletes the row")
    void deleteOnDefault() throws Exception {
        final UUID id = UUID.randomUUID();
        storage.save(Map.of(id, new PlayerPreferences(true, null, null)));
        assertTrue(storage.loadAll().containsKey(id));

        storage.save(Map.of(id, PlayerPreferences.DEFAULT));
        assertFalse(storage.loadAll().containsKey(id));
    }
}
