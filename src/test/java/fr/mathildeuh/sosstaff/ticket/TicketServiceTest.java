package fr.mathildeuh.sosstaff.ticket;

import com.zaxxer.hikari.HikariDataSource;
import fr.mathildeuh.sosstaff.config.ConfigManager;
import fr.mathildeuh.sosstaff.storage.migration.MigrationRunner;
import fr.mathildeuh.sosstaff.storage.sqlite.SqliteDataSourceFactory;
import fr.mathildeuh.sosstaff.storage.sqlite.SqliteMigrations;
import fr.mathildeuh.sosstaff.storage.sqlite.SqliteTicketRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TicketServiceTest {

    private HikariDataSource dataSource;
    private ExecutorService executor;
    private SqliteTicketRepository repository;
    private ConfigManager configManager;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws SQLException {
        dataSource = SqliteDataSourceFactory.create(tempDir.resolve("ticket-service-test.db"));
        new MigrationRunner(dataSource, SqliteMigrations.all()).run();
        executor = Executors.newFixedThreadPool(2);
        repository = new SqliteTicketRepository(dataSource, executor);

        configManager = mock(ConfigManager.class);
        when(configManager.maxOpenTicketsPerPlayer()).thenReturn(1);
        when(configManager.cooldownAfterCloseSeconds()).thenReturn(30);
        when(configManager.escalationEnabled()).thenReturn(true);
        when(configManager.noClaimAfterMinutes()).thenReturn(15);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        dataSource.close();
    }

    @Test
    void createsATicketWhenUnderTheAntiSpamLimit() throws Exception {
        TicketService service = new TicketService(repository, configManager);

        TicketCreationResult result = get(service.createTicket(UUID.randomUUID(), "bug", TicketPriority.MEDIUM, false));

        assertInstanceOf(TicketCreationResult.Created.class, result);
    }

    @Test
    void rejectsCreationWithTheExistingTicketWhenAtTheOpenTicketLimit() throws Exception {
        TicketService service = new TicketService(repository, configManager);
        UUID playerUuid = UUID.randomUUID();
        TicketCreationResult first = get(service.createTicket(playerUuid, "bug", TicketPriority.MEDIUM, false));
        Ticket firstTicket = ((TicketCreationResult.Created) first).ticket();

        TicketCreationResult second = get(service.createTicket(playerUuid, "report", TicketPriority.LOW, false));

        var rejection = assertInstanceOf(TicketCreationResult.RejectedTooManyOpenTickets.class, second);
        assertEquals(firstTicket.id(), rejection.existingTicket().id());
    }

    @Test
    void bypassAntiSpamIgnoresTheOpenTicketLimit() throws Exception {
        TicketService service = new TicketService(repository, configManager);
        UUID playerUuid = UUID.randomUUID();
        get(service.createTicket(playerUuid, "bug", TicketPriority.MEDIUM, false));

        TicketCreationResult second = get(service.createTicket(playerUuid, "report", TicketPriority.LOW, true));

        assertInstanceOf(TicketCreationResult.Created.class, second);
    }

    @Test
    void rejectsCreationDuringTheCooldownAfterAClose() throws Exception {
        UUID playerUuid = UUID.randomUUID();
        Ticket created = get(repository.create(playerUuid, "bug", TicketPriority.LOW));
        get(repository.close(created.id(), "resolved"));
        Instant closedAt = get(repository.findById(created.id())).orElseThrow().closedAt();

        TicketService duringCooldown = new TicketService(repository, configManager,
                Clock.fixed(closedAt.plusSeconds(5), ZoneOffset.UTC));

        TicketCreationResult result = get(duringCooldown.createTicket(playerUuid, "report", TicketPriority.LOW, false));

        var rejection = assertInstanceOf(TicketCreationResult.RejectedCooldownActive.class, result);
        assertEquals(25, rejection.remaining().toSeconds());
    }

    @Test
    void allowsCreationOnceTheCooldownHasElapsed() throws Exception {
        UUID playerUuid = UUID.randomUUID();
        Ticket created = get(repository.create(playerUuid, "bug", TicketPriority.LOW));
        get(repository.close(created.id(), "resolved"));
        Instant closedAt = get(repository.findById(created.id())).orElseThrow().closedAt();

        TicketService afterCooldown = new TicketService(repository, configManager,
                Clock.fixed(closedAt.plusSeconds(35), ZoneOffset.UTC));

        TicketCreationResult result = get(afterCooldown.createTicket(playerUuid, "report", TicketPriority.LOW, false));

        assertInstanceOf(TicketCreationResult.Created.class, result);
    }

    @Test
    void claimSetsStatusAndReturnsTheUpdatedTicket() throws Exception {
        TicketService service = new TicketService(repository, configManager);
        Ticket ticket = ((TicketCreationResult.Created) get(
                service.createTicket(UUID.randomUUID(), "bug", TicketPriority.MEDIUM, false))).ticket();
        UUID staffUuid = UUID.randomUUID();

        Ticket claimed = get(service.claim(ticket.id(), staffUuid));

        assertEquals(TicketStatus.CLAIMED, claimed.status());
        assertEquals(staffUuid, claimed.claimedBy());
    }

    @Test
    void findTicketsNeedingEscalationReturnsOnlyOldUnclaimedOpenTickets() throws Exception {
        Ticket oldEnough = get(repository.create(UUID.randomUUID(), "bug", TicketPriority.MEDIUM));
        Ticket claimed = get(repository.create(UUID.randomUUID(), "bug", TicketPriority.MEDIUM));
        get(repository.claim(claimed.id(), UUID.randomUUID()));
        Instant now = get(repository.findById(oldEnough.id())).orElseThrow().createdAt();

        TicketService service = new TicketService(repository, configManager, Clock.fixed(now.plusSeconds(20 * 60), ZoneOffset.UTC));

        List<Ticket> needingEscalation = get(service.findTicketsNeedingEscalation());

        assertEquals(1, needingEscalation.size());
        assertEquals(oldEnough.id(), needingEscalation.getFirst().id());
    }

    @Test
    void findTicketsNeedingEscalationExcludesTicketsYoungerThanTheThreshold() throws Exception {
        Ticket recent = get(repository.create(UUID.randomUUID(), "bug", TicketPriority.MEDIUM));
        Instant now = get(repository.findById(recent.id())).orElseThrow().createdAt();

        TicketService service = new TicketService(repository, configManager, Clock.fixed(now.plusSeconds(5 * 60), ZoneOffset.UTC));

        assertTrue(get(service.findTicketsNeedingEscalation()).isEmpty());
    }

    @Test
    void findTicketsNeedingEscalationReturnsNothingWhenEscalationIsDisabled() throws Exception {
        when(configManager.escalationEnabled()).thenReturn(false);
        get(repository.create(UUID.randomUUID(), "bug", TicketPriority.MEDIUM));

        TicketService service = new TicketService(repository, configManager,
                Clock.fixed(Instant.now().plusSeconds(60 * 60), ZoneOffset.UTC));

        assertTrue(get(service.findTicketsNeedingEscalation()).isEmpty());
    }

    private static <T> T get(java.util.concurrent.CompletableFuture<T> future) throws Exception {
        return future.get(5, TimeUnit.SECONDS);
    }
}
