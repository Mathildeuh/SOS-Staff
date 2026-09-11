package fr.mathildeuh.sosstaff.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;

/**
 * Identifies a CreationMenu inventory to GuiClickListener and carries the per-slot lookup it
 * needs to know what the player clicked, without re-deriving it from item stacks.
 */
public final class CreationMenuHolder implements InventoryHolder {

    public enum Stage { CATEGORY, PRESET }

    private final Stage stage;
    private final String categoryId;
    private final Map<Integer, String> slotToCategoryId;
    private final Map<Integer, String> slotToPreset;
    private final int customPresetSlot;
    private Inventory inventory;

    private CreationMenuHolder(Stage stage, String categoryId, Map<Integer, String> slotToCategoryId,
                                Map<Integer, String> slotToPreset, int customPresetSlot) {
        this.stage = stage;
        this.categoryId = categoryId;
        this.slotToCategoryId = slotToCategoryId;
        this.slotToPreset = slotToPreset;
        this.customPresetSlot = customPresetSlot;
    }

    public static CreationMenuHolder categoryStage(Map<Integer, String> slotToCategoryId) {
        return new CreationMenuHolder(Stage.CATEGORY, null, slotToCategoryId, Map.of(), -1);
    }

    public static CreationMenuHolder presetStage(String categoryId, Map<Integer, String> slotToPreset, int customPresetSlot) {
        return new CreationMenuHolder(Stage.PRESET, categoryId, Map.of(), slotToPreset, customPresetSlot);
    }

    public Stage stage() {
        return stage;
    }

    public String categoryId() {
        return categoryId;
    }

    public String categoryIdAt(int slot) {
        return slotToCategoryId.get(slot);
    }

    public String presetAt(int slot) {
        return slotToPreset.get(slot);
    }

    public boolean isCustomPresetSlot(int slot) {
        return slot == customPresetSlot;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
