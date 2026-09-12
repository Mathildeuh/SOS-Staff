package fr.mathildeuh.sosstaff.api;

import fr.mathildeuh.sosstaff.ticket.Ticket;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The public entry point other plugins use to read and create SOS-Staff tickets. Obtain an
 * instance through Bukkit's {@code ServicesManager} once SOS-Staff has enabled:
 * {@code Bukkit.getServicesManager().getRegistration(SosStaffAPI.class).getProvider()}. Declare
 * SOS-Staff as a {@code depend} or {@code softdepend} in your own {@code plugin.yml} so it loads
 * first.
 *
 * <p>{@link #getActiveTicket(UUID)} and {@link #getTicketHistory(UUID)} are synchronous and
 * never touch the database or block the calling thread: they read from an in-memory cache that
 * SOS-Staff keeps warm as players use the plugin. A player who has an active ticket in the
 * database but has triggered no SOS-Staff read or write yet this session (for example, they
 * haven't logged in or run a ticket command since the last restart) may not appear until they
 * do. {@link #createTicket(UUID, String, String)} is the only call that reaches storage and
 * Discord, so it returns a {@link CompletableFuture}.
 */
public interface SosStaffAPI {

    /**
     * The player's currently open ticket, if any, read from the in-memory cache.
     *
     * @param playerUuid the Minecraft UUID of the player to look up
     */
    Optional<Ticket> getActiveTicket(UUID playerUuid);

    /**
     * Every ticket the player has ever opened, newest first, read from the in-memory cache.
     *
     * @param playerUuid the Minecraft UUID of the player to look up
     */
    List<Ticket> getTicketHistory(UUID playerUuid);

    /**
     * Creates a new ticket for the player, exactly as if they had run the in-game creation
     * command with the given category and an initial chat message: a database row is inserted,
     * a Discord channel is opened for it, and a live-chat session is started. Fires {@link
     * fr.mathildeuh.sosstaff.api.event.TicketCreateEvent} first, so another plugin can veto the
     * request.
     *
     * @param playerUuid the Minecraft UUID of the player the ticket is for
     * @param category   a category id from {@code config.yml}'s {@code categories} section
     * @param message    the ticket's opening message, relayed into the Discord channel once it
     *                   is created
     * @return a future completing with the created {@link Ticket}, or completing exceptionally
     * with a {@link TicketCreationRejectedException} if a plugin cancelled the request or the
     * anti-spam rules refused it (too many open tickets, or still within the post-close cooldown)
     */
    CompletableFuture<Ticket> createTicket(UUID playerUuid, String category, String message);
}
