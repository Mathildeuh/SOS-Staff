package fr.mathildeuh.sosstaff.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

/**
 * A single line of free text captured through a repurposed anvil inventory: the rename text
 * field is the only reliable native single-line text input Paper offers with no chat prompt.
 * The result slot is force-filled on every keystroke so it is always clickable regardless of
 * whether the typed name would be a valid vanilla repair; GuiClickListener reads the actual
 * captured text from AnvilInventory#getRenameText() when the player takes that result, rather
 * than trusting the result item's own display name.
 */
public final class AnvilInputGui implements Listener {

    public void open(Player player, Component title, Consumer<String> onComplete) {
        AnvilInputHolder holder = new AnvilInputHolder(onComplete);
        Inventory inventory = Bukkit.createInventory(holder, InventoryType.ANVIL, title);
        holder.setInventory(inventory);
        inventory.setItem(0, new ItemStack(Material.PAPER));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (event.getInventory().getHolder() instanceof AnvilInputHolder) {
            event.setResult(new ItemStack(Material.PAPER));
        }
    }
}
