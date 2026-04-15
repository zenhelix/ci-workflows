# Deep Architecture Refactoring — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor ci-workflows into cleanly separated layers with a unified generation interface, composable capabilities, and logical package structure — while keeping generated YAML semantically identical.

**Architecture:** The refactoring proceeds in 7 tasks ordered by dependency. Tasks 1-3 restructure the `workflow-dsl` module (core types, builders, capabilities). Task 4 creates the unified interface and new `ProjectWorkflow` in the main module. Task 5 migrates all 8 base workflows. Task 6 migrates all adapter workflows. Task 7 rewires `Generate.kt` and verifies YAML identity.

**Tech Stack:** Kotlin 2.3.20, github-workflows-kt 3.7.0, kaml 0.104.0, kotlinx-serialization, Gradle

---

### Task 1: Extract value types and restructure workflow-dsl/core

Move `InputRef`, `SecretRef`, `WorkflowInput`, `WorkflowSecret` out of `ReusableWorkflow.kt` into their own file. Move core files into `dsl/core/` package. Move `InputsYamlMapper.kt` into `dsl/yaml/`.

**Files:**
- Create: `workflow-dsl/src/main/kotlin/dsl/core/WorkflowInput.kt`
- Modify: `workflow-dsl/src/main/kotlin/dsl/ReusableWorkflow.kt` → move to `workflow-dsl/src/main/kotlin/dsl/core/ReusableWorkflow.kt`
- Modify: `workflow-dsl/src/main/kotlin/dsl/InputRegistry.kt` → move to `workflow-dsl/src/main/kotlin/dsl/core/InputRegistry.kt`
- Modify: `workflow-dsl/src/main/kotlin/dsl/MatrixDef.kt` → move to `workflow-dsl/src/main/kotlin/dsl/core/MatrixDef.kt`
- Modify: `workflow-dsl/src/main/kotlin/dsl/InputsYamlMapper.kt` → move to `workflow-dsl/src/main/kotlin/dsl/yaml/InputsYamlMapper.kt`
- Delete: original files at old locations after moving

- [ ] **Step 1: Create `dsl/core/WorkflowInput.kt`**

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
```

- [ ] **Step 2: Move `ReusableWorkflow.kt` to `dsl/core/` and remove extracted types**

Move file to `workflow-dsl/src/main/kotlin/dsl/core/ReusableWorkflow.kt`. Update package to `dsl.core`. Remove `InputRef`, `SecretRef`, `WorkflowInput`, `WorkflowSecret` classes (now in `WorkflowInput.kt`). Keep all imports working — add `import dsl.yaml.InputYaml` stays, add no new imports since `WorkflowInput`/`WorkflowSecret` are in same package.

```kotlin
package dsl.core

import dsl.yaml.InputYaml
import dsl.yaml.SecretYaml
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall

abstract class ReusableWorkflow(val fileName: String) {
    private val inputRegistry = InputRegistry()
    private val _secrets = mutableMapOf<String, WorkflowCall.Secret>()

    protected fun input(
        name: String,
        description: String,
        required: Boolean = false,
        type: WorkflowCall.Type = WorkflowCall.Type.String,
        default: String? = null,
    ): WorkflowInput = inputRegistry.input(name, description, required, type, default)

    protected fun booleanInput(
        name: String,
        description: String,
        required: Boolean = false,
        default: Boolean? = null,
    ): WorkflowInput = inputRegistry.booleanInput(name, description, required, default)

    protected fun secret(
        name: String,
        description: String,
        required: Boolean = true,
    ): WorkflowSecret {
        _secrets[name] = WorkflowCall.Secret(description, required)
        return WorkflowSecret(name)
    }

    val inputs: Map<String, WorkflowCall.Input> get() = inputRegistry.inputs
    val secrets: Map<String, WorkflowCall.Secret> get() = _secrets
    val requiredInputNames: Set<String> by lazy {
        inputRegistry.inputs.filter { (_, input) -> input.required }.keys
    }

    abstract val usesString: String

    fun toInputsYaml(): Map<String, InputYaml>? =
        dsl.yaml.toInputsYaml(inputRegistry.inputs, inputRegistry.booleanDefaults)

    fun toSecretsYaml(): Map<String, SecretYaml>? =
        _secrets.takeIf { it.isNotEmpty() }?.mapValues { (_, secret) ->
            SecretYaml(description = secret.description, required = secret.required)
        }

    fun toWorkflowCallTrigger(): WorkflowCall {
        val secretsMap = _secrets.takeIf { it.isNotEmpty() }?.toMap()
        return if (inputRegistry.booleanDefaults.isEmpty()) {
            WorkflowCall(inputs = inputRegistry.inputs.toMap(), secrets = secretsMap)
        } else {
            WorkflowCall(
                secrets = secretsMap,
                _customArguments = mapOf("inputs" to inputsAsRawMap()),
            )
        }
    }

    protected inline fun <B : dsl.builder.ReusableWorkflowJobBuilder> buildJob(
        id: String,
        crossinline builderFactory: () -> B,
        block: B.() -> Unit = {},
    ): dsl.builder.ReusableWorkflowJobDef {
        val builder = builderFactory()
        builder.block()
        return builder.build(id)
    }

    private fun inputsAsRawMap(): Map<String, Map<String, Any?>> =
        inputRegistry.inputs.mapValues { (name, input) ->
            buildMap {
                put("description", input.description)
                put("type", input.type.name.lowercase())
                put("required", input.required)
                inputRegistry.booleanDefaults[name]?.let { put("default", it) }
                    ?: input.default?.let { put("default", it) }
            }
        }
}
```

- [ ] **Step 3: Move `InputRegistry.kt` to `dsl/core/`**

Move file to `workflow-dsl/src/main/kotlin/dsl/core/InputRegistry.kt`. Update package to `dsl.core`.

```kotlin
package dsl.core

import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall

class InputRegistry {
    private val _inputs = linkedMapOf<String, WorkflowCall.Input>()
    private val _booleanDefaults = mutableMapOf<String, Boolean>()

    val inputs: Map<String, WorkflowCall.Input> get() = _inputs
    val booleanDefaults: Map<String, Boolean> get() = _booleanDefaults

    fun input(
        name: String,
        description: String,
        required: Boolean = false,
        type: WorkflowCall.Type = WorkflowCall.Type.String,
        default: String? = null,
    ): WorkflowInput {
        _inputs[name] = WorkflowCall.Input(description, required, type, default)
        return WorkflowInput(name)
    }

    fun booleanInput(
        name: String,
        description: String,
        required: Boolean = false,
        default: Boolean? = null,
    ): WorkflowInput {
        _inputs[name] = WorkflowCall.Input(description, required, WorkflowCall.Type.Boolean, null)
        default?.let { _booleanDefaults[name] = it }
        return WorkflowInput(name)
    }
}
```

- [ ] **Step 4: Move `MatrixDef.kt` to `dsl/core/`**

Move file to `workflow-dsl/src/main/kotlin/dsl/core/MatrixDef.kt`. Update package to `dsl.core`.

```kotlin
package dsl.core

@JvmInline
value class MatrixRefExpr(val expression: String)

class MatrixRef(val key: String) {
    val ref: MatrixRefExpr get() = MatrixRefExpr("\${{ matrix.$key }}")
}

data class MatrixDef(val entries: Map<String, String>)
```

- [ ] **Step 5: Move `InputsYamlMapper.kt` to `dsl/yaml/`**

Move file to `workflow-dsl/src/main/kotlin/dsl/yaml/InputsYamlMapper.kt`. Update package to `dsl.yaml`. Update import for `WorkflowInput` types (no longer needed since it uses `WorkflowCall.Input` from the library, not our `WorkflowInput`).

```kotlin
package dsl.yaml

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

- [ ] **Step 6: Delete old files at original locations**

Delete:
- `workflow-dsl/src/main/kotlin/dsl/ReusableWorkflow.kt`
- `workflow-dsl/src/main/kotlin/dsl/InputRegistry.kt`
- `workflow-dsl/src/main/kotlin/dsl/MatrixDef.kt`
- `workflow-dsl/src/main/kotlin/dsl/InputsYamlMapper.kt`

- [ ] **Step 7: Build to verify**

Run: `./gradlew build 2>&1 | tail -5`
Expected: Compilation errors in downstream files that still import from old packages. This is expected — we fix these in Task 2. If there are errors WITHIN the moved files themselves, fix them now.

- [ ] **Step 8: Commit**

```bash
git add -A workflow-dsl/src/main/kotlin/dsl/
git commit -m "refactor: extract WorkflowInput types and restructure dsl into core/yaml packages"
```

---

### Task 2: Restructure workflow-dsl/builder and unify property delegates

Move builder classes into `dsl/builder/` package. Replace `StringInputProperty`/`RefInputProperty` with unified generic `InputProperty<T>`.

**Files:**
- Create: `workflow-dsl/src/main/kotlin/dsl/builder/InputProperty.kt`
- Modify: `workflow-dsl/src/main/kotlin/dsl/ReusableWorkflowJobBuilder.kt` → move to `workflow-dsl/src/main/kotlin/dsl/builder/ReusableWorkflowJobBuilder.kt`
- Modify: `workflow-dsl/src/main/kotlin/dsl/AdapterWorkflowBuilder.kt` → move to `workflow-dsl/src/main/kotlin/dsl/builder/AdapterWorkflowBuilder.kt`
- Modify: `workflow-dsl/src/main/kotlin/dsl/AdapterWorkflow.kt` → move to `workflow-dsl/src/main/kotlin/dsl/builder/AdapterWorkflow.kt`
- Delete: original files at old locations

- [ ] **Step 1: Create `dsl/builder/InputProperty.kt`**

```kotlin
package dsl.builder

import dsl.core.InputRef
import dsl.core.WorkflowInput
import kotlin.reflect.KProperty

class InputProperty<T>(
    private val input: WorkflowInput,
    private val get: (String) -> T,
    private val set: (T) -> String,
) {
    operator fun getValue(builder: ReusableWorkflowJobBuilder, property: KProperty<*>): T =
        get(builder.getInput(input))

    operator fun setValue(builder: ReusableWorkflowJobBuilder, property: KProperty<*>, value: T) {
        builder.setInput(input, set(value))
    }
}

fun stringInput(input: WorkflowInput) = InputProperty<String>(input, { it }, { it })
fun refInput(input: WorkflowInput) = InputProperty<InputRef>(input, ::InputRef, InputRef::expression)
```

- [ ] **Step 2: Move `ReusableWorkflowJobBuilder.kt` to `dsl/builder/`**

Move file. Update package to `dsl.builder`. Remove `StringInputProperty`, `RefInputProperty`, `stringInput()`, `refInput()` (now in `InputProperty.kt`). Update imports.

```kotlin
package dsl.builder

import dsl.core.InputRef
import dsl.core.MatrixDef
import dsl.core.ReusableWorkflow
import dsl.core.WorkflowInput
import dsl.core.WorkflowSecret
import dsl.yaml.JobYaml
import dsl.yaml.NeedsYaml
import dsl.yaml.StrategyYaml

abstract class ReusableWorkflowJobBuilder(private val workflow: ReusableWorkflow) {
    private val withMap = mutableMapOf<String, String>()
    private val secretsMap = mutableMapOf<String, String>()
    private var needsList = emptyList<String>()
    private var matrixDef: MatrixDef? = null

    fun getInput(input: WorkflowInput): String =
        withMap[input.name] ?: error("Input '${input.name}' has not been set")

    fun setInput(input: WorkflowInput, value: String) {
        withMap[input.name] = value
    }

    fun setInput(input: WorkflowInput, value: InputRef) {
        withMap[input.name] = value.expression
    }

    fun needs(vararg jobIds: String) {
        needsList = jobIds.toList()
    }

    fun strategy(matrix: MatrixDef) {
        matrixDef = matrix
    }

    fun passthroughSecrets(vararg secrets: WorkflowSecret) {
        secrets.forEach { secret ->
            secretsMap[secret.name] = secret.ref.expression
        }
    }

    fun passthroughAllSecrets() {
        workflow.secrets.forEach { (name, _) ->
            secretsMap[name] = "\${{ secrets.$name }}"
        }
    }

    infix fun WorkflowInput.from(source: WorkflowInput) {
        setInput(this, source.ref)
    }

    @PublishedApi
    internal fun build(id: String): ReusableWorkflowJobDef {
        val missingRequired = workflow.requiredInputNames.filter { it !in withMap }
        require(missingRequired.isEmpty()) {
            "Job '$id' using '${workflow.fileName}' is missing required inputs: $missingRequired"
        }

        return ReusableWorkflowJobDef(
            id = id,
            uses = workflow,
            needs = needsList,
            with = withMap.toMap(),
            secrets = secretsMap.toMap(),
            strategy = matrixDef,
        )
    }
}

data class ReusableWorkflowJobDef(
    val id: String,
    val uses: ReusableWorkflow,
    val needs: List<String> = emptyList(),
    val with: Map<String, String> = emptyMap(),
    val secrets: Map<String, String> = emptyMap(),
    val strategy: MatrixDef? = null,
) {
    fun toJobYaml(): JobYaml = JobYaml(
        needs = NeedsYaml.of(needs),
        strategy = strategy?.let { StrategyYaml(matrix = it.entries) },
        uses = uses.usesString,
        with = with.takeIf { it.isNotEmpty() },
        secrets = secrets.takeIf { it.isNotEmpty() },
    )
}
```

- [ ] **Step 3: Move `AdapterWorkflowBuilder.kt` to `dsl/builder/`**

Move file. Update package to `dsl.builder`. Update imports.

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

    fun booleanInput(
        name: String,
        description: String,
        required: Boolean = false,
        default: Boolean? = null,
    ): WorkflowInput = inputRegistry.booleanInput(name, description, required, default)

    fun matrixRef(key: String) = MatrixRef(key)

    fun matrix(vararg entries: Pair<String, String>) = MatrixDef(mapOf(*entries))

    fun registerJob(job: ReusableWorkflowJobDef) {
        jobs += job
    }

    fun build(): AdapterWorkflow = AdapterWorkflow(
        fileName = fileName,
        workflowName = name,
        inputsYaml = toInputsYaml(inputRegistry.inputs, inputRegistry.booleanDefaults),
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

- [ ] **Step 4: Move `AdapterWorkflow.kt` to `dsl/builder/` and add `GeneratableWorkflow` interface**

Move file. Update package. The `GeneratableWorkflow` interface needs to be accessible from `workflow-dsl` module, so define it here. Update imports.

```kotlin
package dsl.builder

import dsl.yaml.AdapterWorkflowYaml
import dsl.yaml.InputYaml
import dsl.yaml.SecretYaml
import dsl.yaml.TriggerYaml
import dsl.yaml.WorkflowCallBodyYaml
import dsl.yaml.adapterWorkflowYaml
import java.io.File

interface GeneratableWorkflow {
    val fileName: String
    fun generate(outputDir: File)
}

private val QUOTED_MAP_KEY = Regex("""^(\s*)'([^']*?)'\s*:""", RegexOption.MULTILINE)

private fun unquoteYamlMapKeys(yaml: String): String =
    yaml.replace(QUOTED_MAP_KEY, "$1$2:")

class AdapterWorkflow(
    override val fileName: String,
    val workflowName: String,
    private val inputsYaml: Map<String, InputYaml>?,
    private val jobs: List<ReusableWorkflowJobDef>,
) : GeneratableWorkflow {
    override fun generate(outputDir: File) {
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
                for (name in job.secrets.keys) {
                    putIfAbsent(name, SecretYaml(
                        description = job.uses.secrets[name]?.description ?: name,
                        required = true,
                    ))
                }
            }
        }.takeIf { it.isNotEmpty() }
}
```

- [ ] **Step 5: Delete old files at original locations**

Delete:
- `workflow-dsl/src/main/kotlin/dsl/ReusableWorkflowJobBuilder.kt`
- `workflow-dsl/src/main/kotlin/dsl/AdapterWorkflowBuilder.kt`
- `workflow-dsl/src/main/kotlin/dsl/AdapterWorkflow.kt`
- `workflow-dsl/src/main/kotlin/dsl/SetupConfigurable.kt` (will be replaced by capability in Task 3)

- [ ] **Step 6: Build workflow-dsl module to verify**

Run: `./gradlew :workflow-dsl:build 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL (the workflow-dsl module should compile on its own)

- [ ] **Step 7: Commit**

```bash
git add -A workflow-dsl/src/main/kotlin/dsl/
git commit -m "refactor: restructure dsl builders into dsl/builder package and unify property delegates"
```

---

### Task 3: Create SetupCapability in dsl/capability and add SetupTool.entries

Create the composable capability interfaces. Add `entries` companion property to `SetupTool`.

**Files:**
- Create: `workflow-dsl/src/main/kotlin/dsl/capability/SetupCapability.kt`
- Modify: `src/main/kotlin/config/SetupTool.kt`

- [ ] **Step 1: Create `dsl/capability/SetupCapability.kt`**

`SetupCapability` is a contract interface — it declares that a workflow has `setupAction`/`setupParams` inputs. Each workflow that implements it calls `input()` directly (since `input()` is `protected` in `ReusableWorkflow` and accessible only to subclasses). The companion holds shared description constants. `SetupCapableJobBuilder` provides the default `applySetup()` implementation for job builders.

```kotlin
package dsl.capability

import dsl.core.WorkflowInput

interface SetupCapability {
    val setupAction: WorkflowInput
    val setupParams: WorkflowInput

    companion object {
        const val SETUP_ACTION_DESCRIPTION = "Setup action to use: gradle, go, python"
        const val SETUP_PARAMS_DESCRIPTION = """JSON object with setup parameters (e.g. {"java-version": "21"})"""
        const val SETUP_PARAMS_DEFAULT = "{}"
    }
}

interface SetupCapableJobBuilder {
    var setupAction: String
    var setupParams: String

    fun applySetup(action: String, params: String) {
        setupAction = action
        setupParams = params
    }
}
```

- [ ] **Step 2: Add `entries` companion to `SetupTool`**

Modify `src/main/kotlin/config/SetupTool.kt`:

```kotlin
package config

import dsl.core.MatrixRefExpr

sealed class SetupTool(
    val actionName: String,
    val versionKey: String,
    val defaultVersion: String,
    val versionDescription: String,
) {
    val id: String get() = actionName.removePrefix("setup-")

    fun toParamsJson(versionExpr: String): String =
        """{"$versionKey": "$versionExpr"}"""

    fun toParamsJson(versionExpr: MatrixRefExpr): String =
        """{"$versionKey": "${versionExpr.expression}"}"""

    data object Gradle : SetupTool("setup-gradle", "java-version", DEFAULT_JAVA_VERSION, "JDK version to use")
    data object Go : SetupTool("setup-go", "go-version", DEFAULT_GO_VERSION, "Go version to use")
    data object Python : SetupTool("setup-python", "python-version", DEFAULT_PYTHON_VERSION, "Python version to use")

    companion object {
        val entries: List<SetupTool> = listOf(Gradle, Go, Python)
    }
}
```

- [ ] **Step 3: Build to verify**

Run: `./gradlew :workflow-dsl:build 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add workflow-dsl/src/main/kotlin/dsl/capability/ src/main/kotlin/config/SetupTool.kt
git commit -m "refactor: add SetupCapability interfaces and SetupTool.entries"
```

---

### Task 4: Create GeneratableWorkflow, new ProjectWorkflow, and SetupSteps

Create the unified interface in the main module. Rewrite `ProjectWorkflow` to absorb `generate()` boilerplate. Move and update setup helpers.

**Files:**
- Modify: `src/main/kotlin/workflows/core/ProjectWorkflow.kt` → move to `src/main/kotlin/workflows/ProjectWorkflow.kt`
- Create: `src/main/kotlin/workflows/support/SetupSteps.kt`
- Delete: `src/main/kotlin/workflows/helpers/SetupHelpers.kt`
- Delete: `src/main/kotlin/workflows/core/` (directory becomes empty)

Note: `GeneratableWorkflow` is already defined in `dsl/builder/AdapterWorkflow.kt` (Task 2, Step 4). The main module accesses it via dependency on `workflow-dsl`.

- [ ] **Step 1: Rewrite `ProjectWorkflow.kt` at new location**

Delete `src/main/kotlin/workflows/core/ProjectWorkflow.kt`. Create `src/main/kotlin/workflows/ProjectWorkflow.kt`:

```kotlin
package workflows

import config.reusableWorkflow
import dsl.builder.GeneratableWorkflow
import dsl.core.ReusableWorkflow
import io.github.typesafegithub.workflows.domain.Mode
import io.github.typesafegithub.workflows.domain.Permission
import io.github.typesafegithub.workflows.dsl.WorkflowBuilder
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import java.io.File

abstract class ProjectWorkflow(
    override val fileName: String,
    private val workflowName: String,
    private val permissions: Map<Permission, Mode> = mapOf(Permission.Contents to Mode.Read),
) : ReusableWorkflow(fileName), GeneratableWorkflow {

    override val usesString: String = reusableWorkflow(fileName)

    protected abstract fun WorkflowBuilder.implementation()

    override fun generate(outputDir: File) {
        workflow(
            name = workflowName,
            on = listOf(toWorkflowCallTrigger()),
            sourceFile = sourceFile(),
            targetFileName = fileName,
            consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
            permissions = permissions,
        ) {
            implementation()
        }
    }

    private fun sourceFile(): File {
        val className = this::class.simpleName ?: error("Anonymous workflow")
        return File("src/main/kotlin/workflows/base/$className.kt")
    }
}
```

- [ ] **Step 2: Create `support/SetupSteps.kt`**

Create `src/main/kotlin/workflows/support/SetupSteps.kt`:

```kotlin
package workflows.support

import actions.SetupAction
import config.SetupTool
import dsl.capability.SetupCapableJobBuilder
import dsl.core.MatrixRefExpr
import dsl.core.WorkflowInput
import io.github.typesafegithub.workflows.dsl.JobBuilder

fun JobBuilder<*>.conditionalSetupSteps(
    tools: List<SetupTool> = SetupTool.entries,
    fetchDepth: String? = null,
) {
    tools.forEach { tool ->
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

fun SetupCapableJobBuilder.setup(tool: SetupTool, versionExpr: MatrixRefExpr) {
    applySetup(tool.id, tool.toParamsJson(versionExpr))
}

fun SetupCapableJobBuilder.setup(tool: SetupTool, versionRef: String) {
    applySetup(tool.id, tool.toParamsJson(versionRef))
}

fun SetupCapableJobBuilder.setup(tool: SetupTool, versionInput: WorkflowInput) {
    applySetup(tool.id, tool.toParamsJson(versionInput.ref.expression))
}
```

- [ ] **Step 3: Delete old files**

Delete:
- `src/main/kotlin/workflows/core/ProjectWorkflow.kt`
- `src/main/kotlin/workflows/core/` (directory)
- `src/main/kotlin/workflows/helpers/SetupHelpers.kt`
- `src/main/kotlin/workflows/helpers/` (directory)

- [ ] **Step 4: Commit**

```bash
git add -A src/main/kotlin/workflows/
git commit -m "refactor: create new ProjectWorkflow with generate() boilerplate and SetupSteps"
```

---

### Task 5: Migrate all 8 base workflows to new packages and patterns

Move all workflows from `src/main/kotlin/workflows/` to `src/main/kotlin/workflows/base/`. Update each to use `ProjectWorkflow(fileName, name, permissions)`, `SetupCapability`, `SetupCapableJobBuilder`, `implementation()`, and new import paths.

**Files:**
- Move+Modify: All 8 workflow files from `workflows/` to `workflows/base/`
- Delete: original workflow files at `workflows/` level

- [ ] **Step 1: Migrate `CheckWorkflow.kt`**

Create `src/main/kotlin/workflows/base/CheckWorkflow.kt`:

```kotlin
package workflows.base

import dsl.builder.AdapterWorkflowBuilder
import dsl.builder.ReusableWorkflowJobBuilder
import dsl.builder.refInput
import dsl.builder.stringInput
import dsl.capability.SetupCapability
import dsl.capability.SetupCapableJobBuilder
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.dsl.WorkflowBuilder
import workflows.ProjectWorkflow
import workflows.support.conditionalSetupSteps

object CheckWorkflow : ProjectWorkflow("check.yml", "Check"), SetupCapability {
    override val setupAction = input("setup-action", SetupCapability.SETUP_ACTION_DESCRIPTION, required = true)
    override val setupParams = input("setup-params", SetupCapability.SETUP_PARAMS_DESCRIPTION, default = SetupCapability.SETUP_PARAMS_DEFAULT)
    val checkCommand = input("check-command", "Command to run for checking", required = true)

    class JobBuilder : ReusableWorkflowJobBuilder(CheckWorkflow), SetupCapableJobBuilder {
        override var setupAction by stringInput(CheckWorkflow.setupAction)
        override var setupParams by stringInput(CheckWorkflow.setupParams)
        var checkCommand by refInput(CheckWorkflow.checkCommand)
    }

    context(builder: AdapterWorkflowBuilder)
    fun job(id: String, block: JobBuilder.() -> Unit = {}) {
        builder.registerJob(buildJob(id, ::JobBuilder, block))
    }

    override fun WorkflowBuilder.implementation() {
        job(id = "build", name = "Build", runsOn = UbuntuLatest) {
            conditionalSetupSteps()
            run(name = "Run check", command = checkCommand.ref.expression)
        }
    }
}
```

- [ ] **Step 2: Migrate `PublishWorkflow.kt`**

Create `src/main/kotlin/workflows/base/PublishWorkflow.kt`:

```kotlin
package workflows.base

import dsl.builder.AdapterWorkflowBuilder
import dsl.builder.ReusableWorkflowJobBuilder
import dsl.builder.refInput
import dsl.builder.stringInput
import dsl.capability.SetupCapability
import dsl.capability.SetupCapableJobBuilder
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.dsl.WorkflowBuilder
import workflows.ProjectWorkflow
import workflows.support.conditionalSetupSteps

object PublishWorkflow : ProjectWorkflow("publish.yml", "Publish"), SetupCapability {
    override val setupAction = input("setup-action", SetupCapability.SETUP_ACTION_DESCRIPTION, required = true)
    override val setupParams = input("setup-params", SetupCapability.SETUP_PARAMS_DESCRIPTION, default = SetupCapability.SETUP_PARAMS_DEFAULT)
    val publishCommand = input("publish-command", "Command to run for publishing", required = true)
    val mavenSonatypeUsername = secret("MAVEN_SONATYPE_USERNAME", "Maven Central (Sonatype) username", required = false)
    val mavenSonatypeToken = secret("MAVEN_SONATYPE_TOKEN", "Maven Central (Sonatype) token", required = false)
    val mavenSonatypeSigningKeyId = secret("MAVEN_SONATYPE_SIGNING_KEY_ID", "GPG signing key ID", required = false)
    val mavenSonatypeSigningPubKeyAsciiArmored = secret("MAVEN_SONATYPE_SIGNING_PUB_KEY_ASCII_ARMORED", "GPG signing public key (ASCII armored)", required = false)
    val mavenSonatypeSigningKeyAsciiArmored = secret("MAVEN_SONATYPE_SIGNING_KEY_ASCII_ARMORED", "GPG signing private key (ASCII armored)", required = false)
    val mavenSonatypeSigningPassword = secret("MAVEN_SONATYPE_SIGNING_PASSWORD", "GPG signing key passphrase", required = false)
    val gradlePublishKey = secret("GRADLE_PUBLISH_KEY", "Gradle Plugin Portal publish key", required = false)
    val gradlePublishSecret = secret("GRADLE_PUBLISH_SECRET", "Gradle Plugin Portal publish secret", required = false)

    class JobBuilder : ReusableWorkflowJobBuilder(PublishWorkflow), SetupCapableJobBuilder {
        override var setupAction by stringInput(PublishWorkflow.setupAction)
        override var setupParams by stringInput(PublishWorkflow.setupParams)
        var publishCommand by refInput(PublishWorkflow.publishCommand)
    }

    context(builder: AdapterWorkflowBuilder)
    fun job(id: String, block: JobBuilder.() -> Unit = {}) {
        builder.registerJob(buildJob(id, ::JobBuilder, block))
    }

    override fun WorkflowBuilder.implementation() {
        job(id = "publish", name = "Publish", runsOn = UbuntuLatest) {
            conditionalSetupSteps()
            run(
                name = "Publish",
                command = publishCommand.ref.expression,
                env = linkedMapOf(
                    "GRADLE_PUBLISH_KEY" to gradlePublishKey.ref.expression,
                    "GRADLE_PUBLISH_SECRET" to gradlePublishSecret.ref.expression,
                    "ORG_GRADLE_PROJECT_signingKeyId" to mavenSonatypeSigningKeyId.ref.expression,
                    "ORG_GRADLE_PROJECT_signingPublicKey" to mavenSonatypeSigningPubKeyAsciiArmored.ref.expression,
                    "ORG_GRADLE_PROJECT_signingKey" to mavenSonatypeSigningKeyAsciiArmored.ref.expression,
                    "ORG_GRADLE_PROJECT_signingPassword" to mavenSonatypeSigningPassword.ref.expression,
                    "MAVEN_SONATYPE_USERNAME" to mavenSonatypeUsername.ref.expression,
                    "MAVEN_SONATYPE_TOKEN" to mavenSonatypeToken.ref.expression,
                ),
            )
        }
    }
}
```

- [ ] **Step 3: Migrate `ReleaseWorkflow.kt`**

Create `src/main/kotlin/workflows/base/ReleaseWorkflow.kt`:

```kotlin
package workflows.base

import config.DEFAULT_CHANGELOG_CONFIG
import dsl.builder.AdapterWorkflowBuilder
import dsl.builder.ReusableWorkflowJobBuilder
import dsl.builder.refInput
import io.github.typesafegithub.workflows.actions.actions.Checkout
import io.github.typesafegithub.workflows.actions.mikepenz.ReleaseChangelogBuilderAction_Untyped
import io.github.typesafegithub.workflows.actions.softprops.ActionGhRelease
import io.github.typesafegithub.workflows.domain.Mode
import io.github.typesafegithub.workflows.domain.Permission
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.dsl.WorkflowBuilder
import workflows.ProjectWorkflow

object ReleaseWorkflow : ProjectWorkflow(
    "release.yml", "Release",
    permissions = mapOf(Permission.Contents to Mode.Write),
) {
    val changelogConfig = input("changelog-config", "Path to changelog configuration file", default = DEFAULT_CHANGELOG_CONFIG)
    val draft = booleanInput("draft", "Create release as draft", default = false)

    class JobBuilder : ReusableWorkflowJobBuilder(ReleaseWorkflow) {
        var changelogConfig by refInput(ReleaseWorkflow.changelogConfig)
        var draft by refInput(ReleaseWorkflow.draft)
    }

    context(builder: AdapterWorkflowBuilder)
    fun job(id: String, block: JobBuilder.() -> Unit = {}) {
        builder.registerJob(buildJob(id, ::JobBuilder, block))
    }

    override fun WorkflowBuilder.implementation() {
        job(id = "release", name = "GitHub Release", runsOn = UbuntuLatest) {
            uses(name = "Check out", action = Checkout(fetchDepth = Checkout.FetchDepth.Value(0)))
            uses(
                name = "Build Changelog",
                action = ReleaseChangelogBuilderAction_Untyped(
                    configuration_Untyped = changelogConfig.ref.expression,
                    toTag_Untyped = "\${{ github.ref_name }}",
                ),
                id = "changelog",
                env = linkedMapOf("GITHUB_TOKEN" to "\${{ secrets.GITHUB_TOKEN }}"),
            )
            uses(
                name = "Create Release",
                action = ActionGhRelease(
                    body = "\${{ steps.changelog.outputs.changelog }}",
                    name = "\${{ github.ref_name }}",
                    tagName = "\${{ github.ref_name }}",
                    draft_Untyped = draft.ref.expression,
                ),
                env = linkedMapOf("GITHUB_TOKEN" to "\${{ secrets.GITHUB_TOKEN }}"),
            )
        }
    }
}
```

- [ ] **Step 4: Migrate `CreateTagWorkflow.kt`**

Create `src/main/kotlin/workflows/base/CreateTagWorkflow.kt`:

```kotlin
package workflows.base

import actions.CreateAppTokenAction
import actions.GithubTagAction
import config.DEFAULT_RELEASE_BRANCHES
import dsl.builder.AdapterWorkflowBuilder
import dsl.builder.ReusableWorkflowJobBuilder
import dsl.builder.refInput
import dsl.builder.stringInput
import dsl.capability.SetupCapability
import dsl.capability.SetupCapableJobBuilder
import io.github.typesafegithub.workflows.domain.Mode
import io.github.typesafegithub.workflows.domain.Permission
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.dsl.WorkflowBuilder
import workflows.ProjectWorkflow
import workflows.support.conditionalSetupSteps

object CreateTagWorkflow : ProjectWorkflow(
    "create-tag.yml", "Create Tag",
    permissions = mapOf(Permission.Contents to Mode.Write),
), SetupCapability {
    override val setupAction = input("setup-action", SetupCapability.SETUP_ACTION_DESCRIPTION, required = true)
    override val setupParams = input("setup-params", SetupCapability.SETUP_PARAMS_DESCRIPTION, default = SetupCapability.SETUP_PARAMS_DEFAULT)
    val checkCommand = input("check-command", "Validation command to run before tagging", required = true)
    val defaultBump = input("default-bump", "Default version bump type (major, minor, patch)", default = "patch")
    val tagPrefix = input("tag-prefix", "Prefix for the tag (e.g. v)", default = "")
    val releaseBranches = input("release-branches", "Comma-separated branch patterns for releases", default = DEFAULT_RELEASE_BRANCHES)
    val appId = secret("app-id", "GitHub App ID for generating commit token")
    val appPrivateKey = secret("app-private-key", "GitHub App private key for generating commit token")

    class JobBuilder : ReusableWorkflowJobBuilder(CreateTagWorkflow), SetupCapableJobBuilder {
        override var setupAction by stringInput(CreateTagWorkflow.setupAction)
        override var setupParams by stringInput(CreateTagWorkflow.setupParams)
        var checkCommand by refInput(CreateTagWorkflow.checkCommand)
        var defaultBump by refInput(CreateTagWorkflow.defaultBump)
        var tagPrefix by refInput(CreateTagWorkflow.tagPrefix)
        var releaseBranches by refInput(CreateTagWorkflow.releaseBranches)
    }

    context(builder: AdapterWorkflowBuilder)
    fun job(id: String, block: JobBuilder.() -> Unit = {}) {
        builder.registerJob(buildJob(id, ::JobBuilder, block))
    }

    override fun WorkflowBuilder.implementation() {
        job(id = "create_tag", name = "Create Tag", runsOn = UbuntuLatest) {
            conditionalSetupSteps(fetchDepth = "0")
            run(name = "Run validation", command = checkCommand.ref.expression)
            uses(
                name = "Generate App Token",
                action = CreateAppTokenAction(
                    appId = appId.ref.expression,
                    appPrivateKey = appPrivateKey.ref.expression,
                ),
                id = "app-token",
            )
            uses(
                name = "Bump version and push tag",
                action = GithubTagAction(
                    githubToken = "\${{ steps.app-token.outputs.token }}",
                    defaultBump = defaultBump.ref.expression,
                    tagPrefix = tagPrefix.ref.expression,
                    releaseBranches = releaseBranches.ref.expression,
                ),
            )
        }
    }
}
```

- [ ] **Step 5: Migrate `ManualCreateTagWorkflow.kt`**

Create `src/main/kotlin/workflows/base/ManualCreateTagWorkflow.kt`:

```kotlin
package workflows.base

import actions.CreateAppTokenAction
import dsl.builder.AdapterWorkflowBuilder
import dsl.builder.ReusableWorkflowJobBuilder
import dsl.builder.refInput
import dsl.builder.stringInput
import dsl.capability.SetupCapability
import dsl.capability.SetupCapableJobBuilder
import io.github.typesafegithub.workflows.domain.Mode
import io.github.typesafegithub.workflows.domain.Permission
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.dsl.WorkflowBuilder
import workflows.ProjectWorkflow
import workflows.support.conditionalSetupSteps

object ManualCreateTagWorkflow : ProjectWorkflow(
    "manual-create-tag.yml", "Manual Create Tag",
    permissions = mapOf(Permission.Contents to Mode.Write),
), SetupCapability {
    val tagVersion = input("tag-version", "Version to tag (e.g. 1.2.3)", required = true)
    val tagPrefix = input("tag-prefix", "Prefix for the tag (e.g. v)", default = "")
    override val setupAction = input("setup-action", SetupCapability.SETUP_ACTION_DESCRIPTION, required = true)
    override val setupParams = input("setup-params", SetupCapability.SETUP_PARAMS_DESCRIPTION, default = SetupCapability.SETUP_PARAMS_DEFAULT)
    val checkCommand = input("check-command", "Validation command to run before tagging", required = true)
    val appId = secret("app-id", "GitHub App ID for generating commit token")
    val appPrivateKey = secret("app-private-key", "GitHub App private key for generating commit token")

    class JobBuilder : ReusableWorkflowJobBuilder(ManualCreateTagWorkflow), SetupCapableJobBuilder {
        var tagVersion by refInput(ManualCreateTagWorkflow.tagVersion)
        var tagPrefix by refInput(ManualCreateTagWorkflow.tagPrefix)
        override var setupAction by stringInput(ManualCreateTagWorkflow.setupAction)
        override var setupParams by stringInput(ManualCreateTagWorkflow.setupParams)
        var checkCommand by refInput(ManualCreateTagWorkflow.checkCommand)
    }

    context(builder: AdapterWorkflowBuilder)
    fun job(id: String, block: JobBuilder.() -> Unit = {}) {
        builder.registerJob(buildJob(id, ::JobBuilder, block))
    }

    override fun WorkflowBuilder.implementation() {
        job(id = "manual_tag", name = "Manual Tag", runsOn = UbuntuLatest) {
            run(
                name = "Validate version format",
                command = """
                    VERSION="${'$'}{{ inputs.tag-version }}"
                    if [[ ! "${'$'}VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9.]+)?${'$'} ]]; then
                      echo "::error::Version must be in semver format (e.g. 1.2.3 or 1.2.3-rc.1)"
                      exit 1
                    fi
                """.trimIndent(),
            )
            conditionalSetupSteps(fetchDepth = "0")
            run(name = "Run validation", command = checkCommand.ref.expression)
            uses(
                name = "Generate App Token",
                action = CreateAppTokenAction(
                    appId = appId.ref.expression,
                    appPrivateKey = appPrivateKey.ref.expression,
                ),
                id = "app-token",
            )
            run(
                name = "Create and push tag",
                command = """
                    TAG="${'$'}{{ inputs.tag-prefix }}${'$'}{{ inputs.tag-version }}"
                    git config user.name "github-actions[bot]"
                    git config user.email "github-actions[bot]@users.noreply.github.com"
                    git tag -a "${'$'}TAG" -m "Release ${'$'}TAG"
                    git push origin "${'$'}TAG"
                    echo "::notice::Created tag ${'$'}TAG"
                """.trimIndent(),
                env = linkedMapOf("GITHUB_TOKEN" to "\${{ steps.app-token.outputs.token }}"),
            )
        }
    }
}
```

- [ ] **Step 6: Migrate `ConventionalCommitCheckWorkflow.kt`**

Create `src/main/kotlin/workflows/base/ConventionalCommitCheckWorkflow.kt`:

```kotlin
package workflows.base

import dsl.builder.AdapterWorkflowBuilder
import dsl.builder.ReusableWorkflowJobBuilder
import dsl.builder.refInput
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.dsl.WorkflowBuilder
import workflows.ProjectWorkflow

object ConventionalCommitCheckWorkflow : ProjectWorkflow("conventional-commit-check.yml", "Conventional Commit Check") {
    val allowedTypes = input("allowed-types", "Comma-separated list of allowed commit types", default = "feat,fix,refactor,docs,test,chore,perf,ci")

    class JobBuilder : ReusableWorkflowJobBuilder(ConventionalCommitCheckWorkflow) {
        var allowedTypes by refInput(ConventionalCommitCheckWorkflow.allowedTypes)
    }

    context(builder: AdapterWorkflowBuilder)
    fun job(id: String, block: JobBuilder.() -> Unit = {}) {
        builder.registerJob(buildJob(id, ::JobBuilder, block))
    }

    override fun WorkflowBuilder.implementation() {
        job(id = "check-title", name = "Check PR Title", runsOn = UbuntuLatest) {
            run(
                name = "Validate PR title format",
                command = """
                    TYPES_PATTERN=${'$'}(echo "${'$'}ALLOWED_TYPES" | tr ',' '|')
                    PATTERN="^(${'$'}TYPES_PATTERN)(\(.+\))?(!)?: .+"
                    if [[ ! "${'$'}PR_TITLE" =~ ${'$'}PATTERN ]]; then
                      echo "::warning::PR title does not match conventional commits format: <type>(<scope>): <description>"
                      echo "::warning::Allowed types: ${'$'}ALLOWED_TYPES"
                      echo "::warning::Got: ${'$'}PR_TITLE"
                    else
                      echo "PR title is valid: ${'$'}PR_TITLE"
                    fi
                """.trimIndent(),
                env = linkedMapOf(
                    "PR_TITLE" to "\${{ github.event.pull_request.title }}",
                    "ALLOWED_TYPES" to allowedTypes.ref.expression,
                ),
            )
        }
    }
}
```

- [ ] **Step 7: Migrate `LabelerWorkflow.kt`**

Create `src/main/kotlin/workflows/base/LabelerWorkflow.kt`:

```kotlin
package workflows.base

import dsl.builder.AdapterWorkflowBuilder
import dsl.builder.ReusableWorkflowJobBuilder
import dsl.builder.refInput
import io.github.typesafegithub.workflows.actions.actions.Labeler
import io.github.typesafegithub.workflows.domain.Mode
import io.github.typesafegithub.workflows.domain.Permission
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.dsl.WorkflowBuilder
import workflows.ProjectWorkflow

object LabelerWorkflow : ProjectWorkflow(
    "labeler.yml", "PR Labeler",
    permissions = mapOf(Permission.Contents to Mode.Write, Permission.PullRequests to Mode.Write),
) {
    val configPath = input("config-path", "Path to labeler configuration file", default = ".github/labeler.yml")

    class JobBuilder : ReusableWorkflowJobBuilder(LabelerWorkflow) {
        var configPath by refInput(LabelerWorkflow.configPath)
    }

    context(builder: AdapterWorkflowBuilder)
    fun job(id: String, block: JobBuilder.() -> Unit = {}) {
        builder.registerJob(buildJob(id, ::JobBuilder, block))
    }

    override fun WorkflowBuilder.implementation() {
        job(id = "label", name = "Label PR", runsOn = UbuntuLatest) {
            uses(
                name = "Label PR based on file paths",
                action = Labeler(
                    repoToken_Untyped = "\${{ secrets.GITHUB_TOKEN }}",
                    configurationPath_Untyped = configPath.ref.expression,
                    syncLabels = true,
                ),
            )
        }
    }
}
```

- [ ] **Step 8: Migrate `AppDeployWorkflow.kt`**

Create `src/main/kotlin/workflows/base/AppDeployWorkflow.kt`:

```kotlin
package workflows.base

import dsl.builder.AdapterWorkflowBuilder
import dsl.builder.ReusableWorkflowJobBuilder
import dsl.builder.refInput
import dsl.builder.stringInput
import dsl.capability.SetupCapability
import dsl.capability.SetupCapableJobBuilder
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.dsl.WorkflowBuilder
import workflows.ProjectWorkflow
import workflows.support.conditionalSetupSteps

object AppDeployWorkflow : ProjectWorkflow("app-deploy.yml", "Application Deploy"), SetupCapability {
    override val setupAction = input("setup-action", SetupCapability.SETUP_ACTION_DESCRIPTION, required = true)
    override val setupParams = input("setup-params", SetupCapability.SETUP_PARAMS_DESCRIPTION, default = SetupCapability.SETUP_PARAMS_DEFAULT)
    val deployCommand = input("deploy-command", "Command to run for deployment", required = true)
    val tag = input("tag", "Tag/version to deploy (checked out at this ref)", required = true)

    class JobBuilder : ReusableWorkflowJobBuilder(AppDeployWorkflow), SetupCapableJobBuilder {
        override var setupAction by stringInput(AppDeployWorkflow.setupAction)
        override var setupParams by stringInput(AppDeployWorkflow.setupParams)
        var deployCommand by refInput(AppDeployWorkflow.deployCommand)
        var tag by refInput(AppDeployWorkflow.tag)
    }

    context(builder: AdapterWorkflowBuilder)
    fun job(id: String, block: JobBuilder.() -> Unit = {}) {
        builder.registerJob(buildJob(id, ::JobBuilder, block))
    }

    override fun WorkflowBuilder.implementation() {
        job(id = "deploy", name = "Deploy", runsOn = UbuntuLatest) {
            conditionalSetupSteps(fetchDepth = "0")
            run(name = "Checkout tag", command = "git checkout \"${tag.ref.expression}\"")
            run(name = "Deploy", command = deployCommand.ref.expression)
        }
    }
}
```

- [ ] **Step 9: Delete old workflow files**

Delete all original files:
- `src/main/kotlin/workflows/CheckWorkflow.kt`
- `src/main/kotlin/workflows/PublishWorkflow.kt`
- `src/main/kotlin/workflows/ReleaseWorkflow.kt`
- `src/main/kotlin/workflows/CreateTagWorkflow.kt`
- `src/main/kotlin/workflows/ManualCreateTagWorkflow.kt`
- `src/main/kotlin/workflows/ConventionalCommitCheckWorkflow.kt`
- `src/main/kotlin/workflows/LabelerWorkflow.kt`
- `src/main/kotlin/workflows/AppDeployWorkflow.kt`

- [ ] **Step 10: Build to check for compilation errors**

Run: `./gradlew build 2>&1 | tail -20`
Expected: Errors in adapter files and Generate.kt (they still import from old packages). This is expected — fixed in Task 6 and 7.

- [ ] **Step 11: Commit**

```bash
git add -A src/main/kotlin/workflows/
git commit -m "refactor: migrate all 8 base workflows to workflows/base with SetupCapability and implementation() pattern"
```

---

### Task 6: Migrate adapter workflows to object containers

Move adapter workflows from function-based to object-based pattern. Update all import paths.

**Files:**
- Rewrite: `src/main/kotlin/workflows/adapters/check/GradleCheck.kt`
- Rewrite: `src/main/kotlin/workflows/adapters/tag/CreateTag.kt` → `src/main/kotlin/workflows/adapters/tag/CreateTagAdapters.kt`
- Rewrite: `src/main/kotlin/workflows/adapters/tag/ManualCreateTag.kt` → `src/main/kotlin/workflows/adapters/tag/ManualCreateTagAdapters.kt`
- Rewrite: `src/main/kotlin/workflows/adapters/release/AppRelease.kt` + `GradleRelease.kt` → `src/main/kotlin/workflows/adapters/release/ReleaseAdapters.kt`
- Delete: old adapter files

- [ ] **Step 1: Rewrite `GradleCheck.kt` as object container**

```kotlin
package workflows.adapters.check

import config.DEFAULT_JAVA_VERSION
import config.JAVA_VERSION_MATRIX_EXPR
import config.SetupTool
import dsl.builder.AdapterWorkflow
import dsl.builder.adapterWorkflow
import workflows.base.CheckWorkflow
import workflows.base.ConventionalCommitCheckWorkflow
import workflows.support.setup

object GradleCheck {
    val appCheck = gradleCheck("app-check.yml", "Application Check")
    val gradleCheck = gradleCheck("gradle-check.yml", "Gradle Check")
    val gradlePluginCheck = gradleCheck("gradle-plugin-check.yml", "Gradle Plugin Check")
    val kotlinLibraryCheck = gradleCheck("kotlin-library-check.yml", "Kotlin Library Check")

    private fun gradleCheck(fileName: String, name: String): AdapterWorkflow = adapterWorkflow(fileName, name) {
        val javaVersion = input("java-version", description = "JDK version to use", default = DEFAULT_JAVA_VERSION)
        val javaVersions = input("java-versions", description = "JSON array of JDK versions for matrix build (overrides java-version)", default = "")
        val gradleCommand = input("gradle-command", description = "Gradle check command", default = "./gradlew check")

        val javaVersionMatrix = matrixRef("java-version")

        ConventionalCommitCheckWorkflow.job("conventional-commit")

        CheckWorkflow.job("check") {
            strategy(matrix(javaVersionMatrix.key to JAVA_VERSION_MATRIX_EXPR))
            setup(SetupTool.Gradle, javaVersionMatrix.ref)
            CheckWorkflow.checkCommand from gradleCommand
        }
    }
}
```

- [ ] **Step 2: Create `CreateTagAdapters.kt`**

```kotlin
package workflows.adapters.tag

import config.DEFAULT_RELEASE_BRANCHES
import config.ToolTagConfig
import dsl.builder.AdapterWorkflow
import dsl.builder.adapterWorkflow
import config.GRADLE_TAG
import config.GO_TAG
import workflows.base.CreateTagWorkflow
import workflows.support.setup

object CreateTagAdapters {
    val gradle = toolCreateTag("gradle-create-tag.yml", "Gradle Create Tag", GRADLE_TAG)
    val go = toolCreateTag("go-create-tag.yml", "Go Create Tag", GO_TAG)

    private fun toolCreateTag(fileName: String, name: String, config: ToolTagConfig): AdapterWorkflow =
        adapterWorkflow(fileName, name) {
            val version = input(config.tool.versionKey, description = config.tool.versionDescription, default = config.tool.defaultVersion)
            val checkCommand = input(config.commandInputName, description = config.commandDescription, default = config.defaultCommand)
            val defaultBump = input("default-bump", description = "Default version bump type (major, minor, patch)", default = "patch")
            val tagPrefix = input("tag-prefix", description = "Prefix for the tag", default = config.defaultTagPrefix)
            val releaseBranches = input("release-branches", description = "Comma-separated branch patterns for releases", default = DEFAULT_RELEASE_BRANCHES)

            CreateTagWorkflow.job("create-tag") {
                setup(config.tool, version)
                CreateTagWorkflow.checkCommand from checkCommand
                CreateTagWorkflow.defaultBump from defaultBump
                CreateTagWorkflow.tagPrefix from tagPrefix
                CreateTagWorkflow.releaseBranches from releaseBranches
                passthroughAllSecrets()
            }
        }
}
```

- [ ] **Step 3: Create `ManualCreateTagAdapters.kt`**

```kotlin
package workflows.adapters.tag

import config.ToolTagConfig
import dsl.builder.AdapterWorkflow
import dsl.builder.adapterWorkflow
import config.GRADLE_TAG
import config.GO_TAG
import workflows.base.ManualCreateTagWorkflow
import workflows.support.setup

object ManualCreateTagAdapters {
    val gradle = toolManualCreateTag("gradle-manual-create-tag.yml", "Gradle Manual Create Tag", GRADLE_TAG)
    val go = toolManualCreateTag("go-manual-create-tag.yml", "Go Manual Create Tag", GO_TAG)

    private fun toolManualCreateTag(fileName: String, name: String, config: ToolTagConfig): AdapterWorkflow =
        adapterWorkflow(fileName, name) {
            val tagVersion = input("tag-version", description = "Version to tag (e.g. 1.2.3)", required = true)
            val version = input(config.tool.versionKey, description = config.tool.versionDescription, default = config.tool.defaultVersion)
            val checkCommand = input(config.commandInputName, description = config.commandDescription, default = config.defaultCommand)
            val tagPrefix = input("tag-prefix", description = "Prefix for the tag", default = config.defaultTagPrefix)

            ManualCreateTagWorkflow.job("manual-tag") {
                ManualCreateTagWorkflow.tagVersion from tagVersion
                ManualCreateTagWorkflow.tagPrefix from tagPrefix
                setup(config.tool, version)
                ManualCreateTagWorkflow.checkCommand from checkCommand
                passthroughAllSecrets()
            }
        }
}
```

- [ ] **Step 4: Create `ReleaseAdapters.kt`**

```kotlin
package workflows.adapters.release

import config.DEFAULT_CHANGELOG_CONFIG
import config.DEFAULT_JAVA_VERSION
import config.SetupTool
import dsl.builder.AdapterWorkflow
import dsl.builder.adapterWorkflow
import workflows.base.PublishWorkflow
import workflows.base.ReleaseWorkflow
import workflows.support.setup

object ReleaseAdapters {
    val app: AdapterWorkflow = adapterWorkflow("app-release.yml", "Application Release") {
        val changelogConfig = input("changelog-config", description = "Path to changelog configuration file", default = DEFAULT_CHANGELOG_CONFIG)
        val draft = booleanInput("draft", description = "Create release as draft (default true for apps)", default = true)

        ReleaseWorkflow.job("release") {
            ReleaseWorkflow.changelogConfig from changelogConfig
            ReleaseWorkflow.draft from draft
        }
    }

    val gradlePlugin = gradleRelease(
        "gradle-plugin-release.yml", "Gradle Plugin Release",
        "Gradle publish command (publishes to both Maven Central and Gradle Portal)",
    )

    val kotlinLibrary = gradleRelease(
        "kotlin-library-release.yml", "Kotlin Library Release",
        "Gradle publish command for Maven Central",
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

    private fun gradleRelease(
        fileName: String,
        name: String,
        publishDescription: String,
        publishSecrets: PublishWorkflow.JobBuilder.() -> Unit = { passthroughAllSecrets() },
    ): AdapterWorkflow = adapterWorkflow(fileName, name) {
        val javaVersion = input("java-version", description = "JDK version to use", default = DEFAULT_JAVA_VERSION)
        val publishCommand = input("publish-command", description = publishDescription, required = true)
        val changelogConfig = input("changelog-config", description = "Path to changelog configuration file", default = DEFAULT_CHANGELOG_CONFIG)

        ReleaseWorkflow.job("release") {
            ReleaseWorkflow.changelogConfig from changelogConfig
        }

        PublishWorkflow.job("publish") {
            needs("release")
            setup(SetupTool.Gradle, javaVersion)
            PublishWorkflow.publishCommand from publishCommand
            publishSecrets()
        }
    }
}
```

- [ ] **Step 5: Delete old adapter files**

Delete:
- `src/main/kotlin/workflows/adapters/tag/CreateTag.kt`
- `src/main/kotlin/workflows/adapters/tag/ManualCreateTag.kt`
- `src/main/kotlin/workflows/adapters/release/AppRelease.kt`
- `src/main/kotlin/workflows/adapters/release/GradleRelease.kt`

- [ ] **Step 6: Commit**

```bash
git add -A src/main/kotlin/workflows/adapters/
git commit -m "refactor: migrate adapter workflows to object containers with named properties"
```

---

### Task 7: Rewire Generate.kt and verify YAML identity

Update `Generate.kt` to use the unified list. Run generation and verify YAML output is identical.

**Files:**
- Modify: `src/main/kotlin/generate/Generate.kt`

- [ ] **Step 1: Rewrite `Generate.kt`**

```kotlin
package generate

import dsl.builder.GeneratableWorkflow
import workflows.base.CheckWorkflow
import workflows.base.ConventionalCommitCheckWorkflow
import workflows.base.CreateTagWorkflow
import workflows.base.ManualCreateTagWorkflow
import workflows.base.ReleaseWorkflow
import workflows.base.PublishWorkflow
import workflows.base.LabelerWorkflow
import workflows.base.AppDeployWorkflow
import workflows.adapters.check.GradleCheck
import workflows.adapters.tag.CreateTagAdapters
import workflows.adapters.tag.ManualCreateTagAdapters
import workflows.adapters.release.ReleaseAdapters
import java.io.File

fun main() {
    val outputDir = File(".github/workflows").apply { mkdirs() }

    listOf<GeneratableWorkflow>(
        // Base workflows
        CheckWorkflow,
        ConventionalCommitCheckWorkflow,
        CreateTagWorkflow,
        ManualCreateTagWorkflow,
        ReleaseWorkflow,
        PublishWorkflow,
        LabelerWorkflow,
        AppDeployWorkflow,

        // Adapters — check
        GradleCheck.appCheck,
        GradleCheck.gradleCheck,
        GradleCheck.gradlePluginCheck,
        GradleCheck.kotlinLibraryCheck,

        // Adapters — tag
        CreateTagAdapters.gradle,
        CreateTagAdapters.go,
        ManualCreateTagAdapters.gradle,
        ManualCreateTagAdapters.go,

        // Adapters — release
        ReleaseAdapters.app,
        ReleaseAdapters.gradlePlugin,
        ReleaseAdapters.kotlinLibrary,
    ).forEach { it.generate(outputDir) }
}
```

- [ ] **Step 2: Build the project**

Run: `./gradlew build 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run generation**

Run: `./gradlew run 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL — all 19 YAML files regenerated

- [ ] **Step 4: Verify YAML identity**

Run: `git diff .github/workflows/`

Expected: Either no diff (ideal), or only cosmetic changes due to `toWorkflowCallTrigger()` now being used uniformly. Specifically, workflows that previously used `WorkflowCall(inputs = inputs)` (Check, Publish, CreateTag, ManualCreateTag, AppDeploy, ConventionalCommitCheck, Labeler) might now have boolean inputs serialized differently if they have boolean inputs. Only ReleaseWorkflow has boolean inputs (`draft`), and it already used `toWorkflowCallTrigger()`, so there should be **no diff**.

If there IS a diff: inspect each changed file. The `sourceFile` path changed from `src/main/kotlin/workflows/XxxWorkflow.kt` to `src/main/kotlin/workflows/base/XxxWorkflow.kt` — this appears in the YAML header comment generated by `github-workflows-kt`. This is a cosmetic change and is acceptable.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/generate/Generate.kt .github/workflows/
git commit -m "refactor: rewire Generate.kt to unified GeneratableWorkflow list"
```

- [ ] **Step 6: Final cleanup — remove any remaining empty directories**

Run: `find src/main/kotlin/workflows/ workflow-dsl/src/main/kotlin/dsl/ -type d -empty` — delete any empty directories found.

- [ ] **Step 7: Final commit if cleanup was needed**

```bash
git add -A
git commit -m "chore: remove empty directories after restructuring"
```
