package fr.mathildeuh.sosstaff.api.event;

import fr.mathildeuh.sosstaff.ticket.Ticket;
import org.bukkit.event.Event;

/**
 * Base class for every ticket-related Bukkit event fired by SOS-Staff. All of them are
 * synchronous ({@link #isAsynchronous()} is {@code false}) and are always fired from the main
 * thread or, on Folia, the region thread that owns the relevant player - never from a JDA
 * callback thread or a storage-executor thread. Register a listener the normal Bukkit way to
 * observe or veto ticket activity from another plugin.
 */
public abstract class TicketEvent extends Event {

    private final Ticket ticket;

    protected TicketEvent(Ticket ticket) {
        this.ticket = ticket;
    }

    /**
     * The ticket this event concerns. See each subclass for whether the ticket is already fully
     * persisted at the point the event fires.
     */
    public Ticket getTicket() {
        return ticket;
    }
}
