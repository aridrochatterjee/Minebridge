package com.minebridge.discord;

import com.minebridge.MineBridgePlugin;
import com.minebridge.command.MineBridgeStatus;
import com.minebridge.config.ConfigManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class DiscordService {

    private static final int OUTGOING_QUEUE_CAPACITY = 100;
    private static final int DISCORD_MESSAGE_LIMIT = 2000;
    private static final long DIAGNOSTIC_INTERVAL_NANOS = 30_000_000_000L;
    private static final long CONNECTION_TIMEOUT_NANOS = 45_000_000_000L;

    private final MineBridgePlugin plugin;
    private final ConfigManager config;
    private final BlockingMessageQueue outgoingQueue =
            new BlockingMessageQueue(OUTGOING_QUEUE_CAPACITY);
    private final Object lifecycleLock = new Object();
    private final AtomicBoolean sending = new AtomicBoolean();
    private final AtomicLong sendSequence = new AtomicLong();
    private final RateLimitedDiagnostics diagnostics =
            new RateLimitedDiagnostics(DIAGNOSTIC_INTERVAL_NANOS);

    private volatile JDA jda;
    private volatile boolean stopping;
    private volatile String lastConnectionState = "NOT_STARTED";
    private long activeSend;
    private long connectionDeadlineNanos;
    private boolean connectionFailureReported;
    private boolean hasConnected;
    private Consumer<ReloadResult> pendingReload;

    public DiscordService(MineBridgePlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    public boolean start() {
        return start(null);
    }

    public boolean start(Consumer<ReloadResult> callback) {
        List<String> errors = config.validationErrors();
        if (!errors.isEmpty()) {
            reportConfigurationErrors(errors);
            complete(callback, new ReloadResult(false, String.join(" ", errors)));
            return false;
        }

        JDA created;
        synchronized (lifecycleLock) {
            if (stopping) {
                complete(callback, new ReloadResult(false, "MineBridge is shutting down."));
                return false;
            }
            if (jda != null) {
                if (callback != null) {
                    if (jda.getStatus() == JDA.Status.CONNECTED) {
                        complete(callback, new ReloadResult(
                                true, "Discord is already connected."));
                    } else {
                        pendingReload = callback;
                        setConnectionDeadline();
                    }
                }
                return true;
            }

            lastConnectionState = "CONNECTING";
            hasConnected = false;
            setConnectionDeadline();
            try {
                created = JDABuilder.createDefault(config.getBotToken())
                        .enableIntents(
                                GatewayIntent.GUILD_MESSAGES,
                                GatewayIntent.MESSAGE_CONTENT)
                        .setMemberCachePolicy(MemberCachePolicy.NONE)
                        .setActivity(Activity.playing("Minecraft"))
                        .addEventListeners(
                                new DiscordMessageListener(plugin, config),
                                new DiscordGatewayListener(this))
                        .build();
                jda = created;
                pendingReload = callback;
            } catch (IllegalArgumentException exception) {
                return startupFailed(
                        callback,
                        "Discord rejected the configured token or client settings. "
                                + "Verify the token and enabled gateway intents; no token was logged.");
            } catch (RuntimeException exception) {
                return startupFailed(
                        callback,
                        "Discord client startup failed (" + exception.getClass().getSimpleName()
                                + "). Check network access and bot configuration; no token was logged.");
            }
        }

        plugin.getLogger().info(
                "Discord client started; waiting for the gateway connection.");
        if (created.getStatus() == JDA.Status.CONNECTED) {
            onGatewayReady(created);
        }
        return true;
    }

    public void reload(Consumer<ReloadResult> callback) {
        List<String> errors = config.validationErrors();
        if (!errors.isEmpty()) {
            reportConfigurationErrors(errors);
            Consumer<ReloadResult> superseded;
            synchronized (lifecycleLock) {
                superseded = pendingReload;
                pendingReload = null;
            }
            complete(superseded, new ReloadResult(
                    false, "A newer reload was rejected because configuration is invalid."));
            complete(callback, new ReloadResult(false, String.join(" ", errors)));
            return;
        }

        JDA previous;
        Consumer<ReloadResult> superseded;
        synchronized (lifecycleLock) {
            if (stopping) {
                complete(callback, new ReloadResult(false, "MineBridge is shutting down."));
                return;
            }
            previous = jda;
            jda = null;
            superseded = pendingReload;
            pendingReload = null;
            activeSend = sendSequence.incrementAndGet();
            sending.set(false);
            lastConnectionState = "CONNECTING";
            hasConnected = false;
        }
        if (previous != null) {
            previous.shutdownNow();
        }
        complete(superseded, new ReloadResult(false, "A newer reload replaced this request."));
        start(callback);
    }

    public MineBridgeStatus getStatus(long uptimeMillis) {
        JDA current = jda;
        String state = current == null ? lastConnectionState : current.getStatus().name();
        boolean channelAvailable = current != null
                && current.getStatus() == JDA.Status.CONNECTED
                && current.getTextChannelById(config.getChannelId()) != null;
        return new MineBridgeStatus(
                state,
                channelAvailable,
                outgoingQueue.size(),
                outgoingQueue.capacity(),
                uptimeMillis,
                config.chatEnabled(),
                config.eventsEnabled());
    }

    public void send(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        if (message.length() > DISCORD_MESSAGE_LIMIT) {
            warnRateLimited(
                    "outgoing-too-long",
                    "An outgoing message exceeded Discord's 2000-character limit and was dropped.");
            return;
        }
        if (!outgoingQueue.offer(message)) {
            warnRateLimited(
                    "outgoing-dropped",
                    "The outgoing Discord queue is full or closed; a new message was dropped.");
        }
    }

    public void processOutgoing() {
        checkConnectionHealth();
        if (stopping || !sending.compareAndSet(false, true)) {
            return;
        }

        synchronized (lifecycleLock) {
            JDA current = jda;
            if (current == null || current.getStatus() != JDA.Status.CONNECTED) {
                sending.set(false);
                return;
            }

            TextChannel channel = current.getTextChannelById(config.getChannelId());
            if (channel == null) {
                sending.set(false);
                warnRateLimited(
                        "channel-unavailable",
                        "The configured Discord channel is unavailable in JDA's cache; queued messages are retained.");
                return;
            }

            String message = outgoingQueue.poll();
            if (message == null) {
                sending.set(false);
                return;
            }

            long sendId = sendSequence.incrementAndGet();
            activeSend = sendId;
            try {
                channel.sendMessage(message)
                        .setAllowedMentions(List.of())
                        .queue(
                                ignored -> finishSend(sendId),
                                error -> {
                                    warnRateLimited(
                                            "outgoing-api-rejected",
                                            "Discord rejected an outgoing message; it was dropped.");
                                    finishSend(sendId);
                                });
            } catch (RuntimeException exception) {
                warnRateLimited(
                        "outgoing-submit-failed",
                        "Could not submit an outgoing message to Discord; it was dropped.");
                finishSend(sendId);
            }
        }
    }

    public boolean isConnected() {
        JDA current = jda;
        return current != null && current.getStatus() == JDA.Status.CONNECTED;
    }

    public void stop() {
        stopping = true;
        JDA previous;
        Consumer<ReloadResult> pending;
        synchronized (lifecycleLock) {
            previous = jda;
            jda = null;
            pending = pendingReload;
            pendingReload = null;
            lastConnectionState = "SHUTDOWN";
            activeSend = sendSequence.incrementAndGet();
            sending.set(false);
        }
        outgoingQueue.close();
        if (previous != null) {
            previous.shutdownNow();
        }
        complete(pending, new ReloadResult(false, "MineBridge shut down before Discord connected."));
    }

    public void onGatewayReady(JDA source) {
        Consumer<ReloadResult> callback;
        synchronized (lifecycleLock) {
            if (source != jda) {
                return;
            }
            lastConnectionState = "CONNECTED";
            hasConnected = true;
            connectionFailureReported = false;
            callback = pendingReload;
            pendingReload = null;
        }
        plugin.getLogger().info("Discord gateway connected.");
        complete(callback, new ReloadResult(true, "Discord connection established."));
    }

    public void onGatewayDisconnect(JDA source) {
        if (source == jda) {
            warnRateLimited(
                    "gateway-disconnected",
                    "Discord gateway disconnected; JDA will attempt to resume or reconnect.");
        }
    }

    public void onGatewayResume(JDA source) {
        if (source == jda) {
            lastConnectionState = "CONNECTED";
            hasConnected = true;
            plugin.getLogger().info("Discord gateway session resumed.");
        }
    }

    private void checkConnectionHealth() {
        Consumer<ReloadResult> callback = null;
        String message = null;
        synchronized (lifecycleLock) {
            JDA current = jda;
            if (current == null || connectionFailureReported) {
                return;
            }

            JDA.Status status = current.getStatus();
            if (status == JDA.Status.FAILED_TO_LOGIN) {
                message = "Discord login failed. Verify the bot token and required gateway intents.";
            } else if (status == JDA.Status.SHUTDOWN
                    || status == JDA.Status.SHUTTING_DOWN) {
                message = "Discord client shut down before the connection became ready. "
                        + "Check the server log for gateway and network errors.";
            } else if (!hasConnected
                    && System.nanoTime() >= connectionDeadlineNanos
                    && status != JDA.Status.CONNECTED) {
                message = "Discord connection was not established within 45 seconds "
                        + "(current state: " + status.name() + "). Check network access, "
                        + "bot token, and gateway intents.";
            }

            if (message != null) {
                lastConnectionState = status == JDA.Status.FAILED_TO_LOGIN
                        ? "FAILED_TO_LOGIN" : status.name();
                connectionFailureReported = true;
                callback = pendingReload;
                pendingReload = null;
            }
        }

        if (message != null) {
            warnRateLimited("connection-failure", message);
            complete(callback, new ReloadResult(false, message));
        }
    }

    private boolean startupFailed(Consumer<ReloadResult> callback, String message) {
        jda = null;
        lastConnectionState = "FAILED";
        connectionFailureReported = true;
        pendingReload = null;
        warnRateLimited("connection-failure", message);
        complete(callback, new ReloadResult(false, message));
        return false;
    }

    private void reportConfigurationErrors(List<String> errors) {
        synchronized (lifecycleLock) {
            if (jda == null) {
                lastConnectionState = "INVALID_CONFIGURATION";
            }
        }
        for (String error : errors) {
            warnRateLimited("configuration-" + errors.indexOf(error), error);
        }
    }

    private void setConnectionDeadline() {
        connectionDeadlineNanos = System.nanoTime() + CONNECTION_TIMEOUT_NANOS;
        connectionFailureReported = false;
    }

    private void complete(Consumer<ReloadResult> callback, ReloadResult result) {
        if (callback != null) {
            callback.accept(result);
        }
    }

    private void finishSend(long sendId) {
        synchronized (lifecycleLock) {
            if (activeSend == sendId) {
                sending.set(false);
            }
        }
    }

    private void warnRateLimited(String category, String message) {
        if (diagnostics.shouldEmit(category)) {
            plugin.getLogger().warning(message);
        }
    }
}
