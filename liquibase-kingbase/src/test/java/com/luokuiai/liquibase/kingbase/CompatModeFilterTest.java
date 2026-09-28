package com.luokuiai.liquibase.kingbase;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CompatModeFilterTest {
    private final CompatModeFilter filter = new CompatModeFilter();

    @AfterEach
    void clearCompatibilityMode() {
        System.clearProperty(KingbaseSupport.COMPAT_MODE_PROPERTY);
    }

    @Test
    void includesCommonAndPostgresChangelogsByDefault() {
        assertTrue(filter.include("db/changelog/common/001.yaml"));
        assertTrue(filter.include("db/changelog/kingbase-pg/010.yaml"));
        assertFalse(filter.include("db/changelog/kingbase-mysql/010.yaml"));
    }

    @Test
    void includesCommonAndMySqlChangelogsInMySqlMode() {
        System.setProperty(KingbaseSupport.COMPAT_MODE_PROPERTY, "mysql");

        assertTrue(filter.include("db/changelog/common/001.yaml"));
        assertFalse(filter.include("db/changelog/kingbase-pg/010.yaml"));
        assertTrue(filter.include("db/changelog/kingbase-mysql/010.yaml"));
    }
}
