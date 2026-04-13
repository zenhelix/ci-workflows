# Typed Reusable Workflow DSL Design

## Problem

Base and adapter workflows in the project heavily use `_customArguments` (untyped `Map<String, Any>`) for:
- `workflow_call` trigger inputs/secrets
- Workflow-level permissions
- Reusable workflow job calls (`uses`, `with`, `secrets`, `needs`, `strategy`)

This bypasses compile-time checks and makes typos/missing fields runtime errors.

## Solution

Replace `_customArguments` with typed DSL constructs at two levels:

1. **Base workflows** — use `github-workflows-kt`'s built-in typed `WorkflowCall(inputs=..., secrets=...)` trigger and `permissions` parameter
2. **Adapter workflows** — use a custom `ReusableWorkflow` DSL that wraps `_customArguments` behind a type-safe builder

Each base workflow is described as a singleton `object` that serves as the **single source of truth** for its inputs and secrets. This object is used both in the base workflow (for the `WorkflowCall` trigger) and in adapter workflows (for calling it).

## Design

### ReusableWorkflow base class

```kotlin
abstract class ReusableWorkflow(val fileName: String) {
    private val _inputs = mutableMapOf<String, WorkflowCall.Input>()
    private val _secrets = mutableMapOf<String, WorkflowCall.Secret>()

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

    protected fun secret(
        name: String,
        description: String,
        required: Boolean = true,
    ): WorkflowSecret {
        _secrets[name] = WorkflowCall.Secret(description, required)
        return WorkflowSecret(name)
    }

    val inputs: Map<String, WorkflowCall.Input> get() = _inputs
    val secrets: Map<String, WorkflowCall.Secret> get() = _secrets
    val usesString: String get() = reusableWorkflow(fileName)
}

class WorkflowInput(val name: String)
class WorkflowSecret(val name: String)
```

### Workflow object definitions (one per base workflow)

```kotlin
object CheckWorkflow : ReusableWorkflow("check.yml") {
    val setupAction = input("setup-action",
        description = "Setup action to use: gradle, go", required = true)
    val setupParams = input("setup-params",
        description = "JSON object with setup parameters", default = "{}")
    val checkCommand = input("check-command",
        description = "Command to run for checking", required = true)
}

object CreateTagWorkflow : ReusableWorkflow("create-tag.yml") {
    val setupAction = input("setup-action",
        description = "Setup action to use: gradle, go", required = true)
    val setupParams = input("setup-params",
        description = "JSON object with setup parameters", default = "{}")
    val checkCommand = input("check-command",
        description = "Validation command to run before tagging", required = true)
    val defaultBump = input("default-bump",
        description = "Default version bump type (major, minor, patch)", default = "patch")
    val tagPrefix = input("tag-prefix",
        description = "Prefix for the tag (e.g. v)", default = "")
    val releaseBranches = input("release-branches",
        description = "Comma-separated branch patterns for releases",
        default = DEFAULT_RELEASE_BRANCHES)
    val appId = secret("app-id", description = "GitHub App ID for generating commit token")
    val appPrivateKey = secret("app-private-key",
        description = "GitHub App private key for generating commit token")
}

// Similar objects for: ManualCreateTagWorkflow, ReleaseWorkflow,
// PublishWorkflow, ConventionalCommitCheckWorkflow, LabelerWorkflow
```

### ReusableWorkflowJobBuilder

```kotlin
class ReusableWorkflowJobBuilder(private val workflow: ReusableWorkflow) {
    private val withMap = mutableMapOf<String, String>()
    private val secretsMap = mutableMapOf<String, String>()
    private var needsList: List<String>? = null
    private var matrixMap: Map<String, Any>? = null

    operator fun WorkflowInput.invoke(value: String) {
        withMap[name] = value
    }

    fun secrets(map: Map<String, String>) {
        secretsMap.putAll(map)
    }

    fun needs(vararg jobIds: String) {
        needsList = jobIds.toList()
    }

    fun strategy(matrix: Map<String, Any>) {
        matrixMap = matrix
    }

    internal fun toCustomArguments(): Map<String, Any?> = buildMap {
        put("uses", workflow.usesString)
        if (withMap.isNotEmpty()) put("with", withMap.toMap())
        if (secretsMap.isNotEmpty()) put("secrets", secretsMap.toMap())
        if (needsList != null) put("needs", needsList)
        if (matrixMap != null) put("strategy", mapOf("matrix" to matrixMap))
    }
}
```

### reusableWorkflowJob() extension function

```kotlin
fun WorkflowBuilder.reusableWorkflowJob(
    id: String,
    name: String? = null,
    uses: ReusableWorkflow,
    condition: String? = null,
    block: ReusableWorkflowJobBuilder.() -> Unit = {},
) {
    val builder = ReusableWorkflowJobBuilder(uses).apply(block)
    job(
        id = id,
        name = name,
        runsOn = RunnerType.UbuntuLatest,
        condition = condition,
        _customArguments = builder.toCustomArguments(),
    ) { noop() }
}
```

### Usage in base workflows (e.g. Check.kt)

Before:
```kotlin
workflow(
    ...,
    _customArguments = mapOf(
        "on" to mapOf("workflow_call" to mapOf(
            "inputs" to mapOf(SETUP_ACTION_INPUT, SETUP_PARAMS_INPUT, CHECK_COMMAND_INPUT),
        )),
        "permissions" to mapOf("contents" to "read"),
    ),
) { ... }
```

After:
```kotlin
workflow(
    ...,
    on = listOf(WorkflowDispatch(), WorkflowCall(inputs = CheckWorkflow.inputs)),
    permissions = mapOf(Permission.Contents to Mode.Read),
) { ... }
```

### Usage in adapter workflows (e.g. AppCheck.kt)

Before:
```kotlin
job(id = "check", runsOn = UbuntuLatest,
    _customArguments = mapOf(
        "strategy" to mapOf("matrix" to mapOf("java-version" to JAVA_VERSION_MATRIX_EXPR)),
        "uses" to reusableWorkflow("check.yml"),
        "with" to mapOf(
            "setup-action" to "gradle",
            "setup-params" to "{\"java-version\": \"\${{ matrix.java-version }}\"}",
            "check-command" to "\${{ inputs.gradle-command }}",
        ),
    ),
) { noop() }
```

After:
```kotlin
reusableWorkflowJob(id = "check", uses = CheckWorkflow) {
    strategy(mapOf("java-version" to JAVA_VERSION_MATRIX_EXPR))
    CheckWorkflow.setupAction("gradle")
    CheckWorkflow.setupParams("""{"java-version": "${'$'}{{ matrix.java-version }}"}""")
    CheckWorkflow.checkCommand(expr("inputs.gradle-command"))
}
```

## File changes

### New files
- `shared/dsl/ReusableWorkflow.kt` — base class, WorkflowInput, WorkflowSecret
- `shared/dsl/ReusableWorkflowJobBuilder.kt` — builder + reusableWorkflowJob() extension
- `shared/dsl/Workflows.kt` — all ReusableWorkflow object definitions

### Modified files
- `shared/Inputs.kt` — remove `stringInput()`, `booleanInput()`, `secretInput()`, `workflowCallInput()`, `SETUP_ACTION_INPUT`, `SETUP_PARAMS_INPUT`, `CHECK_COMMAND_INPUT`, `APP_ID_SECRET`, `APP_PRIVATE_KEY_SECRET`. Keep passthrough constants only.
- Base workflows (7): replace `_customArguments` for `on`/`permissions` with typed `WorkflowCall` + `permissions`
- Adapter workflows (11): replace job-level `_customArguments` with `reusableWorkflowJob()` DSL; replace workflow-level `_customArguments` for `on.workflow_call` with typed `WorkflowCall(inputs = ...)`

### Unchanged files
- `shared/DslHelpers.kt` — `conditionalSetupSteps()` and `noop()` remain
- `shared/PostProcessing.kt` — `cleanReusableWorkflowJobs()` remains (reusableWorkflowJob still generates runs-on + steps that need cleaning)
- `shared/Constants.kt` — unchanged
- `shared/Actions.kt` — unchanged
