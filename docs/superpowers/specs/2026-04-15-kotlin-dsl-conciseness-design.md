# Kotlin DSL Conciseness & Type Safety Improvements

**Date:** 2026-04-15
**Status:** Approved
**Scope:** workflow-dsl module + base workflow objects

## Problem Statement

The codebase has gone through significant refactoring (20+ commits), but several patterns remain that add unnecessary verbosity or rely on runtime checks instead of compile-time guarantees:

1. `InputType.String` enum value conflicts with `kotlin.String`, forcing `kotlin.String` qualification in 5 places
2. `SetupAwareJobBuilder` uses `as?` runtime cast + `error()` instead of generic constraint
3. Every base workflow declares a custom `JobBuilder` class with `refInput` delegated properties that are never used (adapters use `from` infix exclusively)
4. Three `setup()` overloads in `SetupSteps.kt` that all call the same `applySetup()` with a string

## Changes

### 1. Rename `InputType.String` to `InputType.Text`

**Files:** `WorkflowInput.kt`, `InputRegistry.kt`

Rename the enum value to eliminate the naming conflict with `kotlin.String`. Add explicit `yamlName()` mapping for `Text -> "string"` to preserve YAML output.

Remove all `kotlin.String` qualifications from `WorkflowInputDef` and `InputType.yamlName()`.

### 2. Type-safe `SetupAwareJobBuilder<W>` with generic constraint

**Files:** `ReusableWorkflowJobBuilder.kt`

Change `ReusableWorkflowJobBuilder` from `abstract` to `open`.

Replace:
```kotlin
abstract class SetupAwareJobBuilder(workflow: ReusableWorkflow) :
    ReusableWorkflowJobBuilder(workflow), SetupCapableJobBuilder {
    private val capability = (workflow as? SetupCapability)
        ?: error("${workflow.fileName} must implement SetupCapability")
    override var setupAction by stringInput(capability.setupAction)
    override var setupParams by stringInput(capability.setupParams)
}
```

With:
```kotlin
class SetupAwareJobBuilder<W>(workflow: W) :
    ReusableWorkflowJobBuilder(workflow), SetupCapableJobBuilder
    where W : ReusableWorkflow, W : SetupCapability {
    override var setupAction by stringInput(workflow.setupAction)
    override var setupParams by stringInput(workflow.setupParams)
}
```

### 3. Eliminate custom JobBuilder classes from base workflows

**Files:** All 8 base workflow objects (`CheckWorkflow`, `CreateTagWorkflow`, `ManualCreateTagWorkflow`, `PublishWorkflow`, `AppDeployWorkflow`, `ReleaseWorkflow`, `ConventionalCommitCheckWorkflow`, `LabelerWorkflow`)

Remove nested `JobBuilder` classes and their `refInput` property delegations.

For setup-aware workflows, replace:
```kotlin
class JobBuilder : SetupAwareJobBuilder(CheckWorkflow) {
    var checkCommand by refInput(CheckWorkflow.checkCommand)
}
context(builder: AdapterWorkflowBuilder)
fun job(id: String, block: JobBuilder.() -> Unit = {}) = job(id, ::JobBuilder, block)
```

With:
```kotlin
context(builder: AdapterWorkflowBuilder)
fun job(id: String, block: SetupAwareJobBuilder<CheckWorkflow>.() -> Unit = {}) =
    job(id, { SetupAwareJobBuilder(this@CheckWorkflow) }, block)
```

For non-setup workflows, replace:
```kotlin
class JobBuilder : ReusableWorkflowJobBuilder(ReleaseWorkflow) {
    var changelogConfig by refInput(ReleaseWorkflow.changelogConfig)
    var draft by refInput(ReleaseWorkflow.draft)
}
context(builder: AdapterWorkflowBuilder)
fun job(id: String, block: JobBuilder.() -> Unit = {}) = job(id, ::JobBuilder, block)
```

With:
```kotlin
context(builder: AdapterWorkflowBuilder)
fun job(id: String, block: ReusableWorkflowJobBuilder.() -> Unit = {}) =
    job(id, { ReusableWorkflowJobBuilder(this@ReleaseWorkflow) }, block)
```

### 4. Remove `refInput()` from `InputProperty.kt`

**Files:** `InputProperty.kt`

Delete the `refInput()` factory function. Keep `stringInput()` (still used by `SetupAwareJobBuilder`).

### 5. Consolidate `setup()` overloads to single method

**Files:** `SetupSteps.kt`, adapter files

Replace three overloads:
```kotlin
fun SetupCapableJobBuilder.setup(tool: SetupTool, versionExpr: MatrixRefExpr)
fun SetupCapableJobBuilder.setup(tool: SetupTool, versionRef: String)
fun SetupCapableJobBuilder.setup(tool: SetupTool, versionInput: WorkflowInput)
```

With one:
```kotlin
fun SetupCapableJobBuilder.setup(tool: SetupTool, versionExpr: String) {
    applySetup(tool.id, tool.toParamsJson(versionExpr))
}
```

Update call sites to extract `.expression` / `.ref.expression` explicitly.

## What We Do NOT Change

- Mutable builders (standard pattern for Kotlin DSL)
- `InputRegistry` overloads (provide type safety at call site for String vs Boolean defaults)
- Custom serializers in `AdapterWorkflowYaml.kt` (required for correct YAML format)
- `from` infix API (works well, no changes needed)
- `GITHUB_TOKEN` env maps (only 2 places, not worth abstracting)

## Verification

- `./gradlew build` must compile successfully
- `./gradlew run` must generate identical YAML output (no diff in `.github/workflows/`)
- The YAML output is the contract; any diff means a regression
