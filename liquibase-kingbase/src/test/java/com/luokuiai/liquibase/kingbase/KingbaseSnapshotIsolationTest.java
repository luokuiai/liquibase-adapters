package com.luokuiai.liquibase.kingbase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import liquibase.database.Database;
import liquibase.database.core.MySQLDatabase;
import liquibase.database.core.PostgresDatabase;
import liquibase.snapshot.SnapshotGenerator;
import liquibase.structure.DatabaseObject;
import liquibase.structure.core.Index;
import liquibase.structure.core.Schema;
import liquibase.structure.core.Table;
import liquibase.structure.core.View;
import org.junit.jupiter.api.Test;

class KingbaseSnapshotIsolationTest {
    @Test
    void customSnapshotGeneratorsOnlyParticipateForKingbaseMysql() {
        List<SnapshotGenerator> generators = List.of(new KingbaseMySqlTableSnapshotGenerator(),
                new KingbaseMySqlIndexSnapshotGenerator(), new KingbaseMySqlViewSnapshotGenerator());
        List<Class<? extends DatabaseObject>> types = List.of(
                Schema.class, Table.class, Index.class, View.class);
        for (SnapshotGenerator generator : generators) {
            for (Database database : List.of(new KingbasePostgresDatabase(),
                    new PostgresDatabase(), new MySQLDatabase())) {
                for (Class<? extends DatabaseObject> type : types) {
                    assertEquals(SnapshotGenerator.PRIORITY_NONE, generator.getPriority(type, database),
                            generator.getClass().getSimpleName() + " claimed " + database.getShortName());
                }
            }
        }
        assertTrue(generators.get(0).getPriority(Table.class, new KingbaseMySqlDatabase()) > 0);
        assertTrue(generators.get(1).getPriority(Index.class, new KingbaseMySqlDatabase()) > 0);
        assertTrue(generators.get(2).getPriority(View.class, new KingbaseMySqlDatabase()) > 0);
    }
}
