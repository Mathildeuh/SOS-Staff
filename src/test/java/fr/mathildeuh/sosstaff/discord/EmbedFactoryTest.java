package fr.mathildeuh.sosstaff.discord;

import fr.mathildeuh.sosstaff.config.DiscordConfig;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbedFactoryTest {

    @Test
    void managementRowHasTheSixFixedActionsAddressedToTheTicket() {
        ActionRow row = EmbedFactory.managementRow(42L);

        List<String> customIds = row.getButtons().stream().map(Button::getCustomId).toList();
        assertEquals(List.of("sos-manage:claim:42", "sos-manage:priority:42", "sos-manage:transcript:42",
                "sos-manage:ping:42", "sos-manage:close:42"), customIds);
    }

    @Test
    void actionButtonsRequiringOnlineAreDisabledWhenTheTargetIsOffline() {
        DiscordConfig.ActionButton button = new DiscordConfig.ActionButton(
                "heal-player", true, "Heal", "HEAL", "sosstaff.action.heal-player", true, false, null);

        List<ActionRow> rows = EmbedFactory.actionButtonRows(Map.of("heal-player", button), 42L, false);

        assertTrue(rows.getFirst().getButtons().getFirst().isDisabled());
    }

    @Test
    void actionButtonsRequiringOnlineStayEnabledWhenTheTargetIsOnline() {
        DiscordConfig.ActionButton button = new DiscordConfig.ActionButton(
                "heal-player", true, "Heal", "HEAL", "sosstaff.action.heal-player", true, false, null);

        List<ActionRow> rows = EmbedFactory.actionButtonRows(Map.of("heal-player", button), 42L, true);

        assertFalse(rows.getFirst().getButtons().getFirst().isDisabled());
    }

    @Test
    void disabledConfigButtonsAreOmittedEntirely() {
        DiscordConfig.ActionButton button = new DiscordConfig.ActionButton(
                "teleport-spawn", false, "TP Spawn", "tp %player% 0 100 0", "sosstaff.action.teleport-spawn", true, false, null);

        List<ActionRow> rows = EmbedFactory.actionButtonRows(Map.of("teleport-spawn", button), 42L, true);

        assertTrue(rows.isEmpty());
    }
}
