package fr.mathildeuh.sosstaff.ticket;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface TicketMessageRepository {

    CompletableFuture<TicketMessage> append(long ticketId, UUID authorUuid, String authorName, boolean staff, String content);

    CompletableFuture<List<TicketMessage>> findByTicket(long ticketId);
}
