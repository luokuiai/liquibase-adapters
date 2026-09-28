package com.luokuiai.liquibase.kingbase;

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

    static boolean isPostgresMode() {
        String mode = configuredMode();
        return mode.isEmpty()
                || "pg".equals(mode)
                || "postgres".equals(mode)
                || "postgresql".equals(mode);
    }

    static boolean isMySqlMode() {
        return "mysql".equals(configuredMode());
    }

    private static String configuredMode() {
        return normalize(System.getProperty(COMPAT_MODE_PROPERTY));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
