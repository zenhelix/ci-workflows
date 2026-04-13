# Typed Adapter Job Builders Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the adapter→base workflow connection type-safe: each workflow gets its own typed JobBuilder, strategy uses typed MatrixDef, secrets auto-collect from jobs, SetupTool uses JSON serialization.

**Architecture:** Each `ReusableWorkflow` object in `Workflows.kt` gains a nested `JobBuilder` class with methods matching its inputs. The base `ReusableWorkflowJobBuilder` is refactored to use `set(input, value)` instead of `WorkflowInput.invoke()`. `AdapterWorkflow.generate()` auto-collects secrets from job definitions, eliminating manual `init { secrets(X) }` + `passthrough()` duplication.

**Tech Stack:** Kotlin 2.3.20, kotlinx-serialization (core + json), kaml 0.104.0, github-workflows-kt 3.7.0

---

### Task 0: Save YAML baseline for verification

**Files:**
- Read: `.github/workflows/*.yml`

- [ ] **Step 1: Copy current generated YAML as baseline**

```bash
cp -r .github/workflows .github/workflows-baseline
```

This baseline is used at the end to verify generated output hasn't changed.

---

### Task 1: Add kotlinx-serialization-json dependency

**Files:**
- Modify: `build.gradle.kts:19`

- [ ] **Step 1: Add json dependency**

In `build.gradle.kts`, add `kotlinx-serialization-json` next to the existing `core` dependency. Change line 19 from:

```kotlin
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.11.0")
```

to:

```kotlin
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
```

- [ ] **Step 2: Verify build compiles**

```bash
cd /Users/dmitriimedakin/IdeaProjects/zenhelix/ci-workflows && ./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add build.gradle.kts
git commit -m "chore: add kotlinx-serialization-json dependency"
```

---

### Task 2: Create MatrixDef and MatrixRef

**Files:**
- Create: `src/main/kotlin/dsl/MatrixDef.kt`

- [ ] **Step 1: Create the file**

Create `src/main/kotlin/dsl/MatrixDef.kt`:

```kotlin
package dsl

class MatrixRef(val key: String) {
    val ref: String get() = "\${{ matrix.$key }}"
}

data class MatrixDef(val entries: Map<String, String>)
```

- [ ] **Step 2: Verify build compiles**

```bash
cd /Users/dmitriimedakin/IdeaProjects/zenhelix/ci-workflows && ./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/dsl/MatrixDef.kt
git commit -m "feat: add MatrixDef and MatrixRef types"
```

---

### Task 3: Refactor ReusableWorkflowJobBuilder base class

This is the core change. Replace `WorkflowInput.invoke()` extension with `set()`, add `MatrixDef` support, add `passthroughSecrets()` / `passthroughAllSecrets()`, add required-input validation in `build()`.

**Files:**
- Modify: `src/main/kotlin/dsl/ReusableWorkflowJobBuilder.kt`

- [ ] **Step 1: Replace entire file content**

Replace `src/main/kotlin/dsl/ReusableWorkflowJobBuilder.kt` with:

```kotlin
package dsl

import dsl.yaml.JobYaml
import dsl.yaml.NeedsYaml
import dsl.yaml.StrategyYaml

abstract class ReusableWorkflowJobBuilder(private val workflow: ReusableWorkflow) {
    private val withMap = mutableMapOf<String, String>()
    private val secretsMap = mutableMapOf<String, String>()
    private var needsList = emptyList<String>()
    private var matrixDef: MatrixDef? = null

    protected fun set(input: WorkflowInput, value: String) {
        withMap[input.name] = value
    }

    fun needs(vararg jobIds: String) {
        needsList = jobIds.toList()
    }

    fun strategy(matrix: MatrixDef) {
        matrixDef = matrix
    }

    fun passthroughSecrets(vararg secrets: WorkflowSecret) {
        secrets.forEach { secret ->
            secretsMap[secret.name] = secret.ref
        }
    }

    fun passthroughAllSecrets() {
        workflow.secrets.forEach { (name, _) ->
            secretsMap[name] = "\${{ secrets.$name }}"
        }
    }

    internal fun build(id: String): ReusableWorkflowJobDef {
        val missingRequired = workflow.inputs
            .filter { (_, input) -> input.required }
            .keys
            .filter { it !in withMap }
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

Note: The `reusableJob` free function is removed from this file — it moves to `ReusableWorkflow.kt` in Task 5 after typed builders exist. The code will not compile yet (that's expected; Tasks 3-5 form an atomic group).

- [ ] **Step 2: Commit (WIP, won't compile yet)**

```bash
git add src/main/kotlin/dsl/ReusableWorkflowJobBuilder.kt
git commit -m "refactor: rework ReusableWorkflowJobBuilder with typed set(), MatrixDef, passthroughSecrets"
```

---

### Task 4: Add createJobBuilder factory to ReusableWorkflow

**Files:**
- Modify: `src/main/kotlin/dsl/ReusableWorkflow.kt`

- [ ] **Step 1: Add abstract createJobBuilder method and remove secrets(map) helper**

In `src/main/kotlin/dsl/ReusableWorkflow.kt`, add the `createJobBuilder()` factory and the `reusableJob` function. Also remove the `protected fun secrets(map: ...)` method (adapters will no longer call it).

Replace the entire file with:

```kotlin
package dsl

import config.reusableWorkflow
import dsl.yaml.InputYaml
import dsl.yaml.SecretYaml
import dsl.yaml.YamlDefault
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall

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
    val usesString: String get() = reusableWorkflow(fileName)

    abstract fun createJobBuilder(): ReusableWorkflowJobBuilder

    fun toInputsYaml(): Map<String, InputYaml>? {
        if (_inputs.isEmpty()) return null
        return _inputs.map { (name, input) ->
            val boolDefault = _booleanDefaults[name]
            val default = when {
                boolDefault != null  -> YamlDefault.BooleanValue(boolDefault)
                input.default != null -> YamlDefault.StringValue(input.default!!)
                else                  -> null
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
            name to SecretYaml(description = secret.description, required = secret.required)
        }.toMap()
    }

    /**
     * Creates a WorkflowCall trigger from this workflow's inputs and secrets.
     * Handles boolean defaults correctly by falling back to _customArguments
     * when boolean inputs with defaults exist (WorkflowCall.Input only supports String? defaults).
     */
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
        _inputs.map { (name, input) ->
            name to buildMap<String, Any?> {
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
        }.toMap()
}

class WorkflowInput(val name: String) {
    val ref: String get() = "\${{ inputs.$name }}"
}

class WorkflowSecret(val name: String) {
    val ref: String get() = "\${{ secrets.$name }}"
}

fun <B : ReusableWorkflowJobBuilder> reusableJob(
    id: String,
    uses: ReusableWorkflow,
    block: B.() -> Unit = {},
): ReusableWorkflowJobDef {
    @Suppress("UNCHECKED_CAST")
    val builder = uses.createJobBuilder() as B
    builder.block()
    return builder.build(id)
}
```

- [ ] **Step 2: Commit (WIP, won't compile yet)**

```bash
git add src/main/kotlin/dsl/ReusableWorkflow.kt
git commit -m "refactor: add createJobBuilder factory and reusableJob generic function"
```

---

### Task 5: Add typed JobBuilder to each workflow object

**Files:**
- Modify: `src/main/kotlin/dsl/Workflows.kt`

- [ ] **Step 1: Replace entire file with typed builders**

Replace `src/main/kotlin/dsl/Workflows.kt` with:

```kotlin
package dsl

import config.DEFAULT_CHANGELOG_CONFIG
import config.DEFAULT_RELEASE_BRANCHES

object CheckWorkflow : ReusableWorkflow("check.yml") {
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

    override fun createJobBuilder() = JobBuilder()

    class JobBuilder : ReusableWorkflowJobBuilder(CheckWorkflow) {
        fun setupAction(value: String) = set(CheckWorkflow.setupAction, value)
        fun setupParams(value: String) = set(CheckWorkflow.setupParams, value)
        fun checkCommand(value: String) = set(CheckWorkflow.checkCommand, value)
    }
}

object ConventionalCommitCheckWorkflow : ReusableWorkflow("conventional-commit-check.yml") {
    val allowedTypes = input(
        "allowed-types",
        description = "Comma-separated list of allowed commit types",
        default = "feat,fix,refactor,docs,test,chore,perf,ci"
    )

    override fun createJobBuilder() = JobBuilder()

    class JobBuilder : ReusableWorkflowJobBuilder(ConventionalCommitCheckWorkflow) {
        fun allowedTypes(value: String) = set(ConventionalCommitCheckWorkflow.allowedTypes, value)
    }
}

object CreateTagWorkflow : ReusableWorkflow("create-tag.yml") {
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

    override fun createJobBuilder() = JobBuilder()

    class JobBuilder : ReusableWorkflowJobBuilder(CreateTagWorkflow) {
        fun setupAction(value: String) = set(CreateTagWorkflow.setupAction, value)
        fun setupParams(value: String) = set(CreateTagWorkflow.setupParams, value)
        fun checkCommand(value: String) = set(CreateTagWorkflow.checkCommand, value)
        fun defaultBump(value: String) = set(CreateTagWorkflow.defaultBump, value)
        fun tagPrefix(value: String) = set(CreateTagWorkflow.tagPrefix, value)
        fun releaseBranches(value: String) = set(CreateTagWorkflow.releaseBranches, value)
    }
}

object ManualCreateTagWorkflow : ReusableWorkflow("manual-create-tag.yml") {
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

    override fun createJobBuilder() = JobBuilder()

    class JobBuilder : ReusableWorkflowJobBuilder(ManualCreateTagWorkflow) {
        fun tagVersion(value: String) = set(ManualCreateTagWorkflow.tagVersion, value)
        fun tagPrefix(value: String) = set(ManualCreateTagWorkflow.tagPrefix, value)
        fun setupAction(value: String) = set(ManualCreateTagWorkflow.setupAction, value)
        fun setupParams(value: String) = set(ManualCreateTagWorkflow.setupParams, value)
        fun checkCommand(value: String) = set(ManualCreateTagWorkflow.checkCommand, value)
    }
}

object ReleaseWorkflow : ReusableWorkflow("release.yml") {
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

    override fun createJobBuilder() = JobBuilder()

    class JobBuilder : ReusableWorkflowJobBuilder(ReleaseWorkflow) {
        fun changelogConfig(value: String) = set(ReleaseWorkflow.changelogConfig, value)
        fun draft(value: String) = set(ReleaseWorkflow.draft, value)
    }
}

object PublishWorkflow : ReusableWorkflow("publish.yml") {
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

    override fun createJobBuilder() = JobBuilder()

    class JobBuilder : ReusableWorkflowJobBuilder(PublishWorkflow) {
        fun setupAction(value: String) = set(PublishWorkflow.setupAction, value)
        fun setupParams(value: String) = set(PublishWorkflow.setupParams, value)
        fun publishCommand(value: String) = set(PublishWorkflow.publishCommand, value)
    }
}

object LabelerWorkflow : ReusableWorkflow("labeler.yml") {
    val configPath = input(
        "config-path",
        description = "Path to labeler configuration file",
        default = ".github/labeler.yml"
    )

    override fun createJobBuilder() = JobBuilder()

    class JobBuilder : ReusableWorkflowJobBuilder(LabelerWorkflow) {
        fun configPath(value: String) = set(LabelerWorkflow.configPath, value)
    }
}
```

- [ ] **Step 2: Verify build compiles**

```bash
cd /Users/dmitriimedakin/IdeaProjects/zenhelix/ci-workflows && ./gradlew compileKotlin
```

Expected: compilation errors in adapter files (they still use old API). The DSL core itself should be valid.

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/dsl/Workflows.kt
git commit -m "feat: add typed JobBuilder to each workflow object"
```

---

### Task 6: Refactor SetupTool to use Json.encodeToString

**Files:**
- Modify: `src/main/kotlin/config/SetupTool.kt`

- [ ] **Step 1: Replace file content**

Replace `src/main/kotlin/config/SetupTool.kt` with:

```kotlin
package config

import kotlinx.serialization.json.Json

sealed class SetupTool(
    val actionName: String,
    val versionKey: String,
    val defaultVersion: String,
) {
    val id: String get() = actionName.removePrefix("setup-")

    fun toParamsJson(versionExpr: String): String =
        Json.encodeToString(mapOf(versionKey to versionExpr))

    data object Gradle : SetupTool("setup-gradle", "java-version", DEFAULT_JAVA_VERSION)
    data object Go : SetupTool("setup-go", "go-version", DEFAULT_GO_VERSION)
    data object Python : SetupTool("setup-python", "python-version", DEFAULT_PYTHON_VERSION)
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/kotlin/config/SetupTool.kt
git commit -m "refactor: SetupTool.toParamsJson via Json.encodeToString"
```

---

### Task 7: Refactor AdapterWorkflow to auto-collect secrets from jobs

**Files:**
- Modify: `src/main/kotlin/dsl/AdapterWorkflow.kt`

- [ ] **Step 1: Replace file content**

Replace `src/main/kotlin/dsl/AdapterWorkflow.kt` with:

```kotlin
package dsl

import dsl.yaml.AdapterWorkflowYaml
import dsl.yaml.SecretYaml
import dsl.yaml.TriggerYaml
import dsl.yaml.WorkflowCallBodyYaml
import dsl.yaml.adapterWorkflowYaml
import java.io.File

// Kaml SingleQuoted style quotes map keys too; strip quotes to match GitHub Actions convention.
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
        val secretNames = jobDefs.flatMap { it.secrets.keys }.toSet()
        if (secretNames.isEmpty()) return null

        return secretNames.associateWith { name ->
            val workflowSecret = jobDefs
                .mapNotNull { job -> job.uses.secrets[name] }
                .firstOrNull()

            SecretYaml(
                description = workflowSecret?.description ?: name,
                required = workflowSecret?.required ?: true,
            )
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/kotlin/dsl/AdapterWorkflow.kt
git commit -m "refactor: AdapterWorkflow auto-collects secrets from jobs"
```

---

### Task 8: Delete Secrets.kt

**Files:**
- Delete: `src/main/kotlin/config/Secrets.kt`

- [ ] **Step 1: Delete the file**

```bash
rm /Users/dmitriimedakin/IdeaProjects/zenhelix/ci-workflows/src/main/kotlin/config/Secrets.kt
```

- [ ] **Step 2: Commit**

```bash
git add src/main/kotlin/config/Secrets.kt
git commit -m "refactor: delete Secrets.kt, secrets now auto-collected from workflow objects"
```

---

### Task 9: Migrate GradleCheckAdapter

**Files:**
- Modify: `src/main/kotlin/workflows/adapters/check/GradleCheck.kt`

- [ ] **Step 1: Replace file content**

Replace `src/main/kotlin/workflows/adapters/check/GradleCheck.kt` with:

```kotlin
package workflows.adapters.check

import config.DEFAULT_JAVA_VERSION
import config.JAVA_VERSION_MATRIX_EXPR
import config.SetupTool
import dsl.AdapterWorkflow
import dsl.CheckWorkflow
import dsl.ConventionalCommitCheckWorkflow
import dsl.MatrixDef
import dsl.MatrixRef
import dsl.ReusableWorkflowJobDef
import dsl.reusableJob

class GradleCheckAdapter(
    fileName: String,
    override val workflowName: String,
) : AdapterWorkflow(fileName) {

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

    override fun createJobBuilder() = CheckWorkflow.JobBuilder()

    override fun jobs(): List<ReusableWorkflowJobDef> = listOf(
        reusableJob(id = "conventional-commit", uses = ConventionalCommitCheckWorkflow),
        reusableJob<CheckWorkflow.JobBuilder>(id = "check", uses = CheckWorkflow) {
            strategy(MatrixDef(mapOf(javaVersionMatrix.key to JAVA_VERSION_MATRIX_EXPR)))
            setupAction(SetupTool.Gradle.id)
            setupParams(SetupTool.Gradle.toParamsJson(javaVersionMatrix.ref))
            checkCommand(gradleCommand.ref)
        },
    )
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/kotlin/workflows/adapters/check/GradleCheck.kt
git commit -m "refactor: migrate GradleCheckAdapter to typed builder"
```

---

### Task 10: Migrate AppReleaseAdapter

**Files:**
- Modify: `src/main/kotlin/workflows/adapters/release/AppRelease.kt`

- [ ] **Step 1: Replace file content**

Replace `src/main/kotlin/workflows/adapters/release/AppRelease.kt` with:

```kotlin
package workflows.adapters.release

import config.DEFAULT_CHANGELOG_CONFIG
import dsl.AdapterWorkflow
import dsl.ReleaseWorkflow
import dsl.ReusableWorkflowJobDef
import dsl.reusableJob

object AppReleaseAdapter : AdapterWorkflow("app-release.yml") {
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

    override fun createJobBuilder() = ReleaseWorkflow.JobBuilder()

    override fun jobs(): List<ReusableWorkflowJobDef> = listOf(
        reusableJob<ReleaseWorkflow.JobBuilder>(id = "release", uses = ReleaseWorkflow) {
            changelogConfig(this@AppReleaseAdapter.changelogConfig.ref)
            draft(this@AppReleaseAdapter.draft.ref)
        },
    )
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/kotlin/workflows/adapters/release/AppRelease.kt
git commit -m "refactor: migrate AppReleaseAdapter to typed builder"
```

---

### Task 11: Migrate GradlePluginReleaseAdapter

**Files:**
- Modify: `src/main/kotlin/workflows/adapters/release/GradlePluginRelease.kt`

- [ ] **Step 1: Replace file content**

Replace `src/main/kotlin/workflows/adapters/release/GradlePluginRelease.kt` with:

```kotlin
package workflows.adapters.release

import config.DEFAULT_CHANGELOG_CONFIG
import config.DEFAULT_JAVA_VERSION
import config.SetupTool
import dsl.AdapterWorkflow
import dsl.PublishWorkflow
import dsl.ReleaseWorkflow
import dsl.ReusableWorkflowJobDef
import dsl.reusableJob

object GradlePluginReleaseAdapter : AdapterWorkflow("gradle-plugin-release.yml") {
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

    override fun createJobBuilder() = PublishWorkflow.JobBuilder()

    override fun jobs(): List<ReusableWorkflowJobDef> = listOf(
        reusableJob<ReleaseWorkflow.JobBuilder>(id = "release", uses = ReleaseWorkflow) {
            changelogConfig(this@GradlePluginReleaseAdapter.changelogConfig.ref)
        },
        reusableJob<PublishWorkflow.JobBuilder>(id = "publish", uses = PublishWorkflow) {
            needs("release")
            setupAction(SetupTool.Gradle.id)
            setupParams(SetupTool.Gradle.toParamsJson(javaVersion.ref))
            publishCommand(this@GradlePluginReleaseAdapter.publishCommand.ref)
            passthroughSecrets(
                PublishWorkflow.mavenSonatypeUsername,
                PublishWorkflow.mavenSonatypeToken,
                PublishWorkflow.mavenSonatypeSigningKeyId,
                PublishWorkflow.mavenSonatypeSigningPubKeyAsciiArmored,
                PublishWorkflow.mavenSonatypeSigningKeyAsciiArmored,
                PublishWorkflow.mavenSonatypeSigningPassword,
                PublishWorkflow.gradlePublishKey,
                PublishWorkflow.gradlePublishSecret,
            )
        },
    )
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/kotlin/workflows/adapters/release/GradlePluginRelease.kt
git commit -m "refactor: migrate GradlePluginReleaseAdapter to typed builder"
```

---

### Task 12: Migrate KotlinLibraryReleaseAdapter

**Files:**
- Modify: `src/main/kotlin/workflows/adapters/release/KotlinLibraryRelease.kt`

- [ ] **Step 1: Replace file content**

Replace `src/main/kotlin/workflows/adapters/release/KotlinLibraryRelease.kt` with:

```kotlin
package workflows.adapters.release

import config.DEFAULT_CHANGELOG_CONFIG
import config.DEFAULT_JAVA_VERSION
import config.SetupTool
import dsl.AdapterWorkflow
import dsl.PublishWorkflow
import dsl.ReleaseWorkflow
import dsl.ReusableWorkflowJobDef
import dsl.reusableJob

object KotlinLibraryReleaseAdapter : AdapterWorkflow("kotlin-library-release.yml") {
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

    override fun createJobBuilder() = PublishWorkflow.JobBuilder()

    override fun jobs(): List<ReusableWorkflowJobDef> = listOf(
        reusableJob<ReleaseWorkflow.JobBuilder>(id = "release", uses = ReleaseWorkflow) {
            changelogConfig(this@KotlinLibraryReleaseAdapter.changelogConfig.ref)
        },
        reusableJob<PublishWorkflow.JobBuilder>(id = "publish", uses = PublishWorkflow) {
            needs("release")
            setupAction(SetupTool.Gradle.id)
            setupParams(SetupTool.Gradle.toParamsJson(javaVersion.ref))
            publishCommand(this@KotlinLibraryReleaseAdapter.publishCommand.ref)
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

- [ ] **Step 2: Commit**

```bash
git add src/main/kotlin/workflows/adapters/release/KotlinLibraryRelease.kt
git commit -m "refactor: migrate KotlinLibraryReleaseAdapter to typed builder"
```

---

### Task 13: Migrate GradleCreateTagAdapter

**Files:**
- Modify: `src/main/kotlin/workflows/adapters/tag/GradleCreateTag.kt`

- [ ] **Step 1: Replace file content**

Replace `src/main/kotlin/workflows/adapters/tag/GradleCreateTag.kt` with:

```kotlin
package workflows.adapters.tag

import config.DEFAULT_JAVA_VERSION
import config.DEFAULT_RELEASE_BRANCHES
import config.SetupTool
import dsl.AdapterWorkflow
import dsl.CreateTagWorkflow
import dsl.ReusableWorkflowJobDef
import dsl.reusableJob

object GradleCreateTagAdapter : AdapterWorkflow("gradle-create-tag.yml") {
    override val workflowName = "Gradle Create Tag"

    val javaVersion = input("java-version", description = "JDK version to use", default = DEFAULT_JAVA_VERSION)
    val gradleCommand = input("gradle-command", description = "Gradle check command", default = "./gradlew check")
    val defaultBump = input("default-bump", description = "Default version bump type (major, minor, patch)", default = "patch")
    val tagPrefix = input("tag-prefix", description = "Prefix for the tag", default = "")
    val releaseBranches = input("release-branches", description = "Comma-separated branch patterns for releases", default = DEFAULT_RELEASE_BRANCHES)

    override fun createJobBuilder() = CreateTagWorkflow.JobBuilder()

    override fun jobs(): List<ReusableWorkflowJobDef> = listOf(
        reusableJob<CreateTagWorkflow.JobBuilder>(id = "create-tag", uses = CreateTagWorkflow) {
            setupAction(SetupTool.Gradle.id)
            setupParams(SetupTool.Gradle.toParamsJson(javaVersion.ref))
            checkCommand(gradleCommand.ref)
            defaultBump(this@GradleCreateTagAdapter.defaultBump.ref)
            tagPrefix(this@GradleCreateTagAdapter.tagPrefix.ref)
            releaseBranches(this@GradleCreateTagAdapter.releaseBranches.ref)
            passthroughAllSecrets()
        },
    )
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/kotlin/workflows/adapters/tag/GradleCreateTag.kt
git commit -m "refactor: migrate GradleCreateTagAdapter to typed builder"
```

---

### Task 14: Migrate GoCreateTagAdapter

**Files:**
- Modify: `src/main/kotlin/workflows/adapters/tag/GoCreateTag.kt`

- [ ] **Step 1: Replace file content**

Replace `src/main/kotlin/workflows/adapters/tag/GoCreateTag.kt` with:

```kotlin
package workflows.adapters.tag

import config.DEFAULT_GO_VERSION
import config.DEFAULT_RELEASE_BRANCHES
import config.SetupTool
import dsl.AdapterWorkflow
import dsl.CreateTagWorkflow
import dsl.ReusableWorkflowJobDef
import dsl.reusableJob

object GoCreateTagAdapter : AdapterWorkflow("go-create-tag.yml") {
    override val workflowName = "Go Create Tag"

    val goVersion = input("go-version", description = "Go version to use", default = DEFAULT_GO_VERSION)
    val checkCommand = input("check-command", description = "Go validation command", default = "make test")
    val defaultBump = input("default-bump", description = "Default version bump type (major, minor, patch)", default = "patch")
    val tagPrefix = input("tag-prefix", description = "Prefix for the tag", default = "v")
    val releaseBranches = input("release-branches", description = "Comma-separated branch patterns for releases", default = DEFAULT_RELEASE_BRANCHES)

    override fun createJobBuilder() = CreateTagWorkflow.JobBuilder()

    override fun jobs(): List<ReusableWorkflowJobDef> = listOf(
        reusableJob<CreateTagWorkflow.JobBuilder>(id = "create-tag", uses = CreateTagWorkflow) {
            setupAction(SetupTool.Go.id)
            setupParams(SetupTool.Go.toParamsJson(goVersion.ref))
            checkCommand(this@GoCreateTagAdapter.checkCommand.ref)
            defaultBump(this@GoCreateTagAdapter.defaultBump.ref)
            tagPrefix(this@GoCreateTagAdapter.tagPrefix.ref)
            releaseBranches(this@GoCreateTagAdapter.releaseBranches.ref)
            passthroughAllSecrets()
        },
    )
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/kotlin/workflows/adapters/tag/GoCreateTag.kt
git commit -m "refactor: migrate GoCreateTagAdapter to typed builder"
```

---

### Task 15: Migrate GradleManualCreateTagAdapter

**Files:**
- Modify: `src/main/kotlin/workflows/adapters/tag/GradleManualCreateTag.kt`

- [ ] **Step 1: Replace file content**

Replace `src/main/kotlin/workflows/adapters/tag/GradleManualCreateTag.kt` with:

```kotlin
package workflows.adapters.tag

import config.DEFAULT_JAVA_VERSION
import config.SetupTool
import dsl.AdapterWorkflow
import dsl.ManualCreateTagWorkflow
import dsl.ReusableWorkflowJobDef
import dsl.reusableJob

object GradleManualCreateTagAdapter : AdapterWorkflow("gradle-manual-create-tag.yml") {
    override val workflowName = "Gradle Manual Create Tag"

    val tagVersion = input("tag-version", description = "Version to tag (e.g. 1.2.3)", required = true)
    val javaVersion = input("java-version", description = "JDK version to use", default = DEFAULT_JAVA_VERSION)
    val gradleCommand = input("gradle-command", description = "Gradle check command", default = "./gradlew check")
    val tagPrefix = input("tag-prefix", description = "Prefix for the tag", default = "")

    override fun createJobBuilder() = ManualCreateTagWorkflow.JobBuilder()

    override fun jobs(): List<ReusableWorkflowJobDef> = listOf(
        reusableJob<ManualCreateTagWorkflow.JobBuilder>(id = "manual-tag", uses = ManualCreateTagWorkflow) {
            tagVersion(this@GradleManualCreateTagAdapter.tagVersion.ref)
            tagPrefix(this@GradleManualCreateTagAdapter.tagPrefix.ref)
            setupAction(SetupTool.Gradle.id)
            setupParams(SetupTool.Gradle.toParamsJson(javaVersion.ref))
            checkCommand(gradleCommand.ref)
            passthroughAllSecrets()
        },
    )
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/kotlin/workflows/adapters/tag/GradleManualCreateTag.kt
git commit -m "refactor: migrate GradleManualCreateTagAdapter to typed builder"
```

---

### Task 16: Migrate GoManualCreateTagAdapter

**Files:**
- Modify: `src/main/kotlin/workflows/adapters/tag/GoManualCreateTag.kt`

- [ ] **Step 1: Replace file content**

Replace `src/main/kotlin/workflows/adapters/tag/GoManualCreateTag.kt` with:

```kotlin
package workflows.adapters.tag

import config.DEFAULT_GO_VERSION
import config.SetupTool
import dsl.AdapterWorkflow
import dsl.ManualCreateTagWorkflow
import dsl.ReusableWorkflowJobDef
import dsl.reusableJob

object GoManualCreateTagAdapter : AdapterWorkflow("go-manual-create-tag.yml") {
    override val workflowName = "Go Manual Create Tag"

    val tagVersion = input("tag-version", description = "Version to tag (e.g. 1.2.3)", required = true)
    val goVersion = input("go-version", description = "Go version to use", default = DEFAULT_GO_VERSION)
    val checkCommand = input("check-command", description = "Go validation command", default = "make test")
    val tagPrefix = input("tag-prefix", description = "Prefix for the tag", default = "v")

    override fun createJobBuilder() = ManualCreateTagWorkflow.JobBuilder()

    override fun jobs(): List<ReusableWorkflowJobDef> = listOf(
        reusableJob<ManualCreateTagWorkflow.JobBuilder>(id = "manual-tag", uses = ManualCreateTagWorkflow) {
            tagVersion(this@GoManualCreateTagAdapter.tagVersion.ref)
            tagPrefix(this@GoManualCreateTagAdapter.tagPrefix.ref)
            setupAction(SetupTool.Go.id)
            setupParams(SetupTool.Go.toParamsJson(goVersion.ref))
            checkCommand(this@GoManualCreateTagAdapter.checkCommand.ref)
            passthroughAllSecrets()
        },
    )
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/kotlin/workflows/adapters/tag/GoManualCreateTag.kt
git commit -m "refactor: migrate GoManualCreateTagAdapter to typed builder"
```

---

### Task 17: Build, generate, and verify YAML output

**Files:**
- Read: `.github/workflows/*.yml`
- Read: `.github/workflows-baseline/*.yml`

- [ ] **Step 1: Build the project**

```bash
cd /Users/dmitriimedakin/IdeaProjects/zenhelix/ci-workflows && ./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL with no compilation errors.

- [ ] **Step 2: Generate workflows**

```bash
cd /Users/dmitriimedakin/IdeaProjects/zenhelix/ci-workflows && ./gradlew run
```

Expected: BUILD SUCCESSFUL, all YAML files regenerated.

- [ ] **Step 3: Diff against baseline**

```bash
diff -r .github/workflows-baseline .github/workflows
```

Expected: No differences. If there are differences, investigate and fix.

- [ ] **Step 4: Clean up baseline**

```bash
rm -rf .github/workflows-baseline
```

- [ ] **Step 5: Final commit**

```bash
git add .github/workflows
git commit -m "verify: regenerated YAML unchanged after typed builder migration"
```

---

### Task 18: Clean up unused StrategyYaml Map<String, Any> path

**Files:**
- Modify: `src/main/kotlin/dsl/yaml/AdapterWorkflowYaml.kt`

After all adapters are migrated, verify that `StrategyYaml.matrix` is already `Map<String, String>` (it is — line 77-78 of the current file). No change needed to the type, but verify there are no remaining `Map<String, Any>` references anywhere.

- [ ] **Step 1: Grep for Map<String, Any>**

```bash
cd /Users/dmitriimedakin/IdeaProjects/zenhelix/ci-workflows && grep -rn "Map<String, Any>" src/
```

Expected: No results. If any remain, they are stale and should be removed.

- [ ] **Step 2: Commit if any cleanup was needed**

Only if changes were made:

```bash
git add -A
git commit -m "refactor: remove remaining Map<String, Any> references"
```
