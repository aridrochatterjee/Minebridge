package com.minebridge.discord;

import com.minebridge.MineBridgePlugin;
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

public final class DiscordService {

    private static final int OUTGOING_QUEUE_CAPACITY = 100;
    private static final int DISCORD_MESSAGE_LIMIT = 2000;
    private static final long WARNING_INTERVAL_NANOS = 30_000_000_000L;

    private final MineBridgePlugin plugin;
    private final ConfigManager config;
    private final BlockingMessageQueue outgoingQueue =
            new BlockingMessageQueue(OUTGOING_QUEUE_CAPACITY);
    private final Object lifecycleLock = new Object();
    private final AtomicBoolean starting = new AtomicBoolean();
    private final AtomicBoolean sending = new AtomicBoolean();
    private final AtomicLong lifecycleGeneration = new AtomicLong();
    private final AtomicLong sendSequence = new AtomicLong();
    private final AtomicLong nextWarningNanos = new AtomicLong();

    private volatile JDA jda;
    private volatile boolean stopping;
    private long activeSend;

    public DiscordService(MineBridgePlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    public boolean start() {
        if (stopping || !starting.compareAndSet(false, true)) {
            return false;
        }

        long generation = lifecycleGeneration.get();
        try {
            if (jda != null) {
                return true;
            }

            String token = config.getBotToken();
            String channelId = config.getChannelId();
            if (!ConfigManager.isValidBotToken(token)) {
                plugin.getLogger().severe(
                        "Discord token is missing or invalid. Set MINEBRIDGE_BOT_TOKEN "
                                + "or discord.bot-token; the token was not logged.");
                return false;
            }
            if (!ConfigManager.isValidChannelId(channelId)) {
                plugin.getLogger().severe(
                        "Discord channel ID is missing or invalid. Set discord.channel-id "
                                + "to the configured text channel's 17-20 digit ID.");
                return false;
            }

            JDA created = JDABuilder.createDefault(token)
                    .enableIntents(
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.MESSAGE_CONTENT)
                    .setMemberCachePolicy(MemberCachePolicy.NONE)
                    .setActivity(Activity.playing("Minecraft"))
                    .addEventListeners(
                            new DiscordMessageListener(plugin, config),
                            new DiscordGatewayListener(this))
                    .build();

            synchronized (lifecycleLock) {
                if (stopping || generation != lifecycleGeneration.get()) {
                    created.shutdownNow();
                    return false;
                }
                if (jda != null) {
                    created.shutdownNow();
                    return true;
                }
                jda = created;
            }

            plugin.getLogger().info(
                    "Discord client started; waiting for the gateway connection.");
            return true;
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().severe(
                    "Discord rejected the configured token or client settings. "
                            + "Verify the token and enabled gateway intents; no token was logged.");
            return false;
        } catch (RuntimeException exception) {
            plugin.getLogger().severe(
                    "Discord startup failed (" + exception.getClass().getSimpleName()
                            + "). Check network access and bot configuration; no token was logged.");
            return false;
        } finally {
            starting.set(false);
            if (!stopping && generation != lifecycleGeneration.get()) {
                start();
            }
        }
    }

    public void send(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        if (message.length() > DISCORD_MESSAGE_LIMIT) {
            warnRateLimited(
                    "An outgoing message exceeded Discord's 2000-character limit and was dropped.");
            return;
        }
        if (!outgoingQueue.offer(message)) {
            warnRateLimited(
                    "The outgoing Discord queue is full or closed; a new message was dropped.");
        }
    }

    public void processOutgoing() {
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
                        "The configured Discord text channel is unavailable; queued messages are being retained.");
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
                                    "Discord rejected an outgoing message; it was dropped.");
                            finishSend(sendId);
                        });
            } catch (RuntimeException exception) {
                warnRateLimited(
                        "Could not submit an outgoing message to Discord; it was dropped.");
                finishSend(sendId);
            }
        }
    }

    public boolean isConnected() {
        JDA current = jda;
        return current != null && current.getStatus() == JDA.Status.CONNECTED;
    }

    public void reload() {
        if (stopping) {
            return;
        }

        lifecycleGeneration.incrementAndGet();
        JDA previous;
        synchronized (lifecycleLock) {
            previous = jda;
            jda = null;
            activeSend = sendSequence.incrementAndGet();
            sending.set(false);
        }
        if (previous != null) {
            previous.shutdownNow();
        }
        start();
    }

    public void stop() {
        stopping = true;
        lifecycleGeneration.incrementAndGet();

        JDA previous;
        synchronized (lifecycleLock) {
            previous = jda;
            jda = null;
            activeSend = sendSequence.incrementAndGet();
            sending.set(false);
        }
        outgoingQueue.close();
        if (previous != null) {
            previous.shutdownNow();
        }
    }

    public void onGatewayReady(JDA source) {
        if (source == jda) {
            plugin.getLogger().info("Discord gateway connected.");
        }
    }

    public void onGatewayDisconnect(JDA source) {
        if (source == jda) {
            plugin.getLogger().warning(
                    "Discord gateway disconnected; JDA will attempt to resume or reconnect.");
        }
    }

    public void onGatewayResume(JDA source) {
        if (source == jda) {
            plugin.getLogger().info("Discord gateway session resumed.");
        }
    }

    private void finishSend(long sendId) {
        synchronized (lifecycleLock) {
            if (activeSend == sendId) {
                sending.set(false);
            }
        }
    }

    private void warnRateLimited(String message) {
        long now = System.nanoTime();
        long next = nextWarningNanos.get();
        if (now >= next && nextWarningNanos.compareAndSet(
                next, now + WARNING_INTERVAL_NANOS)) {
            plugin.getLogger().warning(message);
        }
    }
}
