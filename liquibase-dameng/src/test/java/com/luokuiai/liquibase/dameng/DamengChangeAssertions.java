package com.luokuiai.liquibase.dameng;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.resource.ClassLoaderResourceAccessor;

/** Checks persisted state after migration and after reversing the alteration. */
final class DamengChangeAssertions {
    private static final String OWNER = "OWNER = SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA')";

    private DamengChangeAssertions() {
    }

    static void verify(String feature, Connection connection, DamengDatabase database)
            throws Exception {
        try (Liquibase liquibase = new Liquibase(
                "db/changelog/matrix/" + feature + ".xml",
                new ClassLoaderResourceAccessor(), database)) {
            liquibase.update(new Contexts(), new LabelExpression());
            liquibase.update(new Contexts(), new LabelExpression());
            assertEquals(2, integer(connection, "SELECT COUNT(*) FROM DATABASECHANGELOG"),
                    feature);
            check(feature, connection, false);
            liquibase.rollback(1, new Contexts(), new LabelExpression());
            check(feature, connection, true);
            liquibase.rollback(1, new Contexts(), new LabelExpression());
            assertEquals(0, integer(connection, "SELECT COUNT(*) FROM DATABASECHANGELOG"),
                    feature);
            assertEquals(0, integer(connection,
                    "SELECT COUNT(*) FROM ALL_TABLES WHERE " + OWNER + " AND TABLE_NAME NOT IN "
                            + "('DATABASECHANGELOG', 'DATABASECHANGELOGLOCK')"), feature);
            assertEquals(0, integer(connection, "SELECT COUNT(*) FROM ALL_SEQUENCES WHERE "
                    + "SEQUENCE_OWNER = SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA')"),
                    feature);
        }
    }

    private static void check(String feature, Connection connection, boolean rolledBack)
            throws Exception {
        switch (feature) {
            case "columns" -> {
                String table = rolledBack ? "T_COLUMNS" : "T_RENAMED";
                String column = rolledBack ? "NAME" : "LABEL";
                assertEquals(rolledBack ? 20 : 80, integer(connection,
                        "SELECT DATA_LENGTH FROM ALL_TAB_COLUMNS WHERE " + OWNER + " AND TABLE_NAME = '"
                                + table + "' AND COLUMN_NAME = '" + column + "'"));
                assertEquals(rolledBack ? "Y" : "N", string(connection,
                        "SELECT NULLABLE FROM ALL_TAB_COLUMNS WHERE " + OWNER + " AND TABLE_NAME = '"
                                + table + "' AND COLUMN_NAME = '" + column + "'"));
                assertEquals(rolledBack ? 1 : 0, integer(connection,
                        "SELECT COUNT(*) FROM ALL_TAB_COLUMNS WHERE " + OWNER + " AND TABLE_NAME = '"
                                + table + "' AND COLUMN_NAME = 'EXTRA'"));
                assertEquals(rolledBack ? 0 : 1, integer(connection,
                        "SELECT COUNT(*) FROM ALL_TAB_COLUMNS WHERE " + OWNER + " AND TABLE_NAME = '"
                                + table + "' AND COLUMN_NAME = 'FLAG'"));
                assertTrue(string(connection,
                        "SELECT DATA_DEFAULT FROM ALL_TAB_COLUMNS WHERE " + OWNER + " AND TABLE_NAME = '"
                                + table + "' AND COLUMN_NAME = '" + column + "'")
                        .contains(rolledBack ? "old" : "new"));
                if (rolledBack) {
                    assertNull(string(connection, "SELECT NAME FROM T_COLUMNS WHERE ID = 1"));
                } else {
                    assertEquals("filled", string(connection, "SELECT LABEL FROM T_RENAMED WHERE ID = 1"));
                    assertEquals(1, integer(connection, "SELECT FLAG FROM T_RENAMED WHERE ID = 1"));
                }
            }
            case "constraints" -> {
                assertEquals(rolledBack ? 0 : 2, integer(connection,
                        "SELECT COUNT(*) FROM ALL_CONSTRAINTS WHERE " + OWNER
                                + " AND TABLE_NAME IN ('T_PARENT', 'T_CHILD') AND CONSTRAINT_TYPE = 'P'"));
                assertEquals(rolledBack ? 0 : 1, integer(connection,
                        "SELECT COUNT(*) FROM ALL_CONSTRAINTS WHERE " + OWNER + " AND CONSTRAINT_TYPE = 'U'"));
                assertEquals(rolledBack ? 0 : 1, integer(connection,
                        "SELECT COUNT(*) FROM ALL_CONSTRAINTS WHERE " + OWNER + " AND CONSTRAINT_TYPE = 'R'"));
                if (!rolledBack) {
                    assertEquals(1, integer(connection, "SELECT COUNT(*) FROM T_CHILD"));
                    assertEquals(3, integer(connection, "SELECT P_ID FROM T_CHILD"));
                }
            }
            case "indexes" -> {
                assertEquals(rolledBack ? 0 : 2, integer(connection,
                        "SELECT COUNT(*) FROM ALL_INDEXES WHERE " + OWNER
                                + " AND INDEX_NAME IN ('IX_COMPOSITE', 'IX_UNIQUE')"));
                assertEquals(0, integer(connection,
                        "SELECT COUNT(*) FROM ALL_INDEXES WHERE " + OWNER + " AND INDEX_NAME = 'IX_TEMP'"));
            }
            case "sequences" -> {
                assertEquals(rolledBack ? 2 : 3, integer(connection,
                        "SELECT INCREMENT_BY FROM ALL_SEQUENCES WHERE SEQUENCE_NAME = 'S_MATRIX'"));
                assertEquals(rolledBack ? 8 : 0, integer(connection,
                        "SELECT CACHE_SIZE FROM ALL_SEQUENCES WHERE SEQUENCE_NAME = 'S_MATRIX'"));
                assertEquals(rolledBack ? "N" : "Y", string(connection,
                        "SELECT CYCLE_FLAG FROM ALL_SEQUENCES WHERE SEQUENCE_NAME = 'S_MATRIX'"));
                assertEquals(rolledBack ? 1 : 2, integer(connection,
                        "SELECT MARKER FROM V_MATRIX"));
            }
            case "dml" -> {
                assertEquals(2, integer(connection, "SELECT COUNT(*) FROM T_DATA"));
                assertEquals(rolledBack ? "initial" : "更新 O'Brien", string(connection,
                        "SELECT NAME FROM T_DATA WHERE ID = 1"));
                assertEquals(rolledBack ? 1 : 0, integer(connection,
                        "SELECT COUNT(*) FROM T_DATA WHERE ID = 2"));
                assertEquals(rolledBack ? 0 : 1, integer(connection,
                        "SELECT COUNT(*) FROM T_DATA WHERE ID = 3 AND ACTIVE = 0 "
                                + "AND OPTIONAL_VALUE IS NULL AND AMOUNT = 12.50"));
            }
            case "types" -> {
                assertEquals(1, integer(connection, "SELECT COUNT(*) FROM T_TYPES"));
                assertEquals("你好 O'Brien", string(connection,
                        "SELECT C_VARCHAR FROM T_TYPES"));
                assertNotNull(string(connection, "SELECT C_TIMESTAMP FROM T_TYPES"));
                assertEquals("VARBINARY", columnInfo(connection, "T_TYPES", "C_VARBINARY", "TYPE_NAME"));
                assertEquals("BINARY", columnInfo(connection, "T_TYPES", "C_BINARY", "TYPE_NAME"));
                assertEquals(rolledBack ? 1 : 0, integer(connection, "SELECT C_BOOLEAN FROM T_TYPES"));
                assertEquals("DM8 binary fixture\n", string(connection,
                        "SELECT CAST(C_BLOB AS VARCHAR(100)) FROM T_TYPES"));
            }
            case "comments" -> {
                assertEquals(rolledBack ? "original table" : "业务 O'Brien", string(connection,
                        "SELECT COMMENTS FROM ALL_TAB_COMMENTS WHERE " + OWNER + " AND TABLE_NAME = 'T_COMMENTS'"));
                assertEquals(rolledBack ? "original column" : "字段 O'Brien",
                        columnInfo(connection, "T_COMMENTS", "ID", "REMARKS"));
                if (!rolledBack) {
                    assertEquals("added column", columnInfo(
                            connection, "T_COMMENTS", "EXTRA", "REMARKS"));
                }
            }
            case "identity" -> {
                if (!rolledBack) {
                    assertEquals(10, integer(connection, "SELECT MIN(ID) FROM T_IDENTITY"));
                    assertEquals(12, integer(connection, "SELECT MAX(ID) FROM T_IDENTITY"));
                    assertEquals(1, integer(connection,
                            "SELECT COUNT(*) FROM T_ADDED WHERE NEW_ID = 20"));
                }
            }
            case "load-data" -> {
                assertEquals(rolledBack ? 2 : 3, integer(connection,
                        "SELECT COUNT(*) FROM T_LOAD"));
                assertEquals(rolledBack ? "初始 O'Brien" : "更新 O'Brien", string(connection,
                        "SELECT NAME FROM T_LOAD WHERE ID = 1"));
                assertEquals(1, integer(connection,
                        "SELECT ACTIVE FROM T_LOAD WHERE ID = 1"));
                assertEquals(rolledBack ? "initial" : "kept", string(connection,
                        "SELECT NAME FROM T_ONLY_UPDATE WHERE ID = 1"));
                assertEquals(1, integer(connection, "SELECT COUNT(*) FROM T_ONLY_UPDATE"));
                assertEquals(rolledBack ? 1 : 2, integer(connection,
                        "SELECT COUNT(*) FROM T_COMPOSITE"));
                assertEquals(rolledBack ? "initial" : "updated", string(connection,
                        "SELECT NAME FROM T_COMPOSITE WHERE ID = 1 AND CODE = 'O''Brien'"));
                assertEquals(rolledBack ? 1 : 2, integer(connection,
                        "SELECT COUNT(*) FROM T_KEY_ONLY"));
                if (!rolledBack) {
                    assertEquals(1, integer(connection,
                            "SELECT COUNT(*) FROM T_LOAD WHERE ID = 3 AND NAME IS NULL"));
                }
            }
            case "composition" -> {
                assertEquals(rolledBack ? 0 : 1, integer(connection,
                        "SELECT COUNT(*) FROM ALL_TABLES WHERE " + OWNER + " AND TABLE_NAME = 'T_STATUS'"));
                assertEquals(rolledBack ? "Ada" : "Ada Lovelace", string(connection,
                        "SELECT " + (rolledBack ? "FIRST_NAME" : "FULL_NAME") + " FROM T_SOURCE"));
                assertEquals(0, integer(connection, "SELECT COUNT(*) FROM ALL_CONSTRAINTS WHERE "
                        + OWNER + " AND CONSTRAINT_TYPE = 'R'"));
            }
            case "cascade" -> {
                assertEquals(rolledBack ? 1 : 0, integer(connection,
                        "SELECT COUNT(*) FROM ALL_TABLES WHERE " + OWNER + " AND TABLE_NAME = 'T_CASCADE_PARENT'"));
                assertEquals(rolledBack ? 1 : 0, integer(connection,
                        "SELECT COUNT(*) FROM ALL_CONSTRAINTS WHERE " + OWNER + " AND CONSTRAINT_NAME = 'FK_CASCADE'"));
            }
            case "quoted" -> {
                assertEquals("quoted", string(connection, rolledBack
                        ? "SELECT \"select\" FROM \"Order\" WHERE \"Id\" = 1"
                        : "SELECT \"Value\" FROM \"OrderRenamed\" WHERE \"Id\" = 1"));
                assertEquals(rolledBack ? 20 : 80, Integer.parseInt(columnInfo(connection,
                        rolledBack ? "Order" : "OrderRenamed",
                        rolledBack ? "select" : "Value", "COLUMN_SIZE")));
            }
            default -> throw new AssertionError("Unknown matrix feature: " + feature);
        }
    }

    static int integer(Connection connection, String sql) throws Exception {
        return Integer.parseInt(string(connection, sql));
    }

    private static String columnInfo(Connection connection, String table, String column,
            String field) throws Exception {
        String schema = string(connection, "SELECT SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA')");
        String escape = connection.getMetaData().getSearchStringEscape();
        try (ResultSet columns = connection.getMetaData().getColumns(null,
                schema.replace("_", escape + "_"), table.replace("_", escape + "_"), column)) {
            assertTrue(columns.next(), schema + "." + table + "." + column);
            return columns.getString(field);
        }
    }

    private static String string(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next(), sql);
            return result.getString(1);
        }
    }
}
