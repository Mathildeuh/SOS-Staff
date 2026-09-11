package fr.mathildeuh.sosstaff.ticket;

import java.time.Duration;

public sealed interface TicketCreationResult {

    record Created(Ticket ticket) implements TicketCreationResult {
    }

    record RejectedTooManyOpenTickets(Ticket existingTicket) implements TicketCreationResult {
    }

    record RejectedCooldownActive(Duration remaining) implements TicketCreationResult {
    }
}
