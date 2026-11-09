package com.luokuiai.liquibase.dameng;

import java.sql.Types;
import java.util.List;
import java.util.Map;

import liquibase.Scope;
import liquibase.database.Database;
import liquibase.database.OfflineConnection;
import liquibase.exception.DatabaseException;
import liquibase.exception.UnexpectedLiquibaseException;
import liquibase.executor.ExecutorService;
import liquibase.snapshot.CachedRow;
import liquibase.snapshot.DatabaseSnapshot;
import liquibase.snapshot.SnapshotGenerator;
import liquibase.snapshot.jvm.ColumnSnapshotGenerator;
import liquibase.statement.core.RawParameterizedSqlStatement;
import liquibase.structure.DatabaseObject;
import liquibase.structure.core.Column;
import liquibase.structure.core.DataType;
import liquibase.structure.core.Table;
import liquibase.structure.core.View;

/** Preserves DM8 temporal precision and identity seeds in generated changelogs. */
@SuppressWarnings("unchecked") // Liquibase 5.x snapshot SPI uses raw return types.
public class DamengColumnSnapshotGenerator extends ColumnSnapshotGenerator {
    @Override
    public int getPriority(Class<? extends DatabaseObject> type, Database database) {
        return database instanceof DamengDatabase && (Column.class.isAssignableFrom(type)
                || Table.class.isAssignableFrom(type) || View.class.isAssignableFrom(type))
                ? PRIORITY_DATABASE : PRIORITY_NONE;
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public Class<? extends SnapshotGenerator>[] replaces() {
        return new Class[] {ColumnSnapshotGenerator.class};
    }

    @Override
    protected DataType readDataType(CachedRow row, Column column, Database database)
            throws DatabaseException {
        DataType type = super.readDataType(row, column, database);
        Integer jdbcType = row.getInt("DATA_TYPE");
        if (jdbcType != null && (jdbcType == Types.TIME || jdbcType == Types.TIME_WITH_TIMEZONE)) {
            type.setColumnSize(row.getInt("DECIMAL_DIGITS"));
            type.setDecimalDigits(null);
        }
        return type;
    }

    @Override
    protected void setAutoIncrementDetails(Column column, Database database,
            DatabaseSnapshot snapshot) {
        if (column.getAutoIncrementInformation() == null || column.getRelation() == null) {
            return;
        }
        if (database.getConnection() == null || database.getConnection() instanceof OfflineConnection) {
            return;
        }
        String table = database.escapeTableName(null, column.getSchema().getName(),
                column.getRelation().getName());
        try {
            List<Map<String, ?>> values = Scope.getCurrentScope()
                    .getSingleton(ExecutorService.class).getExecutor("jdbc", database)
                    .queryForList(new RawParameterizedSqlStatement(
                            "SELECT IDENT_SEED(?) AS IDENTITY_SEED, IDENT_INCR(?) AS IDENTITY_INCREMENT",
                            table, table));
            if (values.size() != 1 || !(values.get(0).get("IDENTITY_SEED") instanceof Number seed)
                    || !(values.get(0).get("IDENTITY_INCREMENT") instanceof Number increment)) {
                throw new DatabaseException("DM8 identity metadata is missing for " + table);
            }
            column.setAutoIncrementInformation(new Column.AutoIncrementInformation(seed, increment));
        } catch (DatabaseException exception) {
            throw new UnexpectedLiquibaseException("Unable to snapshot DM8 identity for " + table, exception);
        }
    }
}
