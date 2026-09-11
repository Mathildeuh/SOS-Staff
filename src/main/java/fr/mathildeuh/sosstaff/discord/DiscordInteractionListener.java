package fr.mathildeuh.sosstaff.discord;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * Every ticket button's custom id is "<namespace>:<key>:<ticketId>", dispatched here to either
 * ButtonHandler (fixed management actions) or ActionButtonHandler (configurable action-buttons
 * and their confirm/cancel follow-ups).
 */
public final class DiscordInteractionListener extends ListenerAdapter {

    private final ButtonHandler buttonHandler;
    private final ActionButtonHandler actionButtonHandler;

    public DiscordInteractionListener(ButtonHandler buttonHandler, ActionButtonHandler actionButtonHandler) {
        this.buttonHandler = buttonHandler;
        this.actionButtonHandler = actionButtonHandler;
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String[] parts = event.getComponentId().split(":", 3);
        if (parts.length != 3) {
            return;
        }
        String namespace = parts[0];
        String key = parts[1];
        long ticketId = parseTicketId(parts[2]);
        if (ticketId < 0) {
            return;
        }

        switch (namespace) {
            case "sos-manage" -> buttonHandler.handle(event, key, ticketId);
            case "sos-action" -> actionButtonHandler.handle(event, key, ticketId);
            case "sos-action-confirm" -> actionButtonHandler.handleConfirm(event, key, ticketId);
            case "sos-action-cancel" -> actionButtonHandler.handleCancel(event);
            default -> { /* not one of ours */ }
        }
    }

    private static long parseTicketId(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
