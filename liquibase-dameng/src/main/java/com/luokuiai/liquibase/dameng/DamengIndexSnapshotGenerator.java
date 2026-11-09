package com.luokuiai.liquibase.dameng;

import java.util.List;
import java.util.Map;

import liquibase.Scope;
import liquibase.database.Database;
import liquibase.exception.DatabaseException;
import liquibase.executor.ExecutorService;
import liquibase.snapshot.DatabaseSnapshot;
import liquibase.snapshot.InvalidExampleException;
import liquibase.snapshot.SnapshotGenerator;
import liquibase.snapshot.jvm.IndexSnapshotGenerator;
import liquibase.statement.core.RawParameterizedSqlStatement;
import liquibase.structure.DatabaseObject;
import liquibase.structure.core.Index;
import liquibase.structure.core.Table;

/** Associates DM8 physical indexes with the constraints that create them. */
@SuppressWarnings("unchecked") // Liquibase 5.x snapshot SPI uses raw return types.
public class DamengIndexSnapshotGenerator extends IndexSnapshotGenerator {
    @Override
    public int getPriority(Class<? extends DatabaseObject> type, Database database) {
        return database instanceof DamengDatabase
                && (Index.class.isAssignableFrom(type) || Table.class.isAssignableFrom(type))
                ? PRIORITY_DATABASE : PRIORITY_NONE;
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public Class<? extends SnapshotGenerator>[] replaces() {
        return new Class[] {IndexSnapshotGenerator.class};
    }

    @Override
    protected void addTo(DatabaseObject object, DatabaseSnapshot snapshot)
            throws DatabaseException, InvalidExampleException {
        super.addTo(object, snapshot);
        if (object instanceof Table table) {
            for (Index index : table.getIndexes()) {
                enrich(index, snapshot);
            }
        }
    }

    @Override
    protected DatabaseObject snapshotObject(DatabaseObject example, DatabaseSnapshot snapshot)
            throws DatabaseException, InvalidExampleException {
        Index index = (Index) super.snapshotObject(example, snapshot);
        if (index != null) {
            enrich(index, snapshot);
        }
        return index;
    }

    private void enrich(Index index, DatabaseSnapshot snapshot) throws DatabaseException {
        String schema = index.getSchema().getName();
        String table = index.getRelation().getName();
        String key = getClass().getName() + ":" + schema + ":" + table;
        List<Map<String, ?>> rows = (List<Map<String, ?>>) snapshot.getScratchData(key);
        if (rows == null) {
            rows = Scope.getCurrentScope().getSingleton(ExecutorService.class)
                    .getExecutor("jdbc", snapshot.getDatabase())
                    .queryForList(new RawParameterizedSqlStatement("""
                            SELECT i.INDEX_NAME, i.INDEX_TYPE, i.GENERATED, i.TABLESPACE_NAME,
                                   c.CONSTRAINT_TYPE
                              FROM ALL_INDEXES i
                              LEFT JOIN ALL_CONSTRAINTS c
                                ON c.OWNER = i.OWNER AND c.INDEX_NAME = i.INDEX_NAME
                               AND c.CONSTRAINT_TYPE IN ('P', 'U')
                             WHERE i.OWNER = ? AND i.TABLE_NAME = ?
                            """, schema, table));
            snapshot.setScratchData(key, rows);
        }
        for (Map<String, ?> row : rows) {
            if (!index.getName().equals(row.get("INDEX_NAME"))) {
                continue;
            }
            index.setTablespace((String) row.get("TABLESPACE_NAME"));
            if ("P".equals(row.get("CONSTRAINT_TYPE"))) {
                index.addAssociatedWith(Index.MARK_PRIMARY_KEY);
            } else if ("U".equals(row.get("CONSTRAINT_TYPE"))) {
                index.addAssociatedWith(Index.MARK_UNIQUE_CONSTRAINT);
            } else if ("Y".equals(row.get("GENERATED"))
                    && "VIRTUAL".equals(row.get("INDEX_TYPE"))) {
                index.addAssociatedWith(Index.MARK_FOREIGN_KEY);
            }
        }
    }
}
