package fr.mathildeuh.sosstaff.api.event;

import fr.mathildeuh.sosstaff.ticket.Ticket;
import fr.mathildeuh.sosstaff.ticket.TicketPriority;
import fr.mathildeuh.sosstaff.ticket.TicketStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TicketEventsTest {

    private static final Ticket TICKET = new Ticket(42, UUID.randomUUID(), "bug", TicketStatus.OPEN,
            TicketPriority.MEDIUM, null, null, Instant.now(), null, null, null);

    @Test
    void ticketCreateEventStartsUncancelledAndExposesItsTicket() {
        TicketCreateEvent event = new TicketCreateEvent(TICKET);

        assertSame(TICKET, event.getTicket());
        assertFalse(event.isCancelled());
        assertFalse(event.isAsynchronous());

        event.setCancelled(true);
        assertTrue(event.isCancelled());
    }

    @Test
    void ticketClaimEventExposesTheClaimingStaffMember() {
        UUID staffUuid = UUID.randomUUID();
        TicketClaimEvent event = new TicketClaimEvent(TICKET, staffUuid);

        assertSame(TICKET, event.getTicket());
        assertEquals(staffUuid, event.getStaffUuid());
        assertFalse(event.isCancelled());

        event.setCancelled(true);
        assertTrue(event.isCancelled());
    }

    @Test
    void ticketCloseEventExposesTheCloseReason() {
        TicketCloseEvent event = new TicketCloseEvent(TICKET, "Resolved by staff");

        assertSame(TICKET, event.getTicket());
        assertEquals("Resolved by staff", event.getReason());
        assertFalse(event.isCancelled());

        event.setCancelled(true);
        assertTrue(event.isCancelled());
    }

    @Test
    void ticketMessageEventExposesAuthorAndContentForAPlayerMessage() {
        UUID authorUuid = UUID.randomUUID();
        TicketMessageEvent event = new TicketMessageEvent(TICKET, authorUuid, "Steve", "Please help", false);

        assertSame(TICKET, event.getTicket());
        assertEquals(authorUuid, event.getAuthorUuid());
        assertEquals("Steve", event.getAuthorName());
        assertEquals("Please help", event.getContent());
        assertFalse(event.isStaff());
        assertFalse(event.isCancelled());
    }

    @Test
    void ticketMessageEventAllowsANullAuthorUuidForAPureDiscordMessage() {
        TicketMessageEvent event = new TicketMessageEvent(TICKET, null, "StaffMember#1234", "On it", true);

        assertNull(event.getAuthorUuid());
        assertTrue(event.isStaff());
    }
}
