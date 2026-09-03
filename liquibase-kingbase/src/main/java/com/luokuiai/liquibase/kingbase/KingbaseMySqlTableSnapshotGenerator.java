package com.luokuiai.liquibase.kingbase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import liquibase.database.Database;
import liquibase.exception.DatabaseException;
import liquibase.snapshot.DatabaseSnapshot;
import liquibase.snapshot.InvalidExampleException;
import liquibase.snapshot.SnapshotGenerator;
import liquibase.snapshot.jvm.TableSnapshotGenerator;
import liquibase.structure.DatabaseObject;
import liquibase.structure.core.Schema;
import liquibase.structure.core.Table;

/** Reads V8 MySQL-mode tables without the JDBC metadata table-type mismatch. */
public class KingbaseMySqlTableSnapshotGenerator extends TableSnapshotGenerator {
    private static final String TABLE_QUERY = """
            select table_name
              from information_schema.tables
             where table_schema = ?
               and table_name = ?
               and table_type = 'BASE TABLE'
            """;
    private static final String TABLES_QUERY = """
            select table_name
              from information_schema.tables
             where table_schema = ?
               and table_type = 'BASE TABLE'
             order by table_name
            """;

    @Override
    public int getPriority(Class<? extends DatabaseObject> objectType, Database database) {
        if (database instanceof KingbaseMySqlDatabase) {
            return (Schema.class.isAssignableFrom(objectType)
                    || Table.class.isAssignableFrom(objectType))
                    ? PRIORITY_DATABASE + 1 : PRIORITY_NONE;
        }
        return super.getPriority(objectType, database);
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public Class<? extends SnapshotGenerator>[] replaces() {
        return new Class[] {TableSnapshotGenerator.class};
    }

    @Override
    protected DatabaseObject snapshotObject(DatabaseObject example,
            DatabaseSnapshot snapshot) throws DatabaseException {
        if (!(snapshot.getDatabase() instanceof KingbaseMySqlDatabase)) {
            return super.snapshotObject(example, snapshot);
        }

        Table requestedTable = (Table) example;
        try {
            Connection connection = (Connection) snapshot.getDatabase()
                    .getConnection().getUnderlyingConnection();
            try (PreparedStatement statement = connection.prepareStatement(TABLE_QUERY)) {
                statement.setString(1, requestedTable.getSchema().getName());
                statement.setString(2, requestedTable.getName());
                try (ResultSet results = statement.executeQuery()) {
                    if (!results.next()) {
                        return null;
                    }
                    return table(requestedTable.getSchema(), results.getString("table_name"));
                }
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to read KingbaseES table metadata", exception);
        }
    }

    @Override
    protected void addTo(DatabaseObject example, DatabaseSnapshot snapshot)
            throws DatabaseException, InvalidExampleException {
        if (!(snapshot.getDatabase() instanceof KingbaseMySqlDatabase)) {
            super.addTo(example, snapshot);
            return;
        }
        if (!(example instanceof Schema schema)
                || !snapshot.getSnapshotControl().shouldInclude(Table.class)) {
            return;
        }

        try {
            Connection connection = (Connection) snapshot.getDatabase()
                    .getConnection().getUnderlyingConnection();
            try (PreparedStatement statement = connection.prepareStatement(TABLES_QUERY)) {
                statement.setString(1, schema.getName());
                try (ResultSet results = statement.executeQuery()) {
                    while (results.next()) {
                        schema.addDatabaseObject(table(schema, results.getString("table_name")));
                    }
                }
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to read KingbaseES table metadata", exception);
        }
    }

    private Table table(Schema schema, String name) {
        Table table = new Table();
        table.setSchema(schema);
        table.setName(name);
        return table;
    }
}
