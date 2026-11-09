package com.luokuiai.liquibase.dameng;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.ServiceLoader;

import liquibase.database.Database;
import liquibase.database.DatabaseConnection;
import liquibase.database.DatabaseFactory;
import liquibase.exception.DatabaseException;
import liquibase.exception.UnexpectedLiquibaseException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DamengDatabaseDetectionTest {
    @AfterEach
    void resetFactory() {
        DatabaseFactory.reset();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 4, 7})
    void selectsTheRegisteredAdapterAndDetectsItsMode(int mode) throws Exception {
        DatabaseConnection connection = DamengTestConnections.connection(mode);
        int matches = 0;
        for (Database candidate : ServiceLoader.load(Database.class)) {
            if (candidate instanceof DamengDatabase) {
                if (candidate.isCorrectDatabaseImplementation(connection)) {
                    matches++;
                }
            }
        }
        assertEquals(1, matches);
        Database selected = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(connection);
        assertInstanceOf(DamengDatabase.class, selected);
        assertEquals("dameng", selected.getShortName());
        assertEquals(mode, ((DamengDatabase) selected).getCompatMode().getValue());
    }

    @Test
    void keepsModeSelectionLocalToEachConnection() throws Exception {
        DatabaseFactory factory = DatabaseFactory.getInstance();
        Database nativeDatabase = factory.findCorrectDatabaseImplementation(
                DamengTestConnections.connection(0));
        Database mysqlDatabase = factory.findCorrectDatabaseImplementation(
                DamengTestConnections.connection(4));
        Database postgresDatabase = factory.findCorrectDatabaseImplementation(
                DamengTestConnections.connection(7));

        assertEquals(DamengCompatMode.MYSQL,
                ((DamengDatabase) mysqlDatabase).getCompatMode());
        assertEquals(DamengCompatMode.POSTGRES,
                ((DamengDatabase) postgresDatabase).getCompatMode());
        assertEquals(DamengCompatMode.NATIVE,
                ((DamengDatabase) nativeDatabase).getCompatMode());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 5, 6, 8, 99})
    void rejectsUnsupportedModesWithoutFallingBack(int mode) throws Exception {
        DatabaseException exception = assertThrows(DatabaseException.class,
                () -> DatabaseFactory.getInstance().findCorrectDatabaseImplementation(
                        DamengTestConnections.connection(mode)));
        assertTrue(exception.getMessage().contains("COMPATIBLE_MODE=" + mode));
    }

    @Test
    void rejectsUntestedDatabaseMajorVersions() throws Exception {
        DatabaseConnection connection = DamengTestConnections.connection(0);
        when(connection.getDatabaseMajorVersion()).thenReturn(9);
        assertThrows(DatabaseException.class,
                () -> new DamengDatabase().isCorrectDatabaseImplementation(connection));
    }

    @Test
    void detectsDmJdbcUrlsWithAnUnknownProductName() throws Exception {
        DatabaseConnection connection = DamengTestConnections.connection(4);
        when(connection.getDatabaseProductName()).thenReturn("Unknown Database");
        when(connection.getURL()).thenReturn("JDBC:DM://localhost:5236");
        assertTrue(new DamengDatabase().isCorrectDatabaseImplementation(connection));
    }

    @Test
    void doesNotProbeParametersForOtherDatabases() throws Exception {
        DatabaseConnection connection = mock(DatabaseConnection.class);
        when(connection.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(connection.getURL()).thenReturn("jdbc:postgresql://localhost/test");
        assertFalse(new DamengDatabase().isCorrectDatabaseImplementation(connection));
        verify(connection, never()).getUnderlyingConnection();
    }

    @Test
    void rejectsAttachingAnUnrelatedConnection() throws Exception {
        DatabaseConnection connection = mock(DatabaseConnection.class);
        when(connection.getDatabaseProductName()).thenReturn("MySQL");
        when(connection.getURL()).thenReturn("jdbc:mysql://localhost/test");
        assertThrows(UnexpectedLiquibaseException.class,
                () -> new DamengDatabase().setConnection(connection));
        verify(connection, never()).attached(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void doesNotGuessNativeModeWhenParameterDetectionFails() throws Exception {
        DatabaseConnection connection = DamengTestConnections.connection(0);
        when(connection.getUnderlyingConnection().createStatement())
                .thenThrow(new SQLException("Parameter query denied"));
        DatabaseException exception = assertThrows(DatabaseException.class,
                () -> new DamengDatabase().isCorrectDatabaseImplementation(connection));
        assertTrue(exception.getMessage().contains("Unable to read DM8 parameter"));
    }
}
