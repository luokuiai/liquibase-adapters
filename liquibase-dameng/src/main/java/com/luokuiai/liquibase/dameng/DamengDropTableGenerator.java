package com.luokuiai.liquibase.dameng;

import liquibase.database.Database;
import liquibase.sql.Sql;
import liquibase.sql.UnparsedSql;
import liquibase.sqlgenerator.SqlGeneratorChain;
import liquibase.sqlgenerator.core.DropTableGenerator;
import liquibase.statement.core.DropTableStatement;

/** Uses DM8 CASCADE CONSTRAINTS when dropping a referenced table. */
public class DamengDropTableGenerator extends DropTableGenerator {
    @Override
    public int getPriority() {
        return PRIORITY_DATABASE;
    }

    @Override
    public boolean supports(DropTableStatement statement, Database database) {
        return database instanceof DamengDatabase;
    }

    @Override
    public Sql[] generateSql(DropTableStatement statement, Database database,
            SqlGeneratorChain chain) {
        return new Sql[] {new UnparsedSql("DROP TABLE "
                + database.escapeTableName(statement.getCatalogName(), statement.getSchemaName(),
                        statement.getTableName())
                + (statement.isCascadeConstraints() ? " CASCADE CONSTRAINTS" : ""),
                getAffectedTable(statement))};
    }
}
