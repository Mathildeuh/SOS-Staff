package fr.mathildeuh.sosstaff.api.event;

import fr.mathildeuh.sosstaff.ticket.Ticket;
import fr.mathildeuh.sosstaff.ticket.TicketStatus;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/**
 * Fired right before a player's ticket-creation request is persisted, letting another plugin
 * veto it entirely by cancelling this event. Because the ticket does not exist yet at this
 * point, {@link #getTicket()} returns a transient, not-yet-persisted preview: its
 * {@link Ticket#id()} is {@code 0} and its {@link Ticket#status()} is always
 * {@link TicketStatus#OPEN}. Treat it as "what would be created," not as a reference to a real,
 * queryable ticket - once the event returns uncancelled, SOS-Staff creates the real ticket and
 * fires no further event for the creation itself.
 */
public final class TicketCreateEvent extends TicketEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private boolean cancelled;

    public TicketCreateEvent(Ticket ticket) {
        super(ticket);
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
