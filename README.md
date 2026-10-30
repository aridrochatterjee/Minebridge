# MineBridge

MineBridge relays Minecraft chat and player join/leave notifications to a
configured Discord text channel, and relays human messages from that channel
back to Minecraft.

It also provides Minecraft commands for Discord connection status and server
information. Discord messages are accepted only from the configured guild
channel; bot and webhook messages are ignored.

Current features:

- Bidirectional chat relay and player join/leave notifications.
- In-game Discord status, server capacity, and online-player commands.
- Configurable message templates, with validated placeholders and safe handling
  of user-provided text.

## Requirements and compatibility

- Java 21 or newer is required to run the plugin.
- The project compiles against Spigot API `1.20.6-R0.1-SNAPSHOT` and declares
  `api-version: '1.20'`; Spigot 1.20.6 is the configured build target.
- No live-server compatibility matrix is maintained. Paper compatibility and
  other server versions are not certified; test the exact server build you
  intend to run.

## Build and install

Build the shaded plugin JAR from the repository root:

```sh
./gradlew build
```

The distributable is `build/libs/MineBridge-1.0.0.jar` for the current project
version. Place it in the `plugins/` directory of the server you have chosen to
test, then start the server once to create `plugins/MineBridge/config.yml`.
Stop the server before replacing the JAR during an upgrade.

## Testing and releases

Run the test suite with `./gradlew test`. Run the full build, including tests
and the shaded plugin JAR, with `./gradlew build`; use `./gradlew clean build`
for a clean build.
The GitHub Actions workflow runs the build on Java 21 for pushes and pull
requests and retains the generated plugin JAR as a CI artifact. It does not
deploy the plugin. Dependency vulnerability scanning runs only when the
`NVD_API_KEY` secret is available; otherwise the workflow skips it and reports
that dependency vulnerability status is unverified.
Dependabot checks Gradle and GitHub Actions dependencies weekly; review its
pull requests with the normal CI results before merging.

Before a release, update `version` in `build.gradle` and `plugin.yml`, add a
dated entry to [CHANGELOG.md](CHANGELOG.md), then build and test from a clean
checkout. Review the [1.0.1 release-note draft](RELEASE_NOTES_1.0.1.md) and test
the resulting JAR against the intended Minecraft server and a Discord test
server. For rollback, stop the server, restore the previous JAR and the
backed-up `config.yml`, then start the server and check `/minebridge status`.

## Dependency vulnerability scanning

Run `./gradlew --no-daemon dependencyCheckAnalyze` to scan resolved production
runtime dependencies, including JDA and its transitive dependencies. The
Spigot API remains `compileOnly` and is not included in the runtime scan or
shaded plugin. OWASP Dependency-Check writes HTML and JSON reports to
`build/reports/dependency-check/`, including CVE identifiers, affected
dependency versions, CVSS severity, and vulnerability descriptions/references
that provide remediation context where available. Review the referenced
advisory for the fixed version before changing dependencies; updates are not
automatic. The task fails the build when a finding has CVSS 7.0 or higher.

The GitHub Actions verification workflow runs this scan only when the
nonempty `NVD_API_KEY` Actions secret is available, and retains generated
reports as an artifact even if that scan fails. When the secret is unavailable
(including forked pull requests), CI explicitly skips the scan and warns that
dependency vulnerability status remains unverified; the workflow's build/test
job still runs. Locally, export the same variable before running the scan.
Never commit or print the key. The first scan downloads vulnerability data and
may take several minutes; network outages or NVD rate limits can prevent
completion. A skipped or failed scan is not a passed vulnerability assessment.

The scan also updates CISA's Known Exploited Vulnerabilities (KEV) catalog by
default. If a restricted network blocks that auxiliary feed, the scan can be
run with `-PdependencyCheckKevEnabled=false`; this keeps NVD CVE analysis
enabled but omits CISA known-exploited enrichment, reducing prioritization
coverage. Re-enable the default or configure an approved KEV mirror when
available. Do not treat a scan with KEV disabled as having full CISA KEV
coverage.

## Discord setup and configuration

1. Create a Discord bot and invite it to the server containing the target
   channel.
2. Grant the bot **View Channel** and **Send Messages** in that channel. No
   additional Discord permissions are required by the documented relay
   features.
3. Enable the **Message Content Intent** for the bot in the Discord Developer
   Portal. MineBridge enables the `GUILD_MESSAGES` and privileged
   `MESSAGE_CONTENT` gateway intents; it does not require member presences or a
   populated member cache.
4. Configure the channel ID and bot token as described below, then start or
   reload MineBridge.

Set `discord.channel-id` in `plugins/MineBridge/config.yml` to the 17-20 digit
ID of the target Discord text channel. Enable Developer Mode in Discord to copy
the channel ID.

Prefer setting the bot token in the server process environment as
`MINEBRIDGE_BOT_TOKEN`. A non-blank environment value takes precedence over
`discord.bot-token`; the config token remains supported for existing
installations. Do not put a real token in source control, paste it into chat,
or include it in logs. The shipped config intentionally has an empty token.

The config contains the Discord token and channel ID, six message templates,
and feature toggles for relays and the server-information commands. Templates
must be non-empty, no longer than 2000 characters, and use only balanced,
supported placeholders:

| Setting | Default | Description |
| --- | --- | --- |
| `discord.bot-token` | `""` | Bot token; use `MINEBRIDGE_BOT_TOKEN` instead when possible. |
| `discord.channel-id` | `""` | Required 17-20 digit ID of the Discord text channel. |
| `features.chat-relay` | `true` | Enable Minecraft and Discord chat relay. |
| `features.join-leave-notifications` | `true` | Enable player join/leave notifications. |
| `features.server-status` | `true` | Enable `/minebridge server`. |
| `features.player-list` | `true` | Enable `/minebridge players`. |

| Template | Default | Supported placeholders |
| --- | --- | --- |
| `messages.minecraft-to-discord` | `**{player}**: {message}` | `{player}`, `{message}`, `{timestamp}` |
| `messages.discord-to-minecraft` | `&9[Discord] &f{player}: {message}` | `{player}`, `{display_name}`, `{username}`, `{message}`, `{timestamp}` |
| `messages.player-join` | `🟢 **{player}** joined the server.` | `{player}`, `{timestamp}` |
| `messages.player-leave` | `🔴 **{player}** left the server.` | `{player}`, `{timestamp}` |
| `messages.server-status` | `&7Server &8\| &a{online}&7/&f{max} players online` | `{online}`, `{max}` |
| `messages.player-list` | `&7Online players (&f{online}&7/&f{max}&7): {players}` | `{online}`, `{max}`, `{players}` |

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

Inbound queue capacity, per-tick batch size, and rate limits currently use
built-in defaults rather than configuration options: the queue holds 100
messages, at most 10 are processed per tick, each Discord user has a burst of
3 messages replenished over 5 seconds, and all users share a burst of 10
messages replenished over 1 second.

## Administration and troubleshooting

- The root command is `/minebridge` (alias `/mb`); running it without a
  subcommand is the same as `/minebridge help`.

| Command | Permission and behavior |
| --- | --- |
| `/minebridge help` | Lists available commands; no additional permission required. |
| `/minebridge status` | Shows Discord connection and outgoing-queue status; no additional permission required. |
| `/minebridge server` | No additional permission; shows online count and capacity when `features.server-status` is `true` (default). |
| `/minebridge players` | No additional permission; shows online count and names when `features.player-list` is `true` (default). |
| `/minebridge reload` | Reloads configuration and replaces the Discord connection after readiness; requires `minebridge.admin` (default: operators). |

- `features.server-status: true` and `features.player-list: true` independently
  enable these commands by default. Disabled commands are omitted from
  MineBridge help and tab completion and report that they are disabled if
  invoked directly.
- The existing `features.chat-relay: true` and
  `features.join-leave-notifications: true` defaults remain unchanged.
- The server-information message templates are color-code-aware and support
  the placeholders listed above. The player list joins names in the order
  returned by the server API, separated by commas; names are display text only.
- `/minebridge status` reports connection state, cached channel availability,
  outgoing queue size/capacity, plugin uptime, and the chat-relay and
  join/leave-notification toggles. It does not make a REST request to check
  channel availability.
- Help and tab completion hide `/minebridge reload` from users without
  `minebridge.admin`.
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

Incoming Discord text is sanitized, and empty or over-2000-character messages
are ignored. Only messages from the configured guild channel are relayed; bot
and webhook messages are filtered out.

API-rejected messages are not retried, to avoid duplicate delivery; therefore a
network failure after JDA accepts a request can lose that message. Messages
remain FIFO while queued, and only one Discord send is in flight at a time.
The in-memory queue is discarded on plugin shutdown. A message accepted by
Discord but whose success callback is interrupted by shutdown has an
indeterminate delivery result.

Discord-to-Minecraft messages use a separate bounded FIFO queue with capacity
100 and are drained on the Minecraft thread in batches of at most 10 per tick.
When the queue is full, the newest message is dropped and a rate-limited warning
is logged. Inbound relay also limits each Discord user to a burst of 3 messages,
replenishing to full over 5 seconds, and all users together to a burst of 10
messages, replenishing to full over 1 second. Rate-limited messages are silently
discarded. Pending inbound messages are discarded when the plugin shuts down.
