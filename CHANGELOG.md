# Changelog

All notable changes to Firefly are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.1.2] - 2026-06-16

### Added
- Firefly is now distributed on **Modrinth** in addition to GitHub Releases; each published release
  is uploaded automatically.
- The project is now licensed under **GPL-3.0-only** (added `LICENSE`).

### Changed
- Compatibility is now verified across the full supported range: the build compiles and tests against
  the **1.21.6** floor (the minimum supported version), and a dedicated forward-compatibility job
  verifies the latest supported build (**Minecraft 26.1.2**).

## [1.1.1] - 2026-06-15

### Security
- Player-supplied text echoed in messages (e.g. an unknown color in `/firefly color`) can no longer
  inject color or formatting codes into the rendered message.

### Fixed
- The H2/MySQL connection pool is no longer leaked when `/firefly reload` re-initializes storage; the
  previous pool is now closed first.

## [1.1.0] - 2026-06-15

### Added
- Customizable, translatable messages via `messages.yml` — every player-facing string can be edited,
  colored with legacy `&` codes, and reloaded with `/firefly reload`; missing keys fall back to the
  built-in defaults.

[Unreleased]: https://github.com/GooberCraft/Firefly/compare/v1.1.2...HEAD
[1.1.2]: https://github.com/GooberCraft/Firefly/releases/tag/v1.1.2
[1.1.1]: https://github.com/GooberCraft/Firefly/releases/tag/v1.1.1
[1.1.0]: https://github.com/GooberCraft/Firefly/releases/tag/v1.1.0
