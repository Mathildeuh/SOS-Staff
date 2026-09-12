package fr.mathildeuh.sosstaff.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ConfigManager {

    private final JavaPlugin plugin;
    private final File configFile;
    private volatile Snapshot snapshot;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
    }

    public void load() throws ConfigValidationException {
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }
        reload();
    }

    public void reload() throws ConfigValidationException {
        this.snapshot = parse(YamlConfiguration.loadConfiguration(configFile));
    }

    private Snapshot parse(YamlConfiguration yaml) throws ConfigValidationException {
        ConfigurationSection commands = requireSection(yaml, "commands");
        String commandMain = commands.getString("main", "ticket");
        List<String> commandAliases = List.copyOf(commands.getStringList("aliases"));

        CreationMode creationMode = parseEnum(yaml.getString("creation-mode", "BOTH"), CreationMode.class, "creation-mode");

        ConfigurationSection categoriesSection = requireSection(yaml, "categories");
        Map<String, CategoryConfig> categories = new LinkedHashMap<>();
        for (String id : categoriesSection.getKeys(false)) {
            ConfigurationSection categorySection = categoriesSection.getConfigurationSection(id);
            if (categorySection == null) {
                throw new ConfigValidationException("categories." + id + " must be a mapping, not a plain value");
            }
            categories.put(id, CategoryConfig.fromSection(id, categorySection));
        }
        if (categories.isEmpty()) {
            throw new ConfigValidationException("categories: at least one category must be configured");
        }

        String defaultCategory = yaml.getString("default-category", categories.keySet().iterator().next());
        if (!categories.containsKey(defaultCategory)) {
            throw new ConfigValidationException("default-category '" + defaultCategory + "' is not one of the configured categories");
        }

        DiscordConfig discord = DiscordConfig.fromSection(requireSection(yaml, "discord"));

        ConfigurationSection escalation = requireSection(yaml, "escalation");
        boolean escalationEnabled = escalation.getBoolean("enabled", true);
        int noClaimAfterMinutes = escalation.getInt("no-claim-after-minutes", 15);

        ConfigurationSection antiSpam = requireSection(yaml, "anti-spam");
        int maxOpenTicketsPerPlayer = antiSpam.getInt("max-open-tickets-per-player", 1);
        int cooldownAfterCloseSeconds = antiSpam.getInt("cooldown-after-close-seconds", 30);
        String antiSpamBypassPermission = antiSpam.getString("bypass-permission", "sosstaff.bypass.antispam");

        ConfigurationSection storage = requireSection(yaml, "storage");
        StorageType storageType = parseEnum(storage.getString("type", "SQLITE"), StorageType.class, "storage.type");
        ConfigurationSection mysqlSection = storage.getConfigurationSection("mysql");
        MysqlSettings mysqlSettings = mysqlSection == null
                ? new MysqlSettings("localhost", 3306, "sosstaff", "", "")
                : new MysqlSettings(
                        mysqlSection.getString("host", "localhost"),
                        mysqlSection.getInt("port", 3306),
                        mysqlSection.getString("database", "sosstaff"),
                        mysqlSection.getString("username", ""),
                        mysqlSection.getString("password", ""));
        if (storageType == StorageType.MYSQL && mysqlSettings.username().isBlank()) {
            throw new ConfigValidationException("storage.mysql.username is required when storage.type is MYSQL");
        }

        int gdprRetentionDays = requireSection(yaml, "gdpr").getInt("transcript-retention-days", 90);
        boolean updateCheckerEnabled = requireSection(yaml, "update-checker").getBoolean("enabled", true);

        ConfigurationSection language = requireSection(yaml, "language");
        String languageDefault = language.getString("default", "en_US");
        List<String> languageShipped = List.copyOf(language.getStringList("shipped"));
        boolean languagePerPlayer = language.getBoolean("per-player", false);
        if (!languageShipped.contains(languageDefault)) {
            throw new ConfigValidationException("language.default '" + languageDefault + "' must be included in language.shipped");
        }

        return new Snapshot(commandMain, commandAliases, creationMode, Map.copyOf(categories), defaultCategory, discord,
                escalationEnabled, noClaimAfterMinutes, maxOpenTicketsPerPlayer, cooldownAfterCloseSeconds,
                antiSpamBypassPermission, storageType, mysqlSettings, gdprRetentionDays, updateCheckerEnabled,
                languageDefault, languageShipped, languagePerPlayer);
    }

    private static ConfigurationSection requireSection(ConfigurationSection parent, String key) throws ConfigValidationException {
        ConfigurationSection child = parent.getConfigurationSection(key);
        if (child == null) {
            throw new ConfigValidationException(key + " section is required in config.yml");
        }
        return child;
    }

    private static <E extends Enum<E>> E parseEnum(String raw, Class<E> type, String path) throws ConfigValidationException {
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ConfigValidationException(path + " '" + raw + "' is not a valid value");
        }
    }

    public String commandMain() {
        return snapshot.commandMain();
    }

    public List<String> commandAliases() {
        return snapshot.commandAliases();
    }

    public CreationMode creationMode() {
        return snapshot.creationMode();
    }

    public Map<String, CategoryConfig> categories() {
        return snapshot.categories();
    }

    /**
     * The category used by the fast {@code /ticket <reason>} path, which has no category step of
     * its own. Explicit in config.yml rather than "whichever category happens to be first" - that
     * would silently depend on YAML key order and file it under something like "Bug" for a player
     * whose issue has nothing to do with a bug.
     */
    public String defaultCategory() {
        return snapshot.defaultCategory();
    }

    public DiscordConfig discord() {
        return snapshot.discord();
    }

    public boolean escalationEnabled() {
        return snapshot.escalationEnabled();
    }

    public int noClaimAfterMinutes() {
        return snapshot.noClaimAfterMinutes();
    }

    public int maxOpenTicketsPerPlayer() {
        return snapshot.maxOpenTicketsPerPlayer();
    }

    public int cooldownAfterCloseSeconds() {
        return snapshot.cooldownAfterCloseSeconds();
    }

    public String antiSpamBypassPermission() {
        return snapshot.antiSpamBypassPermission();
    }

    public StorageType storageType() {
        return snapshot.storageType();
    }

    public MysqlSettings mysqlSettings() {
        return snapshot.mysqlSettings();
    }

    public int gdprRetentionDays() {
        return snapshot.gdprRetentionDays();
    }

    public boolean updateCheckerEnabled() {
        return snapshot.updateCheckerEnabled();
    }

    public String languageDefault() {
        return snapshot.languageDefault();
    }

    public List<String> languageShipped() {
        return snapshot.languageShipped();
    }

    public boolean languagePerPlayer() {
        return snapshot.languagePerPlayer();
    }

    public record MysqlSettings(String host, int port, String database, String username, String password) {
    }

    private record Snapshot(
            String commandMain,
            List<String> commandAliases,
            CreationMode creationMode,
            Map<String, CategoryConfig> categories,
            String defaultCategory,
            DiscordConfig discord,
            boolean escalationEnabled,
            int noClaimAfterMinutes,
            int maxOpenTicketsPerPlayer,
            int cooldownAfterCloseSeconds,
            String antiSpamBypassPermission,
            StorageType storageType,
            MysqlSettings mysqlSettings,
            int gdprRetentionDays,
            boolean updateCheckerEnabled,
            String languageDefault,
            List<String> languageShipped,
            boolean languagePerPlayer) {
    }
}
