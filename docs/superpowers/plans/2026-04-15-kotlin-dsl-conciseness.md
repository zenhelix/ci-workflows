# Kotlin DSL Conciseness & Type Safety — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove boilerplate, improve type safety, and consolidate overloads in the workflow DSL and base workflow objects.

**Architecture:** Five independent changes (can be committed separately) that touch the `workflow-dsl` module and `src/main/kotlin` workflow objects. Changes 2 and 3 are coupled (SetupAwareJobBuilder generic + JobBuilder removal). All others are independent.

**Tech Stack:** Kotlin 2.3.20, github-workflows-kt 3.7.0, kaml 0.104.0

---

### Task 1: Rename `InputType.String` → `InputType.Text`

**Files:**
- Modify: `workflow-dsl/src/main/kotlin/dsl/core/WorkflowInput.kt`
- Modify: `workflow-dsl/src/main/kotlin/dsl/core/InputRegistry.kt`

- [ ] **Step 1: Rename enum value and fix `yamlName()` in `WorkflowInput.kt`**

Replace the `InputType` enum and `WorkflowInputDef` data class in `workflow-dsl/src/main/kotlin/dsl/core/WorkflowInput.kt`:

```kotlin
enum class InputType {
    Text, Boolean, Number, Choice;

    fun yamlName(): String = when (this) {
        Text -> "string"
        else -> name.lowercase()
    }
}

data class WorkflowInputDef(
    val name: String,
    val description: String,
    val type: InputType = InputType.Text,
    val required: Boolean = false,
    val default: InputDefault? = null,
)
```

Key changes:
- `String` → `Text` in enum
- `fun yamlName(): kotlin.String` → `fun yamlName(): String` (no more qualified name)
- `name.lowercase()` → explicit `when` with `Text -> "string"` to preserve YAML output
- All `kotlin.String` and `kotlin.Boolean` qualifications removed from `WorkflowInputDef`

- [ ] **Step 2: Update `InputRegistry.kt` references**

In `workflow-dsl/src/main/kotlin/dsl/core/InputRegistry.kt`, replace all three occurrences of `InputType.String` with `InputType.Text`:

Line 16: `type = InputType.String,` → `type = InputType.Text,`
Line 31: `type = InputType.String,` → `type = InputType.Text,`

(The third overload at line 47 already uses `InputType.Boolean` — no change needed.)

- [ ] **Step 3: Build and verify**

Run: `cd /Users/dmitriimedakin/IdeaProjects/zenhelix/ci-workflows && ./gradlew build`
Expected: BUILD SUCCESSFUL

Run: `./gradlew run && git diff .github/workflows/`
Expected: No diff (YAML output unchanged)

- [ ] **Step 4: Commit**

```bash
git add workflow-dsl/src/main/kotlin/dsl/core/WorkflowInput.kt workflow-dsl/src/main/kotlin/dsl/core/InputRegistry.kt
git commit -m "refactor: rename InputType.String to InputType.Text, remove kotlin.String qualifications"
```

---

### Task 2: Type-safe `SetupAwareJobBuilder<W>` with generic constraint

**Files:**
- Modify: `workflow-dsl/src/main/kotlin/dsl/builder/ReusableWorkflowJobBuilder.kt`

- [ ] **Step 1: Change `ReusableWorkflowJobBuilder` from `abstract` to `open`**

In `workflow-dsl/src/main/kotlin/dsl/builder/ReusableWorkflowJobBuilder.kt`, line 14:

```kotlin
// Before:
abstract class ReusableWorkflowJobBuilder(private val workflow: ReusableWorkflow) {
// After:
open class ReusableWorkflowJobBuilder(private val workflow: ReusableWorkflow) {
```

- [ ] **Step 2: Replace `SetupAwareJobBuilder` with generic version**

In the same file, replace lines 94-102:

```kotlin
// Before:
abstract class SetupAwareJobBuilder(workflow: ReusableWorkflow) :
    ReusableWorkflowJobBuilder(workflow), SetupCapableJobBuilder {

    private val capability = (workflow as? SetupCapability)
        ?: error("${workflow.fileName} must implement SetupCapability")

    override var setupAction by stringInput(capability.setupAction)
    override var setupParams by stringInput(capability.setupParams)
}
```

With:

```kotlin
class SetupAwareJobBuilder<W>(workflow: W) :
    ReusableWorkflowJobBuilder(workflow), SetupCapableJobBuilder
    where W : ReusableWorkflow, W : SetupCapability {

    override var setupAction by stringInput(workflow.setupAction)
    override var setupParams by stringInput(workflow.setupParams)
}
```

Key changes:
- `abstract class` → `class` (concrete, no subclassing needed)
- Added generic `<W>` with `where W : ReusableWorkflow, W : SetupCapability` constraint
- Removed runtime `as?` cast + `error()` — compiler now enforces `SetupCapability`
- `capability.setupAction` → `workflow.setupAction` (direct access, no cast)

- [ ] **Step 3: Build the workflow-dsl module**

Run: `cd /Users/dmitriimedakin/IdeaProjects/zenhelix/ci-workflows && ./gradlew :workflow-dsl:build`
Expected: BUILD SUCCESSFUL (the main module will fail until Task 3 updates workflow objects)

- [ ] **Step 4: Commit**

```bash
git add workflow-dsl/src/main/kotlin/dsl/builder/ReusableWorkflowJobBuilder.kt
git commit -m "refactor: make SetupAwareJobBuilder type-safe with generic constraint"
```

---

### Task 3: Eliminate custom JobBuilder classes from base workflows

**Files:**
- Modify: `src/main/kotlin/workflows/base/CheckWorkflow.kt`
- Modify: `src/main/kotlin/workflows/base/CreateTagWorkflow.kt`
- Modify: `src/main/kotlin/workflows/base/ManualCreateTagWorkflow.kt`
- Modify: `src/main/kotlin/workflows/base/PublishWorkflow.kt`
- Modify: `src/main/kotlin/workflows/base/AppDeployWorkflow.kt`
- Modify: `src/main/kotlin/workflows/base/ReleaseWorkflow.kt`
- Modify: `src/main/kotlin/workflows/base/ConventionalCommitCheckWorkflow.kt`
- Modify: `src/main/kotlin/workflows/base/LabelerWorkflow.kt`
- Modify: `src/main/kotlin/workflows/adapters/release/ReleaseAdapters.kt`

- [ ] **Step 1: Update CheckWorkflow (setup-aware)**

In `src/main/kotlin/workflows/base/CheckWorkflow.kt`, remove the `JobBuilder` class and update imports and `job()`:

```kotlin
package workflows.base

import dsl.builder.AdapterWorkflowBuilder
import dsl.builder.SetupAwareJobBuilder
import dsl.capability.SetupCapability
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.dsl.WorkflowBuilder
import workflows.ProjectWorkflow
import workflows.support.conditionalSetupSteps

object CheckWorkflow : ProjectWorkflow("check.yml", "Check"), SetupCapability {
    override val setupAction = input("setup-action", SetupCapability.SETUP_ACTION_DESCRIPTION, required = true)
    override val setupParams = input("setup-params", SetupCapability.SETUP_PARAMS_DESCRIPTION, default = SetupCapability.SETUP_PARAMS_DEFAULT)
    val checkCommand = input("check-command", "Command to run for checking", required = true)

    context(builder: AdapterWorkflowBuilder)
    fun job(id: String, block: SetupAwareJobBuilder<CheckWorkflow>.() -> Unit = {}) =
        job(id, { SetupAwareJobBuilder(this@CheckWorkflow) }, block)

    override fun WorkflowBuilder.implementation() {
        job(id = "build", name = "Build", runsOn = UbuntuLatest) {
            conditionalSetupSteps()
            run(name = "Run check", command = checkCommand.ref.expression)
        }
    }
}
```

Changes: removed `import dsl.builder.refInput`, removed `class JobBuilder`, changed `job()` signature from `JobBuilder.() -> Unit` to `SetupAwareJobBuilder<CheckWorkflow>.() -> Unit`, changed factory from `::JobBuilder` to `{ SetupAwareJobBuilder(this@CheckWorkflow) }`.

- [ ] **Step 2: Update CreateTagWorkflow (setup-aware)**

In `src/main/kotlin/workflows/base/CreateTagWorkflow.kt`, remove `JobBuilder` and update `job()`:

```kotlin
package workflows.base

import actions.CreateAppTokenAction
import actions.GithubTagAction
import config.DEFAULT_RELEASE_BRANCHES
import dsl.builder.AdapterWorkflowBuilder
import dsl.builder.SetupAwareJobBuilder
import dsl.capability.SetupCapability
import io.github.typesafegithub.workflows.domain.Mode
import io.github.typesafegithub.workflows.domain.Permission
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.dsl.WorkflowBuilder
import workflows.ProjectWorkflow
import workflows.support.conditionalSetupSteps

object CreateTagWorkflow : ProjectWorkflow(
    "create-tag.yml", "Create Tag",
    permissions = mapOf(Permission.Contents to Mode.Write),
), SetupCapability {
    override val setupAction = input("setup-action", SetupCapability.SETUP_ACTION_DESCRIPTION, required = true)
    override val setupParams = input("setup-params", SetupCapability.SETUP_PARAMS_DESCRIPTION, default = SetupCapability.SETUP_PARAMS_DEFAULT)
    val checkCommand = input("check-command", "Validation command to run before tagging", required = true)
    val defaultBump = input("default-bump", "Default version bump type (major, minor, patch)", default = "patch")
    val tagPrefix = input("tag-prefix", "Prefix for the tag (e.g. v)", default = "")
    val releaseBranches = input("release-branches", "Comma-separated branch patterns for releases", default = DEFAULT_RELEASE_BRANCHES)
    val appId = secret("app-id", "GitHub App ID for generating commit token")
    val appPrivateKey = secret("app-private-key", "GitHub App private key for generating commit token")

    context(builder: AdapterWorkflowBuilder)
    fun job(id: String, block: SetupAwareJobBuilder<CreateTagWorkflow>.() -> Unit = {}) =
        job(id, { SetupAwareJobBuilder(this@CreateTagWorkflow) }, block)

    override fun WorkflowBuilder.implementation() {
        job(id = "create_tag", name = "Create Tag", runsOn = UbuntuLatest) {
            conditionalSetupSteps(fetchDepth = "0")
            run(name = "Run validation", command = checkCommand.ref.expression)
            uses(
                name = "Generate App Token",
                action = CreateAppTokenAction(appId = appId.ref.expression, appPrivateKey = appPrivateKey.ref.expression),
                id = "app-token",
            )
            uses(
                name = "Bump version and push tag",
                action = GithubTagAction(
                    githubToken = "\${{ steps.app-token.outputs.token }}",
                    defaultBump = defaultBump.ref.expression,
                    tagPrefix = tagPrefix.ref.expression,
                    releaseBranches = releaseBranches.ref.expression,
                ),
            )
        }
    }
}
```

Changes: removed `import dsl.builder.refInput`, removed `class JobBuilder` (4 `refInput` properties), updated `job()` signature and factory.

- [ ] **Step 3: Update ManualCreateTagWorkflow (setup-aware)**

In `src/main/kotlin/workflows/base/ManualCreateTagWorkflow.kt`, remove `JobBuilder` and update `job()`:

```kotlin
package workflows.base

import actions.CreateAppTokenAction
import dsl.builder.AdapterWorkflowBuilder
import dsl.builder.SetupAwareJobBuilder
import dsl.capability.SetupCapability
import io.github.typesafegithub.workflows.domain.Mode
import io.github.typesafegithub.workflows.domain.Permission
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.dsl.WorkflowBuilder
import workflows.ProjectWorkflow
import workflows.support.conditionalSetupSteps

object ManualCreateTagWorkflow : ProjectWorkflow(
    "manual-create-tag.yml", "Manual Create Tag",
    permissions = mapOf(Permission.Contents to Mode.Write),
), SetupCapability {
    val tagVersion = input("tag-version", "Version to tag (e.g. 1.2.3)", required = true)
    val tagPrefix = input("tag-prefix", "Prefix for the tag (e.g. v)", default = "")
    override val setupAction = input("setup-action", SetupCapability.SETUP_ACTION_DESCRIPTION, required = true)
    override val setupParams = input("setup-params", SetupCapability.SETUP_PARAMS_DESCRIPTION, default = SetupCapability.SETUP_PARAMS_DEFAULT)
    val checkCommand = input("check-command", "Validation command to run before tagging", required = true)
    val appId = secret("app-id", "GitHub App ID for generating commit token")
    val appPrivateKey = secret("app-private-key", "GitHub App private key for generating commit token")

    context(builder: AdapterWorkflowBuilder)
    fun job(id: String, block: SetupAwareJobBuilder<ManualCreateTagWorkflow>.() -> Unit = {}) =
        job(id, { SetupAwareJobBuilder(this@ManualCreateTagWorkflow) }, block)

    override fun WorkflowBuilder.implementation() {
        job(id = "manual_tag", name = "Manual Tag", runsOn = UbuntuLatest) {
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
            run(name = "Run validation", command = checkCommand.ref.expression)
            uses(
                name = "Generate App Token",
                action = CreateAppTokenAction(appId = appId.ref.expression, appPrivateKey = appPrivateKey.ref.expression),
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
                env = linkedMapOf("GITHUB_TOKEN" to "\${{ steps.app-token.outputs.token }}"),
            )
        }
    }
}
```

Changes: removed `import dsl.builder.refInput`, removed `class JobBuilder` (3 `refInput` properties), updated `job()` signature and factory.

- [ ] **Step 4: Update PublishWorkflow (setup-aware)**

In `src/main/kotlin/workflows/base/PublishWorkflow.kt`, remove `JobBuilder` and update `job()`:

```kotlin
package workflows.base

import dsl.builder.AdapterWorkflowBuilder
import dsl.builder.SetupAwareJobBuilder
import dsl.capability.SetupCapability
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.dsl.WorkflowBuilder
import workflows.ProjectWorkflow
import workflows.support.conditionalSetupSteps

object PublishWorkflow : ProjectWorkflow("publish.yml", "Publish"), SetupCapability {
    override val setupAction = input("setup-action", SetupCapability.SETUP_ACTION_DESCRIPTION, required = true)
    override val setupParams = input("setup-params", SetupCapability.SETUP_PARAMS_DESCRIPTION, default = SetupCapability.SETUP_PARAMS_DEFAULT)
    val publishCommand = input("publish-command", "Command to run for publishing", required = true)
    val mavenSonatypeUsername = secret("MAVEN_SONATYPE_USERNAME", "Maven Central (Sonatype) username", required = false)
    val mavenSonatypeToken = secret("MAVEN_SONATYPE_TOKEN", "Maven Central (Sonatype) token", required = false)
    val mavenSonatypeSigningKeyId = secret("MAVEN_SONATYPE_SIGNING_KEY_ID", "GPG signing key ID", required = false)
    val mavenSonatypeSigningPubKeyAsciiArmored = secret("MAVEN_SONATYPE_SIGNING_PUB_KEY_ASCII_ARMORED", "GPG signing public key (ASCII armored)", required = false)
    val mavenSonatypeSigningKeyAsciiArmored = secret("MAVEN_SONATYPE_SIGNING_KEY_ASCII_ARMORED", "GPG signing private key (ASCII armored)", required = false)
    val mavenSonatypeSigningPassword = secret("MAVEN_SONATYPE_SIGNING_PASSWORD", "GPG signing key passphrase", required = false)
    val gradlePublishKey = secret("GRADLE_PUBLISH_KEY", "Gradle Plugin Portal publish key", required = false)
    val gradlePublishSecret = secret("GRADLE_PUBLISH_SECRET", "Gradle Plugin Portal publish secret", required = false)

    val mavenSecrets = listOf(
        mavenSonatypeUsername, mavenSonatypeToken,
        mavenSonatypeSigningKeyId, mavenSonatypeSigningPubKeyAsciiArmored,
        mavenSonatypeSigningKeyAsciiArmored, mavenSonatypeSigningPassword,
    )
    val gradlePortalSecrets = listOf(gradlePublishKey, gradlePublishSecret)

    context(builder: AdapterWorkflowBuilder)
    fun job(id: String, block: SetupAwareJobBuilder<PublishWorkflow>.() -> Unit = {}) =
        job(id, { SetupAwareJobBuilder(this@PublishWorkflow) }, block)

    override fun WorkflowBuilder.implementation() {
        job(id = "publish", name = "Publish", runsOn = UbuntuLatest) {
            conditionalSetupSteps()
            run(
                name = "Publish",
                command = publishCommand.ref.expression,
                env = linkedMapOf(
                    "GRADLE_PUBLISH_KEY" to gradlePublishKey.ref.expression,
                    "GRADLE_PUBLISH_SECRET" to gradlePublishSecret.ref.expression,
                    "ORG_GRADLE_PROJECT_signingKeyId" to mavenSonatypeSigningKeyId.ref.expression,
                    "ORG_GRADLE_PROJECT_signingPublicKey" to mavenSonatypeSigningPubKeyAsciiArmored.ref.expression,
                    "ORG_GRADLE_PROJECT_signingKey" to mavenSonatypeSigningKeyAsciiArmored.ref.expression,
                    "ORG_GRADLE_PROJECT_signingPassword" to mavenSonatypeSigningPassword.ref.expression,
                    "MAVEN_SONATYPE_USERNAME" to mavenSonatypeUsername.ref.expression,
                    "MAVEN_SONATYPE_TOKEN" to mavenSonatypeToken.ref.expression,
                ),
            )
        }
    }
}
```

Changes: removed `import dsl.builder.refInput`, removed `class JobBuilder` (1 `refInput` property), updated `job()` signature and factory.

- [ ] **Step 5: Update AppDeployWorkflow (setup-aware)**

In `src/main/kotlin/workflows/base/AppDeployWorkflow.kt`, remove `JobBuilder` and update `job()`:

```kotlin
package workflows.base

import dsl.builder.AdapterWorkflowBuilder
import dsl.builder.SetupAwareJobBuilder
import dsl.capability.SetupCapability
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.dsl.WorkflowBuilder
import workflows.ProjectWorkflow
import workflows.support.conditionalSetupSteps

object AppDeployWorkflow : ProjectWorkflow("app-deploy.yml", "Application Deploy"), SetupCapability {
    override val setupAction = input("setup-action", SetupCapability.SETUP_ACTION_DESCRIPTION, required = true)
    override val setupParams = input("setup-params", SetupCapability.SETUP_PARAMS_DESCRIPTION, default = SetupCapability.SETUP_PARAMS_DEFAULT)
    val deployCommand = input("deploy-command", "Command to run for deployment", required = true)
    val tag = input("tag", "Tag/version to deploy (checked out at this ref)", required = true)

    context(builder: AdapterWorkflowBuilder)
    fun job(id: String, block: SetupAwareJobBuilder<AppDeployWorkflow>.() -> Unit = {}) =
        job(id, { SetupAwareJobBuilder(this@AppDeployWorkflow) }, block)

    override fun WorkflowBuilder.implementation() {
        job(id = "deploy", name = "Deploy", runsOn = UbuntuLatest) {
            conditionalSetupSteps(fetchDepth = "0")
            run(name = "Checkout tag", command = "git checkout \"${tag.ref.expression}\"")
            run(name = "Deploy", command = deployCommand.ref.expression)
        }
    }
}
```

Changes: removed `import dsl.builder.refInput`, removed `class JobBuilder` (2 `refInput` properties), updated `job()` signature and factory.

- [ ] **Step 6: Update ReleaseWorkflow (non-setup)**

In `src/main/kotlin/workflows/base/ReleaseWorkflow.kt`, remove `JobBuilder` and update `job()`:

```kotlin
package workflows.base

import config.DEFAULT_CHANGELOG_CONFIG
import dsl.builder.AdapterWorkflowBuilder
import dsl.builder.ReusableWorkflowJobBuilder
import io.github.typesafegithub.workflows.actions.actions.Checkout
import io.github.typesafegithub.workflows.actions.mikepenz.ReleaseChangelogBuilderAction_Untyped
import io.github.typesafegithub.workflows.actions.softprops.ActionGhRelease
import io.github.typesafegithub.workflows.domain.Mode
import io.github.typesafegithub.workflows.domain.Permission
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.dsl.WorkflowBuilder
import workflows.ProjectWorkflow

object ReleaseWorkflow : ProjectWorkflow(
    "release.yml", "Release",
    permissions = mapOf(Permission.Contents to Mode.Write),
) {
    val changelogConfig = input("changelog-config", "Path to changelog configuration file", default = DEFAULT_CHANGELOG_CONFIG)
    val draft = input("draft", "Create release as draft", default = false)

    context(builder: AdapterWorkflowBuilder)
    fun job(id: String, block: ReusableWorkflowJobBuilder.() -> Unit = {}) =
        job(id, { ReusableWorkflowJobBuilder(this@ReleaseWorkflow) }, block)

    override fun WorkflowBuilder.implementation() {
        job(id = "release", name = "GitHub Release", runsOn = UbuntuLatest) {
            uses(name = "Check out", action = Checkout(fetchDepth = Checkout.FetchDepth.Value(0)))
            uses(
                name = "Build Changelog",
                action = ReleaseChangelogBuilderAction_Untyped(
                    configuration_Untyped = changelogConfig.ref.expression,
                    toTag_Untyped = "\${{ github.ref_name }}",
                ),
                id = "changelog",
                env = linkedMapOf("GITHUB_TOKEN" to "\${{ secrets.GITHUB_TOKEN }}"),
            )
            uses(
                name = "Create Release",
                action = ActionGhRelease(
                    body = "\${{ steps.changelog.outputs.changelog }}",
                    name = "\${{ github.ref_name }}",
                    tagName = "\${{ github.ref_name }}",
                    draft_Untyped = draft.ref.expression,
                ),
                env = linkedMapOf("GITHUB_TOKEN" to "\${{ secrets.GITHUB_TOKEN }}"),
            )
        }
    }
}
```

Changes: removed `import dsl.builder.refInput`, removed `class JobBuilder` (2 `refInput` properties), updated `job()` to use `ReusableWorkflowJobBuilder` directly.

- [ ] **Step 7: Update ConventionalCommitCheckWorkflow (non-setup)**

In `src/main/kotlin/workflows/base/ConventionalCommitCheckWorkflow.kt`, remove `JobBuilder` and update `job()`:

```kotlin
package workflows.base

import dsl.builder.AdapterWorkflowBuilder
import dsl.builder.ReusableWorkflowJobBuilder
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.dsl.WorkflowBuilder
import workflows.ProjectWorkflow

object ConventionalCommitCheckWorkflow : ProjectWorkflow("conventional-commit-check.yml", "Conventional Commit Check", permissions = null) {
    val allowedTypes = input("allowed-types", "Comma-separated list of allowed commit types", default = "feat,fix,refactor,docs,test,chore,perf,ci")

    context(builder: AdapterWorkflowBuilder)
    fun job(id: String, block: ReusableWorkflowJobBuilder.() -> Unit = {}) =
        job(id, { ReusableWorkflowJobBuilder(this@ConventionalCommitCheckWorkflow) }, block)

    override fun WorkflowBuilder.implementation() {
        job(id = "check-title", name = "Check PR Title", runsOn = UbuntuLatest) {
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
                    "ALLOWED_TYPES" to allowedTypes.ref.expression,
                ),
            )
        }
    }
}
```

Changes: removed `import dsl.builder.refInput`, removed `class JobBuilder` (1 `refInput` property), updated `job()` to use `ReusableWorkflowJobBuilder` directly.

- [ ] **Step 8: Update LabelerWorkflow (non-setup)**

In `src/main/kotlin/workflows/base/LabelerWorkflow.kt`, remove `JobBuilder` and update `job()`:

```kotlin
package workflows.base

import dsl.builder.AdapterWorkflowBuilder
import dsl.builder.ReusableWorkflowJobBuilder
import io.github.typesafegithub.workflows.actions.actions.Labeler
import io.github.typesafegithub.workflows.domain.Mode
import io.github.typesafegithub.workflows.domain.Permission
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.dsl.WorkflowBuilder
import workflows.ProjectWorkflow

object LabelerWorkflow : ProjectWorkflow(
    "labeler.yml", "PR Labeler",
    permissions = mapOf(Permission.Contents to Mode.Write, Permission.PullRequests to Mode.Write),
) {
    val configPath = input("config-path", "Path to labeler configuration file", default = ".github/labeler.yml")

    context(builder: AdapterWorkflowBuilder)
    fun job(id: String, block: ReusableWorkflowJobBuilder.() -> Unit = {}) =
        job(id, { ReusableWorkflowJobBuilder(this@LabelerWorkflow) }, block)

    override fun WorkflowBuilder.implementation() {
        job(id = "label", name = "Label PR", runsOn = UbuntuLatest) {
            uses(
                name = "Label PR based on file paths",
                action = Labeler(
                    repoToken_Untyped = "\${{ secrets.GITHUB_TOKEN }}",
                    configurationPath_Untyped = configPath.ref.expression,
                    syncLabels = true,
                ),
            )
        }
    }
}
```

Changes: removed `import dsl.builder.refInput`, removed `class JobBuilder` (1 `refInput` property), updated `job()` to use `ReusableWorkflowJobBuilder` directly.

- [ ] **Step 9: Update ReleaseAdapters.kt — fix `PublishWorkflow.JobBuilder` reference**

In `src/main/kotlin/workflows/adapters/release/ReleaseAdapters.kt`, the `gradleRelease` function parameter `publishSecrets: PublishWorkflow.JobBuilder.() -> Unit` references the now-deleted inner class. Update line 38:

```kotlin
// Before:
publishSecrets: PublishWorkflow.JobBuilder.() -> Unit = { passthroughAllSecrets() },
// After:
publishSecrets: SetupAwareJobBuilder<PublishWorkflow>.() -> Unit = { passthroughAllSecrets() },
```

Full updated file:

```kotlin
package workflows.adapters.release

import config.DEFAULT_CHANGELOG_CONFIG
import config.SetupTool
import dsl.builder.AdapterWorkflow
import dsl.builder.SetupAwareJobBuilder
import dsl.builder.adapterWorkflow
import workflows.base.PublishWorkflow
import workflows.base.ReleaseWorkflow
import workflows.support.setup

object ReleaseAdapters {
    val app: AdapterWorkflow = adapterWorkflow("app-release.yml", "Application Release") {
        val changelogConfig = input("changelog-config", description = "Path to changelog configuration file", default = DEFAULT_CHANGELOG_CONFIG)
        val draft = input("draft", description = "Create release as draft (default true for apps)", default = true)

        ReleaseWorkflow.job("release") {
            ReleaseWorkflow.changelogConfig from changelogConfig
            ReleaseWorkflow.draft from draft
        }
    }

    val gradlePlugin = gradleRelease(
        "gradle-plugin-release.yml", "Gradle Plugin Release",
        "Gradle publish command (publishes to both Maven Central and Gradle Portal)",
    )

    val kotlinLibrary = gradleRelease(
        "kotlin-library-release.yml", "Kotlin Library Release",
        "Gradle publish command for Maven Central",
    ) {
        passthroughSecrets(PublishWorkflow.mavenSecrets)
    }

    private fun gradleRelease(
        fileName: String,
        name: String,
        publishDescription: String,
        publishSecrets: SetupAwareJobBuilder<PublishWorkflow>.() -> Unit = { passthroughAllSecrets() },
    ): AdapterWorkflow = adapterWorkflow(fileName, name) {
        val javaVersion = input(SetupTool.Gradle.versionKey, description = SetupTool.Gradle.versionDescription, default = SetupTool.Gradle.defaultVersion)
        val publishCommand = input("publish-command", description = publishDescription, required = true)
        val changelogConfig = input("changelog-config", description = "Path to changelog configuration file", default = DEFAULT_CHANGELOG_CONFIG)

        ReleaseWorkflow.job("release") {
            ReleaseWorkflow.changelogConfig from changelogConfig
        }

        PublishWorkflow.job("publish") {
            needs("release")
            setup(SetupTool.Gradle, javaVersion)
            PublishWorkflow.publishCommand from publishCommand
            publishSecrets()
        }
    }
}
```

Changes: added `import dsl.builder.SetupAwareJobBuilder`, changed `PublishWorkflow.JobBuilder.() -> Unit` → `SetupAwareJobBuilder<PublishWorkflow>.() -> Unit`.

- [ ] **Step 10: Build and verify**

Run: `cd /Users/dmitriimedakin/IdeaProjects/zenhelix/ci-workflows && ./gradlew build`
Expected: BUILD SUCCESSFUL

Run: `./gradlew run && git diff .github/workflows/`
Expected: No diff (YAML output unchanged)

- [ ] **Step 11: Commit**

```bash
git add src/main/kotlin/workflows/base/ src/main/kotlin/workflows/adapters/release/ReleaseAdapters.kt
git commit -m "refactor: eliminate custom JobBuilder classes, remove unused refInput properties"
```

---

### Task 4: Remove `refInput()` from `InputProperty.kt`

**Files:**
- Modify: `workflow-dsl/src/main/kotlin/dsl/builder/InputProperty.kt`

- [ ] **Step 1: Delete the `refInput` function**

In `workflow-dsl/src/main/kotlin/dsl/builder/InputProperty.kt`, remove line 4 (`import dsl.core.InputRef`) and line 21 (`fun refInput(...)`):

```kotlin
package dsl.builder

import dsl.core.WorkflowInput
import kotlin.reflect.KProperty

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
```

Changes: removed `import dsl.core.InputRef`, removed `fun refInput(...)` line.

- [ ] **Step 2: Build and verify**

Run: `cd /Users/dmitriimedakin/IdeaProjects/zenhelix/ci-workflows && ./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add workflow-dsl/src/main/kotlin/dsl/builder/InputProperty.kt
git commit -m "refactor: remove unused refInput() function from InputProperty.kt"
```

---

### Task 5: Consolidate `setup()` overloads to single method

**Files:**
- Modify: `src/main/kotlin/workflows/support/SetupSteps.kt`
- Modify: `src/main/kotlin/workflows/adapters/check/GradleCheck.kt`
- Modify: `src/main/kotlin/workflows/adapters/tag/CreateTagAdapters.kt`
- Modify: `src/main/kotlin/workflows/adapters/tag/ManualCreateTagAdapters.kt`
- Modify: `src/main/kotlin/workflows/adapters/release/ReleaseAdapters.kt`

- [ ] **Step 1: Replace three `setup()` overloads with one**

In `src/main/kotlin/workflows/support/SetupSteps.kt`, replace lines 27-37:

```kotlin
package workflows.support

import actions.SetupAction
import config.SetupTool
import dsl.capability.SetupCapableJobBuilder
import io.github.typesafegithub.workflows.dsl.JobBuilder

fun JobBuilder<*>.conditionalSetupSteps(
    tools: List<SetupTool> = SetupTool.entries,
    fetchDepth: String? = null,
) {
    tools.forEach { tool ->
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

fun SetupCapableJobBuilder.setup(tool: SetupTool, versionExpr: String) {
    applySetup(tool.id, tool.toParamsJson(versionExpr))
}
```

Changes: removed imports `dsl.core.MatrixRefExpr` and `dsl.core.WorkflowInput`, replaced three `setup()` overloads with one that takes `String`.

- [ ] **Step 2: Update GradleCheck.kt call site**

In `src/main/kotlin/workflows/adapters/check/GradleCheck.kt`, line 28:

```kotlin
// Before:
setup(SetupTool.Gradle, javaVersionMatrix.ref)
// After:
setup(SetupTool.Gradle, javaVersionMatrix.ref.expression)
```

- [ ] **Step 3: Update CreateTagAdapters.kt call site**

In `src/main/kotlin/workflows/adapters/tag/CreateTagAdapters.kt`, line 26:

```kotlin
// Before:
setup(eco.tool, version)
// After:
setup(eco.tool, version.ref.expression)
```

- [ ] **Step 4: Update ManualCreateTagAdapters.kt call site**

In `src/main/kotlin/workflows/adapters/tag/ManualCreateTagAdapters.kt`, line 26:

```kotlin
// Before:
setup(eco.tool, version)
// After:
setup(eco.tool, version.ref.expression)
```

- [ ] **Step 5: Update ReleaseAdapters.kt call site**

In `src/main/kotlin/workflows/adapters/release/ReleaseAdapters.kt`, line 50:

```kotlin
// Before:
setup(SetupTool.Gradle, javaVersion)
// After:
setup(SetupTool.Gradle, javaVersion.ref.expression)
```

- [ ] **Step 6: Build and verify**

Run: `cd /Users/dmitriimedakin/IdeaProjects/zenhelix/ci-workflows && ./gradlew build`
Expected: BUILD SUCCESSFUL

Run: `./gradlew run && git diff .github/workflows/`
Expected: No diff (YAML output unchanged)

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/workflows/support/SetupSteps.kt src/main/kotlin/workflows/adapters/
git commit -m "refactor: consolidate three setup() overloads into single String-based method"
```
