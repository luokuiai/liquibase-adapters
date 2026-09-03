package com.luokuiai.liquibase.kingbase;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import liquibase.database.DatabaseConnection;
import liquibase.exception.DatabaseException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class KingbaseDatabaseDetectionTest {
    @AfterEach
    void clearCompatibilityMode() {
        System.clearProperty(KingbaseSupport.COMPAT_MODE_PROPERTY);
    }

    @Test
    void selectsPostgresAdapterByDefault() throws DatabaseException {
        DatabaseConnection connection = connection("KingbaseES", null);

        assertTrue(new KingbasePostgresDatabase()
                .isCorrectDatabaseImplementation(connection));
        assertFalse(new KingbaseMySqlDatabase()
                .isCorrectDatabaseImplementation(connection));
    }

    @ParameterizedTest
    @ValueSource(strings = {"pg", "postgres", "postgresql"})
    void acceptsPostgresCompatibilityModeAliases(String mode)
            throws DatabaseException {
        System.setProperty(KingbaseSupport.COMPAT_MODE_PROPERTY, mode);
        DatabaseConnection connection = connection("KingbaseES", null);

        assertTrue(new KingbasePostgresDatabase()
                .isCorrectDatabaseImplementation(connection));
        assertFalse(new KingbaseMySqlDatabase()
                .isCorrectDatabaseImplementation(connection));
    }

    @ParameterizedTest
    @ValueSource(strings = {"mysql", "mariadb"})
    void acceptsMySqlCompatibilityModeAliases(String mode)
            throws DatabaseException {
        System.setProperty(KingbaseSupport.COMPAT_MODE_PROPERTY, mode);
        DatabaseConnection connection = connection("KingbaseES", null);

        assertTrue(new KingbaseMySqlDatabase()
                .isCorrectDatabaseImplementation(connection));
        assertFalse(new KingbasePostgresDatabase()
                .isCorrectDatabaseImplementation(connection));
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
}
