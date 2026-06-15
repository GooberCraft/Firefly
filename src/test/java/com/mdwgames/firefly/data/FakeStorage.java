package com.mdwgames.firefly.data;

import com.mdwgames.firefly.data.storage.Storage;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** In-memory {@link Storage} for tests: seed {@link #data} for load, inspect it after save. */
final class FakeStorage implements Storage {

    final Map<UUID, PlayerPreferences> data = new HashMap<>();
    boolean failInit = false;
    boolean failLoad = false;
    boolean closed = false;

    @Override
    public void init() {
        if (failInit) {
            throw new IllegalStateException("simulated init failure");
        }
    }

    @Override
    public @NotNull Map<UUID, PlayerPreferences> loadAll() {
        if (failLoad) {
            throw new IllegalStateException("simulated load failure");
        }
        return new HashMap<>(data);
    }

    @Override
    public void save(@NotNull final Map<UUID, PlayerPreferences> changed) {
        for (final Map.Entry<UUID, PlayerPreferences> e : changed.entrySet()) {
            if (e.getValue().isDefault()) {
                data.remove(e.getKey());
            } else {
                data.put(e.getKey(), e.getValue());
            }
        }
    }

    @Override
    public void close() {
        closed = true;
    }
}
