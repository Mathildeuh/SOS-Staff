package fr.mathildeuh.sosstaff.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.view.AnvilView;

import java.util.Optional;

public final class GuiClickListener implements Listener {

    private static final int ANVIL_RESULT_SLOT = 2;

    private final CreationMenu creationMenu;
    private final AdminPanel adminPanel;

    public GuiClickListener(CreationMenu creationMenu, AdminPanel adminPanel) {
        this.creationMenu = creationMenu;
        this.adminPanel = adminPanel;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof CreationMenuHolder creationMenuHolder) {
            handleCreationMenuClick(event, creationMenuHolder);
        } else if (holder instanceof AnvilInputHolder anvilInputHolder) {
            handleAnvilClick(event, anvilInputHolder);
        } else if (holder instanceof AdminPanelHolder adminPanelHolder) {
            handleAdminPanelClick(event, adminPanelHolder);
        }
    }

    private void handleAdminPanelClick(InventoryClickEvent event, AdminPanelHolder holder) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getInventory())) {
            return;
        }

        Player staff = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        Long ticketId = holder.ticketIdAt(slot);
        if (ticketId != null) {
            adminPanel.onTicketClicked(staff, ticketId);
            return;
        }
        if (holder.isPrevPageSlot(slot) && holder.page() > 0) {
            adminPanel.open(staff, holder.page() - 1, holder.filterStatus());
            return;
        }
        if (holder.isNextPageSlot(slot)) {
            adminPanel.open(staff, holder.page() + 1, holder.filterStatus());
            return;
        }
        if (holder.hasFilterAt(slot)) {
            adminPanel.open(staff, 0, Optional.ofNullable(holder.filterAt(slot)));
        }
    }

    private void handleCreationMenuClick(InventoryClickEvent event, CreationMenuHolder holder) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getInventory())) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        if (holder.stage() == CreationMenuHolder.Stage.CATEGORY) {
            String categoryId = holder.categoryIdAt(slot);
            if (categoryId != null) {
                creationMenu.onCategoryClicked(player, categoryId);
            }
            return;
        }

        if (holder.isCustomPresetSlot(slot)) {
            creationMenu.onWriteYourOwnClicked(player, holder.categoryId());
            return;
        }
        String preset = holder.presetAt(slot);
        if (preset != null) {
            creationMenu.onPresetClicked(player, holder.categoryId(), preset);
        }
    }

    private void handleAnvilClick(InventoryClickEvent event, AnvilInputHolder holder) {
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getInventory())
                || event.getSlot() != ANVIL_RESULT_SLOT || !(event.getView() instanceof AnvilView anvilView)) {
            return;
        }

        event.setCancelled(true);
        String text = anvilView.getRenameText();
        Player player = (Player) event.getWhoClicked();
        player.closeInventory();
        if (text != null && !text.isBlank()) {
            holder.onComplete().accept(text);
        }
    }
}
