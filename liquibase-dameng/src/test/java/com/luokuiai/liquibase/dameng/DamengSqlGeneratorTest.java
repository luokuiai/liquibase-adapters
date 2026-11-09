package com.luokuiai.liquibase.dameng;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.util.List;
import java.util.ServiceLoader;

import com.luokuiai.liquibase.kingbase.KingbaseMySqlDatabase;
import com.luokuiai.liquibase.kingbase.KingbasePostgresDatabase;
import liquibase.database.Database;
import liquibase.database.ObjectQuotingStrategy;
import liquibase.database.core.MySQLDatabase;
import liquibase.database.core.PostgresDatabase;
import liquibase.datatype.DataTypeFactory;
import liquibase.exception.UnexpectedLiquibaseException;
import liquibase.sqlgenerator.SqlGenerator;
import liquibase.sqlgenerator.SqlGeneratorFactory;
import liquibase.statement.SqlStatement;
import liquibase.statement.core.AddAutoIncrementStatement;
import liquibase.statement.core.AddDefaultValueStatement;
import liquibase.statement.core.AlterSequenceStatement;
import liquibase.statement.core.CreateSequenceStatement;
import liquibase.statement.core.DropDefaultValueStatement;
import liquibase.statement.core.DropTableStatement;
import liquibase.statement.core.InsertOrUpdateStatement;
import liquibase.statement.core.ModifyDataTypeStatement;
import liquibase.statement.core.RenameTableStatement;
import liquibase.statement.core.SetColumnRemarksStatement;
import liquibase.statement.core.SetNullableStatement;
import liquibase.statement.core.SetTableRemarksStatement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DamengSqlGeneratorTest {
    @ParameterizedTest
    @ValueSource(ints = {0, 4, 7})
    void selectsDmAlterSyntaxThroughSpi(int mode) throws Exception {
        Database database = database(mode);
        assertSql(database, new ModifyDataTypeStatement(null, "APP", "T", "NAME", "varchar(80)"),
                "ALTER TABLE APP.T MODIFY NAME VARCHAR(80)");
        assertSql(database, new SetNullableStatement(null, "APP", "T", "NAME", "varchar(80)", false),
                "ALTER TABLE APP.T MODIFY NAME NOT NULL");
        assertSql(database, new SetNullableStatement(null, "APP", "T", "NAME", "varchar(80)", true),
                "ALTER TABLE APP.T MODIFY NAME NULL");
        assertSql(database, new SetNullableStatement(null, "APP", "T", "NAME", "varchar(80)",
                        false, "NN_NAME"),
                "ALTER TABLE APP.T MODIFY NAME CONSTRAINT NN_NAME NOT NULL");
        assertSql(database, new AddDefaultValueStatement(null, "APP", "T", "NAME", "varchar(80)", "O'Brien"),
                "ALTER TABLE APP.T MODIFY NAME DEFAULT 'O''Brien'");
        assertSql(database, new AddDefaultValueStatement(null, "APP", "T", "ACTIVE", "boolean", false),
                "ALTER TABLE APP.T MODIFY ACTIVE DEFAULT 0");
        assertSql(database, new DropDefaultValueStatement(null, "APP", "T", "NAME", "varchar(80)"),
                "ALTER TABLE APP.T MODIFY NAME DEFAULT NULL");
        assertSql(database, new RenameTableStatement(null, "APP", "OLD_T", "NEW_T"),
                "ALTER TABLE APP.OLD_T RENAME TO NEW_T");
        assertSql(database, new DropTableStatement(null, "APP", "T", true),
                "DROP TABLE APP.T CASCADE CONSTRAINTS");
        assertSql(database, new DropTableStatement(null, "APP", "T", false),
                "DROP TABLE APP.T");
    }

    @Test
    void preservesQuotedNamesAndEscapesCommentLiterals() {
        Database database = new DamengDatabase();
        database.setObjectQuotingStrategy(ObjectQuotingStrategy.QUOTE_ALL_OBJECTS);
        assertSql(database, new RenameTableStatement(null, "MixedSchema", "OldTable", "NewTable"),
                "ALTER TABLE \"MixedSchema\".\"OldTable\" RENAME TO \"NewTable\"");
        assertSql(database, new SetTableRemarksStatement(null, "MixedSchema", "Table", "O'Brien"),
                "COMMENT ON TABLE \"MixedSchema\".\"Table\" IS 'O''Brien'");
        assertSql(database, new SetColumnRemarksStatement(null, "MixedSchema", "Table", "Column", "O'Brien"),
                "COMMENT ON COLUMN \"MixedSchema\".\"Table\".\"Column\" IS 'O''Brien'");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 4, 7})
    void preservesSequenceFalseAndZeroOptions(int mode) throws Exception {
        Database database = database(mode);
        assertSql(database, new CreateSequenceStatement(null, "APP", "S")
                        .setStartValue(BigInteger.TEN).setIncrementBy(BigInteger.TWO)
                        .setMinValue(BigInteger.ONE).setMaxValue(BigInteger.valueOf(1000))
                        .setCacheSize(BigInteger.ZERO).setCycle(false).setOrdered(false),
                "CREATE SEQUENCE APP.S START WITH 10 INCREMENT BY 2 MINVALUE 1 MAXVALUE 1000"
                        + " NOCACHE NOCYCLE NOORDER");
        assertSql(database, new AlterSequenceStatement(null, "APP", "S")
                        .setCacheSize(BigInteger.valueOf(8)).setCycle(true).setOrdered(true),
                "ALTER SEQUENCE APP.S CACHE 8 CYCLE ORDER");
        assertSql(database, new AlterSequenceStatement(null, "APP", "S")
                        .setCacheSize(BigInteger.ZERO).setCycle(false).setOrdered(false),
                "ALTER SEQUENCE APP.S NOCACHE NOCYCLE NOORDER");
    }

    @Test
    void rejectsUnsupportedOptionsBeforeExecution() {
        Database database = new DamengDatabase();
        var factory = SqlGeneratorFactory.getInstance();
        assertTrue(factory.validate(new AddAutoIncrementStatement(null, "APP", "T", "ID",
                "bigint", BigInteger.ONE, BigInteger.ONE, null, null), database).toString()
                .contains("cannot convert an existing column"));
        assertTrue(factory.validate(new CreateSequenceStatement(null, "APP", "S")
                .setDataType("bigint"), database).hasErrors());
        assertTrue(factory.validate(new AlterSequenceStatement(null, "APP", "S")
                .setDataType("bigint"), database).hasErrors());
        SetNullableStatement nullable = new SetNullableStatement(null, "APP", "T", "ID", "int", false);
        nullable.setValidate(false);
        assertTrue(factory.validate(nullable, database).hasErrors());
        assertThrows(UnexpectedLiquibaseException.class, () -> database.getAutoIncrementClause(
                null, null, "ALWAYS", null));
        assertThrows(UnexpectedLiquibaseException.class, () -> database.getAutoIncrementClause(
                null, null, null, true));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 4, 7})
    void mergesCompositeKeysAndRespectsColumnUpdateFlags(int mode) throws Exception {
        Database database = database(mode);
        InsertOrUpdateStatement statement = new InsertOrUpdateStatement(null, "APP", "T", "ID, CODE");
        statement.addColumnValue("ID", 1);
        statement.addColumnValue("CODE", "O'Brien");
        statement.addColumnValue("NAME", "更新");
        statement.addColumnValue("FIXED", "keep on update");
        statement.setAllowColumnUpdate("FIXED", false);
        String sql = sql(statement, database);
        assertTrue(sql.startsWith("MERGE INTO APP.T t USING (SELECT"));
        assertTrue(sql.contains("'O''Brien' AS CODE"));
        assertTrue(sql.contains("t.ID = s.ID"));
        assertTrue(sql.contains("t.CODE = s.CODE"));
        assertTrue(sql.contains("WHEN MATCHED THEN UPDATE SET t.NAME = s.NAME"));
        assertFalse(sql.contains("t.FIXED = s.FIXED"));
        assertTrue(sql.contains("WHEN NOT MATCHED THEN INSERT"));
        statement.setOnlyUpdate(true);
        assertFalse(sql(statement, database).contains("WHEN NOT MATCHED"));
    }

    @Test
    void handlesKeyOnlyRowsAndMissingKeysExplicitly() {
        Database database = new DamengDatabase();
        InsertOrUpdateStatement statement = new InsertOrUpdateStatement(null, "APP", "T", "ID");
        statement.addColumnValue("ID", 1);
        assertFalse(sql(statement, database).contains("WHEN MATCHED"));
        assertTrue(sql(statement, database).contains("WHEN NOT MATCHED"));
        statement.setOnlyUpdate(true);
        assertEquals(0, SqlGeneratorFactory.getInstance().generateSql(statement, database).length);
        InsertOrUpdateStatement missing = new InsertOrUpdateStatement(null, "APP", "T", "ID,OTHER");
        missing.addColumnValue("ID", 1);
        assertTrue(SqlGeneratorFactory.getInstance().validate(missing, database).hasErrors());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void newGeneratorsDoNotClaimOtherAdaptersStatements() {
        List<SqlStatement> statements = List.of(
                new ModifyDataTypeStatement(null, null, "T", "C", "int"),
                new SetNullableStatement(null, null, "T", "C", "int", true),
                new AddDefaultValueStatement(null, null, "T", "C", "int", 1),
                new DropDefaultValueStatement(null, null, "T", "C", "int"),
                new RenameTableStatement(null, null, "T", "U"),
                new SetTableRemarksStatement(null, null, "T", "remarks"),
                new SetColumnRemarksStatement(null, null, "T", "C", "remarks"),
                new CreateSequenceStatement(null, null, "S"),
                new AlterSequenceStatement(null, null, "S"),
                new AddAutoIncrementStatement(null, null, "T", "ID", "int", null, null, null, null),
                new InsertOrUpdateStatement(null, null, "T", "ID"),
                new DropTableStatement(null, null, "T", true));
        int generators = 0;
        for (SqlGenerator generator : ServiceLoader.load(SqlGenerator.class)) {
            if (!generator.getClass().getPackageName().equals(DamengDatabase.class.getPackageName())) {
                continue;
            }
            generators++;
            for (SqlStatement statement : statements) {
                try {
                    for (Database database : List.of(new PostgresDatabase(), new MySQLDatabase(),
                            new KingbasePostgresDatabase(), new KingbaseMySqlDatabase())) {
                        assertFalse(generator.supports(statement, database));
                    }
                } catch (ClassCastException ignored) {
                    // SPI generators have different statement types.
                }
            }
        }
        assertEquals(13, generators);
        assertEquals("com.kingbase8.Driver", liquibase.database.DatabaseFactory.getInstance()
                .findDefaultDriver("jdbc:kingbase8://localhost/test"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 4, 7})
    void preservesBinaryTypesAndPortableUuidMapping(int mode) throws Exception {
        Database database = database(mode);
        var factory = DataTypeFactory.getInstance();
        assertEquals("BINARY(16)", factory.fromDescription("binary(16)", database)
                .toDatabaseDataType(database).toString());
        assertEquals("VARBINARY(32)", factory.fromDescription("varbinary(32)", database)
                .toDatabaseDataType(database).toString());
        assertEquals("BLOB", factory.fromDescription("blob", database)
                .toDatabaseDataType(database).toString());
        assertEquals("VARBINARY(32)", factory.fromDescription("java.sql.Types.VARBINARY(32)", database)
                .toDatabaseDataType(database).toString());
        assertEquals("char(36)", factory.fromDescription("uuid", database)
                .toDatabaseDataType(database).toString());
        assertFalse(factory.fromDescription("varbinary(32)", new MySQLDatabase())
                instanceof DamengBlobType);
        for (String type : List.of("BIGINT", "INT", "SMALLINT", "TINYINT", "BIT", "DATE", "BLOB", "CLOB")) {
            assertTrue(database.dataTypeIsNotModifiable(type), type);
        }
        assertFalse(database.dataTypeIsNotModifiable("VARCHAR"));
        assertFalse(database.dataTypeIsNotModifiable("DECIMAL"));
    }

    private Database database(int mode) throws Exception {
        DamengDatabase database = new DamengDatabase();
        database.setConnection(DamengTestConnections.connection(mode));
        return database;
    }

    private void assertSql(Database database, SqlStatement statement, String expected) {
        assertFalse(SqlGeneratorFactory.getInstance().validate(statement, database).hasErrors());
        assertEquals(expected, sql(statement, database));
    }

    private String sql(SqlStatement statement, Database database) {
        return SqlGeneratorFactory.getInstance().generateSql(statement, database)[0].toSql();
    }
}
