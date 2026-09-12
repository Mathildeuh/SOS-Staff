package fr.mathildeuh.sosstaff.ticket;

import fr.mathildeuh.sosstaff.config.ConfigManager;
import fr.mathildeuh.sosstaff.discord.ChannelOrchestrator;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Runs GdprService's transcript purge and the discord.on-close.auto-delete-after-days channel
 * cleanup once a day. Both are plain database/REST sweeps with no Bukkit world/entity state
 * involved, so they belong on the async scheduler rather than any region scheduler.
 */
public final class RetentionScheduler {

    private static final long PERIOD_HOURS = 24;

    private final JavaPlugin plugin;
    private final GdprService gdprService;
    private final TicketService ticketService;
    private final ChannelOrchestrator channelOrchestrator;
    private final ConfigManager configManager;
    private final Logger logger;

    public RetentionScheduler(JavaPlugin plugin, GdprService gdprService, TicketService ticketService,
                               ChannelOrchestrator channelOrchestrator, ConfigManager configManager, Logger logger) {
        this.plugin = plugin;
        this.gdprService = gdprService;
        this.ticketService = ticketService;
        this.channelOrchestrator = channelOrchestrator;
        this.configManager = configManager;
        this.logger = logger;
    }

    public void start() {
        Bukkit.getAsyncScheduler().runAtFixedRate(plugin, ignored -> {
            purgeTranscriptsOnce();
            purgeExpiredChannelsOnce();
        }, PERIOD_HOURS, PERIOD_HOURS, TimeUnit.HOURS);
    }

    private void purgeTranscriptsOnce() {
        gdprService.purgeExpiredTranscripts()
                .thenAccept(count -> {
                    if (count > 0) {
                        logger.info("GDPR transcript retention purged " + count + " expired ticket message(s).");
                    }
                })
                .exceptionally(throwable -> {
                    logger.warning("GDPR transcript retention purge failed: " + throwable);
                    return null;
                });
    }

    private void purgeExpiredChannelsOnce() {
        int days = configManager.discord().onClose().autoDeleteAfterDays();
        if (days <= 0) {
            return;
        }
        Instant threshold = Instant.now().minus(Duration.ofDays(days));
        ticketService.findClosedAndArchivedTickets()
                .thenAccept(tickets -> tickets.stream()
                        .filter(ticket -> ticket.discordChannelId() != null
                                && ticket.closedAt() != null && ticket.closedAt().isBefore(threshold))
                        .forEach(ticket -> channelOrchestrator.deleteChannelById(ticket.discordChannelId())
                                .thenRun(() -> ticketService.setDiscordChannelId(ticket.id(), null))))
                .exceptionally(throwable -> {
                    logger.warning("discord.on-close auto-delete sweep failed: " + throwable);
                    return null;
                });
    }
}
