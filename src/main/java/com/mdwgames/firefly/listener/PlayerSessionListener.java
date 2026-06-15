package com.mdwgames.firefly.listener;

import com.mdwgames.firefly.command.FireflyCommand;
import com.mdwgames.firefly.config.Messages;
import com.mdwgames.firefly.data.PreferenceStore;
import com.mdwgames.firefly.locator.WaypointManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Seeds an admin's runtime see-all bypass on join (from their persisted choice or the config
 * default) and reminds them if it's active; drops a player's cached locator-bar state on quit so
 * the maps don't leak.
 */
public final class PlayerSessionListener implements Listener {

    private final PreferenceStore store;
    private final WaypointManager manager;
    private final Messages messages;
    private final boolean bypassDefault;

    public PlayerSessionListener(@NotNull final PreferenceStore store, @NotNull final WaypointManager manager,
                                 @NotNull final Messages messages, final boolean bypassDefault) {
        this.store = store;
        this.manager = manager;
        this.messages = messages;
        this.bypassDefault = bypassDefault;
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        if (!player.hasPermission(FireflyCommand.PERM_ADMIN)) {
            return;
        }
        final boolean active = store.seedBypass(player.getUniqueId(), bypassDefault);
        if (active) {
            player.sendMessage(messages.get("bypass-login-reminder"));
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        store.clearBypass(event.getPlayer().getUniqueId());
        manager.forget(event.getPlayer().getUniqueId());
    }
}
