package fr.mathildeuh.sosstaff.gui;

import fr.mathildeuh.sosstaff.config.CategoryConfig;
import fr.mathildeuh.sosstaff.config.ConfigManager;
import fr.mathildeuh.sosstaff.config.CreationMode;
import fr.mathildeuh.sosstaff.lang.LangManager;
import fr.mathildeuh.sosstaff.lang.Message;
import fr.mathildeuh.sosstaff.ticket.TicketCreationCoordinator;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CreationMenuTest {

    private CreationMenu creationMenu;
    private Player player;

    @BeforeEach
    void setUp() {
        ServerMock server = MockBukkit.mock();
        player = server.addPlayer();

        CategoryConfig bug = new CategoryConfig("bug", "Bug", Material.BOOK, "#5865F2", CreationMode.PRESET,
                List.of("Duplication", "Crash"), null, null);
        CategoryConfig report = new CategoryConfig("report", "Report", Material.IRON_SWORD, "#ED4245",
                CreationMode.FREE_INPUT, List.of(), null, "creation.report.prompt");
        Map<String, CategoryConfig> categories = new LinkedHashMap<>();
        categories.put("bug", bug);
        categories.put("report", report);

        ConfigManager configManager = mock(ConfigManager.class);
        when(configManager.categories()).thenReturn(categories);
        when(configManager.creationMode()).thenReturn(CreationMode.BOTH);

        LangManager langManager = mock(LangManager.class);
        when(langManager.get(any(Message.class), anyMap())).thenReturn("New Ticket");

        JavaPlugin plugin = mock(JavaPlugin.class);
        TicketCreationCoordinator coordinator = mock(TicketCreationCoordinator.class);
        AnvilInputGui anvilInputGui = mock(AnvilInputGui.class);
        PendingChatPrompts pendingChatPrompts = new PendingChatPrompts();

        creationMenu = new CreationMenu(plugin, configManager, langManager, coordinator, anvilInputGui, pendingChatPrompts);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void openBuildsOneSlotPerCategory() {
        creationMenu.open(player);

        var topInventory = player.getOpenInventory().getTopInventory();
        CreationMenuHolder holder = assertInstanceOf(CreationMenuHolder.class, topInventory.getHolder());
        assertEquals(CreationMenuHolder.Stage.CATEGORY, holder.stage());
        assertEquals("bug", holder.categoryIdAt(0));
        assertEquals("report", holder.categoryIdAt(1));
        assertEquals(Material.BOOK, topInventory.getItem(0).getType());
        assertEquals(Material.IRON_SWORD, topInventory.getItem(1).getType());
    }
}
