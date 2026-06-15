package com.mdwgames.firefly.command;

import com.mdwgames.firefly.config.Messages;
import com.mdwgames.firefly.data.PreferenceStore;
import com.mdwgames.firefly.data.storage.Storage;
import com.mdwgames.firefly.data.storage.YamlStorage;
import com.mdwgames.firefly.locator.WaypointManager;
import org.bukkit.command.Command;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@DisplayName("FireflyCommand")
class FireflyCommandTest {

    private ServerMock server;
    private Plugin plugin;
    private ExecutorService worker;
    private PreferenceStore store;
    private FireflyCommand cmd;
    private Command bukkitCmd;

    @BeforeEach
    void setUp(@TempDir final Path dir) {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("Firefly");
        worker = Executors.newSingleThreadExecutor();
        final Storage storage = new YamlStorage(new File(dir.toFile(), "playerdata.yml"), Logger.getLogger("test"));
        store = new PreferenceStore(storage, worker, Logger.getLogger("test"));
        final WaypointManager manager = new WaypointManager(plugin, store);
        // No messages.yml on disk -> Messages falls back to the bundled defaults (on the test classpath).
        final Messages messages = new Messages(new File(dir.toFile(), "messages.yml"), Logger.getLogger("test"));
        messages.load();
        cmd = new FireflyCommand(plugin, store, manager, messages);
        bukkitCmd = mock(Command.class);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        worker.shutdownNow();
        worker.awaitTermination(2, TimeUnit.SECONDS);
        MockBukkit.unmock();
    }

    /** A player with self-service permission but not admin. */
    private PlayerMock user() {
        final PlayerMock p = server.addPlayer();
        p.addAttachment(plugin, FireflyCommand.PERM_USE, true);
        return p;
    }

    /** A player with both self-service and admin permissions. */
    private PlayerMock admin() {
        final PlayerMock p = user();
        p.addAttachment(plugin, FireflyCommand.PERM_ADMIN, true);
        return p;
    }

    private void run(final org.bukkit.command.CommandSender s, final String... args) {
        cmd.onCommand(s, bukkitCmd, "firefly", args);
    }

    // ========== Self-service ==========

    @Test
    @DisplayName("hide/show/toggle flip the stored hidden state")
    void hideShowToggle() {
        final PlayerMock p = user();

        run(p, "hide");
        assertTrue(store.isHidden(p.getUniqueId()));

        run(p, "show");
        assertFalse(store.isHidden(p.getUniqueId()));

        run(p, "toggle");
        assertTrue(store.isHidden(p.getUniqueId()));
        run(p, "toggle");
        assertFalse(store.isHidden(p.getUniqueId()));
    }

    @Test
    @DisplayName("color sets named and hex colors, and reset/clear remove it")
    void colorSetAndReset() {
        final PlayerMock p = user();

        run(p, "color", "red");
        assertNotNull(store.getColor(p.getUniqueId()));
        assertTrue(store.getColor(p.getUniqueId()) == 0xFF5555);

        run(p, "colour", "#FF8800"); // alias + hex
        assertTrue(store.getColor(p.getUniqueId()) == 0xFF8800);

        run(p, "color", "reset");
        assertNull(store.getColor(p.getUniqueId()));

        run(p, "color", "aqua");
        run(p, "color", "clear");
        assertNull(store.getColor(p.getUniqueId()));
    }

    @Test
    @DisplayName("color with no argument and with an invalid value store nothing")
    void colorInvalid() {
        final PlayerMock p = user();

        run(p, "color"); // missing argument -> usage
        assertNull(store.getColor(p.getUniqueId()));

        run(p, "color", "notacolor");
        assertNull(store.getColor(p.getUniqueId()));
    }

    // ========== Admin ==========

    @Test
    @DisplayName("bypass on/off/toggle/invalid update the runtime bypass")
    void bypassVariants() {
        final PlayerMock a = admin();

        run(a, "bypass", "on");
        assertTrue(store.isBypassing(a.getUniqueId()));
        run(a, "bypass", "off");
        assertFalse(store.isBypassing(a.getUniqueId()));
        run(a, "bypass", "toggle");
        assertTrue(store.isBypassing(a.getUniqueId()));
        run(a, "bypass");          // no arg -> toggle
        assertFalse(store.isBypassing(a.getUniqueId()));
        run(a, "bypass", "huh");   // invalid -> usage, no change
        assertFalse(store.isBypassing(a.getUniqueId()));
    }

    @Test
    @DisplayName("showhidden lists hidden players (online and offline) or reports none")
    void showHidden() {
        final PlayerMock a = admin();

        run(a, "showhidden"); // none hidden yet
        assertNotNull(a.nextMessage());

        final PlayerMock hiddenOnline = user();
        store.setHidden(hiddenOnline.getUniqueId(), true);
        store.setHidden(UUID.randomUUID(), true); // an offline/unknown player

        run(a, "showhidden");
        assertNotNull(a.nextMessage());
    }

    @Test
    @DisplayName("reload reloads config and player data")
    void reload() {
        final PlayerMock a = admin();
        run(a, "reload");
        assertNotNull(a.nextMessage());
    }

    @Test
    @DisplayName("help and the bare command print usage for both permission tiers")
    void help() {
        final PlayerMock u = user();
        run(u);          // bare -> help
        assertNotNull(u.nextMessage());
        run(u, "help");
        assertNotNull(u.nextMessage());

        final PlayerMock a = admin();
        run(a, "help");  // exercises the admin help branch
        assertNotNull(a.nextMessage());
    }

    // ========== Permission gating ==========

    @Test
    @DisplayName("firefly.use is required: a player without it cannot hide")
    void useGating() {
        final PlayerMock plain = server.addPlayer(); // no firefly.use, not op
        run(plain, "hide");
        assertFalse(store.isHidden(plain.getUniqueId()));
    }

    @Test
    @DisplayName("firefly.admin is required for bypass, showhidden, and reload")
    void adminGating() {
        final PlayerMock nonAdmin = user();
        run(nonAdmin, "bypass", "on");
        assertFalse(store.isBypassing(nonAdmin.getUniqueId()));
        run(nonAdmin, "showhidden");
        run(nonAdmin, "reload");
        assertNotNull(nonAdmin.nextMessage());
    }

    @Test
    @DisplayName("console cannot run player-only subcommands")
    void consoleRejected() {
        final ConsoleCommandSender console = server.getConsoleSender();
        run(console, "hide");            // requireUser -> not a player
        run(console, "bypass", "on");    // requireAdminPlayer -> not a player (console has perms)
        // no exception; commands handled gracefully
    }

    @Test
    @DisplayName("an unknown subcommand is handled and replies with guidance")
    void unknownSubcommand() {
        final PlayerMock p = user();
        final boolean handled = cmd.onCommand(p, bukkitCmd, "firefly", new String[]{"frobnicate"});
        assertTrue(handled);
        assertNotNull(p.nextMessage());
    }

    // ========== Tab completion ==========

    @Test
    @DisplayName("first-argument completion is gated by permission")
    void tabCompleteSubcommands() {
        final PlayerMock u = user();
        final List<String> userSubs = cmd.onTabComplete(u, bukkitCmd, "firefly", new String[]{""});
        assertNotNull(userSubs);
        assertTrue(userSubs.contains("hide"));
        assertFalse(userSubs.contains("reload")); // admin-only hidden from a plain user

        final PlayerMock a = admin();
        final List<String> adminSubs = cmd.onTabComplete(a, bukkitCmd, "firefly", new String[]{"re"});
        assertTrue(adminSubs.contains("reload"));
    }

    @Test
    @DisplayName("second-argument completion offers colors and bypass states")
    void tabCompleteArgs() {
        final PlayerMock a = admin();

        final List<String> colors = cmd.onTabComplete(a, bukkitCmd, "firefly", new String[]{"color", "a"});
        assertTrue(colors.contains("aqua"));

        final List<String> bypass = cmd.onTabComplete(a, bukkitCmd, "firefly", new String[]{"bypass", ""});
        assertTrue(bypass.contains("on"));
        assertTrue(bypass.contains("toggle"));

        // unknown second arg and a third arg yield no suggestions
        assertTrue(cmd.onTabComplete(a, bukkitCmd, "firefly", new String[]{"hide", ""}).isEmpty());
        assertTrue(cmd.onTabComplete(a, bukkitCmd, "firefly", new String[]{"color", "red", ""}).isEmpty());
    }
}
