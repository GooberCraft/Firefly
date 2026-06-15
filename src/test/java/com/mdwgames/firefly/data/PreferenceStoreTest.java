package com.mdwgames.firefly.data;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("PreferenceStore")
class PreferenceStoreTest {

    private static final Logger LOG = Logger.getLogger("PreferenceStoreTest");

    private FakeStorage storage;
    private ExecutorService worker;
    private PreferenceStore store;

    @BeforeEach
    void setUp() {
        storage = new FakeStorage();
        worker = Executors.newSingleThreadExecutor();
        store = new PreferenceStore(storage, worker, LOG);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        worker.shutdownNow();
        worker.awaitTermination(2, TimeUnit.SECONDS);
    }

    /** Barrier: waits for all previously-submitted worker tasks (incl. the coalesced flush). */
    private void sync() throws Exception {
        worker.submit(() -> { }).get(2, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("hidden state is tracked in memory and persisted")
    void hidden() throws Exception {
        final UUID id = UUID.randomUUID();
        assertTrue(store.setHidden(id, true));
        assertTrue(store.isHidden(id));
        assertFalse(store.setHidden(id, true)); // no change
        sync();
        assertTrue(storage.data.get(id).hidden());

        assertTrue(store.setHidden(id, false)); // back to default -> row removed
        sync();
        assertFalse(storage.data.containsKey(id));
    }

    @Test
    @DisplayName("color is stored and cleared")
    void color() throws Exception {
        final UUID id = UUID.randomUUID();
        store.setColor(id, 0xFF8800);
        assertEquals(0xFF8800, store.getColor(id));
        sync();
        assertEquals(0xFF8800, storage.data.get(id).colorRgb());

        assertTrue(store.clearColor(id));
        assertNull(store.getColor(id));
        sync();
        assertFalse(storage.data.containsKey(id));
    }

    @Test
    @DisplayName("bypass is persisted; seedBypass honors persisted choice over the config default")
    void bypassPersistAndSeed() throws Exception {
        final UUID id = UUID.randomUUID();

        store.setBypass(id, true);
        assertTrue(store.isBypassing(id));
        sync();
        assertEquals(Boolean.TRUE, storage.data.get(id).bypass());

        // Persisted true wins even when the config default is false.
        assertTrue(store.seedBypass(id, false));
        assertTrue(store.isBypassing(id));

        // Explicit false persists (distinct from "unset").
        store.setBypass(id, false);
        sync();
        assertEquals(Boolean.FALSE, storage.data.get(id).bypass());
        assertFalse(store.seedBypass(id, true)); // persisted false wins over default true
    }

    @Test
    @DisplayName("seedBypass uses the config default for a never-set admin and does not persist it")
    void seedBypassDefault() throws Exception {
        final UUID id = UUID.randomUUID();
        assertTrue(store.seedBypass(id, true));   // no record -> default
        assertFalse(store.seedBypass(id, false));
        sync();
        assertFalse(storage.data.containsKey(id)); // seeding never writes a row
    }

    @Test
    @DisplayName("clearBypass only affects the runtime set")
    void clearBypass() {
        final UUID id = UUID.randomUUID();
        store.seedBypass(id, true);
        assertTrue(store.isBypassing(id));
        store.clearBypass(id);
        assertFalse(store.isBypassing(id));
    }

    @Test
    @DisplayName("load populates in-memory state from storage and runs the ready callback")
    void load() throws Exception {
        final UUID hiddenId = UUID.randomUUID();
        final UUID coloredId = UUID.randomUUID();
        final UUID bypassId = UUID.randomUUID();
        storage.data.put(hiddenId, new PlayerPreferences(true, null, null));
        storage.data.put(coloredId, new PlayerPreferences(false, 0x123456, null));
        storage.data.put(bypassId, new PlayerPreferences(false, null, Boolean.TRUE));

        final boolean[] ready = {false};
        store.load(() -> ready[0] = true);
        sync();

        assertTrue(ready[0]);
        assertTrue(store.isHidden(hiddenId));
        assertEquals(0x123456, store.getColor(coloredId));
        assertTrue(store.seedBypass(bypassId, false)); // loaded bypassPref=true wins
    }

    @Test
    @DisplayName("hasPreferences reflects hidden/color but not bypass")
    void hasPreferences() {
        final UUID id = UUID.randomUUID();
        assertFalse(store.hasPreferences());
        store.setBypass(id, true);
        assertFalse(store.hasPreferences()); // bypass alone doesn't arm the hot path
        store.setHidden(id, true);
        assertTrue(store.hasPreferences());
    }

    @Test
    @DisplayName("close flushes pending changes and closes the storage")
    void closeFlushesAndCloses() {
        final UUID id = UUID.randomUUID();
        store.setHidden(id, true); // queues a flush
        store.close();             // final flush + storage.close + worker shutdown/await
        assertTrue(storage.data.get(id).hidden(), "pending change should be flushed on close");
        assertTrue(storage.closed, "storage should be closed");
    }

    @Test
    @DisplayName("a load failure is handled and still signals ready")
    void loadFailureIsHandled() throws Exception {
        storage.failLoad = true;
        final boolean[] ready = {false};
        store.load(() -> ready[0] = true);
        sync();
        assertTrue(ready[0], "onReady should run even when loadAll fails");
    }
}
