package fr.mathildeuh.sosstaff.discord;

import fr.mathildeuh.sosstaff.api.event.TicketClaimEvent;
import fr.mathildeuh.sosstaff.api.event.TicketCloseEvent;
import fr.mathildeuh.sosstaff.config.CategoryConfig;
import fr.mathildeuh.sosstaff.config.ConfigManager;
import fr.mathildeuh.sosstaff.lang.LangManager;
import fr.mathildeuh.sosstaff.lang.Message;
import fr.mathildeuh.sosstaff.session.LiveChatSessionManager;
import fr.mathildeuh.sosstaff.session.TicketSession;
import fr.mathildeuh.sosstaff.ticket.Ticket;
import fr.mathildeuh.sosstaff.ticket.TicketMessage;
import fr.mathildeuh.sosstaff.ticket.TicketMessageRepository;
import fr.mathildeuh.sosstaff.ticket.TicketPriority;
import fr.mathildeuh.sosstaff.ticket.TicketService;
import fr.mathildeuh.sosstaff.ticket.TicketStatus;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Handles the fixed ticket-management buttons (claim/close/reopen/priority/transcript/ping).
 * Configurable action-buttons from config.yml are ActionButtonHandler's job instead.
 */
public final class ButtonHandler {

    private final JavaPlugin plugin;
    private final TicketService ticketService;
    private final ConfigManager configManager;
    private final TicketMessageRepository messageRepository;
    private final LiveChatSessionManager sessionManager;
    private final EscalationScheduler escalationScheduler;
    private final LangManager langManager;
    private final ChannelOrchestrator channelOrchestrator;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public ButtonHandler(JavaPlugin plugin, TicketService ticketService, ConfigManager configManager,
                          TicketMessageRepository messageRepository, LiveChatSessionManager sessionManager,
                          EscalationScheduler escalationScheduler, LangManager langManager,
                          ChannelOrchestrator channelOrchestrator) {
        this.plugin = plugin;
        this.ticketService = ticketService;
        this.configManager = configManager;
        this.messageRepository = messageRepository;
        this.sessionManager = sessionManager;
        this.langManager = langManager;
        this.escalationScheduler = escalationScheduler;
        this.channelOrchestrator = channelOrchestrator;
    }

    public void handle(ButtonInteractionEvent event, String action, long ticketId) {
        switch (action) {
            case "claim" -> handleClaim(event, ticketId);
            case "close" -> handleClose(event, ticketId);
            case "reopen" -> handleReopen(event, ticketId);
            case "priority" -> handlePriorityCycle(event, ticketId);
            case "transcript" -> handleTranscript(event, ticketId);
            case "ping" -> handlePing(event, ticketId);
            default -> event.reply("Unknown ticket action: " + action).setEphemeral(true).queue();
        }
    }

    private void handleClaim(ButtonInteractionEvent event, long ticketId) {
        event.deferEdit().queue();
        String discordUserId = event.getUser().getId();
        String staffName = event.getMember() != null ? event.getMember().getEffectiveName() : event.getUser().getName();
        ticketService.findById(ticketId).thenAccept(ticketOpt -> ticketOpt.ifPresent(ticket ->
                plugin.getServer().getGlobalRegionScheduler().run(plugin,
                        scheduledTask -> claimIfNotCancelled(event, ticket, discordUserId, staffName))));
    }

    private void claimIfNotCancelled(ButtonInteractionEvent event, Ticket ticket, String discordUserId, String staffName) {
        TicketClaimEvent claimEvent = new TicketClaimEvent(ticket, discordUserId);
        plugin.getServer().getPluginManager().callEvent(claimEvent);
        if (claimEvent.isCancelled()) {
            return;
        }
        ticketService.claim(ticket.id(), discordUserId).thenAccept(claimed -> {
            notifyPlayerOfClaim(claimed, staffName);
            updateEmbed(event, claimed);
        });
    }

    private void notifyPlayerOfClaim(Ticket ticket, String staffName) {
        Player player = plugin.getServer().getPlayer(ticket.playerUuid());
        if (player == null) {
            return;
        }
        player.getScheduler().run(plugin, scheduledTask -> player.sendMessage(miniMessage.deserialize(
                langManager.get(Message.TICKET_CLAIM_NOTIFY, Map.of("id", String.valueOf(ticket.id()), "staff", staffName)))), null);
    }

    private void handleClose(ButtonInteractionEvent event, long ticketId) {
        event.deferEdit().queue();
        String reason = "Closed from Discord by " + event.getUser().getName();
        ticketService.findById(ticketId).thenAccept(ticketOpt -> ticketOpt.ifPresent(ticket ->
                plugin.getServer().getGlobalRegionScheduler().run(plugin, scheduledTask -> closeIfNotCancelled(event, ticket, reason))));
    }

    private void closeIfNotCancelled(ButtonInteractionEvent event, Ticket ticket, String reason) {
        TicketCloseEvent closeEvent = new TicketCloseEvent(ticket, reason);
        plugin.getServer().getPluginManager().callEvent(closeEvent);
        if (closeEvent.isCancelled()) {
            return;
        }
        ticketService.close(ticket.id(), reason).thenAccept(closed -> {
            sessionManager.closeByTicketId(closed.id());
            // Update the embed first: on-close may delete or move this very channel, and doing it
            // before the edit risks the edit landing on a channel that's already gone.
            updateEmbed(event, closed);
            channelOrchestrator.handleTicketClosed(closed);
        });
    }

    private void handleReopen(ButtonInteractionEvent event, long ticketId) {
        event.deferEdit().queue();
        escalationScheduler.onTicketNoLongerPending(ticketId);
        ticketService.reopen(ticketId).thenAccept(ticket -> {
            reopenLiveChatSession(ticket);
            notifyPlayerOfReopen(ticket);
            updateEmbed(event, ticket);
        });
    }

    /**
     * Closing a ticket tears down its live-chat session (see PlayerCommands/ButtonHandler
     * close handling), so reopening it from Discord has to recreate that session - otherwise
     * the player's chat would stay disconnected from a ticket that looks open again.
     */
    private void reopenLiveChatSession(Ticket ticket) {
        if (ticket.discordChannelId() != null) {
            sessionManager.open(new TicketSession(ticket.playerUuid(), ticket.id(), ticket.discordChannelId()));
        }
    }

    private void notifyPlayerOfReopen(Ticket ticket) {
        Player player = plugin.getServer().getPlayer(ticket.playerUuid());
        if (player == null) {
            return;
        }
        player.getScheduler().run(plugin, scheduledTask -> player.sendActionBar(miniMessage.deserialize(
                langManager.get(Message.TICKET_REOPEN_NOTIFY, Map.of("id", String.valueOf(ticket.id()))))), null);
    }

    private void handlePriorityCycle(ButtonInteractionEvent event, long ticketId) {
        event.deferEdit().queue();
        ticketService.findById(ticketId).thenAccept(ticketOpt -> ticketOpt.ifPresent(ticket -> {
            TicketPriority next = nextPriority(ticket.priority());
            ticketService.updatePriority(ticketId, next).thenAccept(updated -> {
                notifyPlayerOfPriorityChange(updated);
                updateEmbed(event, updated);
            });
        }));
    }

    private void notifyPlayerOfPriorityChange(Ticket ticket) {
        Player player = plugin.getServer().getPlayer(ticket.playerUuid());
        if (player == null) {
            return;
        }
        player.getScheduler().run(plugin, scheduledTask -> player.sendMessage(miniMessage.deserialize(
                langManager.get(Message.TICKET_PRIORITY_NOTIFY,
                        Map.of("id", String.valueOf(ticket.id()), "priority", ticket.priority().label())))), null);
    }

    private void handleTranscript(ButtonInteractionEvent event, long ticketId) {
        event.deferReply(true).queue();
        messageRepository.findByTicket(ticketId).thenAccept(messages ->
                event.getHook().editOriginal(formatTranscript(messages)).queue());
    }

    private void handlePing(ButtonInteractionEvent event, long ticketId) {
        var staffRoles = configManager.discord().permissions().staffRoleIds();
        String roleMentions = staffRoles.stream().map(id -> "<@&" + id + ">").collect(Collectors.joining(" "));
        String message = configManager.discord().mentions().onEscalate().message()
                .replace("%id%", String.valueOf(ticketId))
                .replace("%minutes%", String.valueOf(configManager.noClaimAfterMinutes()));
        event.reply((roleMentions.isBlank() ? "" : roleMentions + " ") + message).queue();
    }

    private void updateEmbed(ButtonInteractionEvent event, Ticket ticket) {
        CategoryConfig category = configManager.categories().get(ticket.category());
        String playerName = offlineName(ticket.playerUuid());
        MessageEmbed embed = EmbedFactory.ticketEmbed(ticket, category, playerName);

        boolean closed = ticket.status() == TicketStatus.CLOSED || ticket.status() == TicketStatus.ARCHIVED;
        List<ActionRow> rows = new ArrayList<>();
        if (closed) {
            rows.add(EmbedFactory.reopenRow(ticket.id()));
        } else {
            rows.add(EmbedFactory.managementRow(ticket.id()));
            boolean targetOnline = plugin.getServer().getPlayer(ticket.playerUuid()) != null;
            rows.addAll(EmbedFactory.actionButtonRows(configManager.discord().actionButtons(), ticket.id(), targetOnline));
        }

        event.getHook().editOriginalEmbeds(embed).setComponents(rows).queue();
    }

    private String offlineName(UUID uuid) {
        OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(uuid);
        String name = offlinePlayer.getName();
        return name != null ? name : uuid.toString();
    }

    private static TicketPriority nextPriority(TicketPriority current) {
        TicketPriority[] values = TicketPriority.values();
        return values[(current.ordinal() + 1) % values.length];
    }

    private static String formatTranscript(List<TicketMessage> messages) {
        if (messages.isEmpty()) {
            return "No messages recorded for this ticket yet.";
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneOffset.UTC);
        String body = messages.stream()
                .map(m -> "[" + formatter.format(m.sentAt()) + "] " + m.authorName() + ": " + m.content())
                .collect(Collectors.joining("\n"));
        return body.length() > 1900 ? body.substring(body.length() - 1900) : body;
    }
}
