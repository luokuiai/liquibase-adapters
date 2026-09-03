package com.luokuiai.liquibase.kingbase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.snapshot.SnapshotGeneratorFactory;
import liquibase.structure.core.Index;
import liquibase.structure.core.Schema;
import liquibase.structure.core.Table;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

@Tag("integration")
class KingbaseContainerIntegrationTest {
    private static final int KINGBASE_PORT = 54321;
    private static final String CHANGELOG = "db/changelog/kingbase-test.xml";
    private static final DockerImageName IMAGE = DockerImageName.parse(
            System.getProperty("kingbase.test.image", "kingbase:v8r6"));

    @Test
    void runsLiquibaseUpdateAndRollbackInPostgresMode() throws Exception {
        verifyAdapter("pg", KingbasePostgresDatabase.class);
    }

    @Test
    void runsLiquibaseUpdateAndRollbackInMySqlMode() throws Exception {
        verifyAdapter("mysql", KingbaseMySqlDatabase.class);
    }

    private void verifyAdapter(String databaseMode,
            Class<? extends Database> expectedAdapter) throws Exception {
        try (GenericContainer<?> kingbase = kingbase(databaseMode)) {
            kingbase.start();
            String jdbcUrl = String.format("jdbc:kingbase8://%s:%d/kingbase",
                    kingbase.getHost(), kingbase.getMappedPort(KINGBASE_PORT));

            try (Connection connection = DriverManager.getConnection(
                    jdbcUrl, "kingbase", "dev")) {
                Database database = DatabaseFactory.getInstance()
                        .findCorrectDatabaseImplementation(
                                new JdbcConnection(connection));
                assertInstanceOf(expectedAdapter, database);
                if (expectedAdapter == KingbaseMySqlDatabase.class) {
                    database.setDatabaseChangeLogTableName("database_changelog");
                    database.setDatabaseChangeLogLockTableName("database_changelog_lock");
                }

                try (Liquibase liquibase = new Liquibase(CHANGELOG,
                        new ClassLoaderResourceAccessor(), database)) {
                    liquibase.update(new Contexts(), new LabelExpression());
                    assertEquals(1, probeRowCount(connection));
                    if (expectedAdapter == KingbaseMySqlDatabase.class) {
                        assertEquals(1, queryForInt(connection,
                                "select count(*) from pg_catalog.pg_indexes "
                                        + "where schemaname = current_schema() "
                                        + "and tablename = 'lb_kingbase_probe'"));
                        assertEquals(1, queryForInt(connection,
                                "select count(*) from information_schema.columns "
                                        + "where table_schema = current_schema() "
                                        + "and table_name = 'database_changelog' "
                                        + "and column_name = 'id'"));
                        assertEquals(0, queryForInt(connection,
                                "select count(*) from information_schema.columns "
                                        + "where table_schema = current_schema() "
                                        + "and table_name in ('database_changelog', "
                                        + "'database_changelog_lock') "
                                        + "and column_name <> lower(column_name)"));
                        verifyIndexSnapshot(database);
                    }

                    liquibase.rollback(1, new Contexts(), new LabelExpression());
                    assertThrows(SQLException.class,
                            () -> probeRowCount(connection));
                }
            } finally {
                System.clearProperty(KingbaseSupport.COMPAT_MODE_PROPERTY);
            }
        }
    }

    private GenericContainer<?> kingbase(String mode) {
        return new GenericContainer<>(IMAGE)
                .withPrivilegedMode(true)
                .withEnv("DB_MODE", mode)
                .withEnv("ENABLE_CI", "no")
                .withEnv("DB_USER", "kingbase")
                .withEnv("DB_PASSWORD", "dev")
                .withExposedPorts(KINGBASE_PORT)
                .waitingFor(Wait.forLogMessage(".*server started.*\\n", 1))
                .withStartupTimeout(Duration.ofMinutes(3));
    }

    private int probeRowCount(Connection connection) throws SQLException {
        return queryForInt(connection, "select count(*) from lb_kingbase_probe");
    }

    private void verifyIndexSnapshot(Database database) throws Exception {
        Table example = new Table();
        example.setSchema(new Schema(database.getDefaultCatalogName(),
                database.getDefaultSchemaName()));
        example.setName("lb_kingbase_probe");

        Index indexExample = new Index().setName("lb_kingbase_probe_pkey")
                .setRelation(example);
        Index index = SnapshotGeneratorFactory.getInstance().createSnapshot(indexExample, database);
        assertEquals("lb_kingbase_probe_pkey", index.getName());
        assertEquals("id", index.getColumns().isEmpty() ? null
                : index.getColumns().get(0).getName());
    }

    private int queryForInt(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getInt(1);
        }
    }

}
