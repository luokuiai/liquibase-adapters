package com.luokuiai.liquibase.dameng;

import liquibase.database.Database;
import liquibase.sql.Sql;
import liquibase.sql.UnparsedSql;
import liquibase.sqlgenerator.SqlGeneratorChain;
import liquibase.sqlgenerator.core.DropDefaultValueGenerator;
import liquibase.statement.core.DropDefaultValueStatement;

/** Removes a DM8 default without changing the column's type or nullability. */
public class DamengDropDefaultValueGenerator extends DropDefaultValueGenerator {
    @Override
    public int getPriority() {
        return PRIORITY_DATABASE;
    }

    @Override
    public boolean supports(DropDefaultValueStatement statement, Database database) {
        return database instanceof DamengDatabase;
    }

    @Override
    public Sql[] generateSql(DropDefaultValueStatement statement, Database database,
            SqlGeneratorChain chain) {
        return new Sql[] {new UnparsedSql("ALTER TABLE "
                + database.escapeTableName(statement.getCatalogName(), statement.getSchemaName(),
                        statement.getTableName())
                + " MODIFY " + database.escapeColumnName(statement.getCatalogName(),
                        statement.getSchemaName(), statement.getTableName(), statement.getColumnName())
                + " DEFAULT NULL", getAffectedColumn(statement))};
    }
}
