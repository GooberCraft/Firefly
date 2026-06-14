package com.mdwgames.firefly.data;

import org.jetbrains.annotations.Nullable;

/**
 * Immutable snapshot of a single player's persisted locator-bar preferences.
 *
 * @param hidden   whether the player has hidden their own dot from others
 * @param colorRgb the player's chosen dot color as 0xRRGGBB, or {@code null} for the vanilla color
 * @param bypass   the admin's persisted see-all bypass choice as a tri-state: {@code null} means the
 *                 admin has never chosen (fall back to the config default), {@code true}/{@code false}
 *                 are explicit choices that survive across sessions
 */
public record PlayerPreferences(boolean hidden, @Nullable Integer colorRgb, @Nullable Boolean bypass) {

    /** A player with no customization: visible, vanilla color, no explicit bypass choice. */
    public static final PlayerPreferences DEFAULT = new PlayerPreferences(false, null, null);

    /** Whether this preference carries anything worth persisting (a default row is never stored). */
    public boolean isDefault() {
        return !hidden && colorRgb == null && bypass == null;
    }
}
