package fr.mathildeuh.sosstaff.api.event;

import fr.mathildeuh.sosstaff.ticket.Ticket;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/**
 * Fired right before an existing, already-persisted ticket is closed, whether the close was
 * requested by the player in-game or by staff from Discord. Cancelling this event stops the
 * close from being recorded - the ticket is left exactly as {@link #getTicket()} describes it.
 */
public final class TicketCloseEvent extends TicketEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String reason;
    private boolean cancelled;

    public TicketCloseEvent(Ticket ticket, String reason) {
        super(ticket);
        this.reason = reason;
    }

    /**
     * The close reason about to be recorded.
     */
    public String getReason() {
        return reason;
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
