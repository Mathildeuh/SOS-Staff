package fr.mathildeuh.sosstaff.ticket;

import java.time.Instant;
import java.util.UUID;

public record Ticket(
        long id,
        UUID playerUuid,
        String category,
        TicketStatus status,
        TicketPriority priority,
        String discordChannelId,
        UUID claimedBy,
        Instant createdAt,
        Instant closedAt,
        String closeReason,
        Integer rating
) {
}
