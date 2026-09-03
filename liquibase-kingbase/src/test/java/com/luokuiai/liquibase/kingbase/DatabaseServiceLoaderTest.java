package com.luokuiai.liquibase.kingbase;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ServiceLoader;
import java.util.SortedSet;

import liquibase.database.Database;
import liquibase.snapshot.SnapshotGenerator;
import liquibase.snapshot.SnapshotGeneratorFactory;
import liquibase.structure.DatabaseObject;
import liquibase.structure.core.Schema;
import liquibase.structure.core.Table;
import liquibase.structure.core.View;
import org.junit.jupiter.api.Test;

class DatabaseServiceLoaderTest {
    @Test
    void registersKingbaseDatabaseImplementations() {
        ServiceLoader<Database> databases = ServiceLoader.load(Database.class);

        assertTrue(contains(databases, KingbasePostgresDatabase.class));
        assertTrue(contains(databases, KingbaseMySqlDatabase.class));
    }

    @Test
    void registersKingbaseMySqlSnapshotGenerators() throws Exception {
        SnapshotGeneratorFactory.reset();
        var method = SnapshotGeneratorFactory.class.getDeclaredMethod("getGenerators",
                Class.class, Database.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        SortedSet<SnapshotGenerator> generators = (SortedSet<SnapshotGenerator>) method.invoke(
                SnapshotGeneratorFactory.getInstance(), Schema.class,
                new KingbaseMySqlDatabase());

        assertTrue(contains(generators, KingbaseMySqlTableSnapshotGenerator.class));

        @SuppressWarnings("unchecked")
        SortedSet<SnapshotGenerator> tableGenerators = (SortedSet<SnapshotGenerator>) method.invoke(
                SnapshotGeneratorFactory.getInstance(), Table.class,
                new KingbaseMySqlDatabase());
        @SuppressWarnings("unchecked")
        SortedSet<SnapshotGenerator> viewGenerators = (SortedSet<SnapshotGenerator>) method.invoke(
                SnapshotGeneratorFactory.getInstance(), View.class,
                new KingbaseMySqlDatabase());
        assertTrue(contains(tableGenerators, KingbaseMySqlIndexSnapshotGenerator.class));
        assertTrue(contains(viewGenerators, KingbaseMySqlViewSnapshotGenerator.class));
    }

    private boolean contains(ServiceLoader<Database> databases,
            Class<? extends Database> expectedType) {
        for (Database database : databases) {
            if (expectedType.isInstance(database)) {
                return true;
            }
        }
        return false;
    }

    private boolean contains(Iterable<SnapshotGenerator> generators,
            Class<? extends SnapshotGenerator> expectedType) {
        for (SnapshotGenerator generator : generators) {
            if (expectedType.isInstance(generator)) {
                return true;
            }
        }
        return false;
    }
}
