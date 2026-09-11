package fr.mathildeuh.sosstaff.ticket;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface TicketRepository {

    CompletableFuture<Ticket> create(UUID playerUuid, String category, TicketPriority priority);

    CompletableFuture<Optional<Ticket>> findById(long id);

    CompletableFuture<Optional<Ticket>> findActiveByPlayer(UUID playerUuid);

    CompletableFuture<Optional<Ticket>> findMostRecentClosedByPlayer(UUID playerUuid);

    CompletableFuture<List<Ticket>> findHistoryByPlayer(UUID playerUuid);

    CompletableFuture<List<Ticket>> findByStatus(TicketStatus status);

    CompletableFuture<Integer> countActiveByPlayer(UUID playerUuid);

    CompletableFuture<Void> updateStatus(long id, TicketStatus status);

    CompletableFuture<Void> claim(long id, UUID staffUuid);

    CompletableFuture<Void> updatePriority(long id, TicketPriority priority);

    CompletableFuture<Void> setDiscordChannelId(long id, String discordChannelId);

    CompletableFuture<Void> close(long id, String closeReason);

    CompletableFuture<Void> setRating(long id, int rating);
}
