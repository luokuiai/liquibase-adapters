package com.luokuiai.liquibase.kingbase;

import liquibase.Scope;
import liquibase.changelog.IncludeAllFilter;
import liquibase.database.Database;

/** Selects mode-specific changelogs from an {@code includeAll} tree. */
public class CompatModeFilter implements IncludeAllFilter {
    @Override
    public boolean include(String changeLogPath) {
        String path = "/" + changeLogPath.replace('\\', '/');
        Database database = Scope.getCurrentScope().getDatabase();
        if (database instanceof KingbasePostgresDatabase) {
            return !isMySqlPath(path);
        }
        if (database instanceof KingbaseMySqlDatabase) {
            return !isPostgresPath(path);
        }
        return true;
    }

    private boolean isPostgresPath(String path) {
        return path.contains("/postgresql/")
                || path.contains("/kingbase-pg/");
    }

    private boolean isMySqlPath(String path) {
        return path.contains("/mysql/")
                || path.contains("/kingbase-mysql/");
    }
}
