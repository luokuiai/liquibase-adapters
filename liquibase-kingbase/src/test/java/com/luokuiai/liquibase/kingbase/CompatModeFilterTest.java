package com.luokuiai.liquibase.kingbase;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import liquibase.Scope;
import liquibase.database.core.PostgresDatabase;
import org.junit.jupiter.api.Test;

class CompatModeFilterTest {
    private final CompatModeFilter filter = new CompatModeFilter();

    @Test
    void selectsPostgresChangelogsForKingbasePostgres() throws Exception {
        Scope.child(Map.of(Scope.Attr.database.name(),
                new KingbasePostgresDatabase()), () -> {
                    assertTrue(filter.include("db/changelog/common/001.yaml"));
                    assertTrue(filter.include("db/changelog/postgresql/010.yaml"));
                    assertTrue(filter.include("db/changelog/kingbase-pg/020.yaml"));
                    assertFalse(filter.include("db/changelog/mysql/010.yaml"));
                    assertFalse(filter.include(
                            "db/changelog/kingbase-mysql/020.yaml"));
                    assertTrue(filter.include("db/changelog/oracle/010.yaml"));
                });
    }

    @Test
    void selectsMySqlChangelogsForKingbaseMySql() throws Exception {
        Scope.child(Map.of(Scope.Attr.database.name(),
                new KingbaseMySqlDatabase()), () -> {
                    assertTrue(filter.include("db/changelog/common/001.yaml"));
                    assertFalse(filter.include("db/changelog/postgresql/010.yaml"));
                    assertFalse(filter.include("db/changelog/kingbase-pg/020.yaml"));
                    assertTrue(filter.include("db/changelog/mysql/010.yaml"));
                    assertTrue(filter.include(
                            "db/changelog/kingbase-mysql/020.yaml"));
                    assertTrue(filter.include("db/changelog/sqlite/010.yaml"));
                });
    }

    @Test
    void leavesOtherDatabasesUnchanged() throws Exception {
        Scope.child(Map.of(Scope.Attr.database.name(), new PostgresDatabase()),
                () -> {
                    assertTrue(filter.include("db/changelog/postgresql/010.yaml"));
                    assertTrue(filter.include("db/changelog/mysql/010.yaml"));
                    assertTrue(filter.include(
                            "db/changelog/kingbase-mysql/020.yaml"));
                });
    }
}
