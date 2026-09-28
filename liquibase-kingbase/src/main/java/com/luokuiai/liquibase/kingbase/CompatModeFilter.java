package com.luokuiai.liquibase.kingbase;

import liquibase.changelog.IncludeAllFilter;

/** Selects mode-specific changelogs from an {@code includeAll} tree. */
public class CompatModeFilter implements IncludeAllFilter {
    @Override
    public boolean include(String changeLogPath) {
        String path = "/" + changeLogPath.replace('\\', '/');
        if (path.contains("/kingbase-pg/")) {
            return KingbaseSupport.isPostgresMode();
        }
        if (path.contains("/kingbase-mysql/")) {
            return KingbaseSupport.isMySqlMode();
        }
        return true;
    }
}
