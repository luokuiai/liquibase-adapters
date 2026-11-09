package com.luokuiai.liquibase.dameng;

import java.util.Locale;

import liquibase.database.Database;
import liquibase.datatype.DataTypeInfo;
import liquibase.datatype.DatabaseDataType;
import liquibase.datatype.LiquibaseDataType;
import liquibase.datatype.core.BlobType;

/** Keeps fixed and varying binary columns distinct from large objects. */
@DataTypeInfo(name = "blob",
        aliases = {"longblob", "longvarbinary", "java.sql.Types.BLOB", "java.sql.Types.LONGBLOB",
                "java.sql.Types.LONGVARBINARY", "java.sql.Types.VARBINARY", "java.sql.Types.BINARY",
                "varbinary", "binary", "image", "tinyblob", "mediumblob", "long binary",
                "long varbinary"},
        minParameters = 0, maxParameters = 1, priority = LiquibaseDataType.PRIORITY_DATABASE)
public class DamengBlobType extends BlobType {
    @Override
    public boolean supports(Database database) {
        return database instanceof DamengDatabase;
    }

    @Override
    public DatabaseDataType toDatabaseDataType(Database database) {
        String raw = getRawDefinition() == null ? "" : getRawDefinition().trim()
                .toLowerCase(Locale.ROOT);
        raw = raw.split("\\(", 2)[0].trim();
        if (raw.startsWith("varbinary") || raw.equals("java.sql.types.varbinary")) {
            return new DatabaseDataType("VARBINARY", getParameters());
        }
        if (raw.startsWith("binary") || raw.equals("java.sql.types.binary")) {
            return new DatabaseDataType("BINARY", getParameters());
        }
        return new DatabaseDataType("BLOB");
    }
}
