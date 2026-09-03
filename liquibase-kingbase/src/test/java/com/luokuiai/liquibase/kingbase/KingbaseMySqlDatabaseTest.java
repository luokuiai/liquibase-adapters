package com.luokuiai.liquibase.kingbase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import liquibase.CatalogAndSchema;
import liquibase.sql.Sql;
import liquibase.sqlgenerator.SqlGeneratorFactory;
import liquibase.statement.core.CreateDatabaseChangeLogLockTableStatement;
import liquibase.statement.core.CreateDatabaseChangeLogTableStatement;
import liquibase.structure.core.Schema;
import liquibase.structure.core.Table;
import org.junit.jupiter.api.Test;

class KingbaseMySqlDatabaseTest {
    @Test
    void returnsKingbaseDriverForKingbaseUrl() {
        KingbaseMySqlDatabase database = new KingbaseMySqlDatabase();

        assertEquals(KingbaseSupport.DRIVER_CLASS,
                database.getDefaultDriver("jdbc:kingbase8://localhost:54321/test"));
    }

    @Test
    void exposesKingbaseMySqlShortName() {
        KingbaseMySqlDatabase database = new KingbaseMySqlDatabase();

        assertEquals("kingbase-mysql", database.getShortName());
    }

    @Test
    void mapsJdbcMetadataToKingbaseSchema() {
        KingbaseMySqlDatabase database = new KingbaseMySqlDatabase();

        CatalogAndSchema catalogAndSchema = database.getSchemaFromJdbcInfo(
                "kingbase", "public");

        assertEquals("kingbase", catalogAndSchema.getCatalogName());
        assertEquals("public", catalogAndSchema.getSchemaName());
        assertTrue(database.supportsSchemas());
        assertTrue(database.supportsCatalogs());
        assertFalse(database.supportsDDLInTransaction());
        assertFalse(database.isCaseSensitive());
        assertTrue(database.supports(Schema.class));
        assertFalse(database.supportsCatalogInObjectName(Table.class));
        assertEquals("databasechangelog",
                database.getDatabaseChangeLogTableName());
        assertEquals("databasechangeloglock",
                database.getDatabaseChangeLogLockTableName());
        assertEquals("database_changelog", database.correctObjectName(
                "DATABASE_CHANGELOG", Table.class));
        assertEquals("UPPER(name)", database.escapeColumnName(null, null, null,
                "UPPER(name)", false));
    }

    @Test
    void generatesLowercaseLiquibaseTrackingColumns() {
        KingbaseMySqlDatabase database = new KingbaseMySqlDatabase();

        String changeLogSql = toSql(new CreateDatabaseChangeLogTableStatement(),
                database);
        String lockSql = toSql(new CreateDatabaseChangeLogLockTableStatement(),
                database);

        assertTrue(changeLogSql.contains("id VARCHAR"), changeLogSql);
        assertTrue(changeLogSql.contains("deployment_id VARCHAR"), changeLogSql);
        assertTrue(lockSql.contains("id INT"), lockSql);
        assertTrue(lockSql.contains("lockgranted datetime"), lockSql);
    }

    private String toSql(liquibase.statement.SqlStatement statement,
            KingbaseMySqlDatabase database) {
        Sql[] sql = SqlGeneratorFactory.getInstance().generateSql(statement,
                database);
        return sql[0].toSql();
    }
}
