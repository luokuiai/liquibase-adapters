package com.luokuiai.liquibase.dameng;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import liquibase.Scope;
import liquibase.database.Database;
import liquibase.exception.DatabaseException;
import liquibase.executor.ExecutorService;
import liquibase.snapshot.CachedRow;
import liquibase.snapshot.DatabaseSnapshot;
import liquibase.snapshot.SnapshotGenerator;
import liquibase.snapshot.jvm.UniqueConstraintSnapshotGenerator;
import liquibase.statement.core.RawParameterizedSqlStatement;
import liquibase.structure.DatabaseObject;
import liquibase.structure.core.Schema;
import liquibase.structure.core.Table;
import liquibase.structure.core.UniqueConstraint;

/** Reads DM8 unique constraints without INFORMATION_SCHEMA assumptions. */
@SuppressWarnings("unchecked") // Inherited Liquibase 5.x snapshot method uses a raw return type.
public class DamengUniqueConstraintSnapshotGenerator extends UniqueConstraintSnapshotGenerator {
    @Override
    public int getPriority(Class<? extends DatabaseObject> objectType, Database database) {
        return database instanceof DamengDatabase
                && (Table.class.isAssignableFrom(objectType)
                        || UniqueConstraint.class.isAssignableFrom(objectType))
                ? PRIORITY_DATABASE : PRIORITY_NONE;
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public Class<? extends SnapshotGenerator>[] replaces() {
        return new Class[] {UniqueConstraintSnapshotGenerator.class};
    }

    @Override
    protected List<CachedRow> listConstraints(Table table, DatabaseSnapshot snapshot,
            Schema schema) throws DatabaseException {
        List<Map<String, ?>> rows = query(snapshot.getDatabase(), """
                SELECT CONSTRAINT_NAME
                  FROM ALL_CONSTRAINTS
                 WHERE OWNER = ? AND TABLE_NAME = ? AND CONSTRAINT_TYPE = 'U'
                 ORDER BY CONSTRAINT_NAME
                """, schema.getName(), table.getName());
        List<CachedRow> constraints = new ArrayList<>();
        for (Map<String, ?> row : rows) {
            constraints.add(new CachedRow(new HashMap<>(row)));
        }
        return constraints;
    }

    @Override
    protected List<Map<String, ?>> listColumns(UniqueConstraint example,
            Database database, DatabaseSnapshot snapshot) throws DatabaseException {
        return query(database, """
                SELECT COLUMN_NAME
                  FROM ALL_CONS_COLUMNS
                 WHERE OWNER = ? AND TABLE_NAME = ? AND CONSTRAINT_NAME = ?
                 ORDER BY POSITION
                """, example.getSchema().getName(), example.getRelation().getName(),
                example.getName());
    }

    private List<Map<String, ?>> query(Database database, String sql, Object... parameters)
            throws DatabaseException {
        return Scope.getCurrentScope().getSingleton(ExecutorService.class)
                .getExecutor("jdbc", database)
                .queryForList(new RawParameterizedSqlStatement(sql, parameters));
    }
}
