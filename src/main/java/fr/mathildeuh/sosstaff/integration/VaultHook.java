package fr.mathildeuh.sosstaff.integration;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;

import java.util.Optional;
import java.util.logging.Logger;

/**
 * A thin soft-depend seam onto Vault's Economy service, for any future feature that needs to
 * charge or reward players - SOS-Staff has none today, consistent with the project's own
 * no-premium-logic constraint, but a server owner may still want e.g. a small fee for priority
 * support later. Only constructed when Vault is actually installed - see the presence check in
 * {@code SosStaffPlugin.onEnable}; {@link #economy()} additionally handles the case where Vault
 * is installed but no plugin has registered an Economy implementation behind it.
 */
public final class VaultHook {

    private final ServicesManager servicesManager;

    public VaultHook(ServicesManager servicesManager, Logger logger) {
        this.servicesManager = servicesManager;
        logger.info(economy().isPresent()
                ? "Detected Vault with a registered Economy provider; hook is available."
                : "Vault is present but no Economy provider is registered; hook stays inactive.");
    }

    /**
     * The registered Vault Economy provider, if Vault is installed and another plugin has
     * registered an implementation behind it.
     */
    public Optional<Economy> economy() {
        RegisteredServiceProvider<Economy> registration = servicesManager.getRegistration(Economy.class);
        return registration != null ? Optional.of(registration.getProvider()) : Optional.empty();
    }
}
