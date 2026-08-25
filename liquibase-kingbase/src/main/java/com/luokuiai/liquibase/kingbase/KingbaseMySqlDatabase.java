package com.luokuiai.liquibase.kingbase;

import java.util.Locale;

import liquibase.CatalogAndSchema;
import liquibase.database.DatabaseConnection;
import liquibase.database.core.MySQLDatabase;
import liquibase.exception.DatabaseException;
import liquibase.structure.DatabaseObject;
import liquibase.structure.core.Catalog;
import liquibase.structure.core.Column;
import liquibase.structure.core.Schema;

/**
 * KingbaseES adapter for MySQL compatibility mode.
 *
 * <p>KingbaseES V8 reports its compatibility mode through
 * {@code SHOW database_mode}. Set {@code -Dliquibase.kingbase.compatMode=mysql}
 * to override automatic detection when needed.</p>
 */
public class KingbaseMySqlDatabase extends MySQLDatabase {
    @Override
    public int getPriority() {
        return PRIORITY_DATABASE;
    }

    @Override
    public boolean isCorrectDatabaseImplementation(DatabaseConnection connection)
            throws DatabaseException {
        return KingbaseSupport.isKingbaseConnection(connection)
                && KingbaseSupport.isMySqlMode(connection);
    }

    @Override
    public String getShortName() {
        return "kingbase-mysql";
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

    @Override
    public boolean supportsSchemas() {
        return true;
    }

    @Override
    public boolean supportsCatalogs() {
        return true;
    }

    @Override
    protected String getConnectionSchemaName() {
        try {
            return getConnection().getUnderlyingConnection().getSchema();
        } catch (Exception exception) {
            return null;
        }
    }

    @Override
    public String getLiquibaseSchemaName() {
        String schema = super.getLiquibaseSchemaName();
        return schema != null ? schema : getDefaultSchemaName();
    }

    @Override
    public boolean supportsDDLInTransaction() {
        return false;
    }

    @Override
    public boolean isCaseSensitive() {
        return false;
    }

    @Override
    public boolean supports(Class<? extends DatabaseObject> object) {
        if (Schema.class.isAssignableFrom(object)) {
            return true;
        }
        return super.supports(object);
    }

    @Override
    public boolean supportsCatalogInObjectName(
            Class<? extends DatabaseObject> type) {
        return false;
    }

    @Override
    public CatalogAndSchema getSchemaFromJdbcInfo(String rawCatalogName,
            String rawSchemaName) {
        return new CatalogAndSchema(rawCatalogName, rawSchemaName);
    }

    @Override
    public String correctObjectName(String objectName,
            Class<? extends DatabaseObject> objectType) {
        String corrected = super.correctObjectName(objectName, objectType);
        return corrected == null ? null : corrected.toLowerCase(Locale.ROOT);
    }

    @Override
    public String escapeColumnName(String catalogName, String schemaName,
            String tableName, String columnName) {
        return super.escapeColumnName(catalogName, schemaName, tableName,
                correctObjectName(columnName, Column.class));
    }

    @Override
    public String escapeColumnName(String catalogName, String schemaName,
            String tableName, String columnName,
            boolean quoteNamesThatAreFunctions) {
        String corrected = quoteNamesThatAreFunctions
                ? correctObjectName(columnName, Column.class) : columnName;
        return super.escapeColumnName(catalogName, schemaName, tableName,
                corrected, quoteNamesThatAreFunctions);
    }

}
