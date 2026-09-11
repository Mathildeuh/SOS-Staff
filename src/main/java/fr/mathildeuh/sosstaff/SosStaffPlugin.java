package fr.mathildeuh.sosstaff;

import fr.mathildeuh.sosstaff.config.ConfigManager;
import fr.mathildeuh.sosstaff.config.ConfigValidationException;
import fr.mathildeuh.sosstaff.lang.LangManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class SosStaffPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private LangManager langManager;

    @Override
    public void onEnable() {
        configManager = new ConfigManager(this);
        try {
            configManager.load();
        } catch (ConfigValidationException e) {
            getLogger().severe("Invalid config.yml, disabling: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        langManager = new LangManager(this, configManager.languageDefault(), configManager.languageShipped());
        langManager.load();

        getLogger().info("SOS-Staff has been enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("SOS-Staff has been disabled.");
    }

    public ConfigManager configManager() {
        return configManager;
    }

    public LangManager langManager() {
        return langManager;
    }
}
