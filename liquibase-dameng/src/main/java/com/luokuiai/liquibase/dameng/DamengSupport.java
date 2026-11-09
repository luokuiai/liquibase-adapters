package com.luokuiai.liquibase.dameng;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

import liquibase.database.DatabaseConnection;
import liquibase.exception.DatabaseException;

final class DamengSupport {
    static final String DRIVER_CLASS = "dm.jdbc.driver.DmDriver";
    static final int DEFAULT_PORT = 5236;
    static final String MODE_QUERY = "SELECT SF_GET_PARA_VALUE(2, 'COMPATIBLE_MODE')";
    static final String DDL_QUERY = "SELECT SF_GET_PARA_VALUE(2, 'DDL_AUTO_COMMIT')";
    static final String CASE_QUERY = "SELECT SF_GET_CASE_SENSITIVE_FLAG()";

    private DamengSupport() {
    }

    static boolean isDamengUrl(String url) {
        return url != null && url.toLowerCase(Locale.ROOT).startsWith("jdbc:dm:");
    }

    static boolean isDamengConnection(DatabaseConnection connection)
            throws DatabaseException {
        if (connection == null) {
            return false;
        }
        if (isDamengUrl(connection.getURL())) {
            return true;
        }
        String product = connection.getDatabaseProductName();
        return "DM DBMS".equalsIgnoreCase(product)
                || "DM8".equalsIgnoreCase(product);
    }

    static DamengCompatMode readMode(DatabaseConnection connection)
            throws DatabaseException {
        if (connection.getDatabaseMajorVersion() != 8) {
            throw new DatabaseException("This adapter supports DM8 only");
        }
        try {
            return DamengCompatMode.fromValue(readInt(connection, MODE_QUERY));
        } catch (IllegalArgumentException exception) {
            throw new DatabaseException(exception.getMessage(), exception);
        }
    }

    static int readInt(DatabaseConnection connection, String query)
            throws DatabaseException {
        Connection jdbc = connection.getUnderlyingConnection();
        if (jdbc == null) {
            throw new DatabaseException("A JDBC connection is required to detect DM8 parameters");
        }
        try (Statement statement = jdbc.createStatement();
                ResultSet result = statement.executeQuery(query)) {
            if (!result.next()) {
                throw new DatabaseException("DM8 parameter query returned no value: " + query);
            }
            int value = result.getInt(1);
            if (result.wasNull()) {
                throw new DatabaseException("DM8 parameter query returned NULL: " + query);
            }
            return value;
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to read DM8 parameter: " + query, exception);
        }
    }
}
