package fr.mathildeuh.sosstaff.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.function.Consumer;

/**
 * Identifies an AnvilInputGui inventory to GuiClickListener and carries the callback to invoke
 * with whatever text the player renamed the item to when they click the output slot.
 */
public final class AnvilInputHolder implements InventoryHolder {

    private final Consumer<String> onComplete;
    private Inventory inventory;

    public AnvilInputHolder(Consumer<String> onComplete) {
        this.onComplete = onComplete;
    }

    public Consumer<String> onComplete() {
        return onComplete;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
