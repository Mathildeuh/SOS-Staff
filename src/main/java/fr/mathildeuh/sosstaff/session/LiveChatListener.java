package fr.mathildeuh.sosstaff.session;

import fr.mathildeuh.sosstaff.api.event.TicketMessageEvent;
import fr.mathildeuh.sosstaff.discord.WebhookRelay;
import fr.mathildeuh.sosstaff.gui.PendingChatPrompts;
import fr.mathildeuh.sosstaff.ticket.Ticket;
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
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.logging.Logger;

public final class LiveChatListener implements Listener {

    private final JavaPlugin plugin;
    private final LiveChatSessionManager sessionManager;
    private final WebhookRelay webhookRelay;
    private final TicketMessageRepository messageRepository;
    private final TicketService ticketService;
    private final PendingChatPrompts pendingChatPrompts;
    private final Logger logger;

    public LiveChatListener(JavaPlugin plugin, LiveChatSessionManager sessionManager, WebhookRelay webhookRelay,
                             TicketMessageRepository messageRepository, TicketService ticketService,
                             PendingChatPrompts pendingChatPrompts, Logger logger) {
        this.plugin = plugin;
        this.sessionManager = sessionManager;
        this.webhookRelay = webhookRelay;
        this.messageRepository = messageRepository;
        this.ticketService = ticketService;
        this.pendingChatPrompts = pendingChatPrompts;
        this.logger = logger;
    }

    /**
     * Relays a single message into a ticket outside of normal live chat - used by
     * {@code /ticket <message>} when the player already has an active ticket, so they can reply
     * without needing an open chat session (e.g. right after logging back in). Opens/refreshes
     * the session as a side effect, same as a join would, so any further plain chat also relays.
     */
    public void relayPlayerReply(Ticket ticket, Player player, String content) {
        if (ticket.discordChannelId() == null) {
            return;
        }
        sessionManager.open(new TicketSession(player.getUniqueId(), ticket.id(), ticket.discordChannelId()));
        fireAndRelay(ticket, ticket.discordChannelId(), player, content, false);
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
            relay(session.ticketId(), session.discordChannelId(), player, content, false);
        }, () -> sessionManager.findStaffAttachment(player.getUniqueId()).ifPresent(attachment -> {
            event.setCancelled(true);
            relay(attachment.ticketId(), attachment.discordChannelId(), player, content, true);
        }));
    }

    private void relay(long ticketId, String discordChannelId, Player player, String content, boolean isStaff) {
        ticketService.findById(ticketId).thenAccept(ticketOpt -> ticketOpt.ifPresent(ticket ->
                player.getScheduler().run(plugin,
                        scheduledTask -> fireAndRelay(ticket, discordChannelId, player, content, isStaff), null)));
    }

    private void fireAndRelay(Ticket ticket, String discordChannelId, Player player, String content, boolean isStaff) {
        UUID authorUuid = player.getUniqueId();
        String authorName = player.getName();
        TicketMessageEvent event = new TicketMessageEvent(ticket, authorUuid, authorName, content, isStaff);
        plugin.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }
        messageRepository.append(ticket.id(), authorUuid, authorName, isStaff, content);
        webhookRelay.relayPlayerMessage(discordChannelId, authorUuid, authorName, content)
                .exceptionally(throwable -> {
                    logger.warning("Failed to relay chat for ticket #" + ticket.id() + ": " + throwable);
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
