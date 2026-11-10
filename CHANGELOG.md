# Changelog

All notable changes to MineBridge are documented here. Versions use semantic
versioning: breaking configuration or command changes require a major release;
backward-compatible features use a minor release; compatible fixes use a patch
release.

## [Unreleased]

### Added

- No unreleased changes.

## [1.2.0] - 2026-11-10

### Added

- Configurable Minecraft death messages.
- Discord death notification embeds.
- Minecraft advancement announcements.
- Discord achievement embeds.
- Configurable announcement templates and channels.
- Advancement category classification and challenge-specific announcements.
- Join, leave, and first-join Discord event embeds.
- A shared Discord event embed factory/notifier with timestamps, standard
  footer, and disabled allowed mentions.

### Changed

- Extend the bundled configuration and README with Death Messages and
  Achievement Announcements setup and compatibility details.
- Route player, advancement, and death notifications through the shared
  embed-only delivery path.
- Set the project and plugin metadata version to 1.2.0.

### Fixed

- Suppress the vanilla death line when MineBridge broadcasts its replacement,
  preventing duplicate Minecraft announcements.
- Prevent duplicate advancement announcements within the deduplication window.
- Prevent duplicate join, leave, and death notifications during the
  deduplication window.

### Removed

- No existing features removed.

### Security

- Escape user-controlled Discord Markdown and disable parsed mentions in both
  death and advancement notifications.

### Testing

- Add automated coverage for death classification, advancement category and
  challenge detection, duplicate prevention, message/embed formatting,
  join/leave/first-join embeds, mention suppression, delivery failures,
  notification policy, and configuration validation.

## 1.0.1 - 2026-11-02

### Added

- Add continuous integration for Java 21 builds and tests.
- Add regression and concurrency tests for outbound delivery, inbound
  processing, reloads, and blank Minecraft chat.

### Changed

- Preserve immutable configuration snapshots across Discord client reloads.
- Process Discord-to-Minecraft messages through a bounded FIFO queue.
- Drop new inbound messages when the queue is full and emit rate-limited
  diagnostics for queue overflow.

### Fixed

- Track ownership of in-flight Discord sends so client replacement cannot let
  an older send callback release a newer send's gate.
- Avoid scheduling or processing inbound Discord chat after shutdown begins;
  pending inbound messages are discarded during shutdown.
- Ignore empty and whitespace-only Minecraft chat messages when relaying to
  Discord.

### Security

- Limit inbound Discord chat per user and globally to reduce the impact of
  message floods.
