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
                .thenCompose(rejection -> rejection
                        .map(CompletableFuture::completedFuture)
                        .orElseGet(() -> create(playerUuid, category, priority)));
    }

    public CompletableFuture<Ticket> claim(long ticketId, UUID staffUuid) {
        return repository.claim(ticketId, staffUuid).thenCompose(ignored -> requireById(ticketId));
    }

    public CompletableFuture<Ticket> close(long ticketId, String reason) {
        return repository.close(ticketId, reason).thenCompose(ignored -> requireById(ticketId));
    }

    public CompletableFuture<Ticket> updatePriority(long ticketId, TicketPriority priority) {
        return repository.updatePriority(ticketId, priority).thenCompose(ignored -> requireById(ticketId));
    }

    public CompletableFuture<Ticket> reopen(long ticketId) {
        return repository.updateStatus(ticketId, TicketStatus.OPEN).thenCompose(ignored -> requireById(ticketId));
    }

    public CompletableFuture<Void> setDiscordChannelId(long ticketId, String discordChannelId) {
        return repository.setDiscordChannelId(ticketId, discordChannelId);
    }

    public CompletableFuture<Optional<Ticket>> findActiveTicket(UUID playerUuid) {
        return repository.findActiveByPlayer(playerUuid);
    }

    public CompletableFuture<Optional<Ticket>> findById(long ticketId) {
        return repository.findById(ticketId);
    }

    public CompletableFuture<List<Ticket>> findHistory(UUID playerUuid) {
        return repository.findHistoryByPlayer(playerUuid);
    }

    public CompletableFuture<List<Ticket>> findPage(Optional<TicketStatus> statusFilter, int page, int pageSize) {
        return repository.findPage(statusFilter, page, pageSize);
    }

    public CompletableFuture<Integer> countAll(Optional<TicketStatus> statusFilter) {
        return repository.countAll(statusFilter);
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
                        .thenApply(existing -> existing.map(TicketCreationResult.RejectedTooManyOpenTickets::new));
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
            return Optional.of(new TicketCreationResult.RejectedCooldownActive(cooldown.minus(sinceClose)));
        });
    }

    private CompletableFuture<Ticket> requireById(long ticketId) {
        return repository.findById(ticketId).thenApply(Optional::orElseThrow);
    }
}
