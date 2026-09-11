package fr.mathildeuh.sosstaff.ticket;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Runs GdprService's transcript purge once a day. Purging is a plain database sweep with no
 * Bukkit world/entity state involved, so it belongs on the async scheduler rather than any
 * region scheduler.
 */
public final class RetentionScheduler {

    private static final long PERIOD_HOURS = 24;

    private final JavaPlugin plugin;
    private final GdprService gdprService;
    private final Logger logger;

    public RetentionScheduler(JavaPlugin plugin, GdprService gdprService, Logger logger) {
        this.plugin = plugin;
        this.gdprService = gdprService;
        this.logger = logger;
    }

    public void start() {
        Bukkit.getAsyncScheduler().runAtFixedRate(plugin, ignored -> purgeOnce(), PERIOD_HOURS, PERIOD_HOURS, TimeUnit.HOURS);
    }

    private void purgeOnce() {
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
}
