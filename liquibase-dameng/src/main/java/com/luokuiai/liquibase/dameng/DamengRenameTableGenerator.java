package com.luokuiai.liquibase.dameng;

import liquibase.database.Database;
import liquibase.sql.Sql;
import liquibase.sql.UnparsedSql;
import liquibase.sqlgenerator.SqlGeneratorChain;
import liquibase.sqlgenerator.core.RenameTableGenerator;
import liquibase.statement.core.RenameTableStatement;
import liquibase.structure.core.Table;

/** Qualifies only the source table in a DM8 rename. */
public class DamengRenameTableGenerator extends RenameTableGenerator {
    @Override
    public int getPriority() {
        return PRIORITY_DATABASE;
    }

    @Override
    public boolean supports(RenameTableStatement statement, Database database) {
        return database instanceof DamengDatabase;
    }

    @Override
    public Sql[] generateSql(RenameTableStatement statement, Database database,
            SqlGeneratorChain chain) {
        return new Sql[] {new UnparsedSql("ALTER TABLE "
                + database.escapeTableName(statement.getCatalogName(), statement.getSchemaName(),
                        statement.getOldTableName())
                + " RENAME TO " + database.escapeObjectName(statement.getNewTableName(), Table.class),
                getAffectedOldTable(statement), getAffectedNewTable(statement))};
    }
}
