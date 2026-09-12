package fr.mathildeuh.sosstaff.discord;

import fr.mathildeuh.sosstaff.config.CategoryConfig;
import fr.mathildeuh.sosstaff.config.DiscordConfig;
import fr.mathildeuh.sosstaff.ticket.Ticket;
import fr.mathildeuh.sosstaff.util.SkinRenderer;
import fr.mathildeuh.sosstaff.util.VersionInfo;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class EmbedFactory {

    private static final int MAX_BUTTONS_PER_ROW = 5;

    private EmbedFactory() {
    }

    public static ActionRow managementRow(long ticketId) {
        String suffix = ":" + ticketId;
        return ActionRow.of(
                Button.success("sos-manage:claim" + suffix, "Claim"),
                Button.primary("sos-manage:priority" + suffix, "Priority"),
                Button.secondary("sos-manage:transcript" + suffix, "Transcript"),
                Button.secondary("sos-manage:ping" + suffix, "Ping"),
                Button.danger("sos-manage:close" + suffix, "Close"));
    }

    public static ActionRow reopenRow(long ticketId) {
        return ActionRow.of(Button.secondary("sos-manage:reopen:" + ticketId, "Reopen"));
    }

    public static List<ActionRow> actionButtonRows(Map<String, DiscordConfig.ActionButton> buttons, long ticketId, boolean targetOnline) {
        List<Button> enabledButtons = new ArrayList<>();
        for (DiscordConfig.ActionButton button : buttons.values()) {
            if (!button.enabled()) {
                continue;
            }
            Button rendered = Button.secondary("sos-action:" + button.id() + ":" + ticketId, button.label());
            if (button.requiresOnline() && !targetOnline) {
                rendered = rendered.asDisabled();
            }
            enabledButtons.add(rendered);
        }

        List<ActionRow> rows = new ArrayList<>();
        for (int i = 0; i < enabledButtons.size(); i += MAX_BUTTONS_PER_ROW) {
            rows.add(ActionRow.of(enabledButtons.subList(i, Math.min(i + MAX_BUTTONS_PER_ROW, enabledButtons.size()))));
        }
        return rows;
    }

    /**
     * claimed_by is a Discord user id, so "Claimed by" renders as a live Discord mention -
     * no name resolution needed, and it stays accurate even if that person renames themselves.
     */
    public static MessageEmbed ticketEmbed(Ticket ticket, CategoryConfig category, String playerName) {
        EmbedBuilder builder = new EmbedBuilder();
        builder.setTitle("Ticket #" + ticket.id() + " - " + category.displayName());
        builder.setColor(parseColor(category.colorHex()));
        builder.setThumbnail(SkinRenderer.avatarUrl(ticket.playerUuid()));
        builder.addField("Player", playerName, true);
        builder.addField("Status", ticket.status().name(), true);
        builder.addField("Priority", ticket.priority().name(), true);
        if (ticket.claimedBy() != null) {
            builder.addField("Claimed by", "<@" + ticket.claimedBy() + ">", true);
        }
        builder.setFooter("SOS-Staff v" + VersionInfo.version());
        builder.setTimestamp(Instant.now());
        return builder.build();
    }

    private static int parseColor(String hex) {
        return Integer.parseInt(hex.substring(1), 16);
    }
}
