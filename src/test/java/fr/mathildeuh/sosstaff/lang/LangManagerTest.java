package fr.mathildeuh.sosstaff.lang;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LangManagerTest {

    private static final List<String> SHIPPED = List.of("en_US", "fr_FR", "es_ES", "ru_RU", "de_DE");

    @Test
    void resolvesAKeyInTheDefaultLocale(@TempDir Path dataFolder) throws IOException {
        copyShippedLocales(dataFolder);
        LangManager langManager = new LangManager(pluginWithDataFolder(dataFolder), "en_US", SHIPPED);
        langManager.load();

        String prompt = langManager.get(Message.CREATION_REPORT_PROMPT, Map.of());

        assertTrue(prompt.toLowerCase().contains("report"));
    }

    @Test
    void substitutesPlaceholders(@TempDir Path dataFolder) throws IOException {
        copyShippedLocales(dataFolder);
        LangManager langManager = new LangManager(pluginWithDataFolder(dataFolder), "en_US", SHIPPED);
        langManager.load();

        String message = langManager.get(Message.GENERAL_RELOAD_FAILURE, Map.of("error", "boom"));

        assertEquals("Reload failed: boom", message);
    }

    @Test
    void fallsBackToTheDefaultLocaleWhenAKeyIsMissing(@TempDir Path dataFolder) throws IOException {
        copyShippedLocales(dataFolder);
        // Simulate a translation that has not caught up with the pivot yet.
        Files.writeString(dataFolder.resolve("lang/fr_FR.yml"), "general:\n  reload-success: \"OK\"\n");

        LangManager langManager = new LangManager(pluginWithDataFolder(dataFolder), "en_US", SHIPPED);
        langManager.load();

        String prompt = langManager.get("fr_FR", Message.CREATION_REPORT_PROMPT.key(), Map.of());

        assertTrue(prompt.toLowerCase().contains("report"));
    }

    @Test
    void detectsMissingKeysAgainstThePivotLocale(@TempDir Path dataFolder) throws IOException {
        copyShippedLocales(dataFolder);
        Files.writeString(dataFolder.resolve("lang/de_DE.yml"), "general:\n  reload-success: \"OK\"\n");

        LangManager langManager = new LangManager(pluginWithDataFolder(dataFolder), "en_US", SHIPPED);
        langManager.load();

        Set<String> missing = langManager.missingKeys("de_DE");

        assertTrue(missing.contains("creation.report.prompt"));
        assertTrue(missing.contains("general.reload-failure"));
    }

    private static JavaPlugin pluginWithDataFolder(Path dataFolder) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("SosStaffTest"));
        return plugin;
    }

    private static void copyShippedLocales(Path dataFolder) throws IOException {
        Files.createDirectories(dataFolder.resolve("lang"));
        for (String locale : SHIPPED) {
            String resourcePath = "/lang/" + locale + ".yml";
            try (InputStream in = LangManagerTest.class.getResourceAsStream(resourcePath)) {
                Files.write(dataFolder.resolve("lang/" + locale + ".yml"), in.readAllBytes());
            }
        }
    }
}
