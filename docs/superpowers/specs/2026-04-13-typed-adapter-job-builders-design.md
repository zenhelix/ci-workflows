# Typed Adapter Job Builders

**Date:** 2026-04-13
**Status:** Draft
**Branch:** feature/kts-script

## Problem

The adapter layer's `ReusableWorkflowJobBuilder` is untyped: any `WorkflowInput` from any workflow can be invoked in any builder context. Required inputs can be silently omitted. Strategy uses `Map<String, Any>` with unsafe casts. Secrets require manual synchronization between adapter-level registration and job-level passthrough.

Specific issues:
- `WorkflowInput.invoke(value)` is an extension on the builder — no compile-time check that the input belongs to the target workflow
- `strategy(mapOf("java-version" to expr))` uses `Map<String, Any>`, leading to `@Suppress("UNCHECKED_CAST")` in `toJobYaml()`
- `SetupTool.toParamsJson()` builds JSON via string interpolation instead of serialization
- `init { secrets(X) }` + `secrets(X.passthrough())` are two manual sync points for the same data

## Goals

1. Each workflow object provides its own typed `JobBuilder` — inputs of `CheckWorkflow` are methods on `CheckWorkflow.JobBuilder`, not callable from `PublishWorkflow.JobBuilder`
2. Runtime validation of required inputs at `build()` time
3. Replace `Map<String, Any>` in strategy with typed `MatrixDef`
4. Replace string-interpolated JSON in `SetupTool` with `kotlinx.serialization.json`
5. Automate secrets collection from jobs — single source of truth, no manual passthrough
6. Generated YAML output must not change

## Design

### Typed Job Builder per Workflow

Each workflow object in `Workflows.kt` defines a nested `JobBuilder` class:

```kotlin
object CheckWorkflow : ReusableWorkflow("check.yml") {
    val setupAction = input("setup-action", ..., required = true)
    val setupParams = input("setup-params", ..., default = "{}")
    val checkCommand = input("check-command", ..., required = true)

    class JobBuilder : ReusableWorkflowJobBuilder(CheckWorkflow) {
        fun setupAction(value: String) = set(CheckWorkflow.setupAction, value)
        fun setupParams(value: String) = set(CheckWorkflow.setupParams, value)
        fun checkCommand(value: String) = set(CheckWorkflow.checkCommand, value)
    }
}
```

The base `ReusableWorkflowJobBuilder` provides:
- `set(input: WorkflowInput, value: String)` — protected method that populates `withMap`
- `strategy(matrix: MatrixDef)` — typed matrix strategy
- `passthroughSecrets(vararg secrets: WorkflowSecret)` — selective secret passthrough
- `passthroughAllSecrets()` — passthrough all secrets of the target workflow
- `secret(secret: WorkflowSecret)` — register a single secret passthrough
- `build(id: String)` — validates all required inputs are set, throws `IllegalStateException` if not

The `reusableJob` function becomes generic to infer the correct builder type:

```kotlin
fun <B : ReusableWorkflowJobBuilder> reusableJob(
    id: String,
    uses: ReusableWorkflow,  // workflow object carries builder factory
    block: B.() -> Unit = {},
): ReusableWorkflowJobDef
```

Each workflow object provides a `createJobBuilder()` factory method that `reusableJob` calls internally.

### Adapter Usage (after)

```kotlin
object GradleCheckAdapter(...) : AdapterWorkflow(...) {
    val javaVersion = input(...)
    val gradleCommand = input(...)

    private val javaVersionMatrix = MatrixRef("java-version")

    override fun jobs() = listOf(
        reusableJob(id = "conventional-commit", uses = ConventionalCommitCheckWorkflow),
        reusableJob(id = "check", uses = CheckWorkflow) {
            strategy(MatrixDef(mapOf(javaVersionMatrix.key to JAVA_VERSION_MATRIX_EXPR)))
            setupAction(SetupTool.Gradle.id)
            setupParams(SetupTool.Gradle.toParamsJson(javaVersionMatrix.ref))
            checkCommand(gradleCommand.ref)
        },
    )
}
```

### MatrixDef and MatrixRef

New file `dsl/MatrixDef.kt`:

```kotlin
class MatrixRef(val key: String) {
    val ref: String get() = "${'$'}{{ matrix.$key }}"
}

data class MatrixDef(val entries: Map<String, String>)
```

Replaces `Map<String, Any>` in `ReusableWorkflowJobDef.strategy` and removes the `@Suppress("UNCHECKED_CAST")` in `toJobYaml()`.

### SetupTool JSON Serialization

Replace string interpolation with `kotlinx.serialization.json`:

```kotlin
sealed class SetupTool(val actionName: String, val versionKey: String, ...) {
    fun toParamsJson(versionExpr: String): String =
        Json.encodeToString(mapOf(versionKey to versionExpr))
}
```

One method in the base class instead of overrides in each subclass. `Gradle`, `Go`, `Python` become simple `data object` declarations without `override fun toParamsJson`.

### Secrets Auto-Collection

`AdapterWorkflow.generate()` collects secrets from all jobs automatically:

```kotlin
// In AdapterWorkflow.generate():
val allJobSecrets = jobs().flatMap { it.secrets.keys }.toSet()
// These become the workflow_call secrets in the generated YAML
```

The adapter no longer needs `init { secrets(X) }`. Secrets are declared once — in the job builder via `passthroughSecrets()` or `passthroughAllSecrets()`.

The `passthrough()` extension function in `Secrets.kt` is deleted.

The `MAVEN_SONATYPE_SECRETS`, `GRADLE_PORTAL_SECRETS`, `APP_SECRETS` maps in `Secrets.kt` are deleted — secrets are already declared as typed properties on workflow objects (e.g. `PublishWorkflow.mavenSonatypeUsername`, `CreateTagWorkflow.appId`). The `Secrets.kt` file is removed entirely.

## Files Changed

| File | Change |
|------|--------|
| `dsl/ReusableWorkflowJobBuilder.kt` | Refactor base class: add `set()`, `strategy(MatrixDef)`, `passthroughSecrets()`, `passthroughAllSecrets()`, required-input validation in `build()` |
| `dsl/MatrixDef.kt` | **New** — `MatrixDef`, `MatrixRef` |
| `dsl/ReusableWorkflow.kt` | Add `createJobBuilder()` factory, `requiredInputs` property |
| `dsl/Workflows.kt` | Add nested `JobBuilder` class to each workflow object |
| `dsl/AdapterWorkflow.kt` | Auto-collect secrets from jobs in `generate()` |
| `dsl/yaml/AdapterWorkflowYaml.kt` | Remove `Map<String, Any>` path from `StrategyYaml` |
| `config/SetupTool.kt` | `toParamsJson()` via `Json.encodeToString`, remove per-subclass overrides |
| `config/Secrets.kt` | Delete `passthrough()`, simplify or remove secret maps |
| `workflows/adapters/**/*.kt` | All 9 adapters — migrate to typed builders |

## Files NOT Changed

- `workflows/base/*.kt` — base workflows use `github-workflows-kt` directly
- `dsl/WorkflowHelpers.kt` — used by base workflows only
- `generate/Generate.kt` — `generate()` calls unchanged
- `actions/*.kt` — action wrappers unaffected

## Implementation Order

1. DSL infrastructure — `MatrixDef`, `MatrixRef`, refactor `ReusableWorkflowJobBuilder`
2. Typed builders — nested `JobBuilder` in each workflow object
3. Secrets auto-collection — refactor `AdapterWorkflow.generate()`, delete `passthrough()`
4. SetupTool — `Json.encodeToString` replacement
5. Migrate all 9 adapters to typed builders
6. Verification — `gradle run` + diff generated YAML (must be identical)

## Verification

Generated YAML must not change. Verification:

```bash
# Before refactoring — save baseline
cp -r .github/workflows .github/workflows-baseline

# After refactoring
gradle run
diff -r .github/workflows-baseline .github/workflows
# Expected: no differences
```
