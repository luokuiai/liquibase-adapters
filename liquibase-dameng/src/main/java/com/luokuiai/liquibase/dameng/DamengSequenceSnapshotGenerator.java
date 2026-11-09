package com.luokuiai.liquibase.dameng;

import liquibase.database.Database;
import liquibase.snapshot.SnapshotGenerator;
import liquibase.snapshot.jvm.SequenceSnapshotGenerator;
import liquibase.statement.SqlStatement;
import liquibase.statement.core.RawParameterizedSqlStatement;
import liquibase.structure.DatabaseObject;
import liquibase.structure.core.Schema;
import liquibase.structure.core.Sequence;

/** Uses DM8 sequence metadata in all supported compatibility modes. */
@SuppressWarnings("unchecked") // Inherited Liquibase 5.x snapshot method uses a raw return type.
public class DamengSequenceSnapshotGenerator extends SequenceSnapshotGenerator {
    @Override
    public int getPriority(Class<? extends DatabaseObject> objectType, Database database) {
        return database instanceof DamengDatabase
                && (Schema.class.isAssignableFrom(objectType)
                        || Sequence.class.isAssignableFrom(objectType))
                ? PRIORITY_DATABASE : PRIORITY_NONE;
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public Class<? extends SnapshotGenerator>[] replaces() {
        return new Class[] {SequenceSnapshotGenerator.class};
    }

    @Override
    protected SqlStatement getSelectSequenceStatement(Schema schema, Database database) {
        String owner = schema.getName() == null
                ? database.getDefaultSchemaName() : schema.getName();
        return new RawParameterizedSqlStatement("""
                SELECT SEQUENCE_NAME, MIN_VALUE, MAX_VALUE, INCREMENT_BY,
                       CYCLE_FLAG AS WILL_CYCLE, ORDER_FLAG AS IS_ORDERED,
                       LAST_NUMBER AS START_VALUE, CACHE_SIZE
                  FROM ALL_SEQUENCES
                 WHERE SEQUENCE_OWNER = ?
                """, owner);
    }
}
