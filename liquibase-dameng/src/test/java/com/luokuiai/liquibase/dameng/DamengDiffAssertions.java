package com.luokuiai.liquibase.dameng;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.nio.file.Files;
import java.util.Set;
import java.util.HashSet;

import liquibase.CatalogAndSchema;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.diff.DiffGeneratorFactory;
import liquibase.diff.compare.CompareControl;
import liquibase.diff.output.DiffOutputControl;
import liquibase.diff.output.StandardObjectChangeFilter;
import liquibase.diff.output.changelog.DiffToChangeLog;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.DirectoryResourceAccessor;
import liquibase.serializer.core.xml.XMLChangeLogSerializer;
import liquibase.snapshot.DatabaseSnapshot;
import liquibase.snapshot.EmptyDatabaseSnapshot;
import liquibase.snapshot.SnapshotControl;
import liquibase.snapshot.SnapshotGeneratorFactory;
import liquibase.structure.DatabaseObject;
import liquibase.structure.core.Column;
import liquibase.structure.core.ForeignKey;
import liquibase.structure.core.Index;
import liquibase.structure.core.PrimaryKey;
import liquibase.structure.core.Sequence;
import liquibase.structure.core.Table;
import liquibase.structure.core.UniqueConstraint;
import liquibase.structure.core.View;

/** Recreates the schema from an actual generated diff and compares physical metadata. */
final class DamengDiffAssertions {
    private static final Set<Class<? extends DatabaseObject>> TYPES = Set.of(
            Table.class, Column.class, PrimaryKey.class, UniqueConstraint.class,
            ForeignKey.class, Index.class, View.class, Sequence.class);

    private DamengDiffAssertions() {
    }

    static void verify(Connection connection, DamengDatabase database) throws Exception {
        var resources = new ClassLoaderResourceAccessor();
        var contexts = new Contexts();
        var labels = new LabelExpression();
        var original = new Liquibase("db/changelog/matrix/roundtrip.xml", resources, database);
        original.update(contexts, labels);
        DatabaseSnapshot reference = snapshot(database);
        assertEquals(3, reference.get(Table.class).size());
        assertEquals(1, reference.get(ForeignKey.class).size());
        assertTrue(reference.get(Index.class).stream().anyMatch(
                index -> index.getAssociatedWith().contains(Index.MARK_FOREIGN_KEY)));
        assertTrue(reference.get(Index.class).stream().anyMatch(
                index -> index.getAssociatedWith().contains(Index.MARK_UNIQUE_CONSTRAINT)));
        var compareControl = new CompareControl(new HashSet<>(TYPES));
        var missing = DiffGeneratorFactory.getInstance().compare(reference,
                new EmptyDatabaseSnapshot(database, control(database)), compareControl);
        var output = new DiffOutputControl(false, false, false, null);
        var changeSets = new DiffToChangeLog(missing, output).generateChangeSets();
        assertFalse(changeSets.isEmpty());
        original.rollback(3, contexts, labels);

        var directory = Files.createTempDirectory("dameng-roundtrip-");
        var generated = directory.resolve("generated.xml");
        try {
            try (var stream = Files.newOutputStream(generated)) {
                new XMLChangeLogSerializer().write(changeSets, stream);
            }
            try (var generatedResources = new DirectoryResourceAccessor(directory)) {
                // Serialize and parse the generated changelog just as the CLI does.
                Liquibase replay = new Liquibase("generated.xml", generatedResources, database);
                replay.update(contexts, labels);
                var difference = DiffGeneratorFactory.getInstance().compare(reference, snapshot(database),
                        compareControl);
                assertTrue(difference.areEqual(), () -> "Missing: " + difference.getMissingObjects()
                        + "; unexpected: " + difference.getUnexpectedObjects()
                        + "; changed: " + difference.getChangedObjects());
                assertEquals(0, DamengChangeAssertions.integer(connection, "SELECT COUNT(*) FROM T_REF"));
                try (var statement = connection.createStatement()) {
                    statement.executeUpdate("INSERT INTO T_REF (NAME) VALUES ('first')");
                    statement.executeUpdate("INSERT INTO T_REF (NAME) VALUES ('second')");
                }
                assertEquals(10, DamengChangeAssertions.integer(connection, "SELECT MIN(ID) FROM T_REF"));
                assertEquals(12, DamengChangeAssertions.integer(connection, "SELECT MAX(ID) FROM T_REF"));
                replay.rollback(changeSets.size(), contexts, labels);
                assertEquals(0, snapshot(database).get(Table.class).size());
            }
        } finally {
            Files.deleteIfExists(generated);
            Files.deleteIfExists(directory);
        }
    }

    @SuppressWarnings("unchecked") // Upstream SnapshotControl has a generic varargs constructor.
    private static SnapshotControl control(DamengDatabase database) {
        return new SnapshotControl(database, new StandardObjectChangeFilter(
                StandardObjectChangeFilter.FilterType.EXCLUDE, "table:DATABASECHANGELOG.*"),
                Table.class, Column.class, PrimaryKey.class, UniqueConstraint.class,
                ForeignKey.class, Index.class, View.class, Sequence.class);
    }

    private static DatabaseSnapshot snapshot(DamengDatabase database) throws Exception {
        return SnapshotGeneratorFactory.getInstance().createSnapshot(
                new CatalogAndSchema(null, database.getDefaultSchemaName()),
                database, control(database));
    }
}
