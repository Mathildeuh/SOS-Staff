package fr.mathildeuh.sosstaff.discord;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public final class FreezeListener implements Listener {

    private final FrozenPlayers frozenPlayers;

    public FreezeListener(FrozenPlayers frozenPlayers) {
        this.frozenPlayers = frozenPlayers;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!frozenPlayers.isFrozen(event.getPlayer().getUniqueId())) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            event.setCancelled(true);
        }
    }
}
