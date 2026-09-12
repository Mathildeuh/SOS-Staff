package fr.mathildeuh.sosstaff.integration;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;

import java.util.Optional;
import java.util.logging.Logger;

/**
 * A thin soft-depend seam onto the LuckPerms API, for any future feature that needs group or
 * permission-metadata lookups. Nothing in SOS-Staff calls {@link #api()} yet - every staff
 * action is already gated through plain Bukkit permission nodes - but detecting LuckPerms here
 * means such a feature can be added later without re-deriving how to soft-depend on it. Only
 * constructed when LuckPerms is actually installed - see the presence check in
 * {@code SosStaffPlugin.onEnable}; {@link #api()} additionally guards against the narrower case
 * where LuckPerms is installed but has not finished loading its API singleton yet.
 */
public final class LuckPermsHook {

    public LuckPermsHook(Logger logger) {
        logger.info(api().isPresent()
                ? "Detected LuckPerms; hook is available."
                : "LuckPerms plugin is present but its API is not ready yet; hook stays inactive.");
    }

    /**
     * The LuckPerms API instance, if the plugin is installed and has finished loading.
     */
    public Optional<LuckPerms> api() {
        try {
            return Optional.of(LuckPermsProvider.get());
        } catch (IllegalStateException notLoadedYet) {
            return Optional.empty();
        }
    }
}
