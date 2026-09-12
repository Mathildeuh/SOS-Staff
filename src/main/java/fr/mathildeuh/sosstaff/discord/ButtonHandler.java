package fr.mathildeuh.sosstaff.discord;

import fr.mathildeuh.sosstaff.api.event.TicketClaimEvent;
import fr.mathildeuh.sosstaff.api.event.TicketCloseEvent;
import fr.mathildeuh.sosstaff.config.CategoryConfig;
import fr.mathildeuh.sosstaff.config.ConfigManager;
import fr.mathildeuh.sosstaff.session.LiveChatSessionManager;
import fr.mathildeuh.sosstaff.ticket.Ticket;
import fr.mathildeuh.sosstaff.ticket.TicketMessage;
import fr.mathildeuh.sosstaff.ticket.TicketMessageRepository;
import fr.mathildeuh.sosstaff.ticket.TicketPriority;
import fr.mathildeuh.sosstaff.ticket.TicketService;
import fr.mathildeuh.sosstaff.ticket.TicketStatus;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
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

    public ButtonHandler(JavaPlugin plugin, TicketService ticketService, ConfigManager configManager,
                          TicketMessageRepository messageRepository, LiveChatSessionManager sessionManager,
                          EscalationScheduler escalationScheduler) {
        this.plugin = plugin;
        this.ticketService = ticketService;
        this.configManager = configManager;
        this.messageRepository = messageRepository;
        this.sessionManager = sessionManager;
        this.escalationScheduler = escalationScheduler;
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
        Player staff = StaffResolver.resolveByDiscordName(plugin, event.getUser().getName());
        if (staff == null) {
            replyNoStaffMatch(event);
            return;
        }
        event.deferEdit().queue();
        ticketService.findById(ticketId).thenAccept(ticketOpt -> ticketOpt.ifPresent(ticket ->
                staff.getScheduler().run(plugin, scheduledTask -> claimIfNotCancelled(event, ticket, staff), null)));
    }

    private void claimIfNotCancelled(ButtonInteractionEvent event, Ticket ticket, Player staff) {
        TicketClaimEvent claimEvent = new TicketClaimEvent(ticket, staff.getUniqueId());
        plugin.getServer().getPluginManager().callEvent(claimEvent);
        if (claimEvent.isCancelled()) {
            return;
        }
        ticketService.claim(ticket.id(), staff.getUniqueId()).thenAccept(claimed -> updateEmbed(event, claimed));
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
            updateEmbed(event, closed);
        });
    }

    private void handleReopen(ButtonInteractionEvent event, long ticketId) {
        event.deferEdit().queue();
        escalationScheduler.onTicketNoLongerPending(ticketId);
        ticketService.reopen(ticketId).thenAccept(ticket -> updateEmbed(event, ticket));
    }

    private void handlePriorityCycle(ButtonInteractionEvent event, long ticketId) {
        event.deferEdit().queue();
        ticketService.findById(ticketId).thenAccept(ticketOpt -> ticketOpt.ifPresent(ticket -> {
            TicketPriority next = nextPriority(ticket.priority());
            ticketService.updatePriority(ticketId, next).thenAccept(updated -> updateEmbed(event, updated));
        }));
    }

    private void handleTranscript(ButtonInteractionEvent event, long ticketId) {
        event.deferReply(true).queue();
        messageRepository.findByTicket(ticketId).thenAccept(messages ->
                event.getHook().editOriginal(formatTranscript(messages)).queue());
    }

    private void handlePing(ButtonInteractionEvent event, long ticketId) {
        var staffRoles = configManager.discord().permissions().staffRoleIds();
        String roleMentions = staffRoles.stream().map(id -> "<@&" + id + ">").collect(Collectors.joining(" "));
        String message = configManager.discord().mentions().onEscalate().message().replace("%id%", String.valueOf(ticketId));
        event.reply((roleMentions.isBlank() ? "" : roleMentions + " ") + message).queue();
    }

    private void updateEmbed(ButtonInteractionEvent event, Ticket ticket) {
        CategoryConfig category = configManager.categories().get(ticket.category());
        String playerName = offlineName(ticket.playerUuid());
        String claimedByName = ticket.claimedBy() == null ? null : offlineName(ticket.claimedBy());
        MessageEmbed embed = EmbedFactory.ticketEmbed(ticket, category, playerName, claimedByName);

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

    private void replyNoStaffMatch(ButtonInteractionEvent event) {
        event.reply("No online Minecraft player matches your Discord name '" + event.getUser().getName()
                        + "'. This action needs your Discord display name to match your Minecraft username.")
                .setEphemeral(true).queue();
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
