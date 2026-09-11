package fr.mathildeuh.sosstaff.storage.sqlite;

import com.zaxxer.hikari.HikariDataSource;
import fr.mathildeuh.sosstaff.storage.migration.MigrationRunner;
import fr.mathildeuh.sosstaff.ticket.Ticket;
import fr.mathildeuh.sosstaff.ticket.TicketPriority;
import fr.mathildeuh.sosstaff.ticket.TicketStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteTicketRepositoryTest {

    private HikariDataSource dataSource;
    private ExecutorService executor;
    private SqliteTicketRepository repository;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws SQLException {
        dataSource = SqliteDataSourceFactory.create(tempDir.resolve("tickets-test.db"));
        new MigrationRunner(dataSource, SqliteMigrations.all()).run();
        executor = Executors.newFixedThreadPool(2);
        repository = new SqliteTicketRepository(dataSource, executor);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        dataSource.close();
    }

    @Test
    void createsATicketAndFindsItById() throws Exception {
        UUID playerUuid = UUID.randomUUID();

        Ticket created = get(repository.create(playerUuid, "bug", TicketPriority.MEDIUM));

        assertEquals(playerUuid, created.playerUuid());
        assertEquals(TicketStatus.OPEN, created.status());

        Optional<Ticket> found = get(repository.findById(created.id()));
        assertTrue(found.isPresent());
        assertEquals(created.id(), found.get().id());
    }

    @Test
    void findActiveByPlayerIgnoresClosedTickets() throws Exception {
        UUID playerUuid = UUID.randomUUID();
        Ticket first = get(repository.create(playerUuid, "bug", TicketPriority.LOW));
        get(repository.close(first.id(), "resolved"));

        Ticket second = get(repository.create(playerUuid, "report", TicketPriority.HIGH));

        Optional<Ticket> active = get(repository.findActiveByPlayer(playerUuid));

        assertTrue(active.isPresent());
        assertEquals(second.id(), active.get().id());
    }

    @Test
    void countActiveByPlayerExcludesClosedAndArchivedTickets() throws Exception {
        UUID playerUuid = UUID.randomUUID();
        Ticket ticket = get(repository.create(playerUuid, "bug", TicketPriority.LOW));

        assertEquals(1, get(repository.countActiveByPlayer(playerUuid)));

        get(repository.close(ticket.id(), "done"));

        assertEquals(0, get(repository.countActiveByPlayer(playerUuid)));
    }

    @Test
    void claimSetsStatusAndClaimedBy() throws Exception {
        UUID playerUuid = UUID.randomUUID();
        UUID staffUuid = UUID.randomUUID();
        Ticket ticket = get(repository.create(playerUuid, "bug", TicketPriority.MEDIUM));

        get(repository.claim(ticket.id(), staffUuid));

        Ticket claimed = get(repository.findById(ticket.id())).orElseThrow();
        assertEquals(TicketStatus.CLAIMED, claimed.status());
        assertEquals(staffUuid, claimed.claimedBy());
    }

    @Test
    void closeSetsClosedAtAndCloseReason() throws Exception {
        Ticket ticket = get(repository.create(UUID.randomUUID(), "bug", TicketPriority.MEDIUM));

        get(repository.close(ticket.id(), "fixed in survival"));

        Ticket closed = get(repository.findById(ticket.id())).orElseThrow();
        assertEquals(TicketStatus.CLOSED, closed.status());
        assertEquals("fixed in survival", closed.closeReason());
        assertNotNull(closed.closedAt());
    }

    @Test
    void findHistoryByPlayerReturnsEveryTicketRegardlessOfStatus() throws Exception {
        UUID playerUuid = UUID.randomUUID();
        get(repository.create(playerUuid, "bug", TicketPriority.LOW));
        Ticket second = get(repository.create(playerUuid, "report", TicketPriority.HIGH));
        get(repository.close(second.id(), "done"));

        List<Ticket> history = get(repository.findHistoryByPlayer(playerUuid));

        assertEquals(2, history.size());
    }

    private static <T> T get(java.util.concurrent.CompletableFuture<T> future)
            throws ExecutionException, InterruptedException, TimeoutException {
        return future.get(5, TimeUnit.SECONDS);
    }
}
