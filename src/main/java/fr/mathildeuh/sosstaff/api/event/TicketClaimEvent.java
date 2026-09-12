package fr.mathildeuh.sosstaff.api.event;

import fr.mathildeuh.sosstaff.ticket.Ticket;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * Fired right before a staff member claims an existing, already-persisted ticket. Cancelling
 * this event stops the claim from being recorded - the ticket is left exactly as
 * {@link #getTicket()} describes it.
 */
public final class TicketClaimEvent extends TicketEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID staffUuid;
    private boolean cancelled;

    public TicketClaimEvent(Ticket ticket, UUID staffUuid) {
        super(ticket);
        this.staffUuid = staffUuid;
    }

    /**
     * The Minecraft UUID of the staff member about to claim the ticket.
     */
    public UUID getStaffUuid() {
        return staffUuid;
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
