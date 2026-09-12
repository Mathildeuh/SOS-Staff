package fr.mathildeuh.sosstaff.api;

import com.zaxxer.hikari.HikariDataSource;
import fr.mathildeuh.sosstaff.api.event.TicketCreateEvent;
import fr.mathildeuh.sosstaff.config.ConfigManager;
import fr.mathildeuh.sosstaff.discord.ChannelOrchestrator;
import fr.mathildeuh.sosstaff.discord.WebhookRelay;
import fr.mathildeuh.sosstaff.session.LiveChatSessionManager;
import fr.mathildeuh.sosstaff.storage.migration.MigrationRunner;
import fr.mathildeuh.sosstaff.storage.sqlite.SqliteDataSourceFactory;
import fr.mathildeuh.sosstaff.storage.sqlite.SqliteMigrations;
import fr.mathildeuh.sosstaff.storage.sqlite.SqliteTicketMessageRepository;
import fr.mathildeuh.sosstaff.storage.sqlite.SqliteTicketRepository;
import fr.mathildeuh.sosstaff.ticket.Ticket;
import fr.mathildeuh.sosstaff.ticket.TicketCreationCoordinator;
import fr.mathildeuh.sosstaff.ticket.TicketMessageRepository;
import fr.mathildeuh.sosstaff.ticket.TicketService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SosStaffAPIImplTest {

    private HikariDataSource dataSource;
    private ExecutorService executor;
    private SosStaffAPIImpl api;
    private TicketService ticketService;
    private PluginMock plugin;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws SQLException {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("SOS-Staff");

        dataSource = SqliteDataSourceFactory.create(tempDir.resolve("sosstaffapi-test.db"));
        new MigrationRunner(dataSource, SqliteMigrations.all()).run();
        executor = Executors.newFixedThreadPool(2);
        SqliteTicketRepository ticketRepository = new SqliteTicketRepository(dataSource, executor);
        TicketMessageRepository messageRepository = new SqliteTicketMessageRepository(dataSource, executor);

        ConfigManager configManager = mock(ConfigManager.class);
        when(configManager.maxOpenTicketsPerPlayer()).thenReturn(1);
        when(configManager.cooldownAfterCloseSeconds()).thenReturn(30);
        ticketService = new TicketService(ticketRepository, configManager);

        ChannelOrchestrator channelOrchestrator = mock(ChannelOrchestrator.class);
        when(channelOrchestrator.createChannelForTicket(any(), any())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        TicketCreationCoordinator coordinator = new TicketCreationCoordinator(
                ticketService, channelOrchestrator, mock(LiveChatSessionManager.class), messageRepository, mock(WebhookRelay.class));

        api = new SosStaffAPIImpl(ticketService, coordinator);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        dataSource.close();
        MockBukkit.unmock();
    }

    @Test
    void createTicketPersistsAndPopulatesTheActiveTicketCache() throws Exception {
        UUID playerUuid = UUID.randomUUID();

        Ticket created = get(api.createTicket(playerUuid, "bug", "Something is broken"));

        assertEquals(playerUuid, created.playerUuid());
        assertEquals(Optional.of(created), api.getActiveTicket(playerUuid));
    }

    @Test
    void getTicketHistoryReflectsCreationOnceHistoryHasBeenLookedUpAtLeastOnce() throws Exception {
        UUID playerUuid = UUID.randomUUID();
        get(api.createTicket(playerUuid, "bug", "Something is broken"));
        assertTrue(api.getTicketHistory(playerUuid).isEmpty(), "history cache should be empty before any lookup");

        get(ticketService.findHistory(playerUuid));

        assertEquals(1, api.getTicketHistory(playerUuid).size());
    }

    @Test
    void createTicketRejectsWhenAnotherPluginCancelsTheCreateEvent() {
        Listener canceller = new Listener() {
            @EventHandler
            public void onCreate(TicketCreateEvent event) {
                event.setCancelled(true);
            }
        };
        plugin.getServer().getPluginManager().registerEvents(canceller, plugin);

        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> get(api.createTicket(UUID.randomUUID(), "bug", "Should be blocked")));

        assertInstanceOf(TicketCreationRejectedException.class, failure.getCause());
    }

    @Test
    void createTicketRejectsPastTheAntiSpamLimit() throws Exception {
        UUID playerUuid = UUID.randomUUID();
        get(api.createTicket(playerUuid, "bug", "First issue"));

        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> get(api.createTicket(playerUuid, "report", "Second issue")));

        assertInstanceOf(TicketCreationRejectedException.class, failure.getCause());
    }

    @Test
    void getActiveTicketIsEmptyForAPlayerNeverLookedUp() {
        assertTrue(api.getActiveTicket(UUID.randomUUID()).isEmpty());
    }

    private static <T> T get(CompletableFuture<T> future) throws Exception {
        return future.get(5, TimeUnit.SECONDS);
    }
}
