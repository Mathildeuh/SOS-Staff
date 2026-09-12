package fr.mathildeuh.sosstaff.gui;

import fr.mathildeuh.sosstaff.config.CategoryConfig;
import fr.mathildeuh.sosstaff.config.ConfigManager;
import fr.mathildeuh.sosstaff.lang.LangManager;
import fr.mathildeuh.sosstaff.lang.Message;
import fr.mathildeuh.sosstaff.session.LiveChatSessionManager;
import fr.mathildeuh.sosstaff.session.TicketSession;
import fr.mathildeuh.sosstaff.ticket.Ticket;
import fr.mathildeuh.sosstaff.ticket.TicketService;
import fr.mathildeuh.sosstaff.ticket.TicketStatus;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * /sostaff panel: a paginated, filterable list of every ticket, with a player-head icon per
 * ticket and a colored-wool filter row (the DA calls for both). Clicking a ticket teleports the
 * clicking staff member to its player (if online) and attaches their in-game chat to it, so
 * whatever they type next also relays into that ticket's Discord channel.
 */
public final class AdminPanel {

    private static final int PAGE_SIZE = 45;
    private static final int PREV_PAGE_SLOT = 45;
    private static final int NEXT_PAGE_SLOT = 53;

    private static final List<Optional<TicketStatus>> FILTERS = List.of(
            Optional.empty(), Optional.of(TicketStatus.OPEN), Optional.of(TicketStatus.CLAIMED),
            Optional.of(TicketStatus.WAITING_PLAYER), Optional.of(TicketStatus.WAITING_STAFF),
            Optional.of(TicketStatus.CLOSED), Optional.of(TicketStatus.ARCHIVED));

    private final JavaPlugin plugin;
    private final TicketService ticketService;
    private final ConfigManager configManager;
    private final LangManager langManager;
    private final LiveChatSessionManager sessionManager;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public AdminPanel(JavaPlugin plugin, TicketService ticketService, ConfigManager configManager,
                       LangManager langManager, LiveChatSessionManager sessionManager) {
        this.plugin = plugin;
        this.ticketService = ticketService;
        this.configManager = configManager;
        this.langManager = langManager;
        this.sessionManager = sessionManager;
    }

    public void open(Player staff, int page, Optional<TicketStatus> filterStatus) {
        CompletableFuture<Integer> countFuture = ticketService.countAll(filterStatus);
        CompletableFuture<List<Ticket>> ticketsFuture = ticketService.findPage(filterStatus, page, PAGE_SIZE);

        countFuture.thenCombine(ticketsFuture, Map::entry)
                .thenAccept(data -> staff.getScheduler().run(plugin, scheduledTask -> render(staff, page, filterStatus, data), null))
                .exceptionally(throwable -> {
                    plugin.getLogger().severe("Failed to load the admin panel: " + throwable);
                    return null;
                });
    }

    private void render(Player staff, int page, Optional<TicketStatus> filterStatus, Map.Entry<Integer, List<Ticket>> data) {
        List<Ticket> tickets = data.getValue();
        int totalCount = data.getKey();
        boolean hasNextPage = (long) (page + 1) * PAGE_SIZE < totalCount;

        Map<Integer, Long> slotToTicketId = new LinkedHashMap<>();
        for (int i = 0; i < tickets.size(); i++) {
            slotToTicketId.put(i, tickets.get(i).id());
        }

        Map<Integer, TicketStatus> slotToFilter = new LinkedHashMap<>();
        int filterSlot = 46;
        for (Optional<TicketStatus> filter : FILTERS) {
            slotToFilter.put(filterSlot++, filter.orElse(null));
        }

        AdminPanelHolder holder = new AdminPanelHolder(page, filterStatus, slotToTicketId, slotToFilter, PREV_PAGE_SLOT, NEXT_PAGE_SLOT);
        Inventory inventory = Bukkit.createInventory(holder, 54, title());
        holder.setInventory(inventory);

        for (Map.Entry<Integer, Long> entry : slotToTicketId.entrySet()) {
            inventory.setItem(entry.getKey(), ticketItem(tickets.get(entry.getKey())));
        }
        if (page > 0) {
            inventory.setItem(PREV_PAGE_SLOT, arrowItem("<- Previous page"));
        }
        if (hasNextPage) {
            inventory.setItem(NEXT_PAGE_SLOT, arrowItem("Next page ->"));
        }
        for (Map.Entry<Integer, TicketStatus> entry : slotToFilter.entrySet()) {
            inventory.setItem(entry.getKey(), filterItem(entry.getValue(), filterStatus));
        }

        staff.openInventory(inventory);
    }

    void onTicketClicked(Player staff, long ticketId) {
        ticketService.findById(ticketId).thenAccept(ticketOpt -> ticketOpt.ifPresent(ticket ->
                staff.getScheduler().run(plugin, scheduledTask -> attachAndTeleport(staff, ticket), null)));
    }

    private void attachAndTeleport(Player staff, Ticket ticket) {
        if (ticket.discordChannelId() != null) {
            sessionManager.attachStaff(staff.getUniqueId(), new TicketSession(staff.getUniqueId(), ticket.id(), ticket.discordChannelId()));
        }

        UUID playerUuid = ticket.playerUuid();
        Player target = Bukkit.getPlayer(playerUuid);
        if (target != null) {
            staff.getScheduler().run(plugin, ignored -> staff.teleport(target.getLocation()), null);
        }

        staff.closeInventory();
        staff.sendMessage(miniMessage.deserialize(langManager.get(Message.ADMIN_ATTACHED_TO_TICKET, Map.of("id", String.valueOf(ticket.id())))));
    }

    private ItemStack ticketItem(Ticket ticket) {
        OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(ticket.playerUuid());
        String playerName = offlinePlayer.getName() != null ? offlinePlayer.getName() : ticket.playerUuid().toString();

        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(offlinePlayer);
        meta.displayName(Component.text("#" + ticket.id() + " " + playerName));

        CategoryConfig category = configManager.categories().get(ticket.category());
        String categoryName = category != null ? category.displayName() : ticket.category();
        String lore = langManager.get(Message.GUI_ADMIN_PANEL_TICKET_LORE, Map.of(
                "category", categoryName, "status", ticket.status().name(), "priority", ticket.priority().name()));
        meta.lore(List.of(miniMessage.deserialize(lore)));

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack filterItem(TicketStatus status, Optional<TicketStatus> currentFilter) {
        Material wool = woolFor(status);
        ItemStack item = new ItemStack(wool);
        ItemMeta meta = item.getItemMeta();
        String label = status == null ? langManager.get(Message.GUI_ADMIN_PANEL_FILTER_ALL, Map.of()) : status.name();
        boolean active = currentFilter.equals(Optional.ofNullable(status));
        meta.displayName(Component.text((active ? "> " : "") + label));
        item.setItemMeta(meta);
        return item;
    }

    private static Material woolFor(TicketStatus status) {
        if (status == null) {
            return Material.WHITE_WOOL;
        }
        return switch (status) {
            case OPEN -> Material.YELLOW_WOOL;
            case CLAIMED -> Material.ORANGE_WOOL;
            case WAITING_PLAYER -> Material.LIGHT_BLUE_WOOL;
            case WAITING_STAFF -> Material.RED_WOOL;
            case CLOSED -> Material.GRAY_WOOL;
            case ARCHIVED -> Material.BLACK_WOOL;
        };
    }

    private ItemStack arrowItem(String label) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(label));
        item.setItemMeta(meta);
        return item;
    }

    private Component title() {
        return miniMessage.deserialize(langManager.get(Message.GUI_ADMIN_PANEL_TITLE, Map.of()));
    }
}
