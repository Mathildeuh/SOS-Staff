package fr.mathildeuh.sosstaff.storage.migration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Comparator;
import java.util.List;

public final class MigrationRunner {

    private final DataSource dataSource;
    private final List<Migration> migrations;

    public MigrationRunner(DataSource dataSource, List<Migration> migrations) {
        this.dataSource = dataSource;
        this.migrations = migrations.stream()
                .sorted(Comparator.comparingInt(Migration::version))
                .toList();
    }

    public void run() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            ensureSchemaVersionTable(connection);
            int currentVersion = readCurrentVersion(connection);

            for (Migration migration : migrations) {
                if (migration.version() <= currentVersion) {
                    continue;
                }

                connection.setAutoCommit(false);
                try {
                    migration.apply(connection);
                    writeVersion(connection, migration.version());
                    connection.commit();
                } catch (SQLException e) {
                    connection.rollback();
                    throw e;
                } finally {
                    connection.setAutoCommit(true);
                }
            }
        }
    }

    private void ensureSchemaVersionTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER NOT NULL)");
        }
    }

    private int readCurrentVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT version FROM schema_version LIMIT 1")) {
            return resultSet.next() ? resultSet.getInt("version") : 0;
        }
    }

    private void writeVersion(Connection connection, int version) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM schema_version");
            statement.executeUpdate("INSERT INTO schema_version (version) VALUES (" + version + ")");
        }
    }
}
