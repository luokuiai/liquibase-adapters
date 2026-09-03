# Repository Guidelines

## Scope

- This repository contains thin Liquibase SPI adapters. Keep database-specific
  behavior in adapter modules; do not fork or modify Liquibase core.
- Preserve the distinction between SQL dialect behavior and physical metadata
  behavior. A compatibility mode may reuse a Liquibase dialect while requiring
  dedicated metadata snapshot support.
- Keep support scoped to the documented KingbaseES V8 compatibility modes.
  Do not claim support for an untested database version, driver, or mode.

## Development

- Use Java 17 and follow the existing package, naming, and formatting style.
- Prefer Liquibase extension points and service-provider registrations over
  reflection, patches to third-party libraries, or application-side workarounds.
- Add a focused test for every behavior change. Use unit tests for adapter
  selection and SPI registration, and V8 Testcontainers integration tests for
  JDBC metadata, update, rollback, and snapshot behavior.
- Keep changelog fixtures minimal and include explicit rollback where relevant.
- Avoid unrelated refactors and do not commit local tooling directories such as
  `.codex/`, build output, credentials, or generated artifacts.

## Verification

- Run `./gradlew :liquibase-kingbase:test` for unit changes.
- Run `./gradlew :liquibase-kingbase:integrationTest` when changing database
  detection, SQL generation, JDBC metadata, snapshots, or changelog behavior.
- Before committing, run `git diff --check` and review the staged diff.

## Git

- Create a focused branch from the intended base branch. Use concise
  Conventional Commit messages, for example `fix(kingbase): ...`.
- Keep commits coherent. Squash exploratory or redundant commits before opening
  a pull request.
- Never include local-only files or unrelated changes in a commit.

## Release and Tagging

This repository publishes snapshots from `develop` and releases from annotated
tags through `.github/workflows/publish.yml`. Follow this process exactly so the
branches, tag, and Maven Central publication stay aligned.

### Release Rules

- Start a release only after the user explicitly provides the target version.
- Treat version changes, commits, merges, tags, pushes, and branch deletion as
  separate operations. Do not perform a later operation without user approval.
- Start release branches from an up-to-date `develop` branch with a clean
  worktree.
- Use stable SemVer versions in `X.Y.Z` form. The current workflow does not
  accept prerelease tags.
- Use an annotated tag named `v<VERSION>`. Never use a lightweight tag.
- Never move, replace, or force-push a published tag.
- Push only the release tag, not every local tag.
- The workflow publishes `liquibase-kingbase` to Maven Central; it does not
  create a GitHub Release.

The examples below use `1.0.0`. Replace it consistently for each release.

### 1. Create the Release Branch

```bash
git switch develop
git pull --ff-only origin develop
git switch -c release-1.0.0
```

The branch must be named `release-<VERSION>`.

### 2. Prepare and Verify the Release

The repository does not store a release version in a manifest. The workflow
derives `1.0.0` from the `v1.0.0` tag and passes it to Gradle through
`-PreleaseVersion`. Do not replace the default snapshot version in
`build.gradle` during release preparation.

Run the full release gate on the release branch:

```bash
./gradlew :liquibase-kingbase:clean :liquibase-kingbase:test
./gradlew :liquibase-kingbase:integrationTest
./gradlew properties -q -PreleaseVersion=1.0.0 | grep '^version: 1.0.0$'
git diff --check
```

### 3. Commit Release Preparation Changes

Commit only changes required to make the release pass, such as release notes,
publication metadata, or focused fixes. Do not create an empty version-bump
commit. Use a concise Conventional Commit message appropriate to the change.

Do not push the release branch unless the user explicitly requests it. If the
release branch needs no additional changes, continue only after the user
approves merging and tagging.

### 4. Merge and Tag Main

```bash
git switch main
git pull --ff-only origin main
git merge --no-ff release-1.0.0 \
  -m "Merge branch 'release-1.0.0' into main"
git tag -a v1.0.0 -m "v1.0.0"
```

Before continuing, verify that the tag points to the merge commit and that the
tag-derived Gradle version is correct:

```bash
git show --no-patch --decorate v1.0.0
./gradlew properties -q -PreleaseVersion=1.0.0 | grep '^version: 1.0.0$'
```

### 5. Merge Back to Develop

```bash
git switch develop
git pull --ff-only origin develop
git merge --no-ff release-1.0.0 \
  -m "Merge branch 'release-1.0.0' into develop"
```

### 6. Push in Release Order

Push `main` first, then the exact tag to trigger the release, and finally
`develop`:

```bash
git push origin main
git push origin v1.0.0
git push origin develop
```

Do not use `git push --tags`.

### 7. Verify Maven Central Publishing

The tag triggers `.github/workflows/publish.yml`. The workflow validates the tag
format, derives the publication version, runs unit tests, and uploads the
deployment to Maven Central. It does not publish the deployment automatically.

```bash
gh run list --workflow publish.yml --limit 3
gh run watch <RUN_ID> --exit-status
```

After the workflow succeeds, open the deployment in Maven Central Portal,
verify its coordinates and validation status, and click `Publish`. The release
is complete only after Maven Central reports the deployment as published. If
uploading fails, do not move or recreate the tag. Rerun the failed workflow for
transient failures. Use a new version and tag if code or publication metadata
must change.

### 8. Delete the Release Branch

Delete the local release branch only after `main`, the tag, and `develop` are
pushed and the publishing workflow succeeds:

```bash
git switch develop
git branch -d release-1.0.0
```

If the release branch was explicitly pushed, delete it separately:

```bash
git push origin --delete release-1.0.0
```

### Snapshot Versions

Each push to `develop` publishes a unique snapshot. The workflow derives the
version from the commit SHA and publishes:

```text
1.0.0-<commit-sha-7>-SNAPSHOT
```

For example, commit `d375f4f` publishes `1.0.0-d375f4f-SNAPSHOT`.

## Pull Requests

- Use `.github/pull_request_template.md` exactly. PR descriptions contain only
  `## Summary` and `## Changes`.
- Write the PR title, summary, and changes in English.
- The summary is one short paragraph covering the goal, outcome, and reviewer
  impact. List 3-7 concrete, reviewable changes under `Changes`.
- Open dependent pull requests against their feature branch; open independent
  pull requests against `develop`.
