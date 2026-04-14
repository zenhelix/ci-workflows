# Deep Architecture Refactoring — Design Spec

## Goal

Refactor ci-workflows Kotlin DSL project to achieve:
- Clear separation of infrastructure (DSL, builders, capabilities) from concrete workflow implementations
- Unified generation interface hiding the two YAML generation mechanisms (github-workflows-kt for base, custom kaml for adapters)
- Composable capabilities replacing duplicated boilerplate
- Logical package structure reflecting architectural layers
- Kotlin-idiomatic patterns throughout

**Constraint:** `github-workflows-kt` 3.7.0 does not support job-level reusable workflow calls (`uses:` on jobs). Two generation mechanisms are an objective library limitation, not an architectural choice. The refactoring hides this behind a unified interface.

**Invariant:** Generated YAML files must remain semantically identical after refactoring.

---

## 1. Package Structure

### workflow-dsl module

```
workflow-dsl/src/main/kotlin/dsl/
├── core/                          # Core abstractions — zero business logic
│   ├── ReusableWorkflow.kt        # Abstract reusable workflow base class
│   ├── WorkflowInput.kt           # InputRef, SecretRef, WorkflowInput, WorkflowSecret
│   ├── InputRegistry.kt           # Input registration and tracking
│   └── MatrixDef.kt               # Matrix strategy types (MatrixRef, MatrixDef, MatrixRefExpr)
│
├── builder/                       # Builders — workflow/job construction
│   ├── ReusableWorkflowJobBuilder.kt  # Base job builder with input/secret/needs/strategy
│   ├── InputProperty.kt           # Unified generic property delegate (replaces StringInputProperty + RefInputProperty)
│   ├── AdapterWorkflowBuilder.kt  # DSL builder for adapter workflows
│   └── AdapterWorkflow.kt         # Adapter workflow model + YAML generation
│
├── capability/                    # Composable capabilities
│   └── SetupCapability.kt         # Setup tool capability interface + helpers
│
└── yaml/                          # YAML serialization (unchanged)
    ├── AdapterWorkflowYaml.kt
    └── InputsYamlMapper.kt        # toInputsYaml() mapping function
```

### main module

```
src/main/kotlin/
├── config/                        # Configuration constants (unchanged)
│   ├── Defaults.kt
│   ├── Refs.kt
│   ├── SetupTool.kt
│   └── ToolTagConfig.kt
│
├── actions/                       # Custom action wrappers (unchanged)
│   ├── SetupAction.kt
│   ├── GithubTagAction.kt
│   └── CreateAppTokenAction.kt
│
├── workflows/
│   ├── GeneratableWorkflow.kt     # Unified generation interface
│   ├── ProjectWorkflow.kt         # Base class with generate() boilerplate
│   │
│   ├── base/                      # Base reusable workflow implementations
│   │   ├── CheckWorkflow.kt
│   │   ├── PublishWorkflow.kt
│   │   ├── CreateTagWorkflow.kt
│   │   ├── ManualCreateTagWorkflow.kt
│   │   ├── ReleaseWorkflow.kt
│   │   ├── ConventionalCommitCheckWorkflow.kt
│   │   ├── LabelerWorkflow.kt
│   │   └── AppDeployWorkflow.kt
│   │
│   ├── adapters/                  # Adapter (caller) workflows
│   │   ├── check/
│   │   │   └── GradleCheck.kt
│   │   ├── tag/
│   │   │   ├── CreateTagAdapters.kt
│   │   │   └── ManualCreateTagAdapters.kt
│   │   └── release/
│   │       └── ReleaseAdapters.kt
│   │
│   └── support/                   # Workflow helper extensions
│       └── SetupSteps.kt          # conditionalSetupSteps() + setup() extensions
│
└── generate/
    └── Generate.kt                # Single list of GeneratableWorkflow
```

### Key changes:
- `dsl/` split into `core/`, `builder/`, `capability/`, `yaml/`
- `workflows/` split into `base/`, `adapters/`, `support/`
- `ProjectWorkflow.kt` raised to `workflows/` level (shared base, not "core" DSL)
- `helpers/` renamed to `support/`
- Adapter files consolidated: `AppRelease.kt` + `GradleRelease.kt` -> `ReleaseAdapters.kt`; `CreateTag.kt` -> `CreateTagAdapters.kt`; `ManualCreateTag.kt` -> `ManualCreateTagAdapters.kt`

---

## 2. Unified Generation Interface

### GeneratableWorkflow

```kotlin
// workflows/GeneratableWorkflow.kt
interface GeneratableWorkflow {
    val fileName: String
    fun generate(outputDir: File)
}
```

Both `ProjectWorkflow` (base) and `AdapterWorkflow` (adapter) implement this interface.

**Note on `outputDir`:** Base workflows (via `github-workflows-kt`) determine output path from `sourceFile`/`targetFileName` — the library writes YAML relative to project root automatically. The `outputDir` parameter is used by adapter workflows (custom YAML generation). Base workflows' `generate(outputDir)` ignores the parameter. This is an acceptable trade-off for interface unification — the alternative (no parameter) would require adapter workflows to hardcode their output path.

### ProjectWorkflow — absorbs generate() boilerplate

```kotlin
// workflows/ProjectWorkflow.kt
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

### AdapterWorkflow — adds GeneratableWorkflow

```kotlin
// dsl/builder/AdapterWorkflow.kt
class AdapterWorkflow(
    override val fileName: String,
    // ... existing fields
) : GeneratableWorkflow {
    override fun generate(outputDir: File) {
        // existing logic unchanged
    }
}
```

### Generate.kt — single unified list

```kotlin
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

### Key effects:
- `toWorkflowCallTrigger()` used uniformly for all base workflows (eliminates inconsistency where some used `WorkflowCall(inputs = inputs)` and Release used `toWorkflowCallTrigger()`)
- Each workflow's `generate()` replaced by `implementation()` — only business logic
- `sourceFile`, `targetFileName`, `consistencyCheckJobConfig` computed automatically
- `permissions` passed to constructor, not repeated in every generate()

---

## 3. Composable Capabilities

### SetupCapability — workflow-level contract

```kotlin
// dsl/capability/SetupCapability.kt
interface SetupCapability {
    val setupAction: WorkflowInput
    val setupParams: WorkflowInput

    companion object {
        const val SETUP_ACTION_DESCRIPTION = "Setup action to use: gradle, go, python"
        const val SETUP_PARAMS_DESCRIPTION = """JSON object with setup parameters (e.g. {"java-version": "21"})"""
        const val SETUP_PARAMS_DEFAULT = "{}"
    }
}

fun ReusableWorkflow.setupInputs(): Pair<WorkflowInput, WorkflowInput> {
    val action = input("setup-action", SetupCapability.SETUP_ACTION_DESCRIPTION, required = true)
    val params = input("setup-params", SetupCapability.SETUP_PARAMS_DESCRIPTION, default = SetupCapability.SETUP_PARAMS_DEFAULT)
    return action to params
}
```

### SetupCapableJobBuilder — job builder-level contract

```kotlin
// dsl/capability/SetupCapability.kt
interface SetupCapableJobBuilder {
    var setupAction: String
    var setupParams: String

    fun applySetup(action: String, params: String) {
        setupAction = action
        setupParams = params
    }
}
```

### Usage in workflows

```kotlin
object CheckWorkflow : ProjectWorkflow("check.yml", "Check"), SetupCapability {
    override val setupAction: WorkflowInput
    override val setupParams: WorkflowInput
    init { val (a, p) = setupInputs(); setupAction = a; setupParams = p }

    val checkCommand = input("check-command", "Command to run for checking", required = true)

    class JobBuilder : ReusableWorkflowJobBuilder(CheckWorkflow), SetupCapableJobBuilder {
        override var setupAction by stringInput(CheckWorkflow.setupAction)
        override var setupParams by stringInput(CheckWorkflow.setupParams)
        var checkCommand by refInput(CheckWorkflow.checkCommand)
    }

    // ... job() and implementation() as described
}
```

### conditionalSetupSteps() — extensible via SetupTool.entries

```kotlin
// workflows/support/SetupSteps.kt
fun JobBuilder<*>.conditionalSetupSteps(
    tools: List<SetupTool> = SetupTool.entries,
    fetchDepth: String? = null,
) {
    tools.forEach { tool ->
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

### Workflows affected:
- CheckWorkflow, PublishWorkflow, CreateTagWorkflow, ManualCreateTagWorkflow, AppDeployWorkflow — all gain `SetupCapability` + `SetupCapableJobBuilder`
- ReleaseWorkflow, ConventionalCommitCheckWorkflow, LabelerWorkflow — no capability needed, stay clean

### SetupTool — must use sealed class entries

`SetupTool` is a `sealed class` with `data object` subclasses (Gradle, Go, Python). Unlike `enum`, sealed classes don't have automatic `entries`. Define a companion property manually:

```kotlin
sealed class SetupTool(...) {
    data object Gradle : SetupTool(...)
    data object Go : SetupTool(...)
    data object Python : SetupTool(...)

    companion object {
        val entries: List<SetupTool> = listOf(Gradle, Go, Python)
    }
}
```

Adding a new tool (e.g., `data object Node`) requires adding it to `entries` list. This is a manual step, but the sealed class guarantees exhaustive `when` checks at compile time — if someone adds a subclass and forgets `entries`, any `when(tool)` without a branch will fail to compile.

---

## 4. Unified Property Delegates

Replace `StringInputProperty` and `RefInputProperty` with one generic class:

```kotlin
// dsl/builder/InputProperty.kt
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

Public API (`stringInput()`, `refInput()`) remains unchanged — backward-compatible.

---

## 5. Adapter Workflows — Functions to Objects

Adapter workflows become named properties in container objects instead of top-level factory functions:

### GradleCheck

```kotlin
// workflows/adapters/check/GradleCheck.kt
object GradleCheck {
    val appCheck = gradleCheck("app-check.yml", "Application Check")
    val gradleCheck = gradleCheck("gradle-check.yml", "Gradle Check")
    val gradlePluginCheck = gradleCheck("gradle-plugin-check.yml", "Gradle Plugin Check")
    val kotlinLibraryCheck = gradleCheck("kotlin-library-check.yml", "Kotlin Library Check")

    private fun gradleCheck(fileName: String, name: String): AdapterWorkflow =
        adapterWorkflow(fileName, name) {
            // ... existing DSL logic unchanged
        }
}
```

### CreateTagAdapters

```kotlin
// workflows/adapters/tag/CreateTagAdapters.kt
object CreateTagAdapters {
    val gradle = toolCreateTag("gradle-create-tag.yml", "Gradle Create Tag", GRADLE_TAG)
    val go = toolCreateTag("go-create-tag.yml", "Go Create Tag", GO_TAG)

    private fun toolCreateTag(fileName: String, name: String, config: ToolTagConfig): AdapterWorkflow =
        adapterWorkflow(fileName, name) {
            // ... existing DSL logic unchanged
        }
}
```

### ManualCreateTagAdapters

```kotlin
// workflows/adapters/tag/ManualCreateTagAdapters.kt
object ManualCreateTagAdapters {
    val gradle = toolManualCreateTag("gradle-manual-create-tag.yml", "Gradle Manual Create Tag", GRADLE_TAG)
    val go = toolManualCreateTag("go-manual-create-tag.yml", "Go Manual Create Tag", GO_TAG)

    private fun toolManualCreateTag(fileName: String, name: String, config: ToolTagConfig): AdapterWorkflow =
        adapterWorkflow(fileName, name) {
            // ... existing DSL logic unchanged
        }
}
```

### ReleaseAdapters

```kotlin
// workflows/adapters/release/ReleaseAdapters.kt
object ReleaseAdapters {
    val app: AdapterWorkflow = adapterWorkflow("app-release.yml", "Application Release") {
        // ... existing appRelease logic
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
        // ... existing gradleReleaseWorkflow logic
    }
}
```

### Key effects:
- Factory functions become `private` — implementation details hidden
- Each adapter workflow has a stable name: `GradleCheck.appCheck`, `CreateTagAdapters.gradle`
- Adapter DSL syntax inside builders unchanged
- File count for adapters: 5 files -> 4 files (AppRelease + GradleRelease merged into ReleaseAdapters)

---

## 6. workflow-dsl Module Cleanup

### Extract value types to WorkflowInput.kt

Move `InputRef`, `SecretRef`, `WorkflowInput`, `WorkflowSecret` from `ReusableWorkflow.kt` to `dsl/core/WorkflowInput.kt`. ReusableWorkflow.kt contains only the abstract class.

### Visibility tightening

- `toWorkflowCallTrigger()` — used only by `ProjectWorkflow.generate()`. Can be `internal` or kept as `public` (used across modules).
- `toInputsYaml()`, `toSecretsYaml()` — used only by `AdapterWorkflow`. Keep public (cross-module).
- `inputsAsRawMap()` — already private. No change.

### No changes to:
- `InputRegistry.kt` — logic unchanged, only moves to `dsl/core/`
- `MatrixDef.kt` — moves to `dsl/core/`
- `AdapterWorkflowYaml.kt` — stays in `dsl/yaml/`
- `AdapterWorkflowBuilder.kt` — moves to `dsl/builder/`, logic unchanged
- `InputsYamlMapper.kt` — moves to `dsl/yaml/` (maps inputs to YAML format)

---

## What Does NOT Change

- **YAML serialization** — AdapterWorkflowYaml and all DTOs
- **Config layer** — Defaults, Refs, SetupTool, ToolTagConfig
- **Actions layer** — SetupAction, GithubTagAction, CreateAppTokenAction
- **Adapter DSL syntax** — `adapterWorkflow { }`, `from`, `setup()`, `passthroughSecrets()`, `matrix()`, `matrixRef()`
- **Generated YAML** — must be semantically identical to current output

---

## Verification

After each phase of implementation:
1. Run `./gradlew run` to regenerate all YAML files
2. `git diff .github/workflows/` — verify no semantic changes (cosmetic ordering changes are acceptable if inputs moved to base class)
3. If any YAML diff appears, investigate and either fix or document as intentional
