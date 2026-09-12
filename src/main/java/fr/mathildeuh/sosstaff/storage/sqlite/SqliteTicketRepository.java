package fr.mathildeuh.sosstaff.storage.sqlite;

import fr.mathildeuh.sosstaff.ticket.Ticket;
import fr.mathildeuh.sosstaff.ticket.TicketPriority;
import fr.mathildeuh.sosstaff.ticket.TicketRepository;
import fr.mathildeuh.sosstaff.ticket.TicketStatus;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class SqliteTicketRepository implements TicketRepository {

    private final DataSource dataSource;
    private final Executor executor;

    public SqliteTicketRepository(DataSource dataSource, Executor executor) {
        this.dataSource = dataSource;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<Ticket> create(UUID playerUuid, String category, TicketPriority priority) {
        return supply(() -> {
            Instant createdAt = Instant.now();
            String sql = "INSERT INTO tickets (player_uuid, category, status, priority, created_at) VALUES (?, ?, ?, ?, ?)";
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, playerUuid.toString());
                statement.setString(2, category);
                statement.setString(3, TicketStatus.OPEN.name());
                statement.setString(4, priority.name());
                statement.setTimestamp(5, Timestamp.from(createdAt));
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    keys.next();
                    long id = keys.getLong(1);
                    return new Ticket(id, playerUuid, category, TicketStatus.OPEN, priority, null, null, createdAt, null, null, null);
                }
            }
        });
    }

    @Override
    public CompletableFuture<Optional<Ticket>> findById(long id) {
        return supply(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("SELECT * FROM tickets WHERE id = ?")) {
                statement.setLong(1, id);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public CompletableFuture<Optional<Ticket>> findActiveByPlayer(UUID playerUuid) {
        return findOneByPlayer("SELECT * FROM tickets WHERE player_uuid = ? AND status NOT IN ('CLOSED', 'ARCHIVED') "
                + "ORDER BY created_at DESC LIMIT 1", playerUuid);
    }

    @Override
    public CompletableFuture<Optional<Ticket>> findMostRecentClosedByPlayer(UUID playerUuid) {
        return findOneByPlayer("SELECT * FROM tickets WHERE player_uuid = ? AND status IN ('CLOSED', 'ARCHIVED') "
                + "ORDER BY closed_at DESC LIMIT 1", playerUuid);
    }

    private CompletableFuture<Optional<Ticket>> findOneByPlayer(String sql, UUID playerUuid) {
        return supply(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, playerUuid.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public CompletableFuture<List<Ticket>> findHistoryByPlayer(UUID playerUuid) {
        return supply(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT * FROM tickets WHERE player_uuid = ? ORDER BY created_at DESC")) {
                statement.setString(1, playerUuid.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    return mapAll(resultSet);
                }
            }
        });
    }

    @Override
    public CompletableFuture<List<Ticket>> findByStatus(TicketStatus status) {
        return supply(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT * FROM tickets WHERE status = ? ORDER BY created_at ASC")) {
                statement.setString(1, status.name());
                try (ResultSet resultSet = statement.executeQuery()) {
                    return mapAll(resultSet);
                }
            }
        });
    }

    @Override
    public CompletableFuture<List<Ticket>> findPage(Optional<TicketStatus> statusFilter, int page, int pageSize) {
        String sql = "SELECT * FROM tickets" + (statusFilter.isPresent() ? " WHERE status = ?" : "")
                + " ORDER BY created_at DESC LIMIT ? OFFSET ?";
        return supply(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                int index = 1;
                if (statusFilter.isPresent()) {
                    statement.setString(index++, statusFilter.get().name());
                }
                statement.setInt(index++, pageSize);
                statement.setInt(index, Math.max(0, page) * pageSize);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return mapAll(resultSet);
                }
            }
        });
    }

    @Override
    public CompletableFuture<Integer> countAll(Optional<TicketStatus> statusFilter) {
        String sql = "SELECT COUNT(*) FROM tickets" + (statusFilter.isPresent() ? " WHERE status = ?" : "");
        return supply(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                if (statusFilter.isPresent()) {
                    statement.setString(1, statusFilter.get().name());
                }
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    return resultSet.getInt(1);
                }
            }
        });
    }

    @Override
    public CompletableFuture<Integer> countActiveByPlayer(UUID playerUuid) {
        String sql = "SELECT COUNT(*) FROM tickets WHERE player_uuid = ? AND status NOT IN ('CLOSED', 'ARCHIVED')";
        return supply(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, playerUuid.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    return resultSet.getInt(1);
                }
            }
        });
    }

    @Override
    public CompletableFuture<Void> updateStatus(long id, TicketStatus status) {
        return update("UPDATE tickets SET status = ? WHERE id = ?", statement -> {
            statement.setString(1, status.name());
            statement.setLong(2, id);
        });
    }

    @Override
    public CompletableFuture<Void> claim(long id, String claimedByDiscordId) {
        return update("UPDATE tickets SET status = ?, claimed_by = ? WHERE id = ?", statement -> {
            statement.setString(1, TicketStatus.CLAIMED.name());
            statement.setString(2, claimedByDiscordId);
            statement.setLong(3, id);
        });
    }

    @Override
    public CompletableFuture<Void> updatePriority(long id, TicketPriority priority) {
        return update("UPDATE tickets SET priority = ? WHERE id = ?", statement -> {
            statement.setString(1, priority.name());
            statement.setLong(2, id);
        });
    }

    @Override
    public CompletableFuture<Void> setDiscordChannelId(long id, String discordChannelId) {
        return update("UPDATE tickets SET discord_channel_id = ? WHERE id = ?", statement -> {
            statement.setString(1, discordChannelId);
            statement.setLong(2, id);
        });
    }

    @Override
    public CompletableFuture<Void> close(long id, String closeReason) {
        return update("UPDATE tickets SET status = ?, closed_at = ?, close_reason = ? WHERE id = ?", statement -> {
            statement.setString(1, TicketStatus.CLOSED.name());
            statement.setTimestamp(2, Timestamp.from(Instant.now()));
            statement.setString(3, closeReason);
            statement.setLong(4, id);
        });
    }

    @Override
    public CompletableFuture<Void> setRating(long id, int rating) {
        return update("UPDATE tickets SET rating = ? WHERE id = ?", statement -> {
            statement.setInt(1, rating);
            statement.setLong(2, id);
        });
    }

    @Override
    public CompletableFuture<Integer> deleteAllForPlayer(UUID playerUuid) {
        return supply(() -> {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    String playerUuidString = playerUuid.toString();
                    try (PreparedStatement deleteMessages = connection.prepareStatement(
                            "DELETE FROM ticket_messages WHERE ticket_id IN (SELECT id FROM tickets WHERE player_uuid = ?)")) {
                        deleteMessages.setString(1, playerUuidString);
                        deleteMessages.executeUpdate();
                    }
                    int deletedTickets;
                    try (PreparedStatement deleteTickets = connection.prepareStatement("DELETE FROM tickets WHERE player_uuid = ?")) {
                        deleteTickets.setString(1, playerUuidString);
                        deletedTickets = deleteTickets.executeUpdate();
                    }
                    connection.commit();
                    return deletedTickets;
                } catch (SQLException e) {
                    connection.rollback();
                    throw e;
                } finally {
                    connection.setAutoCommit(true);
                }
            }
        });
    }

    private static List<Ticket> mapAll(ResultSet resultSet) throws SQLException {
        List<Ticket> tickets = new ArrayList<>();
        while (resultSet.next()) {
            tickets.add(map(resultSet));
        }
        return tickets;
    }

    private static Ticket map(ResultSet resultSet) throws SQLException {
        long id = resultSet.getLong("id");
        UUID playerUuid = UUID.fromString(resultSet.getString("player_uuid"));
        String category = resultSet.getString("category");
        TicketStatus status = TicketStatus.valueOf(resultSet.getString("status"));
        TicketPriority priority = TicketPriority.valueOf(resultSet.getString("priority"));
        String discordChannelId = resultSet.getString("discord_channel_id");

        String claimedBy = resultSet.getString("claimed_by");

        Instant createdAt = resultSet.getTimestamp("created_at").toInstant();

        Timestamp closedAtRaw = resultSet.getTimestamp("closed_at");
        Instant closedAt = closedAtRaw == null ? null : closedAtRaw.toInstant();

        String closeReason = resultSet.getString("close_reason");

        // getInt() returns 0 for SQL NULL; wasNull() must be read immediately after it,
        // before any other column access, or it reflects the wrong column.
        int ratingRaw = resultSet.getInt("rating");
        Integer rating = resultSet.wasNull() ? null : ratingRaw;

        return new Ticket(id, playerUuid, category, status, priority, discordChannelId, claimedBy,
                createdAt, closedAt, closeReason, rating);
    }

    private CompletableFuture<Void> update(String sql, SqlConsumer<PreparedStatement> binder) {
        return supply(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                binder.accept(statement);
                statement.executeUpdate();
                return null;
            }
        });
    }

    private <T> CompletableFuture<T> supply(SqlSupplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                future.complete(supplier.get());
            } catch (SQLException e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    @FunctionalInterface
    private interface SqlSupplier<T> {
        T get() throws SQLException;
    }

    @FunctionalInterface
    private interface SqlConsumer<T> {
        void accept(T t) throws SQLException;
    }
}
