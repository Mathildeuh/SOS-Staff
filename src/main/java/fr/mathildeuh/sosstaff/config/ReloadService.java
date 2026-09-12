package fr.mathildeuh.sosstaff.config;

import fr.mathildeuh.sosstaff.lang.LangManager;

import java.util.function.Consumer;

/**
 * config + lang + categories all live inside ConfigManager's single snapshot, so reloading it
 * already covers all three; the one extra step is reconnecting to Discord when the token
 * changed, which the caller supplies as a callback so this class never has to know about JDA.
 * Never touches an already-open ticket's stored Discord channel id - those are read from the
 * database, never recalculated here.
 */
public final class ReloadService {

    private final ConfigManager configManager;
    private final LangManager langManager;
    private final Consumer<String> onDiscordTokenChanged;

    public ReloadService(ConfigManager configManager, LangManager langManager, Consumer<String> onDiscordTokenChanged) {
        this.configManager = configManager;
        this.langManager = langManager;
        this.onDiscordTokenChanged = onDiscordTokenChanged;
    }

    public void reload() throws ConfigValidationException {
        String oldToken = configManager.discord().token();
        configManager.reload();
        langManager.reload();

        String newToken = configManager.discord().token();
        if (!oldToken.equals(newToken)) {
            onDiscordTokenChanged.accept(newToken);
        }
    }
}
