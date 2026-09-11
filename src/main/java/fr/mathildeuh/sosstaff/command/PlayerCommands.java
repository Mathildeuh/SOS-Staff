package fr.mathildeuh.sosstaff.command;

import fr.mathildeuh.sosstaff.config.ConfigManager;
import fr.mathildeuh.sosstaff.discord.ChannelOrchestrator;
import fr.mathildeuh.sosstaff.lang.LangManager;
import fr.mathildeuh.sosstaff.lang.Message;
import fr.mathildeuh.sosstaff.ticket.Ticket;
import fr.mathildeuh.sosstaff.ticket.TicketCreationResult;
import fr.mathildeuh.sosstaff.ticket.TicketPriority;
import fr.mathildeuh.sosstaff.ticket.TicketService;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.paper.util.sender.PaperSimpleSenderMapper;
import org.incendo.cloud.paper.util.sender.PlayerSource;
import org.incendo.cloud.paper.util.sender.Source;
import org.incendo.cloud.parser.standard.StringParser;

import java.util.List;
import java.util.Map;

public final class PlayerCommands {

    private final JavaPlugin plugin;
    private final TicketService ticketService;
    private final ConfigManager configManager;
    private final LangManager langManager;
    private final ChannelOrchestrator channelOrchestrator;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public PlayerCommands(JavaPlugin plugin, TicketService ticketService, ConfigManager configManager,
                           LangManager langManager, ChannelOrchestrator channelOrchestrator) {
        this.plugin = plugin;
        this.ticketService = ticketService;
        this.configManager = configManager;
        this.langManager = langManager;
        this.channelOrchestrator = channelOrchestrator;
    }

    public void register() {
        PaperCommandManager<Source> commandManager = PaperCommandManager
                .builder(PaperSimpleSenderMapper.simpleSenderMapper())
                .executionCoordinator(ExecutionCoordinator.<Source>simpleCoordinator())
                .buildOnEnable(plugin);

        String main = configManager.commandMain();
        String[] aliases = configManager.commandAliases().toArray(new String[0]);

        var root = commandManager.commandBuilder(main, aliases).senderType(PlayerSource.class);

        commandManager.command(root.handler(context -> sendStatus(context.sender().source())));

        commandManager.command(root.literal("status")
                .handler(context -> sendStatus(context.sender().source())));

        commandManager.command(root.literal("list")
                .handler(context -> sendHistory(context.sender().source())));

        commandManager.command(root.literal("cancel")
                .handler(context -> closeOwnActiveTicket(
                        context.sender().source(), "", Message.TICKET_CANCEL_NONE_ACTIVE, Message.TICKET_CANCEL_SUCCESS)));

        commandManager.command(root.literal("close")
                .optional("reason", StringParser.<Source>greedyStringParser())
                .handler(context -> closeOwnActiveTicket(
                        context.sender().source(),
                        context.getOrDefault("reason", ""),
                        Message.TICKET_CLOSE_NONE_ACTIVE,
                        Message.TICKET_CLOSE_SUCCESS)));

        // No free-text "message" argument yet: there is nowhere to persist it until the
        // ticket_messages-backed chat system lands, so the command only takes a category
        // for now and the initial description is entered through live chat once claimed.
        commandManager.command(root.literal("new")
                .required("category", StringParser.<Source>stringParser())
                .handler(context -> createTicket(
                        context.sender().source(),
                        context.get("category"))));
    }

    private void createTicket(Player player, String category) {
        if (!configManager.categories().containsKey(category)) {
            send(player, Message.TICKET_CREATE_UNKNOWN_CATEGORY, Map.of("category", category));
            return;
        }

        boolean bypass = player.hasPermission(configManager.antiSpamBypassPermission());
        ticketService.createTicket(player.getUniqueId(), category, TicketPriority.MEDIUM, bypass)
                .thenAccept(result -> runOnMainThread(() -> handleCreationResult(player, result)))
                .exceptionally(throwable -> logFailure(player, "create a ticket", throwable));
    }

    private void handleCreationResult(Player player, TicketCreationResult result) {
        switch (result) {
            case TicketCreationResult.Created created -> {
                send(player, Message.TICKET_CREATE_SUCCESS, Map.of("id", String.valueOf(created.ticket().id())));
                channelOrchestrator.createChannelForTicket(created.ticket(), player.getName())
                        .thenAccept(channelId -> channelId.ifPresent(id ->
                                ticketService.setDiscordChannelId(created.ticket().id(), id)));
            }
            case TicketCreationResult.RejectedTooManyOpenTickets rejected ->
                    send(player, Message.TICKET_CREATE_REJECTED_TOO_MANY_OPEN,
                            Map.of("id", String.valueOf(rejected.existingTicket().id())));
            case TicketCreationResult.RejectedCooldownActive rejected ->
                    send(player, Message.TICKET_CREATE_REJECTED_COOLDOWN,
                            Map.of("seconds", String.valueOf(rejected.remaining().toSeconds())));
        }
    }

    private void closeOwnActiveTicket(Player player, String reason, Message noneActiveMessage, Message successMessage) {
        ticketService.findActiveTicket(player.getUniqueId())
                .thenCompose(active -> {
                    if (active.isEmpty()) {
                        runOnMainThread(() -> send(player, noneActiveMessage, Map.of()));
                        return java.util.concurrent.CompletableFuture.completedFuture(null);
                    }
                    String effectiveReason = reason.isBlank() ? "Closed by the player" : reason;
                    return ticketService.close(active.get().id(), effectiveReason).thenAccept(closed ->
                            runOnMainThread(() -> send(player, successMessage, Map.of("id", String.valueOf(closed.id())))));
                })
                .exceptionally(throwable -> logFailure(player, "close your ticket", throwable));
    }

    private void sendStatus(Player player) {
        ticketService.findActiveTicket(player.getUniqueId())
                .thenAccept(active -> runOnMainThread(() -> {
                    if (active.isEmpty()) {
                        send(player, Message.TICKET_STATUS_NONE, Map.of());
                        return;
                    }
                    Ticket ticket = active.get();
                    send(player, Message.TICKET_STATUS_ACTIVE, Map.of(
                            "id", String.valueOf(ticket.id()),
                            "status", ticket.status().name(),
                            "priority", ticket.priority().name()));
                }))
                .exceptionally(throwable -> logFailure(player, "read your ticket status", throwable));
    }

    private void sendHistory(Player player) {
        ticketService.findHistory(player.getUniqueId())
                .thenAccept(history -> runOnMainThread(() -> {
                    if (history.isEmpty()) {
                        send(player, Message.TICKET_LIST_EMPTY, Map.of());
                        return;
                    }
                    for (Ticket ticket : history) {
                        send(player, Message.TICKET_LIST_ENTRY, Map.of(
                                "id", String.valueOf(ticket.id()),
                                "category", ticket.category(),
                                "status", ticket.status().name()));
                    }
                }))
                .exceptionally(throwable -> logFailure(player, "read your ticket history", throwable));
    }

    private Void logFailure(Player player, String action, Throwable throwable) {
        plugin.getLogger().severe("Failed to " + action + " for " + player.getUniqueId() + ": " + throwable);
        runOnMainThread(() -> send(player, Message.GENERAL_ERROR_GENERIC, Map.of("error", String.valueOf(throwable.getMessage()))));
        return null;
    }

    private void runOnMainThread(Runnable runnable) {
        plugin.getServer().getScheduler().runTask(plugin, runnable);
    }

    private void send(Player player, Message message, Map<String, String> placeholders) {
        player.sendMessage(miniMessage.deserialize(langManager.get(message, placeholders)));
    }
}
