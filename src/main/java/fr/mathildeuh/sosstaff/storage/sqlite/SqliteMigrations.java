package fr.mathildeuh.sosstaff.storage.sqlite;

import fr.mathildeuh.sosstaff.storage.migration.Migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

public final class SqliteMigrations {

    private SqliteMigrations() {
    }

    public static List<Migration> all() {
        return List.of(new InitialSchema());
    }

    private static final class InitialSchema implements Migration {

        @Override
        public int version() {
            return 1;
        }

        @Override
        public void apply(Connection connection) throws SQLException {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE tickets (
                            id                  INTEGER PRIMARY KEY AUTOINCREMENT,
                            player_uuid         VARCHAR(36)  NOT NULL,
                            category            VARCHAR(64)  NOT NULL,
                            status              VARCHAR(24)  NOT NULL,
                            priority            VARCHAR(16)  NOT NULL DEFAULT 'MEDIUM',
                            discord_channel_id  VARCHAR(32),
                            claimed_by          VARCHAR(36),
                            created_at          TIMESTAMP    NOT NULL,
                            closed_at           TIMESTAMP,
                            close_reason        TEXT,
                            rating              INTEGER
                        )""");
                statement.execute("CREATE INDEX idx_tickets_player ON tickets(player_uuid)");
                statement.execute("CREATE INDEX idx_tickets_status ON tickets(status)");
                statement.execute("CREATE INDEX idx_tickets_category ON tickets(category)");
                statement.execute("CREATE INDEX idx_tickets_channel ON tickets(discord_channel_id)");

                statement.execute("""
                        CREATE TABLE ticket_messages (
                            id          INTEGER PRIMARY KEY AUTOINCREMENT,
                            ticket_id   INTEGER NOT NULL REFERENCES tickets(id),
                            author_uuid VARCHAR(36),
                            author_name VARCHAR(32) NOT NULL,
                            is_staff    BOOLEAN NOT NULL,
                            content     TEXT NOT NULL,
                            sent_at     TIMESTAMP NOT NULL
                        )""");
                statement.execute("CREATE INDEX idx_messages_ticket ON ticket_messages(ticket_id)");

                statement.execute("""
                        CREATE TABLE blacklist (
                            player_uuid VARCHAR(36) PRIMARY KEY,
                            reason      TEXT,
                            expires_at  TIMESTAMP
                        )""");
            }
        }
    }
}
