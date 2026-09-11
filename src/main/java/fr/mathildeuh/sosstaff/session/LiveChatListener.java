package fr.mathildeuh.sosstaff.session;

import fr.mathildeuh.sosstaff.discord.WebhookRelay;
import fr.mathildeuh.sosstaff.ticket.TicketMessageRepository;
import fr.mathildeuh.sosstaff.ticket.TicketService;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.logging.Logger;

public final class LiveChatListener implements Listener {

    private final LiveChatSessionManager sessionManager;
    private final WebhookRelay webhookRelay;
    private final TicketMessageRepository messageRepository;
    private final TicketService ticketService;
    private final Logger logger;

    public LiveChatListener(LiveChatSessionManager sessionManager, WebhookRelay webhookRelay,
                             TicketMessageRepository messageRepository, TicketService ticketService, Logger logger) {
        this.sessionManager = sessionManager;
        this.webhookRelay = webhookRelay;
        this.messageRepository = messageRepository;
        this.ticketService = ticketService;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        sessionManager.findByPlayer(player.getUniqueId()).ifPresent(session -> {
            event.setCancelled(true);
            String content = PlainTextComponentSerializer.plainText().serialize(event.message());

            messageRepository.append(session.ticketId(), player.getUniqueId(), player.getName(), false, content);
            webhookRelay.relayPlayerMessage(session.discordChannelId(), player.getUniqueId(), player.getName(), content)
                    .exceptionally(throwable -> {
                        logger.warning("Failed to relay chat for ticket #" + session.ticketId() + ": " + throwable);
                        return null;
                    });
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        reopenSessionIfNeeded(event.getPlayer());
    }

    public void reopenSessionIfNeeded(Player player) {
        ticketService.findActiveTicket(player.getUniqueId()).thenAccept(activeTicket ->
                activeTicket
                        .filter(ticket -> ticket.discordChannelId() != null)
                        .ifPresent(ticket -> sessionManager.open(
                                new TicketSession(player.getUniqueId(), ticket.id(), ticket.discordChannelId()))));
    }
}
