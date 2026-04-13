# Type-Safe Workflow Generation Design

**Date:** 2026-04-13
**Branch:** feature/kts-script
**Library:** io.github.typesafegithub:github-workflows-kt:3.7.0

## Goal

Increase type safety of GitHub Actions workflow generation by maximizing use of `github-workflows-kt` library features and building targeted custom abstractions for gaps the library doesn't cover.

## Problems Addressed

| ID | Problem | Current State |
|----|---------|---------------|
| A | Manual action classes with hardcoded `usesString` | 7 hand-written classes in `Actions.kt` |
| B | Duplicated input definitions across adapters | Same `WorkflowCall.Input(...)` copy-pasted in multiple adapters |
| C | Raw string expressions `"\${{ inputs.* }}"` | No compile-time verification of input/secret names |
| D | YAML post-processing via `cleanReusableWorkflowJobs()` | 90-line fragile YAML parser removing `runs-on`/`steps` |
| E | JSON strings as parameters | `"{\"java-version\": \"...\"}"` — no structural typing |
| F | Secret passthrough duplication | Each secret group has a manual `*_PASSTHROUGH` companion |

## Library Capabilities vs Custom Code

| Feature | Library Support | Custom Required |
|---------|----------------|-----------------|
| Typed action bindings | Yes — JIT via `bindings.krzeminski.it` | Only for local composite actions |
| `workflow_call` trigger (callee) | Yes | No |
| Reusable workflow jobs (caller, job-level `uses:`) | No | Yes |
| Expression DSL for `inputs.*` | No (issue #811) | Yes |
| Expression DSL for `secrets.*` | Partial | Yes (for workflow-scoped secrets) |
| Boolean input defaults | No (`default: String?` only) | Yes (existing workaround retained) |

---

## Section 1: JIT Action Bindings

**Replace** manual action classes with auto-generated typed bindings from the library's binding server.

### build.gradle.kts Changes

```kotlin
repositories {
    mavenCentral()
    maven("https://bindings.krzeminski.it")
}

dependencies {
    implementation("io.github.typesafegithub:github-workflows-kt:3.7.0")
    implementation("actions:checkout:v6")
    implementation("mathieudutour:github-tag-action:v6")
    implementation("mikepenz:release-changelog-builder-action:v6")
    implementation("softprops:action-gh-release:v2")
    implementation("actions:labeler:v6")
}
```

### Actions.kt Changes

**Remove:** `CheckoutAction`, `GithubTagAction`, `ReleaseChangelogBuilderAction`, `GhReleaseAction`, `LabelerAction`

**Keep:** `SetupAction`, `CreateAppTokenAction` (local composite actions — no JIT bindings available)

**Replace with imports:**
```kotlin
import io.github.typesafegithub.workflows.actions.actions.Checkout
import io.github.typesafegithub.workflows.actions.mathieudutour.GithubTagAction
import io.github.typesafegithub.workflows.actions.mikepenz.ReleaseChangelogBuilderAction
import io.github.typesafegithub.workflows.actions.softprops.ActionGhRelease
import io.github.typesafegithub.workflows.actions.actions.Labeler
```

Use `_Untyped` parameter variants for GitHub expressions (e.g., `fetchDepth_Untyped = "0"`).

---

## Section 2: Type-Safe Expression Refs

**Add** `.ref` property to `WorkflowInput` and `WorkflowSecret` for compile-time safe references.

### ReusableWorkflow.kt Changes

```kotlin
class WorkflowInput(val name: String) {
    val ref: String get() = "\${{ inputs.$name }}"
}

class WorkflowSecret(val name: String) {
    val ref: String get() = "\${{ secrets.$name }}"
}
```

### Impact on Base Workflows

All string literals like `"\${{ inputs.check-command }}"` in base workflows become `CreateTagWorkflow.checkCommand.ref`. Renaming an input in `Workflows.kt` produces compile errors at all usage sites.

### Scope

- **Base workflows** — all `inputs.*` and `secrets.*` string refs replaced with `.ref`
- **Adapter workflows** — refs to base workflow inputs already type-safe via `operator invoke`; refs to own adapter inputs use `inputRef()` helper (see Section 4)

---

## Section 3: SetupTool Sealed Class

**Replace** string-based setup action selection and JSON parameter construction with a sealed class hierarchy.

### New File: config/SetupTool.kt

```kotlin
sealed class SetupTool(
    val actionName: String,
    val versionKey: String,
    val defaultVersion: String,
) {
    val id: String get() = actionName.removePrefix("setup-")

    abstract fun toParamsJson(versionExpr: String): String

    data object Gradle : SetupTool("setup-gradle", "java-version", DEFAULT_JAVA_VERSION) {
        override fun toParamsJson(versionExpr: String) =
            """{"java-version": "$versionExpr"}"""
    }

    data object Go : SetupTool("setup-go", "go-version", DEFAULT_GO_VERSION) {
        override fun toParamsJson(versionExpr: String) =
            """{"go-version": "$versionExpr"}"""
    }

    data object Python : SetupTool("setup-python", "python-version", DEFAULT_PYTHON_VERSION) {
        override fun toParamsJson(versionExpr: String) =
            """{"python-version": "$versionExpr"}"""
    }
}
```

### conditionalSetupSteps() Changes

Replace 3 copy-pasted `uses()` blocks with a loop over `SetupTool::class.sealedSubclasses`:

```kotlin
fun JobBuilder<*>.conditionalSetupSteps(fetchDepth: String? = null) {
    SetupTool::class.sealedSubclasses.mapNotNull { it.objectInstance }.forEach { tool ->
        uses(
            name = "Setup ${tool.id.replaceFirstChar { it.uppercase() }}",
            action = SetupAction(
                tool.actionName, tool.versionKey,
                "\${{ fromJson(inputs.setup-params).${tool.versionKey} || '${tool.defaultVersion}' }}",
                fetchDepth,
            ),
            condition = "inputs.setup-action == '${tool.id}'",
        )
    }
}
```

### Adapter Usage

```kotlin
CreateTagWorkflow.setupAction(SetupTool.Gradle.id)
CreateTagWorkflow.setupParams(SetupTool.Gradle.toParamsJson("\${{ inputs.java-version }}"))
```

Adding a new toolchain = add `data object` to `SetupTool` + composite action. Everything else auto-propagates.

---

## Section 4: Common Adapter Inputs

**Eliminate** input definition duplication across adapter workflows.

### New File: config/CommonInputs.kt

```kotlin
object CommonInputs {
    fun javaVersion(default: String = DEFAULT_JAVA_VERSION) =
        "java-version" to WorkflowCall.Input("JDK version to use", false, WorkflowCall.Type.String, default)

    fun javaVersions() =
        "java-versions" to WorkflowCall.Input(
            "JSON array of JDK versions for matrix build (overrides java-version)",
            false, WorkflowCall.Type.String, "")

    fun gradleCommand(default: String = "./gradlew check") =
        "gradle-command" to WorkflowCall.Input("Gradle check command", false, WorkflowCall.Type.String, default)

    fun publishCommand(description: String = "Gradle publish command") =
        "publish-command" to WorkflowCall.Input(description, true, WorkflowCall.Type.String)

    fun changelogConfig() =
        "changelog-config" to WorkflowCall.Input(
            "Path to changelog configuration file", false, WorkflowCall.Type.String, DEFAULT_CHANGELOG_CONFIG)

    fun goVersion(default: String = DEFAULT_GO_VERSION) =
        "go-version" to WorkflowCall.Input("Go version to use", false, WorkflowCall.Type.String, default)

    fun defaultBump() =
        "default-bump" to WorkflowCall.Input(
            "Default version bump type (major, minor, patch)", false, WorkflowCall.Type.String, "patch")

    fun tagPrefix() =
        "tag-prefix" to WorkflowCall.Input("Prefix for the tag", false, WorkflowCall.Type.String, "")

    fun releaseBranches() =
        "release-branches" to WorkflowCall.Input(
            "Comma-separated branch patterns for releases", false, WorkflowCall.Type.String, DEFAULT_RELEASE_BRANCHES)
}
```

### Helper for Adapter Input Refs

```kotlin
fun inputRef(name: String) = "\${{ inputs.$name }}"
```

---

## Section 5: Adapter Workflow Generation Without Post-Processing

**Replace** `cleanReusableWorkflowJobs()` with direct YAML generation for adapter workflows.

### New File: dsl/AdapterWorkflow.kt

Separate generation path for adapter workflows that only contain reusable workflow job calls:

```kotlin
data class ReusableWorkflowJobDef(
    val id: String,
    val uses: ReusableWorkflow,
    val needs: List<String> = emptyList(),
    val condition: String? = null,
    val with: Map<String, String> = emptyMap(),
    val secrets: Map<String, String> = emptyMap(),
    val strategy: Map<String, Any>? = null,
)

fun generateAdapterWorkflow(
    name: String,
    targetFileName: String,
    on: WorkflowCall,
    permissions: Map<Permission, Mode>? = null,
    jobs: List<ReusableWorkflowJobDef>,
)
```

Generates clean YAML with no `runs-on`/`steps` in reusable workflow jobs. Uses snakeyaml (transitive dependency) or simple template serialization.

### What Gets Deleted

- `cleanReusableWorkflowJobs()` — 90 lines
- `noop()` helper
- All `cleanReusableWorkflowJobs(File(outputDir, targetFile))` calls in adapters

### Builder DSL

`reusableJob()` function reuses `ReusableWorkflowJobBuilder` but returns `ReusableWorkflowJobDef` instead of creating a library job:

```kotlin
fun reusableJob(
    id: String,
    uses: ReusableWorkflow,
    block: ReusableWorkflowJobBuilder.() -> Unit = {},
): ReusableWorkflowJobDef
```

### Two Generation Modes

1. **Base workflows** — `workflow() { job(...) { steps } }` via `github-workflows-kt` DSL (unchanged)
2. **Adapter workflows** — `generateAdapterWorkflow(...)` with direct YAML output (new)

---

## Section 6: Secret Passthrough Simplification

**Replace** manual `*_PASSTHROUGH` constants with an extension function.

### Changes to Secrets.kt

```kotlin
fun Map<String, WorkflowCall.Secret>.passthrough(): Map<String, String> =
    keys.associateWith { "\${{ secrets.$it }}" }
```

**Remove:** `MAVEN_SONATYPE_SECRETS_PASSTHROUGH`, `GRADLE_PORTAL_SECRETS_PASSTHROUGH`, `APP_SECRETS_PASSTHROUGH`

**Usage:**
```kotlin
secrets(MAVEN_SONATYPE_SECRETS.passthrough() + GRADLE_PORTAL_SECRETS.passthrough())
```

---

## Section Dependencies

```
Section 1 (JIT bindings)      — independent
Section 2 (.ref)               — independent
Section 3 (SetupTool)          — independent
Section 4 (CommonInputs)       — independent
Section 5 (AdapterWorkflow)    — depends on 3, 4, 6
Section 6 (.passthrough)       — independent
```

Sections 1-4 and 6 can be implemented in parallel. Section 5 after them.

## Unchanged

- `Workflows.kt` — base workflow definitions
- `Defaults.kt`, `Refs.kt` — constants
- `Generate.kt` — entry point (same functions, same signatures)
- **Generated YAML must remain identical** — this is the acceptance criterion

## File Changes Summary

| File | Change |
|------|--------|
| `build.gradle.kts` | Add bindings repo + JIT action deps |
| `actions/Actions.kt` | Remove 5 classes, keep 2 local action wrappers |
| `config/SetupTool.kt` | **New** — sealed class for setup tools |
| `config/CommonInputs.kt` | **New** — reusable input definitions |
| `config/Secrets.kt` | Remove `*_PASSTHROUGH`, add `.passthrough()` extension |
| `dsl/ReusableWorkflow.kt` | Add `.ref` to `WorkflowInput`/`WorkflowSecret` |
| `dsl/AdapterWorkflow.kt` | **New** — direct YAML generation for adapter workflows |
| `dsl/ReusableWorkflowJobBuilder.kt` | Adapt to return `ReusableWorkflowJobDef` |
| `dsl/WorkflowHelpers.kt` | Simplify `conditionalSetupSteps`, remove `cleanReusableWorkflowJobs` + `noop` |
| `workflows/base/*.kt` | Replace action classes with JIT bindings, string refs with `.ref` |
| `workflows/adapters/**/*.kt` | Use `generateAdapterWorkflow`, `CommonInputs`, `SetupTool` |
