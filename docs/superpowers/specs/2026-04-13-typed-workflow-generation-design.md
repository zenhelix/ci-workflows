# Typed Workflow Generation Design

## Context

The `ci-workflows` project generates GitHub Actions YAML from Kotlin using `github-workflows-kt:3.7.0`. The codebase has a three-level architecture: base workflows (using the library's `workflow()` DSL), adapter workflows (custom YAML generation via `generateAdapterWorkflow()`), and project-level caller workflows.

Current type-safety gaps:
- Adapter workflows build YAML with raw `StringBuilder` — no serialization library, manual escaping
- `_Untyped` action bindings used where typed versions exist (deprecated)
- Adapter inputs referenced via `inputRef("raw-string")` — no compile-time validation
- `CommonInputs` returns `Pair<String, WorkflowCall.Input>` — inputs disconnected from their usage sites

## Approach

Средний вариант: максимально использовать возможности `github-workflows-kt`, заменить raw strings на typed references, добавить `kaml` для YAML-сериализации. Raw YAML остаётся только для job-level `uses:` — единственного gap'а в библиотеке.

## Part 1: Typed Action Bindings

Replace deprecated `_Untyped` action classes with typed versions from JIT bindings (`bindings.krzeminski.it`).

| File | Before | After |
|------|--------|-------|
| `Release.kt` | `Checkout_Untyped(fetchDepth_Untyped = "0")` | `Checkout(fetchDepth = Checkout.FetchDepth.Value(0))` |
| `Release.kt` | `ActionGhRelease_Untyped(body_Untyped, ...)` | `ActionGhRelease(body, name, tagName, draft)` |
| `Labeler.kt` | `Labeler_Untyped(repoToken_Untyped, ...)` | `Labeler(repoToken, configurationPath, syncLabels)` |
| `Release.kt` | `ReleaseChangelogBuilderAction_Untyped(...)` | Stays `_Untyped` — no typed binding in registry |

## Part 2: Adapter Workflows as Typed Classes

New `AdapterWorkflow` base class extending `ReusableWorkflow`:

```kotlin
abstract class AdapterWorkflow(
    fileName: String,
    val workflowName: String,
) : ReusableWorkflow(fileName) {

    abstract fun jobs(): List<ReusableWorkflowJobDef>

    fun generate(outputDir: File) {
        generateAdapterWorkflow(
            name = workflowName,
            targetFileName = fileName,
            trigger = toWorkflowCallTrigger(),
            jobs = jobs(),
            outputDir = outputDir,
        )
    }
}
```

Each adapter becomes a singleton object:

```kotlin
object GradleCheckAdapter : AdapterWorkflow("gradle-check.yml", "Gradle Check") {
    val javaVersion = input("java-version", description = "...", default = DEFAULT_JAVA_VERSION)
    val gradleCommand = input("gradle-command", description = "...", default = "./gradlew check")

    override fun jobs() = listOf(
        reusableJob("conventional-commit", ConventionalCommitCheckWorkflow),
        reusableJob("check", CheckWorkflow) {
            CheckWorkflow.setupAction(SetupTool.Gradle.id)
            CheckWorkflow.setupParams(SetupTool.Gradle.toParamsJson(javaVersion.ref))
            CheckWorkflow.checkCommand(gradleCommand.ref)  // typed reference
        }
    )
}
```

**Shared adapters with different names:** Some adapters are structurally identical but differ only in name/slug (e.g., `AppCheck`, `GradlePluginCheck`, `KotlinLibraryCheck` all delegate to `generateGradleCheckWorkflow()`). These become a single `AdapterWorkflow` subclass instantiated with different constructor arguments:

```kotlin
class GradleCheckAdapter(
    fileName: String,
    workflowName: String,
) : AdapterWorkflow(fileName, workflowName) {
    val javaVersion = input("java-version", ...)
    val gradleCommand = input("gradle-command", ...)

    override fun jobs() = listOf(...)
}

// In Generate.kt:
val gradleCheck = GradleCheckAdapter("gradle-check.yml", "Gradle Check")
val gradlePluginCheck = GradleCheckAdapter("gradle-plugin-check.yml", "Gradle Plugin Check")
val appCheck = GradleCheckAdapter("app-check.yml", "Application Check")
val kotlinLibraryCheck = GradleCheckAdapter("kotlin-library-check.yml", "Kotlin Library Check")
```

For adapters that are unique (e.g., `AppDeploy`, `GradlePluginRelease`), singleton `object` remains the right choice.

Benefits:
- `gradleCommand.ref` instead of `inputRef("gradle-command")` — compile-time checked
- `CommonInputs` object deleted — inputs live in the adapter that owns them
- Trigger generated automatically via inherited `toWorkflowCallTrigger()`
- Uniform pattern across base and adapter workflows
- Shared adapters are a single class, not duplicated functions

## Part 3: YAML Generation via kaml

Add `kaml` + `kotlinx.serialization` for YAML output.

**Dependencies:**
```kotlin
implementation("com.charleskorn.kaml:kaml:0.104.0")
implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.11.0")
```

**Plugin:**
```kotlin
plugins {
    kotlin("plugin.serialization") version "2.3.20"
}
```

**Changes to `AdapterWorkflow.kt`:**
- Trigger section: serialized from `@Serializable` data classes representing `workflow_call` inputs/secrets
- Jobs section: serialized from `@Serializable` `ReusableWorkflowJobDef` (or a serialization-specific DTO)
- Remove manual `yamlString()`, `yamlValue()`, `appendWorkflowCallTrigger()`, `appendJob()`, `appendStrategy()`
- Keep `appendHeader()` — the generation comment is custom and not part of serializable data

**Serialization model (new DTOs):**
```kotlin
@Serializable
data class AdapterWorkflowYaml(
    val name: String,
    val on: WorkflowCallTriggerYaml,
    val jobs: Map<String, JobYaml>,
)

@Serializable
data class WorkflowCallTriggerYaml(
    @SerialName("workflow_call")
    val workflowCall: WorkflowCallBodyYaml,
)

@Serializable
data class WorkflowCallBodyYaml(
    val inputs: Map<String, InputYaml>? = null,
    val secrets: Map<String, SecretYaml>? = null,
)

@Serializable
data class InputYaml(
    val description: String,
    val type: String,
    val required: Boolean,
    val default: YamlDefault? = null, // String, Boolean, or Number via sealed class
)

@Serializable
data class SecretYaml(
    val description: String,
    val required: Boolean,
)

@Serializable
data class JobYaml(
    val needs: List<String>? = null,
    val strategy: StrategyYaml? = null,
    val uses: String,
    val with: Map<String, String>? = null,
    val secrets: Map<String, String>? = null,
)
```

The existing `ReusableWorkflowJobDef` maps to `JobYaml` via a conversion function. This keeps the builder DSL separate from serialization concerns.

## Part 4: Custom Actions Audit

- `SetupAction`, `CreateAppTokenAction` — local composite actions, stay as custom classes. Improve parameter typing where possible.
- `GithubTagAction` (`mathieudutour/github-tag-action@v6.2`) — check JIT registry availability. Replace with binding if available, otherwise keep custom class.

## What Does NOT Change

- Base workflows (`Check.kt`, `Release.kt`, etc.) — already use `workflow()` DSL
- `ReusableWorkflow` base class — extended, not rewritten
- `ReusableWorkflowJobBuilder` / `ReusableWorkflowJobDef` — stay, as job-level `uses:` is not supported by the library
- Generated YAML — must be semantically equivalent to current output

## File Impact Summary

| File | Action |
|------|--------|
| `build.gradle.kts` | Add kaml, kotlinx.serialization deps + serialization plugin |
| `dsl/AdapterWorkflow.kt` | Rewrite: new base class + kaml serialization |
| `dsl/ReusableWorkflow.kt` | Minor: make `inputsAsRawMap()` accessible or refactor trigger building |
| `dsl/ReusableWorkflowJobBuilder.kt` | Add conversion to `JobYaml` |
| `workflows/base/Release.kt` | Replace `_Untyped` actions with typed |
| `workflows/base/Labeler.kt` | Replace `Labeler_Untyped` with `Labeler` |
| `workflows/adapters/**/*.kt` | Rewrite as `AdapterWorkflow` subclasses |
| `config/CommonInputs.kt` | Delete |
| `actions/Actions.kt` | Audit, possibly replace `GithubTagAction` |
| `generate/Generate.kt` | Update to call `adapter.generate(outputDir)` |
| New: `dsl/AdapterWorkflowYaml.kt` (or similar) | Serialization DTOs |
