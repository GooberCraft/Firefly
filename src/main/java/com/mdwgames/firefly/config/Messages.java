package com.mdwgames.firefly.config;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads and formats the customizable, localizable plugin messages from {@code messages.yml}.
 *
 * <p>On {@link #load()} the bundled default {@code /messages.yml} is read first, then the operator's
 * file overlays it — so any key the operator deletes or hasn't translated falls back to the built-in
 * English default, and the file never breaks across plugin updates that add new messages.</p>
 *
 * <p>{@link #get} substitutes {@code {prefix}} (the configurable prefix) and any caller-supplied
 * {@code {token}} placeholders, then translates legacy {@code &} color codes — so messages work on
 * Spigot, Paper, and Folia alike.</p>
 */
// ChatColor.translateAlternateColorCodes is deprecated on Paper, but legacy '&' codes are used
// deliberately for cross-platform (Spigot) message coloring — see FireflyCommand.
@SuppressWarnings("deprecation")
public final class Messages {

    private static final String RESOURCE = "/messages.yml";
    private static final String PREFIX_KEY = "prefix";

    private final File file;
    private final Logger logger;
    private final Map<String, String> defaults = new HashMap<>();
    private final Map<String, String> overrides = new HashMap<>();

    public Messages(@NotNull final File file, @NotNull final Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    /** Reloads the bundled defaults and the operator's overrides. Safe to call from {@code reload}. */
    public void load() {
        defaults.clear();
        overrides.clear();

        try (InputStream in = Messages.class.getResourceAsStream(RESOURCE)) {
            if (in != null) {
                copyStrings(YamlConfiguration.loadConfiguration(
                        new InputStreamReader(in, StandardCharsets.UTF_8)), defaults);
            }
        } catch (final IOException e) {
            logger.log(Level.WARNING, "Failed to read the bundled messages.yml", e);
        }

        if (file.exists()) {
            copyStrings(YamlConfiguration.loadConfiguration(file), overrides);
        }
    }

    /**
     * Returns a formatted message. {@code placeholders} is a flat list of {@code token, value}
     * pairs; each {@code {token}} in the message is replaced with its value. {@code {prefix}} is
     * always substituted, then legacy {@code &} codes are translated.
     */
    public @NotNull String get(@NotNull final String key, @NotNull final String... placeholders) {
        String raw = overrides.getOrDefault(key, defaults.getOrDefault(key, "&cmissing message: " + key));
        raw = raw.replace("{prefix}", overrides.getOrDefault(PREFIX_KEY, defaults.getOrDefault(PREFIX_KEY, "")));
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            raw = raw.replace("{" + placeholders[i] + "}", placeholders[i + 1]);
        }
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    private static void copyStrings(final YamlConfiguration yaml, final Map<String, String> into) {
        for (final String key : yaml.getKeys(false)) {
            if (yaml.isString(key)) {
                into.put(key, yaml.getString(key));
            }
        }
    }
}
