package fr.mathildeuh.sosstaff.lang;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;

public final class LangManager {

    private final JavaPlugin plugin;
    private final String defaultLocale;
    private final List<String> shippedLocales;
    private volatile Map<String, YamlConfiguration> locales = Map.of();

    public LangManager(JavaPlugin plugin, String defaultLocale, List<String> shippedLocales) {
        this.plugin = plugin;
        this.defaultLocale = defaultLocale;
        this.shippedLocales = List.copyOf(shippedLocales);
    }

    public void load() {
        Map<String, YamlConfiguration> loaded = new LinkedHashMap<>();
        for (String locale : shippedLocales) {
            String resourcePath = "lang/" + locale + ".yml";
            File file = new File(plugin.getDataFolder(), resourcePath);
            if (!file.exists()) {
                plugin.saveResource(resourcePath, false);
            }
            loaded.put(locale, YamlConfiguration.loadConfiguration(file));
        }
        this.locales = Map.copyOf(loaded);
        warnAboutMissingKeys();
    }

    public void reload() {
        load();
    }

    public String get(String key, Map<String, String> placeholders) {
        return get(defaultLocale, key, placeholders);
    }

    public String get(Message message, Map<String, String> placeholders) {
        return get(message.key(), placeholders);
    }

    public String get(String locale, String key, Map<String, String> placeholders) {
        String raw = resolveRaw(locale, key);
        if (raw == null) {
            plugin.getLogger().warning("Missing language key '" + key + "' in locale '" + locale + "' and fallback '" + defaultLocale + "'");
            return "[[" + key + "]]";
        }
        return applyPlaceholders(raw, placeholders);
    }

    public Set<String> missingKeys(String locale) {
        YamlConfiguration pivot = locales.get(defaultLocale);
        YamlConfiguration target = locales.get(locale);
        if (pivot == null || target == null) {
            return Set.of();
        }
        Set<String> missing = new TreeSet<>();
        for (String key : pivot.getKeys(true)) {
            if (pivot.isConfigurationSection(key) || target.isSet(key)) {
                continue;
            }
            missing.add(key);
        }
        return missing;
    }

    private String resolveRaw(String locale, String key) {
        YamlConfiguration localeYaml = locales.get(locale);
        String value = localeYaml == null ? null : localeYaml.getString(key);
        if (value != null) {
            return value;
        }
        YamlConfiguration fallback = locales.get(defaultLocale);
        return fallback == null ? null : fallback.getString(key);
    }

    private static String applyPlaceholders(String raw, Map<String, String> placeholders) {
        String result = raw;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return result;
    }

    private void warnAboutMissingKeys() {
        for (String locale : shippedLocales) {
            if (locale.equals(defaultLocale)) {
                continue;
            }
            Set<String> missing = missingKeys(locale);
            if (!missing.isEmpty()) {
                plugin.getLogger().log(Level.WARNING, "Locale '" + locale + "' is missing " + missing.size()
                        + " key(s) compared to '" + defaultLocale + "': " + missing);
            }
        }
    }
}
