package fr.mathildeuh.sosstaff.ticket;

import com.zaxxer.hikari.HikariDataSource;
import fr.mathildeuh.sosstaff.config.ConfigManager;
import fr.mathildeuh.sosstaff.storage.migration.MigrationRunner;
import fr.mathildeuh.sosstaff.storage.sqlite.SqliteDataSourceFactory;
import fr.mathildeuh.sosstaff.storage.sqlite.SqliteMigrations;
import fr.mathildeuh.sosstaff.storage.sqlite.SqliteTicketMessageRepository;
import fr.mathildeuh.sosstaff.storage.sqlite.SqliteTicketRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GdprServiceTest {

    private HikariDataSource dataSource;
    private ExecutorService executor;
    private SqliteTicketRepository ticketRepository;
    private SqliteTicketMessageRepository messageRepository;
    private ConfigManager configManager;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws SQLException {
        dataSource = SqliteDataSourceFactory.create(tempDir.resolve("gdpr-test.db"));
        new MigrationRunner(dataSource, SqliteMigrations.all()).run();
        executor = Executors.newFixedThreadPool(2);
        ticketRepository = new SqliteTicketRepository(dataSource, executor);
        messageRepository = new SqliteTicketMessageRepository(dataSource, executor);
        configManager = mock(ConfigManager.class);
        when(configManager.gdprRetentionDays()).thenReturn(90);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        dataSource.close();
    }

    @Test
    void erasesOnlyTheTargetPlayersTicketsAndMessages() throws Exception {
        UUID target = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        Ticket targetTicket = get(ticketRepository.create(target, "bug", TicketPriority.LOW));
        Ticket otherTicket = get(ticketRepository.create(other, "bug", TicketPriority.LOW));
        get(messageRepository.append(targetTicket.id(), target, "Target", false, "help"));
        get(messageRepository.append(otherTicket.id(), other, "Other", false, "help too"));

        GdprService gdprService = new GdprService(ticketRepository, messageRepository, configManager);
        int erased = get(gdprService.eraseAllDataFor(target));

        assertEquals(1, erased);
        assertTrue(get(ticketRepository.findById(targetTicket.id())).isEmpty());
        assertTrue(get(messageRepository.findByTicket(targetTicket.id())).isEmpty());
        assertTrue(get(ticketRepository.findById(otherTicket.id())).isPresent());
        assertEquals(1, get(messageRepository.findByTicket(otherTicket.id())).size());
    }

    @Test
    void purgesOnlyMessagesOlderThanTheRetentionWindow() throws Exception {
        Ticket ticket = get(ticketRepository.create(UUID.randomUUID(), "bug", TicketPriority.LOW));
        get(messageRepository.append(ticket.id(), UUID.randomUUID(), "Someone", false, "a message"));
        Instant sentAt = get(messageRepository.findByTicket(ticket.id())).getFirst().sentAt();

        // append() always stamps sent_at with the real wall clock, so "age" is simulated by
        // moving the *service's* clock instead of trying to backdate the row.
        Clock stillWithinRetention = Clock.fixed(sentAt.plus(Duration.ofDays(89)), ZoneOffset.UTC);
        GdprService tooEarly = new GdprService(ticketRepository, messageRepository, configManager, stillWithinRetention);
        assertEquals(0, get(tooEarly.purgeExpiredTranscripts()));

        Clock pastRetention = Clock.fixed(sentAt.plus(Duration.ofDays(91)), ZoneOffset.UTC);
        GdprService afterWindow = new GdprService(ticketRepository, messageRepository, configManager, pastRetention);
        int purged = get(afterWindow.purgeExpiredTranscripts());

        assertEquals(1, purged);
        assertTrue(get(messageRepository.findByTicket(ticket.id())).isEmpty());
    }

    private static <T> T get(CompletableFuture<T> future) throws Exception {
        return future.get(5, TimeUnit.SECONDS);
    }
}
