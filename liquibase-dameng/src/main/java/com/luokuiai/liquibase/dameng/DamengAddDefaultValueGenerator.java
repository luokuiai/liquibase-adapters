package com.luokuiai.liquibase.dameng;

import liquibase.database.Database;
import liquibase.datatype.DataTypeFactory;
import liquibase.sql.Sql;
import liquibase.sql.UnparsedSql;
import liquibase.sqlgenerator.SqlGeneratorChain;
import liquibase.sqlgenerator.core.AddDefaultValueGenerator;
import liquibase.statement.core.AddDefaultValueStatement;

/** Changes a default using DM8 MODIFY syntax. */
public class DamengAddDefaultValueGenerator extends AddDefaultValueGenerator {
    @Override
    public int getPriority() {
        return PRIORITY_DATABASE;
    }

    @Override
    public boolean supports(AddDefaultValueStatement statement, Database database) {
        return database instanceof DamengDatabase;
    }

    @Override
    public Sql[] generateSql(AddDefaultValueStatement statement, Database database,
            SqlGeneratorChain chain) {
        Object value = statement.getDefaultValue();
        return new Sql[] {new UnparsedSql("ALTER TABLE "
                + database.escapeTableName(statement.getCatalogName(), statement.getSchemaName(),
                        statement.getTableName())
                + " MODIFY " + database.escapeColumnName(statement.getCatalogName(),
                        statement.getSchemaName(), statement.getTableName(), statement.getColumnName())
                + " DEFAULT " + DataTypeFactory.getInstance().fromObject(value, database)
                        .objectToSql(value, database),
                getAffectedColumn(statement))};
    }
}
