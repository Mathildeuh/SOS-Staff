package fr.mathildeuh.sosstaff.api.event;

import fr.mathildeuh.sosstaff.ticket.Ticket;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/**
 * Fired right before a staff member claims an existing, already-persisted ticket. Claiming
 * happens purely on Discord and never requires the claimer to be online in-game. Cancelling
 * this event stops the claim from being recorded - the ticket is left exactly as
 * {@link #getTicket()} describes it.
 */
public final class TicketClaimEvent extends TicketEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String discordUserId;
    private boolean cancelled;

    public TicketClaimEvent(Ticket ticket, String discordUserId) {
        super(ticket);
        this.discordUserId = discordUserId;
    }

    /**
     * The Discord user id (snowflake) of whoever is about to claim the ticket.
     */
    public String getDiscordUserId() {
        return discordUserId;
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
