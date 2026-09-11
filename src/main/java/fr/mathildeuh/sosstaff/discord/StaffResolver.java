package fr.mathildeuh.sosstaff.discord;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Resolves the Minecraft player behind a Discord interaction by matching the clicker's Discord
 * display name against online player names - the only option available with no account-linking
 * system anywhere in this project (see ChannelOrchestrator's class comment).
 */
public final class StaffResolver {

    private StaffResolver() {
    }

    public static Player resolveByDiscordName(JavaPlugin plugin, String discordDisplayName) {
        return plugin.getServer().getPlayerExact(discordDisplayName);
    }
}
