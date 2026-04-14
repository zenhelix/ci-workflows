# Kotlin-Way Refactoring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor ci-workflows into two Gradle modules (`:workflow-dsl` framework + `:ci-workflows` application), add strict typing with value classes, reduce boilerplate via property delegates, and improve readability — all while keeping generated YAML byte-identical.

**Architecture:** The DSL framework (ReusableWorkflow, AdapterWorkflow, builders, YAML serialization) moves to a standalone `:workflow-dsl` module. The application module keeps project-specific code (SetupTool, Actions, concrete workflow definitions, adapters, Generate.kt). Value classes (`InputRef`, `SecretRef`, `MatrixRefExpr`) enforce type safety at ref boundaries. Property delegates replace hand-written JobBuilder methods.

**Tech Stack:** Kotlin 2.3.20, github-workflows-kt 3.7.0, kaml 0.104.0, kotlinx-serialization 1.11.0, Gradle 9.4.1

---

## File Map

### New files to create

| File | Purpose |
|------|---------|
| `workflow-dsl/build.gradle.kts` | Build config for the DSL framework module |
| `workflow-dsl/src/main/kotlin/dsl/ReusableWorkflow.kt` | Moved from `src/` — base class + WorkflowInput/WorkflowSecret + InputRef/SecretRef value classes |
| `workflow-dsl/src/main/kotlin/dsl/AdapterWorkflow.kt` | Moved from `src/` — adapter base + YAML generation |
| `workflow-dsl/src/main/kotlin/dsl/ReusableWorkflowJobBuilder.kt` | Moved from `src/` — builder + InputProperty delegate + ReusableWorkflowJobDef |
| `workflow-dsl/src/main/kotlin/dsl/MatrixDef.kt` | Moved from `src/` — MatrixDef, MatrixRef, MatrixRefExpr |
| `workflow-dsl/src/main/kotlin/dsl/yaml/AdapterWorkflowYaml.kt` | Moved from `src/` — DTOs + kaml serialization |

### Files to modify (staying in `:ci-workflows`)

| File | Changes |
|------|---------|
| `settings.gradle.kts` | Add `include("workflow-dsl")` |
| `build.gradle.kts` | Add `implementation(project(":workflow-dsl"))`, remove deps moved to dsl module |
| `src/main/kotlin/config/Defaults.kt` | Decompose `JAVA_VERSION_MATRIX_EXPR` |
| `src/main/kotlin/config/SetupTool.kt` | `toParamsJson` accepts `MatrixRefExpr` |
| `src/main/kotlin/actions/Actions.kt` | `?.let` pattern in `SetupAction.toYamlArguments()` |
| `src/main/kotlin/dsl/Workflows.kt` → `src/main/kotlin/workflows/Workflows.kt` | Package rename + property delegates in JobBuilders, remove `createJobBuilder()` overrides |
| `src/main/kotlin/dsl/WorkflowHelpers.kt` → `src/main/kotlin/workflows/WorkflowHelpers.kt` | Package rename, add SetupTool extension functions |
| `src/main/kotlin/workflows/base/Check.kt` | Update imports (package `dsl` → `workflows`) |
| `src/main/kotlin/workflows/base/CreateTag.kt` | Update imports |
| `src/main/kotlin/workflows/base/ManualCreateTag.kt` | Update imports |
| `src/main/kotlin/workflows/base/Release.kt` | Update imports |
| `src/main/kotlin/workflows/base/Publish.kt` | Update imports, `ref` → `.ref.expression` |
| `src/main/kotlin/workflows/base/ConventionalCommitCheck.kt` | Update imports |
| `src/main/kotlin/workflows/base/Labeler.kt` | Update imports |
| `src/main/kotlin/workflows/adapters/deploy/AppDeploy.kt` → `src/main/kotlin/workflows/base/AppDeploy.kt` | Move to base package |
| All adapter files | Update imports, use property assignment syntax, use `::JobBuilder` factory |
| `src/main/kotlin/generate/Generate.kt` | Update imports |

### Files to delete

| File | Reason |
|------|--------|
| `src/main/kotlin/dsl/ReusableWorkflow.kt` | Moved to `workflow-dsl/` |
| `src/main/kotlin/dsl/AdapterWorkflow.kt` | Moved to `workflow-dsl/` |
| `src/main/kotlin/dsl/ReusableWorkflowJobBuilder.kt` | Moved to `workflow-dsl/` |
| `src/main/kotlin/dsl/MatrixDef.kt` | Moved to `workflow-dsl/` |
| `src/main/kotlin/dsl/yaml/AdapterWorkflowYaml.kt` | Moved to `workflow-dsl/` |
| `src/main/kotlin/dsl/Workflows.kt` | Moved to `src/main/kotlin/workflows/Workflows.kt` |
| `src/main/kotlin/dsl/WorkflowHelpers.kt` | Moved to `src/main/kotlin/workflows/WorkflowHelpers.kt` |
| `src/main/kotlin/workflows/adapters/deploy/AppDeploy.kt` | Moved to `workflows/base/` |

---

## Task 1: Snapshot generated YAML for verification

**Files:**
- Read: `.github/workflows/*.yml` (all 20 files)

- [ ] **Step 1: Copy current YAML output as baseline**

```bash
mkdir -p /tmp/ci-workflows-baseline
cp .github/workflows/*.yml /tmp/ci-workflows-baseline/
```

- [ ] **Step 2: Verify baseline has all expected files**

```bash
ls /tmp/ci-workflows-baseline/ | wc -l
```

Expected: 20 files (or however many exist — count them first with `ls .github/workflows/*.yml | wc -l`).

- [ ] **Step 3: Commit (nothing to commit — just a prep step)**

No commit needed; the baseline is in `/tmp/` for later diff.

---

## Task 2: Create `:workflow-dsl` module skeleton

**Files:**
- Create: `workflow-dsl/build.gradle.kts`
- Modify: `settings.gradle.kts`

- [ ] **Step 1: Create `workflow-dsl/build.gradle.kts`**

```kotlin
plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.typesafegithub:github-workflows-kt:3.7.0")
    implementation("com.charleskorn.kaml:kaml:0.104.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.11.0")
}
```

- [ ] **Step 2: Update `settings.gradle.kts`**

```kotlin
rootProject.name = "ci-workflows"

include("workflow-dsl")
```

- [ ] **Step 3: Update root `build.gradle.kts`**

Add the workflow-dsl project dependency. Remove `kaml` and `kotlinx-serialization-core` from root deps (they'll come transitively or from the dsl module). Keep `github-workflows-kt` because base workflows use it directly.

```kotlin
plugins {
    kotlin("jvm") version "2.3.20"
    kotlin("plugin.serialization") version "2.3.20"
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
    implementation("io.github.typesafegithub:github-workflows-kt:3.7.0")

    // JIT action bindings
    implementation("actions:checkout:v6")
    // mathieudutour:github-tag-action:v6 - not yet available in bindings.krzeminski.it registry
    implementation("mikepenz:release-changelog-builder-action:v6")
    implementation("softprops:action-gh-release:v2")
    implementation("actions:labeler:v6")
}
```

- [ ] **Step 4: Verify build compiles**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL (workflow-dsl module compiles with 0 source files).

- [ ] **Step 5: Commit**

```bash
git add workflow-dsl/build.gradle.kts settings.gradle.kts build.gradle.kts
git commit -m "refactor: create :workflow-dsl module skeleton"
```

---

## Task 3: Move DSL framework files to `:workflow-dsl` with value classes and sealed interfaces

This task moves the 5 DSL framework files into the new module and applies the typing/readability changes simultaneously (they affect the same files).

**Files:**
- Create: `workflow-dsl/src/main/kotlin/dsl/ReusableWorkflow.kt`
- Create: `workflow-dsl/src/main/kotlin/dsl/AdapterWorkflow.kt`
- Create: `workflow-dsl/src/main/kotlin/dsl/ReusableWorkflowJobBuilder.kt`
- Create: `workflow-dsl/src/main/kotlin/dsl/MatrixDef.kt`
- Create: `workflow-dsl/src/main/kotlin/dsl/yaml/AdapterWorkflowYaml.kt`
- Delete: `src/main/kotlin/dsl/ReusableWorkflow.kt`
- Delete: `src/main/kotlin/dsl/AdapterWorkflow.kt`
- Delete: `src/main/kotlin/dsl/ReusableWorkflowJobBuilder.kt`
- Delete: `src/main/kotlin/dsl/MatrixDef.kt`
- Delete: `src/main/kotlin/dsl/yaml/AdapterWorkflowYaml.kt`

- [ ] **Step 1: Create `workflow-dsl/src/main/kotlin/dsl/ReusableWorkflow.kt`**

This file adds `InputRef`, `SecretRef` value classes, replaces `.map{}.toMap()` with `mapValues`, removes `abstract fun createJobBuilder()`, and makes `reusableJob` accept an explicit factory.

```kotlin
package dsl

import dsl.yaml.InputYaml
import dsl.yaml.SecretYaml
import dsl.yaml.YamlDefault
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall

@JvmInline
value class InputRef(val expression: String)

@JvmInline
value class SecretRef(val expression: String)

abstract class ReusableWorkflow(val fileName: String) {
    private val _inputs = mutableMapOf<String, WorkflowCall.Input>()
    private val _secrets = mutableMapOf<String, WorkflowCall.Secret>()
    private val _booleanDefaults = mutableMapOf<String, Boolean>()

    protected fun input(
        name: String,
        description: String,
        required: Boolean = false,
        type: WorkflowCall.Type = WorkflowCall.Type.String,
        default: String? = null,
    ): WorkflowInput {
        _inputs[name] = WorkflowCall.Input(description, required, type, default)
        return WorkflowInput(name)
    }

    protected fun booleanInput(
        name: String,
        description: String,
        required: Boolean = false,
        default: Boolean? = null,
    ): WorkflowInput {
        _inputs[name] = WorkflowCall.Input(description, required, WorkflowCall.Type.Boolean, null)
        if (default != null) _booleanDefaults[name] = default
        return WorkflowInput(name)
    }

    protected fun secret(
        name: String,
        description: String,
        required: Boolean = true,
    ): WorkflowSecret {
        _secrets[name] = WorkflowCall.Secret(description, required)
        return WorkflowSecret(name)
    }

    val inputs: Map<String, WorkflowCall.Input> get() = _inputs.toMap()
    val secrets: Map<String, WorkflowCall.Secret> get() = _secrets.toMap()
    val requiredInputNames: Set<String> by lazy {
        _inputs.filter { (_, input) -> input.required }.keys
    }

    fun toInputsYaml(): Map<String, InputYaml>? =
        _inputs.takeIf { it.isNotEmpty() }?.mapValues { (name, input) ->
            val boolDefault = _booleanDefaults[name]
            val default = when {
                boolDefault != null  -> YamlDefault.BooleanValue(boolDefault)
                input.default != null -> YamlDefault.StringValue(input.default!!)
                else                  -> null
            }
            InputYaml(
                description = input.description,
                type = input.type.name.lowercase(),
                required = input.required,
                default = default,
            )
        }

    fun toSecretsYaml(): Map<String, SecretYaml>? =
        _secrets.takeIf { it.isNotEmpty() }?.mapValues { (_, secret) ->
            SecretYaml(description = secret.description, required = secret.required)
        }

    fun toWorkflowCallTrigger(): WorkflowCall {
        val secretsMap = _secrets.takeIf { it.isNotEmpty() }?.toMap()
        return if (_booleanDefaults.isEmpty()) {
            WorkflowCall(inputs = _inputs.toMap(), secrets = secretsMap)
        } else {
            WorkflowCall(
                secrets = secretsMap,
                _customArguments = mapOf("inputs" to inputsAsRawMap()),
            )
        }
    }

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
}

class WorkflowInput(val name: String) {
    val ref: InputRef get() = InputRef("\${{ inputs.$name }}")
}

class WorkflowSecret(val name: String) {
    val ref: SecretRef get() = SecretRef("\${{ secrets.$name }}")
}

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

- [ ] **Step 2: Create `workflow-dsl/src/main/kotlin/dsl/ReusableWorkflowJobBuilder.kt`**

Adds `InputProperty` delegate, renames `set` → `setInput` (public), adds `InputRef` overload.

```kotlin
package dsl

import dsl.yaml.JobYaml
import dsl.yaml.NeedsYaml
import dsl.yaml.StrategyYaml
import kotlin.reflect.KProperty

class InputProperty(private val input: WorkflowInput) {
    operator fun getValue(builder: ReusableWorkflowJobBuilder, property: KProperty<*>): String =
        builder.getInput(input)

    operator fun setValue(builder: ReusableWorkflowJobBuilder, property: KProperty<*>, value: String) {
        builder.setInput(input, value)
    }
}

fun inputProp(input: WorkflowInput) = InputProperty(input)

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

Note: `uses.usesString` is used in `toJobYaml()`. The `usesString` property on `ReusableWorkflow` depends on `config.reusableWorkflow()`, which is in the application module. We need to make `usesString` abstract or injectable. Since `ReusableWorkflow` is in the framework module and can't reference `config.Refs`, we make it a constructor parameter:

Actually, let me reconsider. The `usesString` is derived from `fileName` via `reusableWorkflow(fileName)` which lives in the application module. The cleanest approach: make `usesString` an `open val` with a default that just returns `fileName`, and let the application-module subclass override it. But that's fragile.

Better approach: add a `usesResolver` function parameter to `AdapterWorkflow.generate()`, or make `usesString` a `lateinit` or abstract. Since ALL concrete ReusableWorkflow objects are defined in the application module, the simplest is:

**Make `usesString` an abstract property on `ReusableWorkflow`:**

In the framework module, `ReusableWorkflow` declares:
```kotlin
abstract val usesString: String
```

In the application module, each workflow object sets it:
```kotlin
object CheckWorkflow : ReusableWorkflow("check.yml") {
    override val usesString = reusableWorkflow(fileName)
    // ...
}
```

This keeps the framework module clean. Update the `ReusableWorkflow.kt` file above: replace `val usesString: String get() = reusableWorkflow(fileName)` with `abstract val usesString: String`.

Updated line in `workflow-dsl/src/main/kotlin/dsl/ReusableWorkflow.kt` — replace the `usesString` property getter. The full file in Step 1 above should have this line instead:

```kotlin
    abstract val usesString: String
```

(Remove the import of `config.reusableWorkflow` — it doesn't exist in the framework module.)

- [ ] **Step 3: Create `workflow-dsl/src/main/kotlin/dsl/AdapterWorkflow.kt`**

Adds `collectSecretsFromJobs` simplification.

```kotlin
package dsl

import dsl.yaml.AdapterWorkflowYaml
import dsl.yaml.SecretYaml
import dsl.yaml.TriggerYaml
import dsl.yaml.WorkflowCallBodyYaml
import dsl.yaml.adapterWorkflowYaml
import java.io.File

private val QUOTED_MAP_KEY = Regex("""^(\s*)'([^']*?)'\s*:""", RegexOption.MULTILINE)

private fun unquoteYamlMapKeys(yaml: String): String =
    yaml.replace(QUOTED_MAP_KEY, "$1$2:")

abstract class AdapterWorkflow(fileName: String) : ReusableWorkflow(fileName) {

    abstract val workflowName: String

    abstract fun jobs(): List<ReusableWorkflowJobDef>

    fun generate(outputDir: File) {
        val jobDefs = jobs()
        val collectedSecrets = collectSecretsFromJobs(jobDefs)

        val dto = AdapterWorkflowYaml(
            name = workflowName,
            on = TriggerYaml(
                workflowCall = WorkflowCallBodyYaml(
                    inputs = toInputsYaml(),
                    secrets = collectedSecrets,
                )
            ),
            jobs = jobDefs.associate { job -> job.id to job.toJobYaml() },
        )

        val slug = fileName.removeSuffix(".yml")
        val header = buildString {
            appendLine("# This file was generated using Kotlin DSL (.github/workflow-src/$slug.main.kts).")
            appendLine("# If you want to modify the workflow, please change the Kotlin file and regenerate this YAML file.")
            append("# Generated with https://github.com/typesafegithub/github-workflows-kt")
        }

        val rawBody = adapterWorkflowYaml.encodeToString(AdapterWorkflowYaml.serializer(), dto)
        val body = unquoteYamlMapKeys(rawBody)

        outputDir.mkdirs()
        File(outputDir, fileName).writeText("$header\n\n$body\n")
    }

    private fun collectSecretsFromJobs(jobDefs: List<ReusableWorkflowJobDef>): Map<String, SecretYaml>? {
        val referencedNames = jobDefs.flatMapTo(mutableSetOf()) { it.secrets.keys }
        if (referencedNames.isEmpty()) return null

        val descriptions = jobDefs
            .flatMap { it.uses.secrets.entries }
            .associate { (name, secret) -> name to secret.description }

        return referencedNames.associateWith { name ->
            SecretYaml(description = descriptions[name] ?: name, required = true)
        }
    }
}
```

- [ ] **Step 4: Create `workflow-dsl/src/main/kotlin/dsl/MatrixDef.kt`**

Adds `MatrixRefExpr` value class.

```kotlin
package dsl

@JvmInline
value class MatrixRefExpr(val expression: String)

class MatrixRef(val key: String) {
    val ref: MatrixRefExpr get() = MatrixRefExpr("\${{ matrix.$key }}")
}

data class MatrixDef(val entries: Map<String, String>)
```

- [ ] **Step 5: Create `workflow-dsl/src/main/kotlin/dsl/yaml/AdapterWorkflowYaml.kt`**

Sealed classes → sealed interfaces.

```kotlin
package dsl.yaml

import com.charleskorn.kaml.SingleLineStringStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

val adapterWorkflowYaml: Yaml = Yaml(
    configuration = YamlConfiguration(
        encodeDefaults = false,
        singleLineStringStyle = SingleLineStringStyle.SingleQuoted,
        breakScalarsAt = Int.MAX_VALUE,
    )
)

@Serializable
data class AdapterWorkflowYaml(
    val name: String,
    val on: TriggerYaml,
    val jobs: Map<String, JobYaml>,
)

@Serializable
data class TriggerYaml(
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
    val default: YamlDefault? = null,
)

@Serializable
data class SecretYaml(
    val description: String,
    val required: Boolean,
)

@Serializable
data class JobYaml(
    val needs: NeedsYaml? = null,
    val strategy: StrategyYaml? = null,
    val uses: String,
    val with: Map<String, String>? = null,
    val secrets: Map<String, String>? = null,
)

@Serializable
data class StrategyYaml(
    val matrix: Map<String, String>,
)

@Serializable(with = NeedsYamlSerializer::class)
sealed interface NeedsYaml {
    data class Single(val value: String) : NeedsYaml
    data class Multiple(val values: List<String>) : NeedsYaml

    companion object {
        fun of(list: List<String>): NeedsYaml? = when (list.size) {
            0 -> null
            1 -> Single(list.first())
            else -> Multiple(list)
        }
    }
}

object NeedsYamlSerializer : KSerializer<NeedsYaml> {
    private val listSerializer = ListSerializer(String.serializer())

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("NeedsYaml", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: NeedsYaml) {
        when (value) {
            is NeedsYaml.Single   -> encoder.encodeString(value.value)
            is NeedsYaml.Multiple -> listSerializer.serialize(encoder, value.values)
        }
    }

    override fun deserialize(decoder: Decoder): NeedsYaml =
        NeedsYaml.Single(decoder.decodeString())
}

@Serializable(with = YamlDefaultSerializer::class)
sealed interface YamlDefault {
    data class StringValue(val value: String) : YamlDefault
    data class BooleanValue(val value: Boolean) : YamlDefault
}

object YamlDefaultSerializer : KSerializer<YamlDefault> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("YamlDefault", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: YamlDefault) {
        when (value) {
            is YamlDefault.StringValue  -> encoder.encodeString(value.value)
            is YamlDefault.BooleanValue -> encoder.encodeBoolean(value.value)
        }
    }

    override fun deserialize(decoder: Decoder): YamlDefault =
        YamlDefault.StringValue(decoder.decodeString())
}
```

- [ ] **Step 6: Delete old DSL files from `src/main/kotlin/dsl/`**

```bash
rm src/main/kotlin/dsl/ReusableWorkflow.kt
rm src/main/kotlin/dsl/AdapterWorkflow.kt
rm src/main/kotlin/dsl/ReusableWorkflowJobBuilder.kt
rm src/main/kotlin/dsl/MatrixDef.kt
rm src/main/kotlin/dsl/yaml/AdapterWorkflowYaml.kt
rmdir src/main/kotlin/dsl/yaml
```

Do NOT delete `src/main/kotlin/dsl/Workflows.kt` or `src/main/kotlin/dsl/WorkflowHelpers.kt` yet — those move in the next task.

- [ ] **Step 7: Verify workflow-dsl module compiles**

```bash
./gradlew :workflow-dsl:compileKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "refactor: move DSL framework to :workflow-dsl module

Add InputRef, SecretRef, MatrixRefExpr value classes.
Add InputProperty delegate for builder boilerplate.
Make usesString abstract, remove createJobBuilder().
Sealed classes → sealed interfaces for NeedsYaml, YamlDefault.
Simplify toInputsYaml/toSecretsYaml with mapValues."
```

---

## Task 4: Update application module — Workflows.kt and WorkflowHelpers.kt

Move `Workflows.kt` and `WorkflowHelpers.kt` from `dsl` package to `workflows` package. Apply property delegates and remove `createJobBuilder` overrides. Add SetupTool extensions.

**Files:**
- Create: `src/main/kotlin/workflows/Workflows.kt` (moved + refactored)
- Create: `src/main/kotlin/workflows/WorkflowHelpers.kt` (moved + SetupTool extensions)
- Delete: `src/main/kotlin/dsl/Workflows.kt`
- Delete: `src/main/kotlin/dsl/WorkflowHelpers.kt`

- [ ] **Step 1: Create `src/main/kotlin/workflows/Workflows.kt`**

Each workflow object:
- Adds `override val usesString = reusableWorkflow(fileName)`
- Replaces `fun foo(value: String) = set(...)` with `var foo by inputProp(...)`
- Removes `override fun createJobBuilder() = JobBuilder()`

```kotlin
package workflows

import config.DEFAULT_CHANGELOG_CONFIG
import config.DEFAULT_RELEASE_BRANCHES
import config.reusableWorkflow
import dsl.ReusableWorkflow
import dsl.ReusableWorkflowJobBuilder
import dsl.inputProp

object CheckWorkflow : ReusableWorkflow("check.yml") {
    override val usesString = reusableWorkflow(fileName)

    val setupAction = input(
        "setup-action",
        description = "Setup action to use: gradle, go",
        required = true
    )
    val setupParams = input(
        "setup-params",
        description = "JSON object with setup parameters (e.g. {\"java-version\": \"21\"})",
        default = "{}"
    )
    val checkCommand = input(
        "check-command",
        description = "Command to run for checking",
        required = true
    )

    class JobBuilder : ReusableWorkflowJobBuilder(CheckWorkflow) {
        var setupAction by inputProp(CheckWorkflow.setupAction)
        var setupParams by inputProp(CheckWorkflow.setupParams)
        var checkCommand by inputProp(CheckWorkflow.checkCommand)
    }
}

object ConventionalCommitCheckWorkflow : ReusableWorkflow("conventional-commit-check.yml") {
    override val usesString = reusableWorkflow(fileName)

    val allowedTypes = input(
        "allowed-types",
        description = "Comma-separated list of allowed commit types",
        default = "feat,fix,refactor,docs,test,chore,perf,ci"
    )

    class JobBuilder : ReusableWorkflowJobBuilder(ConventionalCommitCheckWorkflow) {
        var allowedTypes by inputProp(ConventionalCommitCheckWorkflow.allowedTypes)
    }
}

object CreateTagWorkflow : ReusableWorkflow("create-tag.yml") {
    override val usesString = reusableWorkflow(fileName)

    val setupAction = input(
        "setup-action",
        description = "Setup action to use: gradle, go",
        required = true
    )
    val setupParams = input(
        "setup-params",
        description = "JSON object with setup parameters (e.g. {\"java-version\": \"21\"})",
        default = "{}"
    )
    val checkCommand = input(
        "check-command",
        description = "Validation command to run before tagging",
        required = true
    )
    val defaultBump = input(
        "default-bump",
        description = "Default version bump type (major, minor, patch)",
        default = "patch"
    )
    val tagPrefix = input(
        "tag-prefix",
        description = "Prefix for the tag (e.g. v)",
        default = ""
    )
    val releaseBranches = input(
        "release-branches",
        description = "Comma-separated branch patterns for releases",
        default = DEFAULT_RELEASE_BRANCHES
    )
    val appId = secret(
        "app-id",
        description = "GitHub App ID for generating commit token"
    )
    val appPrivateKey = secret(
        "app-private-key",
        description = "GitHub App private key for generating commit token"
    )

    class JobBuilder : ReusableWorkflowJobBuilder(CreateTagWorkflow) {
        var setupAction by inputProp(CreateTagWorkflow.setupAction)
        var setupParams by inputProp(CreateTagWorkflow.setupParams)
        var checkCommand by inputProp(CreateTagWorkflow.checkCommand)
        var defaultBump by inputProp(CreateTagWorkflow.defaultBump)
        var tagPrefix by inputProp(CreateTagWorkflow.tagPrefix)
        var releaseBranches by inputProp(CreateTagWorkflow.releaseBranches)
    }
}

object ManualCreateTagWorkflow : ReusableWorkflow("manual-create-tag.yml") {
    override val usesString = reusableWorkflow(fileName)

    val tagVersion = input(
        "tag-version",
        description = "Version to tag (e.g. 1.2.3)",
        required = true
    )
    val tagPrefix = input(
        "tag-prefix",
        description = "Prefix for the tag (e.g. v)",
        default = ""
    )
    val setupAction = input(
        "setup-action",
        description = "Setup action to use: gradle, go, python",
        required = true
    )
    val setupParams = input(
        "setup-params",
        description = "JSON object with setup parameters (e.g. {\"java-version\": \"21\"})",
        default = "{}"
    )
    val checkCommand = input(
        "check-command",
        description = "Validation command to run before tagging",
        required = true
    )
    val appId = secret(
        "app-id",
        description = "GitHub App ID for generating commit token"
    )
    val appPrivateKey = secret(
        "app-private-key",
        description = "GitHub App private key for generating commit token"
    )

    class JobBuilder : ReusableWorkflowJobBuilder(ManualCreateTagWorkflow) {
        var tagVersion by inputProp(ManualCreateTagWorkflow.tagVersion)
        var tagPrefix by inputProp(ManualCreateTagWorkflow.tagPrefix)
        var setupAction by inputProp(ManualCreateTagWorkflow.setupAction)
        var setupParams by inputProp(ManualCreateTagWorkflow.setupParams)
        var checkCommand by inputProp(ManualCreateTagWorkflow.checkCommand)
    }
}

object ReleaseWorkflow : ReusableWorkflow("release.yml") {
    override val usesString = reusableWorkflow(fileName)

    val changelogConfig = input(
        "changelog-config",
        description = "Path to changelog configuration file",
        default = DEFAULT_CHANGELOG_CONFIG
    )
    val draft = booleanInput(
        "draft",
        description = "Create release as draft",
        default = false
    )

    class JobBuilder : ReusableWorkflowJobBuilder(ReleaseWorkflow) {
        var changelogConfig by inputProp(ReleaseWorkflow.changelogConfig)
        var draft by inputProp(ReleaseWorkflow.draft)
    }
}

object PublishWorkflow : ReusableWorkflow("publish.yml") {
    override val usesString = reusableWorkflow(fileName)

    val setupAction = input(
        "setup-action",
        description = "Setup action to use: gradle, go",
        required = true
    )
    val setupParams = input(
        "setup-params",
        description = "JSON object with setup parameters (e.g. {\"java-version\": \"21\"})",
        default = "{}"
    )
    val publishCommand = input(
        "publish-command",
        description = "Command to run for publishing",
        required = true
    )
    val mavenSonatypeUsername = secret(
        "MAVEN_SONATYPE_USERNAME",
        description = "Maven Central (Sonatype) username", required = false
    )
    val mavenSonatypeToken = secret(
        "MAVEN_SONATYPE_TOKEN",
        description = "Maven Central (Sonatype) token", required = false
    )
    val mavenSonatypeSigningKeyId = secret(
        "MAVEN_SONATYPE_SIGNING_KEY_ID",
        description = "GPG signing key ID", required = false
    )
    val mavenSonatypeSigningPubKeyAsciiArmored = secret(
        "MAVEN_SONATYPE_SIGNING_PUB_KEY_ASCII_ARMORED",
        description = "GPG signing public key (ASCII armored)", required = false
    )
    val mavenSonatypeSigningKeyAsciiArmored = secret(
        "MAVEN_SONATYPE_SIGNING_KEY_ASCII_ARMORED",
        description = "GPG signing private key (ASCII armored)", required = false
    )
    val mavenSonatypeSigningPassword = secret(
        "MAVEN_SONATYPE_SIGNING_PASSWORD",
        description = "GPG signing key passphrase", required = false
    )
    val gradlePublishKey = secret(
        "GRADLE_PUBLISH_KEY",
        description = "Gradle Plugin Portal publish key", required = false
    )
    val gradlePublishSecret = secret(
        "GRADLE_PUBLISH_SECRET",
        description = "Gradle Plugin Portal publish secret", required = false
    )

    class JobBuilder : ReusableWorkflowJobBuilder(PublishWorkflow) {
        var setupAction by inputProp(PublishWorkflow.setupAction)
        var setupParams by inputProp(PublishWorkflow.setupParams)
        var publishCommand by inputProp(PublishWorkflow.publishCommand)
    }
}

object LabelerWorkflow : ReusableWorkflow("labeler.yml") {
    override val usesString = reusableWorkflow(fileName)

    val configPath = input(
        "config-path",
        description = "Path to labeler configuration file",
        default = ".github/labeler.yml"
    )

    class JobBuilder : ReusableWorkflowJobBuilder(LabelerWorkflow) {
        var configPath by inputProp(LabelerWorkflow.configPath)
    }
}
```

- [ ] **Step 2: Create `src/main/kotlin/workflows/WorkflowHelpers.kt`**

Move from `dsl` package to `workflows` package. Add `SetupTool` extension functions for typed builder access.

```kotlin
package workflows

import actions.SetupAction
import config.SetupTool
import dsl.MatrixRefExpr
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

fun CheckWorkflow.JobBuilder.setup(tool: SetupTool, versionExpr: MatrixRefExpr) {
    setupAction = tool.id
    setupParams = tool.toParamsJson(versionExpr)
}

fun CheckWorkflow.JobBuilder.setup(tool: SetupTool, versionRef: String) {
    setupAction = tool.id
    setupParams = tool.toParamsJson(versionRef)
}

fun CreateTagWorkflow.JobBuilder.setup(tool: SetupTool, versionRef: String) {
    setupAction = tool.id
    setupParams = tool.toParamsJson(versionRef)
}

fun ManualCreateTagWorkflow.JobBuilder.setup(tool: SetupTool, versionRef: String) {
    setupAction = tool.id
    setupParams = tool.toParamsJson(versionRef)
}

fun PublishWorkflow.JobBuilder.setup(tool: SetupTool, versionRef: String) {
    setupAction = tool.id
    setupParams = tool.toParamsJson(versionRef)
}
```

- [ ] **Step 3: Delete old files**

```bash
rm src/main/kotlin/dsl/Workflows.kt
rm src/main/kotlin/dsl/WorkflowHelpers.kt
rmdir src/main/kotlin/dsl
```

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor: move Workflows.kt and WorkflowHelpers.kt to workflows package

Property delegates replace manual JobBuilder methods.
Add SetupTool typed extension functions on builders."
```

---

## Task 5: Update config — SetupTool, Defaults, Actions

**Files:**
- Modify: `src/main/kotlin/config/SetupTool.kt`
- Modify: `src/main/kotlin/config/Defaults.kt`
- Modify: `src/main/kotlin/actions/Actions.kt`

- [ ] **Step 1: Update `src/main/kotlin/config/SetupTool.kt`**

Add `MatrixRefExpr` overload for `toParamsJson`.

```kotlin
package config

import dsl.MatrixRefExpr

sealed class SetupTool(
    val actionName: String,
    val versionKey: String,
    val defaultVersion: String,
) {
    val id: String get() = actionName.removePrefix("setup-")

    fun toParamsJson(versionExpr: String): String =
        """{"$versionKey": "$versionExpr"}"""

    fun toParamsJson(versionExpr: MatrixRefExpr): String =
        """{"$versionKey": "${versionExpr.expression}"}"""

    data object Gradle : SetupTool("setup-gradle", "java-version", DEFAULT_JAVA_VERSION)
    data object Go : SetupTool("setup-go", "go-version", DEFAULT_GO_VERSION)
    data object Python : SetupTool("setup-python", "python-version", DEFAULT_PYTHON_VERSION)
}
```

- [ ] **Step 2: Update `src/main/kotlin/config/Defaults.kt`**

Decompose `JAVA_VERSION_MATRIX_EXPR`.

```kotlin
package config

const val DEFAULT_JAVA_VERSION = "17"
const val DEFAULT_GO_VERSION = "1.22"
const val DEFAULT_PYTHON_VERSION = "3.12"
const val DEFAULT_RELEASE_BRANCHES = "main,[0-9]+\\.x"
const val DEFAULT_CHANGELOG_CONFIG = ".github/changelog-config.json"

private const val MATRIX_FALLBACK_TEMPLATE = """["{0}"]"""

const val JAVA_VERSION_MATRIX_EXPR =
    "\${{ fromJson(inputs.java-versions || format('$MATRIX_FALLBACK_TEMPLATE', inputs.java-version)) }}"
```

- [ ] **Step 3: Update `src/main/kotlin/actions/Actions.kt`**

Replace `.apply { if (...) put(...) }` with `?.let` pattern. Note: `toYamlArguments()` must return `LinkedHashMap` (required by github-workflows-kt `Action` base class), so we keep `linkedMapOf`.

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
    override fun toYamlArguments() = linkedMapOf(
        versionKey to version,
    ).apply {
        fetchDepth?.let { put("fetch-depth", it) }
    }

    override fun buildOutputObject(stepId: String) = Outputs(stepId)
}

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

- [ ] **Step 4: Verify compilation**

```bash
./gradlew compileKotlin
```

Expected: FAIL — base workflow files and adapters still reference old imports. That's expected; we fix them in the next tasks.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/config/SetupTool.kt src/main/kotlin/config/Defaults.kt src/main/kotlin/actions/Actions.kt
git commit -m "refactor: add MatrixRefExpr overload to SetupTool, decompose MATRIX_EXPR"
```

---

## Task 6: Update base workflow generators

Update all 8 base workflow files to use new package imports. The `.ref` property now returns `InputRef`/`SecretRef` value classes. In base workflows, `.ref` is passed to github-workflows-kt functions that expect `String`, so we use `.ref.expression`.

**Files:**
- Modify: `src/main/kotlin/workflows/base/Check.kt`
- Modify: `src/main/kotlin/workflows/base/CreateTag.kt`
- Modify: `src/main/kotlin/workflows/base/ManualCreateTag.kt`
- Modify: `src/main/kotlin/workflows/base/Release.kt`
- Modify: `src/main/kotlin/workflows/base/Publish.kt`
- Modify: `src/main/kotlin/workflows/base/ConventionalCommitCheck.kt`
- Modify: `src/main/kotlin/workflows/base/Labeler.kt`
- Move: `src/main/kotlin/workflows/adapters/deploy/AppDeploy.kt` → `src/main/kotlin/workflows/base/AppDeploy.kt`

- [ ] **Step 1: Update `Check.kt`**

Change `import dsl.CheckWorkflow` → `import workflows.CheckWorkflow` and `import dsl.conditionalSetupSteps` → `import workflows.conditionalSetupSteps`.

```kotlin
package workflows.base

import workflows.CheckWorkflow
import workflows.conditionalSetupSteps
import io.github.typesafegithub.workflows.domain.Mode
import io.github.typesafegithub.workflows.domain.Permission
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import java.io.File

fun generateCheck() {
    workflow(
        name = "Check",
        on = listOf(
            WorkflowCall(inputs = CheckWorkflow.inputs),
        ),
        sourceFile = File(".github/workflow-src/check.main.kts"),
        targetFileName = "check.yml",
        consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
        permissions = mapOf(Permission.Contents to Mode.Read),
    ) {
        job(
            id = "build",
            name = "Build",
            runsOn = UbuntuLatest,
        ) {
            conditionalSetupSteps()
            run(
                name = "Run check",
                command = CheckWorkflow.checkCommand.ref.expression,
            )
        }
    }
}
```

- [ ] **Step 2: Update `ConventionalCommitCheck.kt`**

```kotlin
package workflows.base

import workflows.ConventionalCommitCheckWorkflow
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import java.io.File

fun generateConventionalCommitCheck() {
    workflow(
        name = "Conventional Commit Check",
        on = listOf(
            WorkflowCall(inputs = ConventionalCommitCheckWorkflow.inputs),
        ),
        sourceFile = File(".github/workflow-src/conventional-commit-check.main.kts"),
        targetFileName = "conventional-commit-check.yml",
        consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
    ) {
        job(
            id = "check-title",
            name = "Check PR Title",
            runsOn = UbuntuLatest,
        ) {
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
                    "ALLOWED_TYPES" to ConventionalCommitCheckWorkflow.allowedTypes.ref.expression,
                ),
            )
        }
    }
}
```

- [ ] **Step 3: Update `CreateTag.kt`**

```kotlin
package workflows.base

import actions.CreateAppTokenAction
import actions.GithubTagAction
import workflows.CreateTagWorkflow
import workflows.conditionalSetupSteps
import io.github.typesafegithub.workflows.domain.Mode
import io.github.typesafegithub.workflows.domain.Permission
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import java.io.File

fun generateCreateTag() {
    workflow(
        name = "Create Tag",
        on = listOf(
            WorkflowCall(
                inputs = CreateTagWorkflow.inputs,
                secrets = CreateTagWorkflow.secrets,
            ),
        ),
        sourceFile = File(".github/workflow-src/create-tag.main.kts"),
        targetFileName = "create-tag.yml",
        consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
        permissions = mapOf(Permission.Contents to Mode.Write),
    ) {
        job(
            id = "create_tag",
            name = "Create Tag",
            runsOn = UbuntuLatest,
        ) {
            conditionalSetupSteps(fetchDepth = "0")
            run(
                name = "Run validation",
                command = CreateTagWorkflow.checkCommand.ref.expression,
            )
            uses(
                name = "Generate App Token",
                action = CreateAppTokenAction(
                    appId = CreateTagWorkflow.appId.ref.expression,
                    appPrivateKey = CreateTagWorkflow.appPrivateKey.ref.expression,
                ),
                id = "app-token",
            )
            uses(
                name = "Bump version and push tag",
                action = GithubTagAction(
                    githubToken = "\${{ steps.app-token.outputs.token }}",
                    defaultBump = CreateTagWorkflow.defaultBump.ref.expression,
                    tagPrefix = CreateTagWorkflow.tagPrefix.ref.expression,
                    releaseBranches = CreateTagWorkflow.releaseBranches.ref.expression,
                ),
            )
        }
    }
}
```

- [ ] **Step 4: Update `ManualCreateTag.kt`**

```kotlin
package workflows.base

import actions.CreateAppTokenAction
import workflows.ManualCreateTagWorkflow
import workflows.conditionalSetupSteps
import io.github.typesafegithub.workflows.domain.Mode
import io.github.typesafegithub.workflows.domain.Permission
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import java.io.File

fun generateManualCreateTag() {
    workflow(
        name = "Manual Create Tag",
        on = listOf(
            WorkflowCall(
                inputs = ManualCreateTagWorkflow.inputs,
                secrets = ManualCreateTagWorkflow.secrets,
            ),
        ),
        sourceFile = File(".github/workflow-src/manual-create-tag.main.kts"),
        targetFileName = "manual-create-tag.yml",
        consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
        permissions = mapOf(Permission.Contents to Mode.Write),
    ) {
        job(
            id = "manual_tag",
            name = "Manual Tag",
            runsOn = UbuntuLatest,
        ) {
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
            run(
                name = "Run validation",
                command = ManualCreateTagWorkflow.checkCommand.ref.expression,
            )
            uses(
                name = "Generate App Token",
                action = CreateAppTokenAction(
                    appId = ManualCreateTagWorkflow.appId.ref.expression,
                    appPrivateKey = ManualCreateTagWorkflow.appPrivateKey.ref.expression,
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
                env = linkedMapOf(
                    "GITHUB_TOKEN" to "\${{ steps.app-token.outputs.token }}",
                ),
            )
        }
    }
}
```

- [ ] **Step 5: Update `Release.kt`**

```kotlin
package workflows.base

import workflows.ReleaseWorkflow
import io.github.typesafegithub.workflows.actions.actions.Checkout
import io.github.typesafegithub.workflows.actions.mikepenz.ReleaseChangelogBuilderAction_Untyped
import io.github.typesafegithub.workflows.actions.softprops.ActionGhRelease
import io.github.typesafegithub.workflows.domain.Mode
import io.github.typesafegithub.workflows.domain.Permission
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import java.io.File

fun generateRelease() {
    workflow(
        name = "Release",
        on = listOf(
            ReleaseWorkflow.toWorkflowCallTrigger(),
        ),
        sourceFile = File(".github/workflow-src/release.main.kts"),
        targetFileName = "release.yml",
        consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
        permissions = mapOf(Permission.Contents to Mode.Write),
    ) {
        job(
            id = "release",
            name = "GitHub Release",
            runsOn = UbuntuLatest,
        ) {
            uses(
                name = "Check out",
                action = Checkout(fetchDepth = Checkout.FetchDepth.Value(0)),
            )
            uses(
                name = "Build Changelog",
                action = ReleaseChangelogBuilderAction_Untyped(
                    configuration_Untyped = ReleaseWorkflow.changelogConfig.ref.expression,
                    toTag_Untyped = "\${{ github.ref_name }}",
                ),
                id = "changelog",
                env = linkedMapOf(
                    "GITHUB_TOKEN" to "\${{ secrets.GITHUB_TOKEN }}",
                ),
            )
            uses(
                name = "Create Release",
                action = ActionGhRelease(
                    body = "\${{ steps.changelog.outputs.changelog }}",
                    name = "\${{ github.ref_name }}",
                    tagName = "\${{ github.ref_name }}",
                    draft_Untyped = ReleaseWorkflow.draft.ref.expression,
                ),
                env = linkedMapOf(
                    "GITHUB_TOKEN" to "\${{ secrets.GITHUB_TOKEN }}",
                ),
            )
        }
    }
}
```

- [ ] **Step 6: Update `Publish.kt`**

```kotlin
package workflows.base

import workflows.PublishWorkflow
import workflows.conditionalSetupSteps
import io.github.typesafegithub.workflows.domain.Mode
import io.github.typesafegithub.workflows.domain.Permission
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import java.io.File

fun generatePublish() {
    workflow(
        name = "Publish",
        on = listOf(
            WorkflowCall(
                inputs = PublishWorkflow.inputs,
                secrets = PublishWorkflow.secrets,
            ),
        ),
        sourceFile = File(".github/workflow-src/publish.main.kts"),
        targetFileName = "publish.yml",
        consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
        permissions = mapOf(Permission.Contents to Mode.Read),
    ) {
        job(
            id = "publish",
            name = "Publish",
            runsOn = UbuntuLatest,
        ) {
            conditionalSetupSteps()
            run(
                name = "Publish",
                command = PublishWorkflow.publishCommand.ref.expression,
                env = linkedMapOf(
                    "GRADLE_PUBLISH_KEY" to PublishWorkflow.gradlePublishKey.ref.expression,
                    "GRADLE_PUBLISH_SECRET" to PublishWorkflow.gradlePublishSecret.ref.expression,
                    "ORG_GRADLE_PROJECT_signingKeyId" to PublishWorkflow.mavenSonatypeSigningKeyId.ref.expression,
                    "ORG_GRADLE_PROJECT_signingPublicKey" to PublishWorkflow.mavenSonatypeSigningPubKeyAsciiArmored.ref.expression,
                    "ORG_GRADLE_PROJECT_signingKey" to PublishWorkflow.mavenSonatypeSigningKeyAsciiArmored.ref.expression,
                    "ORG_GRADLE_PROJECT_signingPassword" to PublishWorkflow.mavenSonatypeSigningPassword.ref.expression,
                    "MAVEN_SONATYPE_USERNAME" to PublishWorkflow.mavenSonatypeUsername.ref.expression,
                    "MAVEN_SONATYPE_TOKEN" to PublishWorkflow.mavenSonatypeToken.ref.expression,
                ),
            )
        }
    }
}
```

- [ ] **Step 7: Update `Labeler.kt`**

```kotlin
package workflows.base

import workflows.LabelerWorkflow
import io.github.typesafegithub.workflows.actions.actions.Labeler
import io.github.typesafegithub.workflows.domain.Mode
import io.github.typesafegithub.workflows.domain.Permission
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import java.io.File

fun generateLabeler() {
    workflow(
        name = "PR Labeler",
        on = listOf(
            WorkflowCall(inputs = LabelerWorkflow.inputs),
        ),
        sourceFile = File(".github/workflow-src/labeler.main.kts"),
        targetFileName = "labeler.yml",
        consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
        permissions = mapOf(
            Permission.Contents to Mode.Write,
            Permission.PullRequests to Mode.Write,
        ),
    ) {
        job(
            id = "label",
            name = "Label PR",
            runsOn = UbuntuLatest,
        ) {
            uses(
                name = "Label PR based on file paths",
                action = Labeler(
                    repoToken_Untyped = "\${{ secrets.GITHUB_TOKEN }}",
                    configurationPath_Untyped = LabelerWorkflow.configPath.ref.expression,
                    syncLabels = true,
                ),
            )
        }
    }
}
```

- [ ] **Step 8: Move `AppDeploy.kt` to `base/`**

```bash
mv src/main/kotlin/workflows/adapters/deploy/AppDeploy.kt src/main/kotlin/workflows/base/AppDeploy.kt
rmdir src/main/kotlin/workflows/adapters/deploy
```

Update package declaration and imports in the moved file:

```kotlin
package workflows.base

import workflows.conditionalSetupSteps
import io.github.typesafegithub.workflows.domain.Mode
import io.github.typesafegithub.workflows.domain.Permission
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import java.io.File

fun generateAppDeploy(outputDir: File) {
    workflow(
        name = "Application Deploy",
        on = listOf(
            WorkflowCall(
                inputs = mapOf(
                    "setup-action" to WorkflowCall.Input(
                        "Setup action to use: gradle, go, python", true, WorkflowCall.Type.String
                    ),
                    "setup-params" to WorkflowCall.Input(
                        "JSON object with setup parameters (e.g. {\"java-version\": \"21\"})",
                        false, WorkflowCall.Type.String, "{}"
                    ),
                    "deploy-command" to WorkflowCall.Input(
                        "Command to run for deployment", true, WorkflowCall.Type.String
                    ),
                    "tag" to WorkflowCall.Input(
                        "Tag/version to deploy (checked out at this ref)", true, WorkflowCall.Type.String
                    ),
                )
            ),
        ),
        sourceFile = File(".github/workflow-src/app-deploy.main.kts"),
        targetFileName = "app-deploy.yml",
        consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
        permissions = mapOf(Permission.Contents to Mode.Read),
    ) {
        job(
            id = "deploy",
            name = "Deploy",
            runsOn = UbuntuLatest,
        ) {
            conditionalSetupSteps(fetchDepth = "0")
            run(
                name = "Checkout tag",
                command = "\${{ inputs.tag }}",
            )
            run(
                name = "Deploy",
                command = "\${{ inputs.deploy-command }}",
            )
        }
    }
}
```

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "refactor: update base workflows for new package structure and value classes

All imports updated from dsl.* to workflows.*.
.ref now returns InputRef/SecretRef — use .ref.expression where String needed.
Move AppDeploy from adapters/deploy/ to base/."
```

---

## Task 7: Update all adapter workflows

Update imports, switch to property assignment syntax, use `::JobBuilder` factory, use `setup()` extension where applicable.

**Files:**
- Modify: `src/main/kotlin/workflows/adapters/check/GradleCheck.kt`
- Modify: `src/main/kotlin/workflows/adapters/tag/GradleCreateTag.kt`
- Modify: `src/main/kotlin/workflows/adapters/tag/GradleManualCreateTag.kt`
- Modify: `src/main/kotlin/workflows/adapters/tag/GoCreateTag.kt`
- Modify: `src/main/kotlin/workflows/adapters/tag/GoManualCreateTag.kt`
- Modify: `src/main/kotlin/workflows/adapters/release/AppRelease.kt`
- Modify: `src/main/kotlin/workflows/adapters/release/GradlePluginRelease.kt`
- Modify: `src/main/kotlin/workflows/adapters/release/KotlinLibraryRelease.kt`

- [ ] **Step 1: Update `GradleCheck.kt`**

```kotlin
package workflows.adapters.check

import config.DEFAULT_JAVA_VERSION
import config.JAVA_VERSION_MATRIX_EXPR
import config.SetupTool
import config.reusableWorkflow
import dsl.AdapterWorkflow
import dsl.MatrixDef
import dsl.MatrixRef
import dsl.ReusableWorkflowJobDef
import dsl.reusableJob
import workflows.CheckWorkflow
import workflows.ConventionalCommitCheckWorkflow
import workflows.setup

class GradleCheckAdapter(
    fileName: String,
    override val workflowName: String,
) : AdapterWorkflow(fileName) {
    override val usesString = reusableWorkflow(fileName)

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

    private val javaVersionMatrix = MatrixRef("java-version")

    override fun jobs(): List<ReusableWorkflowJobDef> = listOf(
        reusableJob(id = "conventional-commit", uses = ConventionalCommitCheckWorkflow, ConventionalCommitCheckWorkflow::JobBuilder),
        reusableJob(id = "check", uses = CheckWorkflow, CheckWorkflow::JobBuilder) {
            strategy(MatrixDef(mapOf(javaVersionMatrix.key to JAVA_VERSION_MATRIX_EXPR)))
            setup(SetupTool.Gradle, javaVersionMatrix.ref)
            checkCommand = gradleCommand.ref.expression
        },
    )
}
```

- [ ] **Step 2: Update `GradleCreateTag.kt`**

```kotlin
package workflows.adapters.tag

import config.DEFAULT_JAVA_VERSION
import config.DEFAULT_RELEASE_BRANCHES
import config.SetupTool
import config.reusableWorkflow
import dsl.AdapterWorkflow
import dsl.ReusableWorkflowJobDef
import dsl.reusableJob
import workflows.CreateTagWorkflow
import workflows.setup

object GradleCreateTagAdapter : AdapterWorkflow("gradle-create-tag.yml") {
    override val usesString = reusableWorkflow(fileName)
    override val workflowName = "Gradle Create Tag"

    val javaVersion = input("java-version", description = "JDK version to use", default = DEFAULT_JAVA_VERSION)
    val gradleCommand = input("gradle-command", description = "Gradle check command", default = "./gradlew check")
    val defaultBump = input("default-bump", description = "Default version bump type (major, minor, patch)", default = "patch")
    val tagPrefix = input("tag-prefix", description = "Prefix for the tag", default = "")
    val releaseBranches = input("release-branches", description = "Comma-separated branch patterns for releases", default = DEFAULT_RELEASE_BRANCHES)

    override fun jobs(): List<ReusableWorkflowJobDef> = listOf(
        reusableJob(id = "create-tag", uses = CreateTagWorkflow, CreateTagWorkflow::JobBuilder) {
            setup(SetupTool.Gradle, javaVersion.ref.expression)
            checkCommand = gradleCommand.ref.expression
            defaultBump = this@GradleCreateTagAdapter.defaultBump.ref.expression
            tagPrefix = this@GradleCreateTagAdapter.tagPrefix.ref.expression
            releaseBranches = this@GradleCreateTagAdapter.releaseBranches.ref.expression
            passthroughAllSecrets()
        },
    )
}
```

- [ ] **Step 3: Update `GradleManualCreateTag.kt`**

```kotlin
package workflows.adapters.tag

import config.DEFAULT_JAVA_VERSION
import config.SetupTool
import config.reusableWorkflow
import dsl.AdapterWorkflow
import dsl.ReusableWorkflowJobDef
import dsl.reusableJob
import workflows.ManualCreateTagWorkflow
import workflows.setup

object GradleManualCreateTagAdapter : AdapterWorkflow("gradle-manual-create-tag.yml") {
    override val usesString = reusableWorkflow(fileName)
    override val workflowName = "Gradle Manual Create Tag"

    val tagVersion = input("tag-version", description = "Version to tag (e.g. 1.2.3)", required = true)
    val javaVersion = input("java-version", description = "JDK version to use", default = DEFAULT_JAVA_VERSION)
    val gradleCommand = input("gradle-command", description = "Gradle check command", default = "./gradlew check")
    val tagPrefix = input("tag-prefix", description = "Prefix for the tag", default = "")

    override fun jobs(): List<ReusableWorkflowJobDef> = listOf(
        reusableJob(id = "manual-tag", uses = ManualCreateTagWorkflow, ManualCreateTagWorkflow::JobBuilder) {
            tagVersion = this@GradleManualCreateTagAdapter.tagVersion.ref.expression
            tagPrefix = this@GradleManualCreateTagAdapter.tagPrefix.ref.expression
            setup(SetupTool.Gradle, javaVersion.ref.expression)
            checkCommand = gradleCommand.ref.expression
            passthroughAllSecrets()
        },
    )
}
```

- [ ] **Step 4: Update `GoCreateTag.kt`**

```kotlin
package workflows.adapters.tag

import config.DEFAULT_GO_VERSION
import config.DEFAULT_RELEASE_BRANCHES
import config.SetupTool
import config.reusableWorkflow
import dsl.AdapterWorkflow
import dsl.ReusableWorkflowJobDef
import dsl.reusableJob
import workflows.CreateTagWorkflow
import workflows.setup

object GoCreateTagAdapter : AdapterWorkflow("go-create-tag.yml") {
    override val usesString = reusableWorkflow(fileName)
    override val workflowName = "Go Create Tag"

    val goVersion = input("go-version", description = "Go version to use", default = DEFAULT_GO_VERSION)
    val checkCommand = input("check-command", description = "Go validation command", default = "make test")
    val defaultBump = input("default-bump", description = "Default version bump type (major, minor, patch)", default = "patch")
    val tagPrefix = input("tag-prefix", description = "Prefix for the tag", default = "v")
    val releaseBranches = input("release-branches", description = "Comma-separated branch patterns for releases", default = DEFAULT_RELEASE_BRANCHES)

    override fun jobs(): List<ReusableWorkflowJobDef> = listOf(
        reusableJob(id = "create-tag", uses = CreateTagWorkflow, CreateTagWorkflow::JobBuilder) {
            setup(SetupTool.Go, goVersion.ref.expression)
            checkCommand = this@GoCreateTagAdapter.checkCommand.ref.expression
            defaultBump = this@GoCreateTagAdapter.defaultBump.ref.expression
            tagPrefix = this@GoCreateTagAdapter.tagPrefix.ref.expression
            releaseBranches = this@GoCreateTagAdapter.releaseBranches.ref.expression
            passthroughAllSecrets()
        },
    )
}
```

- [ ] **Step 5: Update `GoManualCreateTag.kt`**

```kotlin
package workflows.adapters.tag

import config.DEFAULT_GO_VERSION
import config.SetupTool
import config.reusableWorkflow
import dsl.AdapterWorkflow
import dsl.ReusableWorkflowJobDef
import dsl.reusableJob
import workflows.ManualCreateTagWorkflow
import workflows.setup

object GoManualCreateTagAdapter : AdapterWorkflow("go-manual-create-tag.yml") {
    override val usesString = reusableWorkflow(fileName)
    override val workflowName = "Go Manual Create Tag"

    val tagVersion = input("tag-version", description = "Version to tag (e.g. 1.2.3)", required = true)
    val goVersion = input("go-version", description = "Go version to use", default = DEFAULT_GO_VERSION)
    val checkCommand = input("check-command", description = "Go validation command", default = "make test")
    val tagPrefix = input("tag-prefix", description = "Prefix for the tag", default = "v")

    override fun jobs(): List<ReusableWorkflowJobDef> = listOf(
        reusableJob(id = "manual-tag", uses = ManualCreateTagWorkflow, ManualCreateTagWorkflow::JobBuilder) {
            tagVersion = this@GoManualCreateTagAdapter.tagVersion.ref.expression
            tagPrefix = this@GoManualCreateTagAdapter.tagPrefix.ref.expression
            setup(SetupTool.Go, goVersion.ref.expression)
            checkCommand = this@GoManualCreateTagAdapter.checkCommand.ref.expression
            passthroughAllSecrets()
        },
    )
}
```

- [ ] **Step 6: Update `AppRelease.kt`**

```kotlin
package workflows.adapters.release

import config.DEFAULT_CHANGELOG_CONFIG
import config.reusableWorkflow
import dsl.AdapterWorkflow
import dsl.ReusableWorkflowJobDef
import dsl.reusableJob
import workflows.ReleaseWorkflow

object AppReleaseAdapter : AdapterWorkflow("app-release.yml") {
    override val usesString = reusableWorkflow(fileName)
    override val workflowName = "Application Release"

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

    override fun jobs(): List<ReusableWorkflowJobDef> = listOf(
        reusableJob(id = "release", uses = ReleaseWorkflow, ReleaseWorkflow::JobBuilder) {
            changelogConfig = this@AppReleaseAdapter.changelogConfig.ref.expression
            draft = this@AppReleaseAdapter.draft.ref.expression
        },
    )
}
```

- [ ] **Step 7: Update `GradlePluginRelease.kt`**

```kotlin
package workflows.adapters.release

import config.DEFAULT_CHANGELOG_CONFIG
import config.DEFAULT_JAVA_VERSION
import config.SetupTool
import config.reusableWorkflow
import dsl.AdapterWorkflow
import dsl.ReusableWorkflowJobDef
import dsl.reusableJob
import workflows.PublishWorkflow
import workflows.ReleaseWorkflow
import workflows.setup

object GradlePluginReleaseAdapter : AdapterWorkflow("gradle-plugin-release.yml") {
    override val usesString = reusableWorkflow(fileName)
    override val workflowName = "Gradle Plugin Release"

    val javaVersion = input(
        "java-version",
        description = "JDK version to use",
        default = DEFAULT_JAVA_VERSION,
    )
    val publishCommand = input(
        "publish-command",
        description = "Gradle publish command (publishes to both Maven Central and Gradle Portal)",
        required = true,
    )
    val changelogConfig = input(
        "changelog-config",
        description = "Path to changelog configuration file",
        default = DEFAULT_CHANGELOG_CONFIG,
    )

    override fun jobs(): List<ReusableWorkflowJobDef> = listOf(
        reusableJob(id = "release", uses = ReleaseWorkflow, ReleaseWorkflow::JobBuilder) {
            changelogConfig = this@GradlePluginReleaseAdapter.changelogConfig.ref.expression
        },
        reusableJob(id = "publish", uses = PublishWorkflow, PublishWorkflow::JobBuilder) {
            needs("release")
            setup(SetupTool.Gradle, javaVersion.ref.expression)
            publishCommand = this@GradlePluginReleaseAdapter.publishCommand.ref.expression
            passthroughAllSecrets()
        },
    )
}
```

- [ ] **Step 8: Update `KotlinLibraryRelease.kt`**

```kotlin
package workflows.adapters.release

import config.DEFAULT_CHANGELOG_CONFIG
import config.DEFAULT_JAVA_VERSION
import config.SetupTool
import config.reusableWorkflow
import dsl.AdapterWorkflow
import dsl.ReusableWorkflowJobDef
import dsl.reusableJob
import workflows.PublishWorkflow
import workflows.ReleaseWorkflow
import workflows.setup

object KotlinLibraryReleaseAdapter : AdapterWorkflow("kotlin-library-release.yml") {
    override val usesString = reusableWorkflow(fileName)
    override val workflowName = "Kotlin Library Release"

    val javaVersion = input(
        "java-version",
        description = "JDK version to use",
        default = DEFAULT_JAVA_VERSION,
    )
    val publishCommand = input(
        "publish-command",
        description = "Gradle publish command for Maven Central",
        required = true,
    )
    val changelogConfig = input(
        "changelog-config",
        description = "Path to changelog configuration file",
        default = DEFAULT_CHANGELOG_CONFIG,
    )

    override fun jobs(): List<ReusableWorkflowJobDef> = listOf(
        reusableJob(id = "release", uses = ReleaseWorkflow, ReleaseWorkflow::JobBuilder) {
            changelogConfig = this@KotlinLibraryReleaseAdapter.changelogConfig.ref.expression
        },
        reusableJob(id = "publish", uses = PublishWorkflow, PublishWorkflow::JobBuilder) {
            needs("release")
            setup(SetupTool.Gradle, javaVersion.ref.expression)
            publishCommand = this@KotlinLibraryReleaseAdapter.publishCommand.ref.expression
            passthroughSecrets(
                PublishWorkflow.mavenSonatypeUsername,
                PublishWorkflow.mavenSonatypeToken,
                PublishWorkflow.mavenSonatypeSigningKeyId,
                PublishWorkflow.mavenSonatypeSigningPubKeyAsciiArmored,
                PublishWorkflow.mavenSonatypeSigningKeyAsciiArmored,
                PublishWorkflow.mavenSonatypeSigningPassword,
            )
        },
    )
}
```

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "refactor: update adapters — property assignment, ::JobBuilder factory, setup() extensions"
```

---

## Task 8: Update Generate.kt and verify

**Files:**
- Modify: `src/main/kotlin/generate/Generate.kt`

- [ ] **Step 1: Update `Generate.kt` imports**

```kotlin
package generate

import workflows.adapters.check.GradleCheckAdapter
import workflows.base.generateAppDeploy
import workflows.adapters.release.AppReleaseAdapter
import workflows.adapters.release.GradlePluginReleaseAdapter
import workflows.adapters.release.KotlinLibraryReleaseAdapter
import workflows.adapters.tag.GradleCreateTagAdapter
import workflows.adapters.tag.GradleManualCreateTagAdapter
import workflows.adapters.tag.GoCreateTagAdapter
import workflows.adapters.tag.GoManualCreateTagAdapter
import workflows.base.generateCheck
import workflows.base.generateConventionalCommitCheck
import workflows.base.generateCreateTag
import workflows.base.generateLabeler
import workflows.base.generateManualCreateTag
import workflows.base.generatePublish
import workflows.base.generateRelease
import java.io.File

fun main() {
    val outputDir = File(".github/workflows")

    generateCheck()
    generateConventionalCommitCheck()
    generateCreateTag()
    generateManualCreateTag()
    generateRelease()
    generatePublish()
    generateLabeler()

    // Adapters
    GradleCheckAdapter("app-check.yml", "Application Check").generate(outputDir)
    GradleCheckAdapter("gradle-check.yml", "Gradle Check").generate(outputDir)
    GradleCheckAdapter("gradle-plugin-check.yml", "Gradle Plugin Check").generate(outputDir)
    GradleCheckAdapter("kotlin-library-check.yml", "Kotlin Library Check").generate(outputDir)
    AppReleaseAdapter.generate(outputDir)
    generateAppDeploy(outputDir)
    GradleCreateTagAdapter.generate(outputDir)
    GradleManualCreateTagAdapter.generate(outputDir)
    GradlePluginReleaseAdapter.generate(outputDir)
    KotlinLibraryReleaseAdapter.generate(outputDir)
    GoCreateTagAdapter.generate(outputDir)
    GoManualCreateTagAdapter.generate(outputDir)
}
```

- [ ] **Step 2: Compile**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run generation**

```bash
./gradlew run
```

Expected: BUILD SUCCESSFUL. All YAML files regenerated.

- [ ] **Step 4: Verify YAML output is identical**

```bash
diff -r /tmp/ci-workflows-baseline .github/workflows/
```

Expected: No output (no differences). If there are differences, investigate and fix before proceeding.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/generate/Generate.kt
git commit -m "refactor: update Generate.kt imports for new package structure"
```

---

## Task 9: Clean up empty directories and final verification

- [ ] **Step 1: Remove any empty directories left behind**

```bash
find src/main/kotlin/dsl -type d -empty -delete 2>/dev/null
find src/main/kotlin/workflows/adapters/deploy -type d -empty -delete 2>/dev/null
```

- [ ] **Step 2: Full clean build**

```bash
./gradlew clean run
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Final YAML diff**

```bash
diff -r /tmp/ci-workflows-baseline .github/workflows/
```

Expected: No differences.

- [ ] **Step 4: Commit if any cleanup was needed**

```bash
git add -A
git diff --cached --quiet || git commit -m "chore: remove empty directories after module split"
```

---

## Summary of changes per spec section

| Spec Section | Task(s) |
|---|---|
| 1. Multi-Module Structure | Tasks 2, 3, 4, 6, 7, 8 |
| 2.1 Value classes for ref-strings | Task 3 (ReusableWorkflow.kt, MatrixDef.kt) |
| 2.2 SetupTool typed extensions | Task 4 (WorkflowHelpers.kt), Task 7 (adapters) |
| 2.3 MatrixRef typing | Task 3 (MatrixDef.kt), Task 5 (SetupTool.kt) |
| 2.4 Actions immutability | Task 5 (Actions.kt) |
| 3.1 Property delegates | Task 3 (ReusableWorkflowJobBuilder.kt), Task 4 (Workflows.kt) |
| 3.2 Remove createJobBuilder | Task 3 (ReusableWorkflow.kt), Task 7 (adapters) |
| 4.1 MATRIX_EXPR decomposition | Task 5 (Defaults.kt) |
| 4.2 mapValues idiom | Task 3 (ReusableWorkflow.kt) |
| 4.3 collectSecretsFromJobs | Task 3 (AdapterWorkflow.kt) |
| 4.4 Sealed interfaces | Task 3 (AdapterWorkflowYaml.kt) |
| Move AppDeploy to base/ | Task 6 |
| Verification | Tasks 1, 8, 9 |
