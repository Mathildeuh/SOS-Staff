package fr.mathildeuh.sosstaff.integration;

import fr.mathildeuh.sosstaff.ticket.Ticket;
import fr.mathildeuh.sosstaff.ticket.TicketService;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Optional;

/**
 * Exposes SOS-Staff's ticket state to PlaceholderAPI as {@code %sosstaff_<placeholder>%}:
 * {@code active_id}, {@code active_status}, {@code active_priority} and {@code ticket_count}.
 * Backed entirely by {@link TicketService}'s synchronous cache, so resolving a placeholder never
 * blocks on the database. Only constructed and registered when PlaceholderAPI is actually
 * installed - see the presence check in {@code SosStaffPlugin.onEnable}, which is what keeps
 * this a genuine soft depend rather than a hard runtime requirement.
 */
public final class PlaceholderApiHook extends PlaceholderExpansion {

    private final JavaPlugin plugin;
    private final TicketService ticketService;

    public PlaceholderApiHook(JavaPlugin plugin, TicketService ticketService) {
        this.plugin = plugin;
        this.ticketService = ticketService;
    }

    @Override
    public String getIdentifier() {
        return "sosstaff";
    }

    @Override
    public String getAuthor() {
        return String.join(", ", plugin.getPluginMeta().getAuthors());
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) {
            return "";
        }
        Optional<Ticket> active = ticketService.peekActiveTicket(player.getUniqueId());
        return switch (params.toLowerCase(Locale.ROOT)) {
            case "active_id" -> active.map(ticket -> String.valueOf(ticket.id())).orElse("");
            case "active_status" -> active.map(ticket -> ticket.status().name()).orElse("");
            case "active_priority" -> active.map(ticket -> ticket.priority().name()).orElse("");
            case "ticket_count" -> String.valueOf(ticketService.peekHistory(player.getUniqueId()).size());
            default -> null;
        };
    }
}
