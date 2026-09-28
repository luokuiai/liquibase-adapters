package com.luokuiai.liquibase.kingbase;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import liquibase.database.Database;
import liquibase.database.DatabaseConnection;
import liquibase.database.DatabaseFactory;
import liquibase.database.core.UnsupportedDatabase;
import liquibase.exception.DatabaseException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class KingbaseDatabaseDetectionTest {
    @AfterEach
    void clearCompatibilityMode() {
        System.clearProperty(KingbaseSupport.COMPAT_MODE_PROPERTY);
        DatabaseFactory.reset();
    }

    @Test
    void selectsPostgresAdapterByDefault() throws DatabaseException {
        DatabaseConnection connection = connection("KingbaseES",
                "jdbc:kingbase8://localhost:54321/test");

        assertTrue(new KingbasePostgresDatabase()
                .isCorrectDatabaseImplementation(connection));
        assertFalse(new KingbaseMySqlDatabase()
                .isCorrectDatabaseImplementation(connection));
        assertInstanceOf(KingbasePostgresDatabase.class, select(connection));
    }

    @ParameterizedTest
    @ValueSource(strings = {"pg", "postgres", "postgresql"})
    void acceptsPostgresCompatibilityModeAliases(String mode)
            throws DatabaseException {
        System.setProperty(KingbaseSupport.COMPAT_MODE_PROPERTY, mode);
        DatabaseConnection connection = connection("KingbaseES",
                "jdbc:kingbase8://localhost:54321/test");

        assertTrue(new KingbasePostgresDatabase()
                .isCorrectDatabaseImplementation(connection));
        assertFalse(new KingbaseMySqlDatabase()
                .isCorrectDatabaseImplementation(connection));
        assertInstanceOf(KingbasePostgresDatabase.class, select(connection));
    }

    @Test
    void acceptsMySqlCompatibilityMode() throws DatabaseException {
        System.setProperty(KingbaseSupport.COMPAT_MODE_PROPERTY, "mysql");
        DatabaseConnection connection = connection("KingbaseES",
                "jdbc:kingbase8://localhost:54321/test");

        assertTrue(new KingbaseMySqlDatabase()
                .isCorrectDatabaseImplementation(connection));
        assertFalse(new KingbasePostgresDatabase()
                .isCorrectDatabaseImplementation(connection));
        assertInstanceOf(KingbaseMySqlDatabase.class, select(connection));
    }

    @Test
    void rejectsMariaDbCompatibilityMode() throws DatabaseException {
        System.setProperty(KingbaseSupport.COMPAT_MODE_PROPERTY, "mariadb");
        DatabaseConnection connection = connection("KingbaseES",
                "jdbc:kingbase8://localhost:54321/test");

        assertFalse(new KingbaseMySqlDatabase()
                .isCorrectDatabaseImplementation(connection));
        assertFalse(new KingbasePostgresDatabase()
                .isCorrectDatabaseImplementation(connection));
        assertInstanceOf(UnsupportedDatabase.class, select(connection));
    }

    @Test
    void detectsKingbaseFromJdbcUrl() throws DatabaseException {
        DatabaseConnection connection = connection(
                "Unknown Database", "JDBC:KINGBASE8://localhost:54321/test");

        assertTrue(new KingbasePostgresDatabase()
                .isCorrectDatabaseImplementation(connection));
    }

    @Test
    void rejectsUnrelatedDatabase() throws DatabaseException {
        DatabaseConnection connection = connection(
                "PostgreSQL", "jdbc:postgresql://localhost:5432/test");

        assertFalse(new KingbasePostgresDatabase()
                .isCorrectDatabaseImplementation(connection));
        assertFalse(new KingbaseMySqlDatabase()
                .isCorrectDatabaseImplementation(connection));
    }

    private DatabaseConnection connection(String productName, String url)
            throws DatabaseException {
        DatabaseConnection connection = mock(DatabaseConnection.class);
        when(connection.getDatabaseProductName()).thenReturn(productName);
        when(connection.getURL()).thenReturn(url);
        return connection;
    }

    private Database select(DatabaseConnection connection)
            throws DatabaseException {
        DatabaseFactory.reset();
        return DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(connection);
    }
}
