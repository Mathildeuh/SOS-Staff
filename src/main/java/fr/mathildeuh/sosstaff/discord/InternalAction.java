package fr.mathildeuh.sosstaff.discord;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

@FunctionalInterface
public interface InternalAction {

    /**
     * actingStaff is null when no online player matches the Discord clicker (see
     * InternalActionRegistry's class comment); actions that don't need it simply ignore it.
     * extraArgument carries an already placeholder-substituted string such as a kick reason;
     * actions that don't need one ignore it too.
     */
    void execute(JavaPlugin plugin, Player target, Player actingStaff, String extraArgument);
}
