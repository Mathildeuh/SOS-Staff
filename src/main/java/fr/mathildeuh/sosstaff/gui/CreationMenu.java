package fr.mathildeuh.sosstaff.gui;

import fr.mathildeuh.sosstaff.api.event.TicketCreateEvent;
import fr.mathildeuh.sosstaff.config.CategoryConfig;
import fr.mathildeuh.sosstaff.config.ConfigManager;
import fr.mathildeuh.sosstaff.config.CreationMode;
import fr.mathildeuh.sosstaff.config.CreationUi;
import fr.mathildeuh.sosstaff.lang.LangManager;
import fr.mathildeuh.sosstaff.lang.Message;
import fr.mathildeuh.sosstaff.ticket.Ticket;
import fr.mathildeuh.sosstaff.ticket.TicketCreationCoordinator;
import fr.mathildeuh.sosstaff.ticket.TicketCreationResult;
import fr.mathildeuh.sosstaff.ticket.TicketPriority;
import fr.mathildeuh.sosstaff.ticket.TicketStatus;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CreationMenu {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final LangManager langManager;
    private final TicketCreationCoordinator creationCoordinator;
    private final AnvilInputGui anvilInputGui;
    private final PendingChatPrompts pendingChatPrompts;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public CreationMenu(JavaPlugin plugin, ConfigManager configManager, LangManager langManager,
                         TicketCreationCoordinator creationCoordinator, AnvilInputGui anvilInputGui,
                         PendingChatPrompts pendingChatPrompts) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.langManager = langManager;
        this.creationCoordinator = creationCoordinator;
        this.anvilInputGui = anvilInputGui;
        this.pendingChatPrompts = pendingChatPrompts;
    }

    public void open(Player player) {
        Map<String, CategoryConfig> categories = configManager.categories();
        Map<Integer, String> slotToCategory = new LinkedHashMap<>();
        int slot = 0;
        for (String categoryId : categories.keySet()) {
            slotToCategory.put(slot++, categoryId);
        }

        CreationMenuHolder holder = CreationMenuHolder.categoryStage(slotToCategory);
        Inventory inventory = Bukkit.createInventory(holder, size(slotToCategory.size()), title());
        holder.setInventory(inventory);

        for (Map.Entry<Integer, String> entry : slotToCategory.entrySet()) {
            inventory.setItem(entry.getKey(), categoryItem(categories.get(entry.getValue())));
        }

        player.openInventory(inventory);
    }

    void onCategoryClicked(Player player, String categoryId) {
        CategoryConfig category = configManager.categories().get(categoryId);
        if (category == null) {
            return;
        }
        CreationMode mode = category.resolvedMode(configManager.creationMode());

        if (mode == CreationMode.FREE_INPUT) {
            player.closeInventory();
            beginFreeInput(player, category);
            return;
        }

        openPresetStage(player, category, mode == CreationMode.BOTH);
    }

    private void openPresetStage(Player player, CategoryConfig category, boolean allowCustom) {
        Map<Integer, String> slotToPreset = new LinkedHashMap<>();
        int slot = 0;
        for (String preset : category.presets()) {
            slotToPreset.put(slot++, preset);
        }
        int customSlot = allowCustom ? slot++ : -1;

        CreationMenuHolder holder = CreationMenuHolder.presetStage(category.id(), slotToPreset, customSlot);
        Inventory inventory = Bukkit.createInventory(holder, size(slot), miniMessage.deserialize(category.displayName()));
        holder.setInventory(inventory);

        for (Map.Entry<Integer, String> entry : slotToPreset.entrySet()) {
            inventory.setItem(entry.getKey(), presetItem(entry.getValue()));
        }
        if (allowCustom) {
            inventory.setItem(customSlot, writeYourOwnItem());
        }

        player.openInventory(inventory);
    }

    void onPresetClicked(Player player, String categoryId, String preset) {
        player.closeInventory();
        createTicket(player, categoryId, preset);
    }

    void onWriteYourOwnClicked(Player player, String categoryId) {
        player.closeInventory();
        CategoryConfig category = configManager.categories().get(categoryId);
        if (category != null) {
            beginFreeInput(player, category);
        }
    }

    private void beginFreeInput(Player player, CategoryConfig category) {
        String prompt = category.promptKey() != null
                ? langManager.get(category.promptKey(), Map.of())
                : langManager.get(Message.CREATION_DEFAULT_PROMPT, Map.of());

        if (configManager.creationUi() == CreationUi.GUI) {
            anvilInputGui.open(player, miniMessage.deserialize(prompt),
                    text -> player.getScheduler().run(plugin, scheduledTask -> createTicket(player, category.id(), text), null));
            return;
        }

        player.sendMessage(miniMessage.deserialize(prompt));
        pendingChatPrompts.await(player.getUniqueId(), category.id(),
                text -> player.getScheduler().run(plugin, scheduledTask -> createTicket(player, category.id(), text), null));
    }

    private void createTicket(Player player, String categoryId, String initialMessage) {
        if (!announceCreation(player, categoryId)) {
            player.sendMessage(miniMessage.deserialize(langManager.get(Message.TICKET_CREATE_REJECTED_BY_PLUGIN, Map.of())));
            return;
        }

        boolean bypass = player.hasPermission(configManager.antiSpamBypassPermission());
        creationCoordinator.create(player.getUniqueId(), player.getName(), categoryId, TicketPriority.MEDIUM, bypass, initialMessage)
                .thenAccept(result -> player.getScheduler().run(plugin, scheduledTask -> notify(player, result), null))
                .exceptionally(throwable -> {
                    plugin.getLogger().severe("Failed to create a ticket for " + player.getUniqueId() + " from the GUI: " + throwable);
                    return null;
                });
    }

    /**
     * Fires {@link TicketCreateEvent} with a transient preview of the ticket about to be
     * created, so another plugin can veto the request before it ever reaches the database.
     * Returns {@code false} if a listener cancelled it.
     */
    private boolean announceCreation(Player player, String categoryId) {
        Ticket preview = new Ticket(0, player.getUniqueId(), categoryId, TicketStatus.OPEN,
                TicketPriority.MEDIUM, null, null, Instant.now(), null, null, null);
        TicketCreateEvent event = new TicketCreateEvent(preview);
        Bukkit.getPluginManager().callEvent(event);
        return !event.isCancelled();
    }

    private void notify(Player player, TicketCreationResult result) {
        switch (result) {
            case TicketCreationResult.Created created ->
                    player.sendMessage(miniMessage.deserialize(langManager.get(Message.TICKET_CREATE_SUCCESS,
                            Map.of("id", String.valueOf(created.ticket().id())))));
            case TicketCreationResult.RejectedTooManyOpenTickets rejected ->
                    player.sendMessage(miniMessage.deserialize(langManager.get(Message.TICKET_CREATE_REJECTED_TOO_MANY_OPEN,
                            Map.of("id", String.valueOf(rejected.existingTicket().id())))));
            case TicketCreationResult.RejectedCooldownActive rejected ->
                    player.sendMessage(miniMessage.deserialize(langManager.get(Message.TICKET_CREATE_REJECTED_COOLDOWN,
                            Map.of("seconds", String.valueOf(rejected.remaining().toSeconds())))));
        }
    }

    private ItemStack categoryItem(CategoryConfig category) {
        ItemStack item = new ItemStack(category.icon());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(miniMessage.deserialize("<color:" + category.colorHex() + ">" + category.displayName() + "</color>"));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack presetItem(String preset) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(preset));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack writeYourOwnItem() {
        ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(miniMessage.deserialize(langManager.get(Message.GUI_CREATION_WRITE_YOUR_OWN, Map.of())));
        item.setItemMeta(meta);
        return item;
    }

    private Component title() {
        return miniMessage.deserialize(langManager.get(Message.GUI_CREATION_TITLE, Map.of()));
    }

    private static int size(int itemCount) {
        int rows = Math.clamp((itemCount + 8) / 9, 1, 6);
        return rows * 9;
    }
}
