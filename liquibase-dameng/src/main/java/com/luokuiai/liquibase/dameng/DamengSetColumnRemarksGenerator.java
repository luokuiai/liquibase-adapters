package com.luokuiai.liquibase.dameng;

import liquibase.database.Database;
import liquibase.sqlgenerator.core.SetColumnRemarksGenerator;
import liquibase.statement.core.SetColumnRemarksStatement;

/** Enables the standard COMMENT ON COLUMN implementation for DM8. */
public class DamengSetColumnRemarksGenerator extends SetColumnRemarksGenerator {
    @Override
    public int getPriority() {
        return PRIORITY_DATABASE;
    }

    @Override
    public boolean supports(SetColumnRemarksStatement statement, Database database) {
        return database instanceof DamengDatabase;
    }
}
