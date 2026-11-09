package com.luokuiai.liquibase.dameng;

import liquibase.database.Database;
import liquibase.sqlgenerator.core.SetTableRemarksGenerator;
import liquibase.statement.core.SetTableRemarksStatement;

/** Enables the standard COMMENT ON TABLE implementation for DM8. */
public class DamengSetTableRemarksGenerator extends SetTableRemarksGenerator {
    @Override
    public int getPriority() {
        return PRIORITY_DATABASE;
    }

    @Override
    public boolean supports(SetTableRemarksStatement statement, Database database) {
        return database instanceof DamengDatabase;
    }
}
