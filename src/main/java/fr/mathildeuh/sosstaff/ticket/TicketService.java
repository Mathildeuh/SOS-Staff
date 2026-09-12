package fr.mathildeuh.sosstaff.ticket;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import fr.mathildeuh.sosstaff.config.ConfigManager;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public final class TicketService {

    private final TicketRepository repository;
    private final ConfigManager configManager;
    private final Clock clock;

    /**
     * A write-through mirror of "each player's active ticket" and read-through mirror of "each
     * player's ticket history," kept warm by every method below. It exists so
     * {@link #peekActiveTicket(UUID)}/{@link #peekHistory(UUID)} can answer synchronously and
     * without touching the database - SosStaffAPI's public contract returns those two calls
     * directly rather than as a CompletableFuture, so blocking on the database is not an option.
     */
    private final Cache<UUID, Ticket> activeCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(1)).maximumSize(10_000).build();
    private final Cache<UUID, List<Ticket>> historyCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10)).maximumSize(10_000).build();

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

    public CompletableFuture<Ticket> claim(long ticketId, String claimedByDiscordId) {
        return repository.claim(ticketId, claimedByDiscordId).thenCompose(ignored -> requireById(ticketId))
                .thenApply(this::refreshCache);
    }

    public CompletableFuture<Ticket> close(long ticketId, String reason) {
        return repository.close(ticketId, reason).thenCompose(ignored -> requireById(ticketId))
                .thenApply(this::refreshCache);
    }

    public CompletableFuture<Ticket> updatePriority(long ticketId, TicketPriority priority) {
        return repository.updatePriority(ticketId, priority).thenCompose(ignored -> requireById(ticketId))
                .thenApply(this::refreshCache);
    }

    public CompletableFuture<Ticket> reopen(long ticketId) {
        return repository.updateStatus(ticketId, TicketStatus.OPEN).thenCompose(ignored -> requireById(ticketId))
                .thenApply(this::refreshCache);
    }

    public CompletableFuture<Void> setDiscordChannelId(long ticketId, String discordChannelId) {
        return repository.setDiscordChannelId(ticketId, discordChannelId);
    }

    public CompletableFuture<Optional<Ticket>> findActiveTicket(UUID playerUuid) {
        return repository.findActiveByPlayer(playerUuid).thenApply(ticketOpt -> {
            ticketOpt.ifPresentOrElse(ticket -> activeCache.put(playerUuid, ticket), () -> activeCache.invalidate(playerUuid));
            return ticketOpt;
        });
    }

    public CompletableFuture<Optional<Ticket>> findById(long ticketId) {
        return repository.findById(ticketId);
    }

    public CompletableFuture<List<Ticket>> findHistory(UUID playerUuid) {
        return repository.findHistoryByPlayer(playerUuid).thenApply(history -> {
            historyCache.put(playerUuid, history);
            return history;
        });
    }

    /**
     * A synchronous, cache-only read of the player's active ticket, never touching the
     * database. Reflects whatever the last {@link #findActiveTicket(UUID)} call or mutation for
     * that player observed - it may be empty for a player who has an active ticket in the
     * database but hasn't been looked up yet this session.
     */
    public Optional<Ticket> peekActiveTicket(UUID playerUuid) {
        return Optional.ofNullable(activeCache.getIfPresent(playerUuid));
    }

    /**
     * A synchronous, cache-only read of the player's ticket history, never touching the
     * database. Empty until {@link #findHistory(UUID)} has resolved at least once for that
     * player this session.
     */
    public List<Ticket> peekHistory(UUID playerUuid) {
        List<Ticket> cached = historyCache.getIfPresent(playerUuid);
        return cached != null ? cached : List.of();
    }

    public CompletableFuture<List<Ticket>> findPage(Optional<TicketStatus> statusFilter, int page, int pageSize) {
        return repository.findPage(statusFilter, page, pageSize);
    }

    public CompletableFuture<Integer> countAll(Optional<TicketStatus> statusFilter) {
        return repository.countAll(statusFilter);
    }

    /**
     * All CLOSED and ARCHIVED tickets, for the daily discord.on-close.auto-delete-after-days
     * sweep - not cached, since it only runs once a day and scans the whole closed backlog.
     */
    public CompletableFuture<List<Ticket>> findClosedAndArchivedTickets() {
        return repository.findByStatus(TicketStatus.CLOSED).thenCombine(
                repository.findByStatus(TicketStatus.ARCHIVED),
                (closed, archived) -> Stream.concat(closed.stream(), archived.stream()).toList());
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
        return repository.create(playerUuid, category, priority).thenApply(ticket -> {
            refreshCache(ticket);
            return new TicketCreationResult.Created(ticket);
        });
    }

    private Ticket refreshCache(Ticket ticket) {
        if (ticket.status() == TicketStatus.CLOSED || ticket.status() == TicketStatus.ARCHIVED) {
            activeCache.invalidate(ticket.playerUuid());
        } else {
            activeCache.put(ticket.playerUuid(), ticket);
        }
        historyCache.invalidate(ticket.playerUuid());
        return ticket;
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
