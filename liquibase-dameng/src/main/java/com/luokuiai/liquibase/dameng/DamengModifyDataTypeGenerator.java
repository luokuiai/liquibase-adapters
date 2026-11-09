package com.luokuiai.liquibase.dameng;

import liquibase.database.Database;
import liquibase.sqlgenerator.core.ModifyDataTypeGenerator;
import liquibase.statement.core.ModifyDataTypeStatement;

/** Uses DM8 MODIFY syntax rather than PostgreSQL's ALTER COLUMN TYPE. */
public class DamengModifyDataTypeGenerator extends ModifyDataTypeGenerator {
    @Override
    public int getPriority() {
        return PRIORITY_DATABASE;
    }

    @Override
    public boolean supports(ModifyDataTypeStatement statement, Database database) {
        return database instanceof DamengDatabase;
    }

    @Override
    protected String getModifyString(Database database) {
        return "MODIFY";
    }

    @Override
    protected String getPreDataTypeString(Database database) {
        return " ";
    }
}
