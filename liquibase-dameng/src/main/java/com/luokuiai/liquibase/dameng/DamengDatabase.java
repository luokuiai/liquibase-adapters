package com.luokuiai.liquibase.dameng;

import java.math.BigInteger;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Arrays;

import liquibase.CatalogAndSchema;
import liquibase.database.AbstractJdbcDatabase;
import liquibase.database.DatabaseConnection;
import liquibase.exception.DatabaseException;
import liquibase.exception.UnexpectedLiquibaseException;
import liquibase.statement.SqlStatement;
import liquibase.statement.core.RawParameterizedSqlStatement;
import liquibase.structure.DatabaseObject;

/** Common DM8 behavior, independent of the selected compatibility mode. */
public class DamengDatabase extends AbstractJdbcDatabase {
    private DamengCompatMode compatMode;
    private boolean ddlInTransaction;
    private String metadataEscape = "!";

    /** Creates an adapter whose mode is detected when a JDBC connection is attached. */
    public DamengDatabase() {
        unquotedObjectsAreUppercased = true;
        setCurrentDateTimeFunction("CURRENT_TIMESTAMP");
        sequenceNextValueFunction = "%s.NEXTVAL";
        sequenceCurrentValueFunction = "%s.CURRVAL";
    }

    /**
     * @return the mode detected from the attached connection
     * @throws UnexpectedLiquibaseException if no JDBC connection has been initialized
     */
    public DamengCompatMode getCompatMode() {
        if (compatMode == null) {
            throw new UnexpectedLiquibaseException(
                    "DM8 compatibility mode is available after attaching a JDBC connection");
        }
        return compatMode;
    }

    @Override
    public int getPriority() {
        return PRIORITY_DATABASE;
    }

    @Override
    public boolean isCorrectDatabaseImplementation(DatabaseConnection connection)
            throws DatabaseException {
        if (!DamengSupport.isDamengConnection(connection)) {
            return false;
        }
        DamengSupport.readMode(connection);
        return true;
    }

    @Override
    public void setConnection(DatabaseConnection connection) {
        try {
            if (!DamengSupport.isDamengConnection(connection)) {
                throw new DatabaseException("A DM8 connection is required");
            }
            compatMode = DamengSupport.readMode(connection);
            ddlInTransaction = DamengSupport.readInt(connection,
                    DamengSupport.DDL_QUERY) == 0;
            DatabaseMetaData metadata = connection.getUnderlyingConnection().getMetaData();
            metadataEscape = metadata.getSearchStringEscape();
            if (metadataEscape == null || metadataEscape.isEmpty()) {
                throw new DatabaseException("DM8 JDBC driver did not supply a metadata search escape");
            }
            unquotedObjectsAreUppercased = metadata.storesUpperCaseIdentifiers()
                    ? Boolean.TRUE : metadata.storesLowerCaseIdentifiers()
                    ? Boolean.FALSE : null;
            caseSensitive = DamengSupport.readInt(connection, DamengSupport.CASE_QUERY) == 1;
            String keywords = metadata.getSQLKeywords();
            if (keywords != null && !keywords.isBlank()) {
                addReservedWords(Arrays.asList(keywords.split(",")));
            }
        } catch (DatabaseException | SQLException exception) {
            throw new UnexpectedLiquibaseException("Unable to initialize DM8 adapter", exception);
        }
        super.setConnection(connection);
    }

    @Override
    public String getShortName() {
        return "dameng";
    }

    @Override
    protected String getDefaultDatabaseProductName() {
        return "DM DBMS";
    }

    @Override
    public String getDefaultDriver(String url) {
        return DamengSupport.isDamengUrl(url) ? DamengSupport.DRIVER_CLASS : null;
    }

    @Override
    public Integer getDefaultPort() {
        return DamengSupport.DEFAULT_PORT;
    }

    @Override
    public boolean supportsDDLInTransaction() {
        return ddlInTransaction;
    }

    @Override
    public boolean supportsInitiallyDeferrableColumns() {
        return false;
    }

    @Override
    public boolean supportsTablespaces() {
        return true;
    }

    @Override
    public boolean supportsSchemas() {
        return true;
    }

    @Override
    public boolean supportsCatalogs() {
        // Liquibase 5.x discards schema names during standardization when the
        // catalog container is disabled. DM8 has no JDBC catalog qualification.
        return true;
    }

    @Override
    public boolean supportsCatalogInObjectName(Class<? extends DatabaseObject> type) {
        return false;
    }

    @Override
    public String getDefaultCatalogName() {
        return null;
    }

    @Override
    public String getJdbcCatalogName(CatalogAndSchema schema) {
        return null;
    }

    @Override
    public CatalogAndSchema getSchemaFromJdbcInfo(String rawCatalogName,
            String rawSchemaName) {
        return new CatalogAndSchema(null, rawSchemaName);
    }

    @Override
    public String escapeForLike(String name) {
        if (name == null) {
            return null;
        }
        return name.replace(metadataEscape, metadataEscape + metadataEscape)
                .replace("%", metadataEscape + "%")
                .replace("_", metadataEscape + "_");
    }

    @Override
    protected SqlStatement getConnectionSchemaNameCallStatement() {
        return new RawParameterizedSqlStatement("SELECT SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA')");
    }

    @Override
    public String getAutoIncrementClause(BigInteger startWith, BigInteger incrementBy,
            String generationType, Boolean defaultOnNull) {
        return "IDENTITY(" + (startWith == null ? BigInteger.ONE : startWith)
                + ", " + (incrementBy == null ? BigInteger.ONE : incrementBy) + ")";
    }
}
