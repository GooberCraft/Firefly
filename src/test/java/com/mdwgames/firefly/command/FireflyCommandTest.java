package com.mdwgames.firefly.command;

import com.mdwgames.firefly.data.PreferenceStore;
import com.mdwgames.firefly.locator.WaypointManager;
import org.bukkit.command.Command;
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
    private PreferenceStore store;
    private FireflyCommand cmd;
    private Command bukkitCmd;

    @BeforeEach
    void setUp(@TempDir final Path dir) {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("Firefly");
        store = new PreferenceStore(new File(dir.toFile(), "playerdata.yml"), Logger.getLogger("test"));
        final WaypointManager manager = new WaypointManager(plugin, store);
        cmd = new FireflyCommand(plugin, store, manager);
        bukkitCmd = mock(Command.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** A player with self-service permission but not admin. */
    private PlayerMock user() {
        final PlayerMock p = server.addPlayer();
        p.addAttachment(plugin, FireflyCommand.PERM_USE, true);
        return p;
    }

    private void run(final PlayerMock p, final String... args) {
        cmd.onCommand(p, bukkitCmd, "firefly", args);
    }

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
    @DisplayName("color sets a named color and reset clears it")
    void colorSetAndReset() {
        final PlayerMock p = user();

        run(p, "color", "red");
        assertNotNull(store.getColor(p.getUniqueId()));
        assertTrue(store.getColor(p.getUniqueId()) == 0xFF5555);

        run(p, "color", "reset");
        assertNull(store.getColor(p.getUniqueId()));
    }

    @Test
    @DisplayName("an invalid color is rejected and nothing is stored")
    void colorInvalid() {
        final PlayerMock p = user();
        run(p, "color", "notacolor");
        assertNull(store.getColor(p.getUniqueId()));
    }

    @Test
    @DisplayName("firefly.use is required: a player without it cannot hide")
    void useGating() {
        final PlayerMock plain = server.addPlayer(); // no firefly.use, not op
        run(plain, "hide");
        assertFalse(store.isHidden(plain.getUniqueId()));
    }

    @Test
    @DisplayName("firefly.admin is required for bypass")
    void adminGating() {
        final PlayerMock nonAdmin = user();
        run(nonAdmin, "bypass", "on");
        assertFalse(store.isBypassing(nonAdmin.getUniqueId()));

        final PlayerMock admin = server.addPlayer();
        admin.addAttachment(plugin, FireflyCommand.PERM_ADMIN, true);
        run(admin, "bypass", "on");
        assertTrue(store.isBypassing(admin.getUniqueId()));
    }

    @Test
    @DisplayName("an unknown subcommand is handled and replies with guidance")
    void unknownSubcommand() {
        final PlayerMock p = user();
        final boolean handled = cmd.onCommand(p, bukkitCmd, "firefly", new String[]{"frobnicate"});
        assertTrue(handled);
        assertNotNull(p.nextMessage()); // a reply was sent
    }
}
