package fr.mathildeuh.sosstaff.ticket;

import fr.mathildeuh.sosstaff.discord.ChannelOrchestrator;
import fr.mathildeuh.sosstaff.discord.WebhookRelay;
import fr.mathildeuh.sosstaff.session.LiveChatSessionManager;
import fr.mathildeuh.sosstaff.session.TicketSession;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The single "create a ticket end to end" use case: persist it, open its Discord channel, start
 * its live-chat session, and (if the creation flow already captured an opening message - the
 * in-game command path never does, the GUI creation flow does) relay and persist that first
 * message too. PlayerCommands and CreationMenu both go through this instead of duplicating the
 * same sequence with slightly different wiring.
 */
public final class TicketCreationCoordinator {

    private final TicketService ticketService;
    private final ChannelOrchestrator channelOrchestrator;
    private final LiveChatSessionManager sessionManager;
    private final TicketMessageRepository messageRepository;
    private final WebhookRelay webhookRelay;

    public TicketCreationCoordinator(TicketService ticketService, ChannelOrchestrator channelOrchestrator,
                                      LiveChatSessionManager sessionManager, TicketMessageRepository messageRepository,
                                      WebhookRelay webhookRelay) {
        this.ticketService = ticketService;
        this.channelOrchestrator = channelOrchestrator;
        this.sessionManager = sessionManager;
        this.messageRepository = messageRepository;
        this.webhookRelay = webhookRelay;
    }

    public CompletableFuture<TicketCreationResult> create(UUID playerUuid, String playerName, String category,
                                                            TicketPriority priority, boolean bypassAntiSpam, String initialMessage) {
        return ticketService.createTicket(playerUuid, category, priority, bypassAntiSpam)
                .thenCompose(result -> {
                    if (!(result instanceof TicketCreationResult.Created created)) {
                        return CompletableFuture.completedFuture(result);
                    }
                    return channelOrchestrator.createChannelForTicket(created.ticket(), playerName).thenApply(channelId -> {
                        channelId.ifPresent(id -> onChannelReady(created.ticket().id(), playerUuid, playerName, id, initialMessage));
                        return result;
                    });
                });
    }

    private void onChannelReady(long ticketId, UUID playerUuid, String playerName, String discordChannelId, String initialMessage) {
        ticketService.setDiscordChannelId(ticketId, discordChannelId);
        sessionManager.open(new TicketSession(playerUuid, ticketId, discordChannelId));
        if (initialMessage != null && !initialMessage.isBlank()) {
            messageRepository.append(ticketId, playerUuid, playerName, false, initialMessage);
            webhookRelay.relayPlayerMessage(discordChannelId, playerUuid, playerName, initialMessage);
        }
    }
}
