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
    @DisplayName("DEFAULT is visible, no color, no bypass choice, and is default")
    void defaultConstant() {
        assertFalse(PlayerPreferences.DEFAULT.hidden());
        assertNull(PlayerPreferences.DEFAULT.colorRgb());
        assertNull(PlayerPreferences.DEFAULT.bypass());
        assertTrue(PlayerPreferences.DEFAULT.isDefault());
    }

    @Test
    @DisplayName("isDefault is true only when not hidden, no color, and no explicit bypass")
    void isDefault() {
        assertTrue(new PlayerPreferences(false, null, null).isDefault());
        assertFalse(new PlayerPreferences(true, null, null).isDefault());
        assertFalse(new PlayerPreferences(false, 0x000000, null).isDefault());
        assertFalse(new PlayerPreferences(false, null, Boolean.FALSE).isDefault()); // explicit off still persists
        assertFalse(new PlayerPreferences(false, null, Boolean.TRUE).isDefault());
    }

    @Test
    @DisplayName("accessors return the supplied values")
    void accessors() {
        final PlayerPreferences prefs = new PlayerPreferences(true, 0x123456, Boolean.TRUE);
        assertTrue(prefs.hidden());
        assertEquals(0x123456, prefs.colorRgb());
        assertEquals(Boolean.TRUE, prefs.bypass());
    }
}
