# Full DSL Rewrite Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rewrite the adapter workflow DSL from class-based inheritance to functional builder pattern with context parameters, eliminating duplication and improving Kotlin idiomacy.

**Architecture:** Adapter workflows switch from `class extends AdapterWorkflow { override fun jobs() }` to `adapterWorkflow(fileName, name) { Workflow.job(id) { ... } }`. Context parameters (Kotlin 2.1.20+) allow `Workflow.job()` calls inside the builder lambda to auto-register jobs. The `ReusableWorkflow` base class gains `buildJob()` as a protected factory, and `ReusableWorkflowJobBuilder` gains an infix `from` for input forwarding.

**Tech Stack:** Kotlin 2.3.20, github-workflows-kt 3.7.0, kaml 0.104.0, kotlinx-serialization, Gradle

**Verification invariant:** After every task that touches generation code, run `./gradlew run` and confirm `git diff .github/workflows/` produces zero changes. The YAML checksums captured before the rewrite must match after.

---

## Task 1: Add `-Xcontext-parameters` compiler flag

**Files:**
- Modify: `build.gradle.kts`
- Modify: `workflow-dsl/build.gradle.kts`

- [ ] **Step 1: Add context-parameters flag to root `build.gradle.kts`**

Add `kotlin` block after `dependencies`:

```kotlin
kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}
```

The full file becomes:

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

application {
    mainClass.set("generate.GenerateKt")
}

repositories {
    mavenCentral()
    maven("https://bindings.krzeminski.it")
}

dependencies {
    implementation(project(":workflow-dsl"))
    implementation(libs.github.workflows.kt)

    // JIT action bindings
    implementation("actions:checkout:v6")
    // mathieudutour:github-tag-action:v6 - not yet available in bindings.krzeminski.it registry
    implementation("mikepenz:release-changelog-builder-action:v6")
    implementation("softprops:action-gh-release:v2")
    implementation("actions:labeler:v6")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}
```

- [ ] **Step 2: Add context-parameters flag to `workflow-dsl/build.gradle.kts`**

Add `kotlin` block after `dependencies`:

```kotlin
kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}
```

The full file becomes:

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.github.workflows.kt)
    implementation(libs.kaml)
    implementation(libs.kotlinx.serialization.core)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew compileKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add build.gradle.kts workflow-dsl/build.gradle.kts
git commit -m "chore: add -Xcontext-parameters compiler flag"
```

---

## Task 2: Add `buildJob` to `ReusableWorkflow` and `from` to `ReusableWorkflowJobBuilder`

**Files:**
- Modify: `workflow-dsl/src/main/kotlin/dsl/ReusableWorkflow.kt`
- Modify: `workflow-dsl/src/main/kotlin/dsl/ReusableWorkflowJobBuilder.kt`

- [ ] **Step 1: Add `buildJob` method to `ReusableWorkflow`**

In `workflow-dsl/src/main/kotlin/dsl/ReusableWorkflow.kt`, add after the `toWorkflowCallTrigger()` method (before `inputsAsRawMap`):

```kotlin
    protected inline fun <B : ReusableWorkflowJobBuilder> buildJob(
        id: String,
        crossinline builderFactory: () -> B,
        block: B.() -> Unit = {},
    ): ReusableWorkflowJobDef {
        val builder = builderFactory()
        builder.block()
        return builder.build(id)
    }
```

- [ ] **Step 2: Add `from` infix to `ReusableWorkflowJobBuilder`**

In `workflow-dsl/src/main/kotlin/dsl/ReusableWorkflowJobBuilder.kt`, add inside the `ReusableWorkflowJobBuilder` class after `passthroughAllSecrets()`:

```kotlin
    infix fun WorkflowInput.from(source: WorkflowInput) {
        setInput(this, source.ref)
    }
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew compileKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add workflow-dsl/src/main/kotlin/dsl/ReusableWorkflow.kt workflow-dsl/src/main/kotlin/dsl/ReusableWorkflowJobBuilder.kt
git commit -m "feat: add buildJob factory and infix from for input forwarding"
```

---

## Task 3: Extract `toInputsYaml` into shared utility

**Files:**
- Create: `workflow-dsl/src/main/kotlin/dsl/InputsYamlMapper.kt`
- Modify: `workflow-dsl/src/main/kotlin/dsl/ReusableWorkflow.kt`

- [ ] **Step 1: Create `InputsYamlMapper.kt`**

Create `workflow-dsl/src/main/kotlin/dsl/InputsYamlMapper.kt`:

```kotlin
package dsl

import dsl.yaml.InputYaml
import dsl.yaml.YamlDefault
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall

fun toInputsYaml(
    inputs: Map<String, WorkflowCall.Input>,
    booleanDefaults: Map<String, Boolean>,
): Map<String, InputYaml>? =
    inputs.takeIf { it.isNotEmpty() }?.mapValues { (name, input) ->
        val default = when {
            name in booleanDefaults -> YamlDefault.BooleanValue(booleanDefaults.getValue(name))
            input.default != null   -> YamlDefault.StringValue(input.default!!)
            else                    -> null
        }
        InputYaml(
            description = input.description,
            type = input.type.name.lowercase(),
            required = input.required,
            default = default,
        )
    }
```

- [ ] **Step 2: Update `ReusableWorkflow.toInputsYaml()` to delegate**

In `workflow-dsl/src/main/kotlin/dsl/ReusableWorkflow.kt`, replace the `toInputsYaml()` method:

```kotlin
    fun toInputsYaml(): Map<String, InputYaml>? =
        toInputsYaml(_inputs, _booleanDefaults)
```

Remove the now-unused imports: `dsl.yaml.InputYaml`, `dsl.yaml.YamlDefault` (keep them only if still used elsewhere in the file — check: `YamlDefault` is used in `inputsAsRawMap` via booleanDefaults but that method uses raw `Any?` not `YamlDefault`, so both can be removed).

- [ ] **Step 3: Verify compilation and YAML unchanged**

Run: `./gradlew compileKotlin && ./gradlew run`

Then: `git diff .github/workflows/`

Expected: BUILD SUCCESSFUL, zero diff

- [ ] **Step 4: Commit**

```bash
git add workflow-dsl/src/main/kotlin/dsl/InputsYamlMapper.kt workflow-dsl/src/main/kotlin/dsl/ReusableWorkflow.kt
git commit -m "refactor: extract toInputsYaml into shared utility"
```

---

## Task 4: Create `AdapterWorkflowBuilder` and immutable `AdapterWorkflow`

**Files:**
- Create: `workflow-dsl/src/main/kotlin/dsl/AdapterWorkflowBuilder.kt`
- Modify: `workflow-dsl/src/main/kotlin/dsl/AdapterWorkflow.kt`

This is the core structural change. The old `AdapterWorkflow` abstract class is replaced by an immutable class (result) and a new `AdapterWorkflowBuilder` (DSL scope). The old abstract class is kept temporarily alongside the new code until adapters are migrated.

- [ ] **Step 1: Create `AdapterWorkflowBuilder.kt`**

Create `workflow-dsl/src/main/kotlin/dsl/AdapterWorkflowBuilder.kt`:

```kotlin
package dsl

import dsl.yaml.AdapterWorkflowYaml
import dsl.yaml.SecretYaml
import dsl.yaml.TriggerYaml
import dsl.yaml.WorkflowCallBodyYaml
import dsl.yaml.adapterWorkflowYaml
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import java.io.File

private val QUOTED_MAP_KEY = Regex("""^(\s*)'([^']*?)'\s*:""", RegexOption.MULTILINE)

private fun unquoteYamlMapKeys(yaml: String): String =
    yaml.replace(QUOTED_MAP_KEY, "$1$2:")

class BuiltAdapterWorkflow(
    val fileName: String,
    val workflowName: String,
    private val inputsYaml: Map<String, dsl.yaml.InputYaml>?,
    private val jobs: List<ReusableWorkflowJobDef>,
) {
    fun generate(outputDir: File) {
        val collectedSecrets = collectSecretsFromJobs(jobs)

        val dto = AdapterWorkflowYaml(
            name = workflowName,
            on = TriggerYaml(
                workflowCall = WorkflowCallBodyYaml(
                    inputs = inputsYaml,
                    secrets = collectedSecrets,
                )
            ),
            jobs = jobs.associate { job -> job.id to job.toJobYaml() },
        )

        val header = buildString {
            appendLine("# This file was generated using Kotlin DSL (src/main/kotlin/).")
            appendLine("# If you want to modify the workflow, please change the Kotlin source and regenerate this YAML file.")
            append("# Generated with https://github.com/typesafegithub/github-workflows-kt")
        }

        val rawBody = adapterWorkflowYaml.encodeToString(AdapterWorkflowYaml.serializer(), dto)
        val body = unquoteYamlMapKeys(rawBody)

        File(outputDir, fileName).writeText("$header\n\n$body\n")
    }

    private fun collectSecretsFromJobs(jobDefs: List<ReusableWorkflowJobDef>): Map<String, SecretYaml>? =
        buildMap {
            for (job in jobDefs) {
                val descriptions = job.uses.secrets.mapValues { it.value.description }
                for (name in job.secrets.keys) {
                    putIfAbsent(name, SecretYaml(description = descriptions[name] ?: name, required = true))
                }
            }
        }.takeIf { it.isNotEmpty() }
}

class AdapterWorkflowBuilder(private val fileName: String, private val name: String) {
    private val inputs = linkedMapOf<String, WorkflowCall.Input>()
    private val booleanDefaults = mutableMapOf<String, Boolean>()
    private val jobs = mutableListOf<ReusableWorkflowJobDef>()

    fun input(
        name: String,
        description: String,
        required: Boolean = false,
        type: WorkflowCall.Type = WorkflowCall.Type.String,
        default: String? = null,
    ): WorkflowInput {
        inputs[name] = WorkflowCall.Input(description, required, type, default)
        return WorkflowInput(name)
    }

    fun booleanInput(
        name: String,
        description: String,
        default: Boolean? = null,
    ): WorkflowInput {
        inputs[name] = WorkflowCall.Input(description, false, WorkflowCall.Type.Boolean, null)
        default?.let { booleanDefaults[name] = it }
        return WorkflowInput(name)
    }

    fun matrixRef(key: String) = MatrixRef(key)

    fun matrix(vararg entries: Pair<String, String>) = MatrixDef(mapOf(*entries))

    internal fun registerJob(job: ReusableWorkflowJobDef) {
        jobs += job
    }

    fun build(): BuiltAdapterWorkflow = BuiltAdapterWorkflow(
        fileName = fileName,
        workflowName = name,
        inputsYaml = toInputsYaml(inputs, booleanDefaults),
        jobs = jobs.toList(),
    )
}

fun adapterWorkflow(
    fileName: String,
    name: String,
    block: AdapterWorkflowBuilder.() -> Unit,
): BuiltAdapterWorkflow {
    val builder = AdapterWorkflowBuilder(fileName, name)
    builder.block()
    return builder.build()
}
```

Note: The class is named `BuiltAdapterWorkflow` to avoid name clash with the existing `AdapterWorkflow` abstract class during the migration period. After all adapters are migrated, the old `AdapterWorkflow` is deleted and `BuiltAdapterWorkflow` can be renamed to `AdapterWorkflow`.

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileKotlin`

Expected: BUILD SUCCESSFUL (old code untouched, new code just added)

- [ ] **Step 3: Commit**

```bash
git add workflow-dsl/src/main/kotlin/dsl/AdapterWorkflowBuilder.kt
git commit -m "feat: add AdapterWorkflowBuilder and BuiltAdapterWorkflow"
```

---

## Task 5: Add context parameter `job()` methods to all workflow definitions

**Files:**
- Modify: `src/main/kotlin/workflows/definitions/CheckWorkflow.kt`
- Modify: `src/main/kotlin/workflows/definitions/ConventionalCommitCheckWorkflow.kt`
- Modify: `src/main/kotlin/workflows/definitions/CreateTagWorkflow.kt`
- Modify: `src/main/kotlin/workflows/definitions/ManualCreateTagWorkflow.kt`
- Modify: `src/main/kotlin/workflows/definitions/ReleaseWorkflow.kt`
- Modify: `src/main/kotlin/workflows/definitions/PublishWorkflow.kt`
- Modify: `src/main/kotlin/workflows/definitions/LabelerWorkflow.kt`
- Modify: `src/main/kotlin/workflows/definitions/AppDeployWorkflow.kt`

Each workflow object gets a `context(_: AdapterWorkflowBuilder) fun job(...)` method that calls `buildJob` and registers the result via `registerJob`.

- [ ] **Step 1: Add `job()` to `CheckWorkflow`**

In `src/main/kotlin/workflows/definitions/CheckWorkflow.kt`, add import and method:

Add import: `import dsl.AdapterWorkflowBuilder`

Add after the `JobBuilder` class:

```kotlin
    context(_: AdapterWorkflowBuilder)
    fun job(id: String, block: JobBuilder.() -> Unit = {}) {
        registerJob(buildJob(id, ::JobBuilder, block))
    }
```

- [ ] **Step 2: Add `job()` to `ConventionalCommitCheckWorkflow`**

In `src/main/kotlin/workflows/definitions/ConventionalCommitCheckWorkflow.kt`, add import `import dsl.AdapterWorkflowBuilder` and add after `JobBuilder` class:

```kotlin
    context(_: AdapterWorkflowBuilder)
    fun job(id: String, block: JobBuilder.() -> Unit = {}) {
        registerJob(buildJob(id, ::JobBuilder, block))
    }
```

- [ ] **Step 3: Add `job()` to `CreateTagWorkflow`**

In `src/main/kotlin/workflows/definitions/CreateTagWorkflow.kt`, add import `import dsl.AdapterWorkflowBuilder` and add after `JobBuilder` class:

```kotlin
    context(_: AdapterWorkflowBuilder)
    fun job(id: String, block: JobBuilder.() -> Unit = {}) {
        registerJob(buildJob(id, ::JobBuilder, block))
    }
```

- [ ] **Step 4: Add `job()` to `ManualCreateTagWorkflow`**

In `src/main/kotlin/workflows/definitions/ManualCreateTagWorkflow.kt`, add import `import dsl.AdapterWorkflowBuilder` and add after `JobBuilder` class:

```kotlin
    context(_: AdapterWorkflowBuilder)
    fun job(id: String, block: JobBuilder.() -> Unit = {}) {
        registerJob(buildJob(id, ::JobBuilder, block))
    }
```

- [ ] **Step 5: Add `job()` to `ReleaseWorkflow`**

In `src/main/kotlin/workflows/definitions/ReleaseWorkflow.kt`, add import `import dsl.AdapterWorkflowBuilder` and add after `JobBuilder` class:

```kotlin
    context(_: AdapterWorkflowBuilder)
    fun job(id: String, block: JobBuilder.() -> Unit = {}) {
        registerJob(buildJob(id, ::JobBuilder, block))
    }
```

- [ ] **Step 6: Add `job()` to `PublishWorkflow`**

In `src/main/kotlin/workflows/definitions/PublishWorkflow.kt`, add import `import dsl.AdapterWorkflowBuilder` and add after `JobBuilder` class:

```kotlin
    context(_: AdapterWorkflowBuilder)
    fun job(id: String, block: JobBuilder.() -> Unit = {}) {
        registerJob(buildJob(id, ::JobBuilder, block))
    }
```

- [ ] **Step 7: Add `job()` to `LabelerWorkflow`**

In `src/main/kotlin/workflows/definitions/LabelerWorkflow.kt`, add import `import dsl.AdapterWorkflowBuilder` and add after `JobBuilder` class:

```kotlin
    context(_: AdapterWorkflowBuilder)
    fun job(id: String, block: JobBuilder.() -> Unit = {}) {
        registerJob(buildJob(id, ::JobBuilder, block))
    }
```

- [ ] **Step 8: Add `job()` to `AppDeployWorkflow`**

In `src/main/kotlin/workflows/definitions/AppDeployWorkflow.kt`, add import `import dsl.AdapterWorkflowBuilder` and add after `JobBuilder` class:

```kotlin
    context(_: AdapterWorkflowBuilder)
    fun job(id: String, block: JobBuilder.() -> Unit = {}) {
        registerJob(buildJob(id, ::JobBuilder, block))
    }
```

- [ ] **Step 9: Verify compilation**

Run: `./gradlew compileKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 10: Commit**

```bash
git add src/main/kotlin/workflows/definitions/
git commit -m "feat: add context parameter job() methods to all workflow definitions"
```

---

## Task 6: Migrate check adapters to builder DSL

**Files:**
- Modify: `src/main/kotlin/workflows/adapters/check/GradleCheck.kt`
- Modify: `src/main/kotlin/generate/Generate.kt`

- [ ] **Step 1: Rewrite `GradleCheck.kt` as function**

Replace the entire content of `src/main/kotlin/workflows/adapters/check/GradleCheck.kt`:

```kotlin
package workflows.adapters.check

import config.DEFAULT_JAVA_VERSION
import config.JAVA_VERSION_MATRIX_EXPR
import config.SetupTool
import dsl.BuiltAdapterWorkflow
import dsl.adapterWorkflow
import workflows.definitions.CheckWorkflow
import workflows.definitions.ConventionalCommitCheckWorkflow
import workflows.setup

fun gradleCheck(fileName: String, name: String): BuiltAdapterWorkflow = adapterWorkflow(fileName, name) {
    val javaVersion = input(
        "java-version",
        description = "JDK version to use",
        default = DEFAULT_JAVA_VERSION,
    )
    val javaVersions = input(
        "java-versions",
        description = "JSON array of JDK versions for matrix build (overrides java-version)",
        default = "",
    )
    val gradleCommand = input(
        "gradle-command",
        description = "Gradle check command",
        default = "./gradlew check",
    )

    val javaVersionMatrix = matrixRef("java-version")

    ConventionalCommitCheckWorkflow.job("conventional-commit")

    CheckWorkflow.job("check") {
        strategy(matrix(javaVersionMatrix.key to JAVA_VERSION_MATRIX_EXPR))
        setup(SetupTool.Gradle, javaVersionMatrix.ref)
        CheckWorkflow.checkCommand from gradleCommand
    }
}
```

- [ ] **Step 2: Update `Generate.kt` to use function**

In `src/main/kotlin/generate/Generate.kt`, replace the import:

```
import workflows.adapters.check.GradleCheckAdapter
```

with:

```
import workflows.adapters.check.gradleCheck
```

Replace the 4 `GradleCheckAdapter` calls:

```kotlin
    GradleCheckAdapter("app-check.yml", "Application Check").generate(outputDir)
    GradleCheckAdapter("gradle-check.yml", "Gradle Check").generate(outputDir)
    GradleCheckAdapter("gradle-plugin-check.yml", "Gradle Plugin Check").generate(outputDir)
    GradleCheckAdapter("kotlin-library-check.yml", "Kotlin Library Check").generate(outputDir)
```

with:

```kotlin
    gradleCheck("app-check.yml", "Application Check").generate(outputDir)
    gradleCheck("gradle-check.yml", "Gradle Check").generate(outputDir)
    gradleCheck("gradle-plugin-check.yml", "Gradle Plugin Check").generate(outputDir)
    gradleCheck("kotlin-library-check.yml", "Kotlin Library Check").generate(outputDir)
```

- [ ] **Step 3: Verify YAML unchanged**

Run: `./gradlew run`

Then: `git diff .github/workflows/`

Expected: zero diff on all 4 check YAML files (`app-check.yml`, `gradle-check.yml`, `gradle-plugin-check.yml`, `kotlin-library-check.yml`)

**IMPORTANT:** If the diff shows input ordering differences, fix by ensuring `AdapterWorkflowBuilder.inputs` uses `linkedMapOf` (already specified in Task 4). If `matrix()` helper produces different format, verify `MatrixDef` constructor matches.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/workflows/adapters/check/GradleCheck.kt src/main/kotlin/generate/Generate.kt
git commit -m "refactor: migrate check adapters to builder DSL"
```

---

## Task 7: Migrate tag adapters to builder DSL

**Files:**
- Modify: `src/main/kotlin/workflows/adapters/tag/CreateTagAdapter.kt` (rename to `CreateTag.kt`)
- Modify: `src/main/kotlin/workflows/adapters/tag/ManualCreateTagAdapter.kt` (rename to `ManualCreateTag.kt`)
- Modify: `src/main/kotlin/generate/Generate.kt`

- [ ] **Step 1: Rewrite `CreateTagAdapter.kt` as `CreateTag.kt`**

Delete `src/main/kotlin/workflows/adapters/tag/CreateTagAdapter.kt` and create `src/main/kotlin/workflows/adapters/tag/CreateTag.kt`:

```kotlin
package workflows.adapters.tag

import config.DEFAULT_RELEASE_BRANCHES
import config.SetupTool
import dsl.BuiltAdapterWorkflow
import dsl.adapterWorkflow
import workflows.definitions.CreateTagWorkflow
import workflows.setup

fun toolCreateTag(
    fileName: String,
    name: String,
    tool: SetupTool,
    commandInputName: String,
    commandDescription: String,
    defaultCommand: String,
    defaultTagPrefix: String,
): BuiltAdapterWorkflow = adapterWorkflow(fileName, name) {
    val version = input(tool.versionKey, description = tool.versionDescription, default = tool.defaultVersion)
    val checkCommand = input(commandInputName, description = commandDescription, default = defaultCommand)
    val defaultBump = input("default-bump", description = "Default version bump type (major, minor, patch)", default = "patch")
    val tagPrefix = input("tag-prefix", description = "Prefix for the tag", default = defaultTagPrefix)
    val releaseBranches = input("release-branches", description = "Comma-separated branch patterns for releases", default = DEFAULT_RELEASE_BRANCHES)

    CreateTagWorkflow.job("create-tag") {
        setup(tool, version.ref.expression)
        CreateTagWorkflow.checkCommand from checkCommand
        CreateTagWorkflow.defaultBump from defaultBump
        CreateTagWorkflow.tagPrefix from tagPrefix
        CreateTagWorkflow.releaseBranches from releaseBranches
        passthroughAllSecrets()
    }
}
```

- [ ] **Step 2: Rewrite `ManualCreateTagAdapter.kt` as `ManualCreateTag.kt`**

Delete `src/main/kotlin/workflows/adapters/tag/ManualCreateTagAdapter.kt` and create `src/main/kotlin/workflows/adapters/tag/ManualCreateTag.kt`:

```kotlin
package workflows.adapters.tag

import config.SetupTool
import dsl.BuiltAdapterWorkflow
import dsl.adapterWorkflow
import workflows.definitions.ManualCreateTagWorkflow
import workflows.setup

fun toolManualCreateTag(
    fileName: String,
    name: String,
    tool: SetupTool,
    commandInputName: String,
    commandDescription: String,
    defaultCommand: String,
    defaultTagPrefix: String,
): BuiltAdapterWorkflow = adapterWorkflow(fileName, name) {
    val tagVersion = input("tag-version", description = "Version to tag (e.g. 1.2.3)", required = true)
    val version = input(tool.versionKey, description = tool.versionDescription, default = tool.defaultVersion)
    val checkCommand = input(commandInputName, description = commandDescription, default = defaultCommand)
    val tagPrefix = input("tag-prefix", description = "Prefix for the tag", default = defaultTagPrefix)

    ManualCreateTagWorkflow.job("manual-tag") {
        ManualCreateTagWorkflow.tagVersion from tagVersion
        ManualCreateTagWorkflow.tagPrefix from tagPrefix
        setup(tool, version.ref.expression)
        ManualCreateTagWorkflow.checkCommand from checkCommand
        passthroughAllSecrets()
    }
}
```

- [ ] **Step 3: Update `Generate.kt` imports and calls**

In `src/main/kotlin/generate/Generate.kt`, replace imports:

```
import workflows.adapters.tag.CreateTagAdapter
import workflows.adapters.tag.ManualCreateTagAdapter
```

with:

```
import workflows.adapters.tag.toolCreateTag
import workflows.adapters.tag.toolManualCreateTag
```

Replace the 4 tag adapter instantiations:

```kotlin
    CreateTagAdapter(
        fileName = "gradle-create-tag.yml",
        workflowName = "Gradle Create Tag",
        tool = SetupTool.Gradle,
        commandInputName = "gradle-command",
        commandDescription = "Gradle check command",
        defaultCommand = "./gradlew check",
        defaultTagPrefix = "",
    ).generate(outputDir)
    CreateTagAdapter(
        fileName = "go-create-tag.yml",
        workflowName = "Go Create Tag",
        tool = SetupTool.Go,
        commandInputName = "check-command",
        commandDescription = "Go validation command",
        defaultCommand = "make test",
        defaultTagPrefix = "v",
    ).generate(outputDir)
    ManualCreateTagAdapter(
        fileName = "gradle-manual-create-tag.yml",
        workflowName = "Gradle Manual Create Tag",
        tool = SetupTool.Gradle,
        commandInputName = "gradle-command",
        commandDescription = "Gradle check command",
        defaultCommand = "./gradlew check",
        defaultTagPrefix = "",
    ).generate(outputDir)
    ManualCreateTagAdapter(
        fileName = "go-manual-create-tag.yml",
        workflowName = "Go Manual Create Tag",
        tool = SetupTool.Go,
        commandInputName = "check-command",
        commandDescription = "Go validation command",
        defaultCommand = "make test",
        defaultTagPrefix = "v",
    ).generate(outputDir)
```

with:

```kotlin
    toolCreateTag(
        fileName = "gradle-create-tag.yml",
        name = "Gradle Create Tag",
        tool = SetupTool.Gradle,
        commandInputName = "gradle-command",
        commandDescription = "Gradle check command",
        defaultCommand = "./gradlew check",
        defaultTagPrefix = "",
    ).generate(outputDir)
    toolCreateTag(
        fileName = "go-create-tag.yml",
        name = "Go Create Tag",
        tool = SetupTool.Go,
        commandInputName = "check-command",
        commandDescription = "Go validation command",
        defaultCommand = "make test",
        defaultTagPrefix = "v",
    ).generate(outputDir)
    toolManualCreateTag(
        fileName = "gradle-manual-create-tag.yml",
        name = "Gradle Manual Create Tag",
        tool = SetupTool.Gradle,
        commandInputName = "gradle-command",
        commandDescription = "Gradle check command",
        defaultCommand = "./gradlew check",
        defaultTagPrefix = "",
    ).generate(outputDir)
    toolManualCreateTag(
        fileName = "go-manual-create-tag.yml",
        name = "Go Manual Create Tag",
        tool = SetupTool.Go,
        commandInputName = "check-command",
        commandDescription = "Go validation command",
        defaultCommand = "make test",
        defaultTagPrefix = "v",
    ).generate(outputDir)
```

- [ ] **Step 4: Verify YAML unchanged**

Run: `./gradlew run`

Then: `git diff .github/workflows/`

Expected: zero diff on `gradle-create-tag.yml`, `go-create-tag.yml`, `gradle-manual-create-tag.yml`, `go-manual-create-tag.yml`

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/workflows/adapters/tag/ src/main/kotlin/generate/Generate.kt
git commit -m "refactor: migrate tag adapters to builder DSL"
```

---

## Task 8: Migrate release adapters to builder DSL

**Files:**
- Create: `src/main/kotlin/workflows/adapters/release/GradleRelease.kt`
- Modify: `src/main/kotlin/workflows/adapters/release/AppRelease.kt`
- Delete: `src/main/kotlin/workflows/adapters/release/GradlePluginRelease.kt`
- Delete: `src/main/kotlin/workflows/adapters/release/KotlinLibraryRelease.kt`
- Modify: `src/main/kotlin/generate/Generate.kt`

- [ ] **Step 1: Create `GradleRelease.kt` with unified factory**

Create `src/main/kotlin/workflows/adapters/release/GradleRelease.kt`:

```kotlin
package workflows.adapters.release

import config.DEFAULT_CHANGELOG_CONFIG
import config.DEFAULT_JAVA_VERSION
import config.SetupTool
import dsl.BuiltAdapterWorkflow
import dsl.adapterWorkflow
import workflows.definitions.PublishWorkflow
import workflows.definitions.ReleaseWorkflow
import workflows.setup

fun gradleReleaseWorkflow(
    fileName: String,
    name: String,
    publishDescription: String,
    publishSecrets: PublishWorkflow.JobBuilder.() -> Unit = { passthroughAllSecrets() },
): BuiltAdapterWorkflow = adapterWorkflow(fileName, name) {
    val javaVersion = input(
        "java-version",
        description = "JDK version to use",
        default = DEFAULT_JAVA_VERSION,
    )
    val publishCommand = input(
        "publish-command",
        description = publishDescription,
        required = true,
    )
    val changelogConfig = input(
        "changelog-config",
        description = "Path to changelog configuration file",
        default = DEFAULT_CHANGELOG_CONFIG,
    )

    ReleaseWorkflow.job("release") {
        ReleaseWorkflow.changelogConfig from changelogConfig
    }

    PublishWorkflow.job("publish") {
        needs("release")
        setup(SetupTool.Gradle, javaVersion.ref.expression)
        PublishWorkflow.publishCommand from publishCommand
        publishSecrets()
    }
}
```

- [ ] **Step 2: Rewrite `AppRelease.kt` as function**

Replace the entire content of `src/main/kotlin/workflows/adapters/release/AppRelease.kt`:

```kotlin
package workflows.adapters.release

import config.DEFAULT_CHANGELOG_CONFIG
import dsl.BuiltAdapterWorkflow
import dsl.adapterWorkflow
import workflows.definitions.ReleaseWorkflow

val appRelease: BuiltAdapterWorkflow = adapterWorkflow("app-release.yml", "Application Release") {
    val changelogConfig = input(
        "changelog-config",
        description = "Path to changelog configuration file",
        default = DEFAULT_CHANGELOG_CONFIG,
    )
    val draft = booleanInput(
        "draft",
        description = "Create release as draft (default true for apps)",
        default = true,
    )

    ReleaseWorkflow.job("release") {
        ReleaseWorkflow.changelogConfig from changelogConfig
        ReleaseWorkflow.draft from draft
    }
}
```

- [ ] **Step 3: Delete old release adapter files**

Delete `src/main/kotlin/workflows/adapters/release/GradlePluginRelease.kt` and `src/main/kotlin/workflows/adapters/release/KotlinLibraryRelease.kt`.

- [ ] **Step 4: Update `Generate.kt`**

In `src/main/kotlin/generate/Generate.kt`, replace imports:

```
import workflows.adapters.release.AppReleaseAdapter
import workflows.adapters.release.GradlePluginReleaseAdapter
import workflows.adapters.release.KotlinLibraryReleaseAdapter
```

with:

```
import workflows.adapters.release.appRelease
import workflows.adapters.release.gradleReleaseWorkflow
```

Replace the 3 release adapter calls:

```kotlin
    AppReleaseAdapter.generate(outputDir)
    GradlePluginReleaseAdapter.generate(outputDir)
    KotlinLibraryReleaseAdapter.generate(outputDir)
```

with:

```kotlin
    appRelease.generate(outputDir)
    gradleReleaseWorkflow(
        fileName = "gradle-plugin-release.yml",
        name = "Gradle Plugin Release",
        publishDescription = "Gradle publish command (publishes to both Maven Central and Gradle Portal)",
    ).generate(outputDir)
    gradleReleaseWorkflow(
        fileName = "kotlin-library-release.yml",
        name = "Kotlin Library Release",
        publishDescription = "Gradle publish command for Maven Central",
        publishSecrets = {
            passthroughSecrets(
                PublishWorkflow.mavenSonatypeUsername,
                PublishWorkflow.mavenSonatypeToken,
                PublishWorkflow.mavenSonatypeSigningKeyId,
                PublishWorkflow.mavenSonatypeSigningPubKeyAsciiArmored,
                PublishWorkflow.mavenSonatypeSigningKeyAsciiArmored,
                PublishWorkflow.mavenSonatypeSigningPassword,
            )
        },
    ).generate(outputDir)
```

Add import for `PublishWorkflow` if not already present:

```
import workflows.definitions.PublishWorkflow
```

- [ ] **Step 5: Verify YAML unchanged**

Run: `./gradlew run`

Then: `git diff .github/workflows/`

Expected: zero diff on `app-release.yml`, `gradle-plugin-release.yml`, `kotlin-library-release.yml`

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/workflows/adapters/release/ src/main/kotlin/generate/Generate.kt
git commit -m "refactor: migrate release adapters to builder DSL, unify gradle releases"
```

---

## Task 9: Delete old abstract `AdapterWorkflow` and `ProjectAdapterWorkflow`

**Files:**
- Modify: `workflow-dsl/src/main/kotlin/dsl/AdapterWorkflow.kt` (delete the file)
- Delete: `src/main/kotlin/workflows/ProjectAdapterWorkflow.kt`
- Modify: `workflow-dsl/src/main/kotlin/dsl/AdapterWorkflowBuilder.kt` (rename `BuiltAdapterWorkflow` → `AdapterWorkflow`)

At this point all adapters use the new builder DSL. The old abstract classes have no consumers.

- [ ] **Step 1: Delete old `AdapterWorkflow.kt`**

Delete `workflow-dsl/src/main/kotlin/dsl/AdapterWorkflow.kt`.

- [ ] **Step 2: Delete `ProjectAdapterWorkflow.kt`**

Delete `src/main/kotlin/workflows/ProjectAdapterWorkflow.kt`.

- [ ] **Step 3: Rename `BuiltAdapterWorkflow` to `AdapterWorkflow`**

In `workflow-dsl/src/main/kotlin/dsl/AdapterWorkflowBuilder.kt`, rename the class `BuiltAdapterWorkflow` to `AdapterWorkflow` (find-replace all occurrences in the file).

- [ ] **Step 4: Update adapter imports**

In all adapter files that import `BuiltAdapterWorkflow`, change to `AdapterWorkflow`:

- `src/main/kotlin/workflows/adapters/check/GradleCheck.kt`
- `src/main/kotlin/workflows/adapters/tag/CreateTag.kt`
- `src/main/kotlin/workflows/adapters/tag/ManualCreateTag.kt`
- `src/main/kotlin/workflows/adapters/release/GradleRelease.kt`
- `src/main/kotlin/workflows/adapters/release/AppRelease.kt`

In each file, replace `import dsl.BuiltAdapterWorkflow` with `import dsl.AdapterWorkflow` and replace the return type `BuiltAdapterWorkflow` with `AdapterWorkflow`.

- [ ] **Step 5: Verify compilation and YAML unchanged**

Run: `./gradlew run`

Then: `git diff .github/workflows/`

Expected: zero diff

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor: remove old abstract AdapterWorkflow, rename BuiltAdapterWorkflow"
```

---

## Task 10: Remove old `reusableJob` top-level function

**Files:**
- Modify: `workflow-dsl/src/main/kotlin/dsl/ReusableWorkflow.kt`

- [ ] **Step 1: Remove `reusableJob` function**

In `workflow-dsl/src/main/kotlin/dsl/ReusableWorkflow.kt`, delete the function at the bottom of the file (lines 115-124):

```kotlin
inline fun <B : ReusableWorkflowJobBuilder> reusableJob(
    id: String,
    uses: ReusableWorkflow,
    builderFactory: () -> B,
    block: B.() -> Unit = {},
): ReusableWorkflowJobDef {
    val builder = builderFactory()
    builder.block()
    return builder.build(id)
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileKotlin`

Expected: BUILD SUCCESSFUL (if any file still imports `reusableJob`, the compiler will catch it — fix those imports)

- [ ] **Step 3: Commit**

```bash
git add workflow-dsl/src/main/kotlin/dsl/ReusableWorkflow.kt
git commit -m "refactor: remove old reusableJob top-level function"
```

---

## Task 11: Idiomatic Kotlin improvements

**Files:**
- Modify: `src/main/kotlin/actions/Actions.kt`
- Modify: `src/main/kotlin/config/SetupTool.kt`
- Modify: `workflow-dsl/src/main/kotlin/dsl/ReusableWorkflow.kt`

- [ ] **Step 1: `SetupAction.toYamlArguments` — use `buildMap`**

In `src/main/kotlin/actions/Actions.kt`, replace in `SetupAction`:

```kotlin
    override fun toYamlArguments() = linkedMapOf(
        versionKey to version,
    ).apply {
        fetchDepth?.let { put("fetch-depth", it) }
    }
```

with:

```kotlin
    override fun toYamlArguments() = buildMap {
        put(versionKey, version)
        fetchDepth?.let { put("fetch-depth", it) }
    }
```

- [ ] **Step 2: `SetupTool.toParamsJson` — use `buildJsonObject`**

In `src/main/kotlin/config/SetupTool.kt`, add import:

```kotlin
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
```

Replace both `toParamsJson` methods:

```kotlin
    fun toParamsJson(versionExpr: String): String =
        """{"$versionKey": "$versionExpr"}"""

    fun toParamsJson(versionExpr: MatrixRefExpr): String =
        """{"$versionKey": "${versionExpr.expression}"}"""
```

with:

```kotlin
    fun toParamsJson(versionExpr: String): String =
        buildJsonObject { put(versionKey, versionExpr) }.toString()

    fun toParamsJson(versionExpr: MatrixRefExpr): String =
        toParamsJson(versionExpr.expression)
```

- [ ] **Step 3: `ReusableWorkflow.inputsAsRawMap` — remove explicit type parameter**

In `workflow-dsl/src/main/kotlin/dsl/ReusableWorkflow.kt`, replace:

```kotlin
    private fun inputsAsRawMap(): Map<String, Map<String, Any?>> =
        _inputs.mapValues { (name, input) ->
            buildMap<String, Any?> {
                put("description", input.description)
                put("type", input.type.name.lowercase())
                put("required", input.required)
                val boolDefault = _booleanDefaults[name]
                if (boolDefault != null) {
                    put("default", boolDefault)
                } else if (input.default != null) {
                    put("default", input.default)
                }
            }
        }
```

with:

```kotlin
    private fun inputsAsRawMap(): Map<String, Map<String, Any?>> =
        _inputs.mapValues { (name, input) ->
            buildMap {
                put("description", input.description)
                put("type", input.type.name.lowercase())
                put("required", input.required)
                _booleanDefaults[name]?.let { put("default", it) }
                    ?: input.default?.let { put("default", it) }
            }
        }
```

- [ ] **Step 4: Add `kotlinx-serialization-json` to root dependencies**

The root module needs `kotlinx-serialization-json` for `buildJsonObject`. Check if it's available transitively through `github-workflows-kt`. If not, add to `gradle/libs.versions.toml`:

```toml
[libraries]
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinx-serialization-core" }
```

And to `build.gradle.kts`:

```kotlin
implementation(libs.kotlinx.serialization.json)
```

- [ ] **Step 5: Verify YAML unchanged**

Run: `./gradlew run`

Then: `git diff .github/workflows/`

Expected: zero diff

**WARNING:** `buildJsonObject` produces `{"key":"value"}` (no spaces), while the old string template produced `{"key": "value"}` (with space after colon). Check the generated YAML for `setup-params` values. If they differ, the YAML will change. In that case, keep the string interpolation approach for `toParamsJson` — the idiomatic improvement is not worth a YAML change.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/actions/Actions.kt src/main/kotlin/config/SetupTool.kt workflow-dsl/src/main/kotlin/dsl/ReusableWorkflow.kt gradle/libs.versions.toml build.gradle.kts
git commit -m "refactor: idiomatic Kotlin improvements (buildMap, buildJsonObject)"
```

---

## Task 12: Split `Actions.kt` into separate files

**Files:**
- Modify: `src/main/kotlin/actions/Actions.kt` (keep only `SetupAction`)
- Create: `src/main/kotlin/actions/CreateAppTokenAction.kt`
- Create: `src/main/kotlin/actions/GithubTagAction.kt`

- [ ] **Step 1: Create `CreateAppTokenAction.kt`**

Create `src/main/kotlin/actions/CreateAppTokenAction.kt`:

```kotlin
package actions

import config.localAction
import io.github.typesafegithub.workflows.domain.actions.Action

class CreateAppTokenAction(
    private val appId: String,
    private val appPrivateKey: String,
) : Action<CreateAppTokenAction.CreateAppTokenOutputs>() {
    override val usesString = localAction("create-app-token")
    override fun toYamlArguments() = linkedMapOf(
        "app-id" to appId,
        "app-private-key" to appPrivateKey,
    )

    override fun buildOutputObject(stepId: String) = CreateAppTokenOutputs(stepId)

    class CreateAppTokenOutputs(stepId: String) : Outputs(stepId) {
        val token: String get() = get("token")
    }
}
```

- [ ] **Step 2: Create `GithubTagAction.kt`**

Create `src/main/kotlin/actions/GithubTagAction.kt`:

```kotlin
package actions

import io.github.typesafegithub.workflows.domain.actions.Action

class GithubTagAction(
    private val githubToken: String,
    private val defaultBump: String,
    private val tagPrefix: String,
    private val releaseBranches: String,
) : Action<Action.Outputs>() {
    override val usesString = "mathieudutour/github-tag-action@v6.2"
    override fun toYamlArguments() = linkedMapOf(
        "github_token" to githubToken,
        "default_bump" to defaultBump,
        "tag_prefix" to tagPrefix,
        "release_branches" to releaseBranches,
    )

    override fun buildOutputObject(stepId: String) = Outputs(stepId)
}
```

- [ ] **Step 3: Trim `Actions.kt` to `SetupAction` only and rename**

Replace the entire content of `src/main/kotlin/actions/Actions.kt` with just `SetupAction`. Then rename the file to `SetupAction.kt`:

Delete `src/main/kotlin/actions/Actions.kt`, create `src/main/kotlin/actions/SetupAction.kt`:

```kotlin
package actions

import config.localAction
import io.github.typesafegithub.workflows.domain.actions.Action

class SetupAction(
    private val actionName: String,
    private val versionKey: String,
    private val version: String,
    private val fetchDepth: String? = null,
) : Action<Action.Outputs>() {
    override val usesString = localAction(actionName)
    override fun toYamlArguments() = buildMap {
        put(versionKey, version)
        fetchDepth?.let { put("fetch-depth", it) }
    }

    override fun buildOutputObject(stepId: String) = Outputs(stepId)
}
```

- [ ] **Step 4: Verify compilation and YAML unchanged**

Run: `./gradlew run`

Then: `git diff .github/workflows/`

Expected: zero diff

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/actions/
git commit -m "refactor: split Actions.kt into one file per class"
```

---

## Task 13: Package restructure — `workflows/core/` and `workflows/helpers/`

**Files:**
- Move: `src/main/kotlin/workflows/ProjectWorkflow.kt` → `src/main/kotlin/workflows/core/ProjectWorkflow.kt`
- Move: `src/main/kotlin/workflows/WorkflowHelpers.kt` → `src/main/kotlin/workflows/helpers/SetupHelpers.kt`

- [ ] **Step 1: Move `ProjectWorkflow.kt` to `core/` package**

Create `src/main/kotlin/workflows/core/ProjectWorkflow.kt`:

```kotlin
package workflows.core

import config.reusableWorkflow
import dsl.ReusableWorkflow

abstract class ProjectWorkflow(fileName: String) : ReusableWorkflow(fileName) {
    override val usesString: String = reusableWorkflow(fileName)
}
```

Delete `src/main/kotlin/workflows/ProjectWorkflow.kt`.

- [ ] **Step 2: Update imports in all workflow definitions**

In all 8 files in `src/main/kotlin/workflows/definitions/`, replace:

```
import workflows.ProjectWorkflow
```

with:

```
import workflows.core.ProjectWorkflow
```

Files to update:
- `CheckWorkflow.kt`
- `ConventionalCommitCheckWorkflow.kt`
- `CreateTagWorkflow.kt`
- `ManualCreateTagWorkflow.kt`
- `ReleaseWorkflow.kt`
- `PublishWorkflow.kt`
- `LabelerWorkflow.kt`
- `AppDeployWorkflow.kt`

- [ ] **Step 3: Move and rename `WorkflowHelpers.kt` to `helpers/SetupHelpers.kt`**

Create `src/main/kotlin/workflows/helpers/SetupHelpers.kt`:

```kotlin
package workflows.helpers

import actions.SetupAction
import config.SetupTool
import dsl.MatrixRefExpr
import dsl.SetupConfigurable
import io.github.typesafegithub.workflows.dsl.JobBuilder

fun JobBuilder<*>.conditionalSetupSteps(fetchDepth: String? = null) {
    listOf(SetupTool.Gradle, SetupTool.Go, SetupTool.Python).forEach { tool ->
        uses(
            name = "Setup ${tool.id.replaceFirstChar { c -> c.uppercase() }}",
            action = SetupAction(
                tool.actionName, tool.versionKey,
                "\${{ fromJson(inputs.setup-params).${tool.versionKey} || '${tool.defaultVersion}' }}",
                fetchDepth,
            ),
            condition = "inputs.setup-action == '${tool.id}'",
        )
    }
}

fun SetupConfigurable.setup(tool: SetupTool, versionExpr: MatrixRefExpr) {
    setupAction = tool.id
    setupParams = tool.toParamsJson(versionExpr)
}

fun SetupConfigurable.setup(tool: SetupTool, versionRef: String) {
    setupAction = tool.id
    setupParams = tool.toParamsJson(versionRef)
}
```

Delete `src/main/kotlin/workflows/WorkflowHelpers.kt`.

- [ ] **Step 4: Update imports for `conditionalSetupSteps` and `setup`**

All files importing from `workflows` package need updating.

In `src/main/kotlin/workflows/base/` files (`Check.kt`, `CreateTag.kt`, `ManualCreateTag.kt`, `Publish.kt`, `AppDeploy.kt`, `ConventionalCommitCheck.kt`), replace:

```
import workflows.conditionalSetupSteps
```

with:

```
import workflows.helpers.conditionalSetupSteps
```

In adapter files (`GradleCheck.kt`, `CreateTag.kt`, `ManualCreateTag.kt`, `GradleRelease.kt`), replace:

```
import workflows.setup
```

with:

```
import workflows.helpers.setup
```

- [ ] **Step 5: Verify compilation and YAML unchanged**

Run: `./gradlew run`

Then: `git diff .github/workflows/`

Expected: zero diff

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/workflows/
git commit -m "refactor: move ProjectWorkflow to core/, WorkflowHelpers to helpers/SetupHelpers"
```

---

## Task 14: Restructure `Generate.kt`

**Files:**
- Modify: `src/main/kotlin/generate/Generate.kt`

- [ ] **Step 1: Split `main()` into structured functions**

Replace the entire content of `src/main/kotlin/generate/Generate.kt`:

```kotlin
package generate

import config.SetupTool
import workflows.adapters.check.gradleCheck
import workflows.adapters.release.appRelease
import workflows.adapters.release.gradleReleaseWorkflow
import workflows.adapters.tag.toolCreateTag
import workflows.adapters.tag.toolManualCreateTag
import workflows.base.generateAppDeploy
import workflows.base.generateCheck
import workflows.base.generateConventionalCommitCheck
import workflows.base.generateCreateTag
import workflows.base.generateLabeler
import workflows.base.generateManualCreateTag
import workflows.base.generatePublish
import workflows.base.generateRelease
import workflows.definitions.PublishWorkflow
import java.io.File

fun main() {
    val outputDir = File(".github/workflows").apply { mkdirs() }

    generateBaseWorkflows()
    generateAdapterWorkflows(outputDir)
}

private fun generateBaseWorkflows() {
    generateCheck()
    generateConventionalCommitCheck()
    generateCreateTag()
    generateManualCreateTag()
    generateRelease()
    generatePublish()
    generateLabeler()
    generateAppDeploy()
}

private fun generateAdapterWorkflows(outputDir: File) {
    // Check adapters
    gradleCheck("app-check.yml", "Application Check").generate(outputDir)
    gradleCheck("gradle-check.yml", "Gradle Check").generate(outputDir)
    gradleCheck("gradle-plugin-check.yml", "Gradle Plugin Check").generate(outputDir)
    gradleCheck("kotlin-library-check.yml", "Kotlin Library Check").generate(outputDir)

    // Tag adapters
    toolCreateTag(
        fileName = "gradle-create-tag.yml",
        name = "Gradle Create Tag",
        tool = SetupTool.Gradle,
        commandInputName = "gradle-command",
        commandDescription = "Gradle check command",
        defaultCommand = "./gradlew check",
        defaultTagPrefix = "",
    ).generate(outputDir)
    toolCreateTag(
        fileName = "go-create-tag.yml",
        name = "Go Create Tag",
        tool = SetupTool.Go,
        commandInputName = "check-command",
        commandDescription = "Go validation command",
        defaultCommand = "make test",
        defaultTagPrefix = "v",
    ).generate(outputDir)
    toolManualCreateTag(
        fileName = "gradle-manual-create-tag.yml",
        name = "Gradle Manual Create Tag",
        tool = SetupTool.Gradle,
        commandInputName = "gradle-command",
        commandDescription = "Gradle check command",
        defaultCommand = "./gradlew check",
        defaultTagPrefix = "",
    ).generate(outputDir)
    toolManualCreateTag(
        fileName = "go-manual-create-tag.yml",
        name = "Go Manual Create Tag",
        tool = SetupTool.Go,
        commandInputName = "check-command",
        commandDescription = "Go validation command",
        defaultCommand = "make test",
        defaultTagPrefix = "v",
    ).generate(outputDir)

    // Release adapters
    appRelease.generate(outputDir)
    gradleReleaseWorkflow(
        fileName = "gradle-plugin-release.yml",
        name = "Gradle Plugin Release",
        publishDescription = "Gradle publish command (publishes to both Maven Central and Gradle Portal)",
    ).generate(outputDir)
    gradleReleaseWorkflow(
        fileName = "kotlin-library-release.yml",
        name = "Kotlin Library Release",
        publishDescription = "Gradle publish command for Maven Central",
        publishSecrets = {
            passthroughSecrets(
                PublishWorkflow.mavenSonatypeUsername,
                PublishWorkflow.mavenSonatypeToken,
                PublishWorkflow.mavenSonatypeSigningKeyId,
                PublishWorkflow.mavenSonatypeSigningPubKeyAsciiArmored,
                PublishWorkflow.mavenSonatypeSigningKeyAsciiArmored,
                PublishWorkflow.mavenSonatypeSigningPassword,
            )
        },
    ).generate(outputDir)
}
```

- [ ] **Step 2: Verify YAML unchanged**

Run: `./gradlew run`

Then: `git diff .github/workflows/`

Expected: zero diff

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/generate/Generate.kt
git commit -m "refactor: restructure Generate.kt into generateBaseWorkflows and generateAdapterWorkflows"
```

---

## Task 15: Final verification

**Files:** None (verification only)

- [ ] **Step 1: Full clean build**

Run: `./gradlew clean build`

Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Regenerate and verify YAML**

Run: `./gradlew run`

Then run checksum comparison:

```bash
md5sum .github/workflows/*.yml | sort
```

Expected checksums (captured before refactoring):
```
04a324afaf12bba9419d5fe2db3c57af  .github/workflows/go-create-tag.yml
0c071bf59d4eeec646445adae649b35f  .github/workflows/publish.yml
0e03ecc41225b9e6bdaad47c25c8e15f  .github/workflows/release.yml
10d82bf033d50b7679211244e1680a35  .github/workflows/conventional-commit-check.yml
124b7bc838f04ecdbd3bdd4fff1c68d1  .github/workflows/create-tag.yml
4b66d44d395e5ab706dd94e5cf6d8f47  .github/workflows/go-manual-create-tag.yml
4c2a0c41b6d3d830e2d92e7fd360e2ab  .github/workflows/manual-create-tag.yml
57a8e68a582725725e97508a41710644  .github/workflows/gradle-plugin-release.yml
6f6c80869d6d6e0d2aa2bd6e9d3ae136  .github/workflows/kotlin-library-check.yml
6f7faeacd8c985f68b4f65f7f1565766  .github/workflows/kotlin-library-release.yml
75fd8a9c36ca6da88951456f27d43ee8  .github/workflows/check.yml
78c4a643e6d0bf3b1bc9a9261bd57dec  .github/workflows/gradle-manual-create-tag.yml
80d75a658a60808599ffeab5f54ce3ee  .github/workflows/app-release.yml
88c84f74283016d88d6cb1791e8fa4be  .github/workflows/app-check.yml
910b3efe598f6cc4f1d929a8cdfd40c1  .github/workflows/labeler.yml
999df9f94abc32843cb22979e00f8505  .github/workflows/gradle-plugin-check.yml
9b28b6719acdf2db7a9e4e17a5f23c64  .github/workflows/gradle-check.yml
a29331b026d5365177a43fac0bd5f6de  .github/workflows/gradle-create-tag.yml
a7772a98984909372114f7c7984938a5  .github/workflows/app-deploy.yml
d476d27802d909b9adab1225e5ddf3c2  .github/workflows/verify-workflows.yml
```

Base workflows (`check.yml`, `create-tag.yml`, `manual-create-tag.yml`, `release.yml`, `publish.yml`, `conventional-commit-check.yml`, `labeler.yml`, `app-deploy.yml`, `verify-workflows.yml`) must have identical checksums — they were not modified.

Adapter workflows should also match if the refactoring preserved input ordering and formatting.

- [ ] **Step 3: Check for unused files**

Verify no orphaned files remain:

```bash
find src/main/kotlin -name "*.kt" | sort
find workflow-dsl/src/main/kotlin -name "*.kt" | sort
```

Confirm no old files like `Actions.kt`, `ProjectAdapterWorkflow.kt`, `CreateTagAdapter.kt`, `ManualCreateTagAdapter.kt`, `GradlePluginRelease.kt`, `KotlinLibraryRelease.kt` exist.

- [ ] **Step 4: Final commit if any cleanup needed**

If any fixups were required, commit them:

```bash
git add -A
git commit -m "chore: final cleanup after DSL rewrite"
```
