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

    /**
     * A real SQL LIMIT/OFFSET page (not fetch-everything-then-slice-in-Java), ordered newest
     * first, optionally narrowed to one status for the admin panel's filter row.
     */
    CompletableFuture<List<Ticket>> findPage(Optional<TicketStatus> statusFilter, int page, int pageSize);

    CompletableFuture<Integer> countAll(Optional<TicketStatus> statusFilter);

    CompletableFuture<Integer> countActiveByPlayer(UUID playerUuid);

    CompletableFuture<Void> updateStatus(long id, TicketStatus status);

    CompletableFuture<Void> claim(long id, String claimedByDiscordId);

    CompletableFuture<Void> updatePriority(long id, TicketPriority priority);

    CompletableFuture<Void> setDiscordChannelId(long id, String discordChannelId);

    CompletableFuture<Void> close(long id, String closeReason);

    CompletableFuture<Void> setRating(long id, int rating);

    /**
     * GDPR erasure: deletes every ticket_messages row belonging to one of this player's tickets,
     * then the tickets themselves, in one transaction (ticket_messages.ticket_id has a foreign
     * key onto tickets(id), so the message rows must go first). Returns how many tickets were
     * removed. Only erases tickets where this player is the reporter - a message they authored
     * as staff inside someone else's ticket is untouched.
     */
    CompletableFuture<Integer> deleteAllForPlayer(UUID playerUuid);
}
