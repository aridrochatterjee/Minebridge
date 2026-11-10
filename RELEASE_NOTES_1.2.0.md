# MineBridge 1.2.0

MineBridge 1.2.0 combines Death Messages and Achievement Announcements with
the existing Minecraft-Discord integration. The project remains on Java 21,
Spigot API 1.20.6, and JDA 5.6.1.

## Added

- Configurable Minecraft death messages with player, mob, and environmental
  cause categories.
- Discord death notification embeds with configurable channel, title,
  description, color, and category-specific templates.
- Minecraft advancement announcements for Story, Nether, End, Adventure,
  Husbandry, and other advancement categories.
- Challenge-specific Minecraft announcements and Discord achievement embeds
  containing player, title, description, category, and challenge type.
- Independent notification, category, and challenge toggles, plus customizable
  message templates and Discord channels.
- First-time player welcome embeds and regular join/leave embeds.
- A shared embed-only event delivery path with configurable footer and
  asynchronous JDA delivery.
- Discord event settings for independent player, advancement category,
  challenge, and death-category channels and toggles.

## Reliability and security

- Discord announcements use JDA's asynchronous request queue and do not block
  the Minecraft server thread.
- Duplicate death lines are avoided when MineBridge broadcasts its own
  replacement; repeated advancement events are deduplicated with a bounded
  in-memory cache.
- Bukkit does not expose a cancellable advancement-completion event; when the
  `announceAdvancements` gamerule is on, vanilla advancement lines may appear
  alongside custom messages. The plugin does not change this server-wide rule.
- Dynamic Discord text is Markdown-escaped and outgoing announcement requests
  disable automatic mentions.
- Player join/leave/first-join, advancement/challenge, and death event
  notifications are sent only as Discord embeds.
- Missing Discord channels and API failures are logged without interrupting
  gameplay.

## Configuration and installation

Back up the existing `plugins/MineBridge/config.yml`, install
`build/libs/MineBridge-1.2.0.jar` in the server's `plugins/` directory, then
start the server. New settings are merged from the bundled defaults when
MineBridge loads configuration. Configure the Discord bot token and channel
using the existing `discord` settings; feature-specific channels may be left
blank to use the main configured channel.

See [README.md](./README.md) for the full configuration reference and Discord
channel permissions. The bot needs permission to view and send messages in each
configured notification channel.

## Verification

The release build targets Java 21 and Spigot API 1.20.6 and retains JDA 5.6.1.
Run `./gradlew clean build` to execute tests and produce the shaded plugin JAR.
Live-server and live-Discord integration testing is not part of the automated
build.
