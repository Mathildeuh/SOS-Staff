package fr.mathildeuh.sosstaff.discord;

import fr.mathildeuh.sosstaff.config.ConfigManager;
import fr.mathildeuh.sosstaff.ticket.Ticket;
import fr.mathildeuh.sosstaff.ticket.TicketService;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Periodically pings the staff roles again in any ticket channel whose ticket has gone
 * unclaimed past escalation.no-claim-after-minutes. Each ticket is only pinged once - the
 * in-memory alreadyEscalated set is intentionally not persisted, since it is only there to
 * avoid re-pinging every check interval, not to survive a restart.
 */
public final class EscalationScheduler {

    private static final long CHECK_INTERVAL_MINUTES = 1;

    private final JavaPlugin plugin;
    private final TicketService ticketService;
    private final ConfigManager configManager;
    private final DiscordGateway gateway;
    private final Logger logger;
    private final Set<Long> alreadyEscalated = ConcurrentHashMap.newKeySet();

    public EscalationScheduler(JavaPlugin plugin, TicketService ticketService, ConfigManager configManager,
                               DiscordGateway gateway, Logger logger) {
        this.plugin = plugin;
        this.ticketService = ticketService;
        this.configManager = configManager;
        this.gateway = gateway;
        this.logger = logger;
    }

    public void start() {
        Bukkit.getAsyncScheduler().runAtFixedRate(plugin, ignored -> checkOnce(),
                CHECK_INTERVAL_MINUTES, CHECK_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }

    public void onTicketNoLongerPending(long ticketId) {
        alreadyEscalated.remove(ticketId);
    }

    private void checkOnce() {
        ticketService.findTicketsNeedingEscalation().thenAccept(tickets -> tickets.stream()
                .filter(ticket -> alreadyEscalated.add(ticket.id()))
                .forEach(this::escalate))
                .exceptionally(throwable -> {
                    logger.warning("Escalation check failed: " + throwable);
                    return null;
                });
    }

    private void escalate(Ticket ticket) {
        if (ticket.discordChannelId() == null) {
            return;
        }
        gateway.jda().map(jda -> jda.getTextChannelById(ticket.discordChannelId()))
                .ifPresent(channel -> sendEscalationPing(channel, ticket));
    }

    private void sendEscalationPing(TextChannel channel, Ticket ticket) {
        var permissions = configManager.discord().permissions();
        var mentions = configManager.discord().mentions();
        String roleMentions = permissions.staffRoleIds().stream().map(id -> "<@&" + id + ">").collect(Collectors.joining(" "));
        long minutes = configManager.noClaimAfterMinutes();
        String message = mentions.onEscalate().message()
                .replace("%id%", String.valueOf(ticket.id()))
                .replace("%minutes%", String.valueOf(minutes));

        channel.sendMessage((roleMentions.isBlank() ? "" : roleMentions + " ") + message).queue(
                ignored -> { }, throwable -> logger.warning("Failed to send escalation ping for ticket #" + ticket.id() + ": " + throwable));
    }
}
