package fr.mathildeuh.sosstaff.gui;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Tracks players who were sent a free-input chat prompt and are expected to type their next
 * chat message as the answer, instead of it going to normal server chat.
 */
public final class PendingChatPrompts {

    public record PendingPrompt(String categoryId, Consumer<String> onComplete) {
    }

    private final Map<UUID, PendingPrompt> pending = new ConcurrentHashMap<>();

    public void await(UUID playerUuid, String categoryId, Consumer<String> onComplete) {
        pending.put(playerUuid, new PendingPrompt(categoryId, onComplete));
    }

    public Optional<PendingPrompt> consume(UUID playerUuid) {
        return Optional.ofNullable(pending.remove(playerUuid));
    }

    public void cancel(UUID playerUuid) {
        pending.remove(playerUuid);
    }

    public boolean isPending(UUID playerUuid) {
        return pending.containsKey(playerUuid);
    }
}
