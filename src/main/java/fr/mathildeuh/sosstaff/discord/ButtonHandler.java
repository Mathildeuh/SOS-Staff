package fr.mathildeuh.sosstaff.discord;

import fr.mathildeuh.sosstaff.config.CategoryConfig;
import fr.mathildeuh.sosstaff.config.ConfigManager;
import fr.mathildeuh.sosstaff.session.LiveChatSessionManager;
import fr.mathildeuh.sosstaff.ticket.Ticket;
import fr.mathildeuh.sosstaff.ticket.TicketMessage;
import fr.mathildeuh.sosstaff.ticket.TicketMessageRepository;
import fr.mathildeuh.sosstaff.ticket.TicketPriority;
import fr.mathildeuh.sosstaff.ticket.TicketService;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
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

    public ButtonHandler(JavaPlugin plugin, TicketService ticketService, ConfigManager configManager,
                          TicketMessageRepository messageRepository, LiveChatSessionManager sessionManager) {
        this.plugin = plugin;
        this.ticketService = ticketService;
        this.configManager = configManager;
        this.messageRepository = messageRepository;
        this.sessionManager = sessionManager;
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
        ticketService.claim(ticketId, staff.getUniqueId()).thenAccept(ticket -> updateEmbed(event, ticket));
    }

    private void handleClose(ButtonInteractionEvent event, long ticketId) {
        event.deferEdit().queue();
        ticketService.close(ticketId, "Closed from Discord by " + event.getUser().getName())
                .thenAccept(ticket -> {
                    sessionManager.closeByTicketId(ticketId);
                    updateEmbed(event, ticket);
                });
    }

    private void handleReopen(ButtonInteractionEvent event, long ticketId) {
        event.deferEdit().queue();
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
        var mentions = configManager.discord().mentions().onCreate();
        String roleMentions = mentions.roleIds().stream().map(id -> "<@&" + id + ">").collect(Collectors.joining(" "));
        event.reply((roleMentions.isBlank() ? "" : roleMentions + " ") + mentions.message()).queue();
    }

    private void updateEmbed(ButtonInteractionEvent event, Ticket ticket) {
        CategoryConfig category = configManager.categories().get(ticket.category());
        String playerName = offlineName(ticket.playerUuid());
        String claimedByName = ticket.claimedBy() == null ? null : offlineName(ticket.claimedBy());
        MessageEmbed embed = EmbedFactory.ticketEmbed(ticket, category, playerName, claimedByName);
        event.getHook().editOriginalEmbeds(embed).queue();
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
