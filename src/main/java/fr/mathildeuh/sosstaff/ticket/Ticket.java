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
        // A Discord user id (snowflake), not a Minecraft account - claiming happens purely on
        // Discord and never requires the staff member to be online in-game (see ButtonHandler).
        String claimedBy,
        Instant createdAt,
        Instant closedAt,
        String closeReason,
        Integer rating
) {
}
