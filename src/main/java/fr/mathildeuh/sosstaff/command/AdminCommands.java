package fr.mathildeuh.sosstaff.command;

import fr.mathildeuh.sosstaff.config.ConfigValidationException;
import fr.mathildeuh.sosstaff.config.ReloadService;
import fr.mathildeuh.sosstaff.gui.AdminPanel;
import fr.mathildeuh.sosstaff.lang.LangManager;
import fr.mathildeuh.sosstaff.lang.Message;
import fr.mathildeuh.sosstaff.ticket.GdprService;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.paper.util.sender.PlayerSource;
import org.incendo.cloud.paper.util.sender.Source;
import org.incendo.cloud.parser.standard.StringParser;

import java.util.Map;
import java.util.Optional;

public final class AdminCommands {

    private static final String PANEL_PERMISSION = "sosstaff.admin.panel";
    private static final String RELOAD_PERMISSION = "sosstaff.admin.reload";
    private static final String GDPR_PERMISSION = "sosstaff.admin.gdpr";

    private final JavaPlugin plugin;
    private final AdminPanel adminPanel;
    private final ReloadService reloadService;
    private final GdprService gdprService;
    private final LangManager langManager;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public AdminCommands(JavaPlugin plugin, AdminPanel adminPanel, ReloadService reloadService,
                          GdprService gdprService, LangManager langManager) {
        this.plugin = plugin;
        this.adminPanel = adminPanel;
        this.reloadService = reloadService;
        this.gdprService = gdprService;
        this.langManager = langManager;
    }

    public void register(PaperCommandManager<Source> commandManager) {
        var root = commandManager.commandBuilder("sostaff").senderType(PlayerSource.class);

        commandManager.command(root.literal("panel")
                .permission(PANEL_PERMISSION)
                .handler(context -> adminPanel.open(context.sender().source(), 0, Optional.empty())));

        commandManager.command(root.literal("reload")
                .permission(RELOAD_PERMISSION)
                .handler(context -> reload(context.sender().source())));

        commandManager.command(root.literal("gdpr").literal("erase")
                .permission(GDPR_PERMISSION)
                .required("player", StringParser.<Source>stringParser())
                .handler(context -> eraseGdprData(context.sender().source(), context.get("player"))));
    }

    private void reload(Player sender) {
        try {
            reloadService.reload();
            send(sender, Message.GENERAL_RELOAD_SUCCESS, Map.of());
        } catch (ConfigValidationException e) {
            send(sender, Message.GENERAL_RELOAD_FAILURE, Map.of("error", String.valueOf(e.getMessage())));
        }
    }

    private void eraseGdprData(Player sender, String playerName) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        gdprService.eraseAllDataFor(target.getUniqueId())
                .thenAccept(count -> Bukkit.getScheduler().runTask(plugin, () ->
                        send(sender, Message.ADMIN_GDPR_ERASED, Map.of("count", String.valueOf(count), "player", playerName))));
    }

    private void send(Player player, Message message, Map<String, String> placeholders) {
        player.sendMessage(miniMessage.deserialize(langManager.get(message, placeholders)));
    }
}
