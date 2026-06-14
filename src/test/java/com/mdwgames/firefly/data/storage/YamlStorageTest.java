package com.mdwgames.firefly.data.storage;

import com.mdwgames.firefly.data.PlayerPreferences;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("YamlStorage")
class YamlStorageTest {

    private static final Logger LOG = Logger.getLogger("YamlStorageTest");

    private YamlStorage open(final Path dir) throws Exception {
        final YamlStorage s = new YamlStorage(new File(dir.toFile(), "playerdata.yml"), LOG);
        s.init();
        return s;
    }

    @Test
    @DisplayName("save then reload round-trips hidden, color, and tri-state bypass")
    void roundTrip(@TempDir final Path dir) throws Exception {
        final UUID a = UUID.randomUUID();
        final UUID b = UUID.randomUUID();
        final UUID c = UUID.randomUUID();

        final YamlStorage writer = open(dir);
        final Map<UUID, PlayerPreferences> batch = new HashMap<>();
        batch.put(a, new PlayerPreferences(true, 0x123456, null));
        batch.put(b, new PlayerPreferences(false, null, Boolean.TRUE));
        batch.put(c, new PlayerPreferences(false, null, Boolean.FALSE));
        writer.save(batch);

        final YamlStorage reader = open(dir);
        final Map<UUID, PlayerPreferences> loaded = reader.loadAll();
        assertEquals(3, loaded.size());
        assertTrue(loaded.get(a).hidden());
        assertEquals(0x123456, loaded.get(a).colorRgb());
        assertNull(loaded.get(a).bypass());
        assertEquals(Boolean.TRUE, loaded.get(b).bypass());
        assertEquals(Boolean.FALSE, loaded.get(c).bypass());
    }

    @Test
    @DisplayName("saving a default removes the player's row")
    void deleteOnDefault(@TempDir final Path dir) throws Exception {
        final UUID id = UUID.randomUUID();
        final YamlStorage s = open(dir);
        s.save(Map.of(id, new PlayerPreferences(true, null, null)));
        assertEquals(1, s.loadAll().size());

        s.save(Map.of(id, PlayerPreferences.DEFAULT));
        assertFalse(s.loadAll().containsKey(id));
    }

    @Test
    @DisplayName("loadAll on a missing file is empty")
    void emptyWhenMissing(@TempDir final Path dir) throws Exception {
        assertTrue(open(dir).loadAll().isEmpty());
    }
}
