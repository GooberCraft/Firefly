package com.mdwgames.firefly;

import com.mdwgames.firefly.command.FireflyCommand;
import com.mdwgames.firefly.data.PreferenceStore;
import com.mdwgames.firefly.listener.PlayerSessionListener;
import com.mdwgames.firefly.locator.WaypointManager;
import io.github.retrooper.packetevents.util.folia.FoliaScheduler;
import org.bstats.bukkit.Metrics;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * Firefly — controls which dots appear on the 1.21.6+ locator bar. Players hide/recolor their own
 * dot; admins can bypass hiding to see everyone. All locator-bar manipulation goes through
 * packetevents (a hard dependency) via {@link WaypointManager}.
 */
public final class Firefly extends JavaPlugin {

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

        store = new PreferenceStore(new File(getDataFolder(), "playerdata.yml"), getLogger());
        store.load();
        // Coalesced off-thread saves via packetevents' async scheduler (Folia-aware, Bukkit on Paper).
        store.enableAsyncSaves(task -> FoliaScheduler.getAsyncScheduler().runNow(this, o -> task.run()));

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
            store.save();
        }
    }

    /** The shared preference store; exposed for tests and internal wiring. */
    public PreferenceStore getPreferenceStore() {
        return store;
    }
}
