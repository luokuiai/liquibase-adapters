package com.luokuiai.liquibase.dameng;

import liquibase.database.Database;
import liquibase.exception.ValidationErrors;
import liquibase.sql.Sql;
import liquibase.sql.UnparsedSql;
import liquibase.sqlgenerator.SqlGeneratorChain;
import liquibase.sqlgenerator.core.CreateSequenceGenerator;
import liquibase.statement.core.CreateSequenceStatement;

/** Preserves all DM8 sequence options, including explicit false and zero values. */
public class DamengCreateSequenceGenerator extends CreateSequenceGenerator {
    @Override
    public int getPriority() {
        return PRIORITY_DATABASE;
    }

    @Override
    public boolean supports(CreateSequenceStatement statement, Database database) {
        return database instanceof DamengDatabase;
    }

    @Override
    public ValidationErrors validate(CreateSequenceStatement statement, Database database,
            SqlGeneratorChain chain) {
        ValidationErrors errors = super.validate(statement, database, chain);
        errors.checkDisallowedField("dataType", statement.getDataType(), database, DamengDatabase.class);
        return errors;
    }

    @Override
    public Sql[] generateSql(CreateSequenceStatement statement, Database database,
            SqlGeneratorChain chain) {
        StringBuilder sql = new StringBuilder("CREATE SEQUENCE ")
                .append(database.escapeSequenceName(statement.getCatalogName(),
                        statement.getSchemaName(), statement.getSequenceName()));
        if (statement.getStartValue() != null) {
            sql.append(" START WITH ").append(statement.getStartValue());
        }
        DamengSequenceSql.appendOptions(sql, statement.getIncrementBy(), statement.getMinValue(),
                statement.getMaxValue(), statement.getCacheSize(), statement.getCycle(),
                statement.getOrdered());
        return new Sql[] {new UnparsedSql(sql.toString(), getAffectedSequence(statement))};
    }
}
