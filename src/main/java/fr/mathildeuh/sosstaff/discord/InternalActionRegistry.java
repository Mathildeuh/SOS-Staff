package fr.mathildeuh.sosstaff.discord;

import net.kyori.adventure.text.Component;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * TELEPORT_TO_PLAYER and TELEPORT_PLAYER_TO_STAFF need to know which online Minecraft player
 * the staff member who clicked the Discord button actually is. Nothing in this project links a
 * Discord account to a Minecraft one (see ChannelOrchestrator's class comment for the same gap
 * elsewhere), so ActionButtonHandler resolves "acting staff" by matching the clicker's Discord
 * display name against an online player name; if that match fails, actingStaff is null here and
 * both teleport actions are no-ops. This is a deliberate, documented limitation, not a bug.
 */
public final class InternalActionRegistry {

    private final Map<String, InternalAction> actions = new HashMap<>();
    private final FrozenPlayers frozenPlayers;

    public InternalActionRegistry(FrozenPlayers frozenPlayers) {
        this.frozenPlayers = frozenPlayers;
        register("HEAL", (plugin, target, staff, extra) -> onEntity(plugin, target, () -> {
            AttributeInstance maxHealth = target.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealth != null) {
                target.setHealth(maxHealth.getValue());
            }
        }));
        register("FEED", (plugin, target, staff, extra) -> onEntity(plugin, target, () -> target.setFoodLevel(20)));
        register("KICK", (plugin, target, staff, extra) -> onEntity(plugin, target, () -> target.kick(
                Component.text(extra == null || extra.isBlank() ? "Reconnection requested by support" : extra),
                PlayerKickEvent.Cause.PLUGIN)));
        register("FREEZE", (plugin, target, staff, extra) -> frozenPlayers.freeze(target.getUniqueId()));
        register("UNFREEZE", (plugin, target, staff, extra) -> frozenPlayers.unfreeze(target.getUniqueId()));
        register("TELEPORT_TO_PLAYER", (plugin, target, staff, extra) -> {
            if (staff != null) {
                onEntity(plugin, staff, () -> staff.teleport(target.getLocation()));
            }
        });
        register("TELEPORT_PLAYER_TO_STAFF", (plugin, target, staff, extra) -> {
            if (staff != null) {
                onEntity(plugin, target, () -> target.teleport(staff.getLocation()));
            }
        });
    }

    public Optional<InternalAction> find(String key) {
        return Optional.ofNullable(actions.get(key.toUpperCase(Locale.ROOT)));
    }

    private void register(String key, InternalAction action) {
        actions.put(key, action);
    }

    private static void onEntity(JavaPlugin plugin, Player player, Runnable task) {
        player.getScheduler().run(plugin, scheduledTask -> task.run(), null);
    }
}
