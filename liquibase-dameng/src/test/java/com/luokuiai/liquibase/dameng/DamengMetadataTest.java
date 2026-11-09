package com.luokuiai.liquibase.dameng;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.ServiceLoader;
import java.util.HashMap;
import java.sql.Types;

import com.luokuiai.liquibase.kingbase.KingbaseMySqlIndexSnapshotGenerator;
import com.luokuiai.liquibase.kingbase.KingbaseMySqlTableSnapshotGenerator;
import com.luokuiai.liquibase.kingbase.KingbaseMySqlViewSnapshotGenerator;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.core.PostgresDatabase;
import liquibase.diff.output.DiffOutputControl;
import liquibase.diff.output.changelog.ChangeGenerator;
import liquibase.structure.core.Index;
import liquibase.structure.core.Table;
import liquibase.snapshot.SnapshotGenerator;
import liquibase.snapshot.CachedRow;
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

    @Test
    void snapshotsTimePrecisionInsteadOfJdbcDisplayWidth() throws Exception {
        assertTrue(ServiceLoader.load(SnapshotGenerator.class).stream().anyMatch(
                service -> service.type() == DamengColumnSnapshotGenerator.class));
        var row = new CachedRow(new HashMap<>(java.util.Map.of(
                "TYPE_NAME", "TIME", "DATA_TYPE", Types.TIME,
                "COLUMN_SIZE", 15, "DECIMAL_DIGITS", 6)));
        var generator = new DamengColumnSnapshotGenerator();
        var type = generator.readDataType(row, new liquibase.structure.core.Column(),
                new DamengDatabase());
        assertEquals(6, type.getColumnSize());
        assertNull(type.getDecimalDigits());
        assertEquals(SnapshotGenerator.PRIORITY_NONE, generator.getPriority(
                liquibase.structure.core.Column.class, new PostgresDatabase()));
    }

    @Test
    void kingbaseAndDamengSnapshotExtensionsStayIsolated() {
        var database = new DamengDatabase();
        for (SnapshotGenerator generator : List.of(new KingbaseMySqlIndexSnapshotGenerator(),
                new KingbaseMySqlTableSnapshotGenerator(), new KingbaseMySqlViewSnapshotGenerator())) {
            for (Class<? extends liquibase.structure.DatabaseObject> type : List.of(
                    liquibase.structure.core.Table.class, liquibase.structure.core.Index.class,
                    liquibase.structure.core.View.class, liquibase.structure.core.Schema.class)) {
                assertEquals(SnapshotGenerator.PRIORITY_NONE, generator.getPriority(type, database));
            }
        }
        assertTrue(ServiceLoader.load(SnapshotGenerator.class).stream().anyMatch(
                service -> service.type() == DamengIndexSnapshotGenerator.class));
        assertEquals(SnapshotGenerator.PRIORITY_NONE,
                new DamengIndexSnapshotGenerator().getPriority(
                        liquibase.structure.core.Index.class, new PostgresDatabase()));
    }

    @Test
    void generatedConstraintIndexesDoNotBecomeStandaloneDiffChanges() {
        assertTrue(ServiceLoader.load(ChangeGenerator.class).stream().anyMatch(
                service -> service.type() == DamengMissingIndexChangeGenerator.class));
        var generator = new DamengMissingIndexChangeGenerator();
        var database = new DamengDatabase();
        var output = new DiffOutputControl(false, false, false, null);
        for (String association : List.of(Index.MARK_PRIMARY_KEY,
                Index.MARK_UNIQUE_CONSTRAINT, Index.MARK_FOREIGN_KEY)) {
            Index index = new Index().setName("native_generated").setRelation(new Table(null, "APP", "T"));
            index.addAssociatedWith(association);
            assertEquals(0, generator.fixMissing(index, output, database, database, null).length);
        }
        Index userIndex = new Index().setName("IX_USER").setRelation(new Table(null, "APP", "T"));
        userIndex.addColumn(new liquibase.structure.core.Column("NAME"));
        assertEquals(1, generator.fixMissing(userIndex, output, database, database, null).length);
        assertEquals(ChangeGenerator.PRIORITY_NONE,
                generator.getPriority(Index.class, new PostgresDatabase()));
    }
}
