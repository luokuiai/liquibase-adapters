package com.luokuiai.liquibase.dameng;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

import liquibase.database.DatabaseConnection;

final class DamengTestConnections {
    private DamengTestConnections() {
    }

    static DatabaseConnection connection(int mode) throws Exception {
        return connection(mode, 1, false);
    }

    static DatabaseConnection connection(int mode, int ddlAutoCommit,
            boolean lowercaseIdentifiers) throws Exception {
        DatabaseConnection connection = mock(DatabaseConnection.class);
        Connection jdbc = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(connection.getDatabaseProductName()).thenReturn("DM DBMS");
        when(connection.getDatabaseMajorVersion()).thenReturn(8);
        when(connection.getURL()).thenReturn("jdbc:dm://localhost:5236");
        when(connection.getConnectionUserName()).thenReturn("APP");
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.getUnderlyingConnection()).thenReturn(jdbc);
        when(jdbc.getMetaData()).thenReturn(metadata);
        when(metadata.storesUpperCaseIdentifiers()).thenReturn(!lowercaseIdentifiers);
        when(metadata.storesLowerCaseIdentifiers()).thenReturn(lowercaseIdentifiers);
        when(metadata.supportsMixedCaseIdentifiers()).thenReturn(false);
        when(metadata.getSQLKeywords()).thenReturn("USER,ORDER");
        when(metadata.getSearchStringEscape()).thenReturn("!");
        when(jdbc.createStatement()).thenAnswer(invocation -> {
            Statement statement = mock(Statement.class);
            when(statement.executeQuery(anyString())).thenAnswer(query -> {
                String sql = query.getArgument(0);
                int value;
                if (DamengSupport.MODE_QUERY.equals(sql)) {
                    value = mode;
                } else if (DamengSupport.DDL_QUERY.equals(sql)) {
                    value = ddlAutoCommit;
                } else if (DamengSupport.CASE_QUERY.equals(sql)) {
                    value = 1;
                } else {
                    throw new AssertionError("Unexpected SQL: " + sql);
                }
                ResultSet result = mock(ResultSet.class);
                when(result.next()).thenReturn(true, false);
                when(result.getInt(1)).thenReturn(value);
                return result;
            });
            return statement;
        });
        return connection;
    }
}
