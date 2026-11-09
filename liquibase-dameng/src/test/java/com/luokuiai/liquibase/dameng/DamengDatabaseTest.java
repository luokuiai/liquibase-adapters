package com.luokuiai.liquibase.dameng;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;

import liquibase.CatalogAndSchema;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.ObjectQuotingStrategy;
import liquibase.database.core.PostgresDatabase;
import liquibase.datatype.DataTypeFactory;
import liquibase.statement.SequenceCurrentValueFunction;
import liquibase.statement.SequenceNextValueFunction;
import liquibase.structure.core.Column;
import liquibase.structure.core.Table;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DamengDatabaseTest {
    @Test
    void registersTheDriverForDmUrlsOnly() {
        DatabaseFactory factory = DatabaseFactory.getInstance();
        assertEquals("dm.jdbc.driver.DmDriver", factory.findDefaultDriver(
                "jdbc:dm://localhost:5236"));
        assertEquals("org.postgresql.Driver", factory.findDefaultDriver(
                "jdbc:postgresql://localhost/test"));
        assertEquals(5236, new DamengDatabase().getDefaultPort());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 4, 7})
    void usesDmIdentityAndSequenceSyntaxInEveryMode(int mode) throws Exception {
        Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(
                DamengTestConnections.connection(mode));
        assertEquals("IDENTITY(1, 1)",
                database.getAutoIncrementClause(null, null, null, null));
        assertEquals("IDENTITY(10, 5)", database.getAutoIncrementClause(
                BigInteger.TEN, BigInteger.valueOf(5), null, null));
        assertEquals("probe_seq.NEXTVAL", database.generateDatabaseFunctionValue(
                new SequenceNextValueFunction("probe_seq")));
        assertEquals("probe_seq.CURRVAL", database.generateDatabaseFunctionValue(
                new SequenceCurrentValueFunction("probe_seq")));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 4, 7})
    void mapsBooleansToBitAndNumericLiterals(int mode) throws Exception {
        Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(
                DamengTestConnections.connection(mode));
        var type = DataTypeFactory.getInstance().fromDescription("boolean", database);
        assertInstanceOf(DamengBooleanType.class, type);
        assertEquals("BIT", type.toDatabaseDataType(database).toString());
        assertEquals("1", type.objectToSql(true, database));
        assertEquals("0", type.objectToSql(false, database));
        assertFalse(DataTypeFactory.getInstance().fromDescription(
                "boolean", new PostgresDatabase()) instanceof DamengBooleanType);
    }

    @Test
    void derivesIdentifierRulesFromJdbcInsteadOfTheModeName() throws Exception {
        DamengDatabase uppercase = new DamengDatabase();
        uppercase.setConnection(DamengTestConnections.connection(7, 1, false));
        assertTrue(uppercase.isCaseSensitive());
        assertEquals("PROBE", uppercase.correctObjectName("probe", Table.class));
        assertEquals("\"MixedCase\"", uppercase.correctObjectName(
                "\"MixedCase\"", Column.class));

        DamengDatabase lowercase = new DamengDatabase();
        lowercase.setConnection(DamengTestConnections.connection(0, 1, true));
        assertEquals("probe", lowercase.correctObjectName("PROBE", Table.class));
        lowercase.setObjectQuotingStrategy(ObjectQuotingStrategy.QUOTE_ALL_OBJECTS);
        assertEquals("MixedCase", lowercase.correctObjectName("MixedCase", Column.class));
    }

    @Test
    void readsDdlTransactionBehaviorFromTheConnection() throws Exception {
        DamengDatabase autoCommit = new DamengDatabase();
        autoCommit.setConnection(DamengTestConnections.connection(0, 1, false));
        assertFalse(autoCommit.supportsDDLInTransaction());
        DamengDatabase transactional = new DamengDatabase();
        transactional.setConnection(DamengTestConnections.connection(0, 0, false));
        assertTrue(transactional.supportsDDLInTransaction());
        assertFalse(transactional.getAutoCommitMode());
    }

    @Test
    void usesSchemasWithoutCatalogQualification() {
        DamengDatabase database = new DamengDatabase();
        database.setDefaultSchemaName("app");
        assertTrue(database.supportsSchemas());
        assertTrue(database.createsIndexesForForeignKeys());
        assertTrue(database.supportsDropTableCascadeConstraints());
        assertNull(database.getJdbcCatalogName(database.getDefaultSchema()));
        assertFalse(database.supportsCatalogInObjectName(Table.class));
        assertEquals("APP", database.getDefaultSchemaName());
        assertEquals("app.probe", database.escapeTableName("ignored", "app", "probe"));
        assertEquals("OTHER", new CatalogAndSchema(null, "OTHER")
                .customize(database).getSchemaName());
        assertEquals("QuotedSchema", database.getSchemaFromJdbcInfo(
                "ignored", "QuotedSchema").getSchemaName());
    }

    @Test
    void usesTheJdbcMetadataEscapeForSchemaTableAndColumnPatterns() throws Exception {
        DamengDatabase database = new DamengDatabase();
        database.setConnection(DamengTestConnections.connection(0));
        assertEquals("LB!_APP", database.escapeForLike("LB_APP"));
        assertEquals("A!%B!!C!_D", database.escapeForLike("A%B!C_D"));
        assertNull(database.escapeForLike(null));
    }
}
