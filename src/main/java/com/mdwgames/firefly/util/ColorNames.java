package com.mdwgames.firefly.util;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Maps the sixteen standard Minecraft color names to their RGB values and parses user-supplied
 * colors for {@code /firefly color}. Accepts either a named color (case-insensitive) or a
 * {@code #RRGGBB} hex string — the locator-bar waypoint packet carries a true RGB color, so hex
 * gives players the full range while names stay discoverable via tab-completion.
 *
 * <p>Pure and Bukkit-free so it can be unit-tested in isolation.</p>
 */
public final class ColorNames {

    /** Insertion order is the tab-completion order; values are the vanilla chat-color RGBs. */
    private static final Map<String, Integer> NAMED = new LinkedHashMap<>();

    static {
        NAMED.put("black", 0x000000);
        NAMED.put("dark_blue", 0x0000AA);
        NAMED.put("dark_green", 0x00AA00);
        NAMED.put("dark_aqua", 0x00AAAA);
        NAMED.put("dark_red", 0xAA0000);
        NAMED.put("dark_purple", 0xAA00AA);
        NAMED.put("gold", 0xFFAA00);
        NAMED.put("gray", 0xAAAAAA);
        NAMED.put("dark_gray", 0x555555);
        NAMED.put("blue", 0x5555FF);
        NAMED.put("green", 0x55FF55);
        NAMED.put("aqua", 0x55FFFF);
        NAMED.put("red", 0xFF5555);
        NAMED.put("light_purple", 0xFF55FF);
        NAMED.put("yellow", 0xFFFF55);
        NAMED.put("white", 0xFFFFFF);
    }

    private ColorNames() {
    }

    /**
     * Parses a named color or a {@code #RRGGBB} / {@code RRGGBB} hex string into a 0xRRGGBB int.
     *
     * @return the RGB value, or an empty optional if the input is neither a known name nor valid hex
     */
    public static @NotNull OptionalInt parse(final String input) {
        if (input == null || input.isBlank()) {
            return OptionalInt.empty();
        }
        final String trimmed = input.trim();

        final Integer named = NAMED.get(trimmed.toLowerCase());
        if (named != null) {
            return OptionalInt.of(named);
        }

        final String hex = trimmed.startsWith("#") ? trimmed.substring(1) : trimmed;
        if (hex.length() != 6) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(Integer.parseInt(hex, 16) & 0xFFFFFF);
        } catch (final NumberFormatException ignored) {
            return OptionalInt.empty();
        }
    }

    /**
     * Renders an RGB value for display: the matching color name if one exists, otherwise
     * {@code #RRGGBB}.
     */
    public static @NotNull String format(final int rgb) {
        final int masked = rgb & 0xFFFFFF;
        for (final Map.Entry<String, Integer> entry : NAMED.entrySet()) {
            if (entry.getValue() == masked) {
                return entry.getKey();
            }
        }
        return String.format("#%06X", masked);
    }

    /** The sixteen named colors in display order, for tab-completion. */
    public static @NotNull List<String> names() {
        return List.copyOf(NAMED.keySet());
    }

    /** Read-only view of name → RGB, for tests and tooling. */
    public static @NotNull Map<String, Integer> all() {
        return Collections.unmodifiableMap(NAMED);
    }
}
