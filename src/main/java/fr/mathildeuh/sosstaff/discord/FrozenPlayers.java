package fr.mathildeuh.sosstaff.discord;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FrozenPlayers {

    private final Set<UUID> frozen = ConcurrentHashMap.newKeySet();

    public void freeze(UUID playerUuid) {
        frozen.add(playerUuid);
    }

    public void unfreeze(UUID playerUuid) {
        frozen.remove(playerUuid);
    }

    public boolean isFrozen(UUID playerUuid) {
        return frozen.contains(playerUuid);
    }
}
