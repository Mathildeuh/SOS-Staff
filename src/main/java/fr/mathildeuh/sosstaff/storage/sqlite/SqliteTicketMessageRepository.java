package fr.mathildeuh.sosstaff.storage.sqlite;

import fr.mathildeuh.sosstaff.ticket.TicketMessage;
import fr.mathildeuh.sosstaff.ticket.TicketMessageRepository;

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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class SqliteTicketMessageRepository implements TicketMessageRepository {

    private final DataSource dataSource;
    private final Executor executor;

    public SqliteTicketMessageRepository(DataSource dataSource, Executor executor) {
        this.dataSource = dataSource;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<TicketMessage> append(long ticketId, UUID authorUuid, String authorName, boolean staff, String content) {
        String sql = "INSERT INTO ticket_messages (ticket_id, author_uuid, author_name, is_staff, content, sent_at) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        CompletableFuture<TicketMessage> future = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                Instant sentAt = Instant.now();
                try (Connection connection = dataSource.getConnection();
                     PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    statement.setLong(1, ticketId);
                    statement.setString(2, authorUuid == null ? null : authorUuid.toString());
                    statement.setString(3, authorName);
                    statement.setBoolean(4, staff);
                    statement.setString(5, content);
                    statement.setTimestamp(6, Timestamp.from(sentAt));
                    statement.executeUpdate();
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        keys.next();
                        long id = keys.getLong(1);
                        future.complete(new TicketMessage(id, ticketId, authorUuid, authorName, staff, content, sentAt));
                    }
                }
            } catch (SQLException e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    @Override
    public CompletableFuture<List<TicketMessage>> findByTicket(long ticketId) {
        String sql = "SELECT * FROM ticket_messages WHERE ticket_id = ? ORDER BY sent_at ASC";
        CompletableFuture<List<TicketMessage>> future = new CompletableFuture<>();
        executor.execute(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, ticketId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<TicketMessage> messages = new ArrayList<>();
                    while (resultSet.next()) {
                        messages.add(map(resultSet));
                    }
                    future.complete(messages);
                }
            } catch (SQLException e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    @Override
    public CompletableFuture<Integer> deleteOlderThan(Instant cutoff) {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        executor.execute(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("DELETE FROM ticket_messages WHERE sent_at < ?")) {
                statement.setTimestamp(1, Timestamp.from(cutoff));
                future.complete(statement.executeUpdate());
            } catch (SQLException e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    private static TicketMessage map(ResultSet resultSet) throws SQLException {
        long id = resultSet.getLong("id");
        long ticketId = resultSet.getLong("ticket_id");
        String authorUuidRaw = resultSet.getString("author_uuid");
        UUID authorUuid = authorUuidRaw == null ? null : UUID.fromString(authorUuidRaw);
        String authorName = resultSet.getString("author_name");
        boolean staff = resultSet.getBoolean("is_staff");
        String content = resultSet.getString("content");
        Instant sentAt = resultSet.getTimestamp("sent_at").toInstant();
        return new TicketMessage(id, ticketId, authorUuid, authorName, staff, content, sentAt);
    }
}
