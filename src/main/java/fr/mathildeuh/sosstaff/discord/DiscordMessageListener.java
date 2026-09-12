package fr.mathildeuh.sosstaff.discord;

import fr.mathildeuh.sosstaff.api.event.TicketMessageEvent;
import fr.mathildeuh.sosstaff.lang.LangManager;
import fr.mathildeuh.sosstaff.lang.Message;
import fr.mathildeuh.sosstaff.session.LiveChatSessionManager;
import fr.mathildeuh.sosstaff.session.TicketSession;
import fr.mathildeuh.sosstaff.ticket.Ticket;
import fr.mathildeuh.sosstaff.ticket.TicketMessageRepository;
import fr.mathildeuh.sosstaff.ticket.TicketService;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

/**
 * Relays a staff member's message in a ticket channel back to the player in-game, as long as
 * the player is online and the channel is still tracked as an active session. Webhook messages
 * (the player's own relayed chat bouncing back into the same channel) and other bots are
 * ignored to avoid an echo loop.
 */
public final class DiscordMessageListener extends ListenerAdapter {

    private final JavaPlugin plugin;
    private final LiveChatSessionManager sessionManager;
    private final TicketMessageRepository messageRepository;
    private final TicketService ticketService;
    private final LangManager langManager;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public DiscordMessageListener(JavaPlugin plugin, LiveChatSessionManager sessionManager,
                                   TicketMessageRepository messageRepository, TicketService ticketService,
                                   LangManager langManager) {
        this.plugin = plugin;
        this.sessionManager = sessionManager;
        this.messageRepository = messageRepository;
        this.ticketService = ticketService;
        this.langManager = langManager;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.isFromGuild() || event.isWebhookMessage() || event.getAuthor().isBot()) {
            return;
        }

        sessionManager.findByChannelId(event.getChannel().getId()).ifPresent(session -> {
            String authorName = event.getAuthor().getName();
            String content = event.getMessage().getContentDisplay();

            ticketService.findById(session.ticketId()).thenAccept(ticketOpt -> ticketOpt.ifPresent(ticket ->
                    plugin.getServer().getGlobalRegionScheduler().run(plugin,
                            scheduledTask -> fireAndRelay(ticket, session, authorName, content))));
        });
    }

    private void fireAndRelay(Ticket ticket, TicketSession session, String authorName, String content) {
        TicketMessageEvent event = new TicketMessageEvent(ticket, null, authorName, content, true);
        plugin.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }
        messageRepository.append(ticket.id(), null, authorName, true, content);
        relayToPlayer(session, authorName, content);
    }

    private void relayToPlayer(TicketSession session, String authorName, String content) {
        Player player = plugin.getServer().getPlayer(session.playerUuid());
        if (player == null) {
            return;
        }
        player.getScheduler().run(plugin, scheduledTask -> player.sendMessage(miniMessage.deserialize(
                langManager.get(Message.CHAT_STAFF_REPLY, Map.of("author", authorName, "message", content)))), null);
    }
}
