package fr.mathildeuh.sosstaff.api;

/**
 * Thrown to complete a {@link SosStaffAPI#createTicket(java.util.UUID, String, String)} future
 * exceptionally when the request is refused rather than created: the player already has too
 * many open tickets, is still inside the post-close cooldown, or another plugin cancelled the
 * underlying {@link fr.mathildeuh.sosstaff.api.event.TicketCreateEvent}.
 */
public final class TicketCreationRejectedException extends RuntimeException {

    public TicketCreationRejectedException(String message) {
        super(message);
    }
}
