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
import org.bukkit.plugin.IllegalPluginAccessException;

import java.util.List;
import java.util.function.Consumer;

public final class DiscordService {

    private static final int OUTGOING_QUEUE_CAPACITY = 100;
    private static final long DIAGNOSTIC_INTERVAL_NANOS = 30_000_000_000L;
    private static final long CONNECTION_TIMEOUT_NANOS = 45_000_000_000L;

    private final MineBridgePlugin plugin;
    private final ConfigManager config;
    private final BlockingMessageQueue outgoingQueue =
            new BlockingMessageQueue(OUTGOING_QUEUE_CAPACITY);
    private final Object lifecycleLock = new Object();
    private final ReloadRequestGate reloadRequests = new ReloadRequestGate();
    private final ActiveClientSnapshot<JDA> incomingClient = new ActiveClientSnapshot<>();
    private final OutboundSendGate<JDA> outboundSendGate = new OutboundSendGate<>();
    private final InboundMessageProcessor<InboundMessageProcessor.ChatMessage> inboundProcessor;
    private final RateLimitedDiagnostics diagnostics =
            new RateLimitedDiagnostics(DIAGNOSTIC_INTERVAL_NANOS);

    private volatile JDA jda;
    private volatile JDA pendingJda;
    private volatile boolean stopping;
    private volatile String lastConnectionState = "NOT_STARTED";
    private boolean starting;
    private long connectionDeadlineNanos;
    private boolean connectionFailureReported;
    private boolean hasConnected;
    private boolean latestReloadInProgress;
    private ConfigManager.Settings pendingSettings;
    private long pendingRequestId;
    private Consumer<ReloadResult> startupCallback;
    private Consumer<ReloadResult> pendingReload;

    public DiscordService(MineBridgePlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.inboundProcessor = new InboundMessageProcessor<>(
                InboundMessageProcessor.DEFAULT_CAPACITY,
                InboundMessageProcessor.DEFAULT_BATCH_SIZE,
                new InboundRateLimiter(
                        InboundMessageProcessor.DEFAULT_PER_USER_BURST,
                        InboundMessageProcessor.DEFAULT_PER_USER_REFILL_NANOS,
                        InboundMessageProcessor.DEFAULT_GLOBAL_BURST,
                        InboundMessageProcessor.DEFAULT_GLOBAL_REFILL_NANOS,
                        System::nanoTime),
                task -> {
                    try {
                        plugin.getServer().getScheduler().runTask(plugin, task);
                        return true;
                    } catch (IllegalPluginAccessException exception) {
                        return false;
                    }
                },
                this::broadcastIncoming);
    }

    public boolean start() {
        return start(null);
    }

    public void markConfigurationInvalid() {
        synchronized (lifecycleLock) {
            if (jda == null) {
                lastConnectionState = "INVALID_CONFIGURATION";
            }
        }
    }

    public long beginReloadRequest() {
        synchronized (lifecycleLock) {
            long requestId = reloadRequests.nextRequest();
            latestReloadInProgress = true;
            if (jda == null) {
                lastConnectionState = "CONNECTING";
            }
            return requestId;
        }
    }

    public void finishReloadRequest(long requestId, String failureState) {
        synchronized (lifecycleLock) {
            if (!reloadRequests.isCurrent(requestId)) {
                return;
            }
            latestReloadInProgress = false;
            if (jda == null && pendingJda == null && !stopping) {
                lastConnectionState = failureState;
            }
        }
    }

    public boolean start(Consumer<ReloadResult> callback) {
        ConfigManager.Settings settings = config.currentSettings();
        List<String> errors = ConfigManager.validateConfiguration(
                settings.botToken(), settings.channelId());
        if (!errors.isEmpty()) {
            reportConfigurationErrors(errors);
            complete(callback, new ReloadResult(false, String.join(" ", errors)));
            return false;
        }

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
                        startupCallback = callback;
                        setConnectionDeadline();
                    }
                }
                return true;
            }
            if (starting) {
                complete(callback, new ReloadResult(false, "Discord startup is already in progress."));
                return true;
            }

            starting = true;
            lastConnectionState = "CONNECTING";
            hasConnected = false;
            setConnectionDeadline();
        }

        JDA created;
        try {
            created = createClient(settings);
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

        boolean staleStartup;
        synchronized (lifecycleLock) {
            starting = false;
            staleStartup = stopping || jda != null;
            if (!staleStartup) {
                jda = created;
                incomingClient.activate(created, settings);
                startupCallback = callback;
            }
        }
        if (staleStartup) {
            created.shutdownNow();
            complete(callback, new ReloadResult(false, stopping
                    ? "MineBridge is shutting down."
                    : "Discord startup was superseded by another client."));
            return false;
        }
        plugin.getLogger().info(
                "Discord client started; waiting for the gateway connection.");
        if (created.getStatus() == JDA.Status.CONNECTED) {
            onGatewayReady(created);
        }
        return true;
    }

    public void reload(
            long requestId,
            ConfigManager.Candidate candidate,
            Consumer<ReloadResult> callback) {
        if (!candidate.valid()) {
            finishReloadRequest(requestId, "INVALID_CONFIGURATION");
            complete(callback, new ReloadResult(
                    false, String.join(" ", candidate.errors())));
            return;
        }

        JDA previousPending = null;
        Consumer<ReloadResult> superseded = null;
        boolean requestStale;
        synchronized (lifecycleLock) {
            requestStale = stopping || !reloadRequests.isCurrent(requestId);
            if (!requestStale) {
                previousPending = pendingJda;
                superseded = pendingReload;
                pendingJda = null;
                pendingSettings = null;
                pendingReload = null;
            }
        }
        if (previousPending != null) {
            previousPending.shutdownNow();
        }
        complete(superseded, new ReloadResult(false, "A newer reload replaced this request."));
        if (requestStale) {
            complete(callback, new ReloadResult(false,
                    "This reload was superseded or MineBridge is shutting down."));
            return;
        }

        JDA created;
        try {
            created = createClient(candidate.settings());
        } catch (IllegalArgumentException exception) {
            finishReloadRequest(requestId, "FAILED");
            complete(callback, new ReloadResult(false,
                    "Discord rejected the candidate configuration. Verify the token "
                            + "and gateway intents; no token was logged."));
            return;
        } catch (RuntimeException exception) {
            finishReloadRequest(requestId, "FAILED");
            complete(callback, new ReloadResult(false,
                    "Could not start the replacement Discord client ("
                            + exception.getClass().getSimpleName()
                            + "). The previous connection and configuration remain active."));
            return;
        }

        boolean staleAfterCreate;
        synchronized (lifecycleLock) {
            staleAfterCreate = stopping || !reloadRequests.isCurrent(requestId);
            if (!staleAfterCreate) {
                pendingJda = created;
                pendingSettings = candidate.settings();
                pendingRequestId = requestId;
                pendingReload = callback;
                if (jda == null) {
                    lastConnectionState = "CONNECTING";
                }
                connectionDeadlineNanos =
                        System.nanoTime() + CONNECTION_TIMEOUT_NANOS;
            }
        }
        if (staleAfterCreate) {
            created.shutdownNow();
            finishReloadRequest(requestId, "DISCONNECTED");
            complete(callback, new ReloadResult(false,
                    "This reload was superseded or MineBridge is shutting down."));
            return;
        }
        plugin.getLogger().info(
                "Replacement Discord client started; waiting for the gateway connection.");
        if (created.getStatus() == JDA.Status.CONNECTED) {
            onGatewayReady(created);
        }
    }

    public MineBridgeStatus getStatus(long uptimeMillis) {
        synchronized (lifecycleLock) {
            JDA current = jda;
            String state = current == null
                    ? lastConnectionState : current.getStatus().name();
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
    }

    static String stateAfterPendingDiscard(
            String activeState, String newerPendingState, String fallbackState) {
        if (newerPendingState != null) {
            return newerPendingState;
        }
        if (activeState != null) {
            return activeState;
        }
        return "CONNECTING".equals(fallbackState) ? "DISCONNECTED" : fallbackState;
    }

    public void send(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        if (!RelayMessageFormatter.isWithinDiscordLimit(message)) {
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
        if (stopping) {
            return;
        }

        JDA current;
        TextChannel channel;
        String message;
        long sendId;
        synchronized (lifecycleLock) {
            if (stopping) {
                return;
            }
            current = jda;
            if (current == null || current.getStatus() != JDA.Status.CONNECTED) {
                return;
            }

            channel = current.getTextChannelById(config.getChannelId());
            if (channel == null) {
                warnRateLimited(
                        "channel-unavailable",
                        "The configured Discord channel is unavailable in JDA's cache; queued messages are retained.");
                return;
            }

            sendId = outboundSendGate.tryBegin(current);
            if (sendId == 0) {
                return;
            }
            message = outgoingQueue.poll();
            if (message == null) {
                outboundSendGate.finish(sendId);
                return;
            }
        }

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

    public boolean isConnected() {
        JDA current = jda;
        return current != null && current.getStatus() == JDA.Status.CONNECTED;
    }

    public ConfigManager.Settings captureIncomingSettings(JDA source) {
        return incomingClient.capture(source);
    }

    public void receiveIncoming(
            String userId, InboundMessageProcessor.ChatMessage message) {
        if (stopping) {
            return;
        }
        InboundMessageProcessor.SubmissionResult result =
                inboundProcessor.submit(userId, message);
        if (result == InboundMessageProcessor.SubmissionResult.QUEUE_FULL) {
            warnRateLimited(
                    "inbound-dropped",
                    "The inbound Discord queue is full; a new message was dropped.");
        } else if (result == InboundMessageProcessor.SubmissionResult.SCHEDULER_UNAVAILABLE) {
            warnRateLimited(
                    "inbound-scheduler-unavailable",
                    "Could not schedule inbound Discord messages; the inbound queue was closed.");
        }
    }

    public void stop() {
        JDA previous;
        JDA previousPending;
        JDA retiring;
        Consumer<ReloadResult> pending;
        Consumer<ReloadResult> starting;
        synchronized (lifecycleLock) {
            stopping = true;
            this.starting = false;
            previous = jda;
            previousPending = pendingJda;
            jda = null;
            pendingJda = null;
            pendingSettings = null;
            reloadRequests.nextRequest();
            latestReloadInProgress = false;
            incomingClient.clear();
            pending = pendingReload;
            pendingReload = null;
            starting = startupCallback;
            startupCallback = null;
            lastConnectionState = "SHUTDOWN";
            retiring = outboundSendGate.close();
            inboundProcessor.close();
        }
        outgoingQueue.close();
        if (previous != null) {
            previous.shutdownNow();
        }
        if (previousPending != null) {
            previousPending.shutdownNow();
        }
        if (retiring != null) {
            retiring.shutdownNow();
        }
        complete(pending, new ReloadResult(false, "MineBridge shut down before Discord connected."));
        complete(starting, new ReloadResult(false, "MineBridge shut down before Discord connected."));
    }

    public void onGatewayReady(JDA source) {
        Consumer<ReloadResult> callback;
        Consumer<ReloadResult> replacedStartupCallback = null;
        JDA previous = null;
        JDA previousToShutdown = null;
        JDA discarded = null;
        boolean stalePending = false;
        synchronized (lifecycleLock) {
            if (stopping) {
                return;
            }
            if (source == pendingJda) {
                ConfigManager.Settings activatedSettings = pendingSettings;
                previous = jda;
                boolean activated = reloadRequests.runIfCurrent(pendingRequestId, () -> {
                    config.commit(new ConfigManager.Candidate(activatedSettings, List.of()));
                    jda = source;
                    incomingClient.activate(source, activatedSettings);
                });
                if (!activated) {
                    discarded = pendingJda;
                    pendingJda = null;
                    pendingSettings = null;
                    latestReloadInProgress = false;
                    callback = pendingReload;
                    pendingReload = null;
                    stalePending = true;
                } else {
                    pendingJda = null;
                    pendingSettings = null;
                    latestReloadInProgress = false;
                    if (previous != null) {
                        previousToShutdown = outboundSendGate.clientReplaced(previous);
                    }
                    lastConnectionState = "CONNECTED";
                    hasConnected = true;
                    connectionFailureReported = false;
                    callback = pendingReload;
                    pendingReload = null;
                    replacedStartupCallback = startupCallback;
                    startupCallback = null;
                }
            } else if (source == jda) {
                lastConnectionState = "CONNECTED";
                hasConnected = true;
                connectionFailureReported = false;
                callback = startupCallback;
                startupCallback = null;
            } else {
                return;
            }
        }
        if (discarded != null) {
            discarded.shutdownNow();
        }
        if (!stalePending && previousToShutdown != null) {
            previousToShutdown.shutdownNow();
        }
        if (stalePending) {
            complete(callback, new ReloadResult(false,
                    "A newer reload request superseded this candidate."));
            return;
        }
        plugin.getLogger().info("Discord gateway connected.");
        ReloadResult result = new ReloadResult(true, "Discord connection established.");
        complete(callback, result);
        complete(replacedStartupCallback, result);
    }

    public void onGatewayDisconnect(JDA source) {
        if (source == jda || source == pendingJda) {
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
        JDA failedPending = null;
        synchronized (lifecycleLock) {
            if (stopping) {
                return;
            }
            JDA current = pendingJda != null ? pendingJda : jda;
            boolean checkingReplacement = pendingJda != null;
            if (current == null || (connectionFailureReported && !checkingReplacement)) {
                return;
            }

            boolean staleCandidate = checkingReplacement
                    && !reloadRequests.isCurrent(pendingRequestId);
            if (staleCandidate) {
                failedPending = pendingJda;
                pendingJda = null;
                pendingSettings = null;
                callback = pendingReload;
                pendingReload = null;
                JDA active = jda;
                String activeState = active == null ? null : active.getStatus().name();
                String newerPendingState = latestReloadInProgress ? "CONNECTING" : null;
                lastConnectionState = stateAfterPendingDiscard(
                        activeState, newerPendingState, lastConnectionState);
                message = "A newer reload request superseded this candidate.";
            }

            if (!staleCandidate) {
                JDA.Status status = current.getStatus();
                if (status == JDA.Status.FAILED_TO_LOGIN) {
                    message = "Discord login failed. Verify the bot token and required gateway intents.";
                } else if (status == JDA.Status.SHUTDOWN
                        || status == JDA.Status.SHUTTING_DOWN) {
                    message = "Discord client shut down before the connection became ready. "
                            + "Check the server log for gateway and network errors.";
                } else if ((checkingReplacement || !hasConnected)
                        && System.nanoTime() >= connectionDeadlineNanos
                        && status != JDA.Status.CONNECTED) {
                    message = "Discord connection was not established within 45 seconds "
                            + "(current state: " + status.name() + "). Check network access, "
                            + "bot token, and gateway intents.";
                }

                if (message != null) {
                    if (checkingReplacement) {
                        failedPending = pendingJda;
                        pendingJda = null;
                        pendingSettings = null;
                        latestReloadInProgress = false;
                        if (jda == null) {
                            lastConnectionState = status == JDA.Status.FAILED_TO_LOGIN
                                    ? "FAILED_TO_LOGIN" : status.name();
                        }
                    } else {
                        lastConnectionState = status == JDA.Status.FAILED_TO_LOGIN
                                ? "FAILED_TO_LOGIN" : status.name();
                        connectionFailureReported = true;
                    }
                    callback = checkingReplacement ? pendingReload : startupCallback;
                    if (checkingReplacement) {
                        latestReloadInProgress = false;
                    }
                    if (checkingReplacement) {
                        pendingReload = null;
                    } else {
                        startupCallback = null;
                    }
                }
            }
        }

        if (message != null) {
            if (failedPending != null) {
                failedPending.shutdownNow();
            }
            warnRateLimited("connection-failure", message);
            complete(callback, new ReloadResult(false, message));
        }
    }

    private JDA createClient(ConfigManager.Settings settings) {
        return JDABuilder.createDefault(settings.botToken())
                .enableIntents(
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.MESSAGE_CONTENT)
                .setMemberCachePolicy(MemberCachePolicy.NONE)
                .setActivity(Activity.playing("Minecraft"))
                .addEventListeners(
                        new DiscordMessageListener(this),
                        new DiscordGatewayListener(this))
                .build();
    }

    private boolean startupFailed(Consumer<ReloadResult> callback, String message) {
        synchronized (lifecycleLock) {
            starting = false;
            if (!stopping && jda == null && pendingJda == null) {
                lastConnectionState = "FAILED";
                connectionFailureReported = true;
            }
        }
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
        JDA retired = outboundSendGate.finish(sendId);
        if (retired != null) {
            retired.shutdownNow();
        }
    }

    private void broadcastIncoming(InboundMessageProcessor.ChatMessage message) {
        if (!plugin.isEnabled()) {
            return;
        }
        String formatted = RelayMessageFormatter.formatForMinecraft(
                config.color(message.template()),
                message.player(),
                message.displayName(),
                message.username(),
                message.content(),
                message.timestamp());
        if (plugin.isEnabled()) {
            plugin.getServer().broadcastMessage(formatted);
        }
    }

    private void warnRateLimited(String category, String message) {
        if (diagnostics.shouldEmit(category)) {
            plugin.getLogger().warning(message);
        }
    }
}
