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
import liquibase.snapshot.jvm.ViewSnapshotGenerator;
import liquibase.structure.DatabaseObject;
import liquibase.structure.core.Schema;
import liquibase.structure.core.View;

/** Reads actual V8 MySQL-mode views without JDBC metadata table-type confusion. */
public class KingbaseMySqlViewSnapshotGenerator extends ViewSnapshotGenerator {
    private static final String VIEW_QUERY = """
            select table_name, view_definition
              from information_schema.views
             where table_schema = ?
               and table_name = ?
            """;
    private static final String VIEWS_QUERY = """
            select table_name, view_definition
              from information_schema.views
             where table_schema = ?
             order by table_name
            """;

    @Override
    public int getPriority(Class<? extends DatabaseObject> objectType, Database database) {
        if (database instanceof KingbaseMySqlDatabase) {
            return (Schema.class.isAssignableFrom(objectType)
                    || View.class.isAssignableFrom(objectType))
                    ? PRIORITY_DATABASE + 1 : PRIORITY_NONE;
        }
        return super.getPriority(objectType, database);
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public Class<? extends SnapshotGenerator>[] replaces() {
        return new Class[] {ViewSnapshotGenerator.class};
    }

    @Override
    protected DatabaseObject snapshotObject(DatabaseObject example,
            DatabaseSnapshot snapshot) throws DatabaseException {
        if (!(snapshot.getDatabase() instanceof KingbaseMySqlDatabase)) {
            return super.snapshotObject(example, snapshot);
        }

        View requestedView = (View) example;
        try {
            Connection connection = (Connection) snapshot.getDatabase()
                    .getConnection().getUnderlyingConnection();
            try (PreparedStatement statement = connection.prepareStatement(VIEW_QUERY)) {
                statement.setString(1, requestedView.getSchema().getName());
                statement.setString(2, requestedView.getName());
                try (ResultSet results = statement.executeQuery()) {
                    return results.next() ? view(requestedView.getSchema(), results) : null;
                }
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to read KingbaseES view metadata", exception);
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
                || !snapshot.getSnapshotControl().shouldInclude(View.class)) {
            return;
        }

        try {
            Connection connection = (Connection) snapshot.getDatabase()
                    .getConnection().getUnderlyingConnection();
            try (PreparedStatement statement = connection.prepareStatement(VIEWS_QUERY)) {
                statement.setString(1, schema.getName());
                try (ResultSet results = statement.executeQuery()) {
                    while (results.next()) {
                        schema.addDatabaseObject(view(schema, results));
                    }
                }
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to read KingbaseES view metadata", exception);
        }
    }

    private View view(Schema schema, ResultSet results) throws SQLException {
        View view = new View();
        view.setSchema(schema);
        view.setName(results.getString("table_name"));
        view.setDefinition(results.getString("view_definition"));
        return view;
    }
}
