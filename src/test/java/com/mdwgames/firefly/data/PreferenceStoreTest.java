package com.mdwgames.firefly.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("PreferenceStore")
class PreferenceStoreTest {

    private static final Logger LOG = Logger.getLogger("PreferenceStoreTest");

    private PreferenceStore newStore(final Path dir) {
        return new PreferenceStore(new File(dir.toFile(), "playerdata.yml"), LOG);
    }

    @Test
    @DisplayName("set/get hidden tracks changes and reports whether the value changed")
    void hidden(@TempDir final Path dir) {
        final PreferenceStore store = newStore(dir);
        final UUID id = UUID.randomUUID();

        assertFalse(store.isHidden(id));
        assertTrue(store.setHidden(id, true));   // changed
        assertTrue(store.isHidden(id));
        assertFalse(store.setHidden(id, true));   // no-op
        assertTrue(store.setHidden(id, false));   // changed back
        assertFalse(store.isHidden(id));
    }

    @Test
    @DisplayName("set/clear color")
    void color(@TempDir final Path dir) {
        final PreferenceStore store = newStore(dir);
        final UUID id = UUID.randomUUID();

        assertNull(store.getColor(id));
        store.setColor(id, 0xFF8800);
        assertEquals(0xFF8800, store.getColor(id));
        assertTrue(store.clearColor(id));
        assertNull(store.getColor(id));
        assertFalse(store.clearColor(id));
    }

    @Test
    @DisplayName("hidden and color persist across a save/load round-trip")
    void roundTrip(@TempDir final Path dir) {
        final UUID hiddenId = UUID.randomUUID();
        final UUID coloredId = UUID.randomUUID();

        final PreferenceStore writer = newStore(dir);
        writer.setHidden(hiddenId, true);
        writer.setColor(coloredId, 0x123456);
        writer.save();

        final PreferenceStore reader = newStore(dir);
        reader.load();
        assertTrue(reader.isHidden(hiddenId));
        assertEquals(0x123456, reader.getColor(coloredId));
        assertFalse(reader.isHidden(coloredId));
    }

    @Test
    @DisplayName("hiddenPlayers returns a snapshot of all hidden UUIDs")
    void hiddenPlayersSnapshot(@TempDir final Path dir) {
        final PreferenceStore store = newStore(dir);
        final UUID a = UUID.randomUUID();
        final UUID b = UUID.randomUUID();
        store.setHidden(a, true);
        store.setHidden(b, true);

        assertEquals(2, store.hiddenPlayers().size());
        assertTrue(store.hiddenPlayers().contains(a));
        // snapshot is a copy — mutating it doesn't affect the store
        store.hiddenPlayers().clear();
        assertEquals(2, store.hiddenPlayers().size());
    }

    @Test
    @DisplayName("bypass is runtime-only and never persisted")
    void bypassNotPersisted(@TempDir final Path dir) {
        final UUID id = UUID.randomUUID();
        final PreferenceStore writer = newStore(dir);
        assertTrue(store(writer, id));
        writer.save();

        final PreferenceStore reader = newStore(dir);
        reader.load();
        assertFalse(reader.isBypassing(id));
    }

    private boolean store(final PreferenceStore store, final UUID id) {
        final boolean changed = store.setBypass(id, true);
        assertTrue(store.isBypassing(id));
        return changed;
    }
}
