package fr.mathildeuh.sosstaff.api;

import fr.mathildeuh.sosstaff.api.event.TicketCreateEvent;
import fr.mathildeuh.sosstaff.ticket.Ticket;
import fr.mathildeuh.sosstaff.ticket.TicketCreationCoordinator;
import fr.mathildeuh.sosstaff.ticket.TicketCreationResult;
import fr.mathildeuh.sosstaff.ticket.TicketPriority;
import fr.mathildeuh.sosstaff.ticket.TicketService;
import fr.mathildeuh.sosstaff.ticket.TicketStatus;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The concrete {@link SosStaffAPI} bound in {@code onEnable}. Reads are served straight from
 * {@link TicketService}'s cache; {@link #createTicket(UUID, String, String)} goes through the
 * same {@link TicketCreationCoordinator} pipeline as the in-game command and GUI, so a ticket
 * created through this API behaves identically (Discord channel, live-chat session, and all).
 */
public final class SosStaffAPIImpl implements SosStaffAPI {

    private final TicketService ticketService;
    private final TicketCreationCoordinator creationCoordinator;

    public SosStaffAPIImpl(TicketService ticketService, TicketCreationCoordinator creationCoordinator) {
        this.ticketService = ticketService;
        this.creationCoordinator = creationCoordinator;
    }

    @Override
    public Optional<Ticket> getActiveTicket(UUID playerUuid) {
        return ticketService.peekActiveTicket(playerUuid);
    }

    @Override
    public List<Ticket> getTicketHistory(UUID playerUuid) {
        return ticketService.peekHistory(playerUuid);
    }

    @Override
    public CompletableFuture<Ticket> createTicket(UUID playerUuid, String category, String message) {
        Ticket preview = new Ticket(0, playerUuid, category, TicketStatus.OPEN, TicketPriority.MEDIUM,
                null, null, Instant.now(), null, null, null);
        TicketCreateEvent event = new TicketCreateEvent(preview);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return CompletableFuture.failedFuture(
                    new TicketCreationRejectedException("Ticket creation was cancelled by another plugin."));
        }

        OfflinePlayer player = Bukkit.getOfflinePlayer(playerUuid);
        String playerName = player.getName() != null ? player.getName() : playerUuid.toString();

        return creationCoordinator.create(playerUuid, playerName, category, TicketPriority.MEDIUM, false, message)
                .thenCompose(SosStaffAPIImpl::unwrap);
    }

    private static CompletableFuture<Ticket> unwrap(TicketCreationResult result) {
        return switch (result) {
            case TicketCreationResult.Created created -> CompletableFuture.completedFuture(created.ticket());
            case TicketCreationResult.RejectedTooManyOpenTickets rejected -> CompletableFuture.failedFuture(
                    new TicketCreationRejectedException(
                            "Player already has an open ticket: #" + rejected.existingTicket().id()));
            case TicketCreationResult.RejectedCooldownActive rejected -> CompletableFuture.failedFuture(
                    new TicketCreationRejectedException("Player is still within the post-close cooldown for "
                            + rejected.remaining().toSeconds() + "s"));
        };
    }
}
