package fr.mathildeuh.sosstaff.ticket;

import fr.mathildeuh.sosstaff.config.ConfigManager;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class TicketService {

    private final TicketRepository repository;
    private final ConfigManager configManager;
    private final Clock clock;

    public TicketService(TicketRepository repository, ConfigManager configManager) {
        this(repository, configManager, Clock.systemUTC());
    }

    TicketService(TicketRepository repository, ConfigManager configManager, Clock clock) {
        this.repository = repository;
        this.configManager = configManager;
        this.clock = clock;
    }

    public CompletableFuture<TicketCreationResult> createTicket(UUID playerUuid, String category, TicketPriority priority, boolean bypassAntiSpam) {
        if (bypassAntiSpam) {
            return create(playerUuid, category, priority);
        }
        return checkAntiSpam(playerUuid)
                .thenCompose(rejection -> rejection.isPresent()
                        ? CompletableFuture.completedFuture(rejection.get())
                        : create(playerUuid, category, priority));
    }

    public CompletableFuture<Ticket> claim(long ticketId, UUID staffUuid) {
        return repository.claim(ticketId, staffUuid).thenCompose(v -> requireById(ticketId));
    }

    public CompletableFuture<Ticket> close(long ticketId, String reason) {
        return repository.close(ticketId, reason).thenCompose(v -> requireById(ticketId));
    }

    public CompletableFuture<Ticket> updatePriority(long ticketId, TicketPriority priority) {
        return repository.updatePriority(ticketId, priority).thenCompose(v -> requireById(ticketId));
    }

    public CompletableFuture<Optional<Ticket>> findActiveTicket(UUID playerUuid) {
        return repository.findActiveByPlayer(playerUuid);
    }

    public CompletableFuture<List<Ticket>> findHistory(UUID playerUuid) {
        return repository.findHistoryByPlayer(playerUuid);
    }

    public CompletableFuture<List<Ticket>> findTicketsNeedingEscalation() {
        if (!configManager.escalationEnabled()) {
            return CompletableFuture.completedFuture(List.of());
        }
        var threshold = clock.instant().minus(Duration.ofMinutes(configManager.noClaimAfterMinutes()));
        return repository.findByStatus(TicketStatus.OPEN)
                .thenApply(tickets -> tickets.stream()
                        .filter(ticket -> ticket.createdAt().isBefore(threshold))
                        .toList());
    }

    private CompletableFuture<TicketCreationResult> create(UUID playerUuid, String category, TicketPriority priority) {
        return repository.create(playerUuid, category, priority).thenApply(TicketCreationResult.Created::new);
    }

    private CompletableFuture<Optional<TicketCreationResult>> checkAntiSpam(UUID playerUuid) {
        return repository.countActiveByPlayer(playerUuid).thenCompose(activeCount -> {
            if (activeCount >= configManager.maxOpenTicketsPerPlayer()) {
                return repository.findActiveByPlayer(playerUuid)
                        .thenApply(existing -> existing.map(ticket ->
                                (TicketCreationResult) new TicketCreationResult.RejectedTooManyOpenTickets(ticket)));
            }
            return checkCooldown(playerUuid);
        });
    }

    private CompletableFuture<Optional<TicketCreationResult>> checkCooldown(UUID playerUuid) {
        return repository.findMostRecentClosedByPlayer(playerUuid).thenApply(mostRecentClosed -> {
            if (mostRecentClosed.isEmpty() || mostRecentClosed.get().closedAt() == null) {
                return Optional.empty();
            }
            Duration sinceClose = Duration.between(mostRecentClosed.get().closedAt(), clock.instant());
            Duration cooldown = Duration.ofSeconds(configManager.cooldownAfterCloseSeconds());
            if (sinceClose.compareTo(cooldown) >= 0) {
                return Optional.empty();
            }
            return Optional.of((TicketCreationResult) new TicketCreationResult.RejectedCooldownActive(cooldown.minus(sinceClose)));
        });
    }

    private CompletableFuture<Ticket> requireById(long ticketId) {
        return repository.findById(ticketId).thenApply(Optional::orElseThrow);
    }
}
