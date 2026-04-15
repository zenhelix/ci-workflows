# Kotlin Code Conciseness & Patterns Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Kotlin code more concise and improve patterns without changing generated YAML output.

**Architecture:** 7 independent refactoring changes across `workflow-dsl/` and `src/main/kotlin/`. Each task produces a compilable, YAML-identical codebase. Changes 3 and 4 must run sequentially (4 depends on 3); all others are independent.

**Tech Stack:** Kotlin 2.3.20, github-workflows-kt 3.7.0, kaml 0.104.0, kotlinx-serialization 1.11.0

**Validation:** After every task, run `./gradlew run` and verify generated YAML is identical to the baseline. The YAML files are in `.github/workflows/`. Use `git diff .github/workflows/` — it must show zero changes.

---

## File Map

| Action | File Path | Responsibility |
|--------|-----------|---------------|
| Modify | `workflow-dsl/src/main/kotlin/dsl/core/InputRegistry.kt` | Add `String`/`Boolean` extension functions |
| Modify | `workflow-dsl/src/main/kotlin/dsl/core/ReusableWorkflow.kt` | Simplify `input()` overloads, simplify `secretsAsRawMap()` |
| Modify | `workflow-dsl/src/main/kotlin/dsl/builder/AdapterWorkflowBuilder.kt` | Simplify `input()` overloads |
| Modify | `workflow-dsl/src/main/kotlin/dsl/builder/ReusableWorkflowJobBuilder.kt` | Extract `addSecrets` helper |
| Modify | `workflow-dsl/src/main/kotlin/dsl/yaml/AdapterWorkflowYaml.kt` | `NeedsYaml` sealed → value class |
| Modify | `workflow-dsl/src/main/kotlin/dsl/builder/AdapterWorkflow.kt` | Cache lookup in `collectSecretsFromJobs` |
| Modify | `src/main/kotlin/workflows/base/ManualCreateTagWorkflow.kt` | Extract inline bash to file-level vals |
| Modify | `src/main/kotlin/workflows/base/ConventionalCommitCheckWorkflow.kt` | Extract inline bash to file-level val |
| Delete | `src/main/kotlin/workflows/adapters/tag/CreateTagAdapters.kt` | Replaced by TagAdapters.kt |
| Delete | `src/main/kotlin/workflows/adapters/tag/ManualCreateTagAdapters.kt` | Replaced by TagAdapters.kt |
| Create | `src/main/kotlin/workflows/adapters/tag/TagAdapters.kt` | Merged tag adapters |
| Modify | `src/main/kotlin/workflows/adapters/check/GradleCheck.kt` | Add `val all` |
| Modify | `src/main/kotlin/workflows/adapters/release/ReleaseAdapters.kt` | Add `val all` |
| Modify | `src/main/kotlin/generate/Generate.kt` | Use `all` lists |

---

### Task 1: InputRegistry extension functions

**Files:**
- Modify: `workflow-dsl/src/main/kotlin/dsl/core/InputRegistry.kt`
- Modify: `workflow-dsl/src/main/kotlin/dsl/core/ReusableWorkflow.kt`
- Modify: `workflow-dsl/src/main/kotlin/dsl/builder/AdapterWorkflowBuilder.kt`

- [ ] **Step 1: Add extension functions to InputRegistry.kt**

At the end of `InputRegistry.kt` (after line 23, after the class closing brace), add:

```kotlin
fun InputRegistry.input(name: String, description: String, required: Boolean = false, default: String): WorkflowInput =
    input(name, description, required, InputDefault(default))

fun InputRegistry.input(name: String, description: String, required: Boolean = false, default: Boolean): WorkflowInput =
    input(name, description, required, InputDefault(default))
```

- [ ] **Step 2: Simplify ReusableWorkflow.kt input overloads**

Replace lines 19-31 (the two overloads with `default: String` and `default: Boolean`) with:

```kotlin
    protected fun input(
        name: String,
        description: String,
        required: Boolean = false,
        default: String,
    ): WorkflowInput = inputRegistry.input(name, description, required, default)

    protected fun input(
        name: String,
        description: String,
        required: Boolean = false,
        default: Boolean,
    ): WorkflowInput = inputRegistry.input(name, description, required, default)
```

This removes the `InputDefault(...)` wrapping — it's now handled by the extension functions. The import of `InputDefault` is no longer needed in this file (it was already not imported — the original code used the unqualified name which resolved via the `dsl.core` package).

- [ ] **Step 3: Simplify AdapterWorkflowBuilder.kt input overloads**

Replace lines 20-32 (the two overloads with `default: String` and `default: Boolean`) with:

```kotlin
    fun input(
        name: String,
        description: String,
        required: Boolean = false,
        default: String,
    ): WorkflowInput = inputRegistry.input(name, description, required = required, default = default)

    fun input(
        name: String,
        description: String,
        required: Boolean = false,
        default: Boolean,
    ): WorkflowInput = inputRegistry.input(name, description, required = required, default = default)
```

Remove the `InputDefault` import from line 3 (`import dsl.core.InputDefault`) since it's no longer used in this file.

- [ ] **Step 4: Build and verify**

Run: `./gradlew run`
Then: `git diff .github/workflows/`
Expected: zero diff — YAML output is identical.

- [ ] **Step 5: Commit**

```bash
git add workflow-dsl/src/main/kotlin/dsl/core/InputRegistry.kt \
      workflow-dsl/src/main/kotlin/dsl/core/ReusableWorkflow.kt \
      workflow-dsl/src/main/kotlin/dsl/builder/AdapterWorkflowBuilder.kt
git commit -m "refactor: extract InputDefault wrapping into InputRegistry extension functions"
```

---

### Task 2: Extract addSecrets helper + simplify secretsAsRawMap

**Files:**
- Modify: `workflow-dsl/src/main/kotlin/dsl/builder/ReusableWorkflowJobBuilder.kt`
- Modify: `workflow-dsl/src/main/kotlin/dsl/core/ReusableWorkflow.kt`

- [ ] **Step 1: Extract addSecrets in ReusableWorkflowJobBuilder.kt**

Replace lines 35-45 (the `passthroughSecrets` and `passthroughAllSecrets` methods) with:

```kotlin
    private fun addSecrets(secrets: Iterable<WorkflowSecret>) {
        secrets.forEach { secretsMap[it.name] = it.expr }
    }

    fun passthroughSecrets(vararg secrets: WorkflowSecret) = addSecrets(secrets.asIterable())

    fun passthroughAllSecrets() = addSecrets(workflow.secretObjects)
```

- [ ] **Step 2: Simplify secretsAsRawMap in ReusableWorkflow.kt**

Replace lines 85-92 (`secretsAsRawMap` method) with:

```kotlin
    private fun secretsAsRawMap(): Map<String, Map<String, Any?>> =
        _secrets.mapValues { (_, pair) ->
            mapOf("description" to pair.first.description, "required" to pair.first.required)
        }
```

- [ ] **Step 3: Build and verify**

Run: `./gradlew run`
Then: `git diff .github/workflows/`
Expected: zero diff.

- [ ] **Step 4: Commit**

```bash
git add workflow-dsl/src/main/kotlin/dsl/builder/ReusableWorkflowJobBuilder.kt \
      workflow-dsl/src/main/kotlin/dsl/core/ReusableWorkflow.kt
git commit -m "refactor: extract addSecrets helper, simplify secretsAsRawMap"
```

---

### Task 3: Merge CreateTagAdapters + ManualCreateTagAdapters → TagAdapters

**Files:**
- Delete: `src/main/kotlin/workflows/adapters/tag/CreateTagAdapters.kt`
- Delete: `src/main/kotlin/workflows/adapters/tag/ManualCreateTagAdapters.kt`
- Create: `src/main/kotlin/workflows/adapters/tag/TagAdapters.kt`
- Modify: `src/main/kotlin/generate/Generate.kt`

- [ ] **Step 1: Create TagAdapters.kt**

Create `src/main/kotlin/workflows/adapters/tag/TagAdapters.kt` with:

```kotlin
package workflows.adapters.tag

import config.DEFAULT_RELEASE_BRANCHES
import config.EcosystemConfig
import config.GO
import config.GRADLE
import dsl.builder.AdapterWorkflow
import dsl.builder.adapterWorkflow
import dsl.core.expr
import dsl.capability.setupJob
import workflows.base.CreateTagWorkflow
import workflows.base.ManualCreateTagWorkflow
import workflows.support.setup

object TagAdapters {
    val gradleCreateTag = ecosystemCreateTag("gradle-create-tag.yml", "Gradle Create Tag", GRADLE)
    val goCreateTag = ecosystemCreateTag("go-create-tag.yml", "Go Create Tag", GO)

    val gradleManualTag = ecosystemManualTag("gradle-manual-create-tag.yml", "Gradle Manual Create Tag", GRADLE)
    val goManualTag = ecosystemManualTag("go-manual-create-tag.yml", "Go Manual Create Tag", GO)

    private fun ecosystemCreateTag(fileName: String, name: String, eco: EcosystemConfig): AdapterWorkflow =
        adapterWorkflow(fileName, name) {
            val version = input(eco.tool.versionKey, description = eco.tool.versionDescription, default = eco.tool.defaultVersion)
            val checkCommand = input(eco.checkCommandName, description = eco.checkCommandDescription, default = eco.defaultCheckCommand)
            val defaultBump = input("default-bump", description = "Default version bump type (major, minor, patch)", default = "patch")
            val tagPrefix = input("tag-prefix", description = "Prefix for the tag", default = eco.defaultTagPrefix)
            val releaseBranches = input("release-branches", description = "Comma-separated branch patterns for releases", default = DEFAULT_RELEASE_BRANCHES)

            CreateTagWorkflow.setupJob("create-tag") {
                setup(eco.tool, version.expr)
                CreateTagWorkflow.checkCommand from checkCommand
                CreateTagWorkflow.defaultBump from defaultBump
                CreateTagWorkflow.tagPrefix from tagPrefix
                CreateTagWorkflow.releaseBranches from releaseBranches
                passthroughAllSecrets()
            }
        }

    private fun ecosystemManualTag(fileName: String, name: String, eco: EcosystemConfig): AdapterWorkflow =
        adapterWorkflow(fileName, name) {
            val tagVersion = input("tag-version", description = "Version to tag (e.g. 1.2.3)", required = true)
            val version = input(eco.tool.versionKey, description = eco.tool.versionDescription, default = eco.tool.defaultVersion)
            val checkCommand = input(eco.checkCommandName, description = eco.checkCommandDescription, default = eco.defaultCheckCommand)
            val tagPrefix = input("tag-prefix", description = "Prefix for the tag", default = eco.defaultTagPrefix)

            ManualCreateTagWorkflow.setupJob("manual-tag") {
                ManualCreateTagWorkflow.tagVersion from tagVersion
                ManualCreateTagWorkflow.tagPrefix from tagPrefix
                setup(eco.tool, version.expr)
                ManualCreateTagWorkflow.checkCommand from checkCommand
                passthroughAllSecrets()
            }
        }
}
```

- [ ] **Step 2: Delete old files**

```bash
rm src/main/kotlin/workflows/adapters/tag/CreateTagAdapters.kt
rm src/main/kotlin/workflows/adapters/tag/ManualCreateTagAdapters.kt
```

- [ ] **Step 3: Update Generate.kt imports and references**

In `src/main/kotlin/generate/Generate.kt`:

Replace import lines 13-14:
```kotlin
import workflows.adapters.tag.CreateTagAdapters
import workflows.adapters.tag.ManualCreateTagAdapters
```
with:
```kotlin
import workflows.adapters.tag.TagAdapters
```

Replace lines 38-41 (the tag adapter entries):
```kotlin
        // Adapters — tag
        CreateTagAdapters.gradle,
        CreateTagAdapters.go,
        ManualCreateTagAdapters.gradle,
        ManualCreateTagAdapters.go,
```
with:
```kotlin
        // Adapters — tag
        TagAdapters.gradleCreateTag,
        TagAdapters.goCreateTag,
        TagAdapters.gradleManualTag,
        TagAdapters.goManualTag,
```

- [ ] **Step 4: Build and verify**

Run: `./gradlew run`
Then: `git diff .github/workflows/`
Expected: zero diff.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/workflows/adapters/tag/TagAdapters.kt \
      src/main/kotlin/generate/Generate.kt
git rm src/main/kotlin/workflows/adapters/tag/CreateTagAdapters.kt \
      src/main/kotlin/workflows/adapters/tag/ManualCreateTagAdapters.kt
git commit -m "refactor: merge CreateTagAdapters and ManualCreateTagAdapters into TagAdapters"
```

---

### Task 4: Add `val all` to adapter objects + simplify Generate.kt

**Depends on:** Task 3

**Files:**
- Modify: `src/main/kotlin/workflows/adapters/check/GradleCheck.kt`
- Modify: `src/main/kotlin/workflows/adapters/tag/TagAdapters.kt`
- Modify: `src/main/kotlin/workflows/adapters/release/ReleaseAdapters.kt`
- Modify: `src/main/kotlin/generate/Generate.kt`

- [ ] **Step 1: Add `val all` to GradleCheck.kt**

After line 18 (`val kotlinLibraryCheck = ...`) and before line 20 (`private fun gradleCheck`), add:

```kotlin

    val all: List<AdapterWorkflow> = listOf(appCheck, gradleCheck, gradlePluginCheck, kotlinLibraryCheck)
```

- [ ] **Step 2: Add `val all` to TagAdapters.kt**

After the four `val` declarations (after `val goManualTag = ...`), add:

```kotlin

    val all: List<AdapterWorkflow> = listOf(gradleCreateTag, goCreateTag, gradleManualTag, goManualTag)
```

- [ ] **Step 3: Add `val all` to ReleaseAdapters.kt**

After line 36 (`}`) closing the `kotlinLibrary` lambda, add:

```kotlin

    val all: List<AdapterWorkflow> = listOf(app, gradlePlugin, kotlinLibrary)
```

- [ ] **Step 4: Simplify Generate.kt**

Replace the entire `main()` function body (lines 18-49) with:

```kotlin
fun main() {
    val outputDir = File(".github/workflows").apply { mkdirs() }

    val baseWorkflows = listOf<GeneratableWorkflow>(
        CheckWorkflow,
        ConventionalCommitCheckWorkflow,
        CreateTagWorkflow,
        ManualCreateTagWorkflow,
        ReleaseWorkflow,
        PublishWorkflow,
        LabelerWorkflow,
        AppDeployWorkflow,
    )

    val adapterWorkflows = GradleCheck.all + TagAdapters.all + ReleaseAdapters.all

    (baseWorkflows + adapterWorkflows).forEach { it.generate(outputDir) }
}
```

Remove now-unused individual adapter member imports — the imports should be:

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
import workflows.adapters.tag.TagAdapters
import workflows.adapters.release.ReleaseAdapters
import java.io.File
```

(Same imports as after Task 3 — no change needed if Task 3 was done first.)

- [ ] **Step 5: Build and verify**

Run: `./gradlew run`
Then: `git diff .github/workflows/`
Expected: zero diff.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/workflows/adapters/check/GradleCheck.kt \
      src/main/kotlin/workflows/adapters/tag/TagAdapters.kt \
      src/main/kotlin/workflows/adapters/release/ReleaseAdapters.kt \
      src/main/kotlin/generate/Generate.kt
git commit -m "refactor: add val all to adapter objects, simplify Generate.kt"
```

---

### Task 5: Extract inline bash to file-level vals

**Files:**
- Modify: `src/main/kotlin/workflows/base/ManualCreateTagWorkflow.kt`
- Modify: `src/main/kotlin/workflows/base/ConventionalCommitCheckWorkflow.kt`

- [ ] **Step 1: Extract bash scripts in ManualCreateTagWorkflow.kt**

Add two file-level vals before the `object` declaration (before line 13). Insert between the imports and the object:

```kotlin

private val VALIDATE_VERSION_SCRIPT = """
    VERSION="${'$'}{{ inputs.tag-version }}"
    if [[ ! "${'$'}VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9.]+)?${'$'} ]]; then
      echo "::error::Version must be in semver format (e.g. 1.2.3 or 1.2.3-rc.1)"
      exit 1
    fi
""".trimIndent()

private val CREATE_AND_PUSH_TAG_SCRIPT = """
    TAG="${'$'}{{ inputs.tag-prefix }}${'$'}{{ inputs.tag-version }}"
    git config user.name "github-actions[bot]"
    git config user.email "github-actions[bot]@users.noreply.github.com"
    git tag -a "${'$'}TAG" -m "Release ${'$'}TAG"
    git push origin "${'$'}TAG"
    echo "::notice::Created tag ${'$'}TAG"
""".trimIndent()

```

- [ ] **Step 2: Use extracted vals in ManualCreateTagWorkflow implementation**

Replace lines 27-35 (the first `run(...)` block) with:

```kotlin
            run(name = "Validate version format", command = VALIDATE_VERSION_SCRIPT)
```

Replace lines 44-54 (the second `run(...)` block) with:

```kotlin
            run(
                name = "Create and push tag",
                command = CREATE_AND_PUSH_TAG_SCRIPT,
                env = linkedMapOf("GITHUB_TOKEN" to "\${{ steps.app-token.outputs.token }}"),
            )
```

- [ ] **Step 3: Extract bash script in ConventionalCommitCheckWorkflow.kt**

Add a file-level val before the `object` declaration (before line 8). Insert between imports and the object:

```kotlin

private val VALIDATE_PR_TITLE_SCRIPT = """
    TYPES_PATTERN=${'$'}(echo "${'$'}ALLOWED_TYPES" | tr ',' '|')
    PATTERN="^(${'$'}TYPES_PATTERN)(\(.+\))?(!)?: .+"
    if [[ ! "${'$'}PR_TITLE" =~ ${'$'}PATTERN ]]; then
      echo "::warning::PR title does not match conventional commits format: <type>(<scope>): <description>"
      echo "::warning::Allowed types: ${'$'}ALLOWED_TYPES"
      echo "::warning::Got: ${'$'}PR_TITLE"
    else
      echo "PR title is valid: ${'$'}PR_TITLE"
    fi
""".trimIndent()

```

- [ ] **Step 4: Use extracted val in ConventionalCommitCheckWorkflow implementation**

Replace lines 13-29 (the `run(...)` block inside the job) with:

```kotlin
            run(
                name = "Validate PR title format",
                command = VALIDATE_PR_TITLE_SCRIPT,
                env = linkedMapOf(
                    "PR_TITLE" to "\${{ github.event.pull_request.title }}",
                    "ALLOWED_TYPES" to allowedTypes.expr,
                ),
            )
```

- [ ] **Step 5: Build and verify**

Run: `./gradlew run`
Then: `git diff .github/workflows/`
Expected: zero diff.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/workflows/base/ManualCreateTagWorkflow.kt \
      src/main/kotlin/workflows/base/ConventionalCommitCheckWorkflow.kt
git commit -m "refactor: extract inline bash scripts to file-level vals"
```

---

### Task 6: NeedsYaml sealed interface → value class

**Files:**
- Modify: `workflow-dsl/src/main/kotlin/dsl/yaml/AdapterWorkflowYaml.kt`

- [ ] **Step 1: Replace NeedsYaml sealed interface with value class**

Replace lines 72-101 (the `NeedsYaml` sealed interface + companion + `NeedsYamlSerializer` object) with:

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

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("NeedsYaml", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: NeedsYaml) {
        if (value.values.size == 1) encoder.encodeString(value.values.first())
        else listSerializer.serialize(encoder, value.values)
    }

    override fun deserialize(decoder: Decoder): NeedsYaml =
        NeedsYaml(listOf(decoder.decodeString()))
}
```

- [ ] **Step 2: Build and verify**

Run: `./gradlew run`
Then: `git diff .github/workflows/`
Expected: zero diff.

- [ ] **Step 3: Commit**

```bash
git add workflow-dsl/src/main/kotlin/dsl/yaml/AdapterWorkflowYaml.kt
git commit -m "refactor: simplify NeedsYaml from sealed interface to value class"
```

---

### Task 7: Cache lookup in collectSecretsFromJobs

**Files:**
- Modify: `workflow-dsl/src/main/kotlin/dsl/builder/AdapterWorkflow.kt`

- [ ] **Step 1: Cache the map lookup**

Replace lines 53-63 (`collectSecretsFromJobs` method body) with:

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

- [ ] **Step 2: Build and verify**

Run: `./gradlew run`
Then: `git diff .github/workflows/`
Expected: zero diff.

- [ ] **Step 3: Commit**

```bash
git add workflow-dsl/src/main/kotlin/dsl/builder/AdapterWorkflow.kt
git commit -m "refactor: cache secret lookup in collectSecretsFromJobs"
```
