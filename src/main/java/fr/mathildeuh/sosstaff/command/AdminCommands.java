package fr.mathildeuh.sosstaff.command;

import fr.mathildeuh.sosstaff.gui.AdminPanel;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.paper.util.sender.PlayerSource;
import org.incendo.cloud.paper.util.sender.Source;

import java.util.Optional;

public final class AdminCommands {

    private static final String PANEL_PERMISSION = "sosstaff.admin.panel";

    private final AdminPanel adminPanel;

    public AdminCommands(AdminPanel adminPanel) {
        this.adminPanel = adminPanel;
    }

    public void register(PaperCommandManager<Source> commandManager) {
        var root = commandManager.commandBuilder("sostaff").senderType(PlayerSource.class);

        commandManager.command(root.literal("panel")
                .permission(PANEL_PERMISSION)
                .handler(context -> adminPanel.open(context.sender().source(), 0, Optional.empty())));
    }
}
