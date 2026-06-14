package com.mdwgames.firefly.command;

import com.mdwgames.firefly.data.PreferenceStore;
import com.mdwgames.firefly.locator.WaypointManager;
import com.mdwgames.firefly.util.ColorNames;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

/**
 * Handles {@code /firefly} (alias {@code /ff}). Self-service subcommands (hide/show/toggle/color)
 * are gated by {@code firefly.use}; admin subcommands (bypass/showhidden/reload) by
 * {@code firefly.admin}. Each preference change calls {@link WaypointManager#refresh()} so it applies
 * to the locator bar immediately.
 */
public final class FireflyCommand implements CommandExecutor, TabCompleter {

    public static final String PERM_USE = "firefly.use";
    public static final String PERM_ADMIN = "firefly.admin";

    private static final String PREFIX = ChatColor.GOLD + "[Firefly] " + ChatColor.RESET;

    private static final List<String> USER_SUBS = List.of("hide", "show", "toggle", "color");
    private static final List<String> ADMIN_SUBS = List.of("bypass", "showhidden", "reload");

    private final Plugin plugin;
    private final PreferenceStore store;
    private final WaypointManager manager;

    public FireflyCommand(@NotNull final Plugin plugin, @NotNull final PreferenceStore store,
                          @NotNull final WaypointManager manager) {
        this.plugin = plugin;
        this.store = store;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull final CommandSender sender, @NotNull final Command command,
                             @NotNull final String label, @NotNull final String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "hide" -> handleSetHidden(sender, true);
            case "show" -> handleSetHidden(sender, false);
            case "toggle" -> handleToggle(sender);
            case "color", "colour" -> handleColor(sender, args);
            case "bypass" -> handleBypass(sender, args);
            case "showhidden" -> handleShowHidden(sender);
            case "reload" -> handleReload(sender);
            case "help" -> sendHelp(sender);
            default -> {
                sender.sendMessage(PREFIX + ChatColor.RED + "Unknown subcommand. Try /" + label + " help");
            }
        }
        return true;
    }

    // ========== Self-service (firefly.use) ==========

    private void handleSetHidden(final CommandSender sender, final boolean hide) {
        final Player player = requireUser(sender);
        if (player == null) {
            return;
        }
        store.setHidden(player.getUniqueId(), hide);
        manager.refresh();
        if (hide) {
            sender.sendMessage(PREFIX + ChatColor.GREEN + "Your locator-bar dot is now hidden from other players.");
        } else {
            sender.sendMessage(PREFIX + ChatColor.GREEN + "Your locator-bar dot is now visible to other players.");
        }
    }

    private void handleToggle(final CommandSender sender) {
        final Player player = requireUser(sender);
        if (player == null) {
            return;
        }
        final boolean nowHidden = !store.isHidden(player.getUniqueId());
        store.setHidden(player.getUniqueId(), nowHidden);
        manager.refresh();
        sender.sendMessage(PREFIX + ChatColor.GREEN + "Your locator-bar dot is now "
                + (nowHidden ? "hidden." : "visible."));
    }

    private void handleColor(final CommandSender sender, final String[] args) {
        final Player player = requireUser(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Usage: /firefly color <name|#RRGGBB|reset>");
            return;
        }
        if (args[1].equalsIgnoreCase("reset") || args[1].equalsIgnoreCase("clear")) {
            store.clearColor(player.getUniqueId());
            manager.refresh();
            sender.sendMessage(PREFIX + ChatColor.GREEN + "Your locator-bar dot color was reset to default.");
            return;
        }
        final OptionalInt parsed = ColorNames.parse(args[1]);
        if (parsed.isEmpty()) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Unknown color '" + args[1]
                    + "'. Use a named color or #RRGGBB hex.");
            return;
        }
        store.setColor(player.getUniqueId(), parsed.getAsInt());
        manager.refresh();
        sender.sendMessage(PREFIX + ChatColor.GREEN + "Your locator-bar dot color is now "
                + ColorNames.format(parsed.getAsInt()) + ".");
    }

    // ========== Admin (firefly.admin) ==========

    private void handleBypass(final CommandSender sender, final String[] args) {
        final Player player = requireAdminPlayer(sender);
        if (player == null) {
            return;
        }
        final boolean current = store.isBypassing(player.getUniqueId());
        final boolean target;
        if (args.length < 2 || args[1].equalsIgnoreCase("toggle")) {
            target = !current;
        } else if (args[1].equalsIgnoreCase("on")) {
            target = true;
        } else if (args[1].equalsIgnoreCase("off")) {
            target = false;
        } else {
            sender.sendMessage(PREFIX + ChatColor.RED + "Usage: /firefly bypass <on|off|toggle>");
            return;
        }
        store.setBypass(player.getUniqueId(), target);
        manager.refresh();
        sender.sendMessage(PREFIX + ChatColor.GREEN + "See-all bypass is now " + (target ? "ON" : "OFF")
                + ChatColor.GREEN + ". You " + (target ? "now see" : "no longer see")
                + " players who hid their dot.");
    }

    private void handleShowHidden(final CommandSender sender) {
        if (!sender.hasPermission(PERM_ADMIN)) {
            denied(sender);
            return;
        }
        final Set<UUID> hidden = store.hiddenPlayers();
        if (hidden.isEmpty()) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + "No players currently have their dot hidden.");
            return;
        }
        sender.sendMessage(PREFIX + ChatColor.YELLOW + "Players hiding their locator-bar dot ("
                + hidden.size() + "):");
        for (final UUID uuid : hidden) {
            final OfflinePlayer off = Bukkit.getOfflinePlayer(uuid);
            final String name = off.getName() != null ? off.getName() : uuid.toString();
            final boolean online = off.isOnline();
            sender.sendMessage("  " + (online ? ChatColor.GREEN : ChatColor.GRAY) + name
                    + (online ? "" : " (offline)"));
        }
    }

    private void handleReload(final CommandSender sender) {
        if (!sender.hasPermission(PERM_ADMIN)) {
            denied(sender);
            return;
        }
        plugin.reloadConfig();
        store.load();
        manager.refresh();
        sender.sendMessage(PREFIX + ChatColor.GREEN + "Firefly configuration and player data reloaded.");
    }

    // ========== Helpers ==========

    private @Nullable Player requireUser(final CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Only players can use this command.");
            return null;
        }
        if (!sender.hasPermission(PERM_USE)) {
            denied(sender);
            return null;
        }
        return player;
    }

    private @Nullable Player requireAdminPlayer(final CommandSender sender) {
        if (!sender.hasPermission(PERM_ADMIN)) {
            denied(sender);
            return null;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Only players can toggle their own bypass.");
            return null;
        }
        return player;
    }

    private void denied(final CommandSender sender) {
        sender.sendMessage(PREFIX + ChatColor.RED + "You don't have permission to do that.");
    }

    private void sendHelp(final CommandSender sender) {
        sender.sendMessage(PREFIX + ChatColor.YELLOW + "Locator-bar controls:");
        if (sender.hasPermission(PERM_USE)) {
            sender.sendMessage(ChatColor.GOLD + "  /firefly hide|show|toggle" + ChatColor.GRAY + " - hide or show your dot");
            sender.sendMessage(ChatColor.GOLD + "  /firefly color <name|#RRGGBB|reset>" + ChatColor.GRAY + " - set your dot color");
        }
        if (sender.hasPermission(PERM_ADMIN)) {
            sender.sendMessage(ChatColor.GOLD + "  /firefly bypass <on|off|toggle>" + ChatColor.GRAY + " - see hidden players");
            sender.sendMessage(ChatColor.GOLD + "  /firefly showhidden" + ChatColor.GRAY + " - list hidden players");
            sender.sendMessage(ChatColor.GOLD + "  /firefly reload" + ChatColor.GRAY + " - reload config & player data");
        }
    }

    // ========== Tab completion ==========

    @Override
    public @Nullable List<String> onTabComplete(@NotNull final CommandSender sender,
                                                @NotNull final Command command,
                                                @NotNull final String alias, @NotNull final String[] args) {
        if (args.length == 1) {
            final List<String> subs = new ArrayList<>();
            if (sender.hasPermission(PERM_USE)) {
                subs.addAll(USER_SUBS);
            }
            if (sender.hasPermission(PERM_ADMIN)) {
                subs.addAll(ADMIN_SUBS);
            }
            subs.add("help");
            return filter(subs, args[0]);
        }
        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "color", "colour" -> {
                    if (sender.hasPermission(PERM_USE)) {
                        final List<String> opts = new ArrayList<>(ColorNames.names());
                        opts.add("reset");
                        return filter(opts, args[1]);
                    }
                }
                case "bypass" -> {
                    if (sender.hasPermission(PERM_ADMIN)) {
                        return filter(List.of("on", "off", "toggle"), args[1]);
                    }
                }
                default -> {
                    return List.of();
                }
            }
        }
        return List.of();
    }

    private static List<String> filter(final List<String> options, final String prefix) {
        final String lower = prefix.toLowerCase();
        final List<String> out = new ArrayList<>();
        for (final String opt : options) {
            if (opt.toLowerCase().startsWith(lower)) {
                out.add(opt);
            }
        }
        return out;
    }
}
