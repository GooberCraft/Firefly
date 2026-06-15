package com.mdwgames.firefly.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Messages")
class MessagesTest {

    private static final Logger LOG = Logger.getLogger("MessagesTest");
    private static final char SECTION = '§'; // ChatColor color char

    private Messages load(final Path dir) {
        final Messages m = new Messages(new File(dir.toFile(), "messages.yml"), LOG);
        m.load(); // no file on disk -> bundled defaults (on the classpath) are used
        return m;
    }

    @Test
    @DisplayName("returns a bundled default, with prefix substituted and & codes translated")
    void defaults(@TempDir final Path dir) {
        final String msg = load(dir).get("dot-hidden");
        assertTrue(msg.contains("[Firefly]"), msg);          // prefix substituted
        assertTrue(msg.toLowerCase().contains("hidden"), msg);
        assertTrue(msg.indexOf(SECTION) >= 0, "expected translated color codes: " + msg);
        assertFalse(msg.contains("{prefix}"), msg);
        assertFalse(msg.contains("&"), "expected no raw & codes: " + msg);
    }

    @Test
    @DisplayName("substitutes caller placeholders")
    void placeholders(@TempDir final Path dir) {
        final String msg = load(dir).get("color-set", "color", "aqua");
        assertTrue(msg.contains("aqua"), msg);
        assertFalse(msg.contains("{color}"), msg);
    }

    @Test
    @DisplayName("color/format codes in a caller-supplied value are neutralized, not interpreted")
    void placeholderValueCannotInjectColors(@TempDir final Path dir) {
        final Messages m = load(dir);
        // A player types a 'color' carrying legacy & codes and a raw section-sign code; it is echoed
        // back via {input} in color-unknown. None of it may become active formatting.
        final String evil = "&c&lHACK" + SECTION + "kX";
        final String msg = m.get("color-unknown", "input", evil);
        assertTrue(msg.contains("&c&lHACK"), "value '&' codes must stay literal: " + msg);
        assertFalse(msg.contains(SECTION + "k"), "section-sign code from the value must be stripped: " + msg);
    }

    @Test
    @DisplayName("a missing key falls back to a visible placeholder, not an exception")
    void missingKey(@TempDir final Path dir) {
        assertTrue(load(dir).get("no-such-key").contains("missing message: no-such-key"));
    }

    @Test
    @DisplayName("an operator override wins, but un-overridden keys still fall back to the default")
    void overrideAndFallback(@TempDir final Path dir) throws Exception {
        final File file = new File(dir.toFile(), "messages.yml");
        Files.writeString(file.toPath(), "dot-hidden: \"&aCUSTOM {prefix}done\"\n");
        final Messages m = new Messages(file, LOG);
        m.load();

        assertTrue(m.get("dot-hidden").contains("CUSTOM"), "override should win");
        // a key NOT in the override file still resolves from the bundled default
        assertTrue(m.get("dot-shown").toLowerCase().contains("visible"));
    }
}
