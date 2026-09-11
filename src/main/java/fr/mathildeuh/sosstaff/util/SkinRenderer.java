package fr.mathildeuh.sosstaff.util;

import java.util.UUID;

public final class SkinRenderer {

    private SkinRenderer() {
    }

    public static String avatarUrl(UUID playerUuid) {
        return "https://crafatar.com/avatars/" + playerUuid + "?size=128&overlay";
    }
}
