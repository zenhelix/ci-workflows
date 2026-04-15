# Kotlin Code Conciseness & Patterns Improvement

**Date:** 2026-04-15
**Scope:** workflow-dsl module + main src module
**Branch:** feature/kts-script

## Goal

Make Kotlin code more concise and improve patterns without changing architecture. Each change is independent (except 3→4 dependency).

## Changes

### 1. Extension functions on InputRegistry instead of 6 overloads

**Problem:** 3 identical `input()` overloads in `ReusableWorkflow` and 3 in `AdapterWorkflowBuilder` — all delegate to `InputRegistry.input()` wrapping `default` into `InputDefault(...)`.

**Solution:** Add two extension functions on `InputRegistry` that handle `InputDefault` wrapping:

```kotlin
// InputRegistry.kt
fun InputRegistry.input(name: String, description: String, required: Boolean = false, default: String) =
    input(name, description, required, InputDefault(default))

fun InputRegistry.input(name: String, description: String, required: Boolean = false, default: Boolean) =
    input(name, description, required, InputDefault(default))
```

Both `ReusableWorkflow` and `AdapterWorkflowBuilder` keep 3 `input()` methods each (no default, String default, Boolean default), but now the overloads with `default` simply forward to the extension functions without manually wrapping `InputDefault(...)`. The wrapping logic lives in one place — `InputRegistry` extensions.

**Files:** `InputRegistry.kt`, `ReusableWorkflow.kt`, `AdapterWorkflowBuilder.kt`

### 2. Extract `addSecrets` helper + simplify `secretsAsRawMap`

**Problem 2a:** `passthroughSecrets` and `passthroughAllSecrets` in `ReusableWorkflowJobBuilder` contain identical `forEach` logic.

**Solution:**
```kotlin
private fun addSecrets(secrets: Iterable<WorkflowSecret>) {
    secrets.forEach { secretsMap[it.name] = it.expr }
}

fun passthroughSecrets(vararg secrets: WorkflowSecret) = addSecrets(secrets.asIterable())
fun passthroughAllSecrets() = addSecrets(workflow.secretObjects)
```

**Problem 2b:** `secretsAsRawMap()` in `ReusableWorkflow` uses destructuring + buildMap when `mapOf` suffices.

**Solution:**
```kotlin
private fun secretsAsRawMap(): Map<String, Map<String, Any?>> =
    _secrets.mapValues { (_, pair) ->
        mapOf("description" to pair.first.description, "required" to pair.first.required)
    }
```

**Files:** `ReusableWorkflowJobBuilder.kt`, `ReusableWorkflow.kt`

### 3. Merge CreateTagAdapters + ManualCreateTagAdapters → TagAdapters

**Problem:** Two separate files (~30 lines each) with identical structure: iterate over GRADLE/GO ecosystems, build adapter workflows for tagging.

**Solution:** Merge into one `TagAdapters` object in `workflows/adapters/tag/TagAdapters.kt`:

```kotlin
object TagAdapters {
    val gradleCreateTag = ecosystemCreateTag("gradle-create-tag.yml", "Gradle Create Tag", GRADLE)
    val goCreateTag = ecosystemCreateTag("go-create-tag.yml", "Go Create Tag", GO)

    val gradleManualTag = ecosystemManualTag("gradle-manual-create-tag.yml", "Gradle Manual Create Tag", GRADLE)
    val goManualTag = ecosystemManualTag("go-manual-create-tag.yml", "Go Manual Create Tag", GO)

    private fun ecosystemCreateTag(fileName: String, name: String, eco: EcosystemConfig): AdapterWorkflow = ...
    private fun ecosystemManualTag(fileName: String, name: String, eco: EcosystemConfig): AdapterWorkflow = ...
}
```

Delete both `ManualCreateTagAdapters.kt` and `CreateTagAdapters.kt`. Create new `TagAdapters.kt` in the same `workflows/adapters/tag/` package.

**Files:** Delete `ManualCreateTagAdapters.kt`, delete `CreateTagAdapters.kt`, create `TagAdapters.kt`, update `Generate.kt` imports

### 4. `val all` in adapter objects + simplified Generate.kt

**Problem:** `Generate.kt` has a manual list of 19 workflows. Each new workflow requires editing this file.

**Solution:** Each adapter object exposes `val all: List<AdapterWorkflow>`:

```kotlin
// GradleCheck.kt
val all: List<AdapterWorkflow> = listOf(appCheck, gradleCheck, gradlePluginCheck, kotlinLibraryCheck)

// TagAdapters.kt
val all: List<AdapterWorkflow> = listOf(gradleCreateTag, goCreateTag, gradleManualTag, goManualTag)

// ReleaseAdapters.kt
val all: List<AdapterWorkflow> = listOf(app, gradlePlugin, kotlinLibrary)
```

`Generate.kt` becomes:

```kotlin
fun main() {
    val outputDir = File(".github/workflows").apply { mkdirs() }

    val baseWorkflows = listOf<GeneratableWorkflow>(
        CheckWorkflow, ConventionalCommitCheckWorkflow, CreateTagWorkflow,
        ManualCreateTagWorkflow, ReleaseWorkflow, PublishWorkflow,
        LabelerWorkflow, AppDeployWorkflow,
    )

    val adapterWorkflows = GradleCheck.all + TagAdapters.all + ReleaseAdapters.all

    (baseWorkflows + adapterWorkflows).forEach { it.generate(outputDir) }
}
```

**Depends on:** Change 3 (TagAdapters)

**Files:** `GradleCheck.kt`, `TagAdapters.kt`, `ReleaseAdapters.kt`, `Generate.kt`

### 5. Extract inline bash to file-level vals

**Problem:** `ManualCreateTagWorkflow` has two multi-line bash scripts and `ConventionalCommitCheckWorkflow` has one, embedded directly in workflow definition. `${'$'}` escaping makes them hard to read.

**Solution:** Extract to file-level `private val`:

```kotlin
// ManualCreateTagWorkflow.kt
private val VALIDATE_VERSION_SCRIPT = """
    VERSION="${'$'}{{ inputs.tag-version }}"
    if [[ ! "${'$'}VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9.]+)?${'$'} ]]; then
      echo "::error::Version must be in semver format (e.g. 1.2.3 or 1.2.3-rc.1)"
      exit 1
    fi
""".trimIndent()

private val CREATE_TAG_SCRIPT = """
    TAG="${'$'}{{ inputs.tag-prefix }}${'$'}{{ inputs.tag-version }}"
    git config user.name "github-actions[bot]"
    git config user.email "github-actions[bot]@users.noreply.github.com"
    git tag -a "${'$'}TAG" -m "Release ${'$'}TAG"
    git push origin "${'$'}TAG"
    echo "::notice::Created tag ${'$'}TAG"
""".trimIndent()
```

Then `implementation()` uses them by name:
```kotlin
run(name = "Validate version format", command = VALIDATE_VERSION_SCRIPT)
run(name = "Create and push tag", command = CREATE_TAG_SCRIPT, env = ...)
```

Same pattern for `ConventionalCommitCheckWorkflow` — extract `VALIDATE_PR_TITLE_SCRIPT`.

**Files:** `ManualCreateTagWorkflow.kt`, `ConventionalCommitCheckWorkflow.kt`

### 6. NeedsYaml sealed interface → value class

**Problem:** `NeedsYaml` sealed interface with `Single`/`Multiple` subtypes is overly complex for what it does — serialize a list as string (if 1 element) or list (if multiple).

**Solution:** Replace with value class:

```kotlin
@JvmInline
@Serializable(with = NeedsYamlSerializer::class)
value class NeedsYaml(val values: List<String>) {
    companion object {
        fun of(list: List<String>): NeedsYaml? =
            if (list.isEmpty()) null else NeedsYaml(list)
    }
}

object NeedsYamlSerializer : KSerializer<NeedsYaml> {
    private val listSerializer = ListSerializer(String.serializer())
    override val descriptor = PrimitiveSerialDescriptor("NeedsYaml", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: NeedsYaml) {
        if (value.values.size == 1) encoder.encodeString(value.values.first())
        else listSerializer.serialize(encoder, value.values)
    }

    override fun deserialize(decoder: Decoder): NeedsYaml =
        NeedsYaml(listOf(decoder.decodeString()))
}
```

Call site `NeedsYaml.of(needs)` remains unchanged.

**Files:** `AdapterWorkflowYaml.kt`

### 7. Cache lookup in collectSecretsFromJobs

**Problem:** `workflowSecrets[name]` called twice for the same key.

**Solution:**
```kotlin
private fun collectSecretsFromJobs(jobDefs: List<ReusableWorkflowJobDef>): Map<String, SecretYaml>? =
    jobDefs.flatMap { job ->
        val workflowSecrets = job.uses.secrets
        job.secrets.keys.map { name ->
            val secret = workflowSecrets[name]
            name to SecretYaml(
                description = secret?.description ?: name,
                required = secret?.required ?: true,
            )
        }
    }.toMap()
        .takeIf { it.isNotEmpty() }
```

**Files:** `AdapterWorkflow.kt`

## Execution Order

Changes are independent except 3→4. Recommended order:

1. Change 1 (InputRegistry extensions) — foundation
2. Change 2 (addSecrets + secretsAsRawMap)
3. Change 6 (NeedsYaml value class)
4. Change 7 (collectSecretsFromJobs cache)
5. Change 5 (bash extraction)
6. Change 3 (merge TagAdapters)
7. Change 4 (val all + Generate.kt) — depends on 3

## Validation

After all changes: `./gradlew run` must produce identical YAML output. Diff generated files against current versions to verify.
