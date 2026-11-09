package com.luokuiai.liquibase.dameng;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;

import liquibase.CatalogAndSchema;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.lockservice.LockService;
import liquibase.lockservice.LockServiceFactory;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.snapshot.SnapshotControl;
import liquibase.snapshot.SnapshotGeneratorFactory;
import liquibase.structure.core.Column;
import liquibase.structure.core.Index;
import liquibase.structure.core.PrimaryKey;
import liquibase.structure.core.Schema;
import liquibase.structure.core.Sequence;
import liquibase.structure.core.Table;
import liquibase.structure.core.UniqueConstraint;
import liquibase.structure.core.View;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

@Tag("integration")
class DamengContainerIntegrationTest {
    private static final int DM_PORT = 5236;
    private static final String PASSWORD = "Dm8_test123";
    private static final String CHANGELOG = "db/changelog/dameng-test.xml";
    private static final DockerImageName IMAGE = DockerImageName.parse(
            System.getProperty("dameng.test.image", "cnxc/dm8:20251114-kylin"));

    @ParameterizedTest
    @ValueSource(ints = {0, 4, 7})
    void runsUpdateMetadataLocksAndRollback(int mode) throws Exception {
        try (GenericContainer<?> container = dameng(mode)) {
            container.start();
            String url = "jdbc:dm://" + container.getHost() + ":"
                    + container.getMappedPort(DM_PORT);
            try (Connection connection = DriverManager.getConnection(url, "SYSDBA", PASSWORD)) {
                // Use a non-default schema so snapshot and lock-table lookup cannot
                // accidentally succeed by falling back to the connection username.
                execute(connection, "CREATE SCHEMA LB_APP AUTHORIZATION SYSDBA");
                execute(connection, "SET SCHEMA LB_APP");
                DatabaseFactory.reset();
                DamengDatabase database = (DamengDatabase) DatabaseFactory.getInstance()
                        .findCorrectDatabaseImplementation(new JdbcConnection(connection));
                assertEquals(mode, database.getCompatMode().getValue());
                assertEquals("LB_APP", database.getDefaultSchemaName());
                assertFalse(database.supportsDDLInTransaction());

                System.out.println("DM8 mode=" + mode + ", server="
                        + connection.getMetaData().getDatabaseProductVersion()
                        + ", driver=" + connection.getMetaData().getDriverVersion());
                try (Liquibase liquibase = new Liquibase(CHANGELOG,
                        new ClassLoaderResourceAccessor(), database)) {
                    liquibase.update(new Contexts(), new LabelExpression());
                    liquibase.update(new Contexts(), new LabelExpression());
                    assertEquals(1, number(connection, "SELECT COUNT(*) FROM LB_PROBE"));
                    assertEquals(10, number(connection, "SELECT ID FROM LB_PROBE"));
                    assertEquals(1, number(connection, "SELECT ENABLED FROM LB_PROBE"));
                    assertEquals(mode, number(connection, "SELECT MODE_VALUE FROM LB_MODE"));
                    assertEquals(10, number(connection, "SELECT LB_SEQUENCE.NEXTVAL"));
                    assertEquals(3, number(connection, "SELECT COUNT(*) FROM DATABASECHANGELOG"));
                    assertEquals(0, number(connection,
                            "SELECT LOCKED FROM DATABASECHANGELOGLOCK WHERE ID = 1"));
                    verifyJdbcMetadata(connection);
                    verifySnapshots(database);
                    verifyLockContention(database, url);

                    liquibase.rollback(3, new Contexts(), new LabelExpression());
                    assertFalse(SnapshotGeneratorFactory.getInstance().has(
                            new Table(null, "LB_APP", "LB_PROBE"), database));
                    assertEquals(0, number(connection, "SELECT COUNT(*) FROM DATABASECHANGELOG"));
                    assertEquals(0, number(connection,
                            "SELECT LOCKED FROM DATABASECHANGELOGLOCK WHERE ID = 1"));
                }
            }
        }
    }

    private GenericContainer<?> dameng(int mode) {
        String startup = """
                cd /opt/dmdbms/bin
                ./dminit PATH=/opt/dmdbms/data DB_NAME=DAMENG \
                    INSTANCE_NAME=LB_DM PORT_NUM=5236 PAGE_SIZE=8 LOG_SIZE=256 \
                    BUFFER=128 CASE_SENSITIVE=1 UNICODE_FLAG=1 \
                    SYSDBA_PWD=%s SYSAUDITOR_PWD=%s
                sed -i -E \
                    -e 's/^[[:space:]]*COMPATIBLE_MODE[[:space:]]*=.*/COMPATIBLE_MODE = %d/' \
                    /opt/dmdbms/data/DAMENG/dm.ini
                exec ./dmserver /opt/dmdbms/data/DAMENG/dm.ini -noconsole
                """.formatted(PASSWORD, PASSWORD, mode);
        return new GenericContainer<>(IMAGE)
                .withCreateContainerCmdModifier(command -> command
                        .withUser("dmdba").withEntrypoint("/bin/bash", "-ec"))
                .withCommand(new String[] {startup})
                .withExposedPorts(DM_PORT)
                .waitingFor(Wait.forLogMessage(".*SYSTEM IS READY.*\\n", 1))
                .withStartupTimeout(Duration.ofMinutes(3));
    }

    private void verifyJdbcMetadata(Connection connection) throws Exception {
        try (ResultSet tables = connection.getMetaData().getTables(
                null, "LB_APP", "LB_PROBE", new String[] {"TABLE"})) {
            assertTrue(tables.next());
            assertEquals("LB_APP", tables.getString("TABLE_SCHEM"));
        }
        try (ResultSet columns = connection.getMetaData().getColumns(
                null, "LB_APP", "LB_PROBE", "ID")) {
            assertTrue(columns.next());
            assertEquals("YES", columns.getString("IS_AUTOINCREMENT"));
        }
    }

    private void verifySnapshots(DamengDatabase database) throws Exception {
        var factory = SnapshotGeneratorFactory.getInstance();
        var snapshot = factory.createSnapshot(new CatalogAndSchema(null, "LB_APP"),
                database, new SnapshotControl(database, Table.class, Column.class,
                        PrimaryKey.class, Index.class, View.class, Sequence.class,
                        UniqueConstraint.class));
        Table table = snapshot.get(new Table(null, "LB_APP", "LB_PROBE"));
        assertNotNull(table);
        assertEquals(4, table.getColumns().size());
        assertNotNull(table.getPrimaryKey());
        assertEquals("PK_LB_PROBE", table.getPrimaryKey().getName());
        assertTrue(table.getIndexes().stream().anyMatch(
                index -> "IX_LB_PROBE_NAME".equals(index.getName())
                        && !index.getColumns().isEmpty()));
        UniqueConstraint unique = table.getUniqueConstraints().stream()
                .filter(constraint -> "UK_LB_PROBE_NAME_ENABLED".equals(constraint.getName()))
                .findFirst().orElseThrow();
        assertEquals(2, unique.getColumns().size());
        assertEquals("NAME", unique.getColumns().get(0).getName());
        assertEquals("ENABLED", unique.getColumns().get(1).getName());
        View viewExample = new View();
        viewExample.setName("LB_PROBE_VIEW");
        viewExample.setSchema(new Schema((String) null, "LB_APP"));
        View view = snapshot.get(viewExample);
        assertNotNull(view);
        assertTrue(view.getDefinition().contains("LB_PROBE"));
        Sequence sequence = snapshot.get(new Sequence().setName("LB_SEQUENCE")
                .setSchema(new Schema((String) null, "LB_APP")));
        assertNotNull(sequence);
        assertEquals(2, sequence.getIncrementBy().intValue());
    }

    private void verifyLockContention(DamengDatabase database, String url) throws Exception {
        LockService first = LockServiceFactory.getInstance().getLockService(database);
        assertTrue(first.acquireLock());
        try (Connection connection = DriverManager.getConnection(url, "SYSDBA", PASSWORD)) {
            execute(connection, "SET SCHEMA LB_APP");
            DamengDatabase other = (DamengDatabase) DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            LockService second = LockServiceFactory.getInstance().getLockService(other);
            try {
                assertFalse(second.acquireLock());
            } finally {
                first.releaseLock();
            }
            assertTrue(second.acquireLock());
            second.releaseLock();
        } finally {
            if (first.hasChangeLogLock()) {
                first.releaseLock();
            }
        }
    }

    private void execute(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private int number(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }
}
