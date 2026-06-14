package com.mdwgames.firefly.data;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.io.File;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("PreferenceStore (async saves)")
class PreferenceStoreAsyncTest {

    private ServerMock server;
    private Plugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("Firefly");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("mutations flush to disk on an async task once async saves are enabled")
    void asyncFlushPersists(@TempDir final Path dir) {
        final File file = new File(dir.toFile(), "playerdata.yml");
        final PreferenceStore store = new PreferenceStore(file, Logger.getLogger("async-test"));
        store.enableAsyncSaves(plugin);

        final UUID hiddenId = UUID.randomUUID();
        final UUID coloredId = UUID.randomUUID();
        // A burst of mutations should coalesce into the queued async flush.
        store.setHidden(hiddenId, true);
        store.setColor(coloredId, 0x123456);

        server.getScheduler().waitAsyncTasksFinished();

        // The flush wrote the file off-thread; a fresh store reads back the same state.
        final PreferenceStore reader = new PreferenceStore(file, Logger.getLogger("async-test"));
        reader.load();
        assertTrue(reader.isHidden(hiddenId));
        assertEquals(0x123456, reader.getColor(coloredId));
    }
}
