package fr.mathildeuh.sosstaff.discord;

import fr.mathildeuh.sosstaff.config.ConfigManager;
import fr.mathildeuh.sosstaff.config.DiscordConfig;
import fr.mathildeuh.sosstaff.ticket.Ticket;
import fr.mathildeuh.sosstaff.ticket.TicketService;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Executes the configurable discord.action-buttons from config.yml: either one of the reserved
 * internal keys (HEAL, FEED, KICK, ...) via InternalActionRegistry, or a raw command string with
 * placeholders substituted, dispatched as the console.
 *
 * <p>Every action here targets the ticket's player, not the clicking staff member, so it works
 * from Discord regardless of whether that staff member is online in Minecraft at all - the only
 * exception is TELEPORT_TO_PLAYER/TELEPORT_PLAYER_TO_STAFF, which inherently need a real,
 * resolvable Minecraft character to move. The "permission" field is a Bukkit permission node; it
 * is checked when the clicker resolves to an online player by Discord-name match (see
 * StaffResolver), and otherwise trusted to whoever already has access to the channel
 * (discord.permissions.staff-roles) - there is no way to check a Bukkit permission for someone
 * who only exists as a Discord account with no online session and no account-linking system.
 */
public final class ActionButtonHandler {

    private static final List<String> REQUIRES_ONLINE_STAFF = List.of("TELEPORT_TO_PLAYER", "TELEPORT_PLAYER_TO_STAFF");

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final TicketService ticketService;
    private final InternalActionRegistry internalActionRegistry;

    public ActionButtonHandler(JavaPlugin plugin, ConfigManager configManager, TicketService ticketService,
                                InternalActionRegistry internalActionRegistry) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.ticketService = ticketService;
        this.internalActionRegistry = internalActionRegistry;
    }

    public void handle(ButtonInteractionEvent event, String buttonId, long ticketId) {
        DiscordConfig.ActionButton button = configManager.discord().actionButtons().get(buttonId);
        if (button == null || !button.enabled()) {
            event.reply("This action is no longer available.").setEphemeral(true).queue();
            return;
        }

        Player staff = StaffResolver.resolveByDiscordName(plugin, event.getUser().getName());
        if (staff == null && REQUIRES_ONLINE_STAFF.contains(button.command().toUpperCase())) {
            event.reply("'" + button.label() + "' moves your own Minecraft character, so it needs your Discord "
                    + "display name to match an online Minecraft username.").setEphemeral(true).queue();
            return;
        }
        if (staff != null && !staff.hasPermission(button.permission())) {
            event.reply("You need '" + button.permission() + "' to use this action.").setEphemeral(true).queue();
            return;
        }

        if (button.confirm()) {
            promptConfirmation(event, button, ticketId);
            return;
        }

        event.deferEdit().queue();
        runButton(button, ticketId, staff, event.getUser().getName(), event.getHook());
    }

    public void handleConfirm(ButtonInteractionEvent event, String buttonId, long ticketId) {
        DiscordConfig.ActionButton button = configManager.discord().actionButtons().get(buttonId);
        if (button == null) {
            event.editMessage("This action can no longer be confirmed.").setComponents(List.of()).queue();
            return;
        }
        Player staff = StaffResolver.resolveByDiscordName(plugin, event.getUser().getName());
        if (staff != null && !staff.hasPermission(button.permission())) {
            event.editMessage("This action can no longer be confirmed.").setComponents(List.of()).queue();
            return;
        }
        event.deferEdit().queue();
        runButton(button, ticketId, staff, event.getUser().getName(), event.getHook());
    }

    public void handleCancel(ButtonInteractionEvent event) {
        event.editMessage("Cancelled.").setComponents(List.of()).queue();
    }

    private void promptConfirmation(ButtonInteractionEvent event, DiscordConfig.ActionButton button, long ticketId) {
        ActionRow confirmRow = ActionRow.of(
                Button.danger("sos-action-confirm:" + button.id() + ":" + ticketId, "Confirm"),
                Button.secondary("sos-action-cancel:" + button.id() + ":" + ticketId, "Cancel"));
        event.reply("Confirm running '" + button.label() + "'?").setEphemeral(true).addComponents(confirmRow).queue();
    }

    private void runButton(DiscordConfig.ActionButton button, long ticketId, Player staff, String staffDisplayName, InteractionHook hook) {
        ticketService.findById(ticketId).thenAccept(ticketOpt -> {
            if (ticketOpt.isEmpty()) {
                hook.editOriginal("Ticket #" + ticketId + " no longer exists.").queue();
                return;
            }
            Ticket ticket = ticketOpt.get();
            Player target = Bukkit.getPlayer(ticket.playerUuid());
            if (button.requiresOnline() && target == null) {
                hook.editOriginal("The ticket's player is not online.").queue();
                return;
            }

            String reason = target == null ? null : substitute(button.kickReason(), target.getName(), staffDisplayName, ticketId);
            internalActionRegistry.find(button.command()).ifPresentOrElse(
                    action -> action.execute(plugin, target, staff, reason),
                    () -> runRawCommand(button.command(), target, staffDisplayName, ticketId));

            hook.editOriginal("'" + button.label() + "' executed by " + staffDisplayName + ".").queue();
        });
    }

    private void runRawCommand(String rawCommand, Player target, String staffDisplayName, long ticketId) {
        String command = substitute(rawCommand, target == null ? null : target.getName(), staffDisplayName, ticketId);
        Bukkit.getGlobalRegionScheduler().run(plugin, ignored ->
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
    }

    private static String substitute(String template, String playerName, String staffName, long ticketId) {
        if (template == null) {
            return null;
        }
        String result = template.replace("%staff%", staffName).replace("%ticket_id%", String.valueOf(ticketId));
        return playerName == null ? result : result.replace("%player%", playerName);
    }
}
