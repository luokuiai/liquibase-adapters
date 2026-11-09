package com.luokuiai.liquibase.dameng;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.ServiceLoader;

import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.core.PostgresDatabase;
import liquibase.snapshot.SnapshotGenerator;
import liquibase.sqlgenerator.SqlGeneratorFactory;
import liquibase.statement.core.GetViewDefinitionStatement;
import liquibase.statement.core.RawParameterizedSqlStatement;
import liquibase.structure.core.Schema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DamengMetadataTest {
    @ParameterizedTest
    @ValueSource(ints = {0, 4, 7})
    void selectsDmViewDefinitionSqlInEveryMode(int mode) throws Exception {
        Database database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(DamengTestConnections.connection(mode));
        var statement = new GetViewDefinitionStatement(null, "APP", "PROBE_VIEW");
        String sql = SqlGeneratorFactory.getInstance().generateSql(statement, database)[0]
                .toSql();
        assertEquals("SELECT TEXT FROM ALL_VIEWS WHERE OWNER = 'APP'"
                + " AND VIEW_NAME = 'PROBE_VIEW'", sql);
        assertFalse(new DamengViewDefinitionGenerator().supports(
                statement, new PostgresDatabase()));
    }

    @Test
    void escapesViewNamesInMetadataQueries() {
        DamengDatabase database = new DamengDatabase();
        database.setDefaultSchemaName("APP");
        String sql = SqlGeneratorFactory.getInstance().generateSql(
                new GetViewDefinitionStatement(null, "APP", "VIEW'NAME"), database)[0].toSql();
        assertTrue(sql.endsWith("VIEW_NAME = 'VIEW''NAME'"));
    }

    @Test
    void registersTheSequenceSnapshotAndBindsItsSchema() {
        assertTrue(ServiceLoader.load(SnapshotGenerator.class).stream()
                .anyMatch(service -> service.type() == DamengSequenceSnapshotGenerator.class));
        var generator = new DamengSequenceSnapshotGenerator();
        var statement = (RawParameterizedSqlStatement) generator.getSelectSequenceStatement(
                new Schema((String) null, "APP'OWNER"), new DamengDatabase());
        assertTrue(statement.getSql().contains("WHERE SEQUENCE_OWNER = ?"));
        assertEquals(List.of("APP'OWNER"), statement.getParameters());
    }

    @Test
    void registersTheUniqueConstraintSnapshotWithoutChangingOtherDatabases() {
        assertTrue(ServiceLoader.load(SnapshotGenerator.class).stream()
                .anyMatch(service ->
                        service.type() == DamengUniqueConstraintSnapshotGenerator.class));
        assertEquals(SnapshotGenerator.PRIORITY_NONE,
                new DamengUniqueConstraintSnapshotGenerator().getPriority(
                        liquibase.structure.core.UniqueConstraint.class,
                        new PostgresDatabase()));
    }
}
