package com.luokuiai.liquibase.dameng;

import liquibase.database.Database;
import liquibase.exception.ValidationErrors;
import liquibase.sql.Sql;
import liquibase.sql.UnparsedSql;
import liquibase.sqlgenerator.SqlGeneratorChain;
import liquibase.sqlgenerator.core.SetNullableGenerator;
import liquibase.statement.core.SetNullableStatement;

/** Alters DM8 column nullability without rewriting the column's type or default. */
public class DamengSetNullableGenerator extends SetNullableGenerator {
    @Override
    public int getPriority() {
        return PRIORITY_DATABASE;
    }

    @Override
    public boolean supports(SetNullableStatement statement, Database database) {
        return database instanceof DamengDatabase;
    }

    @Override
    public ValidationErrors validate(SetNullableStatement statement, Database database,
            SqlGeneratorChain chain) {
        ValidationErrors errors = super.validate(statement, database, chain);
        if (!statement.isValidate()) {
            errors.addError("DM8 does not support validate=false for a NOT NULL constraint");
        }
        return errors;
    }

    @Override
    public Sql[] generateSql(SetNullableStatement statement, Database database,
            SqlGeneratorChain chain) {
        String column = database.escapeColumnName(statement.getCatalogName(),
                statement.getSchemaName(), statement.getTableName(), statement.getColumnName());
        return new Sql[] {new UnparsedSql("ALTER TABLE "
                + database.escapeTableName(statement.getCatalogName(), statement.getSchemaName(),
                        statement.getTableName())
                + " MODIFY " + column
                + (!statement.isNullable() && statement.getConstraintName() != null
                        ? " CONSTRAINT " + database.escapeConstraintName(statement.getConstraintName()) : "")
                + (statement.isNullable() ? " NULL" : " NOT NULL"),
                getAffectedColumn(statement))};
    }
}
