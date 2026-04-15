# Kotlin DSL Refactoring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor the workflow-dsl and main source modules to eliminate boolean/string input duplication, reduce JobBuilder boilerplate, introduce EcosystemConfig for extensibility, and add secret groups.

**Architecture:** Six sequential phases — each produces a compilable codebase. Phase 1 (Unified Input Type) is foundational. Phases 2-6 build on top incrementally. Validation is compilation + YAML generation (`./gradlew run`) — no test suite exists.

**Tech Stack:** Kotlin 2.3.20, github-workflows-kt 3.7.0, kaml 0.104.0, kotlinx-serialization 1.11.0, Gradle 9.4.1

**Spec:** `docs/superpowers/specs/2026-04-15-kotlin-dsl-refactoring-design.md`

---

## Pre-work: Capture baseline YAML

Before any changes, snapshot the current generated YAML so we can verify correctness after each phase.

- [ ] **Step 1: Generate and snapshot current YAML**

```bash
cd /Users/dmitriimedakin/IdeaProjects/zenhelix/ci-workflows
./gradlew run
cp -r .github/workflows /tmp/ci-workflows-baseline
```

- [ ] **Step 2: Commit any uncommitted changes**

```bash
git add -A && git status
```

Ensure working tree is clean before starting refactoring.

---

## Task 1: Unified Input Type — new types in WorkflowInput.kt

**Files:**
- Modify: `workflow-dsl/src/main/kotlin/dsl/core/WorkflowInput.kt`

- [ ] **Step 1: Add `InputDefault` sealed interface and `InputType` enum**

Replace the entire file content with:

```kotlin
package dsl.core

@JvmInline
value class InputRef(val expression: String)

@JvmInline
value class SecretRef(val expression: String)

class WorkflowInput(val name: String) {
    val ref: InputRef = InputRef("\${{ inputs.$name }}")
}

class WorkflowSecret(val name: String) {
    val ref: SecretRef = SecretRef("\${{ secrets.$name }}")
}

sealed interface InputDefault {
    data class StringDefault(val value: String) : InputDefault
    data class BooleanDefault(val value: Boolean) : InputDefault
}

enum class InputType {
    String, Boolean, Number, Choice;

    fun yamlName(): kotlin.String = name.lowercase()
}

data class WorkflowInputDef(
    val name: kotlin.String,
    val description: kotlin.String,
    val type: InputType = InputType.String,
    val required: kotlin.Boolean = false,
    val default: InputDefault? = null,
)
```

- [ ] **Step 2: Verify compilation**

```bash
./gradlew :workflow-dsl:compileKotlin
```

Expected: BUILD SUCCESSFUL (new types are additive, nothing references them yet)

- [ ] **Step 3: Commit**

```bash
git add workflow-dsl/src/main/kotlin/dsl/core/WorkflowInput.kt
git commit -m "refactor: add InputDefault, InputType, WorkflowInputDef types"
```

---

## Task 2: Unified Input Type — rewrite InputRegistry

**Files:**
- Modify: `workflow-dsl/src/main/kotlin/dsl/core/InputRegistry.kt`

- [ ] **Step 1: Replace InputRegistry to use WorkflowInputDef**

Replace the entire file with:

```kotlin
package dsl.core

class InputRegistry {
    private val _inputs = linkedMapOf<String, WorkflowInputDef>()

    val inputs: Map<String, WorkflowInputDef> get() = _inputs

    fun input(
        name: String,
        description: String,
        required: Boolean = false,
        default: String? = null,
    ): WorkflowInput {
        _inputs[name] = WorkflowInputDef(
            name = name,
            description = description,
            type = InputType.String,
            required = required,
            default = default?.let { InputDefault.StringDefault(it) },
        )
        return WorkflowInput(name)
    }

    fun input(
        name: String,
        description: String,
        required: Boolean = false,
        default: Boolean? = null,
    ): WorkflowInput {
        _inputs[name] = WorkflowInputDef(
            name = name,
            description = description,
            type = InputType.Boolean,
            required = required,
            default = default?.let { InputDefault.BooleanDefault(it) },
        )
        return WorkflowInput(name)
    }
}
```

- [ ] **Step 2: Verify workflow-dsl compiles**

```bash
./gradlew :workflow-dsl:compileKotlin
```

Expected: FAIL — `ReusableWorkflow`, `AdapterWorkflowBuilder`, `InputsYamlMapper` still reference old API (`inputRegistry.inputs` type changed, `booleanDefaults` removed). This is expected — we fix them in next tasks.

- [ ] **Step 3: Commit (compilation broken, intermediate step)**

```bash
git add workflow-dsl/src/main/kotlin/dsl/core/InputRegistry.kt
git commit -m "refactor: rewrite InputRegistry to use WorkflowInputDef (WIP — breaks compilation)"
```

---

## Task 3: Unified Input Type — rewrite InputsYamlMapper

**Files:**
- Modify: `workflow-dsl/src/main/kotlin/dsl/yaml/InputsYamlMapper.kt`

- [ ] **Step 1: Rewrite toInputsYaml to accept WorkflowInputDef**

Replace the entire file with:

```kotlin
package dsl.yaml

import dsl.core.InputDefault
import dsl.core.WorkflowInputDef

fun toInputsYaml(
    inputs: Map<String, WorkflowInputDef>,
): Map<String, InputYaml>? =
    inputs.takeIf { it.isNotEmpty() }?.mapValues { (_, def) ->
        val default = when (val d = def.default) {
            is InputDefault.StringDefault  -> YamlDefault.StringValue(d.value)
            is InputDefault.BooleanDefault -> YamlDefault.BooleanValue(d.value)
            null                           -> null
        }
        InputYaml(
            description = def.description,
            type = def.type.yamlName(),
            required = def.required,
            default = default,
        )
    }
```

- [ ] **Step 2: Commit**

```bash
git add workflow-dsl/src/main/kotlin/dsl/yaml/InputsYamlMapper.kt
git commit -m "refactor: simplify InputsYamlMapper — read defaults from WorkflowInputDef"
```

---

## Task 4: Unified Input Type — rewrite ReusableWorkflow

**Files:**
- Modify: `workflow-dsl/src/main/kotlin/dsl/core/ReusableWorkflow.kt`

- [ ] **Step 1: Rewrite ReusableWorkflow to use unified inputs**

Replace the entire file with:

```kotlin
package dsl.core

import dsl.builder.ReusableWorkflowJobBuilder
import dsl.builder.ReusableWorkflowJobDef
import dsl.yaml.InputYaml
import dsl.yaml.SecretYaml
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall

abstract class ReusableWorkflow(val fileName: String) {
    private val inputRegistry = InputRegistry()
    private val _secrets = mutableMapOf<String, WorkflowCall.Secret>()
    private val _secretObjects = mutableListOf<WorkflowSecret>()

    protected fun input(
        name: String,
        description: String,
        required: Boolean = false,
        default: String? = null,
    ): WorkflowInput = inputRegistry.input(name, description, required, default)

    protected fun input(
        name: String,
        description: String,
        required: Boolean = false,
        default: Boolean? = null,
    ): WorkflowInput = inputRegistry.input(name, description, required, default)

    protected fun secret(
        name: String,
        description: String,
        required: Boolean = true,
    ): WorkflowSecret {
        _secrets[name] = WorkflowCall.Secret(description, required)
        val obj = WorkflowSecret(name)
        _secretObjects += obj
        return obj
    }

    val inputDefs: Map<String, WorkflowInputDef> get() = inputRegistry.inputs
    val secrets: Map<String, WorkflowCall.Secret> get() = _secrets
    val secretObjects: List<WorkflowSecret> get() = _secretObjects
    val requiredInputNames: Set<String> by lazy {
        inputRegistry.inputs.filter { (_, def) -> def.required }.keys
    }

    abstract val usesString: String

    fun toInputsYaml(): Map<String, InputYaml>? =
        dsl.yaml.toInputsYaml(inputRegistry.inputs)

    fun toSecretsYaml(): Map<String, SecretYaml>? =
        _secrets.takeIf { it.isNotEmpty() }?.mapValues { (_, secret) ->
            SecretYaml(description = secret.description, required = secret.required)
        }

    fun toWorkflowCallTrigger(): WorkflowCall {
        val secretsMap = _secrets.takeIf { it.isNotEmpty() }?.toMap()
        return WorkflowCall(
            secrets = secretsMap,
            _customArguments = mapOf("inputs" to inputsAsRawMap()),
        )
    }

    protected inline fun <B : ReusableWorkflowJobBuilder> buildJob(
        id: String,
        crossinline builderFactory: () -> B,
        block: B.() -> Unit = {},
    ): ReusableWorkflowJobDef {
        val builder = builderFactory()
        builder.block()
        return builder.build(id)
    }

    private fun inputsAsRawMap(): Map<String, Map<String, Any?>> =
        inputRegistry.inputs.mapValues { (_, def) ->
            buildMap {
                put("description", def.description)
                put("type", def.type.yamlName())
                put("required", def.required)
                when (val d = def.default) {
                    is InputDefault.StringDefault  -> put("default", d.value)
                    is InputDefault.BooleanDefault -> put("default", d.value)
                    null                           -> {}
                }
            }
        }
}
```

Key changes:
- `booleanInput()` removed — the Boolean overload of `input()` handles it
- `_secretObjects` list added for `passthroughAllSecrets()` (Task 6)
- `inputDefs` replaces `inputs` (returns `WorkflowInputDef` instead of `WorkflowCall.Input`)
- `toWorkflowCallTrigger()` always uses `_customArguments` path
- `inputsAsRawMap()` uses `when (def.default)` instead of checking two maps

- [ ] **Step 2: Commit**

```bash
git add workflow-dsl/src/main/kotlin/dsl/core/ReusableWorkflow.kt
git commit -m "refactor: rewrite ReusableWorkflow for unified input types"
```

---

## Task 5: Unified Input Type — update AdapterWorkflowBuilder

**Files:**
- Modify: `workflow-dsl/src/main/kotlin/dsl/builder/AdapterWorkflowBuilder.kt`

- [ ] **Step 1: Remove booleanInput(), update build()**

Replace the entire file with:

```kotlin
package dsl.builder

import dsl.core.InputRegistry
import dsl.core.MatrixDef
import dsl.core.MatrixRef
import dsl.core.WorkflowInput
import dsl.yaml.toInputsYaml

class AdapterWorkflowBuilder(private val fileName: String, private val name: String) {
    private val inputRegistry = InputRegistry()
    private val jobs = mutableListOf<ReusableWorkflowJobDef>()

    fun input(
        name: String,
        description: String,
        required: Boolean = false,
        default: String? = null,
    ): WorkflowInput = inputRegistry.input(name, description, required = required, default = default)

    fun input(
        name: String,
        description: String,
        required: Boolean = false,
        default: Boolean? = null,
    ): WorkflowInput = inputRegistry.input(name, description, required = required, default = default)

    fun matrixRef(key: String) = MatrixRef(key)

    fun matrix(vararg entries: Pair<String, String>) = MatrixDef(mapOf(*entries))

    fun registerJob(job: ReusableWorkflowJobDef) {
        jobs += job
    }

    fun build(): AdapterWorkflow = AdapterWorkflow(
        fileName = fileName,
        workflowName = name,
        inputsYaml = toInputsYaml(inputRegistry.inputs),
        jobs = jobs.toList(),
    )
}

fun adapterWorkflow(
    fileName: String,
    name: String,
    block: AdapterWorkflowBuilder.() -> Unit,
): AdapterWorkflow {
    val builder = AdapterWorkflowBuilder(fileName, name)
    builder.block()
    return builder.build()
}
```

Key change: `booleanInput()` replaced by Boolean overload of `input()`. `build()` calls `toInputsYaml(inputRegistry.inputs)` with single argument.

- [ ] **Step 2: Update ReleaseAdapters.kt call site**

In `src/main/kotlin/workflows/adapters/release/ReleaseAdapters.kt`, line 14, change `booleanInput(` to `input(`:

```kotlin
        val draft = input("draft", description = "Create release as draft (default true for apps)", default = true)
```

This is the only call site for `booleanInput()` in adapter code.

- [ ] **Step 3: Update ReleaseWorkflow.kt call site**

In `src/main/kotlin/workflows/base/ReleaseWorkflow.kt`, line 21, change `booleanInput(` to `input(`:

```kotlin
    val draft = input("draft", "Create release as draft", default = false)
```

This is the only call site for `booleanInput()` in base workflow code.

- [ ] **Step 4: Verify full compilation and YAML generation**

```bash
./gradlew build && ./gradlew run
```

Expected: BUILD SUCCESSFUL + all 19 YAML files generated in `.github/workflows/`.

- [ ] **Step 5: Verify YAML output matches baseline**

```bash
diff -r .github/workflows /tmp/ci-workflows-baseline
```

Expected: No differences (or only whitespace/ordering changes that are semantically equivalent). The `toWorkflowCallTrigger()` now always uses `_customArguments`, which may change formatting of base workflow YAML files that previously used the non-custom path. If differences appear, verify they are semantically correct by inspecting the YAML.

- [ ] **Step 6: Commit**

```bash
git add workflow-dsl/src/main/kotlin/dsl/builder/AdapterWorkflowBuilder.kt \
       src/main/kotlin/workflows/adapters/release/ReleaseAdapters.kt \
       src/main/kotlin/workflows/base/ReleaseWorkflow.kt \
       .github/workflows/
git commit -m "refactor: complete unified input type — remove booleanInput(), single input() with overloads"
```

---

## Task 6: passthroughAllSecrets() — use stored WorkflowSecret refs

**Files:**
- Modify: `workflow-dsl/src/main/kotlin/dsl/builder/ReusableWorkflowJobBuilder.kt`

- [ ] **Step 1: Rewrite passthroughAllSecrets()**

In `ReusableWorkflowJobBuilder.kt`, replace lines 43-46:

```kotlin
    fun passthroughAllSecrets() {
        workflow.secrets.forEach { (name, _) ->
            secretsMap[name] = "\${{ secrets.$name }}"
        }
    }
```

with:

```kotlin
    fun passthroughAllSecrets() {
        workflow.secretObjects.forEach { secret ->
            secretsMap[secret.name] = secret.ref.expression
        }
    }
```

- [ ] **Step 2: Verify compilation and YAML generation**

```bash
./gradlew build && ./gradlew run
```

- [ ] **Step 3: Verify YAML output**

```bash
diff -r .github/workflows /tmp/ci-workflows-baseline
```

Expected: No differences (secret expressions are identical).

- [ ] **Step 4: Commit**

```bash
git add workflow-dsl/src/main/kotlin/dsl/builder/ReusableWorkflowJobBuilder.kt
git commit -m "refactor: passthroughAllSecrets() uses stored WorkflowSecret refs"
```

---

## Task 7: SetupAwareJobBuilder base class

**Files:**
- Modify: `workflow-dsl/src/main/kotlin/dsl/builder/ReusableWorkflowJobBuilder.kt`
- Modify: `workflow-dsl/src/main/kotlin/dsl/capability/SetupCapability.kt`

- [ ] **Step 1: Add SetupAwareJobBuilder to ReusableWorkflowJobBuilder.kt**

Add at the end of the file (after `ReusableWorkflowJobDef`):

```kotlin
abstract class SetupAwareJobBuilder(workflow: ReusableWorkflow) :
    ReusableWorkflowJobBuilder(workflow), SetupCapableJobBuilder {
    override var setupAction by stringInput((workflow as SetupCapability).setupAction)
    override var setupParams by stringInput((workflow as SetupCapability).setupParams)
}
```

Add the necessary imports at the top of the file:

```kotlin
import dsl.capability.SetupCapability
import dsl.capability.SetupCapableJobBuilder
```

- [ ] **Step 2: Verify compilation**

```bash
./gradlew :workflow-dsl:compileKotlin
```

Expected: BUILD SUCCESSFUL (new class is additive).

- [ ] **Step 3: Commit**

```bash
git add workflow-dsl/src/main/kotlin/dsl/builder/ReusableWorkflowJobBuilder.kt
git commit -m "refactor: add SetupAwareJobBuilder base class"
```

---

## Task 8: Add generic job() template to ReusableWorkflow

**Files:**
- Modify: `workflow-dsl/src/main/kotlin/dsl/core/ReusableWorkflow.kt`

- [ ] **Step 1: Add job() template method**

Add this method to `ReusableWorkflow` class, after `buildJob()`:

```kotlin
    context(builder: AdapterWorkflowBuilder)
    inline fun <B : ReusableWorkflowJobBuilder> job(
        id: String,
        crossinline factory: () -> B,
        block: B.() -> Unit = {},
    ) {
        builder.registerJob(buildJob(id, factory, block))
    }
```

Add the import at the top:

```kotlin
import dsl.builder.AdapterWorkflowBuilder
```

- [ ] **Step 2: Verify compilation**

```bash
./gradlew :workflow-dsl:compileKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add workflow-dsl/src/main/kotlin/dsl/core/ReusableWorkflow.kt
git commit -m "refactor: add generic job() template to ReusableWorkflow"
```

---

## Task 9: Migrate setup-capable workflows to SetupAwareJobBuilder

**Files:**
- Modify: `src/main/kotlin/workflows/base/CheckWorkflow.kt`
- Modify: `src/main/kotlin/workflows/base/CreateTagWorkflow.kt`
- Modify: `src/main/kotlin/workflows/base/ManualCreateTagWorkflow.kt`
- Modify: `src/main/kotlin/workflows/base/PublishWorkflow.kt`
- Modify: `src/main/kotlin/workflows/base/AppDeployWorkflow.kt`

All 5 files get the same treatment. For each file:

1. Change `JobBuilder` to extend `SetupAwareJobBuilder(ThisWorkflow)` instead of `ReusableWorkflowJobBuilder(ThisWorkflow), SetupCapableJobBuilder`
2. Remove `override var setupAction` and `override var setupParams` lines
3. Replace `job()` body with one-liner delegating to base
4. Remove unused imports (`stringInput`, `SetupCapableJobBuilder`)

- [ ] **Step 1: Migrate CheckWorkflow.kt**

Replace lines 19-28 with:

```kotlin
    class JobBuilder : SetupAwareJobBuilder(CheckWorkflow) {
        var checkCommand by refInput(CheckWorkflow.checkCommand)
    }

    context(builder: AdapterWorkflowBuilder)
    fun job(id: String, block: JobBuilder.() -> Unit = {}) = job(id, ::JobBuilder, block)
```

Update imports: remove `stringInput`, `SetupCapableJobBuilder`. Add `SetupAwareJobBuilder`:

```kotlin
import dsl.builder.SetupAwareJobBuilder
```

- [ ] **Step 2: Migrate CreateTagWorkflow.kt**

Replace lines 32-44 with:

```kotlin
    class JobBuilder : SetupAwareJobBuilder(CreateTagWorkflow) {
        var checkCommand by refInput(CreateTagWorkflow.checkCommand)
        var defaultBump by refInput(CreateTagWorkflow.defaultBump)
        var tagPrefix by refInput(CreateTagWorkflow.tagPrefix)
        var releaseBranches by refInput(CreateTagWorkflow.releaseBranches)
    }

    context(builder: AdapterWorkflowBuilder)
    fun job(id: String, block: JobBuilder.() -> Unit = {}) = job(id, ::JobBuilder, block)
```

Update imports: remove `stringInput`, `SetupCapableJobBuilder`. Add `SetupAwareJobBuilder`.

- [ ] **Step 3: Migrate ManualCreateTagWorkflow.kt**

Replace lines 29-39 with:

```kotlin
    class JobBuilder : SetupAwareJobBuilder(ManualCreateTagWorkflow) {
        var tagVersion by refInput(ManualCreateTagWorkflow.tagVersion)
        var tagPrefix by refInput(ManualCreateTagWorkflow.tagPrefix)
        var checkCommand by refInput(ManualCreateTagWorkflow.checkCommand)
    }

    context(builder: AdapterWorkflowBuilder)
    fun job(id: String, block: JobBuilder.() -> Unit = {}) = job(id, ::JobBuilder, block)
```

Update imports: remove `stringInput`, `SetupCapableJobBuilder`. Add `SetupAwareJobBuilder`.

- [ ] **Step 4: Migrate PublishWorkflow.kt**

Replace lines 27-36 with:

```kotlin
    class JobBuilder : SetupAwareJobBuilder(PublishWorkflow) {
        var publishCommand by refInput(PublishWorkflow.publishCommand)
    }

    context(builder: AdapterWorkflowBuilder)
    fun job(id: String, block: JobBuilder.() -> Unit = {}) = job(id, ::JobBuilder, block)
```

Update imports: remove `stringInput`, `SetupCapableJobBuilder`. Add `SetupAwareJobBuilder`.

- [ ] **Step 5: Migrate AppDeployWorkflow.kt**

Replace lines 20-29 with:

```kotlin
    class JobBuilder : SetupAwareJobBuilder(AppDeployWorkflow) {
        var deployCommand by refInput(AppDeployWorkflow.deployCommand)
        var tag by refInput(AppDeployWorkflow.tag)
    }

    context(builder: AdapterWorkflowBuilder)
    fun job(id: String, block: JobBuilder.() -> Unit = {}) = job(id, ::JobBuilder, block)
```

Update imports: remove `stringInput`, `SetupCapableJobBuilder`. Add `SetupAwareJobBuilder`.

- [ ] **Step 6: Verify compilation and YAML generation**

```bash
./gradlew build && ./gradlew run
```

- [ ] **Step 7: Verify YAML output**

```bash
diff -r .github/workflows /tmp/ci-workflows-baseline
```

Expected: No differences.

- [ ] **Step 8: Commit**

```bash
git add src/main/kotlin/workflows/base/CheckWorkflow.kt \
       src/main/kotlin/workflows/base/CreateTagWorkflow.kt \
       src/main/kotlin/workflows/base/ManualCreateTagWorkflow.kt \
       src/main/kotlin/workflows/base/PublishWorkflow.kt \
       src/main/kotlin/workflows/base/AppDeployWorkflow.kt
git commit -m "refactor: migrate 5 setup-capable workflows to SetupAwareJobBuilder"
```

---

## Task 10: Migrate non-setup workflows to use job() one-liner

**Files:**
- Modify: `src/main/kotlin/workflows/base/ReleaseWorkflow.kt`
- Modify: `src/main/kotlin/workflows/base/ConventionalCommitCheckWorkflow.kt`
- Modify: `src/main/kotlin/workflows/base/LabelerWorkflow.kt`

- [ ] **Step 1: Migrate ReleaseWorkflow.kt**

Replace lines 28-31:

```kotlin
    context(builder: AdapterWorkflowBuilder)
    fun job(id: String, block: JobBuilder.() -> Unit = {}) {
        builder.registerJob(buildJob(id, ::JobBuilder, block))
    }
```

with:

```kotlin
    context(builder: AdapterWorkflowBuilder)
    fun job(id: String, block: JobBuilder.() -> Unit = {}) = job(id, ::JobBuilder, block)
```

- [ ] **Step 2: Migrate ConventionalCommitCheckWorkflow.kt**

Replace lines 17-19:

```kotlin
    context(builder: AdapterWorkflowBuilder)
    fun job(id: String, block: JobBuilder.() -> Unit = {}) {
        builder.registerJob(buildJob(id, ::JobBuilder, block))
    }
```

with:

```kotlin
    context(builder: AdapterWorkflowBuilder)
    fun job(id: String, block: JobBuilder.() -> Unit = {}) = job(id, ::JobBuilder, block)
```

- [ ] **Step 3: Migrate LabelerWorkflow.kt**

Replace lines 23-25:

```kotlin
    context(builder: AdapterWorkflowBuilder)
    fun job(id: String, block: JobBuilder.() -> Unit = {}) {
        builder.registerJob(buildJob(id, ::JobBuilder, block))
    }
```

with:

```kotlin
    context(builder: AdapterWorkflowBuilder)
    fun job(id: String, block: JobBuilder.() -> Unit = {}) = job(id, ::JobBuilder, block)
```

- [ ] **Step 4: Verify compilation and YAML generation**

```bash
./gradlew build && ./gradlew run
```

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/workflows/base/ReleaseWorkflow.kt \
       src/main/kotlin/workflows/base/ConventionalCommitCheckWorkflow.kt \
       src/main/kotlin/workflows/base/LabelerWorkflow.kt
git commit -m "refactor: migrate 3 non-setup workflows to job() one-liner"
```

---

## Task 11: Secret Groups

**Files:**
- Modify: `workflow-dsl/src/main/kotlin/dsl/builder/ReusableWorkflowJobBuilder.kt`
- Modify: `src/main/kotlin/workflows/base/PublishWorkflow.kt`
- Modify: `src/main/kotlin/workflows/adapters/release/ReleaseAdapters.kt`

- [ ] **Step 1: Add passthroughSecrets(List) overload**

In `ReusableWorkflowJobBuilder.kt`, add after the existing `passthroughSecrets(vararg)` method (after line 41):

```kotlin
    fun passthroughSecrets(secrets: List<WorkflowSecret>) {
        secrets.forEach { secret ->
            secretsMap[secret.name] = secret.ref.expression
        }
    }
```

- [ ] **Step 2: Add secret groups to PublishWorkflow**

In `PublishWorkflow.kt`, add after `gradlePublishSecret` (after line 25):

```kotlin
    val mavenSecrets = listOf(
        mavenSonatypeUsername, mavenSonatypeToken,
        mavenSonatypeSigningKeyId, mavenSonatypeSigningPubKeyAsciiArmored,
        mavenSonatypeSigningKeyAsciiArmored, mavenSonatypeSigningPassword,
    )
    val gradlePortalSecrets = listOf(gradlePublishKey, gradlePublishSecret)
```

- [ ] **Step 3: Update ReleaseAdapters to use secret groups**

In `ReleaseAdapters.kt`, replace lines 31-38:

```kotlin
    ) {
        passthroughSecrets(
            PublishWorkflow.mavenSonatypeUsername,
            PublishWorkflow.mavenSonatypeToken,
            PublishWorkflow.mavenSonatypeSigningKeyId,
            PublishWorkflow.mavenSonatypeSigningPubKeyAsciiArmored,
            PublishWorkflow.mavenSonatypeSigningKeyAsciiArmored,
            PublishWorkflow.mavenSonatypeSigningPassword,
        )
    }
```

with:

```kotlin
    ) {
        passthroughSecrets(PublishWorkflow.mavenSecrets)
    }
```

- [ ] **Step 4: Verify compilation and YAML generation**

```bash
./gradlew build && ./gradlew run
```

- [ ] **Step 5: Verify YAML output**

```bash
diff -r .github/workflows /tmp/ci-workflows-baseline
```

Expected: No differences.

- [ ] **Step 6: Commit**

```bash
git add workflow-dsl/src/main/kotlin/dsl/builder/ReusableWorkflowJobBuilder.kt \
       src/main/kotlin/workflows/base/PublishWorkflow.kt \
       src/main/kotlin/workflows/adapters/release/ReleaseAdapters.kt
git commit -m "refactor: add secret groups — passthroughSecrets(List) overload"
```

---

## Task 12: EcosystemConfig — replace ToolTagConfig

**Files:**
- Modify: `src/main/kotlin/config/ToolTagConfig.kt` (rename to `EcosystemConfig.kt`)
- Modify: `src/main/kotlin/workflows/adapters/tag/CreateTagAdapters.kt`
- Modify: `src/main/kotlin/workflows/adapters/tag/ManualCreateTagAdapters.kt`

- [ ] **Step 1: Replace ToolTagConfig.kt with EcosystemConfig.kt**

Delete `src/main/kotlin/config/ToolTagConfig.kt` and create `src/main/kotlin/config/EcosystemConfig.kt`:

```kotlin
package config

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
```

- [ ] **Step 2: Update CreateTagAdapters.kt**

Replace entire file with:

```kotlin
package workflows.adapters.tag

import config.DEFAULT_RELEASE_BRANCHES
import config.EcosystemConfig
import config.GO
import config.GRADLE
import dsl.builder.AdapterWorkflow
import dsl.builder.adapterWorkflow
import workflows.base.CreateTagWorkflow
import workflows.support.setup

object CreateTagAdapters {
    val gradle = ecosystemCreateTag("gradle-create-tag.yml", "Gradle Create Tag", GRADLE)
    val go = ecosystemCreateTag("go-create-tag.yml", "Go Create Tag", GO)
}

fun ecosystemCreateTag(fileName: String, name: String, eco: EcosystemConfig): AdapterWorkflow =
    adapterWorkflow(fileName, name) {
        val version = input(eco.tool.versionKey, description = eco.tool.versionDescription, default = eco.tool.defaultVersion)
        val checkCommand = input(eco.checkCommandName, description = eco.checkCommandDescription, default = eco.defaultCheckCommand)
        val defaultBump = input("default-bump", description = "Default version bump type (major, minor, patch)", default = "patch")
        val tagPrefix = input("tag-prefix", description = "Prefix for the tag", default = eco.defaultTagPrefix)
        val releaseBranches = input("release-branches", description = "Comma-separated branch patterns for releases", default = DEFAULT_RELEASE_BRANCHES)

        CreateTagWorkflow.job("create-tag") {
            setup(eco.tool, version)
            CreateTagWorkflow.checkCommand from checkCommand
            CreateTagWorkflow.defaultBump from defaultBump
            CreateTagWorkflow.tagPrefix from tagPrefix
            CreateTagWorkflow.releaseBranches from releaseBranches
            passthroughAllSecrets()
        }
    }
```

- [ ] **Step 3: Update ManualCreateTagAdapters.kt**

Replace entire file with:

```kotlin
package workflows.adapters.tag

import config.EcosystemConfig
import config.GO
import config.GRADLE
import dsl.builder.AdapterWorkflow
import dsl.builder.adapterWorkflow
import workflows.base.ManualCreateTagWorkflow
import workflows.support.setup

object ManualCreateTagAdapters {
    val gradle = ecosystemManualCreateTag("gradle-manual-create-tag.yml", "Gradle Manual Create Tag", GRADLE)
    val go = ecosystemManualCreateTag("go-manual-create-tag.yml", "Go Manual Create Tag", GO)
}

fun ecosystemManualCreateTag(fileName: String, name: String, eco: EcosystemConfig): AdapterWorkflow =
    adapterWorkflow(fileName, name) {
        val tagVersion = input("tag-version", description = "Version to tag (e.g. 1.2.3)", required = true)
        val version = input(eco.tool.versionKey, description = eco.tool.versionDescription, default = eco.tool.defaultVersion)
        val checkCommand = input(eco.checkCommandName, description = eco.checkCommandDescription, default = eco.defaultCheckCommand)
        val tagPrefix = input("tag-prefix", description = "Prefix for the tag", default = eco.defaultTagPrefix)

        ManualCreateTagWorkflow.job("manual-tag") {
            ManualCreateTagWorkflow.tagVersion from tagVersion
            ManualCreateTagWorkflow.tagPrefix from tagPrefix
            setup(eco.tool, version)
            ManualCreateTagWorkflow.checkCommand from checkCommand
            passthroughAllSecrets()
        }
    }
```

- [ ] **Step 4: Verify compilation and YAML generation**

```bash
./gradlew build && ./gradlew run
```

- [ ] **Step 5: Verify YAML output**

```bash
diff -r .github/workflows /tmp/ci-workflows-baseline
```

Expected: No differences.

- [ ] **Step 6: Commit**

```bash
git rm src/main/kotlin/config/ToolTagConfig.kt
git add src/main/kotlin/config/EcosystemConfig.kt \
       src/main/kotlin/workflows/adapters/tag/CreateTagAdapters.kt \
       src/main/kotlin/workflows/adapters/tag/ManualCreateTagAdapters.kt
git commit -m "refactor: replace ToolTagConfig with EcosystemConfig, extract ecosystem factories"
```

---

## Task 13: Minor improvements — SetupAction, SetupTool

**Files:**
- Modify: `src/main/kotlin/actions/SetupAction.kt`
- Modify: `src/main/kotlin/config/SetupTool.kt`
- Modify: `src/main/kotlin/workflows/support/SetupSteps.kt`

- [ ] **Step 1: SetupAction — use buildMap**

In `SetupAction.kt`, replace lines 13-17:

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

- [ ] **Step 2: SetupTool — remove MatrixRefExpr overload**

In `SetupTool.kt`, remove lines 16-17:

```kotlin
    fun toParamsJson(versionExpr: MatrixRefExpr): String =
        """{"$versionKey": "${versionExpr.expression}"}"""
```

Also remove the import on line 3:

```kotlin
import dsl.core.MatrixRefExpr
```

- [ ] **Step 3: Update SetupSteps.kt — pass expression explicitly**

In `SetupSteps.kt`, replace lines 27-28:

```kotlin
fun SetupCapableJobBuilder.setup(tool: SetupTool, versionExpr: MatrixRefExpr) {
    applySetup(tool.id, tool.toParamsJson(versionExpr))
}
```

with:

```kotlin
fun SetupCapableJobBuilder.setup(tool: SetupTool, versionExpr: MatrixRefExpr) {
    applySetup(tool.id, tool.toParamsJson(versionExpr.expression))
}
```

- [ ] **Step 4: Verify compilation and YAML generation**

```bash
./gradlew build && ./gradlew run
```

- [ ] **Step 5: Verify YAML output**

```bash
diff -r .github/workflows /tmp/ci-workflows-baseline
```

Expected: No differences.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/actions/SetupAction.kt \
       src/main/kotlin/config/SetupTool.kt \
       src/main/kotlin/workflows/support/SetupSteps.kt
git commit -m "refactor: minor improvements — buildMap in SetupAction, remove MatrixRefExpr overload"
```

---

## Task 14: Final verification and cleanup

- [ ] **Step 1: Full clean build and generation**

```bash
./gradlew clean build run
```

Expected: BUILD SUCCESSFUL + all YAML files generated.

- [ ] **Step 2: Final diff against baseline**

```bash
diff -r .github/workflows /tmp/ci-workflows-baseline
```

Review any differences. If YAML changed (e.g., due to `toWorkflowCallTrigger()` always using `_customArguments`), verify the generated YAML is valid and semantically correct.

- [ ] **Step 3: If YAML changed, commit generated files**

```bash
git add .github/workflows/
git commit -m "chore: regenerate workflow YAML after refactoring"
```

- [ ] **Step 4: Clean up baseline**

```bash
rm -rf /tmp/ci-workflows-baseline
```
