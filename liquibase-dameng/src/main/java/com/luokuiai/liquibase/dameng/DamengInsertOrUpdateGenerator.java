package com.luokuiai.liquibase.dameng;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import liquibase.database.Database;
import liquibase.datatype.DataTypeFactory;
import liquibase.exception.ValidationErrors;
import liquibase.sql.Sql;
import liquibase.sql.UnparsedSql;
import liquibase.sqlgenerator.SqlGeneratorChain;
import liquibase.sqlgenerator.core.AbstractSqlGenerator;
import liquibase.statement.core.InsertOrUpdateStatement;
import liquibase.structure.core.Table;

/** Implements loadUpdateData as one atomic MERGE, including onlyUpdate. */
public class DamengInsertOrUpdateGenerator extends AbstractSqlGenerator<InsertOrUpdateStatement> {
    @Override
    public int getPriority() {
        return PRIORITY_DATABASE;
    }

    @Override
    public boolean supports(InsertOrUpdateStatement statement, Database database) {
        return database instanceof DamengDatabase;
    }

    @Override
    public ValidationErrors validate(InsertOrUpdateStatement statement, Database database,
            SqlGeneratorChain chain) {
        ValidationErrors errors = new ValidationErrors();
        errors.checkRequiredField("tableName", statement.getTableName());
        errors.checkRequiredField("columns", statement.getColumnValues());
        errors.checkRequiredField("primaryKey", statement.getPrimaryKey());
        if (statement.getPrimaryKey() != null) {
            for (String key : keys(statement)) {
                if (key.isEmpty() || !statement.getColumnValues().containsKey(key)) {
                    errors.addError("DM8 loadUpdateData primary key column is missing: " + key);
                }
            }
        }
        return errors;
    }

    @Override
    public Sql[] generateSql(InsertOrUpdateStatement statement, Database database,
            SqlGeneratorChain chain) {
        List<String> keys = keys(statement);
        List<String> columns = List.copyOf(statement.getColumnValues().keySet());
        List<String> updates = columns.stream()
                .filter(column -> !keys.contains(column) && statement.getAllowColumnUpdate(column))
                .toList();
        if (statement.getOnlyUpdate() && updates.isEmpty()) {
            return EMPTY_SQL;
        }
        String source = columns.stream().map(column -> {
            Object value = statement.getColumnValue(column);
            String literal = value == null ? "NULL" : DataTypeFactory.getInstance()
                    .fromObject(value, database).objectToSql(value, database);
            return literal + " AS " + column(statement, database, column);
        }).collect(Collectors.joining(", "));
        String on = keys.stream().map(key -> {
            String name = column(statement, database, key);
            return "(t." + name + " = s." + name + " OR (t." + name
                    + " IS NULL AND s." + name + " IS NULL))";
        }).collect(Collectors.joining(" AND "));
        StringBuilder sql = new StringBuilder("MERGE INTO ")
                .append(database.escapeTableName(statement.getCatalogName(),
                        statement.getSchemaName(), statement.getTableName()))
                .append(" t USING (SELECT ").append(source).append(" FROM DUAL) s ON (")
                .append(on).append(")");
        if (!updates.isEmpty()) {
            sql.append(" WHEN MATCHED THEN UPDATE SET ").append(updates.stream()
                    .map(name -> "t." + column(statement, database, name)
                            + " = s." + column(statement, database, name))
                    .collect(Collectors.joining(", ")));
        }
        if (!statement.getOnlyUpdate()) {
            sql.append(" WHEN NOT MATCHED THEN INSERT (").append(columns.stream()
                    .map(name -> column(statement, database, name))
                    .collect(Collectors.joining(", "))).append(") VALUES (")
                    .append(columns.stream().map(name -> "s." + column(statement, database, name))
                            .collect(Collectors.joining(", "))).append(")");
        }
        return new Sql[] {new UnparsedSql(sql.toString(), new Table().setName(statement.getTableName())
                .setSchema(statement.getCatalogName(), statement.getSchemaName()))};
    }

    private List<String> keys(InsertOrUpdateStatement statement) {
        return Arrays.stream(statement.getPrimaryKey().split(",", -1)).map(String::trim).toList();
    }

    private String column(InsertOrUpdateStatement statement, Database database, String name) {
        return database.escapeColumnName(statement.getCatalogName(), statement.getSchemaName(),
                statement.getTableName(), name);
    }
}
