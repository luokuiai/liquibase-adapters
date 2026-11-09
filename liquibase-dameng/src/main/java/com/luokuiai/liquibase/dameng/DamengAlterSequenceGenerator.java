package com.luokuiai.liquibase.dameng;

import liquibase.database.Database;
import liquibase.exception.ValidationErrors;
import liquibase.sql.Sql;
import liquibase.sql.UnparsedSql;
import liquibase.sqlgenerator.SqlGeneratorChain;
import liquibase.sqlgenerator.core.AlterSequenceGenerator;
import liquibase.statement.core.AlterSequenceStatement;

/** Uses DM8 sequence option syntax for every supported mode. */
public class DamengAlterSequenceGenerator extends AlterSequenceGenerator {
    @Override
    public int getPriority() {
        return PRIORITY_DATABASE;
    }

    @Override
    public boolean supports(AlterSequenceStatement statement, Database database) {
        return database instanceof DamengDatabase;
    }

    @Override
    public ValidationErrors validate(AlterSequenceStatement statement, Database database,
            SqlGeneratorChain chain) {
        ValidationErrors errors = super.validate(statement, database, chain);
        errors.checkDisallowedField("dataType", statement.getDataType(), database, DamengDatabase.class);
        return errors;
    }

    @Override
    public Sql[] generateSql(AlterSequenceStatement statement, Database database,
            SqlGeneratorChain chain) {
        StringBuilder sql = new StringBuilder("ALTER SEQUENCE ")
                .append(database.escapeSequenceName(statement.getCatalogName(),
                        statement.getSchemaName(), statement.getSequenceName()));
        DamengSequenceSql.appendOptions(sql, statement.getIncrementBy(), statement.getMinValue(),
                statement.getMaxValue(), statement.getCacheSize(), statement.getCycle(),
                statement.getOrdered());
        return new Sql[] {new UnparsedSql(sql.toString(), getAffectedSequence(statement))};
    }
}
