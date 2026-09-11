package fr.mathildeuh.sosstaff.session;

import java.util.UUID;

public record TicketSession(UUID playerUuid, long ticketId, String discordChannelId) {
}
