package com.luokuiai.liquibase.kingbase;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

import liquibase.database.DatabaseConnection;
import liquibase.exception.DatabaseException;

final class KingbaseSupport {
    static final String DRIVER_CLASS = "com.kingbase8.Driver";
    static final String JDBC_URL_PREFIX = "jdbc:kingbase8:";
    static final int DEFAULT_PORT = 54321;
    static final String COMPAT_MODE_PROPERTY = "liquibase.kingbase.compatMode";

    private KingbaseSupport() {
    }

    static boolean isKingbaseConnection(DatabaseConnection connection)
            throws DatabaseException {
        String productName = normalize(connection.getDatabaseProductName());
        String url = normalize(connection.getURL());

        return productName.contains("kingbase")
                || url.startsWith(JDBC_URL_PREFIX);
    }

    static boolean isKingbaseUrl(String url) {
        return url != null
                && url.toLowerCase(Locale.ROOT).startsWith(JDBC_URL_PREFIX);
    }

    static boolean isPostgresMode(DatabaseConnection connection) {
        String mode = configuredMode();
        if (!mode.isEmpty()) {
            return "pg".equals(mode)
                || "postgres".equals(mode)
                || "postgresql".equals(mode);
        }
        return !"mysql".equals(connectionMode(connection));
    }

    static boolean isMySqlMode(DatabaseConnection connection) {
        String mode = configuredMode();
        if (!mode.isEmpty()) {
            return "mysql".equals(mode) || "mariadb".equals(mode);
        }
        return "mysql".equals(connectionMode(connection));
    }

    private static String configuredMode() {
        return normalize(System.getProperty(COMPAT_MODE_PROPERTY));
    }

    private static String connectionMode(DatabaseConnection connection) {
        Connection jdbcConnection = connection.getUnderlyingConnection();
        if (jdbcConnection == null) {
            return "";
        }
        try (Statement statement = jdbcConnection.createStatement();
                ResultSet results = statement.executeQuery("show database_mode")) {
            return results.next() ? normalize(results.getString(1)) : "";
        } catch (SQLException exception) {
            return "";
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
