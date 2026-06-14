package com.mdwgames.firefly;

import com.mdwgames.firefly.command.FireflyCommand;
import com.mdwgames.firefly.data.PreferenceStore;
import com.mdwgames.firefly.data.storage.Storage;
import com.mdwgames.firefly.data.storage.StorageFactory;
import com.mdwgames.firefly.listener.PlayerSessionListener;
import com.mdwgames.firefly.locator.WaypointManager;
import org.bstats.bukkit.Metrics;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Firefly — controls which dots appear on the 1.21.6+ locator bar. Players hide/recolor their own
 * dot; admins can bypass hiding to see everyone. All locator-bar manipulation goes through
 * packetevents (a hard dependency) via {@link WaypointManager}.
 */
public final class Firefly extends JavaPlugin {

    private ExecutorService storageWorker;
    private PreferenceStore store;
    private WaypointManager waypointManager;

    @Override
    public void onEnable() {
        // packetevents is a hard depend; bail loudly rather than NPE if it's somehow missing.
        if (getServer().getPluginManager().getPlugin("packetevents") == null) {
            getLogger().severe("packetevents is not installed — Firefly cannot control the locator bar. Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();

        // Single dedicated thread for all storage I/O — keeps JDBC/file work off the main thread.
        storageWorker = Executors.newSingleThreadExecutor(r -> {
            final Thread t = new Thread(r, "Firefly-Storage");
            t.setDaemon(true);
            return t;
        });
        final Storage storage = StorageFactory.create(this, getConfig());
        store = new PreferenceStore(storage, storageWorker, getLogger());

        waypointManager = new WaypointManager(this, store);
        waypointManager.register();

        final boolean bypassDefault = getConfig().getBoolean("admin-bypass.default", false);
        getServer().getPluginManager().registerEvents(
                new PlayerSessionListener(store, waypointManager, bypassDefault), this);

        final PluginCommand command = getCommand("firefly");
        if (command != null) {
            final FireflyCommand executor = new FireflyCommand(this, store, waypointManager);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        } else {
            getLogger().warning("Failed to register the /firefly command — is it declared in plugin.yml?");
        }

        // Init + load off-thread; reconcile already-online players once the data has landed.
        store.load(() -> waypointManager.scheduleRefresh());

        final int pluginId = 31993;
        new Metrics(this, pluginId);

        getLogger().info("Firefly enabled (admin-bypass default=" + bypassDefault + ").");
    }

    @Override
    public void onDisable() {
        if (waypointManager != null) {
            waypointManager.shutdown();
        }
        if (store != null) {
            store.close(); // final flush + close pool + shut down the storage worker
        }
    }

    /** The shared preference store; exposed for tests and internal wiring. */
    public PreferenceStore getPreferenceStore() {
        return store;
    }
}
