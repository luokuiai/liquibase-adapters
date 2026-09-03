package com.luokuiai.liquibase.kingbase;

import liquibase.database.DatabaseConnection;
import liquibase.database.core.PostgresDatabase;
import liquibase.exception.DatabaseException;

/**
 * KingbaseES adapter for PostgreSQL compatibility mode.
 */
public class KingbasePostgresDatabase extends PostgresDatabase {
    @Override
    public int getPriority() {
        return PRIORITY_DATABASE;
    }

    @Override
    public boolean isCorrectDatabaseImplementation(DatabaseConnection connection)
            throws DatabaseException {
        return KingbaseSupport.isKingbaseConnection(connection)
                && KingbaseSupport.isPostgresMode(connection);
    }

    @Override
    public String getShortName() {
        return "kingbase";
    }

    @Override
    protected String getDefaultDatabaseProductName() {
        return "KingbaseES";
    }

    @Override
    public String getDefaultDriver(String url) {
        if (KingbaseSupport.isKingbaseUrl(url)) {
            return KingbaseSupport.DRIVER_CLASS;
        }
        return super.getDefaultDriver(url);
    }

    @Override
    public Integer getDefaultPort() {
        return KingbaseSupport.DEFAULT_PORT;
    }
}
