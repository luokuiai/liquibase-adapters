package com.luokuiai.liquibase.dameng;

import liquibase.database.Database;
import liquibase.exception.ValidationErrors;
import liquibase.sqlgenerator.SqlGeneratorChain;
import liquibase.sqlgenerator.core.AddAutoIncrementGenerator;
import liquibase.statement.core.AddAutoIncrementStatement;

/** Reports the native DM8 restriction before running an invalid ALTER statement. */
public class DamengAddAutoIncrementGenerator extends AddAutoIncrementGenerator {
    @Override
    public int getPriority() {
        return PRIORITY_DATABASE;
    }

    @Override
    public boolean supports(AddAutoIncrementStatement statement, Database database) {
        return database instanceof DamengDatabase;
    }

    @Override
    public ValidationErrors validate(AddAutoIncrementStatement statement, Database database,
            SqlGeneratorChain chain) {
        return super.validate(statement, database, chain).addError(
                "DM8 cannot convert an existing column to an identity column. "
                        + "Declare autoIncrement on createTable or on a new addColumn instead.");
    }
}
