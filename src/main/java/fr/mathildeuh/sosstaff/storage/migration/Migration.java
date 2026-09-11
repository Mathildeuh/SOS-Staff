package fr.mathildeuh.sosstaff.storage.migration;

import java.sql.Connection;
import java.sql.SQLException;

public interface Migration {

    int version();

    void apply(Connection connection) throws SQLException;
}
