# Kotlin DSL Refactoring Design

**Date:** 2026-04-15
**Scope:** workflow-dsl module + src/main/kotlin/ (33 Kotlin files across 2 modules)
**Approach:** Structural refactoring (Approach B) — significant code reduction with ecosystem extensibility

## Context

The ci-workflows project generates GitHub Actions YAML workflows from Kotlin using a type-safe DSL. The codebase has accumulated several repetitive patterns and workarounds that increase the cost of adding new ecosystem support (Python, Node, Rust). This refactoring aims to make the code more concise, eliminate duplication, and prepare for ecosystem expansion.

### Constraints

- Kotlin 2.3.20 with `-Xcontext-parameters`
- No KSP or annotation processing
- Dual serialization path stays (github-workflows-kt for base workflows, kaml for adapters) — this is forced by upstream library limitations
- Generated YAML can change (no backward-compatibility requirement)

---

## 1. Unified Input Type

### Problem

Boolean inputs require a separate `booleanInput()` method and a parallel `_booleanDefaults: Map<String, Boolean>` map. This split propagates through 4 locations:
- `InputRegistry` (two maps, two methods)
- `ReusableWorkflow.inputsAsRawMap()` (checks `booleanDefaults` first)
- `ReusableWorkflow.toWorkflowCallTrigger()` (branches on `booleanDefaults.isEmpty()`)
- `InputsYamlMapper.toInputsYaml()` (checks `booleanDefaults` for each input)

### Solution

Replace `WorkflowCall.Input` + `_booleanDefaults` with a single `WorkflowInputDef`:

```kotlin
sealed interface InputDefault {
    data class StringDefault(val value: String) : InputDefault
    data class BooleanDefault(val value: Boolean) : InputDefault
}

// Thin wrapper over WorkflowCall.Type to avoid leaking upstream types
enum class InputType { String, Boolean, Number, Choice }

data class WorkflowInputDef(
    val name: String,
    val description: String,
    val type: InputType = InputType.String,
    val required: Boolean = false,
    val default: InputDefault? = null,
)
```

`InputRegistry` stores a single `LinkedHashMap<String, WorkflowInputDef>`. Two overloaded `input()` methods:

```kotlin
fun input(name: String, description: String, required: Boolean = false, default: String? = null): WorkflowInput
fun input(name: String, description: String, required: Boolean = false, default: Boolean? = null): WorkflowInput
// The Boolean overload sets type = Boolean automatically
```

### Impact

- `InputRegistry`: one map instead of two, `booleanDefaults` property removed
- `ReusableWorkflow`: `inputsAsRawMap()` simplified — single `when (def.default)` branch
- `ReusableWorkflow.toWorkflowCallTrigger()`: always uses `_customArguments` path (the non-boolean path was a special case that produced identical output for string-only workflows)
- `InputsYamlMapper.toInputsYaml()`: simplified — reads `default` directly from `WorkflowInputDef`
- `AdapterWorkflowBuilder`: uses same `InputRegistry`, gets unified `input()` for free

### Files Changed

| File | Change |
|------|--------|
| `workflow-dsl/.../core/InputRegistry.kt` | Single map, unified `input()` methods |
| `workflow-dsl/.../core/WorkflowInput.kt` | Add `InputDefault`, `InputType`, `WorkflowInputDef` |
| `workflow-dsl/.../core/ReusableWorkflow.kt` | Remove `booleanDefaults` references, simplify `inputsAsRawMap()` and `toWorkflowCallTrigger()` |
| `workflow-dsl/.../yaml/InputsYamlMapper.kt` | Simplify — read `default` from `WorkflowInputDef` directly |
| `workflow-dsl/.../builder/AdapterWorkflowBuilder.kt` | Remove explicit `booleanInput()`, rely on overloaded `input()` |

---

## 2. JobBuilder Boilerplate Reduction

### Problem

8 base workflows repeat the same pattern:
```kotlin
class JobBuilder : ReusableWorkflowJobBuilder(ThisWorkflow), SetupCapableJobBuilder {
    override var setupAction by stringInput(ThisWorkflow.setupAction)
    override var setupParams by stringInput(ThisWorkflow.setupParams)
    var customField by refInput(ThisWorkflow.customField)
}

context(builder: AdapterWorkflowBuilder)
fun job(id: String, block: JobBuilder.() -> Unit = {}) {
    builder.registerJob(buildJob(id, ::JobBuilder, block))
}
```

The `setupAction`/`setupParams` delegation and the `job()` function are identical in 5 of 8 workflows.

### Solution

**2a. `SetupAwareJobBuilder` base class:**

```kotlin
abstract class SetupAwareJobBuilder(workflow: ReusableWorkflow) :
    ReusableWorkflowJobBuilder(workflow), SetupCapableJobBuilder {
    // Auto-wires setupAction/setupParams if workflow implements SetupCapability
    override var setupAction by stringInput((workflow as SetupCapability).setupAction)
    override var setupParams by stringInput((workflow as SetupCapability).setupParams)
}
```

5 workflows (Check, CreateTag, ManualCreateTag, Publish, AppDeploy) switch from:
```kotlin
class JobBuilder : ReusableWorkflowJobBuilder(X), SetupCapableJobBuilder {
    override var setupAction by stringInput(X.setupAction)
    override var setupParams by stringInput(X.setupParams)
    ...
}
```
to:
```kotlin
class JobBuilder : SetupAwareJobBuilder(X) {
    ...
}
```

**2b. `job()` function moved to `ReusableWorkflow`:**

```kotlin
// In ReusableWorkflow — template job() function
context(builder: AdapterWorkflowBuilder)
inline fun <B : ReusableWorkflowJobBuilder> job(
    id: String,
    crossinline factory: () -> B,
    block: B.() -> Unit = {},
) {
    builder.registerJob(buildJob(id, factory, block))
}
```

Each workflow keeps a per-workflow `job()` as a one-liner delegating to the base. This preserves the current call-site API (`CheckWorkflow.job("check") { ... }`) while eliminating the repeated body:

```kotlin
context(builder: AdapterWorkflowBuilder)
fun job(id: String, block: JobBuilder.() -> Unit = {}) = job(id, ::JobBuilder, block)
```

### Impact

- ~4 lines removed per setup-capable workflow (5 workflows = ~20 lines)
- `job()` function body eliminated from all 8 workflows (either removed or reduced to one-liner)

### Files Changed

| File | Change |
|------|--------|
| `workflow-dsl/.../builder/ReusableWorkflowJobBuilder.kt` | Add `SetupAwareJobBuilder` |
| `workflow-dsl/.../core/ReusableWorkflow.kt` | Add generic `job()` template |
| `src/.../base/CheckWorkflow.kt` | `JobBuilder` extends `SetupAwareJobBuilder`, remove `job()` body |
| `src/.../base/CreateTagWorkflow.kt` | Same |
| `src/.../base/ManualCreateTagWorkflow.kt` | Same |
| `src/.../base/PublishWorkflow.kt` | Same |
| `src/.../base/AppDeployWorkflow.kt` | Same |
| `src/.../base/ReleaseWorkflow.kt` | Remove `job()` body (delegate to base) |
| `src/.../base/ConventionalCommitCheckWorkflow.kt` | Same |
| `src/.../base/LabelerWorkflow.kt` | Same |

---

## 3. Ecosystem Config & Adapter Factories

### Problem

`ToolTagConfig` only covers tag-related adapters. Check adapters (GradleCheck) and release adapters (ReleaseAdapters) have their own inline configuration. Adding a new ecosystem requires creating adapters in 3-4 places with similar boilerplate.

### Solution

Replace `ToolTagConfig` with `EcosystemConfig`:

```kotlin
data class EcosystemConfig(
    val tool: SetupTool,
    val checkCommandName: String,
    val checkCommandDescription: String,
    val defaultCheckCommand: String,
    val defaultTagPrefix: String,
)

val GRADLE = EcosystemConfig(
    tool = SetupTool.Gradle,
    checkCommandName = "gradle-command",
    checkCommandDescription = "Gradle check command",
    defaultCheckCommand = "./gradlew check",
    defaultTagPrefix = "",
)

val GO = EcosystemConfig(
    tool = SetupTool.Go,
    checkCommandName = "check-command",
    checkCommandDescription = "Go validation command",
    defaultCheckCommand = "make test",
    defaultTagPrefix = "v",
)

// Future:
// val PYTHON = EcosystemConfig(tool = SetupTool.Python, ...)
// val NODE = EcosystemConfig(tool = SetupTool.Node, ...)
```

Unified adapter factories:

```kotlin
fun ecosystemCreateTag(fileName: String, name: String, eco: EcosystemConfig): AdapterWorkflow
fun ecosystemManualCreateTag(fileName: String, name: String, eco: EcosystemConfig): AdapterWorkflow
fun ecosystemCheck(fileName: String, name: String, eco: EcosystemConfig, matrixExpr: String): AdapterWorkflow
```

Adding a full Python ecosystem becomes:

```kotlin
object PythonCheck {
    val pythonCheck = ecosystemCheck("python-check.yml", "Python Check", PYTHON, PYTHON_VERSION_MATRIX_EXPR)
}

object CreateTagAdapters {
    val gradle = ecosystemCreateTag("gradle-create-tag.yml", "Gradle Create Tag", GRADLE)
    val go = ecosystemCreateTag("go-create-tag.yml", "Go Create Tag", GO)
    val python = ecosystemCreateTag("python-create-tag.yml", "Python Create Tag", PYTHON)
}
```

### Impact

- `ToolTagConfig` replaced by `EcosystemConfig`
- `CreateTagAdapters.toolCreateTag()` renamed to standalone `ecosystemCreateTag()`
- `ManualCreateTagAdapters.toolManualCreateTag()` renamed to standalone `ecosystemManualCreateTag()`
- GradleCheck's `gradleCheck()` factory generalized to `ecosystemCheck()`
- Adding a new ecosystem: 1 `EcosystemConfig` + N one-liner adapter declarations

### Files Changed

| File | Change |
|------|--------|
| `src/.../config/ToolTagConfig.kt` | Rename to `EcosystemConfig.kt`, expand fields |
| `src/.../adapters/tag/CreateTagAdapters.kt` | Use `ecosystemCreateTag()` |
| `src/.../adapters/tag/ManualCreateTagAdapters.kt` | Use `ecosystemManualCreateTag()` |
| `src/.../adapters/check/GradleCheck.kt` | Extract `ecosystemCheck()`, generalize |

---

## 4. Secret Groups

### Problem

Selective secret passthrough requires listing each secret explicitly. `ReleaseAdapters.kotlinLibrary` lists 6 Maven secrets individually. If a new Maven secret is added to `PublishWorkflow`, every adapter that passes Maven secrets must be updated.

### Solution

Named secret groups as `List<WorkflowSecret>` in workflow objects:

```kotlin
object PublishWorkflow : ProjectWorkflow("publish.yml", "Publish"), SetupCapability {
    val mavenSonatypeUsername = secret(...)
    val mavenSonatypeToken = secret(...)
    val mavenSonatypeSigningKeyId = secret(...)
    val mavenSonatypeSigningPubKeyAsciiArmored = secret(...)
    val mavenSonatypeSigningKeyAsciiArmored = secret(...)
    val mavenSonatypeSigningPassword = secret(...)
    val gradlePublishKey = secret(...)
    val gradlePublishSecret = secret(...)

    val mavenSecrets = listOf(
        mavenSonatypeUsername, mavenSonatypeToken,
        mavenSonatypeSigningKeyId, mavenSonatypeSigningPubKeyAsciiArmored,
        mavenSonatypeSigningKeyAsciiArmored, mavenSonatypeSigningPassword,
    )
    val gradlePortalSecrets = listOf(gradlePublishKey, gradlePublishSecret)
}
```

New overload in `ReusableWorkflowJobBuilder`:

```kotlin
fun passthroughSecrets(secrets: List<WorkflowSecret>) {
    secrets.forEach { secret -> secretsMap[secret.name] = secret.ref.expression }
}
```

Usage:
```kotlin
// Kotlin Library — Maven only
passthroughSecrets(PublishWorkflow.mavenSecrets)

// Gradle Plugin — all
passthroughAllSecrets()
```

### Impact

- Adding/removing a Maven secret: change `PublishWorkflow.mavenSecrets` list, all consumers pick it up
- `passthroughSecrets(vararg)` overload stays for ad-hoc usage

### Files Changed

| File | Change |
|------|--------|
| `workflow-dsl/.../builder/ReusableWorkflowJobBuilder.kt` | Add `passthroughSecrets(List)` overload |
| `src/.../base/PublishWorkflow.kt` | Add `mavenSecrets`, `gradlePortalSecrets` lists |
| `src/.../adapters/release/ReleaseAdapters.kt` | Use `passthroughSecrets(PublishWorkflow.mavenSecrets)` |

---

## 5. Minor Improvements

### 5.1 `passthroughAllSecrets()` — use stored `WorkflowSecret` refs

**Current:** Reconstructs expression string `"\${{ secrets.$name }}"` from scratch.
**Change:** Store `WorkflowSecret` objects in `ReusableWorkflow._secretObjects` and reuse `ref.expression`.

**Files:** `ReusableWorkflow.kt`, `ReusableWorkflowJobBuilder.kt`

### 5.2 `SetupAction.toYamlArguments()` — `buildMap` instead of `apply`

**Current:** `linkedMapOf(...).apply { ... }`
**Change:** `buildMap { put(...); fetchDepth?.let { put(...) } }`

**Files:** `SetupAction.kt`

### 5.3 `SetupTool.toParamsJson` — remove `MatrixRefExpr` overload

**Current:** Two methods with identical body except `.expression` unwrap.
**Change:** Keep only `toParamsJson(String)`. Callers pass `matrixRef.expression` explicitly. Add extension if needed.

**Files:** `SetupTool.kt`, `SetupSteps.kt`

### 5.4 `InputDsl` interface for input registration

**Current:** Both `ReusableWorkflow` and `AdapterWorkflowBuilder` manually delegate to `InputRegistry`.
**Change:** Extract `InputDsl` interface with `input()` overloads. Both classes implement it via delegation to `InputRegistry`.

**Files:** `InputRegistry.kt` (add `InputDsl` interface), `ReusableWorkflow.kt`, `AdapterWorkflowBuilder.kt`

---

## Summary of Changes

| Category | Lines Removed (est.) | Lines Added (est.) | Net |
|----------|---------------------|--------------------|----|
| Unified Input Type | ~60 | ~40 | -20 |
| JobBuilder Reduction | ~50 | ~15 | -35 |
| Ecosystem Config | ~40 | ~30 | -10 |
| Secret Groups | ~10 | ~15 | +5 |
| Minor Improvements | ~20 | ~10 | -10 |
| **Total** | **~180** | **~110** | **-70** |

## Execution Order

Phases listed by dependency (within each phase, tasks are independent):

1. **Unified Input Type** — foundational change, everything else builds on it
2. **InputDsl interface + passthroughAllSecrets fix** — small, enables cleaner API for phase 3
3. **JobBuilder reduction + SetupAwareJobBuilder** — requires unified inputs to be in place
4. **Secret Groups** — independent, but cleaner after JobBuilder changes
5. **Ecosystem Config + Adapter Factories** — final step, uses all prior improvements
6. **Minor improvements** (SetupAction, SetupTool overloads) — independent, can be done anytime
