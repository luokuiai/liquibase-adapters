package com.luokuiai.liquibase.dameng;

import liquibase.database.Database;
import liquibase.datatype.DataTypeInfo;
import liquibase.datatype.DatabaseDataType;
import liquibase.datatype.LiquibaseDataType;
import liquibase.datatype.core.BooleanType;

/** Maps logical booleans, including Liquibase's lock column, to DM8 BIT. */
@DataTypeInfo(name = "boolean",
        aliases = {"java.sql.Types.BOOLEAN", "java.lang.Boolean", "bit", "bool"},
        minParameters = 0, maxParameters = 0,
        priority = LiquibaseDataType.PRIORITY_DATABASE)
public class DamengBooleanType extends BooleanType {
    @Override
    public boolean supports(Database database) {
        return database instanceof DamengDatabase;
    }

    @Override
    public DatabaseDataType toDatabaseDataType(Database database) {
        return new DatabaseDataType("BIT");
    }

    @Override
    protected boolean isNumericBoolean(Database database) {
        return true;
    }
}
