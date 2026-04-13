# Migration to github-workflows-kt

## Goal

Replace hand-written YAML workflow definitions with Kotlin DSL (github-workflows-kt) for type-safety and IDE autocomplete. Composite actions remain as YAML.

## Decisions

| Decision | Choice |
|----------|--------|
| Scope | Full migration — all 20 workflows |
| Source location | `.github/workflow-src/` (separate from generated YAML) |
| YAML generation | Standalone `.main.kts` scripts (no Gradle) |
| Consistency | Local `generate.sh` + CI `verify-workflows.yml` |
| Composite actions | Stay as YAML (4 actions unchanged) |

## Project Structure

```
.github/
  workflow-src/                         # Kotlin source
    _shared.main.kts                    # shared helpers, constants, enums
    check.main.kts
    create-tag.main.kts
    manual-create-tag.main.kts
    release.main.kts
    publish.main.kts
    conventional-commit-check.main.kts
    labeler.main.kts
    app-check.main.kts
    app-release.main.kts
    app-deploy.main.kts
    gradle-create-tag.main.kts
    gradle-manual-create-tag.main.kts
    gradle-plugin-check.main.kts
    gradle-plugin-release.main.kts
    kotlin-library-check.main.kts
    kotlin-library-release.main.kts
    go-create-tag.main.kts
    go-manual-create-tag.main.kts
    generate.sh                         # runs all .main.kts, moves YAML to ../workflows/
  workflows/                            # generated YAML (git-tracked)
    check.yml
    create-tag.yml
    manual-create-tag.yml
    release.yml
    publish.yml
    conventional-commit-check.yml
    labeler.yml
    app-check.yml
    app-release.yml
    app-deploy.yml
    gradle-create-tag.yml
    gradle-manual-create-tag.yml
    gradle-plugin-check.yml
    gradle-plugin-release.yml
    kotlin-library-check.yml
    kotlin-library-release.yml
    go-create-tag.yml
    go-manual-create-tag.yml
    verify-workflows.yml                # hand-written CI (bootstrap)
  actions/                              # unchanged
    setup-gradle/action.yml
    setup-go/action.yml
    setup-python/action.yml
    create-app-token/action.yml
  labeler.yml                           # unchanged
```

## Generation Pipeline

### generate.sh

Iterates over all `.main.kts` files in `workflow-src/` (skipping `_shared`), executes each with `kotlin`, and moves generated YAML to `../workflows/`.

### verify-workflows.yml (hand-written YAML)

Runs on PRs and pushes to main when `.github/workflow-src/**` or `.github/workflows/**` change. Steps:

1. Checkout
2. Setup JDK 17 (Temurin)
3. Run `generate.sh`
4. `git diff --exit-code .github/workflows/` — fails if YAML is out of sync

## Kotlin DSL Structure

### _shared.main.kts

Contains:
- `SetupAction` enum (Gradle, Go, Python) — replaces raw strings
- Default constants: `DEFAULT_JAVA_VERSION`, `DEFAULT_GO_VERSION`, `DEFAULT_PYTHON_VERSION`, `DEFAULT_RELEASE_BRANCHES`
- Helper functions for conditional setup steps, app token creation

### Base workflows (6)

`check`, `create-tag`, `manual-create-tag`, `release`, `publish`, `conventional-commit-check` — language-agnostic, accept `setup-action` input, use helpers from `_shared`.

### Adapter workflows (12)

`gradle-*`, `kotlin-*`, `go-*`, `app-*` — thin wrappers calling base workflows with language-specific defaults.

### Utility workflows (1)

`labeler` — standalone, wraps `actions/labeler@v6`.

## Dependencies

### Core library

```kotlin
@file:Repository("https://repo1.maven.org/maven2/")
@file:DependsOn("io.github.typesafegithub:github-workflows-kt:LATEST_VERSION")
```

### Action bindings

```kotlin
@file:Repository("https://bindings.krzeminski.it/")
@file:DependsOn("actions:checkout:v6")
@file:DependsOn("actions:setup-java:v5")
@file:DependsOn("actions:setup-go:v5")
@file:DependsOn("actions:setup-python:v5")
@file:DependsOn("gradle:actions__setup-gradle:v5")
@file:DependsOn("actions:create-github-app-token:v2")
@file:DependsOn("actions:labeler:v6")
@file:DependsOn("mathieudutour:github-tag-action:v6.2")
@file:DependsOn("mikepenz:release-changelog-builder-action:v6")
@file:DependsOn("softprops:action-gh-release:v2")
```

For actions without typed bindings: use `_customAction` as fallback.

### Shared code import

```kotlin
@file:Import("_shared.main.kts")
```

## Migration Order

1. **Infrastructure** — `workflow-src/`, `_shared.main.kts`, `generate.sh`
2. **Base workflows** (6) — `check`, `create-tag`, `manual-create-tag`, `release`, `publish`, `conventional-commit-check`
3. **Adapter workflows** (12) — `gradle-*`, `kotlin-*`, `go-*`, `app-*`
4. **Utility** (1) — `labeler`
5. **CI bootstrap** — `verify-workflows.yml`
6. **Validation** — run `generate.sh`, verify generated YAML is functionally identical

## Success Criteria

Generated YAML must be **functionally identical** to current YAML.

Acceptable differences:
- Formatting (whitespace, quoting, key order)
- Comments (github-workflows-kt does not generate YAML comments)

Unacceptable:
- Logic changes (conditions, steps, inputs/outputs)
- Missing secrets or inputs
- Changed job/step names (breaks `needs:` in consuming repos)

## Out of Scope

- Composite actions (`.github/actions/`) — remain YAML
- Labeler config (`.github/labeler.yml`) — remains YAML
- Consuming repositories — no changes needed
