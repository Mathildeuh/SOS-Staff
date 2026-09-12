package fr.mathildeuh.sosstaff.discord;

import fr.mathildeuh.sosstaff.config.CategoryConfig;
import fr.mathildeuh.sosstaff.config.ConfigManager;
import fr.mathildeuh.sosstaff.config.DiscordConfig;
import fr.mathildeuh.sosstaff.ticket.Ticket;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.restaction.ChannelAction;
import org.bukkit.Bukkit;

import net.dv8tion.jda.api.components.actionrow.ActionRow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Creates the private Discord channel for a newly opened ticket. There is no Minecraft-to-Discord
 * account linking anywhere in this project, so "creator-can-see" in config.yml cannot be honored as
 * a per-member permission override - there is no Discord user id to grant it to. The player's side of
 * the conversation happens entirely in-game through the webhook-backed live chat relay (a later phase),
 * never by the player opening Discord themselves, so the channel is only made visible to staff roles.
 */
public final class ChannelOrchestrator {

    private final DiscordGateway gateway;
    private final ConfigManager configManager;
    private final Logger logger;

    public ChannelOrchestrator(DiscordGateway gateway, ConfigManager configManager, Logger logger) {
        this.gateway = gateway;
        this.configManager = configManager;
        this.logger = logger;
    }

    public CompletableFuture<Optional<String>> createChannelForTicket(Ticket ticket, String playerName) {
        Optional<Guild> guildOpt = gateway.primaryGuild();
        if (guildOpt.isEmpty()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        Guild guild = guildOpt.get();
        DiscordConfig discordConfig = configManager.discord();

        return findOrCreateCategory(guild, discordConfig.category())
                .thenCompose(category -> createChannel(guild, category, ticket, playerName, discordConfig))
                .exceptionally(throwable -> {
                    logger.severe("Failed to create the Discord channel for ticket #" + ticket.id() + ": " + throwable);
                    return Optional.empty();
                });
    }

    private CompletableFuture<Category> findOrCreateCategory(Guild guild, DiscordConfig.Category categoryConfig) {
        List<Category> existing = guild.getCategoryCache().getElementsByName(categoryConfig.name(), true);
        if (!existing.isEmpty()) {
            return CompletableFuture.completedFuture(existing.getFirst());
        }
        if (!categoryConfig.autoCreate()) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "discord.category '" + categoryConfig.name() + "' does not exist and auto-create is disabled"));
        }

        CompletableFuture<Category> future = new CompletableFuture<>();
        guild.createCategory(categoryConfig.name()).queue(future::complete, future::completeExceptionally);
        return future;
    }

    private CompletableFuture<Optional<String>> createChannel(
            Guild guild, Category category, Ticket ticket, String playerName, DiscordConfig discordConfig) {
        String channelName = formatChannelName(discordConfig.channel().nameFormat(), ticket, playerName);
        String topic = formatTopic(discordConfig.channel().topicFormat(), ticket);

        ChannelAction<TextChannel> action = guild.createTextChannel(channelName, category).setTopic(topic);

        if (discordConfig.permissions().hideFromEveryone()) {
            action = action.addRolePermissionOverride(
                    guild.getPublicRole().getIdLong(), Collections.emptyList(), List.of(Permission.VIEW_CHANNEL));
        }

        List<Permission> staffAllow = List.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY);
        for (String roleId : discordConfig.permissions().staffRoleIds()) {
            try {
                action = action.addRolePermissionOverride(Long.parseLong(roleId), staffAllow, Collections.emptyList());
            } catch (NumberFormatException e) {
                logger.warning("discord.permissions.staff-roles contains an invalid role id: '" + roleId + "'");
            }
        }

        CompletableFuture<Optional<String>> future = new CompletableFuture<>();
        action.queue(channel -> {
            postInitialMessage(channel, ticket, playerName, discordConfig);
            future.complete(Optional.of(channel.getId()));
        }, future::completeExceptionally);
        return future;
    }

    private void postInitialMessage(TextChannel channel, Ticket ticket, String playerName, DiscordConfig discordConfig) {
        CategoryConfig category = configManager.categories().get(ticket.category());
        boolean targetOnline = Bukkit.getPlayer(ticket.playerUuid()) != null;

        List<ActionRow> buttonRows = new ArrayList<>(List.of(EmbedFactory.managementRow(ticket.id())));
        buttonRows.addAll(EmbedFactory.actionButtonRows(discordConfig.actionButtons(), ticket.id(), targetOnline));

        var onCreate = discordConfig.mentions().onCreate();
        String roleMentions = buildRoleMentions(onCreate.roleIds(), category.pingRoleId());
        String pingMessage = (roleMentions.isBlank() ? "" : roleMentions + " ")
                + onCreate.message().replace("%player%", playerName).replace("%category%", category.displayName());

        channel.sendMessage(pingMessage)
                .addEmbeds(EmbedFactory.ticketEmbed(ticket, category, playerName, null))
                .addComponents(buttonRows)
                .queue();
    }

    static String formatChannelName(String format, Ticket ticket, String playerName) {
        return format.replace("%id%", String.valueOf(ticket.id())).replace("%player%", playerName);
    }

    /**
     * The global on-create roles ping every ticket; a category's own ping-role (if it sets one)
     * pings in addition to those, not instead of them.
     */
    static String buildRoleMentions(List<String> globalRoleIds, String categoryPingRoleId) {
        List<String> roleIds = new ArrayList<>(globalRoleIds);
        if (categoryPingRoleId != null && !categoryPingRoleId.isBlank()) {
            roleIds.add(categoryPingRoleId);
        }
        return roleIds.stream().map(id -> "<@&" + id + ">").collect(Collectors.joining(" "));
    }

    static String formatTopic(String format, Ticket ticket) {
        return format.replace("%id%", String.valueOf(ticket.id()))
                .replace("%category%", ticket.category())
                .replace("%priority%", ticket.priority().name());
    }
}
