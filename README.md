# liquibase-adapters

Liquibase `Database` SPI adapters for databases that are not supported by
Liquibase core.

## KingbaseES

The `liquibase-kingbase` module adds KingbaseES support.

This project is intentionally thin. It lets Liquibase recognize
`jdbc:kingbase8:` URLs and `com.kingbase8.Driver`, then reuses Liquibase's
built-in PostgreSQL or MySQL database implementations for SQL dialect behavior.

## Compatibility Modes

PostgreSQL mode is the default:

```bash
liquibase \
  --classpath=liquibase-kingbase.jar:kingbase8.jar \
  --url=jdbc:kingbase8://localhost:54321/test \
  --username=system \
  --password=secret \
  --changelog-file=db.changelog.yaml \
  update
```

Set `-Dliquibase.kingbase.compatMode=mysql` before Liquibase initializes to
select MySQL compatibility mode.

You can also select the implementation explicitly with Liquibase's
`databaseClass` setting:

```bash
liquibase \
  --database-class=com.luokuiai.liquibase.kingbase.KingbaseMySqlDatabase \
  update
```

## Spring Boot

Add the adapter and the Kingbase JDBC driver to the application classpath. Use
the latest release version in production. Snapshot builds also require the
Central Portal snapshot repository.

```groovy
repositories {
    mavenCentral()
}

dependencies {
    implementation 'com.luokuiai.liquibase:liquibase-kingbase:<version>'
    runtimeOnly 'cn.com.kingbase:kingbase8:8.6.0'
}
```

Default PostgreSQL compatibility mode:

```properties
spring.datasource.driver-class-name=com.kingbase8.Driver
spring.datasource.url=jdbc:kingbase8://localhost:54321/test
spring.liquibase.change-log=classpath:/db/changelog/db.changelog-master.yaml
```

MySQL compatibility mode:

```bash
java -Dliquibase.kingbase.compatMode=mysql -jar app.jar
```

## Changelog Guidance

For the first production version, prefer explicit SQL changesets and explicit
rollback blocks. Liquibase's structured change types are inherited from the
PostgreSQL/MySQL implementations and should be validated against your KingbaseES
compatibility mode before broad use.

Both compatibility modes expose the `kingbase` short name, so
`dbms: kingbase` applies to both. When SQL differs by mode, keep one master
changelog and organize files under `common/`, shared dialect directories such
as `postgresql/` and `mysql/`, and Kingbase-only directories such as
`kingbase-pg/` and `kingbase-mysql/`. Apply the compatibility-mode filter once
on the parent directory:

```yaml
databaseChangeLog:
  - includeAll:
      path: db/changelog/changes
      filter: com.luokuiai.liquibase.kingbase.CompatModeFilter
```

The filter only changes behavior for a selected Kingbase adapter. PostgreSQL
mode includes `postgresql/` and `kingbase-pg/`; MySQL mode includes `mysql/`
and `kingbase-mysql/`. Other databases and directories are left to Liquibase's
normal `dbms` filtering. Shared dialect changesets can use
`dbms: postgresql,kingbase` or `dbms: mysql,kingbase`. No Liquibase context
configuration is required.

```yaml
databaseChangeLog:
  - changeSet:
      id: 001-create-user
      author: team
      changes:
        - sql:
            sql: |
              create table sys_user (
                id bigint primary key,
                username varchar(64) not null
              )
      rollback:
        - sql:
            sql: drop table sys_user
```

## Build

```bash
./gradlew :liquibase-kingbase:test
```

This adapter requires Liquibase 5.x and currently compiles against `5.0.3`.
To verify another Liquibase 5.x version:

```bash
./gradlew :liquibase-kingbase:test -PliquibaseVersion=5.0.3
```

KingbaseES integration tests use Testcontainers and the local
`kingbase:v8r6` image. They start isolated PostgreSQL- and MySQL-compatible
containers, run a Liquibase update, and verify rollback:

```bash
./gradlew :liquibase-kingbase:integrationTest
```

Use another image name when required:

```bash
./gradlew :liquibase-kingbase:integrationTest \
  -PkingbaseTestImage=registry/kingbase:v8r6
```

## Dameng DM8

The `liquibase-dameng` module provides a single Database SPI implementation for
DM8 compatibility modes:

| `COMPATIBLE_MODE` | Mode | Changelog directory |
| --- | --- | --- |
| `0` | DM8 native | `dameng-native/` |
| `4` | MySQL compatibility | `dameng-mysql/` |
| `7` | PostgreSQL compatibility | `dameng-pg/` |

The adapter reads the active `COMPATIBLE_MODE` from each JDBC connection.
There is no JVM-wide mode setting. Connections using different modes can
coexist in one process. Unsupported modes, non-DM8 database versions, and
failed parameter queries are rejected rather than assumed to be native mode.
The adapter never changes the server's compatibility parameters.

All three modes use `dbms: dameng`. Their implementation shares DM8 JDBC
metadata and SQL behavior, without inheriting PostgreSQL or MySQL system-catalog
queries. Mode names do not imply complete SQL compatibility with those databases.

### Connecting

Add the adapter and a separately supplied DM JDBC driver:

```groovy
dependencies {
    implementation 'com.luokuiai.liquibase:liquibase-dameng:<version>'
    runtimeOnly 'com.dameng:DmJdbcDriver18:8.1.3.140'
}
```

```properties
spring.datasource.driver-class-name=dm.jdbc.driver.DmDriver
spring.datasource.url=jdbc:dm://localhost:5236
spring.datasource.username=APP
spring.datasource.password=${DB_PASSWORD}
spring.liquibase.change-log=classpath:/db/changelog/db.changelog-master.yaml
```

For CLI use, put both JARs on Liquibase's classpath. The implementation is
selected automatically; an explicit `databaseClass`, if needed, is
`com.luokuiai.liquibase.dameng.DamengDatabase`.

### Changelogs and modes

The application controls which changelogs to load through its master changelog,
`include`, `dbms`, contexts, or labels. A shared `common/` directory can contain
portable changes for Kingbase, Dameng, and other databases; it is not specific
to a Dameng compatibility mode.

For applications that organize SQL by compatibility-mode directories, optionally
apply the Dameng filter once at the common parent:

```yaml
databaseChangeLog:
  - includeAll:
      path: db/changelog/changes
      filter: com.luokuiai.liquibase.dameng.CompatModeFilter
```

The filter includes `common/` and other ordinary directories in every mode.
Native mode includes `dameng-native/`; MySQL mode includes `mysql/` and
`dameng-mysql/`; PostgreSQL mode includes `postgresql/` and `dameng-pg/`.
The filter only selects compatibility-mode directories for a connected Dameng
adapter. Other directories, including Kingbase-specific directories, are left
to Liquibase's normal `dbms` filtering. Mark Kingbase-only changesets with
`dbms: kingbase` and Dameng-only changesets with `dbms: dameng`; directory names
alone do not isolate databases. Other databases are unaffected by this filter.

Use `dbms: dameng` for DM8-only changesets, or `dbms: mysql,dameng` /
`dbms: postgresql,dameng` for shared dialect SQL that has been verified on DM8.
Keep explicit rollback blocks for SQL changesets.

### Validation and scope

The adapter targets Liquibase 5.x (tested with 5.0.3) and Java 17. Integration tests use
`cnxc/dm8:20251114-kylin` (server `8.1.4.170`), a third-party DM8 image, with the
Maven Central driver `com.dameng:DmJdbcDriver18:8.1.3.140`.
Each test initializes a fresh database, sets the mode before startup, and uses
a separate application schema.
The image must contain `/opt/dmdbms/bin/dminit`, `dmserver`, and the `dmdba` user.

```bash
./gradlew :liquibase-dameng:test
./gradlew :liquibase-dameng:integrationTest
```

To select another image with the same layout:

```bash
./gradlew :liquibase-dameng:integrationTest \
  -PdamengTestImage=registry/dm8:tested-version
```

The integration suite covers modes 0, 4, and 7 with `CASE_SENSITIVE=1` and the
default `DDL_AUTO_COMMIT=1`: automatic selection, history and lock tables,
repeated update, structured table/index/view/sequence creation, boolean values,
identity columns, JDBC metadata, preconditions, snapshots of tables, columns,
primary keys, unique constraints, indexes, views, and sequences, lock contention,
mode-specific includes, and explicit rollback.

Identifier folding comes from JDBC metadata, and DDL transaction behavior is
read from the active `DDL_AUTO_COMMIT` parameter. Other case-sensitivity,
identifier, and DDL configurations require separate integration validation.
Support for additional DM versions, driver versions, compatibility modes,
advanced change types, and full schema diff is not implied by these tests.
