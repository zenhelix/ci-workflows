# Typed Reusable Workflow DSL Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace untyped `_customArguments` with a type-safe DSL for workflow_call triggers, permissions, and reusable workflow job calls.

**Architecture:** Define `ReusableWorkflow` base class with typed input/secret definitions. Each base workflow becomes a singleton object. Adapter workflows use a `reusableWorkflowJob()` DSL builder that generates `_customArguments` under the hood. Workflow-level `_customArguments` for `on` and `permissions` are replaced with native `github-workflows-kt` typed parameters.

**Tech Stack:** Kotlin 2.3.20, github-workflows-kt 3.7.0

---

### Task 1: Create ReusableWorkflow base class and WorkflowInput/WorkflowSecret

**Files:**
- Create: `src/main/kotlin/shared/dsl/ReusableWorkflow.kt`

- [ ] **Step 1: Create the file with all three classes**

```kotlin
package shared.dsl

import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import shared.reusableWorkflow

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

    protected fun booleanInput(
        name: String,
        description: String,
        required: Boolean = false,
        default: Boolean? = null,
    ): WorkflowInput {
        _inputs[name] = WorkflowCall.Input(description, required, WorkflowCall.Type.Boolean, default?.toString())
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
}

class WorkflowInput(val name: String)

class WorkflowSecret(val name: String)
```

- [ ] **Step 2: Verify it compiles**

Run: `cd /Users/dmitriimedakin/IdeaProjects/zenhelix/ci-workflows && ./gradlew compileKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/shared/dsl/ReusableWorkflow.kt
git commit -m "feat: add ReusableWorkflow base class with typed inputs/secrets"
```

---

### Task 2: Create ReusableWorkflowJobBuilder and reusableWorkflowJob()

**Files:**
- Create: `src/main/kotlin/shared/dsl/ReusableWorkflowJobBuilder.kt`

- [ ] **Step 1: Create the builder and extension function**

```kotlin
package shared.dsl

import io.github.typesafegithub.workflows.domain.RunnerType
import io.github.typesafegithub.workflows.dsl.WorkflowBuilder
import shared.noop

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

- [ ] **Step 2: Verify it compiles**

Run: `cd /Users/dmitriimedakin/IdeaProjects/zenhelix/ci-workflows && ./gradlew compileKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/shared/dsl/ReusableWorkflowJobBuilder.kt
git commit -m "feat: add ReusableWorkflowJobBuilder and reusableWorkflowJob() DSL"
```

---

### Task 3: Create workflow object definitions for all 7 base workflows

**Files:**
- Create: `src/main/kotlin/shared/dsl/Workflows.kt`

Note: The `Publish` workflow uses combined secrets from `MAVEN_SONATYPE_SECRETS + GRADLE_PORTAL_SECRETS` with `required = false`. Since `PublishWorkflow` is the single source of truth, define all secrets directly there with `required = false`. The passthrough constants in `Inputs.kt` will be derived from the object's `.secrets.keys` in a later task.

- [ ] **Step 1: Create the file with all workflow objects**

```kotlin
package shared.dsl

import shared.DEFAULT_CHANGELOG_CONFIG
import shared.DEFAULT_RELEASE_BRANCHES

object CheckWorkflow : ReusableWorkflow("check.yml") {
    val setupAction = input("setup-action",
        description = "Setup action to use: gradle, go",
        required = true)
    val setupParams = input("setup-params",
        description = "JSON object with setup parameters (e.g. {\"java-version\": \"21\"})",
        default = "{}")
    val checkCommand = input("check-command",
        description = "Command to run for checking",
        required = true)
}

object ConventionalCommitCheckWorkflow : ReusableWorkflow("conventional-commit-check.yml") {
    val allowedTypes = input("allowed-types",
        description = "Comma-separated list of allowed commit types",
        default = "feat,fix,refactor,docs,test,chore,perf,ci")
}

object CreateTagWorkflow : ReusableWorkflow("create-tag.yml") {
    val setupAction = input("setup-action",
        description = "Setup action to use: gradle, go",
        required = true)
    val setupParams = input("setup-params",
        description = "JSON object with setup parameters (e.g. {\"java-version\": \"21\"})",
        default = "{}")
    val checkCommand = input("check-command",
        description = "Validation command to run before tagging",
        required = true)
    val defaultBump = input("default-bump",
        description = "Default version bump type (major, minor, patch)",
        default = "patch")
    val tagPrefix = input("tag-prefix",
        description = "Prefix for the tag (e.g. v)",
        default = "")
    val releaseBranches = input("release-branches",
        description = "Comma-separated branch patterns for releases",
        default = DEFAULT_RELEASE_BRANCHES)
    val appId = secret("app-id",
        description = "GitHub App ID for generating commit token")
    val appPrivateKey = secret("app-private-key",
        description = "GitHub App private key for generating commit token")
}

object ManualCreateTagWorkflow : ReusableWorkflow("manual-create-tag.yml") {
    val tagVersion = input("tag-version",
        description = "Version to tag (e.g. 1.2.3)",
        required = true)
    val tagPrefix = input("tag-prefix",
        description = "Prefix for the tag (e.g. v)",
        default = "")
    val setupAction = input("setup-action",
        description = "Setup action to use: gradle, go, python",
        required = true)
    val setupParams = input("setup-params",
        description = "JSON object with setup parameters (e.g. {\"java-version\": \"21\"})",
        default = "{}")
    val checkCommand = input("check-command",
        description = "Validation command to run before tagging",
        required = true)
    val appId = secret("app-id",
        description = "GitHub App ID for generating commit token")
    val appPrivateKey = secret("app-private-key",
        description = "GitHub App private key for generating commit token")
}

object ReleaseWorkflow : ReusableWorkflow("release.yml") {
    val changelogConfig = input("changelog-config",
        description = "Path to changelog configuration file",
        default = DEFAULT_CHANGELOG_CONFIG)
    val draft = booleanInput("draft",
        description = "Create release as draft",
        default = false)
}

object PublishWorkflow : ReusableWorkflow("publish.yml") {
    val setupAction = input("setup-action",
        description = "Setup action to use: gradle, go",
        required = true)
    val setupParams = input("setup-params",
        description = "JSON object with setup parameters (e.g. {\"java-version\": \"21\"})",
        default = "{}")
    val publishCommand = input("publish-command",
        description = "Command to run for publishing",
        required = true)
    val mavenSonatypeUsername = secret("MAVEN_SONATYPE_USERNAME",
        description = "Maven Central (Sonatype) username", required = false)
    val mavenSonatypeToken = secret("MAVEN_SONATYPE_TOKEN",
        description = "Maven Central (Sonatype) token", required = false)
    val mavenSonatypeSigningKeyId = secret("MAVEN_SONATYPE_SIGNING_KEY_ID",
        description = "GPG signing key ID", required = false)
    val mavenSonatypeSigningPubKeyAsciiArmored = secret("MAVEN_SONATYPE_SIGNING_PUB_KEY_ASCII_ARMORED",
        description = "GPG signing public key (ASCII armored)", required = false)
    val mavenSonatypeSigningKeyAsciiArmored = secret("MAVEN_SONATYPE_SIGNING_KEY_ASCII_ARMORED",
        description = "GPG signing private key (ASCII armored)", required = false)
    val mavenSonatypeSigningPassword = secret("MAVEN_SONATYPE_SIGNING_PASSWORD",
        description = "GPG signing key passphrase", required = false)
    val gradlePublishKey = secret("GRADLE_PUBLISH_KEY",
        description = "Gradle Plugin Portal publish key", required = false)
    val gradlePublishSecret = secret("GRADLE_PUBLISH_SECRET",
        description = "Gradle Plugin Portal publish secret", required = false)
}

object LabelerWorkflow : ReusableWorkflow("labeler.yml") {
    val configPath = input("config-path",
        description = "Path to labeler configuration file",
        default = ".github/labeler.yml")
}
```

- [ ] **Step 2: Verify it compiles**

Run: `cd /Users/dmitriimedakin/IdeaProjects/zenhelix/ci-workflows && ./gradlew compileKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/shared/dsl/Workflows.kt
git commit -m "feat: add typed ReusableWorkflow object definitions for all base workflows"
```

---

### Task 4: Refactor Inputs.kt — remove untyped helpers, keep passthroughs

**Files:**
- Modify: `src/main/kotlin/shared/Inputs.kt`

The typed workflow objects from Task 3 now own the input/secret definitions. `Inputs.kt` keeps only the passthrough constants needed by adapter workflows and the secret maps needed by adapters that define their own `WorkflowCall` triggers with typed secrets inline.

- [ ] **Step 1: Replace Inputs.kt with passthrough-only content**

Replace the entire file with:

```kotlin
package shared

import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall

val MAVEN_SONATYPE_SECRETS = mapOf(
    "MAVEN_SONATYPE_USERNAME" to WorkflowCall.Secret("Maven Central (Sonatype) username", true),
    "MAVEN_SONATYPE_TOKEN" to WorkflowCall.Secret("Maven Central (Sonatype) token", true),
    "MAVEN_SONATYPE_SIGNING_KEY_ID" to WorkflowCall.Secret("GPG signing key ID", true),
    "MAVEN_SONATYPE_SIGNING_PUB_KEY_ASCII_ARMORED" to WorkflowCall.Secret("GPG signing public key (ASCII armored)", true),
    "MAVEN_SONATYPE_SIGNING_KEY_ASCII_ARMORED" to WorkflowCall.Secret("GPG signing private key (ASCII armored)", true),
    "MAVEN_SONATYPE_SIGNING_PASSWORD" to WorkflowCall.Secret("GPG signing key passphrase", true),
)

val MAVEN_SONATYPE_SECRETS_PASSTHROUGH = MAVEN_SONATYPE_SECRETS.keys.associateWith { "\${{ secrets.$it }}" }

val GRADLE_PORTAL_SECRETS = mapOf(
    "GRADLE_PUBLISH_KEY" to WorkflowCall.Secret("Gradle Plugin Portal publish key", true),
    "GRADLE_PUBLISH_SECRET" to WorkflowCall.Secret("Gradle Plugin Portal publish secret", true),
)

val GRADLE_PORTAL_SECRETS_PASSTHROUGH = GRADLE_PORTAL_SECRETS.keys.associateWith { "\${{ secrets.$it }}" }

val APP_SECRETS_PASSTHROUGH = mapOf(
    "app-id" to "\${{ secrets.app-id }}",
    "app-private-key" to "\${{ secrets.app-private-key }}",
)

val APP_SECRETS = mapOf(
    "app-id" to WorkflowCall.Secret("GitHub App ID for generating commit token", true),
    "app-private-key" to WorkflowCall.Secret("GitHub App private key for generating commit token", true),
)

fun Map<String, WorkflowCall.Secret>.withRequired(required: Boolean): Map<String, WorkflowCall.Secret> =
    mapValues { (_, v) -> WorkflowCall.Secret(v.description, required) }
```

Note: Compilation will fail at this point because base workflows still import removed symbols. This is expected — they will be migrated in subsequent tasks.

- [ ] **Step 2: Commit**

```bash
git add src/main/kotlin/shared/Inputs.kt
git commit -m "refactor: replace untyped input/secret helpers with typed WorkflowCall definitions"
```

---

### Task 5: Migrate base workflows to typed WorkflowCall + permissions

**Files:**
- Modify: `src/main/kotlin/workflows/base/Check.kt`
- Modify: `src/main/kotlin/workflows/base/ConventionalCommitCheck.kt`
- Modify: `src/main/kotlin/workflows/base/CreateTag.kt`
- Modify: `src/main/kotlin/workflows/base/ManualCreateTag.kt`
- Modify: `src/main/kotlin/workflows/base/Release.kt`
- Modify: `src/main/kotlin/workflows/base/Publish.kt`
- Modify: `src/main/kotlin/workflows/base/Labeler.kt`

- [ ] **Step 1: Replace Check.kt**

Replace entire file with:

```kotlin
package workflows.base

import io.github.typesafegithub.workflows.domain.Mode
import io.github.typesafegithub.workflows.domain.Permission
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.domain.triggers.WorkflowDispatch
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import shared.conditionalSetupSteps
import shared.dsl.CheckWorkflow
import java.io.File

fun generateCheck(outputDir: File) {
    workflow(
        name = "Check",
        on = listOf(
            WorkflowDispatch(),
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
                command = "\${{ inputs.check-command }}",
            )
        }
    }
}
```

- [ ] **Step 2: Replace ConventionalCommitCheck.kt**

Replace entire file with:

```kotlin
package workflows.base

import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.domain.triggers.WorkflowDispatch
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import shared.dsl.ConventionalCommitCheckWorkflow
import java.io.File

fun generateConventionalCommitCheck(outputDir: File) {
    workflow(
        name = "Conventional Commit Check",
        on = listOf(
            WorkflowDispatch(),
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
                    "ALLOWED_TYPES" to "\${{ inputs.allowed-types }}",
                ),
            )
        }
    }
}
```

- [ ] **Step 3: Replace CreateTag.kt**

Replace entire file with:

```kotlin
package workflows.base

import io.github.typesafegithub.workflows.domain.Mode
import io.github.typesafegithub.workflows.domain.Permission
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.domain.triggers.WorkflowDispatch
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import shared.CreateAppTokenAction
import shared.GithubTagAction
import shared.conditionalSetupSteps
import shared.dsl.CreateTagWorkflow
import java.io.File

fun generateCreateTag(outputDir: File) {
    workflow(
        name = "Create Tag",
        on = listOf(
            WorkflowDispatch(),
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
                command = "\${{ inputs.check-command }}",
            )
            uses(
                name = "Generate App Token",
                action = CreateAppTokenAction(
                    appId = "\${{ secrets.app-id }}",
                    appPrivateKey = "\${{ secrets.app-private-key }}",
                ),
                id = "app-token",
            )
            uses(
                name = "Bump version and push tag",
                action = GithubTagAction(
                    githubToken = "\${{ steps.app-token.outputs.token }}",
                    defaultBump = "\${{ inputs.default-bump }}",
                    tagPrefix = "\${{ inputs.tag-prefix }}",
                    releaseBranches = "\${{ inputs.release-branches }}",
                ),
            )
        }
    }
}
```

- [ ] **Step 4: Replace ManualCreateTag.kt**

Replace entire file with:

```kotlin
package workflows.base

import io.github.typesafegithub.workflows.domain.Mode
import io.github.typesafegithub.workflows.domain.Permission
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.domain.triggers.WorkflowDispatch
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import shared.CreateAppTokenAction
import shared.conditionalSetupSteps
import shared.dsl.ManualCreateTagWorkflow
import java.io.File

fun generateManualCreateTag(outputDir: File) {
    workflow(
        name = "Manual Create Tag",
        on = listOf(
            WorkflowDispatch(),
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
                command = "\${{ inputs.check-command }}",
            )
            uses(
                name = "Generate App Token",
                action = CreateAppTokenAction(
                    appId = "\${{ secrets.app-id }}",
                    appPrivateKey = "\${{ secrets.app-private-key }}",
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

- [ ] **Step 5: Replace Release.kt**

Replace entire file with:

```kotlin
package workflows.base

import io.github.typesafegithub.workflows.domain.Mode
import io.github.typesafegithub.workflows.domain.Permission
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.domain.triggers.WorkflowDispatch
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import shared.CheckoutAction
import shared.GhReleaseAction
import shared.ReleaseChangelogBuilderAction
import shared.dsl.ReleaseWorkflow
import java.io.File

fun generateRelease(outputDir: File) {
    workflow(
        name = "Release",
        on = listOf(
            WorkflowDispatch(),
            WorkflowCall(inputs = ReleaseWorkflow.inputs),
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
                action = CheckoutAction(fetchDepth = "0"),
            )
            uses(
                name = "Build Changelog",
                action = ReleaseChangelogBuilderAction(
                    configuration = "\${{ inputs.changelog-config }}",
                    toTag = "\${{ github.ref_name }}",
                ),
                id = "changelog",
                env = linkedMapOf(
                    "GITHUB_TOKEN" to "\${{ secrets.GITHUB_TOKEN }}",
                ),
            )
            uses(
                name = "Create Release",
                action = GhReleaseAction(
                    body = "\${{ steps.changelog.outputs.changelog }}",
                    name = "\${{ github.ref_name }}",
                    tagName = "\${{ github.ref_name }}",
                    draft = "\${{ inputs.draft }}",
                ),
                env = linkedMapOf(
                    "GITHUB_TOKEN" to "\${{ secrets.GITHUB_TOKEN }}",
                ),
            )
        }
    }
}
```

- [ ] **Step 6: Replace Publish.kt**

Replace entire file with:

```kotlin
package workflows.base

import io.github.typesafegithub.workflows.domain.Mode
import io.github.typesafegithub.workflows.domain.Permission
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.domain.triggers.WorkflowDispatch
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import shared.conditionalSetupSteps
import shared.dsl.PublishWorkflow
import java.io.File

fun generatePublish(outputDir: File) {
    workflow(
        name = "Publish",
        on = listOf(
            WorkflowDispatch(),
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
                command = "\${{ inputs.publish-command }}",
                env = linkedMapOf(
                    "GRADLE_PUBLISH_KEY" to "\${{ secrets.GRADLE_PUBLISH_KEY }}",
                    "GRADLE_PUBLISH_SECRET" to "\${{ secrets.GRADLE_PUBLISH_SECRET }}",
                    "ORG_GRADLE_PROJECT_signingKeyId" to "\${{ secrets.MAVEN_SONATYPE_SIGNING_KEY_ID }}",
                    "ORG_GRADLE_PROJECT_signingPublicKey" to "\${{ secrets.MAVEN_SONATYPE_SIGNING_PUB_KEY_ASCII_ARMORED }}",
                    "ORG_GRADLE_PROJECT_signingKey" to "\${{ secrets.MAVEN_SONATYPE_SIGNING_KEY_ASCII_ARMORED }}",
                    "ORG_GRADLE_PROJECT_signingPassword" to "\${{ secrets.MAVEN_SONATYPE_SIGNING_PASSWORD }}",
                    "MAVEN_SONATYPE_USERNAME" to "\${{ secrets.MAVEN_SONATYPE_USERNAME }}",
                    "MAVEN_SONATYPE_TOKEN" to "\${{ secrets.MAVEN_SONATYPE_TOKEN }}",
                ),
            )
        }
    }
}
```

- [ ] **Step 7: Replace Labeler.kt**

Replace entire file with:

```kotlin
package workflows.base

import io.github.typesafegithub.workflows.domain.Mode
import io.github.typesafegithub.workflows.domain.Permission
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.domain.triggers.WorkflowDispatch
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import shared.LabelerAction
import shared.dsl.LabelerWorkflow
import java.io.File

fun generateLabeler(outputDir: File) {
    workflow(
        name = "PR Labeler",
        on = listOf(
            WorkflowDispatch(),
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
                action = LabelerAction(
                    repoToken = "\${{ secrets.GITHUB_TOKEN }}",
                    configurationPath = "\${{ inputs.config-path }}",
                    syncLabels = "true",
                ),
            )
        }
    }
}
```

- [ ] **Step 8: Verify it compiles**

Run: `cd /Users/dmitriimedakin/IdeaProjects/zenhelix/ci-workflows && ./gradlew compileKotlin 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL (base workflows compile, adapter workflows may still have errors from removed `stringInput`/`booleanInput` — these are fixed in the next task)

- [ ] **Step 9: Commit**

```bash
git add src/main/kotlin/workflows/base/ src/main/kotlin/shared/Inputs.kt
git commit -m "refactor: migrate base workflows to typed WorkflowCall triggers and permissions"
```

---

### Task 6: Migrate adapter workflows — Check variants (AppCheck, GradlePluginCheck, KotlinLibraryCheck)

**Files:**
- Modify: `src/main/kotlin/workflows/adapters/AppCheck.kt`
- Modify: `src/main/kotlin/workflows/adapters/GradlePluginCheck.kt`
- Modify: `src/main/kotlin/workflows/adapters/KotlinLibraryCheck.kt`

All three check adapters share an identical structure: a `conventional-commit` reusable job + a `check` reusable job with matrix strategy. They differ only in workflow name/file.

- [ ] **Step 1: Replace AppCheck.kt**

Replace entire file with:

```kotlin
package workflows.adapters

import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.domain.triggers.WorkflowDispatch
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import shared.DEFAULT_JAVA_VERSION
import shared.JAVA_VERSION_MATRIX_EXPR
import shared.cleanReusableWorkflowJobs
import shared.dsl.CheckWorkflow
import shared.dsl.ConventionalCommitCheckWorkflow
import shared.dsl.reusableWorkflowJob
import java.io.File

fun generateAppCheck(outputDir: File) {
    val targetFile = "app-check.yml"

    workflow(
        name = "Application Check",
        on = listOf(
            WorkflowDispatch(),
            WorkflowCall(inputs = mapOf(
                "java-version" to WorkflowCall.Input("JDK version to use", false, WorkflowCall.Type.String, DEFAULT_JAVA_VERSION),
                "java-versions" to WorkflowCall.Input("JSON array of JDK versions for matrix build (overrides java-version)", false, WorkflowCall.Type.String, ""),
                "gradle-command" to WorkflowCall.Input("Gradle check command", false, WorkflowCall.Type.String, "./gradlew check"),
            )),
        ),
        sourceFile = File(".github/workflow-src/app-check.main.kts"),
        targetFileName = targetFile,
        consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
    ) {
        reusableWorkflowJob(
            id = "conventional-commit",
            uses = ConventionalCommitCheckWorkflow,
        )

        reusableWorkflowJob(id = "check", uses = CheckWorkflow) {
            strategy(mapOf("java-version" to JAVA_VERSION_MATRIX_EXPR))
            CheckWorkflow.setupAction("gradle")
            CheckWorkflow.setupParams("{\"java-version\": \"\${{ matrix.java-version }}\"}")
            CheckWorkflow.checkCommand("\${{ inputs.gradle-command }}")
        }
    }

    cleanReusableWorkflowJobs(File(outputDir, targetFile))
}
```

- [ ] **Step 2: Replace GradlePluginCheck.kt**

Replace entire file with:

```kotlin
package workflows.adapters

import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.domain.triggers.WorkflowDispatch
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import shared.DEFAULT_JAVA_VERSION
import shared.JAVA_VERSION_MATRIX_EXPR
import shared.cleanReusableWorkflowJobs
import shared.dsl.CheckWorkflow
import shared.dsl.ConventionalCommitCheckWorkflow
import shared.dsl.reusableWorkflowJob
import java.io.File

fun generateGradlePluginCheck(outputDir: File) {
    val targetFile = "gradle-plugin-check.yml"

    workflow(
        name = "Gradle Plugin Check",
        on = listOf(
            WorkflowDispatch(),
            WorkflowCall(inputs = mapOf(
                "java-version" to WorkflowCall.Input("JDK version to use", false, WorkflowCall.Type.String, DEFAULT_JAVA_VERSION),
                "java-versions" to WorkflowCall.Input("JSON array of JDK versions for matrix build (overrides java-version)", false, WorkflowCall.Type.String, ""),
                "gradle-command" to WorkflowCall.Input("Gradle check command", false, WorkflowCall.Type.String, "./gradlew check"),
            )),
        ),
        sourceFile = File(".github/workflow-src/gradle-plugin-check.main.kts"),
        targetFileName = targetFile,
        consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
    ) {
        reusableWorkflowJob(
            id = "conventional-commit",
            uses = ConventionalCommitCheckWorkflow,
        )

        reusableWorkflowJob(id = "check", uses = CheckWorkflow) {
            strategy(mapOf("java-version" to JAVA_VERSION_MATRIX_EXPR))
            CheckWorkflow.setupAction("gradle")
            CheckWorkflow.setupParams("{\"java-version\": \"\${{ matrix.java-version }}\"}")
            CheckWorkflow.checkCommand("\${{ inputs.gradle-command }}")
        }
    }

    cleanReusableWorkflowJobs(File(outputDir, targetFile))
}
```

- [ ] **Step 3: Replace KotlinLibraryCheck.kt**

Replace entire file with:

```kotlin
package workflows.adapters

import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.domain.triggers.WorkflowDispatch
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import shared.DEFAULT_JAVA_VERSION
import shared.JAVA_VERSION_MATRIX_EXPR
import shared.cleanReusableWorkflowJobs
import shared.dsl.CheckWorkflow
import shared.dsl.ConventionalCommitCheckWorkflow
import shared.dsl.reusableWorkflowJob
import java.io.File

fun generateKotlinLibraryCheck(outputDir: File) {
    val targetFile = "kotlin-library-check.yml"

    workflow(
        name = "Kotlin Library Check",
        on = listOf(
            WorkflowDispatch(),
            WorkflowCall(inputs = mapOf(
                "java-version" to WorkflowCall.Input("JDK version to use", false, WorkflowCall.Type.String, DEFAULT_JAVA_VERSION),
                "java-versions" to WorkflowCall.Input("JSON array of JDK versions for matrix build (overrides java-version)", false, WorkflowCall.Type.String, ""),
                "gradle-command" to WorkflowCall.Input("Gradle check command", false, WorkflowCall.Type.String, "./gradlew check"),
            )),
        ),
        sourceFile = File(".github/workflow-src/kotlin-library-check.main.kts"),
        targetFileName = targetFile,
        consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
    ) {
        reusableWorkflowJob(
            id = "conventional-commit",
            uses = ConventionalCommitCheckWorkflow,
        )

        reusableWorkflowJob(id = "check", uses = CheckWorkflow) {
            strategy(mapOf("java-version" to JAVA_VERSION_MATRIX_EXPR))
            CheckWorkflow.setupAction("gradle")
            CheckWorkflow.setupParams("{\"java-version\": \"\${{ matrix.java-version }}\"}")
            CheckWorkflow.checkCommand("\${{ inputs.gradle-command }}")
        }
    }

    cleanReusableWorkflowJobs(File(outputDir, targetFile))
}
```

- [ ] **Step 4: Verify it compiles**

Run: `cd /Users/dmitriimedakin/IdeaProjects/zenhelix/ci-workflows && ./gradlew compileKotlin 2>&1 | tail -10`
Expected: May still have errors from other adapter files — that's OK. Check that the three modified files have no errors.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/workflows/adapters/AppCheck.kt src/main/kotlin/workflows/adapters/GradlePluginCheck.kt src/main/kotlin/workflows/adapters/KotlinLibraryCheck.kt
git commit -m "refactor: migrate check adapter workflows to typed DSL"
```

---

### Task 7: Migrate adapter workflows — Release variants (AppRelease, GradlePluginRelease, KotlinLibraryRelease)

**Files:**
- Modify: `src/main/kotlin/workflows/adapters/AppRelease.kt`
- Modify: `src/main/kotlin/workflows/adapters/GradlePluginRelease.kt`
- Modify: `src/main/kotlin/workflows/adapters/KotlinLibraryRelease.kt`

- [ ] **Step 1: Replace AppRelease.kt**

Replace entire file with:

```kotlin
package workflows.adapters

import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.domain.triggers.WorkflowDispatch
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import shared.DEFAULT_CHANGELOG_CONFIG
import shared.cleanReusableWorkflowJobs
import shared.dsl.ReleaseWorkflow
import shared.dsl.reusableWorkflowJob
import java.io.File

fun generateAppRelease(outputDir: File) {
    val targetFile = "app-release.yml"

    workflow(
        name = "Application Release",
        on = listOf(
            WorkflowDispatch(),
            WorkflowCall(inputs = mapOf(
                "changelog-config" to WorkflowCall.Input("Path to changelog configuration file", false, WorkflowCall.Type.String, DEFAULT_CHANGELOG_CONFIG),
                "draft" to WorkflowCall.Input("Create release as draft (default true for apps)", false, WorkflowCall.Type.Boolean, "true"),
            )),
        ),
        sourceFile = File(".github/workflow-src/app-release.main.kts"),
        targetFileName = targetFile,
        consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
    ) {
        reusableWorkflowJob(id = "release", uses = ReleaseWorkflow) {
            ReleaseWorkflow.changelogConfig("\${{ inputs.changelog-config }}")
            ReleaseWorkflow.draft("\${{ inputs.draft }}")
        }
    }

    cleanReusableWorkflowJobs(File(outputDir, targetFile))
}
```

- [ ] **Step 2: Replace GradlePluginRelease.kt**

Replace entire file with:

```kotlin
package workflows.adapters

import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.domain.triggers.WorkflowDispatch
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import shared.DEFAULT_CHANGELOG_CONFIG
import shared.DEFAULT_JAVA_VERSION
import shared.GRADLE_PORTAL_SECRETS
import shared.GRADLE_PORTAL_SECRETS_PASSTHROUGH
import shared.MAVEN_SONATYPE_SECRETS
import shared.MAVEN_SONATYPE_SECRETS_PASSTHROUGH
import shared.cleanReusableWorkflowJobs
import shared.dsl.PublishWorkflow
import shared.dsl.ReleaseWorkflow
import shared.dsl.reusableWorkflowJob
import java.io.File

fun generateGradlePluginRelease(outputDir: File) {
    val targetFile = "gradle-plugin-release.yml"

    workflow(
        name = "Gradle Plugin Release",
        on = listOf(
            WorkflowDispatch(),
            WorkflowCall(
                inputs = mapOf(
                    "java-version" to WorkflowCall.Input("JDK version to use", false, WorkflowCall.Type.String, DEFAULT_JAVA_VERSION),
                    "publish-command" to WorkflowCall.Input("Gradle publish command (publishes to both Maven Central and Gradle Portal)", true, WorkflowCall.Type.String),
                    "changelog-config" to WorkflowCall.Input("Path to changelog configuration file", false, WorkflowCall.Type.String, DEFAULT_CHANGELOG_CONFIG),
                ),
                secrets = MAVEN_SONATYPE_SECRETS + GRADLE_PORTAL_SECRETS,
            ),
        ),
        sourceFile = File(".github/workflow-src/gradle-plugin-release.main.kts"),
        targetFileName = targetFile,
        consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
    ) {
        reusableWorkflowJob(id = "release", uses = ReleaseWorkflow) {
            ReleaseWorkflow.changelogConfig("\${{ inputs.changelog-config }}")
        }

        reusableWorkflowJob(id = "publish", uses = PublishWorkflow) {
            needs("release")
            PublishWorkflow.setupAction("gradle")
            PublishWorkflow.setupParams("{\"java-version\": \"\${{ inputs.java-version }}\"}")
            PublishWorkflow.publishCommand("\${{ inputs.publish-command }}")
            secrets(MAVEN_SONATYPE_SECRETS_PASSTHROUGH + GRADLE_PORTAL_SECRETS_PASSTHROUGH)
        }
    }

    cleanReusableWorkflowJobs(File(outputDir, targetFile))
}
```

- [ ] **Step 3: Replace KotlinLibraryRelease.kt**

Replace entire file with:

```kotlin
package workflows.adapters

import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.domain.triggers.WorkflowDispatch
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import shared.DEFAULT_CHANGELOG_CONFIG
import shared.DEFAULT_JAVA_VERSION
import shared.MAVEN_SONATYPE_SECRETS
import shared.MAVEN_SONATYPE_SECRETS_PASSTHROUGH
import shared.cleanReusableWorkflowJobs
import shared.dsl.PublishWorkflow
import shared.dsl.ReleaseWorkflow
import shared.dsl.reusableWorkflowJob
import java.io.File

fun generateKotlinLibraryRelease(outputDir: File) {
    val targetFile = "kotlin-library-release.yml"

    workflow(
        name = "Kotlin Library Release",
        on = listOf(
            WorkflowDispatch(),
            WorkflowCall(
                inputs = mapOf(
                    "java-version" to WorkflowCall.Input("JDK version to use", false, WorkflowCall.Type.String, DEFAULT_JAVA_VERSION),
                    "publish-command" to WorkflowCall.Input("Gradle publish command for Maven Central", true, WorkflowCall.Type.String),
                    "changelog-config" to WorkflowCall.Input("Path to changelog configuration file", false, WorkflowCall.Type.String, DEFAULT_CHANGELOG_CONFIG),
                ),
                secrets = MAVEN_SONATYPE_SECRETS,
            ),
        ),
        sourceFile = File(".github/workflow-src/kotlin-library-release.main.kts"),
        targetFileName = targetFile,
        consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
    ) {
        reusableWorkflowJob(id = "release", uses = ReleaseWorkflow) {
            ReleaseWorkflow.changelogConfig("\${{ inputs.changelog-config }}")
        }

        reusableWorkflowJob(id = "publish", uses = PublishWorkflow) {
            needs("release")
            PublishWorkflow.setupAction("gradle")
            PublishWorkflow.setupParams("{\"java-version\": \"\${{ inputs.java-version }}\"}")
            PublishWorkflow.publishCommand("\${{ inputs.publish-command }}")
            secrets(MAVEN_SONATYPE_SECRETS_PASSTHROUGH)
        }
    }

    cleanReusableWorkflowJobs(File(outputDir, targetFile))
}
```

- [ ] **Step 4: Verify it compiles**

Run: `cd /Users/dmitriimedakin/IdeaProjects/zenhelix/ci-workflows && ./gradlew compileKotlin 2>&1 | tail -10`

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/workflows/adapters/AppRelease.kt src/main/kotlin/workflows/adapters/GradlePluginRelease.kt src/main/kotlin/workflows/adapters/KotlinLibraryRelease.kt
git commit -m "refactor: migrate release adapter workflows to typed DSL"
```

---

### Task 8: Migrate adapter workflows — CreateTag variants (GradleCreateTag, GradleManualCreateTag, GoCreateTag, GoManualCreateTag)

**Files:**
- Modify: `src/main/kotlin/workflows/adapters/GradleCreateTag.kt`
- Modify: `src/main/kotlin/workflows/adapters/GradleManualCreateTag.kt`
- Modify: `src/main/kotlin/workflows/adapters/GoCreateTag.kt`
- Modify: `src/main/kotlin/workflows/adapters/GoManualCreateTag.kt`

- [ ] **Step 1: Replace GradleCreateTag.kt**

Replace entire file with:

```kotlin
package workflows.adapters

import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.domain.triggers.WorkflowDispatch
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import shared.APP_SECRETS
import shared.APP_SECRETS_PASSTHROUGH
import shared.DEFAULT_JAVA_VERSION
import shared.DEFAULT_RELEASE_BRANCHES
import shared.cleanReusableWorkflowJobs
import shared.dsl.CreateTagWorkflow
import shared.dsl.reusableWorkflowJob
import java.io.File

fun generateGradleCreateTag(outputDir: File) {
    val targetFile = "gradle-create-tag.yml"

    workflow(
        name = "Gradle Create Tag",
        on = listOf(
            WorkflowDispatch(),
            WorkflowCall(
                inputs = mapOf(
                    "java-version" to WorkflowCall.Input("JDK version to use", false, WorkflowCall.Type.String, DEFAULT_JAVA_VERSION),
                    "gradle-command" to WorkflowCall.Input("Gradle validation command", false, WorkflowCall.Type.String, "./gradlew check"),
                    "default-bump" to WorkflowCall.Input("Default version bump type (major, minor, patch)", false, WorkflowCall.Type.String, "patch"),
                    "tag-prefix" to WorkflowCall.Input("Prefix for the tag", false, WorkflowCall.Type.String, ""),
                    "release-branches" to WorkflowCall.Input("Comma-separated branch patterns for releases", false, WorkflowCall.Type.String, DEFAULT_RELEASE_BRANCHES),
                ),
                secrets = APP_SECRETS,
            ),
        ),
        sourceFile = File(".github/workflow-src/gradle-create-tag.main.kts"),
        targetFileName = targetFile,
        consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
    ) {
        reusableWorkflowJob(id = "create-tag", uses = CreateTagWorkflow) {
            CreateTagWorkflow.setupAction("gradle")
            CreateTagWorkflow.setupParams("{\"java-version\": \"\${{ inputs.java-version }}\"}")
            CreateTagWorkflow.checkCommand("\${{ inputs.gradle-command }}")
            CreateTagWorkflow.defaultBump("\${{ inputs.default-bump }}")
            CreateTagWorkflow.tagPrefix("\${{ inputs.tag-prefix }}")
            CreateTagWorkflow.releaseBranches("\${{ inputs.release-branches }}")
            secrets(APP_SECRETS_PASSTHROUGH)
        }
    }

    cleanReusableWorkflowJobs(File(outputDir, targetFile))
}
```

- [ ] **Step 2: Replace GradleManualCreateTag.kt**

Replace entire file with:

```kotlin
package workflows.adapters

import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.domain.triggers.WorkflowDispatch
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import shared.APP_SECRETS
import shared.APP_SECRETS_PASSTHROUGH
import shared.DEFAULT_JAVA_VERSION
import shared.cleanReusableWorkflowJobs
import shared.dsl.ManualCreateTagWorkflow
import shared.dsl.reusableWorkflowJob
import java.io.File

fun generateGradleManualCreateTag(outputDir: File) {
    val targetFile = "gradle-manual-create-tag.yml"

    workflow(
        name = "Gradle Manual Create Tag",
        on = listOf(
            WorkflowDispatch(),
            WorkflowCall(
                inputs = mapOf(
                    "tag-version" to WorkflowCall.Input("Version to tag (e.g. 1.2.3)", true, WorkflowCall.Type.String),
                    "java-version" to WorkflowCall.Input("JDK version to use", false, WorkflowCall.Type.String, DEFAULT_JAVA_VERSION),
                    "gradle-command" to WorkflowCall.Input("Gradle validation command", false, WorkflowCall.Type.String, "./gradlew check"),
                    "tag-prefix" to WorkflowCall.Input("Prefix for the tag", false, WorkflowCall.Type.String, ""),
                ),
                secrets = APP_SECRETS,
            ),
        ),
        sourceFile = File(".github/workflow-src/gradle-manual-create-tag.main.kts"),
        targetFileName = targetFile,
        consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
    ) {
        reusableWorkflowJob(id = "manual-tag", uses = ManualCreateTagWorkflow) {
            ManualCreateTagWorkflow.tagVersion("\${{ inputs.tag-version }}")
            ManualCreateTagWorkflow.tagPrefix("\${{ inputs.tag-prefix }}")
            ManualCreateTagWorkflow.setupAction("gradle")
            ManualCreateTagWorkflow.setupParams("{\"java-version\": \"\${{ inputs.java-version }}\"}")
            ManualCreateTagWorkflow.checkCommand("\${{ inputs.gradle-command }}")
            secrets(APP_SECRETS_PASSTHROUGH)
        }
    }

    cleanReusableWorkflowJobs(File(outputDir, targetFile))
}
```

- [ ] **Step 3: Replace GoCreateTag.kt**

Replace entire file with:

```kotlin
package workflows.adapters

import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.domain.triggers.WorkflowDispatch
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import shared.APP_SECRETS
import shared.APP_SECRETS_PASSTHROUGH
import shared.DEFAULT_GO_VERSION
import shared.DEFAULT_RELEASE_BRANCHES
import shared.cleanReusableWorkflowJobs
import shared.dsl.CreateTagWorkflow
import shared.dsl.reusableWorkflowJob
import java.io.File

fun generateGoCreateTag(outputDir: File) {
    val targetFile = "go-create-tag.yml"

    workflow(
        name = "Go Create Tag",
        on = listOf(
            WorkflowDispatch(),
            WorkflowCall(
                inputs = mapOf(
                    "go-version" to WorkflowCall.Input("Go version to use", false, WorkflowCall.Type.String, DEFAULT_GO_VERSION),
                    "check-command" to WorkflowCall.Input("Go validation command", false, WorkflowCall.Type.String, "make test"),
                    "default-bump" to WorkflowCall.Input("Default version bump type (major, minor, patch)", false, WorkflowCall.Type.String, "patch"),
                    "tag-prefix" to WorkflowCall.Input("Prefix for the tag", false, WorkflowCall.Type.String, "v"),
                    "release-branches" to WorkflowCall.Input("Comma-separated branch patterns for releases", false, WorkflowCall.Type.String, DEFAULT_RELEASE_BRANCHES),
                ),
                secrets = APP_SECRETS,
            ),
        ),
        sourceFile = File(".github/workflow-src/go-create-tag.main.kts"),
        targetFileName = targetFile,
        consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
    ) {
        reusableWorkflowJob(id = "create-tag", uses = CreateTagWorkflow) {
            CreateTagWorkflow.setupAction("go")
            CreateTagWorkflow.setupParams("{\"go-version\": \"\${{ inputs.go-version }}\"}")
            CreateTagWorkflow.checkCommand("\${{ inputs.check-command }}")
            CreateTagWorkflow.defaultBump("\${{ inputs.default-bump }}")
            CreateTagWorkflow.tagPrefix("\${{ inputs.tag-prefix }}")
            CreateTagWorkflow.releaseBranches("\${{ inputs.release-branches }}")
            secrets(APP_SECRETS_PASSTHROUGH)
        }
    }

    cleanReusableWorkflowJobs(File(outputDir, targetFile))
}
```

- [ ] **Step 4: Replace GoManualCreateTag.kt**

Replace entire file with:

```kotlin
package workflows.adapters

import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.domain.triggers.WorkflowDispatch
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import shared.APP_SECRETS
import shared.APP_SECRETS_PASSTHROUGH
import shared.DEFAULT_GO_VERSION
import shared.cleanReusableWorkflowJobs
import shared.dsl.ManualCreateTagWorkflow
import shared.dsl.reusableWorkflowJob
import java.io.File

fun generateGoManualCreateTag(outputDir: File) {
    val targetFile = "go-manual-create-tag.yml"

    workflow(
        name = "Go Manual Create Tag",
        on = listOf(
            WorkflowDispatch(),
            WorkflowCall(
                inputs = mapOf(
                    "tag-version" to WorkflowCall.Input("Version to tag (e.g. 1.2.3)", true, WorkflowCall.Type.String),
                    "go-version" to WorkflowCall.Input("Go version to use", false, WorkflowCall.Type.String, DEFAULT_GO_VERSION),
                    "check-command" to WorkflowCall.Input("Go validation command", false, WorkflowCall.Type.String, "make test"),
                    "tag-prefix" to WorkflowCall.Input("Prefix for the tag", false, WorkflowCall.Type.String, "v"),
                ),
                secrets = APP_SECRETS,
            ),
        ),
        sourceFile = File(".github/workflow-src/go-manual-create-tag.main.kts"),
        targetFileName = targetFile,
        consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
    ) {
        reusableWorkflowJob(id = "manual-tag", uses = ManualCreateTagWorkflow) {
            ManualCreateTagWorkflow.tagVersion("\${{ inputs.tag-version }}")
            ManualCreateTagWorkflow.tagPrefix("\${{ inputs.tag-prefix }}")
            ManualCreateTagWorkflow.setupAction("go")
            ManualCreateTagWorkflow.setupParams("{\"go-version\": \"\${{ inputs.go-version }}\"}")
            ManualCreateTagWorkflow.checkCommand("\${{ inputs.check-command }}")
            secrets(APP_SECRETS_PASSTHROUGH)
        }
    }

    cleanReusableWorkflowJobs(File(outputDir, targetFile))
}
```

- [ ] **Step 5: Verify it compiles**

Run: `cd /Users/dmitriimedakin/IdeaProjects/zenhelix/ci-workflows && ./gradlew compileKotlin 2>&1 | tail -10`

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/workflows/adapters/GradleCreateTag.kt src/main/kotlin/workflows/adapters/GradleManualCreateTag.kt src/main/kotlin/workflows/adapters/GoCreateTag.kt src/main/kotlin/workflows/adapters/GoManualCreateTag.kt
git commit -m "refactor: migrate create-tag adapter workflows to typed DSL"
```

---

### Task 9: Migrate AppDeploy adapter (non-reusable-job workflow)

**Files:**
- Modify: `src/main/kotlin/workflows/adapters/AppDeploy.kt`

AppDeploy does NOT use reusable workflow jobs — it has actual steps. Only the workflow-level `_customArguments` needs migration.

- [ ] **Step 1: Replace AppDeploy.kt**

Replace entire file with:

```kotlin
package workflows.adapters

import io.github.typesafegithub.workflows.domain.Mode
import io.github.typesafegithub.workflows.domain.Permission
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.domain.triggers.WorkflowDispatch
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import shared.conditionalSetupSteps
import java.io.File

fun generateAppDeploy(outputDir: File) {
    workflow(
        name = "Application Deploy",
        on = listOf(
            WorkflowDispatch(),
            WorkflowCall(inputs = mapOf(
                "setup-action" to WorkflowCall.Input("Setup action to use: gradle, go, python", true, WorkflowCall.Type.String),
                "setup-params" to WorkflowCall.Input("JSON object with setup parameters (e.g. {\"java-version\": \"21\"})", false, WorkflowCall.Type.String, "{}"),
                "deploy-command" to WorkflowCall.Input("Command to run for deployment", true, WorkflowCall.Type.String),
                "tag" to WorkflowCall.Input("Tag/version to deploy (checked out at this ref)", true, WorkflowCall.Type.String),
            )),
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

- [ ] **Step 2: Verify full project compiles**

Run: `cd /Users/dmitriimedakin/IdeaProjects/zenhelix/ci-workflows && ./gradlew compileKotlin 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL — all files should compile now.

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/workflows/adapters/AppDeploy.kt
git commit -m "refactor: migrate AppDeploy to typed WorkflowCall trigger and permissions"
```

---

### Task 10: Verify generated YAML is identical

**Files:** No changes — verification only.

The goal is to confirm the refactoring produces byte-identical YAML output.

- [ ] **Step 1: Save current YAML as baseline**

```bash
cd /Users/dmitriimedakin/IdeaProjects/zenhelix/ci-workflows
cp -r .github/workflows /tmp/workflows-baseline
```

- [ ] **Step 2: Regenerate all workflows**

Run: `cd /Users/dmitriimedakin/IdeaProjects/zenhelix/ci-workflows && ./gradlew run 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL, no errors

- [ ] **Step 3: Diff the output**

Run: `diff -rq /tmp/workflows-baseline .github/workflows`
Expected: No differences. If there are differences, inspect them with `diff -u /tmp/workflows-baseline/<file> .github/workflows/<file>` and fix the source.

**Common causes of diff:**
- Key ordering: `WorkflowCall.Input` constructor parameter order differs from the old `mapOf` order. The library may serialize fields in a different order.
- Boolean defaults: `"false"` (string) vs `false` (boolean) — ensure `booleanInput` in `ReusableWorkflow` passes the default correctly.
- Missing/extra whitespace in descriptions.

If diffs exist, go back to the relevant task and fix the definitions to match the expected YAML output.

- [ ] **Step 4: If YAML is identical, commit any remaining changes and clean up**

```bash
cd /Users/dmitriimedakin/IdeaProjects/zenhelix/ci-workflows
rm -rf /tmp/workflows-baseline
git status
```

If there are no uncommitted changes, the migration is complete.
