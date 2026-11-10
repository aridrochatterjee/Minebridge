package com.minebridge.config;

import com.minebridge.MineBridgePlugin;
import com.minebridge.achievement.AdvancementCategory;
import com.minebridge.death.DeathCategory;
import com.minebridge.discord.DiscordEmbedFactory;
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
    private static final Set<String> DEATH_PLACEHOLDERS =
            Set.of("victim", "killer", "cause", "category");
    private static final Set<String> DEATH_EMBED_PLACEHOLDERS =
            Set.of("victim", "killer", "cause", "category", "message");
    private static final Set<String> ACHIEVEMENT_PLACEHOLDERS =
            Set.of("player", "title", "description", "category", "challenge");
    private static final Map<String, String> DEFAULT_ACHIEVEMENT_MESSAGES = Map.of(
            "normal", "&a✦ {player} has made the advancement [{title}]!",
            "challenge", "&6🏆 {player} has completed the challenge [{title}]!");

    private final MineBridgePlugin plugin;
    private volatile Settings settings = new Settings(
            "", "", true, true, SAFE_MESSAGES, true, true,
            defaultDeathSettings(), defaultAchievementSettings(), defaultDiscordEventsSettings());

    public record Settings(
            String botToken,
            String channelId,
            boolean chatEnabled,
            boolean eventsEnabled,
            Map<String, String> messages,
            boolean serverStatusEnabled,
            boolean playerListEnabled,
            DeathSettings deathMessages,
            AchievementSettings achievements,
            DiscordEventsSettings discordEvents) {
        public Settings {
            messages = Map.copyOf(messages);
        }

        public Settings(
                String botToken,
                String channelId,
                boolean chatEnabled,
                boolean eventsEnabled,
                Map<String, String> messages,
                boolean serverStatusEnabled,
                boolean playerListEnabled) {
            this(botToken, channelId, chatEnabled, eventsEnabled, messages,
                    serverStatusEnabled, playerListEnabled,
                    defaultDeathSettings(), defaultAchievementSettings(),
                    defaultDiscordEventsSettings());
        }

        public Settings(
                String botToken,
                String channelId,
                boolean chatEnabled,
                boolean eventsEnabled,
                Map<String, String> messages,
                boolean serverStatusEnabled,
                boolean playerListEnabled,
                DeathSettings deathMessages) {
            this(botToken, channelId, chatEnabled, eventsEnabled, messages,
                    serverStatusEnabled, playerListEnabled,
                    deathMessages, defaultAchievementSettings(), defaultDiscordEventsSettings());
        }

        public Settings(
                String botToken,
                String channelId,
                boolean chatEnabled,
                boolean eventsEnabled,
                Map<String, String> messages,
                boolean serverStatusEnabled,
                boolean playerListEnabled,
                DeathSettings deathMessages,
                AchievementSettings achievements) {
            this(botToken, channelId, chatEnabled, eventsEnabled, messages,
                    serverStatusEnabled, playerListEnabled, deathMessages,
                    achievements, defaultDiscordEventsSettings());
        }

        public Settings(
                String botToken,
                String channelId,
                boolean chatEnabled,
                boolean eventsEnabled,
                Map<String, String> messages) {
            this(botToken, channelId, chatEnabled, eventsEnabled, messages,
                    true, true, defaultDeathSettings(), defaultAchievementSettings(),
                    defaultDiscordEventsSettings());
        }
    }

    public record DiscordEventsSettings(
            boolean enabled,
            boolean embedsEnabled,
            String footer,
            boolean playerEventsEnabled,
            String playerEventsChannelId,
            boolean joinEnabled,
            boolean leaveEnabled,
            boolean firstJoinEnabled,
            boolean achievementsEnabled,
            String achievementsChannelId,
            Map<String, Boolean> achievementCategories,
            boolean challengesEnabled,
            boolean deathsEnabled,
            String deathsChannelId,
            boolean playerKillsEnabled,
            boolean mobKillsEnabled,
            boolean environmentalDeathsEnabled) {
        public DiscordEventsSettings {
            footer = footer == null ? "MineBridge | Minecraft Events" : footer;
            playerEventsChannelId = playerEventsChannelId == null ? "" : playerEventsChannelId;
            achievementsChannelId = achievementsChannelId == null ? "" : achievementsChannelId;
            deathsChannelId = deathsChannelId == null ? "" : deathsChannelId;
            achievementCategories = Map.copyOf(achievementCategories);
        }
    }

    public record DeathSettings(
            boolean enabled,
            boolean minecraftEnabled,
            boolean minecraftBroadcast,
            boolean discordEnabled,
            boolean embedEnabled,
            String discordChannelId,
            String embedTitle,
            String embedDescription,
            int embedColor,
            Map<String, Boolean> categories,
            Map<String, String> messages) {
        public DeathSettings {
            discordChannelId = discordChannelId == null ? "" : discordChannelId;
            categories = Map.copyOf(categories);
            messages = Map.copyOf(messages);
        }
    }

    public record AchievementSettings(
            boolean enabled,
            boolean minecraftEnabled,
            boolean discordEnabled,
            boolean challengesEnabled,
            String discordChannelId,
            String messageTemplate,
            String challengeMessageTemplate,
            String embedTitle,
            String embedDescription,
            int embedColor,
            Map<String, Boolean> categories) {
        public AchievementSettings {
            discordChannelId = discordChannelId == null ? "" : discordChannelId;
            categories = Map.copyOf(categories);
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

        DeathSettings deathMessages = readDeathSettings(configuration, errors);
        AchievementSettings achievements = readAchievementSettings(configuration, errors);
        DiscordEventsSettings discordEvents = readDiscordEventsSettings(configuration, errors);
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
                    playerListEnabled,
                    deathMessages,
                    achievements,
                    discordEvents), List.of());
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

    public static List<String> validateDeathMessageTemplates(Map<String, ?> templates) {
        List<String> errors = new ArrayList<>();
        for (DeathCategory category : DeathCategory.values()) {
            validateDeathTemplate(
                    "death-messages.messages." + category.key(),
                    templates.get(category.key()),
                    DEATH_PLACEHOLDERS,
                    2000,
                    errors);
        }
        validateDeathTemplate(
                "death-messages.discord.title",
                templates.get("discord.title"),
                DEATH_EMBED_PLACEHOLDERS,
                256,
                errors);
        validateDeathTemplate(
                "death-messages.discord.description",
                templates.get("discord.description"),
                DEATH_EMBED_PLACEHOLDERS,
                4096,
                errors);
        return List.copyOf(errors);
    }

    public static List<String> validateAchievementTemplates(Map<String, ?> templates) {
        List<String> errors = new ArrayList<>();
        validateDeathTemplate(
                "achievements.messages.normal",
                templates.get("normal"),
                ACHIEVEMENT_PLACEHOLDERS,
                2000,
                errors);
        validateDeathTemplate(
                "achievements.messages.challenge",
                templates.get("challenge"),
                ACHIEVEMENT_PLACEHOLDERS,
                2000,
                errors);
        validateDeathTemplate(
                "achievements.discord.title",
                templates.get("discord.title"),
                ACHIEVEMENT_PLACEHOLDERS,
                256,
                errors);
        validateDeathTemplate(
                "achievements.discord.description",
                templates.get("discord.description"),
                ACHIEVEMENT_PLACEHOLDERS,
                4096,
                errors);
        return List.copyOf(errors);
    }

    private static void validateDeathTemplate(
            String key,
            Object value,
            Set<String> allowed,
            int maxLength,
            List<String> errors) {
        if (!(value instanceof String template)) {
            errors.add(key + " must be a string.");
        } else if (template.isBlank()) {
            errors.add(key + " must not be empty.");
        } else if (template.length() > maxLength) {
            errors.add(key + " exceeds the " + maxLength + "-character limit.");
        } else {
            validateTemplatePlaceholders(key, template, allowed, errors);
        }
    }

    private static void validateTemplatePlaceholders(
            String key, String template, List<String> errors) {
        validateTemplatePlaceholders(key, template, MESSAGE_PLACEHOLDERS.get(key), errors);
    }

    private static void validateTemplatePlaceholders(
            String key, String template, Set<String> allowed, List<String> errors) {
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

    private static DeathSettings readDeathSettings(
            ConfigurationSection configuration, List<String> errors) {
        Object sectionValue = configuration.get("death-messages");
        if (sectionValue != null && !(sectionValue instanceof ConfigurationSection)) {
            errors.add("death-messages must be a configuration section.");
            return defaultDeathSettings();
        }
        for (String section : List.of("minecraft", "discord", "categories", "messages")) {
            Object value = configuration.get("death-messages." + section);
            if (value != null && !(value instanceof ConfigurationSection)) {
                errors.add("death-messages." + section + " must be a configuration section.");
            }
        }

        Map<String, Boolean> categories = new java.util.HashMap<>();
        for (DeathCategory category : DeathCategory.values()) {
            String path = "death-messages.categories." + category.key();
            categories.put(category.key(), readBoolean(
                    configuration, path, true, errors));
        }

        Map<String, String> messages = new java.util.HashMap<>();
        Map<String, Object> templates = new java.util.HashMap<>();
        for (DeathCategory category : DeathCategory.values()) {
            String key = category.key();
            String path = "death-messages.messages." + key;
            String value = readStringOrDefault(
                    configuration, path, defaultDeathMessage(key), errors);
            messages.put(key, value);
            templates.put(key, value);
        }

        String title = readStringOrDefault(
                configuration,
                "death-messages.discord.title",
                "☠ Player death",
                errors);
        String description = readStringOrDefault(
                configuration,
                "death-messages.discord.description",
                "{message}\nCategory: {category}\nCause: {cause}",
                errors);
        templates.put("discord.title", title);
        templates.put("discord.description", description);
        errors.addAll(validateDeathMessageTemplates(templates));

        String deathChannelId = readStringOrDefault(
                configuration, "death-messages.discord.channel-id", "", errors);
        if (!deathChannelId.isBlank() && !isValidChannelId(deathChannelId)) {
            errors.add(
                    "death-messages.discord.channel-id must be a 17-20 digit Discord channel ID.");
        }

        Object colorValue = configuration.get("death-messages.discord.color");
        String color = colorValue == null ? "#D32F2F"
                : colorValue instanceof String stringValue ? stringValue : null;
        int embedColor = 0xD32F2F;
        if (color == null) {
            errors.add("death-messages.discord.color must be a six-digit hex color string.");
        } else {
            String normalized = color.startsWith("#") ? color.substring(1) : color;
            if (!normalized.matches("[0-9a-fA-F]{6}")) {
                errors.add("death-messages.discord.color must be a six-digit hex color string.");
            } else {
                embedColor = Integer.parseInt(normalized, 16);
            }
        }

        return new DeathSettings(
                readBoolean(configuration, "death-messages.enabled", true, errors),
                readBoolean(configuration, "death-messages.minecraft.enabled", true, errors),
                readBoolean(configuration, "death-messages.minecraft.broadcast", true, errors),
                readBoolean(configuration, "death-messages.discord.enabled", true, errors),
                readBoolean(configuration, "death-messages.discord.embed", true, errors),
                deathChannelId,
                title,
                description,
                embedColor,
                categories,
                messages);
    }

    private static AchievementSettings readAchievementSettings(
            ConfigurationSection configuration, List<String> errors) {
        Object sectionValue = configuration.get("achievements");
        if (sectionValue != null && !(sectionValue instanceof ConfigurationSection)) {
            errors.add("achievements must be a configuration section.");
            return defaultAchievementSettings();
        }
        for (String section : List.of("minecraft", "discord", "categories", "messages", "challenges")) {
            Object value = configuration.get("achievements." + section);
            if (value != null && !(value instanceof ConfigurationSection)) {
                errors.add("achievements." + section + " must be a configuration section.");
            }
        }

        Map<String, Boolean> categories = new java.util.HashMap<>();
        for (AdvancementCategory category : AdvancementCategory.values()) {
            categories.put(
                    category.key(),
                    readBoolean(
                            configuration,
                            "achievements.categories." + category.key(),
                            true,
                            errors));
        }

        String normalMessage = readStringOrDefault(
                configuration,
                "achievements.messages.normal",
                DEFAULT_ACHIEVEMENT_MESSAGES.get("normal"),
                errors);
        String challengeMessage = readStringOrDefault(
                configuration,
                "achievements.messages.challenge",
                DEFAULT_ACHIEVEMENT_MESSAGES.get("challenge"),
                errors);
        String title = readStringOrDefault(
                configuration,
                "achievements.discord.title",
                "✦ Advancement unlocked",
                errors);
        String description = readStringOrDefault(
                configuration,
                "achievements.discord.description",
                "{description}",
                errors);
        errors.addAll(validateAchievementTemplates(Map.of(
                "normal", normalMessage,
                "challenge", challengeMessage,
                "discord.title", title,
                "discord.description", description)));

        String channelId = readStringOrDefault(
                configuration, "achievements.discord.channel-id", "", errors);
        if (!channelId.isBlank() && !isValidChannelId(channelId)) {
            errors.add("achievements.discord.channel-id must be a 17-20 digit Discord channel ID.");
        }

        Object colorValue = configuration.get("achievements.discord.color");
        String color = colorValue == null ? "#43A047"
                : colorValue instanceof String stringValue ? stringValue : null;
        int embedColor = 0x43A047;
        if (color == null) {
            errors.add("achievements.discord.color must be a six-digit hex color string.");
        } else {
            String normalized = color.startsWith("#") ? color.substring(1) : color;
            if (!normalized.matches("[0-9a-fA-F]{6}")) {
                errors.add("achievements.discord.color must be a six-digit hex color string.");
            } else {
                embedColor = Integer.parseInt(normalized, 16);
            }
        }

        return new AchievementSettings(
                readBoolean(configuration, "achievements.enabled", true, errors),
                readBoolean(configuration, "achievements.minecraft.enabled", true, errors),
                readBoolean(configuration, "achievements.discord.enabled", true, errors),
                readBoolean(configuration, "achievements.challenges.enabled", true, errors),
                channelId,
                normalMessage,
                challengeMessage,
                title,
                description,
                embedColor,
                categories);
    }

    private static DiscordEventsSettings readDiscordEventsSettings(
            ConfigurationSection configuration, List<String> errors) {
        Object sectionValue = configuration.get("discord-events");
        if (sectionValue != null && !(sectionValue instanceof ConfigurationSection)) {
            errors.add("discord-events must be a configuration section.");
            return defaultDiscordEventsSettings();
        }
        for (String section : List.of("embeds", "player-events", "achievements", "deaths")) {
            Object value = configuration.get("discord-events." + section);
            if (value != null && !(value instanceof ConfigurationSection)) {
                errors.add("discord-events." + section + " must be a configuration section.");
            }
        }

        String footer = readStringOrDefault(
                configuration,
                "discord-events.embeds.footer",
                DiscordEmbedFactory.DEFAULT_FOOTER,
                errors);
        String playerChannel = readStringOrDefault(
                configuration, "discord-events.player-events.channel-id", "", errors);
        String achievementChannel = readStringOrDefault(
                configuration, "discord-events.achievements.channel-id", "", errors);
        String deathsChannel = readStringOrDefault(
                configuration, "discord-events.deaths.channel-id", "", errors);
        validateOptionalChannelId(
                "discord-events.player-events.channel-id", playerChannel, errors);
        validateOptionalChannelId(
                "discord-events.achievements.channel-id", achievementChannel, errors);
        validateOptionalChannelId(
                "discord-events.deaths.channel-id", deathsChannel, errors);

        Map<String, Boolean> achievementCategories = new java.util.HashMap<>();
        for (AdvancementCategory category : AdvancementCategory.values()) {
            if (category != AdvancementCategory.OTHER) {
                achievementCategories.put(
                        category.key(),
                        readBoolean(
                                configuration,
                                "discord-events.achievements." + category.key(),
                                true,
                                errors));
            }
        }

        return new DiscordEventsSettings(
                readBoolean(configuration, "discord-events.enabled", true, errors),
                readBoolean(configuration, "discord-events.embeds.enabled", true, errors),
                footer,
                readBoolean(
                        configuration, "discord-events.player-events.enabled", true, errors),
                playerChannel,
                readBoolean(configuration, "discord-events.player-events.join", true, errors),
                readBoolean(configuration, "discord-events.player-events.leave", true, errors),
                readBoolean(
                        configuration, "discord-events.player-events.first-join", true, errors),
                readBoolean(
                        configuration, "discord-events.achievements.enabled", true, errors),
                achievementChannel,
                achievementCategories,
                readBoolean(
                        configuration, "discord-events.achievements.challenges", true, errors),
                readBoolean(configuration, "discord-events.deaths.enabled", true, errors),
                deathsChannel,
                readBoolean(
                        configuration, "discord-events.deaths.player-kill", true, errors),
                readBoolean(configuration, "discord-events.deaths.mob-kill", true, errors),
                readBoolean(
                        configuration, "discord-events.deaths.environmental", true, errors));
    }

    private static void validateOptionalChannelId(
            String path, String channelId, List<String> errors) {
        if (!channelId.isBlank() && !isValidChannelId(channelId)) {
            errors.add(path + " must be a 17-20 digit Discord channel ID.");
        }
    }

    private static boolean readBoolean(
            ConfigurationSection configuration,
            String path,
            boolean defaultValue,
            List<String> errors) {
        Object value = configuration.get(path);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        errors.add(path + " must be true or false.");
        return defaultValue;
    }

    private static String readStringOrDefault(
            ConfigurationSection configuration,
            String path,
            String defaultValue,
            List<String> errors) {
        Object value = configuration.get(path);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof String stringValue) {
            return stringValue;
        }
        errors.add(path + " must be a string.");
        return defaultValue;
    }

    private static DeathSettings defaultDeathSettings() {
        Map<String, Boolean> categories = new java.util.HashMap<>();
        Map<String, String> messages = new java.util.HashMap<>();
        for (DeathCategory category : DeathCategory.values()) {
            categories.put(category.key(), true);
            messages.put(category.key(), defaultDeathMessage(category.key()));
        }
        return new DeathSettings(
                true,
                true,
                true,
                true,
                true,
                "",
                "☠ Player death",
                "{message}\nCategory: {category}\nCause: {cause}",
                0xD32F2F,
                categories,
                messages);
    }

    private static AchievementSettings defaultAchievementSettings() {
        Map<String, Boolean> categories = new java.util.HashMap<>();
        for (AdvancementCategory category : AdvancementCategory.values()) {
            categories.put(category.key(), true);
        }
        return new AchievementSettings(
                true,
                true,
                true,
                true,
                "",
                DEFAULT_ACHIEVEMENT_MESSAGES.get("normal"),
                DEFAULT_ACHIEVEMENT_MESSAGES.get("challenge"),
                "✦ Advancement unlocked",
                "{description}",
                0x43A047,
                categories);
    }

    private static DiscordEventsSettings defaultDiscordEventsSettings() {
        Map<String, Boolean> achievementCategories = new java.util.HashMap<>();
        for (AdvancementCategory category : AdvancementCategory.values()) {
            if (category != AdvancementCategory.OTHER) {
                achievementCategories.put(category.key(), true);
            }
        }
        return new DiscordEventsSettings(
                true,
                true,
                "MineBridge | Minecraft Events",
                true,
                "",
                true,
                true,
                true,
                true,
                "",
                achievementCategories,
                true,
                true,
                "",
                true,
                true,
                true);
    }

    private static String defaultDeathMessage(String category) {
        return switch (category) {
            case "player-kill", "mob-kill" -> "&c☠ {victim} was slain by {killer}";
            case "fall" -> "&c☠ {victim} fell to their death";
            case "lava" -> "&c☠ {victim} tried to swim in lava";
            case "fire" -> "&c☠ {victim} burned to death";
            case "drowning" -> "&c☠ {victim} drowned";
            case "void" -> "&c☠ {victim} fell out of the world";
            case "explosion" -> "&c☠ {victim} died in an explosion";
            case "environmental" -> "&c☠ {victim} died from {cause}";
            default -> "&c☠ {victim} died";
        };
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
