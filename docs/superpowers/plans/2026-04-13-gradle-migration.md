# Gradle Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace standalone `.main.kts` scripts with a Gradle project for full IDE support.

**Architecture:** Kotlin files in `src/main/kotlin/` with `shared/` and `workflows/` packages. `./gradlew run` generates all YAML into `.github/workflows/`. Byte-identical output to current `.main.kts` approach.

**Tech Stack:** Kotlin 2.3.20, Gradle 9.4.0, github-workflows-kt 3.7.0, application plugin

---

## File Structure

### New files to create:
| File | Responsibility |
|------|---------------|
| `build.gradle.kts` | Application plugin, dependencies |
| `settings.gradle.kts` | Project name |
| `gradle/wrapper/*` | Gradle wrapper (copy from orient project) |
| `gradlew`, `gradlew.bat` | Gradle wrapper scripts |
| `src/main/kotlin/shared/Constants.kt` | Version defaults, workflow/action path helpers |
| `src/main/kotlin/shared/Inputs.kt` | Input/secret builder functions, common input vals |
| `src/main/kotlin/shared/Actions.kt` | All custom Action classes |
| `src/main/kotlin/shared/PostProcessing.kt` | `cleanReusableWorkflowJobs()` |
| `src/main/kotlin/shared/DslHelpers.kt` | `conditionalSetupSteps()`, `noop()` |
| `src/main/kotlin/workflows/base/Check.kt` | `generateCheck()` |
| `src/main/kotlin/workflows/base/ConventionalCommitCheck.kt` | `generateConventionalCommitCheck()` |
| `src/main/kotlin/workflows/base/CreateTag.kt` | `generateCreateTag()` |
| `src/main/kotlin/workflows/base/ManualCreateTag.kt` | `generateManualCreateTag()` |
| `src/main/kotlin/workflows/base/Release.kt` | `generateRelease()` |
| `src/main/kotlin/workflows/base/Publish.kt` | `generatePublish()` |
| `src/main/kotlin/workflows/base/Labeler.kt` | `generateLabeler()` |
| `src/main/kotlin/workflows/adapters/AppCheck.kt` | `generateAppCheck()` |
| `src/main/kotlin/workflows/adapters/AppRelease.kt` | `generateAppRelease()` |
| `src/main/kotlin/workflows/adapters/AppDeploy.kt` | `generateAppDeploy()` |
| `src/main/kotlin/workflows/adapters/GradleCreateTag.kt` | `generateGradleCreateTag()` |
| `src/main/kotlin/workflows/adapters/GradleManualCreateTag.kt` | `generateGradleManualCreateTag()` |
| `src/main/kotlin/workflows/adapters/GradlePluginCheck.kt` | `generateGradlePluginCheck()` |
| `src/main/kotlin/workflows/adapters/GradlePluginRelease.kt` | `generateGradlePluginRelease()` |
| `src/main/kotlin/workflows/adapters/KotlinLibraryCheck.kt` | `generateKotlinLibraryCheck()` |
| `src/main/kotlin/workflows/adapters/KotlinLibraryRelease.kt` | `generateKotlinLibraryRelease()` |
| `src/main/kotlin/workflows/adapters/GoCreateTag.kt` | `generateGoCreateTag()` |
| `src/main/kotlin/workflows/adapters/GoManualCreateTag.kt` | `generateGoManualCreateTag()` |
| `src/main/kotlin/Generate.kt` | `main()` entry point |

### Files to delete:
- `.github/workflow-src/` — entire directory

### Files to modify:
- `.gitignore` — add `.gradle/`, `build/`
- `.github/workflows/verify-workflows.yml` — update CI to use `./gradlew run`

### Files unchanged:
- `.github/workflows/*.yml` — must be byte-identical after migration
- `.github/actions/`, `.github/labeler.yml`

---

### Task 1: Gradle project setup

**Files:**
- Create: `build.gradle.kts`
- Create: `settings.gradle.kts`
- Copy: `gradle/wrapper/*`, `gradlew`, `gradlew.bat`
- Modify: `.gitignore`

- [ ] **Step 1: Copy Gradle wrapper**

```bash
cp -r /Users/dmitriimedakin/IdeaProjects/orient/backend-api-copy/gradle ./gradle
cp /Users/dmitriimedakin/IdeaProjects/orient/backend-api-copy/gradlew ./gradlew
cp /Users/dmitriimedakin/IdeaProjects/orient/backend-api-copy/gradlew.bat ./gradlew.bat
chmod +x gradlew
```

- [ ] **Step 2: Create `settings.gradle.kts`**

```kotlin
rootProject.name = "ci-workflows"
```

- [ ] **Step 3: Create `build.gradle.kts`**

```kotlin
plugins {
    kotlin("jvm") version "2.3.20"
    application
}

application {
    mainClass.set("GenerateKt")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.typesafegithub:github-workflows-kt:3.7.0")
}
```

- [ ] **Step 4: Add Gradle dirs to `.gitignore`**

Append to `.gitignore`:
```
# Gradle
.gradle/
build/
```

- [ ] **Step 5: Verify Gradle builds**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL (no source files yet, just project setup).

- [ ] **Step 6: Commit**

```bash
git add build.gradle.kts settings.gradle.kts gradle/ gradlew gradlew.bat .gitignore
git commit -m "ci: add Gradle project for workflow generation"
```

---

### Task 2: Shared code — Constants, Inputs, Actions

**Files:**
- Create: `src/main/kotlin/shared/Constants.kt`
- Create: `src/main/kotlin/shared/Inputs.kt`
- Create: `src/main/kotlin/shared/Actions.kt`

- [ ] **Step 1: Create `src/main/kotlin/shared/Constants.kt`**

```kotlin
package shared

const val DEFAULT_JAVA_VERSION = "17"
const val DEFAULT_GO_VERSION = "1.22"
const val DEFAULT_PYTHON_VERSION = "3.12"
const val DEFAULT_RELEASE_BRANCHES = "main,[0-9]+\\.x"
const val DEFAULT_CHANGELOG_CONFIG = ".github/changelog-config.json"

const val WORKFLOW_REF = "v2"
const val WORKFLOW_BASE = "zenhelix/ci-workflows/.github/workflows"
const val ACTION_BASE = "zenhelix/ci-workflows/.github/actions"

fun reusableWorkflow(name: String) = "$WORKFLOW_BASE/$name@$WORKFLOW_REF"
fun localAction(name: String) = "$ACTION_BASE/$name@$WORKFLOW_REF"
```

- [ ] **Step 2: Create `src/main/kotlin/shared/Inputs.kt`**

```kotlin
package shared

fun workflowCallInput(
    description: String,
    type: String,
    required: Boolean,
    default: Any? = null,
): Map<String, Any?> = buildMap {
    put("description", description)
    put("type", type)
    put("required", required)
    if (default != null) put("default", default)
}

fun stringInput(description: String, required: Boolean = false, default: String? = null) =
    workflowCallInput(description, "string", required, default)

fun booleanInput(description: String, required: Boolean = false, default: Boolean? = null) =
    workflowCallInput(description, "boolean", required, default)

fun secretInput(description: String, required: Boolean = true) = mapOf(
    "description" to description,
    "required" to required,
)

val SETUP_ACTION_INPUT = "setup-action" to stringInput(
    description = "Setup action to use: gradle, go",
    required = true,
)

val SETUP_PARAMS_INPUT = "setup-params" to stringInput(
    description = "JSON object with setup parameters (e.g. {\"java-version\": \"21\"})",
    default = "{}",
)

val CHECK_COMMAND_INPUT = "check-command" to stringInput(
    description = "Command to run for checking",
    required = true,
)

val APP_ID_SECRET = "app-id" to secretInput("GitHub App ID for generating commit token")
val APP_PRIVATE_KEY_SECRET = "app-private-key" to secretInput("GitHub App private key for generating commit token")

val MAVEN_SONATYPE_SECRETS = mapOf(
    "MAVEN_SONATYPE_USERNAME" to secretInput("Maven Central (Sonatype) username"),
    "MAVEN_SONATYPE_TOKEN" to secretInput("Maven Central (Sonatype) token"),
    "MAVEN_SONATYPE_SIGNING_KEY_ID" to secretInput("GPG signing key ID"),
    "MAVEN_SONATYPE_SIGNING_PUB_KEY_ASCII_ARMORED" to secretInput("GPG signing public key (ASCII armored)"),
    "MAVEN_SONATYPE_SIGNING_KEY_ASCII_ARMORED" to secretInput("GPG signing private key (ASCII armored)"),
    "MAVEN_SONATYPE_SIGNING_PASSWORD" to secretInput("GPG signing key passphrase"),
)

val MAVEN_SONATYPE_SECRETS_PASSTHROUGH = MAVEN_SONATYPE_SECRETS.keys.associateWith { "\${{ secrets.$it }}" }

val GRADLE_PORTAL_SECRETS = mapOf(
    "GRADLE_PUBLISH_KEY" to secretInput("Gradle Plugin Portal publish key"),
    "GRADLE_PUBLISH_SECRET" to secretInput("Gradle Plugin Portal publish secret"),
)

val GRADLE_PORTAL_SECRETS_PASSTHROUGH = GRADLE_PORTAL_SECRETS.keys.associateWith { "\${{ secrets.$it }}" }
```

- [ ] **Step 3: Create `src/main/kotlin/shared/Actions.kt`**

```kotlin
package shared

import io.github.typesafegithub.workflows.domain.actions.Action

class SetupGradleAction(
    private val javaVersion: String,
    private val fetchDepth: String? = null,
) : Action<Action.Outputs>() {
    override val usesString = localAction("setup-gradle")
    override fun toYamlArguments() = linkedMapOf(
        "java-version" to javaVersion,
    ).apply {
        if (fetchDepth != null) put("fetch-depth", fetchDepth)
    }
    override fun buildOutputObject(stepId: String) = Outputs(stepId)
}

class SetupGoAction(
    private val goVersion: String,
    private val fetchDepth: String? = null,
) : Action<Action.Outputs>() {
    override val usesString = localAction("setup-go")
    override fun toYamlArguments() = linkedMapOf(
        "go-version" to goVersion,
    ).apply {
        if (fetchDepth != null) put("fetch-depth", fetchDepth)
    }
    override fun buildOutputObject(stepId: String) = Outputs(stepId)
}

class SetupPythonAction(
    private val pythonVersion: String,
    private val fetchDepth: String? = null,
) : Action<Action.Outputs>() {
    override val usesString = localAction("setup-python")
    override fun toYamlArguments() = linkedMapOf(
        "python-version" to pythonVersion,
    ).apply {
        if (fetchDepth != null) put("fetch-depth", fetchDepth)
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

    class CreateAppTokenOutputs(stepId: String) : Action.Outputs(stepId) {
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

class CheckoutAction(
    private val fetchDepth: String? = null,
) : Action<Action.Outputs>() {
    override val usesString = "actions/checkout@v6"
    override fun toYamlArguments() = linkedMapOf<String, String>().apply {
        if (fetchDepth != null) put("fetch-depth", fetchDepth)
    }
    override fun buildOutputObject(stepId: String) = Outputs(stepId)
}

class ReleaseChangelogBuilderAction(
    private val configuration: String,
    private val toTag: String,
) : Action<ReleaseChangelogBuilderAction.ChangelogOutputs>() {
    override val usesString = "mikepenz/release-changelog-builder-action@v6"
    override fun toYamlArguments() = linkedMapOf(
        "configuration" to configuration,
        "toTag" to toTag,
    )
    override fun buildOutputObject(stepId: String) = ChangelogOutputs(stepId)

    class ChangelogOutputs(stepId: String) : Action.Outputs(stepId) {
        val changelog: String get() = get("changelog")
    }
}

class GhReleaseAction(
    private val body: String,
    private val name: String,
    private val tagName: String,
    private val draft: String,
) : Action<Action.Outputs>() {
    override val usesString = "softprops/action-gh-release@v2"
    override fun toYamlArguments() = linkedMapOf(
        "body" to body,
        "name" to name,
        "tag_name" to tagName,
        "draft" to draft,
    )
    override fun buildOutputObject(stepId: String) = Outputs(stepId)
}

class LabelerAction(
    private val repoToken: String,
    private val configurationPath: String,
    private val syncLabels: String,
) : Action<Action.Outputs>() {
    override val usesString = "actions/labeler@v6"
    override fun toYamlArguments() = linkedMapOf(
        "repo-token" to repoToken,
        "configuration-path" to configurationPath,
        "sync-labels" to syncLabels,
    )
    override fun buildOutputObject(stepId: String) = Outputs(stepId)
}
```

- [ ] **Step 4: Verify compilation**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/shared/
git commit -m "ci: add shared Constants, Inputs, and Actions for Gradle workflow generator"
```

---

### Task 3: Shared code — DslHelpers and PostProcessing

**Files:**
- Create: `src/main/kotlin/shared/DslHelpers.kt`
- Create: `src/main/kotlin/shared/PostProcessing.kt`

- [ ] **Step 1: Create `src/main/kotlin/shared/DslHelpers.kt`**

```kotlin
package shared

import io.github.typesafegithub.workflows.dsl.JobBuilder

fun JobBuilder<*>.conditionalSetupSteps(fetchDepth: String? = null) {
    uses(
        name = "Setup Gradle",
        action = SetupGradleAction(
            javaVersion = "\${{ fromJson(inputs.setup-params).java-version || '17' }}",
            fetchDepth = fetchDepth,
        ),
        condition = "inputs.setup-action == 'gradle'",
    )
    uses(
        name = "Setup Go",
        action = SetupGoAction(
            goVersion = "\${{ fromJson(inputs.setup-params).go-version || '1.22' }}",
            fetchDepth = fetchDepth,
        ),
        condition = "inputs.setup-action == 'go'",
    )
    uses(
        name = "Setup Python",
        action = SetupPythonAction(
            pythonVersion = "\${{ fromJson(inputs.setup-params).python-version || '3.12' }}",
            fetchDepth = fetchDepth,
        ),
        condition = "inputs.setup-action == 'python'",
    )
}

fun JobBuilder<*>.noop() {
    run(name = "noop", command = "true")
}
```

- [ ] **Step 2: Create `src/main/kotlin/shared/PostProcessing.kt`**

```kotlin
package shared

import java.io.File

fun cleanReusableWorkflowJobs(targetFile: File) {
    val lines = targetFile.readLines()
    val output = mutableListOf<String>()

    val jobsLineIdx = lines.indexOfFirst { it.trimStart() == "jobs:" }

    var currentJobLines = mutableListOf<String>()
    var currentJobHasUses = false
    var inJobsSection = false
    var i = 0

    fun flushJob() {
        if (!currentJobHasUses) {
            output.addAll(currentJobLines)
        } else {
            var j = 0
            while (j < currentJobLines.size) {
                val buffered = currentJobLines[j]
                val bIndent = if (buffered.isBlank()) -1 else buffered.length - buffered.trimStart().length

                if (bIndent == 4 && buffered.trimStart().startsWith("runs-on:")) {
                    j++
                    continue
                }

                if (bIndent == 4 && buffered.trimStart().startsWith("steps:")) {
                    j++
                    while (j < currentJobLines.size) {
                        val stepLine = currentJobLines[j]
                        if (stepLine.isBlank()) { j++; continue }
                        val sIndent = stepLine.length - stepLine.trimStart().length
                        if (sIndent < 4) break
                        if (sIndent == 4 && !stepLine.trimStart().startsWith("-")) break
                        j++
                    }
                    continue
                }

                output.add(buffered)
                j++
            }
        }
        currentJobLines = mutableListOf()
        currentJobHasUses = false
    }

    while (i < lines.size) {
        val line = lines[i]
        val indent = if (line.isBlank()) -1 else line.length - line.trimStart().length

        if (!inJobsSection) {
            output.add(line)
            if (i == jobsLineIdx) inJobsSection = true
            i++
            continue
        }

        if (indent == 2 && line.trimEnd().endsWith(":")) {
            flushJob()
            currentJobLines.add(line)
            i++
            continue
        }

        if (indent == 0 && !line.isBlank()) {
            flushJob()
            output.add(line)
            inJobsSection = false
            i++
            continue
        }

        if (indent == 4 && line.trimStart().startsWith("uses:")) {
            currentJobHasUses = true
        }

        currentJobLines.add(line)
        i++
    }

    flushJob()

    targetFile.writeText(output.joinToString("\n") + "\n")
}
```

Note: Changed signature from `cleanReusableWorkflowJobs(targetFileName: String)` to `cleanReusableWorkflowJobs(targetFile: File)` — caller passes the full `File` object instead of a relative path.

- [ ] **Step 3: Verify compilation**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/shared/DslHelpers.kt src/main/kotlin/shared/PostProcessing.kt
git commit -m "ci: add DslHelpers and PostProcessing for Gradle workflow generator"
```

---

### Task 4: Base workflows + Generate.kt

**Files:**
- Create: `src/main/kotlin/workflows/base/Check.kt`
- Create: `src/main/kotlin/workflows/base/ConventionalCommitCheck.kt`
- Create: `src/main/kotlin/workflows/base/CreateTag.kt`
- Create: `src/main/kotlin/workflows/base/ManualCreateTag.kt`
- Create: `src/main/kotlin/workflows/base/Release.kt`
- Create: `src/main/kotlin/workflows/base/Publish.kt`
- Create: `src/main/kotlin/workflows/base/Labeler.kt`
- Create: `src/main/kotlin/Generate.kt`

- [ ] **Step 1: Create all 7 base workflow files**

Each file follows this pattern — take the existing `.main.kts` code and:
1. Add `package workflows.base`
2. Add proper imports for `shared.*`
3. Wrap in `fun generateXxx(outputDir: java.io.File)` function
4. Replace `sourceFile = __FILE__` with removing it (not needed in Gradle)
5. Replace `targetFileName = "xxx.yml"` with `targetFileName = outputDir.resolve("xxx.yml").toString()`
6. Remove `@file:Import`, `@file:Repository`, `@file:DependsOn`, `#!/usr/bin/env kotlin`

Create `src/main/kotlin/workflows/base/Check.kt`:
```kotlin
package workflows.base

import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowDispatch
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import shared.*
import java.io.File

fun generateCheck(outputDir: File) {
    workflow(
        name = "Check",
        on = listOf(WorkflowDispatch()),
        targetFileName = outputDir.resolve("check.yml").toString(),
        consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
        _customArguments = mapOf(
            "on" to mapOf(
                "workflow_call" to mapOf(
                    "inputs" to mapOf(
                        SETUP_ACTION_INPUT,
                        SETUP_PARAMS_INPUT,
                        CHECK_COMMAND_INPUT,
                    ),
                ),
            ),
            "permissions" to mapOf("contents" to "read"),
        ),
    ) {
        job(
            id = "build",
            name = "Build",
            runsOn = UbuntuLatest,
        ) {
            conditionalSetupSteps()
            run(
                name = "Run check",
                command = "\${{ inputs.check-command }}",
            )
        }
    }
}
```

Create `src/main/kotlin/workflows/base/ConventionalCommitCheck.kt`:
```kotlin
package workflows.base

import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowDispatch
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import shared.*
import java.io.File

fun generateConventionalCommitCheck(outputDir: File) {
    workflow(
        name = "Conventional Commit Check",
        on = listOf(WorkflowDispatch()),
        targetFileName = outputDir.resolve("conventional-commit-check.yml").toString(),
        consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
        _customArguments = mapOf(
            "on" to mapOf(
                "workflow_call" to mapOf(
                    "inputs" to mapOf(
                        "allowed-types" to stringInput(
                            description = "Comma-separated list of allowed commit types",
                            default = "feat,fix,refactor,docs,test,chore,perf,ci",
                        ),
                    ),
                ),
            ),
        ),
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
                    "ALLOWED_TYPES" to "\${{ inputs.allowed-types }}",
                ),
            )
        }
    }
}
```

Create `src/main/kotlin/workflows/base/CreateTag.kt`, `ManualCreateTag.kt`, `Release.kt`, `Publish.kt`, `Labeler.kt` — same pattern. Copy the body from the corresponding `.main.kts` file, wrap in `fun generateXxx(outputDir: File)`, add `package workflows.base`, add imports, replace `targetFileName`.

**IMPORTANT**: Each file must be an exact transliteration of its `.main.kts` counterpart. The ONLY changes are:
- `package workflows.base` header
- `import shared.*` and `import java.io.File`
- Standard library imports (`io.github.typesafegithub...`)
- Remove `sourceFile = __FILE__`
- `targetFileName = outputDir.resolve("xxx.yml").toString()`
- Wrap body in `fun generateXxx(outputDir: File) { ... }`

- [ ] **Step 2: Create `src/main/kotlin/Generate.kt`**

```kotlin
import workflows.base.*
import workflows.adapters.*
import java.io.File

fun main() {
    val outputDir = File(".github/workflows")

    // Base workflows
    generateCheck(outputDir)
    generateConventionalCommitCheck(outputDir)
    generateCreateTag(outputDir)
    generateManualCreateTag(outputDir)
    generateRelease(outputDir)
    generatePublish(outputDir)
    generateLabeler(outputDir)

    // Adapters (added in Task 5)
}
```

- [ ] **Step 3: Verify compilation**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL (adapter imports will warn but compile since the package exists even if empty).

- [ ] **Step 4: Run and compare base workflows**

```bash
./gradlew run
```

Then compare generated YAML with current:
```bash
diff <(git show HEAD:.github/workflows/check.yml) .github/workflows/check.yml
diff <(git show HEAD:.github/workflows/conventional-commit-check.yml) .github/workflows/conventional-commit-check.yml
diff <(git show HEAD:.github/workflows/create-tag.yml) .github/workflows/create-tag.yml
diff <(git show HEAD:.github/workflows/manual-create-tag.yml) .github/workflows/manual-create-tag.yml
diff <(git show HEAD:.github/workflows/release.yml) .github/workflows/release.yml
diff <(git show HEAD:.github/workflows/publish.yml) .github/workflows/publish.yml
diff <(git show HEAD:.github/workflows/labeler.yml) .github/workflows/labeler.yml
```

Expected: No differences (byte-identical). If there are diffs, fix the Kotlin source and re-run.

**Note**: The `sourceFile` parameter may affect the generated header comment. If the output has a different header comment path, that needs to be fixed. One option: remove `sourceFile` parameter entirely (it's only used for consistency check which is disabled). If the library requires it, pass a `__FILE` equivalent or adjust.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/
git commit -m "ci: add base workflow generators and Generate.kt entry point"
```

---

### Task 5: Adapter workflows

**Files:**
- Create: `src/main/kotlin/workflows/adapters/AppCheck.kt`
- Create: `src/main/kotlin/workflows/adapters/AppRelease.kt`
- Create: `src/main/kotlin/workflows/adapters/AppDeploy.kt`
- Create: `src/main/kotlin/workflows/adapters/GradleCreateTag.kt`
- Create: `src/main/kotlin/workflows/adapters/GradleManualCreateTag.kt`
- Create: `src/main/kotlin/workflows/adapters/GradlePluginCheck.kt`
- Create: `src/main/kotlin/workflows/adapters/GradlePluginRelease.kt`
- Create: `src/main/kotlin/workflows/adapters/KotlinLibraryCheck.kt`
- Create: `src/main/kotlin/workflows/adapters/KotlinLibraryRelease.kt`
- Create: `src/main/kotlin/workflows/adapters/GoCreateTag.kt`
- Create: `src/main/kotlin/workflows/adapters/GoManualCreateTag.kt`
- Modify: `src/main/kotlin/Generate.kt`

- [ ] **Step 1: Create all 11 adapter workflow files**

Same pattern as base workflows. Each adapter that uses `cleanReusableWorkflowJobs` needs to be updated:

Old (`.main.kts`):
```kotlin
cleanReusableWorkflowJobs(targetFile)
```

New (Gradle):
```kotlin
cleanReusableWorkflowJobs(outputDir.resolve("xxx.yml"))
```

Example `src/main/kotlin/workflows/adapters/AppCheck.kt`:
```kotlin
package workflows.adapters

import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowDispatch
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import shared.*
import java.io.File

fun generateAppCheck(outputDir: File) {
    val targetFile = outputDir.resolve("app-check.yml")

    workflow(
        name = "Application Check",
        on = listOf(WorkflowDispatch()),
        targetFileName = targetFile.toString(),
        consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
        _customArguments = mapOf(
            "on" to mapOf(
                "workflow_call" to mapOf(
                    "inputs" to mapOf(
                        "java-version" to stringInput(
                            description = "JDK version to use",
                            default = DEFAULT_JAVA_VERSION,
                        ),
                        "java-versions" to stringInput(
                            description = "JSON array of JDK versions for matrix build (overrides java-version)",
                            default = "",
                        ),
                        "gradle-command" to stringInput(
                            description = "Gradle check command",
                            default = "./gradlew check",
                        ),
                    ),
                ),
            ),
        ),
    ) {
        job(
            id = "conventional-commit",
            runsOn = UbuntuLatest,
            _customArguments = mapOf(
                "uses" to reusableWorkflow("conventional-commit-check.yml"),
            ),
        ) {
            noop()
        }

        job(
            id = "check",
            runsOn = UbuntuLatest,
            _customArguments = mapOf(
                "strategy" to mapOf(
                    "matrix" to mapOf(
                        "java-version" to "\${{ fromJson(inputs.java-versions || format('[\"" + "{0}" + "\"]', inputs.java-version)) }}",
                    ),
                ),
                "uses" to reusableWorkflow("check.yml"),
                "with" to mapOf(
                    "setup-action" to "gradle",
                    "setup-params" to "{\"java-version\": \"\${{ matrix.java-version }}\"}",
                    "check-command" to "\${{ inputs.gradle-command }}",
                ),
            ),
        ) {
            noop()
        }
    }

    cleanReusableWorkflowJobs(targetFile)
}
```

Create all other adapters following the same pattern. Each is a direct transliteration from its `.main.kts` counterpart.

- [ ] **Step 2: Update `Generate.kt` to call adapters**

Add to `main()`:
```kotlin
    // Adapters
    generateAppCheck(outputDir)
    generateAppRelease(outputDir)
    generateAppDeploy(outputDir)
    generateGradleCreateTag(outputDir)
    generateGradleManualCreateTag(outputDir)
    generateGradlePluginCheck(outputDir)
    generateGradlePluginRelease(outputDir)
    generateKotlinLibraryCheck(outputDir)
    generateKotlinLibraryRelease(outputDir)
    generateGoCreateTag(outputDir)
    generateGoManualCreateTag(outputDir)
```

- [ ] **Step 3: Run and compare ALL workflows**

```bash
./gradlew run
```

Then for each of the 18 workflow files:
```bash
for f in check conventional-commit-check create-tag manual-create-tag release publish labeler app-check app-release app-deploy gradle-create-tag gradle-manual-create-tag gradle-plugin-check gradle-plugin-release kotlin-library-check kotlin-library-release go-create-tag go-manual-create-tag; do
    echo "=== $f.yml ==="
    diff <(git show HEAD:.github/workflows/$f.yml) .github/workflows/$f.yml || echo "DIFF FOUND"
done
```

Expected: No differences for any file. Fix any diffs before proceeding.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/
git commit -m "ci: add adapter workflow generators"
```

---

### Task 6: Delete old workflow-src, update CI

**Files:**
- Delete: `.github/workflow-src/` (entire directory)
- Modify: `.github/workflows/verify-workflows.yml`

- [ ] **Step 1: Delete `.github/workflow-src/`**

```bash
rm -rf .github/workflow-src/
```

- [ ] **Step 2: Update `verify-workflows.yml`**

Replace the current content with:

```yaml
name: 'Verify Workflows'

on:
  pull_request:
    paths:
      - 'src/**'
      - 'build.gradle.kts'
      - '.github/workflows/**'
  push:
    branches: ['main']
    paths:
      - 'src/**'
      - 'build.gradle.kts'
      - '.github/workflows/**'

permissions:
  contents: 'read'

jobs:
  verify:
    name: 'Verify generated workflows are up-to-date'
    runs-on: 'ubuntu-latest'
    steps:
      - name: 'Check out'
        uses: 'actions/checkout@v6'

      - name: 'Set up JDK'
        uses: 'actions/setup-java@v5'
        with:
          distribution: 'temurin'
          java-version: '17'

      - name: 'Setup Gradle'
        uses: 'gradle/actions/setup-gradle@v5'

      - name: 'Generate workflows'
        run: ./gradlew run

      - name: 'Check for differences'
        run: |
          if ! git diff --exit-code .github/workflows/; then
            echo "::error::Generated workflow YAML files are out of date. Run ./gradlew run and commit."
            exit 1
          fi
          echo "All generated workflows are up-to-date."
```

- [ ] **Step 3: Final verification**

```bash
./gradlew run
git diff --exit-code .github/workflows/
```

Expected: Exit code 0, no diffs.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "ci: remove workflow-src, update verify-workflows to use Gradle"
```
