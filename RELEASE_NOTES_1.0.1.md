# MineBridge 1.0.1 (Draft)

This draft summarizes the stabilization changes intended for MineBridge 1.0.1.
The project version has not yet been changed.

## Reliability improvements

- Track ownership of in-flight Discord sends so a callback from a replaced
  client cannot release a newer send's gate.
- Process Discord-to-Minecraft messages through a bounded FIFO queue, with a
  maximum of 10 messages handled per tick.
- Drop the newest inbound message when the queue is full and limit overflow
  diagnostics to avoid repeated log noise.
- Discard pending inbound messages and reject new submissions during shutdown.
- Ignore empty and whitespace-only Minecraft chat messages when relaying to
  Discord.

## Inbound rate limits

- Limit each Discord user to a burst of 3 messages, replenished over 5 seconds.
- Limit all users together to a burst of 10 messages, replenished over
  1 second. Excess messages are discarded without per-message logging.

## Tests

- Add regression and concurrency tests for outbound delivery, reload behavior,
  inbound queue processing, rate limiting, shutdown, and blank Minecraft chat.

## Verification status

- Dependency-Check was skipped because `NVD_API_KEY` was unavailable; dependency
  vulnerability status remains unverified.
- Paper compatibility has not been certified.
