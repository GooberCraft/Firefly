package com.mdwgames.firefly.locator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("WaypointManager.shouldHide")
class WaypointPredicateTest {

    private final UUID receiver = UUID.randomUUID();
    private final UUID transmitter = UUID.randomUUID();

    @Test
    @DisplayName("a visible transmitter is never hidden")
    void notHiddenWhenVisible() {
        assertFalse(WaypointManager.shouldHide(receiver, transmitter, false, false));
        assertFalse(WaypointManager.shouldHide(receiver, transmitter, false, true));
    }

    @Test
    @DisplayName("a hidden transmitter is hidden from a non-bypassing receiver")
    void hiddenWhenHiddenAndNoBypass() {
        assertTrue(WaypointManager.shouldHide(receiver, transmitter, true, false));
    }

    @Test
    @DisplayName("a bypassing receiver sees a hidden transmitter")
    void visibleWhenBypassing() {
        assertFalse(WaypointManager.shouldHide(receiver, transmitter, true, true));
    }

    @Test
    @DisplayName("a player never hides their own dot")
    void neverHidesSelf() {
        assertFalse(WaypointManager.shouldHide(receiver, receiver, true, false));
    }
}
