# Changelog

All notable changes to MineBridge are documented here. Versions use semantic
versioning: breaking configuration or command changes require a major release;
backward-compatible features use a minor release; compatible fixes use a patch
release.

## Unreleased

- Add continuous integration for Java 21 builds and tests.
- Preserve immutable configuration snapshots across Discord client reloads.
- Avoid scheduling inbound Discord chat after the Minecraft server has begun
  shutting down.
