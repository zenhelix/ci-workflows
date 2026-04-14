# Kotlin-Way Refactoring: Module Split, Typing, and Code Quality

## Goal

Refactor the CI workflow generator to follow Kotlin idioms: clear module boundaries between DSL framework and business logic, strict typing for ref-strings, reduced boilerplate via property delegates, and improved readability across the codebase.

## Non-Goals

- Changing the dual generation approach (github-workflows-kt for base workflows, kaml for adapters)
- Altering the three-level architecture (base -> adapter -> project)
- Changing generated YAML output (must remain identical)

---

## 1. Multi-Module Structure

Split into two Gradle modules with a unidirectional dependency.

### `:workflow-dsl` module (framework, reusable)

```
workflow-dsl/src/main/kotlin/dsl/
  ReusableWorkflow.kt        -- base class + WorkflowInput/WorkflowSecret + value classes
  AdapterWorkflow.kt         -- adapter base class + YAML generation
  ReusableWorkflowJobBuilder.kt -- builder + InputProperty delegate + ReusableWorkflowJobDef
  MatrixDef.kt               -- MatrixDef, MatrixRef, MatrixRefExpr
  yaml/
    AdapterWorkflowYaml.kt   -- DTOs + kaml serialization
```

Contains NO references to `zenhelix`, `gradle`, `go`, or any project-specific concepts.

Dependencies: `github-workflows-kt`, `kaml`, `kotlinx-serialization-core`.

### `:ci-workflows` module (application)

```
src/main/kotlin/
  actions/
    Actions.kt               -- SetupAction, CreateAppTokenAction, GithubTagAction
  config/
    Defaults.kt              -- version constants, JAVA_VERSION_MATRIX_EXPR
    Refs.kt                  -- reusableWorkflow(), localAction()
    SetupTool.kt             -- sealed class Gradle/Go/Python
  workflows/
    Workflows.kt             -- CheckWorkflow, CreateTagWorkflow, etc.
    WorkflowHelpers.kt       -- conditionalSetupSteps()
    base/                    -- generateCheck(), generateRelease(), generateAppDeploy()...
    adapters/
      check/                 -- GradleCheckAdapter
      tag/                   -- GradleCreateTag, GoCreateTag, etc.
      release/               -- AppRelease, GradlePluginRelease, KotlinLibraryRelease
  generate/
    Generate.kt              -- main()
```

Dependencies: `:workflow-dsl`, `github-workflows-kt`, action bindings.

### Key decisions

- `generateAppDeploy` moves from `adapters/deploy/` to `base/` -- it defines steps directly, not wrapping other reusable workflows.
- `config/Refs.kt` stays in application module (contains `zenhelix`-specific paths).
- `WorkflowHelpers.kt` stays in application module (depends on `SetupTool`).

---

## 2. Typing and Strictness

### 2.1 Value classes for ref-strings (in `:workflow-dsl`)

```kotlin
@JvmInline value class InputRef(val expression: String)
@JvmInline value class SecretRef(val expression: String)
@JvmInline value class MatrixRefExpr(val expression: String)
```

`WorkflowInput.ref` returns `InputRef`, `WorkflowSecret.ref` returns `SecretRef`, `MatrixRef.ref` returns `MatrixRefExpr`.

`ReusableWorkflowJobBuilder.setInput()` accepts both `String` (literal values) and `InputRef` (typed refs) via overloads.

### 2.2 SetupTool typed extensions (in `:ci-workflows`)

Extension functions on specific `JobBuilder` types:

```kotlin
fun CheckWorkflow.JobBuilder.setupParams(tool: SetupTool, versionExpr: String) {
    setupAction = tool.id
    setupParams = tool.toParamsJson(versionExpr)
}
```

This collapses two builder calls into one typed call. `SetupTool` stays in the application module; the extension bridges the gap.

### 2.3 MatrixRef typing

`SetupTool.toParamsJson` accepts `MatrixRefExpr` instead of raw `String`:

```kotlin
fun toParamsJson(versionExpr: MatrixRefExpr): String =
    """{"$versionKey": "${versionExpr.expression}"}"""
```

### 2.4 Actions immutability

Replace `.apply { put() }` with `buildMap`:

```kotlin
override fun toYamlArguments() = buildMap {
    put(versionKey, version)
    fetchDepth?.let { put("fetch-depth", it) }
}
```

---

## 3. Boilerplate Reduction

### 3.1 Property delegates for JobBuilder inputs

New `InputProperty` delegate in `:workflow-dsl`:

```kotlin
class InputProperty(private val input: WorkflowInput) {
    operator fun setValue(builder: ReusableWorkflowJobBuilder, property: KProperty<*>, value: String) {
        builder.setInput(input, value)
    }
}

fun inputProp(input: WorkflowInput) = InputProperty(input)
```

Before:
```kotlin
class JobBuilder : ReusableWorkflowJobBuilder(CheckWorkflow) {
    fun setupAction(value: String) = set(CheckWorkflow.setupAction, value)
    fun setupParams(value: String) = set(CheckWorkflow.setupParams, value)
    fun checkCommand(value: String) = set(CheckWorkflow.checkCommand, value)
}
```

After:
```kotlin
class JobBuilder : ReusableWorkflowJobBuilder(CheckWorkflow) {
    var setupAction by inputProp(CheckWorkflow.setupAction)
    var setupParams by inputProp(CheckWorkflow.setupParams)
    var checkCommand by inputProp(CheckWorkflow.checkCommand)
}
```

### 3.2 Remove `createJobBuilder()` and unchecked casts

Remove `abstract fun createJobBuilder()` from `ReusableWorkflow`. Remove `override` from `AdapterWorkflow`.

`reusableJob` takes an explicit factory parameter:

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

Call site: `reusableJob(id = "check", uses = CheckWorkflow, ::JobBuilder) { ... }`

No `@Suppress("UNCHECKED_CAST")`.

---

## 4. Readability Improvements

### 4.1 JAVA_VERSION_MATRIX_EXPR decomposition

```kotlin
private const val MATRIX_FALLBACK_TEMPLATE = """["{0}"]"""

const val JAVA_VERSION_MATRIX_EXPR =
    "\${{ fromJson(inputs.java-versions || format('$MATRIX_FALLBACK_TEMPLATE', inputs.java-version)) }}"
```

### 4.2 `toInputsYaml()` / `toSecretsYaml()` -- idiomatic Kotlin

Replace `.map { }.toMap()` with `mapValues` + `takeIf`:

```kotlin
fun toSecretsYaml(): Map<String, SecretYaml>? =
    _secrets.takeIf { it.isNotEmpty() }?.mapValues { (_, secret) ->
        SecretYaml(description = secret.description, required = secret.required)
    }
```

### 4.3 `collectSecretsFromJobs` simplification

Use `flatMapTo(mutableSetOf())` to avoid intermediate list. Store only `description: String` instead of full `Secret` objects.

### 4.4 Sealed interfaces

`NeedsYaml` and `YamlDefault` become `sealed interface` instead of `sealed class` -- no empty constructor, cleaner Kotlin idiom.

---

## Verification

After refactoring, `./gradlew run` must produce byte-identical YAML output. Compare with `git diff` against the pre-refactoring generated files.
