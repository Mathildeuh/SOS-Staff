package fr.mathildeuh.sosstaff.discord;

import fr.mathildeuh.sosstaff.util.SkinRenderer;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.IncomingWebhookClient;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.WebhookClient;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Relays a player's in-game chat into their ticket channel by impersonating them through a
 * Discord webhook (display name + skin avatar), rather than posting as the bot account. One
 * webhook is created per ticket channel and reused for its lifetime; a channel restart looks
 * for an existing "SOS-Staff Relay" webhook before creating a new one, so tickets surviving a
 * plugin restart do not accumulate duplicate webhooks.
 */
public final class WebhookRelay {

    private static final String WEBHOOK_NAME = "SOS-Staff Relay";

    private final DiscordGateway gateway;
    private final Logger logger;
    private final Map<String, IncomingWebhookClient> clientsByChannelId = new ConcurrentHashMap<>();

    public WebhookRelay(DiscordGateway gateway, Logger logger) {
        this.gateway = gateway;
        this.logger = logger;
    }

    public CompletableFuture<Void> relayPlayerMessage(String discordChannelId, UUID playerUuid, String playerName, String content) {
        return webhookClientFor(discordChannelId).thenCompose(clientOpt -> {
            if (clientOpt.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            CompletableFuture<Void> future = new CompletableFuture<>();
            clientOpt.get().sendMessage(content)
                    .setUsername(playerName)
                    .setAvatarUrl(SkinRenderer.avatarUrl(playerUuid))
                    .queue(ignored -> future.complete(null), future::completeExceptionally);
            return future;
        });
    }

    private CompletableFuture<Optional<IncomingWebhookClient>> webhookClientFor(String discordChannelId) {
        IncomingWebhookClient cached = clientsByChannelId.get(discordChannelId);
        if (cached != null) {
            return CompletableFuture.completedFuture(Optional.of(cached));
        }

        Optional<JDA> jdaOpt = gateway.jda();
        if (jdaOpt.isEmpty()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        TextChannel channel = jdaOpt.get().getTextChannelById(discordChannelId);
        if (channel == null) {
            logger.warning("Discord channel " + discordChannelId + " no longer exists; cannot relay chat into it.");
            return CompletableFuture.completedFuture(Optional.empty());
        }

        CompletableFuture<Optional<IncomingWebhookClient>> future = new CompletableFuture<>();
        channel.retrieveWebhooks().queue(webhooks -> {
            Webhook existing = webhooks.stream()
                    .filter(webhook -> WEBHOOK_NAME.equals(webhook.getName()))
                    .findFirst()
                    .orElse(null);
            if (existing != null) {
                future.complete(Optional.of(cacheClient(discordChannelId, existing, jdaOpt.get())));
                return;
            }
            channel.createWebhook(WEBHOOK_NAME).queue(
                    created -> future.complete(Optional.of(cacheClient(discordChannelId, created, jdaOpt.get()))),
                    future::completeExceptionally);
        }, future::completeExceptionally);
        return future;
    }

    private IncomingWebhookClient cacheClient(String discordChannelId, Webhook webhook, JDA jda) {
        IncomingWebhookClient client = WebhookClient.createClient(jda, webhook.getId(), webhook.getToken());
        clientsByChannelId.put(discordChannelId, client);
        return client;
    }
}
