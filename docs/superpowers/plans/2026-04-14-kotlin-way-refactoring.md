# Kotlin-Way Refactoring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor ci-workflows project for better readability, consistency, and Kotlin conventions without changing generated YAML output.

**Architecture:** 6 sequential phases, each atomic and independently compilable. Phases proceed from build infrastructure (Version Catalog) through structural changes (split files, unify patterns) to cosmetic improvements (rename delegates, fix headers).

**Tech Stack:** Kotlin 2.3.20, Gradle (Kotlin DSL), github-workflows-kt 3.7.0, kaml, kotlinx-serialization

---

## File Structure

### Files to Create
- `gradle/libs.versions.toml` — Gradle Version Catalog
- `src/main/kotlin/workflows/definitions/CheckWorkflow.kt`
- `src/main/kotlin/workflows/definitions/ConventionalCommitCheckWorkflow.kt`
- `src/main/kotlin/workflows/definitions/CreateTagWorkflow.kt`
- `src/main/kotlin/workflows/definitions/ManualCreateTagWorkflow.kt`
- `src/main/kotlin/workflows/definitions/ReleaseWorkflow.kt`
- `src/main/kotlin/workflows/definitions/PublishWorkflow.kt`
- `src/main/kotlin/workflows/definitions/LabelerWorkflow.kt`
- `src/main/kotlin/workflows/definitions/AppDeployWorkflow.kt`
- `src/main/kotlin/workflows/adapters/tag/CreateTagAdapter.kt`
- `src/main/kotlin/workflows/adapters/tag/ManualCreateTagAdapter.kt`

### Files to Delete
- `src/main/kotlin/workflows/Workflows.kt`
- `src/main/kotlin/workflows/adapters/tag/GradleCreateTag.kt`
- `src/main/kotlin/workflows/adapters/tag/GoCreateTag.kt`
- `src/main/kotlin/workflows/adapters/tag/GradleManualCreateTag.kt`
- `src/main/kotlin/workflows/adapters/tag/GoManualCreateTag.kt`

### Files to Modify
- `build.gradle.kts` — use Version Catalog
- `workflow-dsl/build.gradle.kts` — use Version Catalog
- `settings.gradle.kts` — no change needed (Gradle auto-discovers `libs.versions.toml`)
- `workflow-dsl/src/main/kotlin/dsl/ReusableWorkflowJobBuilder.kt` — rename delegates
- `workflow-dsl/src/main/kotlin/dsl/AdapterWorkflow.kt` — fix header comment
- `src/main/kotlin/workflows/base/Check.kt` — update imports + sourceFile
- `src/main/kotlin/workflows/base/ConventionalCommitCheck.kt` — update imports + sourceFile
- `src/main/kotlin/workflows/base/CreateTag.kt` — update imports + sourceFile
- `src/main/kotlin/workflows/base/ManualCreateTag.kt` — update imports + sourceFile
- `src/main/kotlin/workflows/base/Release.kt` — update imports + sourceFile
- `src/main/kotlin/workflows/base/Publish.kt` — update imports + sourceFile
- `src/main/kotlin/workflows/base/Labeler.kt` — update imports + sourceFile
- `src/main/kotlin/workflows/base/AppDeploy.kt` — rewrite to use AppDeployWorkflow + update sourceFile
- `src/main/kotlin/workflows/WorkflowHelpers.kt` — update imports
- `src/main/kotlin/workflows/adapters/check/GradleCheck.kt` — update imports
- `src/main/kotlin/workflows/adapters/release/AppRelease.kt` — update imports
- `src/main/kotlin/workflows/adapters/release/GradlePluginRelease.kt` — update imports
- `src/main/kotlin/workflows/adapters/release/KotlinLibraryRelease.kt` — update imports
- `src/main/kotlin/generate/Generate.kt` — update imports + refactor tag adapter instantiation

---

## Task 1: Gradle Version Catalog

**Files:**
- Create: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts`
- Modify: `workflow-dsl/build.gradle.kts`

- [ ] **Step 1: Create `gradle/libs.versions.toml`**

```toml
[versions]
kotlin = "2.3.20"
github-workflows-kt = "3.7.0"
kaml = "0.104.0"
kotlinx-serialization-core = "1.11.0"

[libraries]
github-workflows-kt = { module = "io.github.typesafegithub:github-workflows-kt", version.ref = "github-workflows-kt" }
kaml = { module = "com.charleskorn.kaml:kaml", version.ref = "kaml" }
kotlinx-serialization-core = { module = "org.jetbrains.kotlinx:kotlinx-serialization-core", version.ref = "kotlinx-serialization-core" }

[plugins]
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

- [ ] **Step 2: Update root `build.gradle.kts`**

Replace the full file content with:

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

application {
    mainClass.set("generate.GenerateKt")
}

repositories {
    mavenCentral()
    maven("https://bindings.krzeminski.it")
}

dependencies {
    implementation(project(":workflow-dsl"))
    implementation(libs.github.workflows.kt)

    // JIT action bindings
    implementation("actions:checkout:v6")
    // mathieudutour:github-tag-action:v6 - not yet available in bindings.krzeminski.it registry
    implementation("mikepenz:release-changelog-builder-action:v6")
    implementation("softprops:action-gh-release:v2")
    implementation("actions:labeler:v6")
}
```

Note: JIT action bindings use a special repository format (`owner:action:version`) and cannot be moved to the Version Catalog.

- [ ] **Step 3: Update `workflow-dsl/build.gradle.kts`**

Replace the full file content with:

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.github.workflows.kt)
    implementation(libs.kaml)
    implementation(libs.kotlinx.serialization.core)
}
```

- [ ] **Step 4: Verify build compiles**

Run: `cd /Users/dmitriimedakin/IdeaProjects/zenhelix/ci-workflows && ./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml build.gradle.kts workflow-dsl/build.gradle.kts
git commit -m "refactor: introduce Gradle Version Catalog for dependency management"
```

---

## Task 2: Split `Workflows.kt` into `workflows/definitions/` package

**Files:**
- Delete: `src/main/kotlin/workflows/Workflows.kt`
- Create: 7 files in `src/main/kotlin/workflows/definitions/`
- Modify: all files that import from `workflows.CheckWorkflow`, `workflows.CreateTagWorkflow`, etc.

- [ ] **Step 1: Create `src/main/kotlin/workflows/definitions/CheckWorkflow.kt`**

```kotlin
package workflows.definitions

import dsl.ReusableWorkflowJobBuilder
import dsl.SetupConfigurable
import dsl.inputProp
import dsl.inputRefProp
import workflows.ProjectWorkflow

object CheckWorkflow : ProjectWorkflow("check.yml") {

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

    class JobBuilder : ReusableWorkflowJobBuilder(CheckWorkflow), SetupConfigurable {
        override var setupAction by inputProp(CheckWorkflow.setupAction)
        override var setupParams by inputProp(CheckWorkflow.setupParams)
        var checkCommand by inputRefProp(CheckWorkflow.checkCommand)
    }
}
```

- [ ] **Step 2: Create `src/main/kotlin/workflows/definitions/ConventionalCommitCheckWorkflow.kt`**

```kotlin
package workflows.definitions

import dsl.ReusableWorkflowJobBuilder
import dsl.inputRefProp
import workflows.ProjectWorkflow

object ConventionalCommitCheckWorkflow : ProjectWorkflow("conventional-commit-check.yml") {

    val allowedTypes = input(
        "allowed-types",
        description = "Comma-separated list of allowed commit types",
        default = "feat,fix,refactor,docs,test,chore,perf,ci"
    )

    class JobBuilder : ReusableWorkflowJobBuilder(ConventionalCommitCheckWorkflow) {
        var allowedTypes by inputRefProp(ConventionalCommitCheckWorkflow.allowedTypes)
    }
}
```

- [ ] **Step 3: Create `src/main/kotlin/workflows/definitions/CreateTagWorkflow.kt`**

```kotlin
package workflows.definitions

import config.DEFAULT_RELEASE_BRANCHES
import dsl.ReusableWorkflowJobBuilder
import dsl.SetupConfigurable
import dsl.inputProp
import dsl.inputRefProp
import workflows.ProjectWorkflow

object CreateTagWorkflow : ProjectWorkflow("create-tag.yml") {

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

    class JobBuilder : ReusableWorkflowJobBuilder(CreateTagWorkflow), SetupConfigurable {
        override var setupAction by inputProp(CreateTagWorkflow.setupAction)
        override var setupParams by inputProp(CreateTagWorkflow.setupParams)
        var checkCommand by inputRefProp(CreateTagWorkflow.checkCommand)
        var defaultBump by inputRefProp(CreateTagWorkflow.defaultBump)
        var tagPrefix by inputRefProp(CreateTagWorkflow.tagPrefix)
        var releaseBranches by inputRefProp(CreateTagWorkflow.releaseBranches)
    }
}
```

- [ ] **Step 4: Create `src/main/kotlin/workflows/definitions/ManualCreateTagWorkflow.kt`**

```kotlin
package workflows.definitions

import dsl.ReusableWorkflowJobBuilder
import dsl.SetupConfigurable
import dsl.inputProp
import dsl.inputRefProp
import workflows.ProjectWorkflow

object ManualCreateTagWorkflow : ProjectWorkflow("manual-create-tag.yml") {

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

    class JobBuilder : ReusableWorkflowJobBuilder(ManualCreateTagWorkflow), SetupConfigurable {
        var tagVersion by inputRefProp(ManualCreateTagWorkflow.tagVersion)
        var tagPrefix by inputRefProp(ManualCreateTagWorkflow.tagPrefix)
        override var setupAction by inputProp(ManualCreateTagWorkflow.setupAction)
        override var setupParams by inputProp(ManualCreateTagWorkflow.setupParams)
        var checkCommand by inputRefProp(ManualCreateTagWorkflow.checkCommand)
    }
}
```

- [ ] **Step 5: Create `src/main/kotlin/workflows/definitions/ReleaseWorkflow.kt`**

```kotlin
package workflows.definitions

import config.DEFAULT_CHANGELOG_CONFIG
import dsl.ReusableWorkflowJobBuilder
import dsl.inputRefProp
import workflows.ProjectWorkflow

object ReleaseWorkflow : ProjectWorkflow("release.yml") {

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

    class JobBuilder : ReusableWorkflowJobBuilder(ReleaseWorkflow) {
        var changelogConfig by inputRefProp(ReleaseWorkflow.changelogConfig)
        var draft by inputRefProp(ReleaseWorkflow.draft)
    }
}
```

- [ ] **Step 6: Create `src/main/kotlin/workflows/definitions/PublishWorkflow.kt`**

```kotlin
package workflows.definitions

import dsl.ReusableWorkflowJobBuilder
import dsl.SetupConfigurable
import dsl.inputProp
import dsl.inputRefProp
import workflows.ProjectWorkflow

object PublishWorkflow : ProjectWorkflow("publish.yml") {

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

    class JobBuilder : ReusableWorkflowJobBuilder(PublishWorkflow), SetupConfigurable {
        override var setupAction by inputProp(PublishWorkflow.setupAction)
        override var setupParams by inputProp(PublishWorkflow.setupParams)
        var publishCommand by inputRefProp(PublishWorkflow.publishCommand)
    }
}
```

- [ ] **Step 7: Create `src/main/kotlin/workflows/definitions/LabelerWorkflow.kt`**

```kotlin
package workflows.definitions

import dsl.ReusableWorkflowJobBuilder
import dsl.inputRefProp
import workflows.ProjectWorkflow

object LabelerWorkflow : ProjectWorkflow("labeler.yml") {

    val configPath = input(
        "config-path",
        description = "Path to labeler configuration file",
        default = ".github/labeler.yml"
    )

    class JobBuilder : ReusableWorkflowJobBuilder(LabelerWorkflow) {
        var configPath by inputRefProp(LabelerWorkflow.configPath)
    }
}
```

- [ ] **Step 8: Update imports in base workflow files**

Update each file to replace `workflows.XxxWorkflow` with `workflows.definitions.XxxWorkflow`:

**`src/main/kotlin/workflows/base/Check.kt`** — change:
- `import workflows.CheckWorkflow` → `import workflows.definitions.CheckWorkflow`

**`src/main/kotlin/workflows/base/ConventionalCommitCheck.kt`** — change:
- `import workflows.ConventionalCommitCheckWorkflow` → `import workflows.definitions.ConventionalCommitCheckWorkflow`

**`src/main/kotlin/workflows/base/CreateTag.kt`** — change:
- `import workflows.CreateTagWorkflow` → `import workflows.definitions.CreateTagWorkflow`

**`src/main/kotlin/workflows/base/ManualCreateTag.kt`** — change:
- `import workflows.ManualCreateTagWorkflow` → `import workflows.definitions.ManualCreateTagWorkflow`

**`src/main/kotlin/workflows/base/Release.kt`** — change:
- `import workflows.ReleaseWorkflow` → `import workflows.definitions.ReleaseWorkflow`

**`src/main/kotlin/workflows/base/Publish.kt`** — change:
- `import workflows.PublishWorkflow` → `import workflows.definitions.PublishWorkflow`

**`src/main/kotlin/workflows/base/Labeler.kt`** — change:
- `import workflows.LabelerWorkflow` → `import workflows.definitions.LabelerWorkflow`

- [ ] **Step 9: Update imports in adapter files**

**`src/main/kotlin/workflows/adapters/check/GradleCheck.kt`** — change:
- `import workflows.CheckWorkflow` → `import workflows.definitions.CheckWorkflow`
- `import workflows.ConventionalCommitCheckWorkflow` → `import workflows.definitions.ConventionalCommitCheckWorkflow`

**`src/main/kotlin/workflows/adapters/release/AppRelease.kt`** — change:
- `import workflows.ReleaseWorkflow` → `import workflows.definitions.ReleaseWorkflow`

**`src/main/kotlin/workflows/adapters/release/GradlePluginRelease.kt`** — change:
- `import workflows.PublishWorkflow` → `import workflows.definitions.PublishWorkflow`
- `import workflows.ReleaseWorkflow` → `import workflows.definitions.ReleaseWorkflow`

**`src/main/kotlin/workflows/adapters/release/KotlinLibraryRelease.kt`** — change:
- `import workflows.PublishWorkflow` → `import workflows.definitions.PublishWorkflow`
- `import workflows.ReleaseWorkflow` → `import workflows.definitions.ReleaseWorkflow`

**`src/main/kotlin/workflows/adapters/tag/GradleCreateTag.kt`** — change:
- `import workflows.CreateTagWorkflow` → `import workflows.definitions.CreateTagWorkflow`

**`src/main/kotlin/workflows/adapters/tag/GoCreateTag.kt`** — change:
- `import workflows.CreateTagWorkflow` → `import workflows.definitions.CreateTagWorkflow`

**`src/main/kotlin/workflows/adapters/tag/GradleManualCreateTag.kt`** — change:
- `import workflows.ManualCreateTagWorkflow` → `import workflows.definitions.ManualCreateTagWorkflow`

**`src/main/kotlin/workflows/adapters/tag/GoManualCreateTag.kt`** — change:
- `import workflows.ManualCreateTagWorkflow` → `import workflows.definitions.ManualCreateTagWorkflow`

- [ ] **Step 10: Delete `src/main/kotlin/workflows/Workflows.kt`**

- [ ] **Step 11: Verify build compiles**

Run: `cd /Users/dmitriimedakin/IdeaProjects/zenhelix/ci-workflows && ./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 12: Commit**

```bash
git add -A
git commit -m "refactor: split Workflows.kt into workflows/definitions/ package"
```

---

## Task 3: Create `AppDeployWorkflow` object

**Files:**
- Create: `src/main/kotlin/workflows/definitions/AppDeployWorkflow.kt`
- Modify: `src/main/kotlin/workflows/base/AppDeploy.kt`
- Modify: `src/main/kotlin/generate/Generate.kt`

- [ ] **Step 1: Create `src/main/kotlin/workflows/definitions/AppDeployWorkflow.kt`**

```kotlin
package workflows.definitions

import dsl.ReusableWorkflowJobBuilder
import dsl.SetupConfigurable
import dsl.inputProp
import dsl.inputRefProp
import workflows.ProjectWorkflow

object AppDeployWorkflow : ProjectWorkflow("app-deploy.yml") {

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
    val deployCommand = input(
        "deploy-command",
        description = "Command to run for deployment",
        required = true
    )
    val tag = input(
        "tag",
        description = "Tag/version to deploy (checked out at this ref)",
        required = true
    )

    class JobBuilder : ReusableWorkflowJobBuilder(AppDeployWorkflow), SetupConfigurable {
        override var setupAction by inputProp(AppDeployWorkflow.setupAction)
        override var setupParams by inputProp(AppDeployWorkflow.setupParams)
        var deployCommand by inputRefProp(AppDeployWorkflow.deployCommand)
        var tag by inputRefProp(AppDeployWorkflow.tag)
    }
}
```

- [ ] **Step 2: Rewrite `src/main/kotlin/workflows/base/AppDeploy.kt`**

Replace the full file content with:

```kotlin
package workflows.base

import workflows.conditionalSetupSteps
import workflows.definitions.AppDeployWorkflow
import io.github.typesafegithub.workflows.domain.Mode
import io.github.typesafegithub.workflows.domain.Permission
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import java.io.File

fun generateAppDeploy() {
    workflow(
        name = "Application Deploy",
        on = listOf(
            WorkflowCall(inputs = AppDeployWorkflow.inputs),
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
                command = "git checkout \"${AppDeployWorkflow.tag.ref.expression}\"",
            )
            run(
                name = "Deploy",
                command = AppDeployWorkflow.deployCommand.ref.expression,
            )
        }
    }
}
```

- [ ] **Step 3: Update `src/main/kotlin/generate/Generate.kt`**

Change the `generateAppDeploy` call — remove `outputDir` argument:
- `generateAppDeploy(outputDir)` → `generateAppDeploy()`

Also remove the now-unused import if the IDE flags it (the `import workflows.base.generateAppDeploy` stays, just the call changes).

- [ ] **Step 4: Verify build compiles**

Run: `cd /Users/dmitriimedakin/IdeaProjects/zenhelix/ci-workflows && ./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor: create AppDeployWorkflow object for consistency with other base workflows"
```

---

## Task 4: Generalize tag adapters

**Files:**
- Create: `src/main/kotlin/workflows/adapters/tag/CreateTagAdapter.kt`
- Create: `src/main/kotlin/workflows/adapters/tag/ManualCreateTagAdapter.kt`
- Delete: `src/main/kotlin/workflows/adapters/tag/GradleCreateTag.kt`
- Delete: `src/main/kotlin/workflows/adapters/tag/GoCreateTag.kt`
- Delete: `src/main/kotlin/workflows/adapters/tag/GradleManualCreateTag.kt`
- Delete: `src/main/kotlin/workflows/adapters/tag/GoManualCreateTag.kt`
- Modify: `src/main/kotlin/generate/Generate.kt`

- [ ] **Step 1: Create `src/main/kotlin/workflows/adapters/tag/CreateTagAdapter.kt`**

```kotlin
package workflows.adapters.tag

import config.DEFAULT_RELEASE_BRANCHES
import config.SetupTool
import dsl.ReusableWorkflowJobDef
import dsl.reusableJob
import workflows.ProjectAdapterWorkflow
import workflows.definitions.CreateTagWorkflow
import workflows.setup

class CreateTagAdapter(
    fileName: String,
    override val workflowName: String,
    private val tool: SetupTool,
    private val defaultVersion: String,
    private val commandInputName: String,
    private val defaultCommand: String,
    private val defaultTagPrefix: String,
) : ProjectAdapterWorkflow(fileName) {

    val version = input("${tool.versionKey}", description = "${tool.id.replaceFirstChar { it.uppercase() }} version to use", default = defaultVersion)
    val checkCommand = input(commandInputName, description = "Validation command to run before tagging", default = defaultCommand)
    val defaultBump = input("default-bump", description = "Default version bump type (major, minor, patch)", default = "patch")
    val tagPrefix = input("tag-prefix", description = "Prefix for the tag", default = defaultTagPrefix)
    val releaseBranches = input("release-branches", description = "Comma-separated branch patterns for releases", default = DEFAULT_RELEASE_BRANCHES)

    override fun jobs(): List<ReusableWorkflowJobDef> = listOf(
        reusableJob(id = "create-tag", uses = CreateTagWorkflow, CreateTagWorkflow::JobBuilder) {
            setup(tool, version.ref.expression)
            checkCommand = this@CreateTagAdapter.checkCommand.ref
            defaultBump = this@CreateTagAdapter.defaultBump.ref
            tagPrefix = this@CreateTagAdapter.tagPrefix.ref
            releaseBranches = this@CreateTagAdapter.releaseBranches.ref
            passthroughAllSecrets()
        },
    )
}
```

- [ ] **Step 2: Create `src/main/kotlin/workflows/adapters/tag/ManualCreateTagAdapter.kt`**

```kotlin
package workflows.adapters.tag

import config.SetupTool
import dsl.ReusableWorkflowJobDef
import dsl.reusableJob
import workflows.ProjectAdapterWorkflow
import workflows.definitions.ManualCreateTagWorkflow
import workflows.setup

class ManualCreateTagAdapter(
    fileName: String,
    override val workflowName: String,
    private val tool: SetupTool,
    private val defaultVersion: String,
    private val commandInputName: String,
    private val defaultCommand: String,
    private val defaultTagPrefix: String,
) : ProjectAdapterWorkflow(fileName) {

    val tagVersion = input("tag-version", description = "Version to tag (e.g. 1.2.3)", required = true)
    val version = input("${tool.versionKey}", description = "${tool.id.replaceFirstChar { it.uppercase() }} version to use", default = defaultVersion)
    val checkCommand = input(commandInputName, description = "Validation command to run before tagging", default = defaultCommand)
    val tagPrefix = input("tag-prefix", description = "Prefix for the tag", default = defaultTagPrefix)

    override fun jobs(): List<ReusableWorkflowJobDef> = listOf(
        reusableJob(id = "manual-tag", uses = ManualCreateTagWorkflow, ManualCreateTagWorkflow::JobBuilder) {
            tagVersion = this@ManualCreateTagAdapter.tagVersion.ref
            tagPrefix = this@ManualCreateTagAdapter.tagPrefix.ref
            setup(tool, this@ManualCreateTagAdapter.version.ref.expression)
            checkCommand = this@ManualCreateTagAdapter.checkCommand.ref
            passthroughAllSecrets()
        },
    )
}
```

- [ ] **Step 3: Update `src/main/kotlin/generate/Generate.kt`**

Replace the full file content with:

```kotlin
package generate

import config.DEFAULT_GO_VERSION
import config.DEFAULT_JAVA_VERSION
import config.SetupTool
import workflows.adapters.check.GradleCheckAdapter
import workflows.adapters.release.AppReleaseAdapter
import workflows.adapters.release.GradlePluginReleaseAdapter
import workflows.adapters.release.KotlinLibraryReleaseAdapter
import workflows.adapters.tag.CreateTagAdapter
import workflows.adapters.tag.ManualCreateTagAdapter
import workflows.base.generateAppDeploy
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

    generateCheck()
    generateConventionalCommitCheck()
    generateCreateTag()
    generateManualCreateTag()
    generateRelease()
    generatePublish()
    generateLabeler()
    generateAppDeploy()

    // Adapters
    GradleCheckAdapter("app-check.yml", "Application Check").generate(outputDir)
    GradleCheckAdapter("gradle-check.yml", "Gradle Check").generate(outputDir)
    GradleCheckAdapter("gradle-plugin-check.yml", "Gradle Plugin Check").generate(outputDir)
    GradleCheckAdapter("kotlin-library-check.yml", "Kotlin Library Check").generate(outputDir)
    AppReleaseAdapter.generate(outputDir)
    GradlePluginReleaseAdapter.generate(outputDir)
    KotlinLibraryReleaseAdapter.generate(outputDir)
    CreateTagAdapter("gradle-create-tag.yml", "Gradle Create Tag", SetupTool.Gradle, DEFAULT_JAVA_VERSION, "gradle-command", "./gradlew check", "").generate(outputDir)
    CreateTagAdapter("go-create-tag.yml", "Go Create Tag", SetupTool.Go, DEFAULT_GO_VERSION, "check-command", "make test", "v").generate(outputDir)
    ManualCreateTagAdapter("gradle-manual-create-tag.yml", "Gradle Manual Create Tag", SetupTool.Gradle, DEFAULT_JAVA_VERSION, "gradle-command", "./gradlew check", "").generate(outputDir)
    ManualCreateTagAdapter("go-manual-create-tag.yml", "Go Manual Create Tag", SetupTool.Go, DEFAULT_GO_VERSION, "check-command", "make test", "v").generate(outputDir)
}
```

- [ ] **Step 4: Delete old tag adapter files**

Delete:
- `src/main/kotlin/workflows/adapters/tag/GradleCreateTag.kt`
- `src/main/kotlin/workflows/adapters/tag/GoCreateTag.kt`
- `src/main/kotlin/workflows/adapters/tag/GradleManualCreateTag.kt`
- `src/main/kotlin/workflows/adapters/tag/GoManualCreateTag.kt`

- [ ] **Step 5: Verify build compiles**

Run: `cd /Users/dmitriimedakin/IdeaProjects/zenhelix/ci-workflows && ./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Verify generated YAML is identical**

Run: `cd /Users/dmitriimedakin/IdeaProjects/zenhelix/ci-workflows && ./gradlew run && git diff .github/workflows/`
Expected: no diff in `gradle-create-tag.yml`, `go-create-tag.yml`, `gradle-manual-create-tag.yml`, `go-manual-create-tag.yml`

Note: The `GradleCreateTag` adapter used input name `gradle-command` while `GoCreateTag` used `check-command`. The `commandInputName` parameter preserves each adapter's original input name to avoid breaking changes for consumers.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "refactor: generalize tag adapters into CreateTagAdapter and ManualCreateTagAdapter"
```

---

## Task 5: Rename delegate functions

**Files:**
- Modify: `workflow-dsl/src/main/kotlin/dsl/ReusableWorkflowJobBuilder.kt`
- Modify: all files in `src/main/kotlin/workflows/definitions/`

- [ ] **Step 1: Update `workflow-dsl/src/main/kotlin/dsl/ReusableWorkflowJobBuilder.kt`**

Rename classes and functions:
- `InputProperty` → `StringInputProperty`
- `inputProp` → `stringInput`
- `InputRefProperty` → `RefInputProperty`
- `inputRefProp` → `refInput`

The full updated top section of the file (lines 1-28):

```kotlin
package dsl

import dsl.yaml.JobYaml
import dsl.yaml.NeedsYaml
import dsl.yaml.StrategyYaml
import kotlin.reflect.KProperty

class StringInputProperty(private val input: WorkflowInput) {
    operator fun getValue(builder: ReusableWorkflowJobBuilder, property: KProperty<*>): String =
        builder.getInput(input)

    operator fun setValue(builder: ReusableWorkflowJobBuilder, property: KProperty<*>, value: String) {
        builder.setInput(input, value)
    }
}

fun stringInput(input: WorkflowInput) = StringInputProperty(input)

class RefInputProperty(private val input: WorkflowInput) {
    operator fun getValue(builder: ReusableWorkflowJobBuilder, property: KProperty<*>): InputRef =
        InputRef(builder.getInput(input))

    operator fun setValue(builder: ReusableWorkflowJobBuilder, property: KProperty<*>, value: InputRef) {
        builder.setInput(input, value)
    }
}

fun refInput(input: WorkflowInput) = RefInputProperty(input)
```

The rest of the file (from `abstract class ReusableWorkflowJobBuilder` onward) stays unchanged.

- [ ] **Step 2: Update imports and usages in all `workflows/definitions/` files**

In every file under `src/main/kotlin/workflows/definitions/`, replace:
- `import dsl.inputProp` → `import dsl.stringInput`
- `import dsl.inputRefProp` → `import dsl.refInput`
- `by inputProp(` → `by stringInput(`
- `by inputRefProp(` → `by refInput(`

Files affected:
- `CheckWorkflow.kt` — has both `inputProp` and `inputRefProp`
- `ConventionalCommitCheckWorkflow.kt` — has `inputRefProp`
- `CreateTagWorkflow.kt` — has both
- `ManualCreateTagWorkflow.kt` — has both
- `ReleaseWorkflow.kt` — has `inputRefProp`
- `PublishWorkflow.kt` — has both
- `LabelerWorkflow.kt` — has `inputRefProp`
- `AppDeployWorkflow.kt` — has both

- [ ] **Step 3: Verify build compiles**

Run: `cd /Users/dmitriimedakin/IdeaProjects/zenhelix/ci-workflows && ./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor: rename inputProp/inputRefProp to stringInput/refInput for clarity"
```

---

## Task 6: Fix header comments in generated YAML

**Files:**
- Modify: `workflow-dsl/src/main/kotlin/dsl/AdapterWorkflow.kt`
- Modify: all 8 files in `src/main/kotlin/workflows/base/`

- [ ] **Step 1: Update header in `workflow-dsl/src/main/kotlin/dsl/AdapterWorkflow.kt`**

In the `generate()` method, replace the header `buildString` block (lines 37-40):

Old:
```kotlin
        val slug = fileName.removeSuffix(".yml")
        val header = buildString {
            appendLine("# This file was generated using Kotlin DSL (.github/workflow-src/$slug.main.kts).")
            appendLine("# If you want to modify the workflow, please change the Kotlin file and regenerate this YAML file.")
            append("# Generated with https://github.com/typesafegithub/github-workflows-kt")
        }
```

New:
```kotlin
        val header = buildString {
            appendLine("# This file was generated using Kotlin DSL (src/main/kotlin/).")
            appendLine("# If you want to modify the workflow, please change the Kotlin source and regenerate this YAML file.")
            append("# Generated with https://github.com/typesafegithub/github-workflows-kt")
        }
```

Also remove the now-unused `val slug` line.

- [ ] **Step 2: Update `sourceFile` in all base workflow files**

The `sourceFile` parameter in `workflow()` is used by `github-workflows-kt` to generate a similar header comment. All 8 base workflow files reference the old `.github/workflow-src/*.main.kts` path. Update each:

**`src/main/kotlin/workflows/base/Check.kt`:**
- `sourceFile = File(".github/workflow-src/check.main.kts")` → `sourceFile = File("src/main/kotlin/workflows/base/Check.kt")`

**`src/main/kotlin/workflows/base/ConventionalCommitCheck.kt`:**
- `sourceFile = File(".github/workflow-src/conventional-commit-check.main.kts")` → `sourceFile = File("src/main/kotlin/workflows/base/ConventionalCommitCheck.kt")`

**`src/main/kotlin/workflows/base/CreateTag.kt`:**
- `sourceFile = File(".github/workflow-src/create-tag.main.kts")` → `sourceFile = File("src/main/kotlin/workflows/base/CreateTag.kt")`

**`src/main/kotlin/workflows/base/ManualCreateTag.kt`:**
- `sourceFile = File(".github/workflow-src/manual-create-tag.main.kts")` → `sourceFile = File("src/main/kotlin/workflows/base/ManualCreateTag.kt")`

**`src/main/kotlin/workflows/base/Release.kt`:**
- `sourceFile = File(".github/workflow-src/release.main.kts")` → `sourceFile = File("src/main/kotlin/workflows/base/Release.kt")`

**`src/main/kotlin/workflows/base/Publish.kt`:**
- `sourceFile = File(".github/workflow-src/publish.main.kts")` → `sourceFile = File("src/main/kotlin/workflows/base/Publish.kt")`

**`src/main/kotlin/workflows/base/Labeler.kt`:**
- `sourceFile = File(".github/workflow-src/labeler.main.kts")` → `sourceFile = File("src/main/kotlin/workflows/base/Labeler.kt")`

**`src/main/kotlin/workflows/base/AppDeploy.kt`:**
- `sourceFile = File(".github/workflow-src/app-deploy.main.kts")` → `sourceFile = File("src/main/kotlin/workflows/base/AppDeploy.kt")`

- [ ] **Step 3: Verify build compiles**

Run: `cd /Users/dmitriimedakin/IdeaProjects/zenhelix/ci-workflows && ./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Regenerate YAML and verify**

Run: `cd /Users/dmitriimedakin/IdeaProjects/zenhelix/ci-workflows && ./gradlew run`

Check that:
- Adapter YAML files now have `# This file was generated using Kotlin DSL (src/main/kotlin/).` in the header
- Base workflow YAML files now reference `src/main/kotlin/workflows/base/Xxx.kt` in their header

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor: update header comments in generated YAML to reflect actual source paths"
```
