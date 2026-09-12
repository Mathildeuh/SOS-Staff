package fr.mathildeuh.sosstaff.api.event;

import fr.mathildeuh.sosstaff.ticket.Ticket;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * Fired right before a live-chat message is persisted and relayed across the Minecraft/Discord
 * bridge, in either direction. Cancelling this event drops the message entirely: it is neither
 * stored in {@code ticket_messages} nor forwarded to the other side. Useful for a chat-filter or
 * moderation plugin that needs to veto specific content.
 */
public final class TicketMessageEvent extends TicketEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID authorUuid;
    private final String authorName;
    private final String content;
    private final boolean staff;
    private boolean cancelled;

    public TicketMessageEvent(Ticket ticket, UUID authorUuid, String authorName, String content, boolean staff) {
        super(ticket);
        this.authorUuid = authorUuid;
        this.authorName = authorName;
        this.content = content;
        this.staff = staff;
    }

    /**
     * The author's Minecraft UUID, or {@code null} for a message authored purely on the Discord
     * side by someone with no resolvable Minecraft account.
     */
    public UUID getAuthorUuid() {
        return authorUuid;
    }

    /**
     * The author's display name - a Minecraft username for a player message, a Discord display
     * name for a staff message.
     */
    public String getAuthorName() {
        return authorName;
    }

    /**
     * The plain-text message content, about to be relayed.
     */
    public String getContent() {
        return content;
    }

    /**
     * {@code true} if this message is a staff reply relayed from Discord into the game,
     * {@code false} if it is a player's in-game chat relayed into the ticket's Discord channel.
     */
    public boolean isStaff() {
        return staff;
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
