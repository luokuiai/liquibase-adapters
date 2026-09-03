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

The adapter detects KingbaseES V8 MySQL mode from `SHOW database_mode`:

```bash
liquibase \
  --classpath=liquibase-kingbase.jar:kingbase8.jar \
  --url=jdbc:kingbase8://localhost:54321/test \
  --username=system \
  --password=secret \
  --changelog-file=db.changelog.yaml \
  update
```

Set `-Dliquibase.kingbase.compatMode=mysql` to override automatic detection
when a connection does not expose `database_mode`.

You can also bypass auto-detection with Liquibase's `databaseClass` setting:

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

Explicit MySQL compatibility-mode override, only when automatic detection is unavailable:

```bash
java -Dliquibase.kingbase.compatMode=mysql -jar app.jar
```

## Changelog Guidance

For the first production version, prefer explicit SQL changesets and explicit
rollback blocks. Liquibase's structured change types are inherited from the
PostgreSQL/MySQL implementations and should be validated against your KingbaseES
compatibility mode before broad use.

When one application supports both KingbaseES compatibility modes, organize
changelogs by compatibility mode. Directories are for maintainability; the
`dbms` attribute determines which changesets Liquibase executes.

```text
src/main/resources/db/changelog/
  db.changelog-master.yaml
  common/
    001-create-user.yaml
  kingbase-pg/
    010-postgres-mode.yaml
  kingbase-mysql/
    010-mysql-mode.yaml
```

The master changelog includes every directory:

```yaml
databaseChangeLog:
  - includeAll:
      path: db/changelog/common
  - includeAll:
      path: db/changelog/kingbase-pg
  - includeAll:
      path: db/changelog/kingbase-mysql
```

Put SQL that works in both modes in `common` without a `dbms` value. For
mode-specific SQL, use `kingbase` for the default PostgreSQL-compatible mode
and `kingbase-mysql` when `liquibase.kingbase.compatMode=mysql` is set:

```yaml
databaseChangeLog:
  - changeSet:
      id: pg-010-add-index
      author: team
      dbms: kingbase
      changes:
        - sql:
            sql: create index idx_user_name on sys_user(username)
```

```yaml
databaseChangeLog:
  - changeSet:
      id: mysql-010-add-index
      author: team
      dbms: kingbase-mysql
      changes:
        - sql:
            sql: create index idx_user_name on sys_user(username)
```

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
