package fr.mathildeuh.sosstaff.session;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveChatSessionManagerTest {

    @Test
    void aSessionIsFindableByBothPlayerAndChannelId() {
        LiveChatSessionManager manager = new LiveChatSessionManager();
        UUID playerUuid = UUID.randomUUID();
        TicketSession session = new TicketSession(playerUuid, 7L, "channel-7");

        manager.open(session);

        assertEquals(session, manager.findByPlayer(playerUuid).orElseThrow());
        assertEquals(session, manager.findByChannelId("channel-7").orElseThrow());
    }

    @Test
    void closingByTicketIdRemovesBothIndexEntries() {
        LiveChatSessionManager manager = new LiveChatSessionManager();
        UUID playerUuid = UUID.randomUUID();
        manager.open(new TicketSession(playerUuid, 7L, "channel-7"));

        manager.closeByTicketId(7L);

        assertTrue(manager.findByPlayer(playerUuid).isEmpty());
        assertTrue(manager.findByChannelId("channel-7").isEmpty());
    }

    @Test
    void closingByPlayerRemovesBothIndexEntries() {
        LiveChatSessionManager manager = new LiveChatSessionManager();
        UUID playerUuid = UUID.randomUUID();
        manager.open(new TicketSession(playerUuid, 7L, "channel-7"));

        manager.closeByPlayer(playerUuid);

        assertTrue(manager.findByPlayer(playerUuid).isEmpty());
        assertTrue(manager.findByChannelId("channel-7").isEmpty());
    }
}
