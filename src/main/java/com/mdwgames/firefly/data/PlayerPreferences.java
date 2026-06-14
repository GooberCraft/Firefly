package com.mdwgames.firefly.data;

import org.jetbrains.annotations.Nullable;

/**
 * Immutable snapshot of a single player's persisted locator-bar preferences.
 *
 * @param hidden   whether the player has hidden their own dot from others
 * @param colorRgb the player's chosen dot color as 0xRRGGBB, or {@code null} for the vanilla color
 */
public record PlayerPreferences(boolean hidden, @Nullable Integer colorRgb) {

    /** A player with no customization: visible, vanilla color. */
    public static final PlayerPreferences DEFAULT = new PlayerPreferences(false, null);

    /** Whether this preference carries anything worth persisting. */
    public boolean isDefault() {
        return !hidden && colorRgb == null;
    }
}
