package fr.mathildeuh.sosstaff.command;

import fr.mathildeuh.sosstaff.api.event.TicketCloseEvent;
import fr.mathildeuh.sosstaff.api.event.TicketCreateEvent;
import fr.mathildeuh.sosstaff.config.ConfigManager;
import fr.mathildeuh.sosstaff.discord.ChannelOrchestrator;
import fr.mathildeuh.sosstaff.gui.CreationMenu;
import fr.mathildeuh.sosstaff.lang.LangManager;
import fr.mathildeuh.sosstaff.lang.Message;
import fr.mathildeuh.sosstaff.session.LiveChatListener;
import fr.mathildeuh.sosstaff.session.LiveChatSessionManager;
import fr.mathildeuh.sosstaff.ticket.Ticket;
import fr.mathildeuh.sosstaff.ticket.TicketCreationCoordinator;
import fr.mathildeuh.sosstaff.ticket.TicketCreationResult;
import fr.mathildeuh.sosstaff.ticket.TicketPriority;
import fr.mathildeuh.sosstaff.ticket.TicketService;
import fr.mathildeuh.sosstaff.ticket.TicketStatus;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.paper.util.sender.PlayerSource;
import org.incendo.cloud.paper.util.sender.Source;
import org.incendo.cloud.parser.standard.StringParser;

import java.time.Instant;
import java.util.Map;

public final class PlayerCommands {

    private final JavaPlugin plugin;
    private final TicketService ticketService;
    private final ConfigManager configManager;
    private final LangManager langManager;
    private final LiveChatSessionManager sessionManager;
    private final TicketCreationCoordinator creationCoordinator;
    private final CreationMenu creationMenu;
    private final ChannelOrchestrator channelOrchestrator;
    private final LiveChatListener liveChatListener;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public PlayerCommands(JavaPlugin plugin, TicketService ticketService, ConfigManager configManager,
                           LangManager langManager, LiveChatSessionManager sessionManager,
                           TicketCreationCoordinator creationCoordinator, CreationMenu creationMenu,
                           ChannelOrchestrator channelOrchestrator, LiveChatListener liveChatListener) {
        this.plugin = plugin;
        this.ticketService = ticketService;
        this.configManager = configManager;
        this.langManager = langManager;
        this.sessionManager = sessionManager;
        this.creationCoordinator = creationCoordinator;
        this.creationMenu = creationMenu;
        this.channelOrchestrator = channelOrchestrator;
        this.liveChatListener = liveChatListener;
    }

    public void register(PaperCommandManager<Source> commandManager) {
        String main = configManager.commandMain();
        String[] aliases = configManager.commandAliases().toArray(new String[0]);

        var root = commandManager.commandBuilder(main, aliases).senderType(PlayerSource.class);

        // Bare /ticket opens the creation panel - /ticket status is still there for players who
        // want the raw status/priority readout instead.
        commandManager.command(root.handler(context -> creationMenu.open(context.sender().source())));

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

        // /ticket new opens the category/preset panel; /ticket new <category> stays as a
        // text-mode shortcut for players who'd rather type it (skips the preset step - it
        // always creates with no message). /ticket <reason...> is the fast path: one command,
        // one step, using the first configured category as the default.
        commandManager.command(root.literal("new")
                .handler(context -> creationMenu.open(context.sender().source())));

        commandManager.command(root.literal("new")
                .required("category", StringParser.<Source>stringParser())
                .handler(context -> createTicket(context.sender().source(), context.get("category"), null)));

        commandManager.command(root.required("reason", StringParser.<Source>greedyStringParser())
                .handler(context -> createTicketWithDefaultCategory(context.sender().source(), context.get("reason"))));
    }

    /**
     * The fast path: with no active ticket, this creates one; with an active ticket already
     * open, the text is instead relayed as a reply into it - re-running "the same command" is
     * the natural way for a player to keep talking to staff, not a way to spam new tickets.
     */
    private void createTicketWithDefaultCategory(Player player, String reason) {
        ticketService.findActiveTicket(player.getUniqueId())
                .thenAccept(active -> active.ifPresentOrElse(
                        ticket -> runOnPlayerThread(player, () -> replyToActiveTicket(player, ticket, reason)),
                        () -> createTicket(player, configManager.defaultCategory(), reason)))
                .exceptionally(throwable -> logFailure(player, "reply to your ticket", throwable));
    }

    private void replyToActiveTicket(Player player, Ticket ticket, String content) {
        liveChatListener.relayPlayerReply(ticket, player, content);
        send(player, Message.TICKET_REPLY_SENT, Map.of("id", String.valueOf(ticket.id())));
    }

    private void createTicket(Player player, String category, String initialMessage) {
        if (!configManager.categories().containsKey(category)) {
            send(player, Message.TICKET_CREATE_UNKNOWN_CATEGORY, Map.of("category", category));
            return;
        }

        if (!announceCreation(player, category)) {
            send(player, Message.TICKET_CREATE_REJECTED_BY_PLUGIN, Map.of());
            return;
        }

        boolean bypass = player.hasPermission(configManager.antiSpamBypassPermission());
        creationCoordinator.create(player.getUniqueId(), player.getName(), category, TicketPriority.MEDIUM, bypass, initialMessage)
                .thenAccept(result -> runOnPlayerThread(player, () -> handleCreationResult(player, result)))
                .exceptionally(throwable -> logFailure(player, "create a ticket", throwable));
    }

    /**
     * Fires {@link TicketCreateEvent} with a transient preview of the ticket about to be
     * created, so another plugin can veto the request before it ever reaches the database.
     * Returns {@code false} if a listener cancelled it.
     */
    private boolean announceCreation(Player player, String category) {
        Ticket preview = new Ticket(0, player.getUniqueId(), category, TicketStatus.OPEN,
                TicketPriority.MEDIUM, null, null, Instant.now(), null, null, null);
        TicketCreateEvent event = new TicketCreateEvent(preview);
        plugin.getServer().getPluginManager().callEvent(event);
        return !event.isCancelled();
    }

    private void handleCreationResult(Player player, TicketCreationResult result) {
        switch (result) {
            case TicketCreationResult.Created created ->
                    send(player, Message.TICKET_CREATE_SUCCESS, Map.of("id", String.valueOf(created.ticket().id())));
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
                .thenAccept(active -> {
                    if (active.isEmpty()) {
                        runOnPlayerThread(player, () -> send(player, noneActiveMessage, Map.of()));
                        return;
                    }
                    String effectiveReason = reason.isBlank() ? "Closed by the player" : reason;
                    player.getScheduler().run(plugin,
                            scheduledTask -> closeIfNotCancelled(player, active.get(), effectiveReason, successMessage), null);
                })
                .exceptionally(throwable -> logFailure(player, "close your ticket", throwable));
    }

    private void closeIfNotCancelled(Player player, Ticket ticket, String reason, Message successMessage) {
        TicketCloseEvent event = new TicketCloseEvent(ticket, reason);
        plugin.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }
        ticketService.close(ticket.id(), reason).thenAccept(closed -> {
            sessionManager.closeByTicketId(closed.id());
            channelOrchestrator.handleTicketClosed(closed);
            runOnPlayerThread(player, () -> sendActionBar(player, successMessage, Map.of("id", String.valueOf(closed.id()))));
        }).exceptionally(throwable -> logFailure(player, "close your ticket", throwable));
    }

    private void sendStatus(Player player) {
        ticketService.findActiveTicket(player.getUniqueId())
                .thenAccept(active -> runOnPlayerThread(player, () -> {
                    if (active.isEmpty()) {
                        send(player, Message.TICKET_STATUS_NONE, Map.of());
                        return;
                    }
                    Ticket ticket = active.get();
                    send(player, Message.TICKET_STATUS_ACTIVE, Map.of(
                            "id", String.valueOf(ticket.id()),
                            "status", ticket.status().label(),
                            "priority", ticket.priority().label()));
                }))
                .exceptionally(throwable -> logFailure(player, "read your ticket status", throwable));
    }

    private void sendHistory(Player player) {
        ticketService.findHistory(player.getUniqueId())
                .thenAccept(history -> runOnPlayerThread(player, () -> {
                    if (history.isEmpty()) {
                        send(player, Message.TICKET_LIST_EMPTY, Map.of());
                        return;
                    }
                    for (Ticket ticket : history) {
                        send(player, Message.TICKET_LIST_ENTRY, Map.of(
                                "id", String.valueOf(ticket.id()),
                                "category", ticket.category(),
                                "status", ticket.status().label()));
                    }
                }))
                .exceptionally(throwable -> logFailure(player, "read your ticket history", throwable));
    }

    private Void logFailure(Player player, String action, Throwable throwable) {
        plugin.getLogger().severe("Failed to " + action + " for " + player.getUniqueId() + ": " + throwable);
        runOnPlayerThread(player, () -> send(player, Message.GENERAL_ERROR_GENERIC, Map.of("error", String.valueOf(throwable.getMessage()))));
        return null;
    }

    private void runOnPlayerThread(Player player, Runnable runnable) {
        player.getScheduler().run(plugin, scheduledTask -> runnable.run(), null);
    }

    private void send(Player player, Message message, Map<String, String> placeholders) {
        player.sendMessage(miniMessage.deserialize(langManager.get(message, placeholders)));
    }

    /**
     * A ticket being closed/cancelled is transient feedback for an action the player just took
     * themselves, so it goes to the action bar. Ticket creation stays in chat instead (see
     * handleCreationResult) since it's the one confirmation the player may want to scroll back to.
     */
    private void sendActionBar(Player player, Message message, Map<String, String> placeholders) {
        player.sendActionBar(miniMessage.deserialize(langManager.get(message, placeholders)));
    }
}
