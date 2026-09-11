package fr.mathildeuh.sosstaff.ticket;

import fr.mathildeuh.sosstaff.config.ConfigManager;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class GdprService {

    private final TicketRepository ticketRepository;
    private final TicketMessageRepository messageRepository;
    private final ConfigManager configManager;
    private final Clock clock;

    public GdprService(TicketRepository ticketRepository, TicketMessageRepository messageRepository, ConfigManager configManager) {
        this(ticketRepository, messageRepository, configManager, Clock.systemUTC());
    }

    GdprService(TicketRepository ticketRepository, TicketMessageRepository messageRepository,
                ConfigManager configManager, Clock clock) {
        this.ticketRepository = ticketRepository;
        this.messageRepository = messageRepository;
        this.configManager = configManager;
        this.clock = clock;
    }

    /**
     * Erases every ticket this player opened (and its messages). Does not touch messages they
     * may have authored as staff inside someone else's ticket - see TicketRepository#deleteAllForPlayer.
     */
    public CompletableFuture<Integer> eraseAllDataFor(UUID playerUuid) {
        return ticketRepository.deleteAllForPlayer(playerUuid);
    }

    /**
     * gdpr.transcript-retention-days: deletes every ticket message older than that many days,
     * regardless of its ticket's status. Meant to run on a periodic schedule.
     */
    public CompletableFuture<Integer> purgeExpiredTranscripts() {
        var cutoff = clock.instant().minus(Duration.ofDays(configManager.gdprRetentionDays()));
        return messageRepository.deleteOlderThan(cutoff);
    }
}
