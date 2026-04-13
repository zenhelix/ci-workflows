# Typed Workflow Generation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace raw string YAML generation with typed Kotlin DSL and kaml serialization for maximum compile-time safety.

**Architecture:** The `AdapterWorkflow` abstract class extends `ReusableWorkflow` so adapter inputs become typed properties. YAML generation switches from `StringBuilder` to kaml `@Serializable` DTOs. Deprecated `_Untyped` action bindings are replaced with typed JIT bindings.

**Tech Stack:** Kotlin 2.3.20, github-workflows-kt 3.7.0, kaml 0.104.0, kotlinx-serialization 1.11.0

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `build.gradle.kts` | Modify | Add serialization plugin + kaml/kotlinx deps |
| `src/main/kotlin/dsl/yaml/AdapterWorkflowYaml.kt` | Create | `@Serializable` DTOs for YAML output |
| `src/main/kotlin/dsl/AdapterWorkflow.kt` | Rewrite | Abstract base class + kaml generation |
| `src/main/kotlin/dsl/ReusableWorkflow.kt` | Modify | Expose trigger data for serialization |
| `src/main/kotlin/dsl/ReusableWorkflowJobBuilder.kt` | Modify | Add `toYaml()` conversion |
| `src/main/kotlin/workflows/base/Release.kt` | Modify | Replace `_Untyped` with typed bindings |
| `src/main/kotlin/workflows/base/Labeler.kt` | Modify | Replace `_Untyped` with typed bindings |
| `src/main/kotlin/workflows/adapters/check/GradleCheck.kt` | Rewrite | `GradleCheckAdapter` class |
| `src/main/kotlin/workflows/adapters/check/AppCheck.kt` | Delete | Merged into `GradleCheckAdapter` instances |
| `src/main/kotlin/workflows/adapters/check/GradlePluginCheck.kt` | Delete | Merged into `GradleCheckAdapter` instances |
| `src/main/kotlin/workflows/adapters/check/KotlinLibraryCheck.kt` | Delete | Merged into `GradleCheckAdapter` instances |
| `src/main/kotlin/workflows/adapters/release/AppRelease.kt` | Rewrite | `AppReleaseAdapter` object |
| `src/main/kotlin/workflows/adapters/release/GradlePluginRelease.kt` | Rewrite | `GradlePluginReleaseAdapter` object |
| `src/main/kotlin/workflows/adapters/release/KotlinLibraryRelease.kt` | Rewrite | `KotlinLibraryReleaseAdapter` object |
| `src/main/kotlin/workflows/adapters/tag/GradleCreateTag.kt` | Rewrite | `GradleCreateTagAdapter` object |
| `src/main/kotlin/workflows/adapters/tag/GradleManualCreateTag.kt` | Rewrite | `GradleManualCreateTagAdapter` object |
| `src/main/kotlin/workflows/adapters/tag/GoCreateTag.kt` | Rewrite | `GoCreateTagAdapter` object |
| `src/main/kotlin/workflows/adapters/tag/GoManualCreateTag.kt` | Rewrite | `GoManualCreateTagAdapter` object |
| `src/main/kotlin/workflows/adapters/deploy/AppDeploy.kt` | Modify | Replace `CommonInputs` with inline inputs |
| `src/main/kotlin/generate/Generate.kt` | Modify | Use `adapter.generate(outputDir)` calls |
| `src/main/kotlin/config/CommonInputs.kt` | Delete | Inputs move to adapter classes |
| `src/main/kotlin/dsl/WorkflowHelpers.kt` | Modify | Remove `inputRef()` function |

## Verification Strategy

No unit tests exist. Verification is done by comparing generated YAML before and after changes:

```bash
# Before any code changes, save baseline:
cp -r .github/workflows /tmp/workflows-baseline

# After code changes, regenerate and diff:
./gradlew run
diff -r /tmp/workflows-baseline .github/workflows
```

The generated YAML must be **semantically equivalent** (identical content, minor whitespace/quoting differences acceptable).

---

### Task 1: Save Baseline YAML and Add Dependencies

**Files:**
- Modify: `build.gradle.kts`

- [ ] **Step 1: Save current YAML baseline for later comparison**

```bash
cp -r .github/workflows /tmp/workflows-baseline
```

- [ ] **Step 2: Add serialization plugin and dependencies to `build.gradle.kts`**

Replace the current `plugins` block:

```kotlin
plugins {
    kotlin("jvm") version "2.3.20"
    kotlin("plugin.serialization") version "2.3.20"
    application
}
```

Add to `dependencies`:

```kotlin
dependencies {
    implementation("io.github.typesafegithub:github-workflows-kt:3.7.0")

    // YAML serialization
    implementation("com.charleskorn.kaml:kaml:0.104.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.11.0")

    // JIT action bindings
    implementation("actions:checkout:v6")
    // mathieudutour:github-tag-action:v6 - not yet available in bindings.krzeminski.it registry
    implementation("mikepenz:release-changelog-builder-action:v6")
    implementation("softprops:action-gh-release:v2")
    implementation("actions:labeler:v6")
}
```

- [ ] **Step 3: Verify build compiles**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add build.gradle.kts
git commit -m "chore: add kaml and kotlinx-serialization dependencies"
```

---

### Task 2: Replace `_Untyped` Action Bindings in Release.kt

**Files:**
- Modify: `src/main/kotlin/workflows/base/Release.kt`

- [ ] **Step 1: Replace imports and action usages in `Release.kt`**

Replace the entire file content with:

```kotlin
package workflows.base

import dsl.ReleaseWorkflow
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
                    configuration_Untyped = ReleaseWorkflow.changelogConfig.ref,
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
                    draft = ReleaseWorkflow.draft.ref,
                ),
                env = linkedMapOf(
                    "GITHUB_TOKEN" to "\${{ secrets.GITHUB_TOKEN }}",
                ),
            )
        }
    }
}
```

Key changes:
- `Checkout_Untyped(fetchDepth_Untyped = "0")` → `Checkout(fetchDepth = Checkout.FetchDepth.Value(0))`
- `ActionGhRelease_Untyped(body_Untyped, ...)` → `ActionGhRelease(body, name, tagName, draft)`
- `ReleaseChangelogBuilderAction_Untyped` stays — no typed version in registry

- [ ] **Step 2: Verify build compiles**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL. If `Checkout.FetchDepth.Value(0)` doesn't compile, check the typed binding API — it may use `Checkout.FetchDepth.Numeric(0)` or accept `Int` directly. Adjust accordingly.

- [ ] **Step 3: Verify generated YAML is semantically equivalent**

```bash
./gradlew run
diff .github/workflows/release.yml /tmp/workflows-baseline/release.yml
```

Expected: No differences, or minor quoting differences only (e.g., `'0'` vs `0` for fetch-depth). The action names and parameter keys must remain the same: `fetch-depth`, `configuration`, `toTag`, `body`, `name`, `tag_name`, `draft`.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/workflows/base/Release.kt
git add .github/workflows/release.yml
git commit -m "refactor: replace _Untyped with typed action bindings in Release.kt"
```

---

### Task 3: Replace `_Untyped` Action Bindings in Labeler.kt

**Files:**
- Modify: `src/main/kotlin/workflows/base/Labeler.kt`

- [ ] **Step 1: Replace imports and action usages in `Labeler.kt`**

Replace the entire file content with:

```kotlin
package workflows.base

import dsl.LabelerWorkflow
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
                    repoToken = "\${{ secrets.GITHUB_TOKEN }}",
                    configurationPath = LabelerWorkflow.configPath.ref,
                    syncLabels = true,
                ),
            )
        }
    }
}
```

Key changes:
- `Labeler_Untyped(repoToken_Untyped, configurationPath_Untyped, syncLabels_Untyped = "true")` → `Labeler(repoToken, configurationPath, syncLabels = true)`

- [ ] **Step 2: Verify build compiles**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL. If `syncLabels` expects a different type (e.g., `Labeler.SyncLabels` enum), adjust accordingly.

- [ ] **Step 3: Verify generated YAML is semantically equivalent**

```bash
./gradlew run
diff .github/workflows/labeler.yml /tmp/workflows-baseline/labeler.yml
```

Expected: Same parameter names and values. `sync-labels` should still be `'true'` or `true` in YAML.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/workflows/base/Labeler.kt
git add .github/workflows/labeler.yml
git commit -m "refactor: replace _Untyped with typed action bindings in Labeler.kt"
```

---

### Task 4: Create Serialization DTOs

**Files:**
- Create: `src/main/kotlin/dsl/yaml/AdapterWorkflowYaml.kt`

- [ ] **Step 1: Create the YAML DTO file**

Create file `src/main/kotlin/dsl/yaml/AdapterWorkflowYaml.kt`:

```kotlin
package dsl.yaml

import com.charleskorn.kaml.SingleLineStringStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

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
    @Serializable(with = YamlDefaultSerializer::class)
    val default: YamlDefault? = null,
)

@Serializable
data class SecretYaml(
    val description: String,
    val required: Boolean,
)

@Serializable
data class JobYaml(
    val needs: JobNeeds? = null,
    val strategy: StrategyYaml? = null,
    val uses: String,
    @SerialName("with")
    val withParams: Map<String, String>? = null,
    val secrets: Map<String, String>? = null,
)

@Serializable
data class JobNeeds(val values: List<String>) {
    companion object {
        fun of(vararg ids: String) = JobNeeds(ids.toList())
    }
}

@Serializable
data class StrategyYaml(
    val matrix: Map<String, String>,
)

sealed class YamlDefault {
    data class StringValue(val value: String) : YamlDefault()
    data class BooleanValue(val value: Boolean) : YamlDefault()
    data class NumberValue(val value: Number) : YamlDefault()
}

object YamlDefaultSerializer : KSerializer<YamlDefault> {
    override val descriptor = PrimitiveSerialDescriptor("YamlDefault", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: YamlDefault) {
        when (value) {
            is YamlDefault.StringValue -> encoder.encodeString(value.value)
            is YamlDefault.BooleanValue -> encoder.encodeBoolean(value.value)
            is YamlDefault.NumberValue -> encoder.encodeDouble(value.value.toDouble())
        }
    }

    override fun deserialize(decoder: Decoder): YamlDefault {
        throw UnsupportedOperationException("Deserialization not needed")
    }
}

object JobNeedsSerializer : KSerializer<JobNeeds> {
    override val descriptor = PrimitiveSerialDescriptor("JobNeeds", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: JobNeeds) {
        if (value.values.size == 1) {
            encoder.encodeString(value.values.first())
        } else {
            val composite = encoder.beginCollection(descriptor, value.values.size)
            value.values.forEachIndexed { index, item ->
                composite.encodeStringElement(descriptor, index, item)
            }
            composite.endStructure(descriptor)
        }
    }

    override fun deserialize(decoder: Decoder): JobNeeds {
        throw UnsupportedOperationException("Deserialization not needed")
    }
}

val adapterYaml = Yaml(
    configuration = YamlConfiguration(
        encodeDefaults = false,
        singleLineStringStyle = SingleLineStringStyle.SingleQuoted,
    ),
)
```

**Note:** The `JobNeeds` serializer handles the GitHub Actions convention where a single dependency is a scalar string (`needs: 'release'`) and multiple are a list. The exact kaml API for `singleLineStringStyle` may differ — check kaml docs if it doesn't compile. The key requirement is that strings are single-quoted in the output to match the current YAML format.

- [ ] **Step 2: Verify build compiles**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL. If kaml configuration API differs (e.g., different property names), adjust to match the actual kaml 0.104.0 API.

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/dsl/yaml/AdapterWorkflowYaml.kt
git commit -m "feat: add @Serializable DTOs for adapter workflow YAML generation"
```

---

### Task 5: Create `AdapterWorkflow` Base Class and Rewrite `generateAdapterWorkflow()`

**Files:**
- Rewrite: `src/main/kotlin/dsl/AdapterWorkflow.kt`
- Modify: `src/main/kotlin/dsl/ReusableWorkflow.kt`
- Modify: `src/main/kotlin/dsl/ReusableWorkflowJobBuilder.kt`

- [ ] **Step 1: Add `toInputYaml()` and `toSecretYaml()` methods to `ReusableWorkflow`**

In `src/main/kotlin/dsl/ReusableWorkflow.kt`, add these methods inside the `ReusableWorkflow` class (after the existing `toWorkflowCallTrigger()` method):

```kotlin
    fun toInputsYaml(): Map<String, InputYaml>? {
        if (_inputs.isEmpty()) return null
        return _inputs.map { (name, input) ->
            val boolDefault = _booleanDefaults[name]
            val default = when {
                boolDefault != null -> YamlDefault.BooleanValue(boolDefault)
                input.default != null -> YamlDefault.StringValue(input.default!!)
                else -> null
            }
            name to InputYaml(
                description = input.description,
                type = input.type.name.lowercase(),
                required = input.required,
                default = default,
            )
        }.toMap()
    }

    fun toSecretsYaml(): Map<String, SecretYaml>? {
        if (_secrets.isEmpty()) return null
        return _secrets.map { (name, secret) ->
            name to SecretYaml(
                description = secret.description,
                required = secret.required,
            )
        }.toMap()
    }
```

Add these imports at the top of the file:

```kotlin
import dsl.yaml.InputYaml
import dsl.yaml.SecretYaml
import dsl.yaml.YamlDefault
```

- [ ] **Step 2: Add `toJobYaml()` method to `ReusableWorkflowJobDef`**

In `src/main/kotlin/dsl/ReusableWorkflowJobBuilder.kt`, add this method to the `ReusableWorkflowJobDef` data class:

```kotlin
    fun toJobYaml(): JobYaml = JobYaml(
        needs = if (needs.isEmpty()) null else JobNeeds(needs),
        strategy = strategy?.let { strat ->
            @Suppress("UNCHECKED_CAST")
            val matrix = strat["matrix"] as? Map<String, Any>
            matrix?.let { StrategyYaml(it.mapValues { (_, v) -> v.toString() }) }
        },
        uses = uses.usesString,
        withParams = with.takeIf { it.isNotEmpty() },
        secrets = secrets.takeIf { it.isNotEmpty() },
    )
```

Add these imports at the top of the file:

```kotlin
import dsl.yaml.JobNeeds
import dsl.yaml.JobYaml
import dsl.yaml.StrategyYaml
```

- [ ] **Step 3: Rewrite `AdapterWorkflow.kt` with base class and kaml generation**

Replace the entire file `src/main/kotlin/dsl/AdapterWorkflow.kt` with:

```kotlin
package dsl

import dsl.yaml.AdapterWorkflowYaml
import dsl.yaml.TriggerYaml
import dsl.yaml.WorkflowCallBodyYaml
import dsl.yaml.adapterYaml
import java.io.File

abstract class AdapterWorkflow(
    fileName: String,
    val workflowName: String,
) : ReusableWorkflow(fileName) {

    abstract fun jobs(): List<ReusableWorkflowJobDef>

    fun generate(outputDir: File) {
        val workflowYaml = AdapterWorkflowYaml(
            name = workflowName,
            on = TriggerYaml(
                workflowCall = WorkflowCallBodyYaml(
                    inputs = toInputsYaml(),
                    secrets = toSecretsYaml(),
                ),
            ),
            jobs = jobs().associate { it.id to it.toJobYaml() },
        )

        val yamlContent = buildString {
            appendLine("# This file was generated using Kotlin DSL (.github/workflow-src/${fileName.removeSuffix(".yml")}.main.kts).")
            appendLine("# If you want to modify the workflow, please change the Kotlin file and regenerate this YAML file.")
            appendLine("# Generated with https://github.com/typesafegithub/github-workflows-kt")
            appendLine()
            append(adapterYaml.encodeToString(AdapterWorkflowYaml.serializer(), workflowYaml))
        }

        outputDir.mkdirs()
        File(outputDir, fileName).writeText(yamlContent)
    }
}
```

- [ ] **Step 4: Verify build compiles**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL. Existing adapter functions still compile since the old `generateAdapterWorkflow()` top-level function is now removed — but the adapter files still import it. This is expected to fail at this step. If it does, keep the old `generateAdapterWorkflow()` function temporarily (rename to `generateAdapterWorkflowLegacy()` or similar) until all adapters are migrated in subsequent tasks.

**Fallback:** If compilation fails because adapters still reference the old function, add this temporary bridge at the bottom of `AdapterWorkflow.kt`:

```kotlin
@Deprecated("Use AdapterWorkflow class instead", level = DeprecationLevel.WARNING)
fun generateAdapterWorkflow(
    name: String,
    sourceFileSlug: String,
    targetFileName: String,
    trigger: io.github.typesafegithub.workflows.domain.triggers.WorkflowCall,
    jobs: List<ReusableWorkflowJobDef>,
    outputDir: File,
) {
    // Legacy: keep old StringBuilder implementation until all adapters are migrated
    val yaml = buildString {
        appendLine("# This file was generated using Kotlin DSL (.github/workflow-src/$sourceFileSlug.main.kts).")
        appendLine("# If you want to modify the workflow, please change the Kotlin file and regenerate this YAML file.")
        appendLine("# Generated with https://github.com/typesafegithub/github-workflows-kt")
        appendLine()
        appendLine("name: ${legacyYamlString(name)}")
        appendLine("on:")
        appendLegacyWorkflowCallTrigger(trigger)
        appendLine("jobs:")
        jobs.forEach { job -> appendLegacyJob(job) }
    }
    outputDir.mkdirs()
    File(outputDir, targetFileName).writeText(yaml)
}

// Copy the old private functions here with "legacy" prefix, keeping them exactly as they were.
// These will be deleted when all adapters are migrated.
```

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/dsl/AdapterWorkflow.kt
git add src/main/kotlin/dsl/ReusableWorkflow.kt
git add src/main/kotlin/dsl/ReusableWorkflowJobBuilder.kt
git commit -m "feat: add AdapterWorkflow base class with kaml YAML generation"
```

---

### Task 6: Migrate Check Adapters (GradleCheck, AppCheck, GradlePluginCheck, KotlinLibraryCheck)

**Files:**
- Rewrite: `src/main/kotlin/workflows/adapters/check/GradleCheck.kt`
- Delete: `src/main/kotlin/workflows/adapters/check/AppCheck.kt`
- Delete: `src/main/kotlin/workflows/adapters/check/GradlePluginCheck.kt`
- Delete: `src/main/kotlin/workflows/adapters/check/KotlinLibraryCheck.kt`
- Modify: `src/main/kotlin/generate/Generate.kt` (check adapter calls only)

- [ ] **Step 1: Rewrite `GradleCheck.kt` as `GradleCheckAdapter` class**

Replace `src/main/kotlin/workflows/adapters/check/GradleCheck.kt` with:

```kotlin
package workflows.adapters.check

import config.DEFAULT_JAVA_VERSION
import config.JAVA_VERSION_MATRIX_EXPR
import config.SetupTool
import dsl.AdapterWorkflow
import dsl.CheckWorkflow
import dsl.ConventionalCommitCheckWorkflow
import dsl.ReusableWorkflowJobDef
import dsl.reusableJob
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall

class GradleCheckAdapter(
    fileName: String,
    workflowName: String,
) : AdapterWorkflow(fileName, workflowName) {

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

    override fun jobs(): List<ReusableWorkflowJobDef> = listOf(
        reusableJob(id = "conventional-commit", uses = ConventionalCommitCheckWorkflow),
        reusableJob(id = "check", uses = CheckWorkflow) {
            strategy(mapOf("java-version" to JAVA_VERSION_MATRIX_EXPR))
            CheckWorkflow.setupAction(SetupTool.Gradle.id)
            CheckWorkflow.setupParams(SetupTool.Gradle.toParamsJson("\${{ matrix.java-version }}"))
            CheckWorkflow.checkCommand(gradleCommand.ref)
        },
    )
}
```

- [ ] **Step 2: Delete the wrapper files**

```bash
rm src/main/kotlin/workflows/adapters/check/AppCheck.kt
rm src/main/kotlin/workflows/adapters/check/GradlePluginCheck.kt
rm src/main/kotlin/workflows/adapters/check/KotlinLibraryCheck.kt
```

- [ ] **Step 3: Update `Generate.kt` — replace check adapter calls**

In `src/main/kotlin/generate/Generate.kt`, replace the check adapter imports and calls.

Remove these imports:
```kotlin
import workflows.adapters.check.generateAppCheck
import workflows.adapters.check.generateGradlePluginCheck
import workflows.adapters.check.generateKotlinLibraryCheck
```

Add this import:
```kotlin
import workflows.adapters.check.GradleCheckAdapter
```

Replace the four check adapter calls:
```kotlin
    // Old:
    generateAppCheck(outputDir)
    generateGradlePluginCheck(outputDir)
    generateKotlinLibraryCheck(outputDir)
```

With:
```kotlin
    GradleCheckAdapter("app-check.yml", "Application Check").generate(outputDir)
    GradleCheckAdapter("gradle-check.yml", "Gradle Check").generate(outputDir)
    GradleCheckAdapter("gradle-plugin-check.yml", "Gradle Plugin Check").generate(outputDir)
    GradleCheckAdapter("kotlin-library-check.yml", "Kotlin Library Check").generate(outputDir)
```

Also remove the old `generateGradleCheckWorkflow` function import if it's directly imported (the function was internal in the same package, so it may just be a direct call — the file was deleted so the reference is gone).

- [ ] **Step 4: Verify build compiles**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Verify generated YAML matches baseline**

```bash
./gradlew run
diff .github/workflows/app-check.yml /tmp/workflows-baseline/app-check.yml
diff .github/workflows/gradle-plugin-check.yml /tmp/workflows-baseline/gradle-plugin-check.yml
diff .github/workflows/kotlin-library-check.yml /tmp/workflows-baseline/kotlin-library-check.yml
```

Expected: Semantically equivalent. Differences in quoting style are acceptable (e.g., kaml may not quote all strings the same way). If there are meaningful differences (missing fields, wrong values), fix the DTOs or serialization config.

**Common kaml tuning needed:**
- If kaml outputs `default: ""` but the old YAML had `default: ''` — this is a quoting style issue, adjust `singleLineStringStyle` in `adapterYaml` config.
- If kaml omits `null` fields — that's correct behavior with `encodeDefaults = false`.
- If boolean `default: true` appears as `default: 'true'` — check the `YamlDefaultSerializer` handles boolean encoding properly.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/workflows/adapters/check/
git add src/main/kotlin/generate/Generate.kt
git add .github/workflows/app-check.yml .github/workflows/gradle-plugin-check.yml .github/workflows/kotlin-library-check.yml
git commit -m "refactor: migrate check adapters to GradleCheckAdapter class with kaml"
```

---

### Task 7: Migrate Release Adapters (AppRelease, GradlePluginRelease, KotlinLibraryRelease)

**Files:**
- Rewrite: `src/main/kotlin/workflows/adapters/release/AppRelease.kt`
- Rewrite: `src/main/kotlin/workflows/adapters/release/GradlePluginRelease.kt`
- Rewrite: `src/main/kotlin/workflows/adapters/release/KotlinLibraryRelease.kt`
- Modify: `src/main/kotlin/generate/Generate.kt` (release adapter calls only)

- [ ] **Step 1: Rewrite `AppRelease.kt`**

Replace `src/main/kotlin/workflows/adapters/release/AppRelease.kt` with:

```kotlin
package workflows.adapters.release

import config.DEFAULT_CHANGELOG_CONFIG
import dsl.AdapterWorkflow
import dsl.ReleaseWorkflow
import dsl.ReusableWorkflowJobDef
import dsl.reusableJob
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall

object AppReleaseAdapter : AdapterWorkflow("app-release.yml", "Application Release") {

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
        reusableJob(id = "release", uses = ReleaseWorkflow) {
            ReleaseWorkflow.changelogConfig(changelogConfig.ref)
            ReleaseWorkflow.draft(draft.ref)
        },
    )
}
```

- [ ] **Step 2: Rewrite `GradlePluginRelease.kt`**

Replace `src/main/kotlin/workflows/adapters/release/GradlePluginRelease.kt` with:

```kotlin
package workflows.adapters.release

import config.DEFAULT_CHANGELOG_CONFIG
import config.DEFAULT_JAVA_VERSION
import config.GRADLE_PORTAL_SECRETS
import config.MAVEN_SONATYPE_SECRETS
import config.SetupTool
import config.passthrough
import dsl.AdapterWorkflow
import dsl.PublishWorkflow
import dsl.ReleaseWorkflow
import dsl.ReusableWorkflowJobDef
import dsl.reusableJob
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall

object GradlePluginReleaseAdapter : AdapterWorkflow("gradle-plugin-release.yml", "Gradle Plugin Release") {

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

    init {
        (MAVEN_SONATYPE_SECRETS + GRADLE_PORTAL_SECRETS).forEach { (name, s) ->
            secret(name, description = s.description, required = s.required)
        }
    }

    override fun jobs(): List<ReusableWorkflowJobDef> = listOf(
        reusableJob(id = "release", uses = ReleaseWorkflow) {
            ReleaseWorkflow.changelogConfig(changelogConfig.ref)
        },
        reusableJob(id = "publish", uses = PublishWorkflow) {
            needs("release")
            PublishWorkflow.setupAction(SetupTool.Gradle.id)
            PublishWorkflow.setupParams(SetupTool.Gradle.toParamsJson(javaVersion.ref))
            PublishWorkflow.publishCommand(publishCommand.ref)
            secrets((MAVEN_SONATYPE_SECRETS + GRADLE_PORTAL_SECRETS).passthrough())
        },
    )
}
```

**Note:** The `init` block registers secrets from the predefined maps. The `secret()` method is `protected` in `ReusableWorkflow`, so it's callable from subclass `init` blocks. Additionally, add this bulk-register helper to `ReusableWorkflow` (in `src/main/kotlin/dsl/ReusableWorkflow.kt`, inside the class body, after the existing `secret()` method):

```kotlin
    protected fun secrets(map: Map<String, WorkflowCall.Secret>) {
        map.forEach { (name, s) -> secret(name, description = s.description, required = s.required) }
    }
```

This allows adapters to register predefined secret maps in one call: `secrets(APP_SECRETS)` instead of the `forEach` loop. Both approaches work — pick one consistently across all adapters.

- [ ] **Step 3: Rewrite `KotlinLibraryRelease.kt`**

Replace `src/main/kotlin/workflows/adapters/release/KotlinLibraryRelease.kt` with:

```kotlin
package workflows.adapters.release

import config.DEFAULT_CHANGELOG_CONFIG
import config.DEFAULT_JAVA_VERSION
import config.MAVEN_SONATYPE_SECRETS
import config.SetupTool
import config.passthrough
import dsl.AdapterWorkflow
import dsl.PublishWorkflow
import dsl.ReleaseWorkflow
import dsl.ReusableWorkflowJobDef
import dsl.reusableJob
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall

object KotlinLibraryReleaseAdapter : AdapterWorkflow("kotlin-library-release.yml", "Kotlin Library Release") {

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

    init {
        MAVEN_SONATYPE_SECRETS.forEach { (name, s) ->
            secret(name, description = s.description, required = s.required)
        }
    }

    override fun jobs(): List<ReusableWorkflowJobDef> = listOf(
        reusableJob(id = "release", uses = ReleaseWorkflow) {
            ReleaseWorkflow.changelogConfig(changelogConfig.ref)
        },
        reusableJob(id = "publish", uses = PublishWorkflow) {
            needs("release")
            PublishWorkflow.setupAction(SetupTool.Gradle.id)
            PublishWorkflow.setupParams(SetupTool.Gradle.toParamsJson(javaVersion.ref))
            PublishWorkflow.publishCommand(publishCommand.ref)
            secrets(MAVEN_SONATYPE_SECRETS.passthrough())
        },
    )
}
```

- [ ] **Step 4: Update `Generate.kt` — replace release adapter calls**

In `src/main/kotlin/generate/Generate.kt`, replace the release adapter imports and calls.

Remove these imports:
```kotlin
import workflows.adapters.release.generateAppRelease
import workflows.adapters.release.generateGradlePluginRelease
import workflows.adapters.release.generateKotlinLibraryRelease
```

Add these imports:
```kotlin
import workflows.adapters.release.AppReleaseAdapter
import workflows.adapters.release.GradlePluginReleaseAdapter
import workflows.adapters.release.KotlinLibraryReleaseAdapter
```

Replace the three calls:
```kotlin
    // Old:
    generateAppRelease(outputDir)
    generateGradlePluginRelease(outputDir)
    generateKotlinLibraryRelease(outputDir)
```

With:
```kotlin
    AppReleaseAdapter.generate(outputDir)
    GradlePluginReleaseAdapter.generate(outputDir)
    KotlinLibraryReleaseAdapter.generate(outputDir)
```

- [ ] **Step 5: Verify build compiles and YAML matches**

```bash
./gradlew compileKotlin && ./gradlew run
diff .github/workflows/app-release.yml /tmp/workflows-baseline/app-release.yml
diff .github/workflows/gradle-plugin-release.yml /tmp/workflows-baseline/gradle-plugin-release.yml
diff .github/workflows/kotlin-library-release.yml /tmp/workflows-baseline/kotlin-library-release.yml
```

Expected: Semantically equivalent YAML.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/workflows/adapters/release/
git add src/main/kotlin/generate/Generate.kt
git add src/main/kotlin/dsl/ReusableWorkflow.kt
git add .github/workflows/app-release.yml .github/workflows/gradle-plugin-release.yml .github/workflows/kotlin-library-release.yml
git commit -m "refactor: migrate release adapters to AdapterWorkflow classes with kaml"
```

---

### Task 8: Migrate Tag Adapters (GradleCreateTag, GradleManualCreateTag, GoCreateTag, GoManualCreateTag)

**Files:**
- Rewrite: `src/main/kotlin/workflows/adapters/tag/GradleCreateTag.kt`
- Rewrite: `src/main/kotlin/workflows/adapters/tag/GradleManualCreateTag.kt`
- Rewrite: `src/main/kotlin/workflows/adapters/tag/GoCreateTag.kt`
- Rewrite: `src/main/kotlin/workflows/adapters/tag/GoManualCreateTag.kt`
- Modify: `src/main/kotlin/generate/Generate.kt` (tag adapter calls only)

- [ ] **Step 1: Rewrite `GradleCreateTag.kt`**

Replace with:

```kotlin
package workflows.adapters.tag

import config.APP_SECRETS
import config.DEFAULT_JAVA_VERSION
import config.DEFAULT_RELEASE_BRANCHES
import config.SetupTool
import config.passthrough
import dsl.AdapterWorkflow
import dsl.CreateTagWorkflow
import dsl.ReusableWorkflowJobDef
import dsl.reusableJob

object GradleCreateTagAdapter : AdapterWorkflow("gradle-create-tag.yml", "Gradle Create Tag") {

    val javaVersion = input(
        "java-version",
        description = "JDK version to use",
        default = DEFAULT_JAVA_VERSION,
    )
    val gradleCommand = input(
        "gradle-command",
        description = "Gradle validation command",
        default = "./gradlew check",
    )
    val defaultBump = input(
        "default-bump",
        description = "Default version bump type (major, minor, patch)",
        default = "patch",
    )
    val tagPrefix = input(
        "tag-prefix",
        description = "Prefix for the tag",
        default = "",
    )
    val releaseBranches = input(
        "release-branches",
        description = "Comma-separated branch patterns for releases",
        default = DEFAULT_RELEASE_BRANCHES,
    )

    init {
        APP_SECRETS.forEach { (name, s) ->
            secret(name, description = s.description, required = s.required)
        }
    }

    override fun jobs(): List<ReusableWorkflowJobDef> = listOf(
        reusableJob(id = "create-tag", uses = CreateTagWorkflow) {
            CreateTagWorkflow.setupAction(SetupTool.Gradle.id)
            CreateTagWorkflow.setupParams(SetupTool.Gradle.toParamsJson(javaVersion.ref))
            CreateTagWorkflow.checkCommand(gradleCommand.ref)
            CreateTagWorkflow.defaultBump(defaultBump.ref)
            CreateTagWorkflow.tagPrefix(tagPrefix.ref)
            CreateTagWorkflow.releaseBranches(releaseBranches.ref)
            secrets(APP_SECRETS.passthrough())
        },
    )
}
```

- [ ] **Step 2: Rewrite `GradleManualCreateTag.kt`**

Replace with:

```kotlin
package workflows.adapters.tag

import config.APP_SECRETS
import config.DEFAULT_JAVA_VERSION
import config.SetupTool
import config.passthrough
import dsl.AdapterWorkflow
import dsl.ManualCreateTagWorkflow
import dsl.ReusableWorkflowJobDef
import dsl.reusableJob

object GradleManualCreateTagAdapter : AdapterWorkflow("gradle-manual-create-tag.yml", "Gradle Manual Create Tag") {

    val tagVersion = input(
        "tag-version",
        description = "Version to tag (e.g. 1.2.3)",
        required = true,
    )
    val javaVersion = input(
        "java-version",
        description = "JDK version to use",
        default = DEFAULT_JAVA_VERSION,
    )
    val gradleCommand = input(
        "gradle-command",
        description = "Gradle validation command",
        default = "./gradlew check",
    )
    val tagPrefix = input(
        "tag-prefix",
        description = "Prefix for the tag",
        default = "",
    )

    init {
        APP_SECRETS.forEach { (name, s) ->
            secret(name, description = s.description, required = s.required)
        }
    }

    override fun jobs(): List<ReusableWorkflowJobDef> = listOf(
        reusableJob(id = "manual-tag", uses = ManualCreateTagWorkflow) {
            ManualCreateTagWorkflow.tagVersion(tagVersion.ref)
            ManualCreateTagWorkflow.tagPrefix(tagPrefix.ref)
            ManualCreateTagWorkflow.setupAction(SetupTool.Gradle.id)
            ManualCreateTagWorkflow.setupParams(SetupTool.Gradle.toParamsJson(javaVersion.ref))
            ManualCreateTagWorkflow.checkCommand(gradleCommand.ref)
            secrets(APP_SECRETS.passthrough())
        },
    )
}
```

- [ ] **Step 3: Rewrite `GoCreateTag.kt`**

Replace with:

```kotlin
package workflows.adapters.tag

import config.APP_SECRETS
import config.DEFAULT_GO_VERSION
import config.DEFAULT_RELEASE_BRANCHES
import config.SetupTool
import config.passthrough
import dsl.AdapterWorkflow
import dsl.CreateTagWorkflow
import dsl.ReusableWorkflowJobDef
import dsl.reusableJob

object GoCreateTagAdapter : AdapterWorkflow("go-create-tag.yml", "Go Create Tag") {

    val goVersion = input(
        "go-version",
        description = "Go version to use",
        default = DEFAULT_GO_VERSION,
    )
    val checkCommand = input(
        "check-command",
        description = "Go validation command",
        default = "make test",
    )
    val defaultBump = input(
        "default-bump",
        description = "Default version bump type (major, minor, patch)",
        default = "patch",
    )
    val tagPrefix = input(
        "tag-prefix",
        description = "Prefix for the tag",
        default = "v",
    )
    val releaseBranches = input(
        "release-branches",
        description = "Comma-separated branch patterns for releases",
        default = DEFAULT_RELEASE_BRANCHES,
    )

    init {
        APP_SECRETS.forEach { (name, s) ->
            secret(name, description = s.description, required = s.required)
        }
    }

    override fun jobs(): List<ReusableWorkflowJobDef> = listOf(
        reusableJob(id = "create-tag", uses = CreateTagWorkflow) {
            CreateTagWorkflow.setupAction(SetupTool.Go.id)
            CreateTagWorkflow.setupParams(SetupTool.Go.toParamsJson(goVersion.ref))
            CreateTagWorkflow.checkCommand(checkCommand.ref)
            CreateTagWorkflow.defaultBump(defaultBump.ref)
            CreateTagWorkflow.tagPrefix(tagPrefix.ref)
            CreateTagWorkflow.releaseBranches(releaseBranches.ref)
            secrets(APP_SECRETS.passthrough())
        },
    )
}
```

- [ ] **Step 4: Rewrite `GoManualCreateTag.kt`**

Replace with:

```kotlin
package workflows.adapters.tag

import config.APP_SECRETS
import config.DEFAULT_GO_VERSION
import config.SetupTool
import config.passthrough
import dsl.AdapterWorkflow
import dsl.ManualCreateTagWorkflow
import dsl.ReusableWorkflowJobDef
import dsl.reusableJob

object GoManualCreateTagAdapter : AdapterWorkflow("go-manual-create-tag.yml", "Go Manual Create Tag") {

    val tagVersion = input(
        "tag-version",
        description = "Version to tag (e.g. 1.2.3)",
        required = true,
    )
    val goVersion = input(
        "go-version",
        description = "Go version to use",
        default = DEFAULT_GO_VERSION,
    )
    val checkCommand = input(
        "check-command",
        description = "Go validation command",
        default = "make test",
    )
    val tagPrefix = input(
        "tag-prefix",
        description = "Prefix for the tag",
        default = "v",
    )

    init {
        APP_SECRETS.forEach { (name, s) ->
            secret(name, description = s.description, required = s.required)
        }
    }

    override fun jobs(): List<ReusableWorkflowJobDef> = listOf(
        reusableJob(id = "manual-tag", uses = ManualCreateTagWorkflow) {
            ManualCreateTagWorkflow.tagVersion(tagVersion.ref)
            ManualCreateTagWorkflow.tagPrefix(tagPrefix.ref)
            ManualCreateTagWorkflow.setupAction(SetupTool.Go.id)
            ManualCreateTagWorkflow.setupParams(SetupTool.Go.toParamsJson(goVersion.ref))
            ManualCreateTagWorkflow.checkCommand(checkCommand.ref)
            secrets(APP_SECRETS.passthrough())
        },
    )
}
```

- [ ] **Step 5: Update `Generate.kt` — replace tag adapter calls**

Remove these imports:
```kotlin
import workflows.adapters.tag.generateGoCreateTag
import workflows.adapters.tag.generateGoManualCreateTag
import workflows.adapters.tag.generateGradleCreateTag
import workflows.adapters.tag.generateGradleManualCreateTag
```

Add these imports:
```kotlin
import workflows.adapters.tag.GradleCreateTagAdapter
import workflows.adapters.tag.GradleManualCreateTagAdapter
import workflows.adapters.tag.GoCreateTagAdapter
import workflows.adapters.tag.GoManualCreateTagAdapter
```

Replace the four calls:
```kotlin
    GradleCreateTagAdapter.generate(outputDir)
    GradleManualCreateTagAdapter.generate(outputDir)
    GoCreateTagAdapter.generate(outputDir)
    GoManualCreateTagAdapter.generate(outputDir)
```

- [ ] **Step 6: Verify build compiles and YAML matches**

```bash
./gradlew compileKotlin && ./gradlew run
diff .github/workflows/gradle-create-tag.yml /tmp/workflows-baseline/gradle-create-tag.yml
diff .github/workflows/gradle-manual-create-tag.yml /tmp/workflows-baseline/gradle-manual-create-tag.yml
diff .github/workflows/go-create-tag.yml /tmp/workflows-baseline/go-create-tag.yml
diff .github/workflows/go-manual-create-tag.yml /tmp/workflows-baseline/go-manual-create-tag.yml
```

Expected: Semantically equivalent.

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/workflows/adapters/tag/
git add src/main/kotlin/generate/Generate.kt
git add .github/workflows/gradle-create-tag.yml .github/workflows/gradle-manual-create-tag.yml .github/workflows/go-create-tag.yml .github/workflows/go-manual-create-tag.yml
git commit -m "refactor: migrate tag adapters to AdapterWorkflow classes with kaml"
```

---

### Task 9: Update AppDeploy and Cleanup

**Files:**
- Modify: `src/main/kotlin/workflows/adapters/deploy/AppDeploy.kt`
- Delete: `src/main/kotlin/config/CommonInputs.kt`
- Modify: `src/main/kotlin/dsl/WorkflowHelpers.kt`
- Modify: `src/main/kotlin/generate/Generate.kt` (final cleanup)

- [ ] **Step 1: Update `AppDeploy.kt` to inline inputs instead of `CommonInputs`**

`AppDeploy` uses the `workflow()` DSL directly (not `generateAdapterWorkflow()`), so it stays as a function. Replace `CommonInputs` usage with inline `WorkflowCall.Input` construction:

```kotlin
package workflows.adapters.deploy

import config.SetupTool
import dsl.conditionalSetupSteps
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
                command = "git checkout \"\${{ inputs.tag }}\"",
            )
            run(
                name = "Deploy",
                command = "\${{ inputs.deploy-command }}",
            )
        }
    }
}
```

- [ ] **Step 2: Remove `inputRef()` from `WorkflowHelpers.kt`**

In `src/main/kotlin/dsl/WorkflowHelpers.kt`, remove the `inputRef()` function:

```kotlin
package dsl

import actions.SetupAction
import config.SetupTool
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
```

- [ ] **Step 3: Delete `CommonInputs.kt`**

```bash
rm src/main/kotlin/config/CommonInputs.kt
```

- [ ] **Step 4: Remove legacy `generateAdapterWorkflow()` function if still present**

If the temporary bridge function was added in Task 5, remove it now from `AdapterWorkflow.kt`. The file should contain only the `AdapterWorkflow` abstract class.

- [ ] **Step 5: Final cleanup of `Generate.kt`**

Verify `src/main/kotlin/generate/Generate.kt` has no remaining imports from deleted files. The final file should look like:

```kotlin
package generate

import workflows.adapters.check.GradleCheckAdapter
import workflows.adapters.deploy.generateAppDeploy
import workflows.adapters.release.AppReleaseAdapter
import workflows.adapters.release.GradlePluginReleaseAdapter
import workflows.adapters.release.KotlinLibraryReleaseAdapter
import workflows.adapters.tag.GoCreateTagAdapter
import workflows.adapters.tag.GoManualCreateTagAdapter
import workflows.adapters.tag.GradleCreateTagAdapter
import workflows.adapters.tag.GradleManualCreateTagAdapter
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

    // Base workflows (using github-workflows-kt DSL directly)
    generateCheck()
    generateConventionalCommitCheck()
    generateCreateTag()
    generateManualCreateTag()
    generateRelease()
    generatePublish()
    generateLabeler()

    // Adapter workflows (using AdapterWorkflow + kaml)
    GradleCheckAdapter("app-check.yml", "Application Check").generate(outputDir)
    GradleCheckAdapter("gradle-check.yml", "Gradle Check").generate(outputDir)
    GradleCheckAdapter("gradle-plugin-check.yml", "Gradle Plugin Check").generate(outputDir)
    GradleCheckAdapter("kotlin-library-check.yml", "Kotlin Library Check").generate(outputDir)
    AppReleaseAdapter.generate(outputDir)
    GradlePluginReleaseAdapter.generate(outputDir)
    KotlinLibraryReleaseAdapter.generate(outputDir)
    GradleCreateTagAdapter.generate(outputDir)
    GradleManualCreateTagAdapter.generate(outputDir)
    GoCreateTagAdapter.generate(outputDir)
    GoManualCreateTagAdapter.generate(outputDir)

    // Deploy (uses workflow() DSL directly)
    generateAppDeploy(outputDir)
}
```

- [ ] **Step 6: Full verification**

```bash
./gradlew compileKotlin && ./gradlew run
diff -r /tmp/workflows-baseline .github/workflows
```

Expected: All YAML files semantically equivalent to baseline. Review any differences — they should be limited to quoting style changes from kaml vs the old StringBuilder approach.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "refactor: delete CommonInputs, remove inputRef, finalize adapter migration"
```

---

### Task 10: Kaml Output Tuning

**Files:**
- Modify: `src/main/kotlin/dsl/yaml/AdapterWorkflowYaml.kt` (as needed)

This task handles any YAML output differences found during verification in previous tasks. Only execute if `diff` showed meaningful differences.

- [ ] **Step 1: Compare baseline vs generated YAML in detail**

```bash
diff -r /tmp/workflows-baseline .github/workflows | head -100
```

- [ ] **Step 2: Fix identified differences**

Common fixes:

**Problem: Strings not single-quoted.** Kaml may output `name: Gradle Check` instead of `name: 'Gradle Check'`. Fix by adjusting `singleLineStringStyle` in `adapterYaml` config. Check kaml docs for exact API — it may be `SingleLineStringStyle.SingleQuoted` or a different name.

**Problem: Boolean defaults serialized as strings.** If `default: 'true'` appears instead of `default: true`, verify `YamlDefaultSerializer.serialize()` calls `encoder.encodeBoolean()` not `encoder.encodeString()`.

**Problem: Empty string defaults omitted.** If kaml drops `default: ''` fields, set `encodeDefaults = true` in the config and use explicit defaults on the DTO properties.

**Problem: `needs` as list instead of scalar.** If kaml outputs `needs:\n- 'release'` instead of `needs: 'release'`, fix the `JobNeeds` serializer or adjust the DTO to use `String?` for single-need and `List<String>?` for multiple.

**Problem: Map key ordering.** If YAML keys appear in a different order than baseline, kaml serializes in declaration order of `@Serializable` data class fields — reorder fields to match expected YAML output.

- [ ] **Step 3: Re-verify after fixes**

```bash
./gradlew run
diff -r /tmp/workflows-baseline .github/workflows
```

- [ ] **Step 4: Commit if any changes were made**

```bash
git add src/main/kotlin/dsl/yaml/AdapterWorkflowYaml.kt
git add .github/workflows/
git commit -m "fix: tune kaml output to match expected YAML format"
```

---

### Task 11: Custom Actions Audit

**Files:**
- Modify (potentially): `src/main/kotlin/actions/Actions.kt`
- Modify (potentially): `build.gradle.kts`

- [ ] **Step 1: Check if `mathieudutour/github-tag-action` is available in JIT registry**

```bash
cd /Users/dmitriimedakin/IdeaProjects/zenhelix/ci-workflows
# Try adding the dependency and see if Gradle resolves it
# Temporarily add to build.gradle.kts:
# implementation("mathieudutour:github-tag-action:v6")
# Then run:
./gradlew dependencies --configuration runtimeClasspath 2>&1 | grep -i "mathieudutour"
```

If it resolves: replace `GithubTagAction` custom class with the JIT binding.
If it fails (404 / not found): keep custom class. The comment in `build.gradle.kts` already notes this.

- [ ] **Step 2: If JIT binding available, replace `GithubTagAction`**

In `build.gradle.kts`, add:
```kotlin
implementation("mathieudutour:github-tag-action:v6")
```

In `Actions.kt`, remove the `GithubTagAction` class.

In workflow files that use it (check `CreateTag.kt`, `ManualCreateTag.kt`), replace with the JIT binding import.

If NOT available: no changes needed.

- [ ] **Step 3: Verify build and YAML**

```bash
./gradlew compileKotlin && ./gradlew run
diff -r /tmp/workflows-baseline .github/workflows
```

- [ ] **Step 4: Commit if any changes were made**

```bash
git add build.gradle.kts src/main/kotlin/actions/Actions.kt
git commit -m "chore: audit custom actions, replace JIT bindings where available"
```

---

### Task 12: Final Verification and Cleanup

- [ ] **Step 1: Full clean build**

```bash
./gradlew clean compileKotlin
```

Expected: BUILD SUCCESSFUL with zero warnings about `_Untyped` deprecation (except `ReleaseChangelogBuilderAction_Untyped` which has no typed alternative).

- [ ] **Step 2: Regenerate all YAML and verify**

```bash
./gradlew run
diff -r /tmp/workflows-baseline .github/workflows
```

Document any remaining differences and confirm they are cosmetic (quoting style only).

- [ ] **Step 3: Verify no stale files remain**

```bash
# Check that deleted files are gone
test ! -f src/main/kotlin/config/CommonInputs.kt && echo "CommonInputs deleted OK"
test ! -f src/main/kotlin/workflows/adapters/check/AppCheck.kt && echo "AppCheck deleted OK"
test ! -f src/main/kotlin/workflows/adapters/check/GradlePluginCheck.kt && echo "GradlePluginCheck deleted OK"
test ! -f src/main/kotlin/workflows/adapters/check/KotlinLibraryCheck.kt && echo "KotlinLibraryCheck deleted OK"
```

- [ ] **Step 4: Verify no remaining `inputRef()` calls**

```bash
grep -r "inputRef" src/main/kotlin/ || echo "No inputRef references remaining"
```

Expected: No matches (the function was removed and all callers replaced with `.ref`).

- [ ] **Step 5: Verify no remaining `CommonInputs` references**

```bash
grep -r "CommonInputs" src/main/kotlin/ || echo "No CommonInputs references remaining"
```

Expected: No matches.

- [ ] **Step 6: Final commit if any cleanup was done**

```bash
git add -A
git status
# If there are changes:
git commit -m "chore: final verification and cleanup of typed workflow migration"
```
