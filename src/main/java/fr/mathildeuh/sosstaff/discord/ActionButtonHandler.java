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
 * <p>The "permission" field on each button is a Bukkit permission node (matching the
 * sosstaff.action.* convention used everywhere else in config.yml), which only means something
 * once resolved against an actual Bukkit Permissible. Since the button was clicked in Discord,
 * this resolves the clicker to an online player by matching their Discord display name (see
 * StaffResolver) and checks the permission against that player - there is no other way to check
 * a Bukkit permission for someone who only exists as a Discord account.
 */
public final class ActionButtonHandler {

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
        if (staff == null || !staff.hasPermission(button.permission())) {
            event.reply("You need to be online in-game (with a Minecraft username matching your Discord name) "
                    + "and hold '" + button.permission() + "' to use this action.").setEphemeral(true).queue();
            return;
        }

        if (button.confirm()) {
            promptConfirmation(event, button, ticketId);
            return;
        }

        event.deferEdit().queue();
        runButton(button, ticketId, staff, event.getHook());
    }

    public void handleConfirm(ButtonInteractionEvent event, String buttonId, long ticketId) {
        DiscordConfig.ActionButton button = configManager.discord().actionButtons().get(buttonId);
        Player staff = StaffResolver.resolveByDiscordName(plugin, event.getUser().getName());
        if (button == null || staff == null || !staff.hasPermission(button.permission())) {
            event.editMessage("This action can no longer be confirmed.").setComponents(List.of()).queue();
            return;
        }
        event.deferEdit().queue();
        runButton(button, ticketId, staff, event.getHook());
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

    private void runButton(DiscordConfig.ActionButton button, long ticketId, Player staff, InteractionHook hook) {
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

            String reason = target == null ? null : substitute(button.kickReason(), target.getName(), staff.getName(), ticketId);
            internalActionRegistry.find(button.command()).ifPresentOrElse(
                    action -> action.execute(plugin, target, staff, reason),
                    () -> runRawCommand(button.command(), target, staff, ticketId));

            hook.editOriginal("'" + button.label() + "' executed by " + staff.getName() + ".").queue();
        });
    }

    private void runRawCommand(String rawCommand, Player target, Player staff, long ticketId) {
        String command = substitute(rawCommand, target == null ? null : target.getName(), staff.getName(), ticketId);
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
