package com.mdwgames.firefly.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("PreferenceStore (async saves)")
class PreferenceStoreAsyncTest {

    @Test
    @DisplayName("mutations flush to disk via the injected async saver")
    void asyncFlushPersists(@TempDir final Path dir) throws InterruptedException {
        // Production wires the saver to packetevents' FoliaScheduler async scheduler; here a
        // single-thread executor exercises the same off-thread dispatch deterministically
        // (awaitTermination waits for every queued save to finish — no timing races).
        final ExecutorService exec = Executors.newSingleThreadExecutor();
        try {
            final File file = new File(dir.toFile(), "playerdata.yml");
            final PreferenceStore store = new PreferenceStore(file, Logger.getLogger("async-test"));
            store.enableAsyncSaves(exec::execute);

            final UUID hiddenId = UUID.randomUUID();
            final UUID coloredId = UUID.randomUUID();
            // A burst of mutations coalesces into the queued async flush(es).
            store.setHidden(hiddenId, true);
            store.setColor(coloredId, 0x123456);

            exec.shutdown();
            assertTrue(exec.awaitTermination(5, TimeUnit.SECONDS), "async save did not finish in time");

            // The flush wrote the file off-thread; a fresh store reads back the same state.
            final PreferenceStore reader = new PreferenceStore(file, Logger.getLogger("async-test"));
            reader.load();
            assertTrue(reader.isHidden(hiddenId));
            assertEquals(0x123456, reader.getColor(coloredId));
        } finally {
            exec.shutdownNow();
        }
    }
}
