# Firefly

[![Build Status](https://github.com/GooberCraft/Firefly/workflows/Build/badge.svg)](https://github.com/GooberCraft/Firefly/actions)
[![CodeFactor](https://www.codefactor.io/repository/github/goobercraft/firefly/badge)](https://www.codefactor.io/repository/github/goobercraft/firefly)
[![GitHub release](https://img.shields.io/github/v/release/GooberCraft/Firefly)](https://github.com/GooberCraft/Firefly/releases)
[![bStats Servers](https://img.shields.io/bstats/servers/31993)](https://bstats.org/plugin/bukkit/Firefly/31993)

Firefly gives players control over the **locator bar** — the on-screen bar added in Minecraft
1.21.6+ that shows other players' positions as colored dots/waypoints. Players can hide their own
dot from everyone else and recolor it; admins can bypass hiding to see everyone, list who is
currently hidden, and configure the default behavior.

Firefly works entirely by intercepting the server→client `WAYPOINT` packet through
[packetevents](https://github.com/retrooper/packetevents) — there is no Bukkit API for per-viewer
locator control, so packetevents is a hard dependency.

## Requirements

- A **Paper or Spigot 1.21.6+** server (the locator bar exists from 1.21.6 onward; Firefly also
  targets the Minecraft 26.1 waypoint update).
- The **[packetevents](https://www.spigotmc.org/resources/packetevents.80279/)** plugin installed in
  `plugins/`. Firefly will refuse to enable without it.
- Clients on 1.21.6+ to actually render the locator bar.

## Installation

1. Install the **packetevents** plugin in your server's `plugins/` folder.
2. Drop `Firefly-1.0.jar` (from `target/` after building) into `plugins/`.
3. Start the server. Firefly creates `plugins/Firefly/config.yml` and `plugins/Firefly/playerdata.yml`.

## Commands

Base command: `/firefly` (alias `/ff`).

| Command | Permission | Description |
| --- | --- | --- |
| `/firefly hide` | `firefly.use` | Hide your dot from other players' locator bars. |
| `/firefly show` | `firefly.use` | Show your dot again. |
| `/firefly toggle` | `firefly.use` | Flip your hidden state. |
| `/firefly color <name\|#RRGGBB>` | `firefly.use` | Set the color of your dot as others see it. |
| `/firefly color reset` | `firefly.use` | Reset your dot to the vanilla color. |
| `/firefly bypass <on\|off\|toggle>` | `firefly.admin` | See-all: reveal players who have hidden their dot. |
| `/firefly showhidden` | `firefly.admin` | List players who currently have their dot hidden. |
| `/firefly reload` | `firefly.admin` | Reload `config.yml` and `playerdata.yml`. |

### Colors

`/firefly color` accepts either one of the sixteen Minecraft color names (tab-completed) or a
`#RRGGBB` hex code for any RGB color:

```
/ff color aqua
/ff color #FF8800
/ff color reset
```

Named colors: `black`, `dark_blue`, `dark_green`, `dark_aqua`, `dark_red`, `dark_purple`, `gold`,
`gray`, `dark_gray`, `blue`, `green`, `aqua`, `red`, `light_purple`, `yellow`, `white`.

## Permissions

| Permission | Default | Grants |
| --- | --- | --- |
| `firefly.use` | everyone | Hide/show/toggle and recolor your own dot. |
| `firefly.admin` | ops | Bypass hiding, list hidden players, reload. |

## Configuration

`plugins/Firefly/config.yml`:

```yaml
admin-bypass:
  # When an admin (firefly.admin) joins, should "see-all" bypass start enabled?
  #   false - admins respect player hiding until they opt in (recommended)
  #   true  - admins see everyone by default
  default: false
```

`bypass` is per-admin and runtime-only: this option only sets the value each admin starts a session
with. Admins flip it live with `/firefly bypass`.

### Persistence

Hide state and chosen colors are stored in `plugins/Firefly/playerdata.yml`, keyed by player UUID,
and survive relogs and restarts. Bypass is intentionally **not** persisted.

## How it works

The locator bar is driven by one `WAYPOINT` packet per tracked player, with `TRACK` / `UPDATE` /
`UNTRACK` operations keyed by the transmitter's UUID. Firefly's
[`WaypointManager`](src/main/java/com/mdwgames/firefly/locator/WaypointManager.java):

- **Hides** a dot by cancelling its `TRACK`/`UPDATE` (or rewriting it to `UNTRACK` if already shown),
  when the transmitter is hidden and the receiver isn't bypassing.
- **Recolors** a visible dot by rebuilding its waypoint with a new icon color.
- **Reconciles actively**: toggling hide/color or bypass produces no vanilla packets, so the manager
  caches each waypoint's last payload and pushes a surgical `UNTRACK`/`TRACK`/`UPDATE` the moment a
  preference changes — no waiting for the player to move out of range.

Packet interception runs on netty I/O threads (read-only against concurrent state); all packet
*sends* and reconciliation run on the main thread.

## Building

```bash
mvn clean package
```

Produces `target/Firefly-1.0.jar` and runs the test suite (JUnit 6 + Mockito + MockBukkit).
packetevents and the Paper API are `provided` — they are not bundled into the jar.

## Project layout

```
src/main/java/com/mdwgames/firefly/
  Firefly.java                      plugin entry point / wiring
  command/FireflyCommand.java       /firefly executor + tab completion
  data/PlayerPreferences.java       immutable preference snapshot
  data/PreferenceStore.java         in-memory state + playerdata.yml persistence
  listener/PlayerSessionListener.java  seeds bypass on join, cleans up on quit
  locator/WaypointManager.java      packetevents listener — the core
  util/ColorNames.java              named-color / hex parsing
src/test/java/com/mdwgames/firefly/  tests mirror the production packages
```
