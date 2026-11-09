package com.luokuiai.liquibase.dameng;

import liquibase.change.Change;
import liquibase.database.Database;
import liquibase.diff.output.DiffOutputControl;
import liquibase.diff.output.changelog.ChangeGeneratorChain;
import liquibase.diff.output.changelog.core.MissingIndexChangeGenerator;
import liquibase.structure.DatabaseObject;
import liquibase.structure.core.Index;

/** Emits constraints, rather than separate changes for their generated indexes. */
public class DamengMissingIndexChangeGenerator extends MissingIndexChangeGenerator {
    @Override
    public int getPriority(Class<? extends DatabaseObject> type, Database database) {
        return database instanceof DamengDatabase && Index.class.isAssignableFrom(type)
                ? PRIORITY_DATABASE : PRIORITY_NONE;
    }

    @Override
    public Change[] fixMissing(DatabaseObject missing, DiffOutputControl control,
            Database reference, Database comparison, ChangeGeneratorChain chain) {
        Index index = (Index) missing;
        if (index.getAssociatedWith().contains(Index.MARK_PRIMARY_KEY)
                || index.getAssociatedWith().contains(Index.MARK_UNIQUE_CONSTRAINT)
                || index.getAssociatedWith().contains(Index.MARK_FOREIGN_KEY)) {
            return EMPTY_CHANGE;
        }
        return super.fixMissing(missing, control, reference, comparison, chain);
    }
}
