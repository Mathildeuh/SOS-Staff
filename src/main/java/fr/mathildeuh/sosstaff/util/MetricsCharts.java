package fr.mathildeuh.sosstaff.util;

import fr.mathildeuh.sosstaff.config.ConfigManager;
import fr.mathildeuh.sosstaff.discord.DiscordGateway;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;

/**
 * Registers the custom bStats charts SOS-Staff reports beyond Metrics' own defaults (server
 * software, online mode, player count, and so on). Every chart here reads directly from
 * already-loaded ConfigManager/DiscordGateway state - no database access, so none of this can
 * ever block a thread waiting on storage. The point is adoption/configuration insight for
 * whoever maintains the plugin (which storage backend, which creation flow, how strict the
 * anti-spam and retention settings are in practice), not per-ticket telemetry.
 */
public final class MetricsCharts {

    private MetricsCharts() {
    }

    public static void register(Metrics metrics, ConfigManager configManager, DiscordGateway discordGateway) {
        metrics.addCustomChart(new SimplePie("storage_type", () -> configManager.storageType().name()));
        metrics.addCustomChart(new SimplePie("creation_mode", () -> configManager.creationMode().name()));
        metrics.addCustomChart(new SimplePie("creation_ui", () -> configManager.creationUi().name()));
        metrics.addCustomChart(new SimplePie("default_language", configManager::languageDefault));
        metrics.addCustomChart(new SimplePie("escalation_enabled",
                () -> String.valueOf(configManager.escalationEnabled())));
        metrics.addCustomChart(new SimplePie("update_checker_enabled",
                () -> String.valueOf(configManager.updateCheckerEnabled())));
        metrics.addCustomChart(new SimplePie("discord_connected",
                () -> String.valueOf(discordGateway.jda().isPresent())));
        metrics.addCustomChart(new SimplePie("max_open_tickets_per_player",
                () -> String.valueOf(configManager.maxOpenTicketsPerPlayer())));
        metrics.addCustomChart(new SimplePie("configured_categories",
                () -> bucket(configManager.categories().size(), 2, 5, 10)));
        metrics.addCustomChart(new SimplePie("configured_action_buttons",
                () -> bucket(configManager.discord().actionButtons().size(), 0, 2, 5)));
        metrics.addCustomChart(new SimplePie("gdpr_retention_days",
                () -> bucket(configManager.gdprRetentionDays(), 30, 90, 180)));
    }

    /**
     * Turns a raw count into a small, human-readable range label instead of one pie slice per
     * distinct integer - config values like retention days or category counts vary widely
     * enough that the raw numbers alone would make for an unreadable chart.
     */
    private static String bucket(int value, int low, int mid, int high) {
        if (value <= low) {
            return "0-" + low;
        }
        if (value <= mid) {
            return (low + 1) + "-" + mid;
        }
        if (value <= high) {
            return (mid + 1) + "-" + high;
        }
        return high + "+";
    }
}
