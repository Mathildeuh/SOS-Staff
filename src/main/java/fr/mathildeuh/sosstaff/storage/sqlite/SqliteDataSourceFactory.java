package fr.mathildeuh.sosstaff.storage.sqlite;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.nio.file.Path;

public final class SqliteDataSourceFactory {

    private SqliteDataSourceFactory() {
    }

    public static HikariDataSource create(Path databaseFile) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + databaseFile.toAbsolutePath() + "?journal_mode=WAL&foreign_keys=on");
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(5);
        config.setPoolName("sosstaff-sqlite");
        return new HikariDataSource(config);
    }
}
