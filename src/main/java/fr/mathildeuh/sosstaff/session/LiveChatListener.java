package fr.mathildeuh.sosstaff.session;

import fr.mathildeuh.sosstaff.discord.WebhookRelay;
import fr.mathildeuh.sosstaff.gui.PendingChatPrompts;
import fr.mathildeuh.sosstaff.ticket.TicketMessageRepository;
import fr.mathildeuh.sosstaff.ticket.TicketService;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;
import java.util.logging.Logger;

public final class LiveChatListener implements Listener {

    private final LiveChatSessionManager sessionManager;
    private final WebhookRelay webhookRelay;
    private final TicketMessageRepository messageRepository;
    private final TicketService ticketService;
    private final PendingChatPrompts pendingChatPrompts;
    private final Logger logger;

    public LiveChatListener(LiveChatSessionManager sessionManager, WebhookRelay webhookRelay,
                             TicketMessageRepository messageRepository, TicketService ticketService,
                             PendingChatPrompts pendingChatPrompts, Logger logger) {
        this.sessionManager = sessionManager;
        this.webhookRelay = webhookRelay;
        this.messageRepository = messageRepository;
        this.ticketService = ticketService;
        this.pendingChatPrompts = pendingChatPrompts;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String content = PlainTextComponentSerializer.plainText().serialize(event.message());

        pendingChatPrompts.consume(player.getUniqueId()).ifPresentOrElse(prompt -> {
            event.setCancelled(true);
            prompt.onComplete().accept(content);
        }, () -> relayIfInSession(event, player, content));
    }

    private void relayIfInSession(AsyncChatEvent event, Player player, String content) {
        sessionManager.findByPlayer(player.getUniqueId()).ifPresentOrElse(session -> {
            event.setCancelled(true);
            relay(session.ticketId(), session.discordChannelId(), player.getUniqueId(), player.getName(), content, false);
        }, () -> sessionManager.findStaffAttachment(player.getUniqueId()).ifPresent(attachment -> {
            event.setCancelled(true);
            relay(attachment.ticketId(), attachment.discordChannelId(), player.getUniqueId(), player.getName(), content, true);
        }));
    }

    private void relay(long ticketId, String discordChannelId, UUID authorUuid, String authorName, String content, boolean isStaff) {
        messageRepository.append(ticketId, authorUuid, authorName, isStaff, content);
        webhookRelay.relayPlayerMessage(discordChannelId, authorUuid, authorName, content)
                .exceptionally(throwable -> {
                    logger.warning("Failed to relay chat for ticket #" + ticketId + ": " + throwable);
                    return null;
                });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        reopenSessionIfNeeded(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerUuid = event.getPlayer().getUniqueId();
        pendingChatPrompts.cancel(playerUuid);
        sessionManager.detachStaff(playerUuid);
    }

    public void reopenSessionIfNeeded(Player player) {
        ticketService.findActiveTicket(player.getUniqueId()).thenAccept(activeTicket ->
                activeTicket
                        .filter(ticket -> ticket.discordChannelId() != null)
                        .ifPresent(ticket -> sessionManager.open(
                                new TicketSession(player.getUniqueId(), ticket.id(), ticket.discordChannelId()))));
    }
}
