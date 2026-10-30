package com.minebridge.config;

import com.minebridge.MineBridgePlugin;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ConfigManager {

    private static final List<String> MESSAGE_KEYS = List.of(
            "messages.minecraft-to-discord",
            "messages.discord-to-minecraft",
            "messages.player-join",
            "messages.player-leave",
            "messages.server-status",
            "messages.player-list");
    private static final Map<String, Set<String>> MESSAGE_PLACEHOLDERS = Map.of(
            "messages.minecraft-to-discord", Set.of("player", "message", "timestamp"),
            "messages.discord-to-minecraft",
            Set.of("player", "display_name", "username", "message", "timestamp"),
            "messages.player-join", Set.of("player", "timestamp"),
            "messages.player-leave", Set.of("player", "timestamp"),
            "messages.server-status", Set.of("online", "max"),
            "messages.player-list", Set.of("online", "max", "players"));
    private static final int MAX_TEMPLATE_LENGTH = 2000;
    private static final Map<String, String> SAFE_MESSAGES = Map.of(
            "messages.minecraft-to-discord", "**{player}**: {message}",
            "messages.discord-to-minecraft", "&9[Discord] &f{player}: {message}",
            "messages.player-join", "🟢 **{player}** joined the server.",
            "messages.player-leave", "🔴 **{player}** left the server.",
            "messages.server-status", "&7Server &8| &a{online}&7/&f{max} players online",
            "messages.player-list",
            "&7Online players (&f{online}&7/&f{max}&7): {players}");

    private final MineBridgePlugin plugin;
    private volatile Settings settings = new Settings(
            "", "", true, true, SAFE_MESSAGES);

    public record Settings(
            String botToken,
            String channelId,
            boolean chatEnabled,
            boolean eventsEnabled,
            Map<String, String> messages,
            boolean serverStatusEnabled,
            boolean playerListEnabled) {
        public Settings {
            messages = Map.copyOf(messages);
        }

        public Settings(
                String botToken,
                String channelId,
                boolean chatEnabled,
                boolean eventsEnabled,
                Map<String, String> messages) {
            this(botToken, channelId, chatEnabled, eventsEnabled, messages, true, true);
        }
    }

    public record Candidate(Settings settings, List<String> errors) {
        public Candidate {
            errors = List.copyOf(errors);
        }

        public boolean valid() {
            return settings != null && errors.isEmpty();
        }
    }

    public ConfigManager(MineBridgePlugin plugin) {
        this.plugin = plugin;
    }

    public Candidate loadCandidate() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        try {
            YamlConfiguration defaults = loadBundledDefaults();
            YamlConfiguration loaded = new YamlConfiguration();
            loaded.setDefaults(defaults);
            loaded.load(configFile);
            return candidateFrom(loaded, System.getenv("MINEBRIDGE_BOT_TOKEN"));
        } catch (IOException | InvalidConfigurationException | SecurityException exception) {
            return new Candidate(null, List.of(
                    "Could not read config.yml (" + exception.getClass().getSimpleName()
                            + "). Check that the file is readable and contains valid YAML."));
        }
    }

    public Candidate candidateFrom(
            ConfigurationSection configuration, String environmentToken) {
        List<String> errors = new ArrayList<>();
        String configuredToken = environmentToken == null || environmentToken.isBlank()
                ? readString(configuration, "discord.bot-token", errors) : "";
        String channelId = readString(configuration, "discord.channel-id", errors);
        Object chatValue = configuration.get("features.chat-relay");
        Object eventsValue = configuration.get("features.join-leave-notifications");
        Object serverStatusValue = configuration.get("features.server-status");
        Object playerListValue = configuration.get("features.player-list");
        errors.addAll(validateFeatureToggles(
                chatValue, eventsValue, serverStatusValue, playerListValue));
        Boolean chatEnabled = chatValue instanceof Boolean value ? value : null;
        Boolean eventsEnabled = eventsValue instanceof Boolean value ? value : null;
        Boolean serverStatusEnabled =
                serverStatusValue instanceof Boolean value ? value : null;
        Boolean playerListEnabled =
                playerListValue instanceof Boolean value ? value : null;

        Map<String, String> messages = new java.util.HashMap<>();
        Map<String, Object> messageValues = new java.util.HashMap<>();
        for (String key : MESSAGE_KEYS) {
            Object value = configuration.get(key);
            messageValues.put(key, value);
            if (value instanceof String stringValue) {
                messages.put(key, stringValue);
            }
        }
        errors.addAll(validateMessageTemplates(messageValues));

        String token = resolveBotToken(environmentToken, configuredToken);
        errors.addAll(validateConfiguration(token, channelId));
        if (errors.isEmpty()) {
            return new Candidate(new Settings(
                    token,
                    channelId,
                    chatEnabled,
                    eventsEnabled,
                    Map.copyOf(messages),
                    serverStatusEnabled,
                    playerListEnabled), List.of());
        }
        return new Candidate(null, errors);
    }

    public void commit(Candidate candidate) {
        if (!candidate.valid()) {
            throw new IllegalArgumentException("Cannot commit invalid MineBridge configuration.");
        }
        settings = candidate.settings();
    }

    public Settings currentSettings() {
        return settings;
    }

    public String getBotToken() {
        return settings.botToken();
    }

    public String getChannelId() {
        return settings.channelId();
    }

    public static String resolveBotToken(String environmentToken, String configuredToken) {
        if (environmentToken != null && !environmentToken.isBlank()) {
            return environmentToken.trim();
        }
        return configuredToken == null ? "" : configuredToken.trim();
    }

    public static List<String> validateFeatureToggles(
            Object chatRelay, Object joinLeaveNotifications) {
        return validateFeatureToggles(
                chatRelay, joinLeaveNotifications, Boolean.TRUE, Boolean.TRUE);
    }

    public static List<String> validateFeatureToggles(
            Object chatRelay,
            Object joinLeaveNotifications,
            Object serverStatus,
            Object playerList) {
        List<String> errors = new ArrayList<>();
        if (!(chatRelay instanceof Boolean)) {
            errors.add("features.chat-relay must be true or false.");
        }
        if (!(joinLeaveNotifications instanceof Boolean)) {
            errors.add("features.join-leave-notifications must be true or false.");
        }
        if (!(serverStatus instanceof Boolean)) {
            errors.add("features.server-status must be true or false.");
        }
        if (!(playerList instanceof Boolean)) {
            errors.add("features.player-list must be true or false.");
        }
        return List.copyOf(errors);
    }

    public static List<String> validateMessageTemplates(Map<String, ?> messages) {
        List<String> errors = new ArrayList<>();
        for (String key : MESSAGE_KEYS) {
            Object value = messages.get(key);
            if (!(value instanceof String stringValue)) {
                errors.add(key + " must be a string.");
            } else if (stringValue.isBlank()) {
                errors.add(key + " must not be empty.");
            } else if (stringValue.length() > MAX_TEMPLATE_LENGTH) {
                errors.add(key + " exceeds the 2000-character message limit.");
            } else {
                validateTemplatePlaceholders(key, stringValue, errors);
            }
        }
        return List.copyOf(errors);
    }

    private static void validateTemplatePlaceholders(
            String key, String template, List<String> errors) {
        Set<String> allowed = MESSAGE_PLACEHOLDERS.get(key);
        for (int index = 0; index < template.length();) {
            char current = template.charAt(index);
            if (current == '}') {
                errors.add(key + " contains malformed placeholder braces.");
                return;
            }
            if (current != '{') {
                index++;
                continue;
            }

            int closingBrace = template.indexOf('}', index + 1);
            int nestedBrace = template.indexOf('{', index + 1);
            if (closingBrace < 0 || (nestedBrace >= 0 && nestedBrace < closingBrace)) {
                errors.add(key + " contains malformed placeholder braces.");
                return;
            }

            String placeholder = template.substring(index + 1, closingBrace);
            if (!allowed.contains(placeholder)) {
                errors.add(key + " contains an unsupported placeholder; check its allowed values.");
            }
            index = closingBrace + 1;
        }
    }

    public static boolean isValidBotToken(String token) {
        return token != null
                && !token.isBlank()
                && token.length() <= 512
                && !token.equals("PUT_YOUR_BOT_TOKEN_HERE")
                && token.chars().noneMatch(Character::isWhitespace);
    }

    public static boolean isValidChannelId(String channelId) {
        return channelId != null && channelId.matches("\\d{17,20}");
    }

    public static List<String> validateConfiguration(String token, String channelId) {
        List<String> errors = new ArrayList<>();
        if (!isValidBotToken(token)) {
            errors.add(
                    "Discord bot token is missing or invalid; set MINEBRIDGE_BOT_TOKEN "
                            + "or discord.bot-token.");
        }
        if (!isValidChannelId(channelId)) {
            errors.add(
                    "Discord channel ID is missing or invalid; set discord.channel-id "
                            + "to the configured text channel's 17-20 digit ID.");
        }
        return List.copyOf(errors);
    }

    public List<String> validationErrors() {
        return validateConfiguration(settings.botToken(), settings.channelId());
    }

    public boolean chatEnabled() {
        return settings.chatEnabled();
    }

    public boolean eventsEnabled() {
        return settings.eventsEnabled();
    }

    public String message(String key, String fallback) {
        return settings.messages().getOrDefault(key, fallback);
    }

    public String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private YamlConfiguration loadBundledDefaults() throws IOException, InvalidConfigurationException {
        try (InputStream stream = plugin.getResource("config.yml")) {
            if (stream == null) {
                throw new IOException("Bundled config.yml is missing.");
            }
            YamlConfiguration defaults = new YamlConfiguration();
            defaults.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return defaults;
        }
    }

    private static String readString(
            ConfigurationSection configuration, String key, List<String> errors) {
        Object value = configuration.get(key);
        if (!(value instanceof String stringValue)) {
            errors.add(key + " must be a string.");
            return null;
        }
        return stringValue;
    }

}
