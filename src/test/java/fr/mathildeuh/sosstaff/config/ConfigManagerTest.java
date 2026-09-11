package fr.mathildeuh.sosstaff.config;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfigManagerTest {

    @Test
    void loadsAllSectionsFromTheShippedDefaultConfig(@TempDir Path dataFolder) throws IOException, ConfigValidationException {
        writeConfig(dataFolder, defaultConfigYaml());

        ConfigManager configManager = new ConfigManager(pluginWithDataFolder(dataFolder));
        configManager.load();

        assertEquals("ticket", configManager.commandMain());
        assertEquals(2, configManager.categories().size());
        assertTrue(configManager.categories().containsKey("bug"));
        assertEquals(CreationMode.PRESET, configManager.categories().get("bug").mode());
        assertEquals(StorageType.SQLITE, configManager.storageType());
        assertEquals("en_US", configManager.languageDefault());
        assertTrue(configManager.languageShipped().contains("fr_FR"));
        assertEquals(4, configManager.discord().actionButtons().size());
        assertEquals("HEAL", configManager.discord().actionButtons().get("heal-player").command());
    }

    @Test
    void rejectsAnUnknownMaterialIcon(@TempDir Path dataFolder) throws IOException {
        String broken = defaultConfigYaml().replace("icon: BOOK", "icon: NOT_A_REAL_MATERIAL");
        writeConfig(dataFolder, broken);

        ConfigManager configManager = new ConfigManager(pluginWithDataFolder(dataFolder));

        assertThrows(ConfigValidationException.class, configManager::load);
    }

    @Test
    void rejectsMysqlStorageWithoutUsername(@TempDir Path dataFolder) throws IOException {
        String broken = defaultConfigYaml().replace("type: SQLITE", "type: MYSQL");
        writeConfig(dataFolder, broken);

        ConfigManager configManager = new ConfigManager(pluginWithDataFolder(dataFolder));

        assertThrows(ConfigValidationException.class, configManager::load);
    }

    @Test
    void discordTokenEnvironmentVariableIsOnlyUsedWhenSet(@TempDir Path dataFolder) throws IOException, ConfigValidationException {
        writeConfig(dataFolder, defaultConfigYaml());

        ConfigManager configManager = new ConfigManager(pluginWithDataFolder(dataFolder));
        configManager.load();

        assertFalse(configManager.discord().hasToken());
    }

    private static JavaPlugin pluginWithDataFolder(Path dataFolder) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        return plugin;
    }

    private static void writeConfig(Path dataFolder, String yaml) throws IOException {
        Files.writeString(dataFolder.resolve("config.yml"), yaml);
    }

    private static String defaultConfigYaml() throws IOException {
        try (InputStream in = ConfigManagerTest.class.getResourceAsStream("/config.yml")) {
            return new String(in.readAllBytes());
        }
    }
}
