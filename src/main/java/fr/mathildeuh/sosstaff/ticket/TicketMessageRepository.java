package fr.mathildeuh.sosstaff.ticket;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface TicketMessageRepository {

    CompletableFuture<TicketMessage> append(long ticketId, UUID authorUuid, String authorName, boolean staff, String content);

    CompletableFuture<List<TicketMessage>> findByTicket(long ticketId);

    /**
     * GDPR transcript retention: deletes every message sent before the cutoff, regardless of
     * its ticket's status. Returns how many rows were removed.
     */
    CompletableFuture<Integer> deleteOlderThan(Instant cutoff);
}
