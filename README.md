# MineBridge

MineBridge relays Minecraft chat and player join/leave notifications to a
configured Discord text channel, and relays human messages from that channel
back to Minecraft.

## Requirements and compatibility

- Java 21 or newer to run the plugin.
- The build compiles against Spigot API `1.20.6-R0.1-SNAPSHOT` and declares
  `api-version: '1.20'`.
- The repository does not include a live-server compatibility matrix. These
  build settings do not certify any Paper release; test the plugin on the exact
  server version you intend to run.

## Build and install

Build the shaded plugin JAR from the repository root:

```sh
./gradlew clean test build
```

The distributable is `build/libs/MineBridge-1.0.0.jar`. Place that JAR in the
Paper server's `plugins/` directory, then start the server once to create
`plugins/MineBridge/config.yml`. Stop the server before replacing the JAR during
an upgrade.

## Discord setup and configuration

Set `discord.channel-id` in `plugins/MineBridge/config.yml` to the 17-20 digit
ID of the target Discord text channel. Enable Developer Mode in Discord to copy
the channel ID. The bot needs access to that channel with permission to view it
and send messages. MineBridge enables the `GUILD_MESSAGES` and privileged
`MESSAGE_CONTENT` gateway intents; enable Message Content Intent for the bot in
the Discord Developer Portal. The implementation does not require member
presences or a populated member cache.

Prefer setting the bot token in the server process environment as
`MINEBRIDGE_BOT_TOKEN`. A non-blank environment value takes precedence over
`discord.bot-token`; the config token remains supported for existing
installations. Do not put a real token in source control, paste it into chat,
or include it in logs. The shipped config intentionally has an empty token.

The existing config format contains the Discord token and channel ID, four
message templates, and `features.chat-relay` /
`features.join-leave-notifications` toggles. Templates must be non-empty, no
longer than 2000 characters, and use only balanced, supported placeholders:

| Template | Supported placeholders |
| --- | --- |
| `messages.minecraft-to-discord` | `{player}`, `{message}`, `{timestamp}` |
| `messages.discord-to-minecraft` | `{player}`, `{display_name}`, `{username}`, `{message}`, `{timestamp}` |
| `messages.player-join` | `{player}`, `{timestamp}` |
| `messages.player-leave` | `{player}`, `{timestamp}` |

`{timestamp}` is UTC ISO-8601 and is inserted only when present in a template.
For Discord messages, `{player}` remains the effective author name used by
previous versions; `{display_name}` uses the guild member display name when
available and otherwise falls back to the effective author name. `{username}`
is the Discord account username. These are display values only and do not link
Discord and Minecraft accounts. Placeholder values are inserted once and are
never parsed as template syntax.

Incorrect types for recognized settings, missing credentials, invalid channel
IDs, empty templates, malformed or unsupported placeholders, and templates
longer than 2000 characters are rejected. A rejected reload does not replace
the active config or Discord client. Edit config while the server is running
and use `/minebridge reload`; success is reported only after the replacement
Discord gateway reaches Ready. Reload requests use latest-request-wins
semantics: a newer attempt supersedes an older pending candidate even if the
newer configuration is invalid or its Discord connection fails. A failed
attempt leaves the currently active connection and settings in place; an older
candidate will not be applied afterward.

## Administration and troubleshooting

- `/minebridge status` reports connection state, cached channel availability,
  outgoing queue size/capacity, plugin uptime, and both feature toggles. It
  does not make a REST request to check channel availability.
- `/minebridge reload` is available only to users with `minebridge.admin`
  (default: server operators). Help and tab completion hide it from others.
- A `config.yml` validation warning identifies the setting to correct without
  echoing the bot token. For YAML parse/read failures, check that the file is
  valid YAML and readable by the server process.
- If login fails or does not reach Ready, verify the token, network access, and
  that Message Content Intent is enabled in the Developer Portal.
- If status reports the channel unavailable, verify the configured channel ID
  and that the bot can view the channel. JDA's channel cache is used; status
  does not prove that a send will be accepted.
- Connection, channel-unavailability, and outgoing-drop diagnostics are
  rate-limited per category. They never include token values.

For upgrades, back up `plugins/MineBridge/config.yml` and keep a copy of the
previous JAR before replacement. The plugin preserves the current config file;
review new release notes before manually merging any newly documented settings.

## Reliability behavior

Discord startup and reload run asynchronously, and gateway resume/reconnect is
handled by JDA's existing client rather than constructing duplicate clients.
`/minebridge status` reports the connection state without revealing
configuration values, configured-channel cache availability, outgoing queue
size/capacity, plugin uptime, and both feature toggles. Channel availability is
a local JDA cache check; status does not perform a Discord REST request.
`/minebridge reload` is admin-only, reloads the config, and replaces the JDA
client. It reports configuration/startup errors immediately and reports success
only after the Discord gateway is ready. If readiness is not confirmed within
45 seconds, it reports the current JDA state rather than claiming success.
Help and tab completion hide the reload action from users without
`minebridge.admin`.

Discord events accepted before a successful client swap finish using that
client's immutable configuration snapshot. Events arriving from the old client
after the swap are ignored.

Connection, channel-availability, and outgoing-drop diagnostics are rate
limited per category. Diagnostics contain actionable configuration/network
guidance and do not include token values.

Outgoing messages use a bounded queue of 100 messages. Messages are retained
while disconnected or while the configured channel cannot be resolved from
JDA's cache. If the queue is full, a new message is dropped and a rate-limited
warning is logged. Messages longer than Discord's 2000-character limit are
dropped rather than truncated, and one rate-limited warning is emitted per
diagnostic interval. Minecraft formatting control codes are removed from
relayed user text and line breaks are normalized to spaces. Discord
`@everyone` and `@here` in relayed Minecraft text are neutralized; JDA also
disables all parsed mentions for outgoing messages. Discord Markdown syntax in
user-controlled Minecraft text is escaped, while trusted configured templates
retain their formatting. Other Unicode, including emoji, is preserved. Discord
message syntax such as `<@id>` is shown as text in Minecraft and does not
execute commands or formatting.

API-rejected messages are not retried, to avoid duplicate delivery; therefore a
network failure after JDA accepts a request can lose that message. Messages
remain FIFO while queued, and only one Discord send is in flight at a time.
The in-memory queue is discarded on plugin shutdown. A message accepted by
Discord but whose success callback is interrupted by shutdown has an
indeterminate delivery result.
