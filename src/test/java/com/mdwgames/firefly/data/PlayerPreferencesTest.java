package com.mdwgames.firefly.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("PlayerPreferences")
class PlayerPreferencesTest {

    @Test
    @DisplayName("DEFAULT is visible with no color and is default")
    void defaultConstant() {
        assertFalse(PlayerPreferences.DEFAULT.hidden());
        assertNull(PlayerPreferences.DEFAULT.colorRgb());
        assertTrue(PlayerPreferences.DEFAULT.isDefault());
    }

    @Test
    @DisplayName("isDefault is true only when not hidden and no color")
    void isDefault() {
        assertTrue(new PlayerPreferences(false, null).isDefault());
        assertFalse(new PlayerPreferences(true, null).isDefault());
        assertFalse(new PlayerPreferences(false, 0x000000).isDefault());
        assertFalse(new PlayerPreferences(true, 0xFF5555).isDefault());
    }

    @Test
    @DisplayName("accessors return the supplied values")
    void accessors() {
        final PlayerPreferences prefs = new PlayerPreferences(true, 0x123456);
        assertTrue(prefs.hidden());
        assertEquals(0x123456, prefs.colorRgb());
    }
}
