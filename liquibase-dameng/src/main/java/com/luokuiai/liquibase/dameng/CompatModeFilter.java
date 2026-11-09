package com.luokuiai.liquibase.dameng;

import liquibase.Scope;
import liquibase.changelog.IncludeAllFilter;
import liquibase.database.Database;

/** Selects shared and mode-specific changelogs for DM8. */
public class CompatModeFilter implements IncludeAllFilter {
    @Override
    public boolean include(String changeLogPath) {
        Database database = Scope.getCurrentScope().getDatabase();
        if (!(database instanceof DamengDatabase dameng)) {
            return true;
        }
        String path = "/" + changeLogPath.replace('\\', '/');
        if (path.contains("/dameng-native/")) {
            return dameng.getCompatMode() == DamengCompatMode.NATIVE;
        }
        if (path.contains("/mysql/") || path.contains("/dameng-mysql/")) {
            return dameng.getCompatMode() == DamengCompatMode.MYSQL;
        }
        if (path.contains("/postgresql/") || path.contains("/dameng-pg/")) {
            return dameng.getCompatMode() == DamengCompatMode.POSTGRES;
        }
        return true;
    }
}
