package fr.mathildeuh.sosstaff.storage.migration;

import com.zaxxer.hikari.HikariDataSource;
import fr.mathildeuh.sosstaff.storage.sqlite.SqliteDataSourceFactory;
import fr.mathildeuh.sosstaff.storage.sqlite.SqliteMigrations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationRunnerTest {

    private HikariDataSource dataSource;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        dataSource = SqliteDataSourceFactory.create(tempDir.resolve("migration-test.db"));
    }

    @AfterEach
    void tearDown() {
        dataSource.close();
    }

    @Test
    void createsAllTablesAndRecordsTheSchemaVersion() throws SQLException {
        new MigrationRunner(dataSource, SqliteMigrations.all()).run();

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT version FROM schema_version")) {
            assertTrue(resultSet.next());
            assertEquals(1, resultSet.getInt("version"));
        }

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            assertDoesNotThrow(() -> statement.execute("SELECT * FROM tickets LIMIT 0"));
            assertDoesNotThrow(() -> statement.execute("SELECT * FROM ticket_messages LIMIT 0"));
            assertDoesNotThrow(() -> statement.execute("SELECT * FROM blacklist LIMIT 0"));
        }
    }

    @Test
    void runningTheMigrationsTwiceDoesNotReapplyThem() throws SQLException {
        MigrationRunner runner = new MigrationRunner(dataSource, SqliteMigrations.all());
        runner.run();

        assertDoesNotThrow(runner::run);
    }
}
