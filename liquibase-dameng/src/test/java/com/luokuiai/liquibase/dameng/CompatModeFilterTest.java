package com.luokuiai.liquibase.dameng;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import liquibase.Scope;
import liquibase.database.core.PostgresDatabase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CompatModeFilterTest {
    private final CompatModeFilter filter = new CompatModeFilter();

    @ParameterizedTest
    @EnumSource(DamengCompatMode.class)
    void includesOnlyTheSelectedDialectAndDmMode(DamengCompatMode mode) throws Exception {
        DamengDatabase database = new DamengDatabase();
        database.setConnection(DamengTestConnections.connection(mode.getValue()));
        Scope.child(Map.of(Scope.Attr.database.name(), database), () -> {
            assertTrue(filter.include("db/changelog/common/001.xml"));
            assertTrue(filter.include("db/changelog/shared/001.xml"));
            assertEquals(mode == DamengCompatMode.NATIVE,
                    filter.include("db/changelog/dameng-native/001.xml"));
            assertEquals(mode == DamengCompatMode.MYSQL,
                    filter.include("db/changelog/mysql/001.xml"));
            assertEquals(mode == DamengCompatMode.MYSQL,
                    filter.include("db/changelog/dameng-mysql/001.xml"));
            assertEquals(mode == DamengCompatMode.POSTGRES,
                    filter.include("db/changelog/postgresql/001.xml"));
            assertEquals(mode == DamengCompatMode.POSTGRES,
                    filter.include("db\\changelog\\dameng-pg\\001.xml"));
        });
    }

    @Test
    void leavesOtherDatabasesAndAnAbsentScopeUnchanged() throws Exception {
        assertTrue(filter.include("db/changelog/dameng-native/001.xml"));
        Scope.child(Map.of(Scope.Attr.database.name(), new PostgresDatabase()), () -> {
            assertTrue(filter.include("db/changelog/dameng-native/001.xml"));
            assertTrue(filter.include("db/changelog/mysql/001.xml"));
            assertTrue(filter.include("db/changelog/dameng-pg/001.xml"));
        });
    }
}
