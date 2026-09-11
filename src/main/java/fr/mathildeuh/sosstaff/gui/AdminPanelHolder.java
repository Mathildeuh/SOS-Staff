package fr.mathildeuh.sosstaff.gui;

import fr.mathildeuh.sosstaff.ticket.TicketStatus;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;
import java.util.Optional;

/**
 * Identifies an AdminPanel inventory to GuiClickListener and carries the per-slot lookups it
 * needs: which ticket a row belongs to, and what each control-row button does. filterStatus is
 * empty for "no filter" (every status shown) rather than null, since a null TicketStatus for
 * one specific filter button already means "the ALL button" in slotToFilter.
 */
public final class AdminPanelHolder implements InventoryHolder {

    private final int page;
    private final Optional<TicketStatus> filterStatus;
    private final Map<Integer, Long> slotToTicketId;
    private final Map<Integer, TicketStatus> slotToFilter;
    private final int prevPageSlot;
    private final int nextPageSlot;
    private Inventory inventory;

    public AdminPanelHolder(int page, Optional<TicketStatus> filterStatus, Map<Integer, Long> slotToTicketId,
                             Map<Integer, TicketStatus> slotToFilter, int prevPageSlot, int nextPageSlot) {
        this.page = page;
        this.filterStatus = filterStatus;
        this.slotToTicketId = slotToTicketId;
        this.slotToFilter = slotToFilter;
        this.prevPageSlot = prevPageSlot;
        this.nextPageSlot = nextPageSlot;
    }

    public int page() {
        return page;
    }

    public Optional<TicketStatus> filterStatus() {
        return filterStatus;
    }

    public Long ticketIdAt(int slot) {
        return slotToTicketId.get(slot);
    }

    public boolean hasFilterAt(int slot) {
        return slotToFilter.containsKey(slot);
    }

    public TicketStatus filterAt(int slot) {
        return slotToFilter.get(slot);
    }

    public boolean isPrevPageSlot(int slot) {
        return slot == prevPageSlot;
    }

    public boolean isNextPageSlot(int slot) {
        return slot == nextPageSlot;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
