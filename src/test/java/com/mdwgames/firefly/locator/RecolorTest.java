package com.mdwgames.firefly.locator;

import com.github.retrooper.packetevents.protocol.color.Color;
import com.github.retrooper.packetevents.protocol.world.waypoint.EmptyWaypointInfo;
import com.github.retrooper.packetevents.protocol.world.waypoint.TrackedWaypoint;
import com.github.retrooper.packetevents.protocol.world.waypoint.WaypointIcon;
import com.github.retrooper.packetevents.util.Either;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@DisplayName("WaypointManager.recolor")
class RecolorTest {

    private TrackedWaypoint waypoint(final WaypointIcon icon) {
        return new TrackedWaypoint(Either.createLeft(UUID.randomUUID()), icon, EmptyWaypointInfo.EMPTY);
    }

    @Test
    @DisplayName("changes the icon color while preserving identifier, style, and info")
    void recolorsPreservingEverythingElse() {
        final TrackedWaypoint original = waypoint(
                new WaypointIcon(WaypointIcon.ICON_STYLE_DEFAULT, new Color(0x00FF00)));

        final TrackedWaypoint recolored = WaypointManager.recolor(original, 0xFF8800);

        assertSame(original.getIdentifier(), recolored.getIdentifier());
        assertSame(original.getInfo(), recolored.getInfo());
        assertEquals(WaypointIcon.ICON_STYLE_DEFAULT, recolored.getIcon().getStyle());
        assertNotNull(recolored.getIcon().getColor());
        assertEquals(0xFF8800, recolored.getIcon().getColor().asRGB());
    }

    @Test
    @DisplayName("handles an original icon that had no color")
    void recolorsWhenOriginalHadNullColor() {
        final TrackedWaypoint original = waypoint(
                new WaypointIcon(WaypointIcon.ICON_STYLE_BOWTIE, null));

        final TrackedWaypoint recolored = WaypointManager.recolor(original, 0x123456);

        assertEquals(WaypointIcon.ICON_STYLE_BOWTIE, recolored.getIcon().getStyle());
        assertNotNull(recolored.getIcon().getColor());
        assertEquals(0x123456, recolored.getIcon().getColor().asRGB());
    }

    @Test
    @DisplayName("masks the color to 24 bits")
    void masksColor() {
        final TrackedWaypoint original = waypoint(
                new WaypointIcon(WaypointIcon.ICON_STYLE_DEFAULT, Color.WHITE));

        final TrackedWaypoint recolored = WaypointManager.recolor(original, 0xFFABCDEF);

        assertEquals(0xABCDEF, recolored.getIcon().getColor().asRGB());
    }
}
