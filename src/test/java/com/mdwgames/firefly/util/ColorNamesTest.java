package com.mdwgames.firefly.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ColorNames")
class ColorNamesTest {

    @Test
    @DisplayName("parses all sixteen named colors")
    void parsesNamedColors() {
        assertEquals(16, ColorNames.names().size());
        assertEquals(OptionalInt.of(0xFF5555), ColorNames.parse("red"));
        assertEquals(OptionalInt.of(0x55FFFF), ColorNames.parse("aqua"));
        assertEquals(OptionalInt.of(0xFFAA00), ColorNames.parse("gold"));
        assertEquals(OptionalInt.of(0x000000), ColorNames.parse("black"));
    }

    @Test
    @DisplayName("named lookup is case-insensitive")
    void namedCaseInsensitive() {
        assertEquals(OptionalInt.of(0xFF5555), ColorNames.parse("RED"));
        assertEquals(OptionalInt.of(0xFF5555), ColorNames.parse("Red"));
    }

    @Test
    @DisplayName("parses hex with and without leading #")
    void parsesHex() {
        assertEquals(OptionalInt.of(0xFF8800), ColorNames.parse("#FF8800"));
        assertEquals(OptionalInt.of(0xFF8800), ColorNames.parse("ff8800"));
        assertEquals(OptionalInt.of(0x000000), ColorNames.parse("#000000"));
        assertEquals(OptionalInt.of(0xFFFFFF), ColorNames.parse("#FFFFFF"));
    }

    @Test
    @DisplayName("rejects invalid input")
    void rejectsInvalid() {
        assertTrue(ColorNames.parse("").isEmpty());
        assertTrue(ColorNames.parse("   ").isEmpty());
        assertTrue(ColorNames.parse(null).isEmpty());
        assertTrue(ColorNames.parse("notacolor").isEmpty());
        assertTrue(ColorNames.parse("#FFF").isEmpty());      // wrong length
        assertTrue(ColorNames.parse("#GGGGGG").isEmpty());   // non-hex digits
        assertFalse(ColorNames.parse("aqua").isEmpty());
    }

    @Test
    @DisplayName("format round-trips named colors and falls back to hex")
    void formats() {
        assertEquals("red", ColorNames.format(0xFF5555));
        assertEquals("aqua", ColorNames.format(0x55FFFF));
        assertEquals("#123456", ColorNames.format(0x123456));
    }
}
