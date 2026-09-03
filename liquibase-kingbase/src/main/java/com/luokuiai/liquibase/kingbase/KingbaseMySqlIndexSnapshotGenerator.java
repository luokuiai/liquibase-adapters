package com.luokuiai.liquibase.kingbase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import liquibase.database.Database;
import liquibase.exception.DatabaseException;
import liquibase.snapshot.DatabaseSnapshot;
import liquibase.snapshot.InvalidExampleException;
import liquibase.snapshot.SnapshotGenerator;
import liquibase.snapshot.jvm.IndexSnapshotGenerator;
import liquibase.structure.DatabaseObject;
import liquibase.structure.core.Column;
import liquibase.structure.core.Index;
import liquibase.structure.core.Table;

/** Reads indexes without the broken V8 MySQL-mode JDBC getIndexInfo call. */
public class KingbaseMySqlIndexSnapshotGenerator extends IndexSnapshotGenerator {
    private static final String INDEX_QUERY = """
            select indexname, indexdef
              from pg_catalog.pg_indexes
             where schemaname = ?
               and tablename = ?
             order by indexname
            """;
    private static final Pattern INDEX_METHOD = Pattern.compile(
            "(?i)\\busing\\s+(\\S+)\\s*\\(");

    @Override
    public int getPriority(Class<? extends DatabaseObject> objectType, Database database) {
        if (database instanceof KingbaseMySqlDatabase) {
            if (Index.class.isAssignableFrom(objectType)
                    || Table.class.isAssignableFrom(objectType)) {
                return PRIORITY_DATABASE + 1;
            }
            return PRIORITY_NONE;
        }
        return super.getPriority(objectType, database);
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public Class<? extends SnapshotGenerator>[] replaces() {
        return new Class[] {IndexSnapshotGenerator.class};
    }

    @Override
    protected void addTo(DatabaseObject example, DatabaseSnapshot snapshot)
            throws DatabaseException, InvalidExampleException {
        if (!(snapshot.getDatabase() instanceof KingbaseMySqlDatabase)) {
            super.addTo(example, snapshot);
            return;
        }
        if (!(example instanceof Table table)) {
            return;
        }

        try {
            Connection connection = (Connection) snapshot.getDatabase()
                    .getConnection().getUnderlyingConnection();
            try (PreparedStatement statement = connection.prepareStatement(INDEX_QUERY)) {
                statement.setString(1, table.getSchema().getName());
                statement.setString(2, table.getName());
                try (ResultSet results = statement.executeQuery()) {
                    while (results.next()) {
                        String indexName = results.getString("indexname");
                        Index existingIndex = findIndex(table, indexName);
                        if (existingIndex != null && !existingIndex.getColumns().isEmpty()) {
                            continue;
                        }
                        if (existingIndex != null) {
                            table.getIndexes().remove(existingIndex);
                        }
                        table.getIndexes().add(createIndex(table, indexName,
                                results.getString("indexdef")));
                    }
                }
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to read KingbaseES index metadata", exception);
        }
    }

    @Override
    protected DatabaseObject snapshotObject(DatabaseObject example,
            DatabaseSnapshot snapshot) throws DatabaseException, InvalidExampleException {
        if (!(snapshot.getDatabase() instanceof KingbaseMySqlDatabase)) {
            return super.snapshotObject(example, snapshot);
        }

        Index requestedIndex = (Index) example;
        if (!(requestedIndex.getRelation() instanceof Table table)) {
            return null;
        }
        addTo(table, snapshot);
        return findIndex(table, requestedIndex.getName());
    }

    private Index createIndex(Table table, String indexName, String definition) {
        Index index = new Index().setName(indexName).setRelation(table);
        String upperCaseDefinition = definition.toUpperCase(Locale.ROOT);
        index.setUnique(upperCaseDefinition.startsWith("CREATE UNIQUE INDEX"));

        Matcher methodMatcher = INDEX_METHOD.matcher(definition);
        if (methodMatcher.find()) {
            index.setUsing(methodMatcher.group(1).toLowerCase(Locale.ROOT));
        }

        int openingParenthesis = definition.indexOf('(');
        int closingParenthesis = definition.lastIndexOf(')');
        if (openingParenthesis < 0 || closingParenthesis <= openingParenthesis) {
            return index;
        }

        for (String columnDefinition : splitColumns(
                definition.substring(openingParenthesis + 1, closingParenthesis))) {
            String columnName = columnDefinition.trim();
            columnName = columnName.replaceFirst(
                    "(?i)\\s+NULLS\\s+(FIRST|LAST)$", "");
            boolean descending = columnName.toUpperCase(Locale.ROOT).endsWith(" DESC");
            if (descending) {
                columnName = columnName.substring(0, columnName.length() - 5).trim();
            } else if (columnName.toUpperCase(Locale.ROOT).endsWith(" ASC")) {
                columnName = columnName.substring(0, columnName.length() - 4).trim();
            }
            index.addColumn(new Column(columnName).setComputed(columnName.startsWith("("))
                    .setDescending(descending).setRelation(table));
        }
        return index;
    }

    private List<String> splitColumns(String definitions) {
        List<String> columns = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int index = 0; index < definitions.length(); index++) {
            char character = definitions.charAt(index);
            if (character == '(') {
                depth++;
            } else if (character == ')') {
                depth--;
            } else if (character == ',' && depth == 0) {
                columns.add(definitions.substring(start, index));
                start = index + 1;
            }
        }
        columns.add(definitions.substring(start));
        return columns;
    }

    private Index findIndex(Table table, String indexName) {
        for (Index index : table.getIndexes()) {
            if (indexName == null || indexName.equals(index.getName())) {
                return index;
            }
        }
        return null;
    }
}
