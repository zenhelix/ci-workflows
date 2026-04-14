# Full DSL Rewrite Design

## Summary

Полная переработка DSL для генерации GitHub Actions workflow YAML. Переход от class-based адаптеров с наследованием к функциональному builder DSL. Устранение дублирования, улучшение читаемости, идиоматичный Kotlin.

**Ключевое ограничение:** генерируемый YAML должен оставаться идентичным.

---

## Архитектура: до и после

### До
```
AdapterWorkflow (abstract class, наследование)
  └── ProjectAdapterWorkflow (abstract, добавляет usesString)
        ├── GradleCheckAdapter (class, override jobs())
        ├── CreateTagAdapter (class, override jobs())
        ├── ManualCreateTagAdapter (class, override jobs())
        ├── KotlinLibraryReleaseAdapter (object, override jobs())
        ├── GradlePluginReleaseAdapter (object, override jobs())
        └── AppReleaseAdapter (object, override jobs())

reusableJob(id, uses, ::JobBuilder) { ... }  — top-level function
```

### После
```
AdapterWorkflow (immutable data class, результат builder-a)
AdapterWorkflowBuilder (mutable DSL scope)
adapterWorkflow(fileName, name) { ... }  — top-level builder function

Workflow.job(id) { ... }  — context(AdapterWorkflowBuilder), factory method на каждом workflow-объекте
```

---

## Секция 1: DSL ядро (workflow-dsl)

### 1.1. `ReusableWorkflow` — метод `buildJob`

Добавляется `protected` метод для создания job definitions. Заменяет top-level `reusableJob()`:

```kotlin
abstract class ReusableWorkflow(val fileName: String) {
    // ... существующие inputs/secrets/booleanDefaults ...

    protected inline fun <B : ReusableWorkflowJobBuilder> buildJob(
        id: String,
        crossinline builderFactory: () -> B,
        block: B.() -> Unit = {},
    ): ReusableWorkflowJobDef {
        val builder = builderFactory()
        builder.block()
        return builder.build(id)
    }
}
```

Top-level `reusableJob()` удаляется.

### 1.2. `ReusableWorkflowJobBuilder` — infix `from`

Для устранения `this@Adapter.field.ref` паттерна:

```kotlin
abstract class ReusableWorkflowJobBuilder(private val workflow: ReusableWorkflow) {
    // ... существующий код ...

    infix fun WorkflowInput.from(source: WorkflowInput) {
        setInput(this, source.ref)
    }
}
```

Использование:
```kotlin
CreateTagWorkflow.checkCommand from checkCommand
// вместо: checkCommand = this@CreateTagAdapter.checkCommand.ref
```

### 1.3. `AdapterWorkflow` — иммутабельный результат

Из abstract class превращается в immutable class:

```kotlin
class AdapterWorkflow(
    val fileName: String,
    val workflowName: String,
    private val inputsYaml: Map<String, InputYaml>?,
    private val jobs: List<ReusableWorkflowJobDef>,
) {
    fun generate(outputDir: File) {
        val collectedSecrets = collectSecretsFromJobs(jobs)
        val dto = AdapterWorkflowYaml(
            name = workflowName,
            on = TriggerYaml(workflowCall = WorkflowCallBodyYaml(inputs = inputsYaml, secrets = collectedSecrets)),
            jobs = jobs.associate { it.id to it.toJobYaml() },
        )
        val header = buildString {
            appendLine("# This file was generated using Kotlin DSL (src/main/kotlin/).")
            appendLine("# If you want to modify the workflow, please change the Kotlin source and regenerate this YAML file.")
            append("# Generated with https://github.com/typesafegithub/github-workflows-kt")
        }
        val body = unquoteYamlMapKeys(adapterWorkflowYaml.encodeToString(AdapterWorkflowYaml.serializer(), dto))
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
```

### 1.4. `AdapterWorkflowBuilder` — новый DSL scope

```kotlin
class AdapterWorkflowBuilder(private val fileName: String, private val name: String) {
    private val inputs = mutableMapOf<String, WorkflowCall.Input>()
    private val booleanDefaults = mutableMapOf<String, Boolean>()
    private val jobs = mutableListOf<ReusableWorkflowJobDef>()

    fun input(name: String, description: String, required: Boolean = false,
              type: WorkflowCall.Type = WorkflowCall.Type.String, default: String? = null): WorkflowInput {
        inputs[name] = WorkflowCall.Input(description, required, type, default)
        return WorkflowInput(name)
    }

    fun booleanInput(name: String, description: String, default: Boolean? = null): WorkflowInput {
        inputs[name] = WorkflowCall.Input(description, false, WorkflowCall.Type.Boolean, null)
        default?.let { booleanDefaults[name] = it }
        return WorkflowInput(name)
    }

    fun matrixRef(key: String) = MatrixRef(key)
    fun matrix(vararg entries: Pair<String, String>) = MatrixDef(mapOf(*entries))

    internal fun registerJob(job: ReusableWorkflowJobDef) { jobs += job }

    fun build(): AdapterWorkflow = AdapterWorkflow(
        fileName = fileName,
        workflowName = name,
        inputsYaml = toInputsYaml(inputs, booleanDefaults),
        jobs = jobs.toList(),
    )
}

fun adapterWorkflow(fileName: String, name: String, block: AdapterWorkflowBuilder.() -> Unit): AdapterWorkflow {
    val builder = AdapterWorkflowBuilder(fileName, name)
    builder.block()
    return builder.build()
}
```

Функция `toInputsYaml` выносится из `ReusableWorkflow` в shared utility (используется и `ReusableWorkflow`, и `AdapterWorkflowBuilder`).

### 1.5. Утилиты `toInputsYaml`

```kotlin
// dsl/InputsYamlMapper.kt или в ReusableWorkflow companion
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

---

## Секция 2: Context parameters для `Workflow.job()`

### Compiler flag

В `build.gradle.kts` (root и workflow-dsl):
```kotlin
kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}
```

### Каждый workflow-объект получает `job()` метод

```kotlin
object CheckWorkflow : ProjectWorkflow("check.yml") {
    val setupAction = input("setup-action", ...)
    val setupParams = input("setup-params", ...)
    val checkCommand = input("check-command", ...)

    class JobBuilder : ReusableWorkflowJobBuilder(CheckWorkflow), SetupConfigurable {
        override var setupAction by stringInput(CheckWorkflow.setupAction)
        override var setupParams by stringInput(CheckWorkflow.setupParams)
        var checkCommand by refInput(CheckWorkflow.checkCommand)
    }

    context(_: AdapterWorkflowBuilder)
    fun job(id: String, block: JobBuilder.() -> Unit = {}) {
        registerJob(buildJob(id, ::JobBuilder, block))
    }
}
```

Аналогично для всех 8 workflow definitions.

Внутри `adapterWorkflow { ... }` лямбды `this` = `AdapterWorkflowBuilder`, что обеспечивает context parameter автоматически:
```kotlin
adapterWorkflow("gradle-check.yml", "Gradle Check") {
    // this: AdapterWorkflowBuilder — context parameter для Workflow.job()
    CheckWorkflow.job("check") { ... }  // registerJob вызывается на this
}
```

---

## Секция 3: Миграция адаптеров

### 3.1. Check-адаптеры

`GradleCheckAdapter` class → `gradleCheck()` function:

```kotlin
fun gradleCheck(fileName: String, name: String) = adapterWorkflow(fileName, name) {
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
```

### 3.2. Tag-адаптеры (устранение дублирования)

`CreateTagAdapter` + `ManualCreateTagAdapter` classes → `toolCreateTag()` + `toolManualCreateTag()`:

```kotlin
fun toolCreateTag(
    fileName: String, name: String,
    tool: SetupTool,
    commandInputName: String, commandDescription: String,
    defaultCommand: String, defaultTagPrefix: String,
) = adapterWorkflow(fileName, name) {
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

fun toolManualCreateTag(
    fileName: String, name: String,
    tool: SetupTool,
    commandInputName: String, commandDescription: String,
    defaultCommand: String, defaultTagPrefix: String,
) = adapterWorkflow(fileName, name) {
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

### 3.3. Release-адаптеры (устранение дублирования)

`KotlinLibraryReleaseAdapter` + `GradlePluginReleaseAdapter` → `gradleReleaseWorkflow()`:

```kotlin
fun gradleReleaseWorkflow(
    fileName: String, name: String,
    publishDescription: String,
    publishSecrets: PublishWorkflow.JobBuilder.() -> Unit = { passthroughAllSecrets() },
) = adapterWorkflow(fileName, name) {
    val javaVersion = input("java-version", description = "JDK version to use", default = DEFAULT_JAVA_VERSION)
    val publishCommand = input("publish-command", description = publishDescription, required = true)
    val changelogConfig = input("changelog-config", description = "Path to changelog configuration file", default = DEFAULT_CHANGELOG_CONFIG)

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

`AppReleaseAdapter` → `val appRelease`:

```kotlin
val appRelease = adapterWorkflow("app-release.yml", "Application Release") {
    val changelogConfig = input("changelog-config", description = "Path to changelog configuration file", default = DEFAULT_CHANGELOG_CONFIG)
    val draft = booleanInput("draft", description = "Create release as draft (default true for apps)", default = true)

    ReleaseWorkflow.job("release") {
        ReleaseWorkflow.changelogConfig from changelogConfig
        ReleaseWorkflow.draft from draft
    }
}
```

---

## Секция 4: Идиоматичный Kotlin

### 4.1. `SetupAction.toYamlArguments()` — `buildMap`
```kotlin
override fun toYamlArguments() = buildMap {
    put(versionKey, version)
    fetchDepth?.let { put("fetch-depth", it) }
}
```

### 4.2. `SetupTool.toParamsJson` — `buildJsonObject`
```kotlin
fun toParamsJson(versionExpr: String): String =
    buildJsonObject { put(versionKey, versionExpr) }.toString()

fun toParamsJson(versionExpr: MatrixRefExpr): String =
    toParamsJson(versionExpr.expression)
```

### 4.3. `ReusableWorkflow.inputsAsRawMap` — убрать explicit type parameter
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

---

## Секция 5: Реструктуризация файлов и пакетов

### 5.1. Actions — один класс на файл
```
actions/
  SetupAction.kt
  CreateAppTokenAction.kt
  GithubTagAction.kt
```

### 5.2. Workflows — подпакеты
```
workflows/
  core/
    ProjectWorkflow.kt
    (ProjectAdapterWorkflow.kt удаляется — адаптеры больше не наследуют)
  helpers/
    SetupHelpers.kt              (переименование из WorkflowHelpers.kt)
  definitions/
    CheckWorkflow.kt
    ConventionalCommitCheckWorkflow.kt
    CreateTagWorkflow.kt
    ManualCreateTagWorkflow.kt
    ReleaseWorkflow.kt
    PublishWorkflow.kt
    LabelerWorkflow.kt
    AppDeployWorkflow.kt
  adapters/
    check/GradleCheck.kt
    tag/CreateTag.kt
    tag/ManualCreateTag.kt
    release/AppRelease.kt
    release/GradleRelease.kt     (заменяет GradlePluginRelease.kt + KotlinLibraryRelease.kt)
  base/
    Check.kt, CreateTag.kt, ManualCreateTag.kt, Release.kt,
    Publish.kt, ConventionalCommitCheck.kt, Labeler.kt, AppDeploy.kt
```

### 5.3. Generate.kt — структурирование
```kotlin
fun main() {
    val outputDir = File(".github/workflows").apply { mkdirs() }
    generateBaseWorkflows()
    generateAdapterWorkflows(outputDir)
}

private fun generateBaseWorkflows() { ... }
private fun generateAdapterWorkflows(outputDir: File) { ... }
```

---

## Секция 6: Сериализация

`kaml` и `AdapterWorkflowYaml` модель **остаются без изменений**. Сериализация работает корректно и не является целью этого рефакторинга.

Единственное изменение: `collectSecretsFromJobs` переезжает из abstract `AdapterWorkflow` в immutable `AdapterWorkflow` class, использует `buildMap`.

---

## Что НЕ меняется

- Base workflow generators (`workflows/base/*.kt`) — используют `github-workflows-kt` DSL, работают хорошо
- Workflow definition objects — структура та же, добавляется только `context(AdapterWorkflowBuilder) fun job()`
- `kaml` сериализация (`AdapterWorkflowYaml.kt`, `TriggerYaml`, `JobYaml`, etc.)
- Генерируемый YAML — **должен остаться идентичным**
- Custom actions YAML (`.github/actions/`)

---

## Порядок реализации

1. **Compiler flag** — `-Xcontext-parameters` в обоих `build.gradle.kts`
2. **workflow-dsl: buildJob + from** — `ReusableWorkflow.buildJob()`, `ReusableWorkflowJobBuilder.from()`
3. **workflow-dsl: AdapterWorkflow split** — immutable `AdapterWorkflow` + `AdapterWorkflowBuilder` + `adapterWorkflow()` builder
4. **toInputsYaml extraction** — shared utility из `ReusableWorkflow`
5. **Workflow definitions: context job()** — `context(AdapterWorkflowBuilder) fun job()` на всех 8 объектах
6. **Миграция адаптеров** — check (gradleCheck) → tag (toolCreateTag, toolManualCreateTag) → release (gradleReleaseWorkflow, appRelease)
7. **Идиоматика** — buildMap, buildJsonObject, убрать explicit types
8. **Actions split** — 3 отдельных файла
9. **Package restructure** — `core/`, `helpers/SetupHelpers.kt`
10. **Generate.kt restructure** — `generateBaseWorkflows()` + `generateAdapterWorkflows()`
11. **Cleanup** — удалить top-level `reusableJob()`, старые adapter classes, `ProjectAdapterWorkflow`
12. **Verification** — `./gradlew run` + `git diff .github/workflows/` = 0 изменений

---

## Риски

- **Context parameters** — experimental feature, может измениться в будущих версиях Kotlin. Mitigation: feature стабильна в 2.1.20+, активно используется в экосистеме.
- **Порядок inputs в YAML** — при миграции с class-based на builder inputs могут оказаться в другом порядке. Mitigation: проверять `git diff` после каждого шага.
- **`usesString` больше не нужен адаптерам** — адаптеры только генерируют YAML, сами не используются как `uses:` target. `usesString` остаётся только в `ReusableWorkflow` / `ProjectWorkflow` для base workflows.
