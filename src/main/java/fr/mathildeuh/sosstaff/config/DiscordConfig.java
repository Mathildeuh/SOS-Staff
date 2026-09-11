package fr.mathildeuh.sosstaff.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record DiscordConfig(
        String token,
        Category category,
        Channel channel,
        Permissions permissions,
        Mentions mentions,
        OnClose onClose,
        Map<String, ActionButton> actionButtons
) {

    private static final String TOKEN_ENV_VAR = "SOSSTAFF_DISCORD_TOKEN";

    public static DiscordConfig fromSection(ConfigurationSection section) throws ConfigValidationException {
        if (section == null) {
            throw new ConfigValidationException("discord section is required");
        }

        String envToken = System.getenv(TOKEN_ENV_VAR);
        String token = (envToken != null && !envToken.isBlank()) ? envToken : section.getString("token", "");

        Category category = Category.fromSection(requireSection(section, "category"));
        Channel channel = Channel.fromSection(requireSection(section, "channel"));
        Permissions permissions = Permissions.fromSection(requireSection(section, "permissions"));
        Mentions mentions = Mentions.fromSection(requireSection(section, "mentions"));
        OnClose onClose = OnClose.fromSection(requireSection(section, "on-close"));

        Map<String, ActionButton> actionButtons = new LinkedHashMap<>();
        ConfigurationSection buttonsSection = section.getConfigurationSection("action-buttons");
        if (buttonsSection != null) {
            for (String buttonId : buttonsSection.getKeys(false)) {
                actionButtons.put(buttonId, ActionButton.fromSection(buttonId, buttonsSection.getConfigurationSection(buttonId)));
            }
        }

        return new DiscordConfig(token, category, channel, permissions, mentions, onClose, Map.copyOf(actionButtons));
    }

    private static ConfigurationSection requireSection(ConfigurationSection parent, String key) throws ConfigValidationException {
        ConfigurationSection child = parent.getConfigurationSection(key);
        if (child == null) {
            throw new ConfigValidationException("discord." + key + " section is required");
        }
        return child;
    }

    public boolean hasToken() {
        return token != null && !token.isBlank();
    }

    public record Category(String name, boolean autoCreate, String archivedCategoryName) {
        static Category fromSection(ConfigurationSection section) {
            return new Category(
                    section.getString("name", "Tickets"),
                    section.getBoolean("auto-create", true),
                    section.getString("archived-category-name", "Archived Tickets")
            );
        }
    }

    public record Channel(String nameFormat, String topicFormat) {
        static Channel fromSection(ConfigurationSection section) {
            return new Channel(
                    section.getString("name-format", "ticket-%id%-%player%"),
                    section.getString("topic-format", "Ticket #%id% - %category% - Priority: %priority%")
            );
        }
    }

    public record Permissions(List<String> staffRoleIds, boolean hideFromEveryone, boolean creatorCanSee) {
        static Permissions fromSection(ConfigurationSection section) {
            return new Permissions(
                    List.copyOf(section.getStringList("staff-roles")),
                    section.getBoolean("hide-from-everyone", true),
                    section.getBoolean("creator-can-see", true)
            );
        }
    }

    public record Mentions(OnCreate onCreate, OnEscalate onEscalate) {
        static Mentions fromSection(ConfigurationSection section) {
            ConfigurationSection onCreateSection = section.getConfigurationSection("on-create");
            ConfigurationSection onEscalateSection = section.getConfigurationSection("on-escalate");
            return new Mentions(
                    OnCreate.fromSection(onCreateSection),
                    OnEscalate.fromSection(onEscalateSection)
            );
        }
    }

    public record OnCreate(List<String> roleIds, String message) {
        static OnCreate fromSection(ConfigurationSection section) {
            if (section == null) {
                return new OnCreate(List.of(), "");
            }
            return new OnCreate(List.copyOf(section.getStringList("roles")), section.getString("message", ""));
        }
    }

    public record OnEscalate(String message) {
        static OnEscalate fromSection(ConfigurationSection section) {
            return new OnEscalate(section == null ? "" : section.getString("message", ""));
        }
    }

    public enum OnCloseAction { ARCHIVE, DELETE }

    public record OnClose(OnCloseAction action, boolean lockChannel, int autoDeleteAfterDays) {
        static OnClose fromSection(ConfigurationSection section) throws ConfigValidationException {
            String raw = section.getString("action", "ARCHIVE");
            OnCloseAction action;
            try {
                action = OnCloseAction.valueOf(raw.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ConfigValidationException("discord.on-close.action '" + raw + "' must be ARCHIVE or DELETE");
            }
            return new OnClose(action, section.getBoolean("lock-channel", true), section.getInt("auto-delete-after-days", 7));
        }
    }

    public record ActionButton(
            String id,
            boolean enabled,
            String label,
            String command,
            String permission,
            boolean requiresOnline,
            boolean confirm,
            String kickReason
    ) {
        static ActionButton fromSection(String id, ConfigurationSection section) throws ConfigValidationException {
            if (section == null) {
                throw new ConfigValidationException("discord.action-buttons." + id + " section is empty");
            }
            String label = section.getString("label", "").trim();
            String command = section.getString("command", "").trim();
            String permission = section.getString("permission", "").trim();
            if (label.isEmpty() || command.isEmpty() || permission.isEmpty()) {
                throw new ConfigValidationException("discord.action-buttons." + id + " requires label, command and permission");
            }
            return new ActionButton(
                    id,
                    section.getBoolean("enabled", true),
                    label,
                    command,
                    permission,
                    section.getBoolean("requires-online", true),
                    section.getBoolean("confirm", false),
                    section.getString("kick-reason")
            );
        }
    }
}
