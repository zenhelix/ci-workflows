# Kotlin Idioms Refactoring — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Kotlin DSL code more concise and idiomatic without changing generated YAML output.

**Architecture:** Pure refactoring — consolidate overloads, add extension properties, extract shared functions, use functional chains. No new modules or interfaces.

**Tech Stack:** Kotlin 2.3.20, github-workflows-kt 3.7.0, kaml 0.104.0, Gradle 9.4.1

---

## Prerequisite: Capture YAML Baseline

Before any code changes, save the current generated YAML so we can diff after each task.

- [ ] **Step 1: Generate and snapshot baseline YAML**

```bash
cd /Users/dmitriimedakin/IdeaProjects/zenhelix/ci-workflows
./gradlew clean run
mkdir -p /tmp/ci-workflows-baseline
cp .github/workflows/*.yml /tmp/ci-workflows-baseline/
```

This baseline is used in every task's verification step.

---

### Task 1: Unify `input()` overloads (9 -> 5 methods)

**Files:**
- Modify: `workflow-dsl/src/main/kotlin/dsl/core/WorkflowInput.kt` (add `InputDefault` companion factories)
- Modify: `workflow-dsl/src/main/kotlin/dsl/core/InputRegistry.kt` (collapse 3 methods into 1)
- Modify: `workflow-dsl/src/main/kotlin/dsl/core/ReusableWorkflow.kt` (collapse 3 methods into 2)
- Modify: `workflow-dsl/src/main/kotlin/dsl/builder/AdapterWorkflowBuilder.kt` (collapse 3 methods into 2)

- [ ] **Step 1: Add `InputDefault` companion factory methods**

In `workflow-dsl/src/main/kotlin/dsl/core/WorkflowInput.kt`, add a companion to `InputDefault`:

```kotlin
sealed interface InputDefault {
    val rawValue: Any

    data class StringDefault(val value: String) : InputDefault {
        override val rawValue get() = value
    }
    data class BooleanDefault(val value: Boolean) : InputDefault {
        override val rawValue get() = value
    }

    companion object {
        operator fun invoke(value: String): InputDefault = StringDefault(value)
        operator fun invoke(value: Boolean): InputDefault = BooleanDefault(value)
    }
}
```

- [ ] **Step 2: Collapse `InputRegistry` to one method**

Replace the three `input()` methods in `workflow-dsl/src/main/kotlin/dsl/core/InputRegistry.kt` with:

```kotlin
class InputRegistry {
    private val _inputs = linkedMapOf<String, WorkflowInputDef>()

    val inputs: Map<String, WorkflowInputDef> get() = _inputs

    fun input(
        name: String,
        description: String,
        required: Boolean = false,
        default: InputDefault? = null,
    ): WorkflowInput {
        _inputs[name] = WorkflowInputDef(
            name = name,
            description = description,
            type = if (default is InputDefault.BooleanDefault) InputType.Boolean else InputType.Text,
            required = required,
            default = default,
        )
        return WorkflowInput(name)
    }
}
```

- [ ] **Step 3: Collapse `ReusableWorkflow.input()` to two thin wrappers**

In `workflow-dsl/src/main/kotlin/dsl/core/ReusableWorkflow.kt`, replace the three `input()` methods (lines 13-31) with:

```kotlin
    protected fun input(
        name: String,
        description: String,
        required: Boolean = false,
    ): WorkflowInput = inputRegistry.input(name, description, required)

    protected fun input(
        name: String,
        description: String,
        required: Boolean = false,
        default: String,
    ): WorkflowInput = inputRegistry.input(name, description, required, InputDefault(default))

    protected fun input(
        name: String,
        description: String,
        required: Boolean = false,
        default: Boolean,
    ): WorkflowInput = inputRegistry.input(name, description, required, InputDefault(default))
```

Wait — this is still 3 methods. The spec says "thin wrapper overloads (default: String and default: Boolean)" plus the no-default version. That's still 3 in `ReusableWorkflow`. The savings come from `InputRegistry` going from 3 to 1. Keep all 3 here since they delegate to the single core method.

Actually, re-reading the spec: "ReusableWorkflow and AdapterWorkflowBuilder keep thin wrapper overloads" — so these stay at 3 each, but now they're one-liners delegating to `InputRegistry`'s single method. The total goes from 9 distinct implementations to 1 implementation + 6 one-line delegates. That's the real win — no duplicated logic.

Keep the three methods in `ReusableWorkflow` as-is (they already delegate). Just update the `default: String` and `default: Boolean` overloads to wrap:

```kotlin
    protected fun input(
        name: String,
        description: String,
        required: Boolean = false,
        default: String,
    ): WorkflowInput = inputRegistry.input(name, description, required, InputDefault(default))

    protected fun input(
        name: String,
        description: String,
        required: Boolean = false,
        default: Boolean,
    ): WorkflowInput = inputRegistry.input(name, description, required, InputDefault(default))
```

The no-default overload stays unchanged:
```kotlin
    protected fun input(
        name: String,
        description: String,
        required: Boolean = false,
    ): WorkflowInput = inputRegistry.input(name, description, required)
```

- [ ] **Step 4: Collapse `AdapterWorkflowBuilder.input()` the same way**

In `workflow-dsl/src/main/kotlin/dsl/builder/AdapterWorkflowBuilder.kt`, update the `default: String` and `default: Boolean` overloads to wrap with `InputDefault(...)`:

```kotlin
    fun input(
        name: String,
        description: String,
        required: Boolean = false,
        default: String,
    ): WorkflowInput = inputRegistry.input(name, description, required = required, default = InputDefault(default))

    fun input(
        name: String,
        description: String,
        required: Boolean = false,
        default: Boolean,
    ): WorkflowInput = inputRegistry.input(name, description, required = required, default = InputDefault(default))
```

The no-default overload stays unchanged.

- [ ] **Step 5: Build and verify**

```bash
cd /Users/dmitriimedakin/IdeaProjects/zenhelix/ci-workflows
./gradlew clean run
diff -r /tmp/ci-workflows-baseline .github/workflows/
```

Expected: build succeeds, no YAML diff.

- [ ] **Step 6: Commit**

```bash
git add workflow-dsl/src/main/kotlin/dsl/core/WorkflowInput.kt \
      workflow-dsl/src/main/kotlin/dsl/core/InputRegistry.kt \
      workflow-dsl/src/main/kotlin/dsl/core/ReusableWorkflow.kt \
      workflow-dsl/src/main/kotlin/dsl/builder/AdapterWorkflowBuilder.kt
git commit -m "refactor: unify input() overloads via InputDefault companion factories"
```

---

### Task 2: Add `expr` extension properties for WorkflowInput / WorkflowSecret

**Files:**
- Modify: `workflow-dsl/src/main/kotlin/dsl/core/WorkflowInput.kt` (add extension properties)
- Modify: `workflow-dsl/src/main/kotlin/dsl/builder/ReusableWorkflowJobBuilder.kt` (use `.expr` in `from`)
- Modify: `src/main/kotlin/workflows/base/CheckWorkflow.kt`
- Modify: `src/main/kotlin/workflows/base/CreateTagWorkflow.kt`
- Modify: `src/main/kotlin/workflows/base/ManualCreateTagWorkflow.kt`
- Modify: `src/main/kotlin/workflows/base/PublishWorkflow.kt`
- Modify: `src/main/kotlin/workflows/base/ReleaseWorkflow.kt`
- Modify: `src/main/kotlin/workflows/base/AppDeployWorkflow.kt`
- Modify: `src/main/kotlin/workflows/base/ConventionalCommitCheckWorkflow.kt`
- Modify: `src/main/kotlin/workflows/base/LabelerWorkflow.kt`
- Modify: `src/main/kotlin/workflows/support/SetupSteps.kt`

- [ ] **Step 1: Add extension properties in WorkflowInput.kt**

Append to the bottom of `workflow-dsl/src/main/kotlin/dsl/core/WorkflowInput.kt`:

```kotlin
val WorkflowInput.expr: String get() = ref.expression
val WorkflowSecret.expr: String get() = ref.expression
```

- [ ] **Step 2: Replace all `.ref.expression` with `.expr` in workflow-dsl module**

In `workflow-dsl/src/main/kotlin/dsl/builder/ReusableWorkflowJobBuilder.kt`, update the `from` infix function (line 55-57):

```kotlin
    infix fun WorkflowInput.from(source: WorkflowInput) {
        setInput(this, source.expr)
    }
```

And update `passthroughSecrets` (lines 43-46) and `passthroughAllSecrets` (lines 49-53):

```kotlin
    fun passthroughSecrets(vararg secrets: WorkflowSecret) {
        passthroughSecrets(secrets.toList())
    }

    fun passthroughSecrets(secrets: List<WorkflowSecret>) {
        secrets.forEach { secret ->
            secretsMap[secret.name] = secret.expr
        }
    }

    fun passthroughAllSecrets() {
        workflow.secretObjects.forEach { secret ->
            secretsMap[secret.name] = secret.expr
        }
    }
```

- [ ] **Step 3: Replace `.ref.expression` in all base workflow files**

**CheckWorkflow.kt** — line 23:
```kotlin
run(name = "Run check", command = checkCommand.expr)
```

**CreateTagWorkflow.kt** — lines 38-49:
```kotlin
            uses(
                name = "Generate App Token",
                action = CreateAppTokenAction(appId = appId.expr, appPrivateKey = appPrivateKey.expr),
                id = "app-token",
            )
            uses(
                name = "Bump version and push tag",
                action = GithubTagAction(
                    githubToken = "\${{ steps.app-token.outputs.token }}",
                    defaultBump = defaultBump.expr,
                    tagPrefix = tagPrefix.expr,
                    releaseBranches = releaseBranches.expr,
                ),
            )
```
Also line 36:
```kotlin
            run(name = "Run validation", command = checkCommand.expr)
```

**ManualCreateTagWorkflow.kt** — line 43:
```kotlin
            run(name = "Run validation", command = checkCommand.expr)
```
Line 46:
```kotlin
                action = CreateAppTokenAction(appId = appId.expr, appPrivateKey = appPrivateKey.expr),
```

**PublishWorkflow.kt** — lines 42-49 (env map):
```kotlin
                env = linkedMapOf(
                    "GRADLE_PUBLISH_KEY" to gradlePublishKey.expr,
                    "GRADLE_PUBLISH_SECRET" to gradlePublishSecret.expr,
                    "ORG_GRADLE_PROJECT_signingKeyId" to mavenSonatypeSigningKeyId.expr,
                    "ORG_GRADLE_PROJECT_signingPublicKey" to mavenSonatypeSigningPubKeyAsciiArmored.expr,
                    "ORG_GRADLE_PROJECT_signingKey" to mavenSonatypeSigningKeyAsciiArmored.expr,
                    "ORG_GRADLE_PROJECT_signingPassword" to mavenSonatypeSigningPassword.expr,
                    "MAVEN_SONATYPE_USERNAME" to mavenSonatypeUsername.expr,
                    "MAVEN_SONATYPE_TOKEN" to mavenSonatypeToken.expr,
                ),
```
Also line 40:
```kotlin
                command = publishCommand.expr,
```

**ReleaseWorkflow.kt** — line 32:
```kotlin
                    configuration_Untyped = changelogConfig.expr,
```
Line 44:
```kotlin
                    draft_Untyped = draft.expr,
```

**AppDeployWorkflow.kt** — line 24:
```kotlin
            run(name = "Checkout tag", command = "git checkout \"${tag.expr}\"")
```
Line 25:
```kotlin
            run(name = "Deploy", command = deployCommand.expr)
```

**ConventionalCommitCheckWorkflow.kt** — line 33:
```kotlin
                    "ALLOWED_TYPES" to allowedTypes.expr,
```

**LabelerWorkflow.kt** — line 29:
```kotlin
                    configurationPath_Untyped = configPath.expr,
```

- [ ] **Step 4: Replace `.ref.expression` in SetupSteps.kt**

`src/main/kotlin/workflows/support/SetupSteps.kt` does NOT use `.ref.expression` directly (it uses `tool.defaultVersion` and string templates). No changes needed.

- [ ] **Step 5: Build and verify**

```bash
cd /Users/dmitriimedakin/IdeaProjects/zenhelix/ci-workflows
./gradlew clean run
diff -r /tmp/ci-workflows-baseline .github/workflows/
```

Expected: build succeeds, no YAML diff.

- [ ] **Step 6: Commit**

```bash
git add workflow-dsl/src/main/kotlin/dsl/core/WorkflowInput.kt \
      workflow-dsl/src/main/kotlin/dsl/builder/ReusableWorkflowJobBuilder.kt \
      src/main/kotlin/workflows/base/ \
      src/main/kotlin/workflows/support/SetupSteps.kt
git commit -m "refactor: add .expr extension property, replace .ref.expression throughout"
```

---

### Task 3: Extract `setupJob()` / `simpleJob()` extensions, remove 8 boilerplate functions

**Files:**
- Modify: `workflow-dsl/src/main/kotlin/dsl/capability/SetupCapability.kt` (add `setupJob` extension)
- Modify: `workflow-dsl/src/main/kotlin/dsl/core/ReusableWorkflow.kt` (add `simpleJob` extension)
- Modify: `workflow-dsl/src/main/kotlin/dsl/builder/ReusableWorkflowJobBuilder.kt` (make `build` public or keep `@PublishedApi internal`)
- Modify: `src/main/kotlin/workflows/base/CheckWorkflow.kt` (remove `job()`)
- Modify: `src/main/kotlin/workflows/base/CreateTagWorkflow.kt` (remove `job()`)
- Modify: `src/main/kotlin/workflows/base/ManualCreateTagWorkflow.kt` (remove `job()`)
- Modify: `src/main/kotlin/workflows/base/PublishWorkflow.kt` (remove `job()`)
- Modify: `src/main/kotlin/workflows/base/AppDeployWorkflow.kt` (remove `job()`)
- Modify: `src/main/kotlin/workflows/base/ReleaseWorkflow.kt` (remove `job()`)
- Modify: `src/main/kotlin/workflows/base/ConventionalCommitCheckWorkflow.kt` (remove `job()`)
- Modify: `src/main/kotlin/workflows/base/LabelerWorkflow.kt` (remove `job()`)
- Modify: `src/main/kotlin/workflows/adapters/check/GradleCheck.kt` (use `setupJob`/`simpleJob`)
- Modify: `src/main/kotlin/workflows/adapters/tag/CreateTagAdapters.kt` (use `setupJob`)
- Modify: `src/main/kotlin/workflows/adapters/tag/ManualCreateTagAdapters.kt` (use `setupJob`)
- Modify: `src/main/kotlin/workflows/adapters/release/ReleaseAdapters.kt` (use `setupJob`/`simpleJob`)

- [ ] **Step 1: Add `setupJob` extension to `SetupCapability.kt`**

Append to `workflow-dsl/src/main/kotlin/dsl/capability/SetupCapability.kt`:

```kotlin
import dsl.builder.AdapterWorkflowBuilder
import dsl.builder.SetupAwareJobBuilder
import dsl.core.ReusableWorkflow

context(builder: AdapterWorkflowBuilder)
inline fun <reified W> W.setupJob(
    id: String,
    noinline block: SetupAwareJobBuilder<W>.() -> Unit = {},
) where W : ReusableWorkflow, W : SetupCapability =
    job(id, { SetupAwareJobBuilder(this@W) }, block)
```

- [ ] **Step 2: Add `simpleJob` extension to `ReusableWorkflow.kt`**

Append to the bottom of `workflow-dsl/src/main/kotlin/dsl/core/ReusableWorkflow.kt` (outside the class):

```kotlin
context(builder: AdapterWorkflowBuilder)
fun ReusableWorkflow.simpleJob(
    id: String,
    block: ReusableWorkflowJobBuilder.() -> Unit = {},
) = job(id, { ReusableWorkflowJobBuilder(this@ReusableWorkflow) }, block)
```

Add missing import at top:
```kotlin
import dsl.builder.AdapterWorkflowBuilder
```
(Already imported — verify it's there.)

- [ ] **Step 3: Remove `job()` functions from all 8 workflow objects**

In each of these files, delete the 3-line `context(...) fun job(...)` block:

**CheckWorkflow.kt** — delete lines 16-18:
```kotlin
    context(builder: AdapterWorkflowBuilder)
    fun job(id: String, block: SetupAwareJobBuilder<CheckWorkflow>.() -> Unit = {}) =
        job(id, { SetupAwareJobBuilder(this@CheckWorkflow) }, block)
```
Also remove now-unused imports: `dsl.builder.AdapterWorkflowBuilder`, `dsl.builder.SetupAwareJobBuilder`.

**CreateTagWorkflow.kt** — delete lines 29-31. Remove unused imports.

**ManualCreateTagWorkflow.kt** — delete lines 26-28. Remove unused imports.

**PublishWorkflow.kt** — delete lines 31-33. Remove unused imports.

**AppDeployWorkflow.kt** — delete lines 17-19. Remove unused imports.

**ReleaseWorkflow.kt** — delete lines 22-24. Remove unused import `dsl.builder.AdapterWorkflowBuilder`. Keep `dsl.builder.ReusableWorkflowJobBuilder` import (still used in `implementation()`? No — it's only used in the deleted `job()`. Actually `ReusableWorkflowJobBuilder` is not used elsewhere in ReleaseWorkflow. Remove it.)

**ConventionalCommitCheckWorkflow.kt** — delete lines 13-14. Remove unused imports.

**LabelerWorkflow.kt** — delete lines 18-20. Remove unused imports.

- [ ] **Step 4: Update all adapter call-sites**

**GradleCheck.kt** — change:
```kotlin
        ConventionalCommitCheckWorkflow.job("conventional-commit")
```
to:
```kotlin
        ConventionalCommitCheckWorkflow.simpleJob("conventional-commit")
```

And:
```kotlin
        CheckWorkflow.job("check") {
```
to:
```kotlin
        CheckWorkflow.setupJob("check") {
```

Add imports:
```kotlin
import dsl.capability.setupJob
import dsl.core.simpleJob
```

**CreateTagAdapters.kt** — change:
```kotlin
        CreateTagWorkflow.job("create-tag") {
```
to:
```kotlin
        CreateTagWorkflow.setupJob("create-tag") {
```

Add import:
```kotlin
import dsl.capability.setupJob
```

**ManualCreateTagAdapters.kt** — change:
```kotlin
        ManualCreateTagWorkflow.job("manual-tag") {
```
to:
```kotlin
        ManualCreateTagWorkflow.setupJob("manual-tag") {
```

Add import:
```kotlin
import dsl.capability.setupJob
```

**ReleaseAdapters.kt** — change:
```kotlin
        ReleaseWorkflow.job("release") {
```
to (3 occurrences):
```kotlin
        ReleaseWorkflow.simpleJob("release") {
```

And:
```kotlin
        PublishWorkflow.job("publish") {
```
to:
```kotlin
        PublishWorkflow.setupJob("publish") {
```

Add imports:
```kotlin
import dsl.capability.setupJob
import dsl.core.simpleJob
```

- [ ] **Step 5: Build and verify**

```bash
cd /Users/dmitriimedakin/IdeaProjects/zenhelix/ci-workflows
./gradlew clean run
diff -r /tmp/ci-workflows-baseline .github/workflows/
```

Expected: build succeeds, no YAML diff.

- [ ] **Step 6: Commit**

```bash
git add workflow-dsl/src/main/kotlin/dsl/capability/SetupCapability.kt \
      workflow-dsl/src/main/kotlin/dsl/core/ReusableWorkflow.kt \
      src/main/kotlin/workflows/base/ \
      src/main/kotlin/workflows/adapters/
git commit -m "refactor: extract setupJob/simpleJob extensions, remove 8 boilerplate job() functions"
```

---

### Task 4: Remove `setInput(InputRef)` overload and List `passthroughSecrets` overload

**Files:**
- Modify: `workflow-dsl/src/main/kotlin/dsl/builder/ReusableWorkflowJobBuilder.kt`
- Modify: `src/main/kotlin/workflows/adapters/release/ReleaseAdapters.kt`

- [ ] **Step 1: Remove `setInput(InputRef)` overload**

In `workflow-dsl/src/main/kotlin/dsl/builder/ReusableWorkflowJobBuilder.kt`, delete lines 27-29:

```kotlin
    fun setInput(input: WorkflowInput, value: InputRef) {
        withMap[input.name] = value.expression
    }
```

The `from` infix was already updated in Task 2 to use `.expr` (String), so no further changes needed.

Remove unused import `dsl.core.InputRef` if it's no longer referenced.

- [ ] **Step 2: Collapse `passthroughSecrets` to vararg-only**

Replace the two methods (lines 39-47 after step 1 deletions) with one:

```kotlin
    fun passthroughSecrets(vararg secrets: WorkflowSecret) {
        secrets.forEach { secret ->
            secretsMap[secret.name] = secret.expr
        }
    }
```

- [ ] **Step 3: Update ReleaseAdapters.kt call-site**

In `src/main/kotlin/workflows/adapters/release/ReleaseAdapters.kt`, line 33 change:

```kotlin
        passthroughSecrets(PublishWorkflow.mavenSecrets)
```
to:
```kotlin
        passthroughSecrets(*PublishWorkflow.mavenSecrets.toTypedArray())
```

- [ ] **Step 4: Build and verify**

```bash
cd /Users/dmitriimedakin/IdeaProjects/zenhelix/ci-workflows
./gradlew clean run
diff -r /tmp/ci-workflows-baseline .github/workflows/
```

Expected: build succeeds, no YAML diff.

- [ ] **Step 5: Commit**

```bash
git add workflow-dsl/src/main/kotlin/dsl/builder/ReusableWorkflowJobBuilder.kt \
      src/main/kotlin/workflows/adapters/release/ReleaseAdapters.kt
git commit -m "refactor: remove setInput(InputRef) and List passthroughSecrets overloads"
```

---

### Task 5: Functional chain in `collectSecretsFromJobs`

**Files:**
- Modify: `workflow-dsl/src/main/kotlin/dsl/builder/AdapterWorkflow.kt`

- [ ] **Step 1: Replace imperative loop with functional chain**

In `workflow-dsl/src/main/kotlin/dsl/builder/AdapterWorkflow.kt`, replace the `collectSecretsFromJobs` method (lines 53-64):

```kotlin
    private fun collectSecretsFromJobs(jobDefs: List<ReusableWorkflowJobDef>): Map<String, SecretYaml>? =
        jobDefs.flatMap { job ->
            val workflowSecrets = job.uses.secrets
            job.secrets.keys.map { name ->
                name to SecretYaml(
                    description = workflowSecrets[name]?.description ?: name,
                    required = workflowSecrets[name]?.required ?: true,
                )
            }
        }.toMap()
         .takeIf { it.isNotEmpty() }
```

- [ ] **Step 2: Build and verify**

```bash
cd /Users/dmitriimedakin/IdeaProjects/zenhelix/ci-workflows
./gradlew clean run
diff -r /tmp/ci-workflows-baseline .github/workflows/
```

Expected: build succeeds, no YAML diff.

- [ ] **Step 3: Commit**

```bash
git add workflow-dsl/src/main/kotlin/dsl/builder/AdapterWorkflow.kt
git commit -m "refactor: use functional chain in collectSecretsFromJobs"
```

---

### Task 6: Private scope for ecosystem adapter functions

**Files:**
- Modify: `src/main/kotlin/workflows/adapters/tag/CreateTagAdapters.kt`
- Modify: `src/main/kotlin/workflows/adapters/tag/ManualCreateTagAdapters.kt`

- [ ] **Step 1: Move `ecosystemCreateTag` inside object**

In `src/main/kotlin/workflows/adapters/tag/CreateTagAdapters.kt`, move the top-level function inside the object as a private method. Replace the entire file content with:

```kotlin
package workflows.adapters.tag

import config.DEFAULT_RELEASE_BRANCHES
import config.EcosystemConfig
import config.GO
import config.GRADLE
import dsl.builder.AdapterWorkflow
import dsl.builder.adapterWorkflow
import dsl.capability.setupJob
import workflows.base.CreateTagWorkflow
import workflows.support.setup

object CreateTagAdapters {
    val gradle = ecosystemCreateTag("gradle-create-tag.yml", "Gradle Create Tag", GRADLE)
    val go = ecosystemCreateTag("go-create-tag.yml", "Go Create Tag", GO)

    private fun ecosystemCreateTag(fileName: String, name: String, eco: EcosystemConfig): AdapterWorkflow =
        adapterWorkflow(fileName, name) {
            val version = input(eco.tool.versionKey, description = eco.tool.versionDescription, default = eco.tool.defaultVersion)
            val checkCommand = input(eco.checkCommandName, description = eco.checkCommandDescription, default = eco.defaultCheckCommand)
            val defaultBump = input("default-bump", description = "Default version bump type (major, minor, patch)", default = "patch")
            val tagPrefix = input("tag-prefix", description = "Prefix for the tag", default = eco.defaultTagPrefix)
            val releaseBranches = input("release-branches", description = "Comma-separated branch patterns for releases", default = DEFAULT_RELEASE_BRANCHES)

            CreateTagWorkflow.setupJob("create-tag") {
                setup(eco.tool, version.ref.expression)
                CreateTagWorkflow.checkCommand from checkCommand
                CreateTagWorkflow.defaultBump from defaultBump
                CreateTagWorkflow.tagPrefix from tagPrefix
                CreateTagWorkflow.releaseBranches from releaseBranches
                passthroughAllSecrets()
            }
        }
}
```

Note: `version.ref.expression` here — if Task 2 was applied, this should already be using a different pattern. But `version` is a `WorkflowInput` defined locally, so it should use the `expr` extension from Task 2. Update to `version.expr` if Task 2 is already applied. The adapter call-sites in the original code use `version.ref.expression` — these should become `version.expr` after Task 2.

- [ ] **Step 2: Move `ecosystemManualCreateTag` inside object**

In `src/main/kotlin/workflows/adapters/tag/ManualCreateTagAdapters.kt`, replace the entire file:

```kotlin
package workflows.adapters.tag

import config.EcosystemConfig
import config.GO
import config.GRADLE
import dsl.builder.AdapterWorkflow
import dsl.builder.adapterWorkflow
import dsl.capability.setupJob
import workflows.base.ManualCreateTagWorkflow
import workflows.support.setup

object ManualCreateTagAdapters {
    val gradle = ecosystemManualCreateTag("gradle-manual-create-tag.yml", "Gradle Manual Create Tag", GRADLE)
    val go = ecosystemManualCreateTag("go-manual-create-tag.yml", "Go Manual Create Tag", GO)

    private fun ecosystemManualCreateTag(fileName: String, name: String, eco: EcosystemConfig): AdapterWorkflow =
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

- [ ] **Step 3: Build and verify**

```bash
cd /Users/dmitriimedakin/IdeaProjects/zenhelix/ci-workflows
./gradlew clean run
diff -r /tmp/ci-workflows-baseline .github/workflows/
```

Expected: build succeeds, no YAML diff.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/workflows/adapters/tag/CreateTagAdapters.kt \
      src/main/kotlin/workflows/adapters/tag/ManualCreateTagAdapters.kt
git commit -m "refactor: move ecosystem adapter functions to private scope inside objects"
```

---

## Final Verification

- [ ] **Full clean rebuild and YAML diff**

```bash
cd /Users/dmitriimedakin/IdeaProjects/zenhelix/ci-workflows
./gradlew clean run
diff -r /tmp/ci-workflows-baseline .github/workflows/
```

Expected: zero differences. All 19 YAML files identical to baseline.
