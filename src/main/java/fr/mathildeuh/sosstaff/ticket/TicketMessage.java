package fr.mathildeuh.sosstaff.ticket;

import java.time.Instant;
import java.util.UUID;

public record TicketMessage(
        long id,
        long ticketId,
        UUID authorUuid,
        String authorName,
        boolean staff,
        String content,
        Instant sentAt
) {
}
