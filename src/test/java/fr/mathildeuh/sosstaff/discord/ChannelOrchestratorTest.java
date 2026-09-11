package fr.mathildeuh.sosstaff.discord;

import fr.mathildeuh.sosstaff.ticket.Ticket;
import fr.mathildeuh.sosstaff.ticket.TicketPriority;
import fr.mathildeuh.sosstaff.ticket.TicketStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChannelOrchestratorTest {

    private static final Ticket TICKET = new Ticket(
            42L, UUID.randomUUID(), "bug", TicketStatus.OPEN, TicketPriority.HIGH,
            null, null, Instant.now(), null, null, null);

    @Test
    void formatsThePlaceholdersInTheChannelNameFormat() {
        String name = ChannelOrchestrator.formatChannelName("ticket-%id%-%player%", TICKET, "Notch");

        assertEquals("ticket-42-Notch", name);
    }

    @Test
    void formatsThePlaceholdersInTheTopicFormat() {
        String topic = ChannelOrchestrator.formatTopic("Ticket #%id% - %category% - Priority: %priority%", TICKET);

        assertEquals("Ticket #42 - bug - Priority: HIGH", topic);
    }
}
