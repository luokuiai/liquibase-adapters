package com.luokuiai.liquibase.dameng;

import liquibase.CatalogAndSchema;
import liquibase.database.Database;
import liquibase.sql.Sql;
import liquibase.sql.UnparsedSql;
import liquibase.sqlgenerator.SqlGeneratorChain;
import liquibase.sqlgenerator.core.GetViewDefinitionGenerator;
import liquibase.statement.core.GetViewDefinitionStatement;
import liquibase.structure.core.View;

/** Reads DM8 view definitions without relying on INFORMATION_SCHEMA. */
public class DamengViewDefinitionGenerator extends GetViewDefinitionGenerator {
    @Override
    public int getPriority() {
        return PRIORITY_DATABASE;
    }

    @Override
    public boolean supports(GetViewDefinitionStatement statement, Database database) {
        return database instanceof DamengDatabase;
    }

    @Override
    public Sql[] generateSql(GetViewDefinitionStatement statement, Database database,
            SqlGeneratorChain chain) {
        CatalogAndSchema schema = new CatalogAndSchema(statement.getCatalogName(),
                statement.getSchemaName()).customize(database);
        String owner = database.escapeStringForDatabase(schema.getSchemaName());
        String name = database.escapeStringForDatabase(
                database.correctObjectName(statement.getViewName(), View.class));
        return new Sql[] {new UnparsedSql("SELECT TEXT FROM ALL_VIEWS WHERE OWNER = '"
                + owner + "' AND VIEW_NAME = '" + name + "'")};
    }
}
