# MineBridge

MineBridge relays Minecraft chat and player join/leave notifications to a
configured Discord text channel, and relays human messages from that channel
back to Minecraft.

## Configuration

Set `discord.channel-id` in `config.yml` to the 17-20 digit ID of the Discord
text channel. Prefer setting the bot token in the server process environment as
`MINEBRIDGE_BOT_TOKEN`; `discord.bot-token` remains supported for existing
installations. Do not put a real token in source control or share startup logs
containing credentials. The shipped config intentionally has an empty token.

The bot needs access to the configured channel and the `GUILD_MESSAGES` and
`MESSAGE_CONTENT` gateway intents. The message content intent must also be
enabled for the bot in the Discord developer settings.

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

Connection, channel-availability, and outgoing-drop diagnostics are rate
limited per category. Diagnostics contain actionable configuration/network
guidance and do not include token values.

Outgoing messages use a bounded queue of 100 messages. Messages are retained
while disconnected or while the configured channel cannot be resolved from
JDA's cache. If the queue is full, a new message is dropped and a rate-limited
warning is logged. Messages longer than Discord's 2000-character limit are
dropped. API-rejected messages are not retried, to avoid duplicate delivery;
therefore a network failure at that point can lose that message. Outgoing
messages also disable Discord mentions. The queue is in-memory only and is
discarded on plugin shutdown.

## Build and verification

Build the distributable Shadow JAR with:

```sh
./gradlew clean test build
```

The project targets Java 21 bytecode. Successful compilation does not alone
establish compatibility with a particular Paper release; test the resulting
JAR on each server release before declaring it supported.
