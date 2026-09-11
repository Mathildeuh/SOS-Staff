package fr.mathildeuh.sosstaff.session;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which online players currently have their chat bridged into a Discord ticket
 * channel. Both lookup directions are O(1): by player (the chat listener, on the async chat
 * thread) and by Discord channel id (the JDA message listener, on JDA's own threads). Both
 * maps are updated together, so it is safe to read either concurrently from any thread.
 */
public final class LiveChatSessionManager {

    private final Map<UUID, TicketSession> byPlayer = new ConcurrentHashMap<>();
    private final Map<String, TicketSession> byChannelId = new ConcurrentHashMap<>();

    public void open(TicketSession session) {
        byPlayer.put(session.playerUuid(), session);
        byChannelId.put(session.discordChannelId(), session);
    }

    public void closeByPlayer(UUID playerUuid) {
        TicketSession removed = byPlayer.remove(playerUuid);
        if (removed != null) {
            byChannelId.remove(removed.discordChannelId());
        }
    }

    public void closeByTicketId(long ticketId) {
        byPlayer.values().stream()
                .filter(session -> session.ticketId() == ticketId)
                .findFirst()
                .ifPresent(session -> closeByPlayer(session.playerUuid()));
    }

    public Optional<TicketSession> findByPlayer(UUID playerUuid) {
        return Optional.ofNullable(byPlayer.get(playerUuid));
    }

    public Optional<TicketSession> findByChannelId(String discordChannelId) {
        return Optional.ofNullable(byChannelId.get(discordChannelId));
    }
}
