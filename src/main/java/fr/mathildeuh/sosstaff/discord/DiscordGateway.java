package fr.mathildeuh.sosstaff.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

public final class DiscordGateway {

    private final Logger logger;
    private JDA jda;

    public DiscordGateway(Logger logger) {
        this.logger = logger;
    }

    public boolean start(String token, Object... eventListeners) {
        if (token == null || token.isBlank()) {
            logger.warning("No Discord bot token configured (discord.token / SOSSTAFF_DISCORD_TOKEN); "
                    + "the Discord bridge stays disabled and tickets will only exist in-game.");
            return false;
        }

        try {
            jda = JDABuilder.createLight(token, GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                    .addEventListeners(eventListeners)
                    .build();
            return true;
        } catch (InvalidTokenException e) {
            logger.severe("The configured Discord bot token is invalid: " + e.getMessage());
            jda = null;
            return false;
        }
    }

    /**
     * Blocks briefly (plugin disable, not normal runtime, so this is fine) until JDA's own
     * threads actually stop, instead of just signalling shutdown and returning immediately.
     * Without this wait, a background JDA thread can still be mid-reconnect when Bukkit closes
     * this plugin's classloader right after onDisable() returns, throwing a confusing
     * "zip file closed" error instead of shutting down cleanly.
     */
    public void stop() {
        if (jda == null) {
            return;
        }
        jda.shutdown();
        try {
            if (!jda.awaitShutdown(Duration.ofSeconds(5))) {
                jda.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            jda.shutdownNow();
        }
        jda = null;
    }

    public boolean isReady() {
        return jda != null && jda.getStatus() == JDA.Status.CONNECTED;
    }

    public Optional<JDA> jda() {
        return Optional.ofNullable(jda);
    }

    /**
     * Every ticket channel is created in the single guild this bot is a member of; SOS-Staff
     * is designed for one Minecraft server bridged to one Discord server, so there is no
     * guild-id setting in config.yml. Logs a warning and returns empty if the bot has not
     * been invited anywhere yet, or has been invited to more than one guild.
     */
    public Optional<Guild> primaryGuild() {
        if (jda == null) {
            return Optional.empty();
        }
        List<Guild> guilds = jda.getGuilds();
        if (guilds.isEmpty()) {
            logger.warning("The Discord bot is not a member of any server yet; invite it before creating tickets.");
            return Optional.empty();
        }
        if (guilds.size() > 1) {
            logger.warning("The Discord bot is a member of " + guilds.size()
                    + " servers; SOS-Staff only supports one and will use '" + guilds.getFirst().getName() + "'.");
        }
        return Optional.of(guilds.getFirst());
    }
}
