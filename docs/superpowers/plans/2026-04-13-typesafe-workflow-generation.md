# Type-Safe Workflow Generation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make GitHub Actions workflow generation fully type-safe by leveraging `github-workflows-kt` JIT action bindings and building custom DSL abstractions for gaps the library doesn't cover.

**Architecture:** The project generates GitHub Actions YAML from Kotlin using `github-workflows-kt:3.7.0`. There are two layers: base workflows (with real steps) and adapter workflows (only calling base workflows via reusable workflow jobs). We improve type safety at both layers while keeping generated YAML identical.

**Tech Stack:** Kotlin 2.3.20, Gradle, `io.github.typesafegithub:github-workflows-kt:3.7.0`, JIT action bindings via `bindings.krzeminski.it`

**Spec:** `docs/superpowers/specs/2026-04-13-typesafe-workflow-generation-design.md`

---

## Acceptance Criterion

After every task that modifies generation code, run `./gradlew run` and verify `git diff .github/workflows/` shows **no changes** to generated YAML. The only exception is Task 13 (migrate adapters to AdapterWorkflow) where minor whitespace/quoting differences are acceptable as long as the YAML is semantically equivalent.

---

## Task 1: Snapshot Current YAML Output

Save a reference copy of all generated YAML before making any changes. This will be used to verify output equivalence throughout.

**Files:**
- Read: `.github/workflows/*.yml` (19 files)

- [ ] **Step 1: Generate current YAML and save checksums**

```bash
cd /Users/dmitriimedakin/IdeaProjects/zenhelix/ci-workflows
./gradlew run
find .github/workflows -name '*.yml' -not -name 'verify-workflows.yml' | sort | xargs md5sum > /tmp/ci-workflows-yaml-checksums.txt
cat /tmp/ci-workflows-yaml-checksums.txt
```

- [ ] **Step 2: Verify no uncommitted changes**

```bash
git diff --stat .github/workflows/
```

Expected: clean — no changes.

- [ ] **Step 3: Commit (skip — nothing to commit, this is a verification step)**

---

## Task 2: Add `.ref` to WorkflowInput and WorkflowSecret (Spec Section 2)

Add compile-time safe expression references. This is the simplest change with the widest impact.

**Files:**
- Modify: `src/main/kotlin/dsl/ReusableWorkflow.kt:79-81`

- [ ] **Step 1: Add `.ref` property to WorkflowInput**

In `src/main/kotlin/dsl/ReusableWorkflow.kt`, replace:

```kotlin
class WorkflowInput(val name: String)
```

with:

```kotlin
class WorkflowInput(val name: String) {
    /** Generates "\${{ inputs.<name> }}" for use in workflow expressions */
    val ref: String get() = "\${{ inputs.$name }}"
}
```

- [ ] **Step 2: Add `.ref` property to WorkflowSecret**

In the same file, replace:

```kotlin
class WorkflowSecret(val name: String)
```

with:

```kotlin
class WorkflowSecret(val name: String) {
    /** Generates "\${{ secrets.<name> }}" for use in workflow expressions */
    val ref: String get() = "\${{ secrets.$name }}"
}
```

- [ ] **Step 3: Build to verify compilation**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL (no usages changed yet, just new properties added).

- [ ] **Step 4: Run generation and verify no YAML changes**

```bash
./gradlew run
git diff .github/workflows/
```

Expected: no changes.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/dsl/ReusableWorkflow.kt
git commit -m "feat: add .ref property to WorkflowInput and WorkflowSecret"
```

---

## Task 3: Add `inputRef()` Helper for Adapter Workflows (Spec Section 4 — partial)

Add a helper function for referencing adapter-level inputs. Placed in the dsl package alongside other helpers.

**Files:**
- Modify: `src/main/kotlin/dsl/WorkflowHelpers.kt`

- [ ] **Step 1: Add `inputRef()` function**

At the top of `src/main/kotlin/dsl/WorkflowHelpers.kt` (after the package and imports), add:

```kotlin
/** Reference an input of the current workflow: generates "\${{ inputs.<name> }}" */
fun inputRef(name: String) = "\${{ inputs.$name }}"
```

- [ ] **Step 2: Build to verify**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/dsl/WorkflowHelpers.kt
git commit -m "feat: add inputRef() helper for adapter workflow input references"
```

---

## Task 4: Create SetupTool Sealed Class (Spec Section 3)

Replace string-based setup action selection with a sealed class hierarchy.

**Files:**
- Create: `src/main/kotlin/config/SetupTool.kt`

- [ ] **Step 1: Create SetupTool.kt**

Create `src/main/kotlin/config/SetupTool.kt`:

```kotlin
package config

sealed class SetupTool(
    val actionName: String,
    val versionKey: String,
    val defaultVersion: String,
) {
    val id: String get() = actionName.removePrefix("setup-")

    abstract fun toParamsJson(versionExpr: String): String

    data object Gradle : SetupTool("setup-gradle", "java-version", DEFAULT_JAVA_VERSION) {
        override fun toParamsJson(versionExpr: String) =
            """{"java-version": "$versionExpr"}"""
    }

    data object Go : SetupTool("setup-go", "go-version", DEFAULT_GO_VERSION) {
        override fun toParamsJson(versionExpr: String) =
            """{"go-version": "$versionExpr"}"""
    }

    data object Python : SetupTool("setup-python", "python-version", DEFAULT_PYTHON_VERSION) {
        override fun toParamsJson(versionExpr: String) =
            """{"python-version": "$versionExpr"}"""
    }
}
```

- [ ] **Step 2: Build to verify**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/config/SetupTool.kt
git commit -m "feat: add SetupTool sealed class for typed setup action selection"
```

---

## Task 5: Refactor `conditionalSetupSteps()` to Use SetupTool (Spec Section 3)

Replace the 3 copy-pasted `uses()` blocks with a loop over sealed subclasses.

**Files:**
- Modify: `src/main/kotlin/dsl/WorkflowHelpers.kt:7-32`

- [ ] **Step 1: Replace conditionalSetupSteps implementation**

In `src/main/kotlin/dsl/WorkflowHelpers.kt`, replace the entire `conditionalSetupSteps` function (lines 7-32):

```kotlin
fun JobBuilder<*>.conditionalSetupSteps(fetchDepth: String? = null) {
    uses(
        name = "Setup Gradle",
        action = SetupAction(
            "setup-gradle", "java-version",
            "\${{ fromJson(inputs.setup-params).java-version || '17' }}", fetchDepth
        ),
        condition = "inputs.setup-action == 'gradle'",
    )
    uses(
        name = "Setup Go",
        action = SetupAction(
            "setup-go", "go-version",
            "\${{ fromJson(inputs.setup-params).go-version || '1.22' }}", fetchDepth
        ),
        condition = "inputs.setup-action == 'go'",
    )
    uses(
        name = "Setup Python",
        action = SetupAction(
            "setup-python", "python-version",
            "\${{ fromJson(inputs.setup-params).python-version || '3.12' }}", fetchDepth
        ),
        condition = "inputs.setup-action == 'python'",
    )
}
```

with:

```kotlin
fun JobBuilder<*>.conditionalSetupSteps(fetchDepth: String? = null) {
    SetupTool::class.sealedSubclasses.mapNotNull { it.objectInstance }.forEach { tool ->
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

Add the import at the top of the file:

```kotlin
import config.SetupTool
```

- [ ] **Step 2: Build and verify**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run generation and verify YAML is identical**

```bash
./gradlew run
git diff .github/workflows/
```

Expected: no changes. If `sealedSubclasses` ordering differs from the hardcoded order (Gradle, Go, Python), the step order in generated YAML may change. In that case, check whether the sealed subclasses iteration order matches declaration order in Kotlin (it does — Kotlin reflects sealed subclasses in declaration order). If there are differences, the YAML steps will be in a different order which is functionally equivalent but will show a diff. Verify the steps are the same content, just potentially reordered.

**Important:** If the order does differ, you can fix it by switching to an explicit list:

```kotlin
val allTools = listOf(SetupTool.Gradle, SetupTool.Go, SetupTool.Python)
allTools.forEach { tool -> ... }
```

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/dsl/WorkflowHelpers.kt
git commit -m "refactor: use SetupTool sealed class in conditionalSetupSteps"
```

---

## Task 6: Create CommonInputs Object (Spec Section 4)

Centralize adapter input definitions to eliminate duplication.

**Files:**
- Create: `src/main/kotlin/config/CommonInputs.kt`

- [ ] **Step 1: Create CommonInputs.kt**

Create `src/main/kotlin/config/CommonInputs.kt`:

```kotlin
package config

import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall

object CommonInputs {
    fun javaVersion(default: String = DEFAULT_JAVA_VERSION) =
        "java-version" to WorkflowCall.Input("JDK version to use", false, WorkflowCall.Type.String, default)

    fun javaVersions() =
        "java-versions" to WorkflowCall.Input(
            "JSON array of JDK versions for matrix build (overrides java-version)",
            false, WorkflowCall.Type.String, "")

    fun gradleCommand(default: String = "./gradlew check") =
        "gradle-command" to WorkflowCall.Input("Gradle check command", false, WorkflowCall.Type.String, default)

    fun publishCommand(description: String = "Gradle publish command") =
        "publish-command" to WorkflowCall.Input(description, true, WorkflowCall.Type.String)

    fun changelogConfig() =
        "changelog-config" to WorkflowCall.Input(
            "Path to changelog configuration file", false, WorkflowCall.Type.String, DEFAULT_CHANGELOG_CONFIG)

    fun goVersion(default: String = DEFAULT_GO_VERSION) =
        "go-version" to WorkflowCall.Input("Go version to use", false, WorkflowCall.Type.String, default)

    fun tagVersion() =
        "tag-version" to WorkflowCall.Input("Version to tag (e.g. 1.2.3)", true, WorkflowCall.Type.String)

    fun defaultBump() =
        "default-bump" to WorkflowCall.Input(
            "Default version bump type (major, minor, patch)", false, WorkflowCall.Type.String, "patch")

    fun tagPrefix(default: String = "") =
        "tag-prefix" to WorkflowCall.Input("Prefix for the tag", false, WorkflowCall.Type.String, default)

    fun releaseBranches() =
        "release-branches" to WorkflowCall.Input(
            "Comma-separated branch patterns for releases", false, WorkflowCall.Type.String, DEFAULT_RELEASE_BRANCHES)

    fun checkCommand(description: String = "Validation command", default: String) =
        "check-command" to WorkflowCall.Input(description, false, WorkflowCall.Type.String, default)

    fun deployCommand() =
        "deploy-command" to WorkflowCall.Input("Command to run for deployment", true, WorkflowCall.Type.String)

    fun setupAction() =
        "setup-action" to WorkflowCall.Input("Setup action to use: gradle, go, python", true, WorkflowCall.Type.String)

    fun setupParams() =
        "setup-params" to WorkflowCall.Input(
            "JSON object with setup parameters (e.g. {\"java-version\": \"21\"})", false, WorkflowCall.Type.String, "{}")

    fun tag() =
        "tag" to WorkflowCall.Input("Tag/version to deploy (checked out at this ref)", true, WorkflowCall.Type.String)
}
```

- [ ] **Step 2: Build to verify**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/config/CommonInputs.kt
git commit -m "feat: add CommonInputs object for reusable adapter input definitions"
```

---

## Task 7: Add `.passthrough()` Extension (Spec Section 6)

Replace manual `*_PASSTHROUGH` constants with an extension function.

**Files:**
- Modify: `src/main/kotlin/config/Secrets.kt`

- [ ] **Step 1: Add extension function and remove passthrough constants**

In `src/main/kotlin/config/Secrets.kt`, replace the entire file content:

```kotlin
package config

import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall

fun Map<String, WorkflowCall.Secret>.passthrough(): Map<String, String> =
    keys.associateWith { "\${{ secrets.$it }}" }

val MAVEN_SONATYPE_SECRETS = mapOf(
    "MAVEN_SONATYPE_USERNAME" to WorkflowCall.Secret("Maven Central (Sonatype) username", true),
    "MAVEN_SONATYPE_TOKEN" to WorkflowCall.Secret("Maven Central (Sonatype) token", true),
    "MAVEN_SONATYPE_SIGNING_KEY_ID" to WorkflowCall.Secret("GPG signing key ID", true),
    "MAVEN_SONATYPE_SIGNING_PUB_KEY_ASCII_ARMORED" to WorkflowCall.Secret("GPG signing public key (ASCII armored)", true),
    "MAVEN_SONATYPE_SIGNING_KEY_ASCII_ARMORED" to WorkflowCall.Secret("GPG signing private key (ASCII armored)", true),
    "MAVEN_SONATYPE_SIGNING_PASSWORD" to WorkflowCall.Secret("GPG signing key passphrase", true),
)

val GRADLE_PORTAL_SECRETS = mapOf(
    "GRADLE_PUBLISH_KEY" to WorkflowCall.Secret("Gradle Plugin Portal publish key", true),
    "GRADLE_PUBLISH_SECRET" to WorkflowCall.Secret("Gradle Plugin Portal publish secret", true),
)

val APP_SECRETS = mapOf(
    "app-id" to WorkflowCall.Secret("GitHub App ID for generating commit token", true),
    "app-private-key" to WorkflowCall.Secret("GitHub App private key for generating commit token", true),
)
```

- [ ] **Step 2: Fix compilation errors — update all usages of removed constants**

The following files reference `*_PASSTHROUGH` constants and must be updated:

**`src/main/kotlin/workflows/adapters/release/GradlePluginRelease.kt`** — replace:
- `import config.GRADLE_PORTAL_SECRETS_PASSTHROUGH` → remove
- `import config.MAVEN_SONATYPE_SECRETS_PASSTHROUGH` → remove
- `secrets(MAVEN_SONATYPE_SECRETS_PASSTHROUGH + GRADLE_PORTAL_SECRETS_PASSTHROUGH)` → `secrets(MAVEN_SONATYPE_SECRETS.passthrough() + GRADLE_PORTAL_SECRETS.passthrough())`

**`src/main/kotlin/workflows/adapters/release/KotlinLibraryRelease.kt`** — replace:
- `import config.MAVEN_SONATYPE_SECRETS_PASSTHROUGH` → remove
- `secrets(MAVEN_SONATYPE_SECRETS_PASSTHROUGH)` → `secrets(MAVEN_SONATYPE_SECRETS.passthrough())`

**`src/main/kotlin/workflows/adapters/tag/GradleCreateTag.kt`** — replace:
- `import config.APP_SECRETS_PASSTHROUGH` → remove
- `secrets(APP_SECRETS_PASSTHROUGH)` → `secrets(APP_SECRETS.passthrough())`

**`src/main/kotlin/workflows/adapters/tag/GradleManualCreateTag.kt`** — replace:
- `import config.APP_SECRETS_PASSTHROUGH` → remove
- `secrets(APP_SECRETS_PASSTHROUGH)` → `secrets(APP_SECRETS.passthrough())`

**`src/main/kotlin/workflows/adapters/tag/GoCreateTag.kt`** — replace:
- `import config.APP_SECRETS_PASSTHROUGH` → remove
- `secrets(APP_SECRETS_PASSTHROUGH)` → `secrets(APP_SECRETS.passthrough())`

**`src/main/kotlin/workflows/adapters/tag/GoManualCreateTag.kt`** — replace:
- `import config.APP_SECRETS_PASSTHROUGH` → remove
- `secrets(APP_SECRETS_PASSTHROUGH)` → `secrets(APP_SECRETS.passthrough())`

- [ ] **Step 3: Build and verify**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run generation and verify YAML is identical**

```bash
./gradlew run
git diff .github/workflows/
```

Expected: no changes.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/config/Secrets.kt src/main/kotlin/workflows/adapters/
git commit -m "refactor: replace *_PASSTHROUGH constants with .passthrough() extension"
```

---

## Task 8: Add JIT Action Bindings to Build (Spec Section 1 — partial)

Add the bindings Maven repository and action dependencies. Don't change any source files yet.

**Files:**
- Modify: `build.gradle.kts`

- [ ] **Step 1: Update build.gradle.kts**

Replace the entire `build.gradle.kts`:

```kotlin
plugins {
    kotlin("jvm") version "2.3.20"
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
    implementation("io.github.typesafegithub:github-workflows-kt:3.7.0")

    // JIT action bindings
    implementation("actions:checkout:v6")
    implementation("mathieudutour:github-tag-action:v6")
    implementation("mikepenz:release-changelog-builder-action:v6")
    implementation("softprops:action-gh-release:v2")
    implementation("actions:labeler:v6")
}
```

- [ ] **Step 2: Sync Gradle and verify dependencies resolve**

```bash
./gradlew dependencies --configuration runtimeClasspath 2>&1 | grep -E "(actions:|mathieudutour:|mikepenz:|softprops:)"
```

Expected: all 5 action bindings resolve successfully.

- [ ] **Step 3: Build to verify**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add build.gradle.kts
git commit -m "feat: add JIT action binding dependencies from bindings.krzeminski.it"
```

---

## Task 9: Replace Manual Action Classes with JIT Bindings (Spec Section 1)

Replace the 5 hand-written action classes with imports from JIT bindings and update all usage sites.

**Files:**
- Modify: `src/main/kotlin/actions/Actions.kt` — remove 5 classes
- Modify: `src/main/kotlin/workflows/base/CreateTag.kt` — update action imports/usages
- Modify: `src/main/kotlin/workflows/base/Release.kt` — update action imports/usages
- Modify: `src/main/kotlin/workflows/base/Labeler.kt` — update action imports/usages

**Important context:** JIT bindings use `_Untyped` suffixed parameters for string expressions. The exact parameter names depend on the generated bindings. Before implementing, inspect the available classes:

- [ ] **Step 1: Discover JIT binding class names and parameter names**

Create a temporary Kotlin file to check imports compile, or use IDE autocompletion. The expected classes are:

```kotlin
import io.github.typesafegithub.workflows.actions.actions.Checkout           // actions/checkout@v6
import io.github.typesafegithub.workflows.actions.actions.Labeler            // actions/labeler@v6
import io.github.typesafegithub.workflows.actions.mathieudutour.GithubTagAction  // mathieudutour/github-tag-action@v6
import io.github.typesafegithub.workflows.actions.mikepenz.ReleaseChangelogBuilderAction  // mikepenz/release-changelog-builder-action@v6
import io.github.typesafegithub.workflows.actions.softprops.ActionGhRelease  // softprops/action-gh-release@v2
```

Verify these by running:

```bash
./gradlew compileKotlin 2>&1 | head -5
```

with a test import. If class names differ, adjust accordingly. **Check the generated binding sources** (in Gradle cache or via IDE) to find exact constructor parameter names and their `_Untyped` variants.

- [ ] **Step 2: Trim Actions.kt to only local action wrappers**

Replace `src/main/kotlin/actions/Actions.kt` with:

```kotlin
package actions

import config.localAction
import io.github.typesafegithub.workflows.domain.actions.Action

class SetupAction(
    private val actionName: String,
    private val versionKey: String,
    private val version: String,
    private val fetchDepth: String? = null,
) : Action<Action.Outputs>() {
    override val usesString = localAction(actionName)
    override fun toYamlArguments() = linkedMapOf(
        versionKey to version,
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

    class CreateAppTokenOutputs(stepId: String) : Outputs(stepId) {
        val token: String get() = get("token")
    }
}
```

- [ ] **Step 3: Update CreateTag.kt**

In `src/main/kotlin/workflows/base/CreateTag.kt`:

Remove import:
```kotlin
import actions.GithubTagAction
```

Add import:
```kotlin
import io.github.typesafegithub.workflows.actions.mathieudutour.GithubTagAction
```

Replace the `GithubTagAction` usage (the constructor parameters will change to match JIT binding names). The old code:
```kotlin
uses(
    name = "Bump version and push tag",
    action = GithubTagAction(
        githubToken = "\${{ steps.app-token.outputs.token }}",
        defaultBump = "\${{ inputs.default-bump }}",
        tagPrefix = "\${{ inputs.tag-prefix }}",
        releaseBranches = "\${{ inputs.release-branches }}",
    ),
)
```

Replace with JIT binding equivalent (parameter names use `_Untyped` suffix for expressions):
```kotlin
uses(
    name = "Bump version and push tag",
    action = GithubTagAction(
        githubToken_Untyped = "\${{ steps.app-token.outputs.token }}",
        defaultBump_Untyped = "\${{ inputs.default-bump }}",
        tagPrefix_Untyped = "\${{ inputs.tag-prefix }}",
        releaseBranches_Untyped = "\${{ inputs.release-branches }}",
    ),
)
```

**Note:** The exact `_Untyped` parameter names may differ. Check the generated class. If the JIT binding doesn't have `_Untyped` variants for all params (they might all be `String` already), use the regular parameter names.

- [ ] **Step 4: Update Release.kt**

In `src/main/kotlin/workflows/base/Release.kt`:

Remove imports:
```kotlin
import actions.CheckoutAction
import actions.GhReleaseAction
import actions.ReleaseChangelogBuilderAction
```

Add imports:
```kotlin
import io.github.typesafegithub.workflows.actions.actions.Checkout
import io.github.typesafegithub.workflows.actions.mikepenz.ReleaseChangelogBuilderAction
import io.github.typesafegithub.workflows.actions.softprops.ActionGhRelease
```

Replace action usages:

Old `CheckoutAction(fetchDepth = "0")` → New `Checkout(fetchDepth_Untyped = "0")`

Old `ReleaseChangelogBuilderAction(configuration = "...", toTag = "...")` → New `ReleaseChangelogBuilderAction(configuration_Untyped = "...", toTag_Untyped = "...")`

Old `GhReleaseAction(body = "...", name = "...", tagName = "...", draft = "...")` → New `ActionGhRelease(body_Untyped = "...", name_Untyped = "...", tagName_Untyped = "...", draft_Untyped = "...")`

**Note:** Again, verify exact parameter names from the JIT bindings.

- [ ] **Step 5: Update Labeler.kt**

In `src/main/kotlin/workflows/base/Labeler.kt`:

Remove import:
```kotlin
import actions.LabelerAction
```

Add import:
```kotlin
import io.github.typesafegithub.workflows.actions.actions.Labeler
```

Replace:
```kotlin
uses(
    name = "Label PR based on file paths",
    action = LabelerAction(
        repoToken = "\${{ secrets.GITHUB_TOKEN }}",
        configurationPath = "\${{ inputs.config-path }}",
        syncLabels = "true",
    ),
)
```

with JIT binding equivalent:
```kotlin
uses(
    name = "Label PR based on file paths",
    action = Labeler(
        repoToken_Untyped = "\${{ secrets.GITHUB_TOKEN }}",
        configurationPath_Untyped = "\${{ inputs.config-path }}",
        syncLabels_Untyped = "true",
    ),
)
```

- [ ] **Step 6: Update ManualCreateTag.kt (if it uses CheckoutAction — it doesn't, but verify)**

Check `src/main/kotlin/workflows/base/ManualCreateTag.kt` — it uses `CreateAppTokenAction` (local, kept) but no external actions. No changes needed.

- [ ] **Step 7: Build and verify**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL. If compilation fails, check the JIT binding parameter names and adjust.

- [ ] **Step 8: Run generation and verify YAML**

```bash
./gradlew run
git diff .github/workflows/
```

Expected: no changes, OR minor differences in action version strings (e.g., `@v6` instead of `@v6.2` for `github-tag-action`). If there are version differences, this is expected — the JIT bindings use the major version tag. Verify the YAML is functionally equivalent.

- [ ] **Step 9: Commit**

```bash
git add src/main/kotlin/actions/Actions.kt src/main/kotlin/workflows/base/
git commit -m "refactor: replace manual action classes with JIT action bindings"
```

---

## Task 10: Apply `.ref` in Base Workflows (Spec Section 2)

Replace raw string expressions with `.ref` in all base workflow files.

**Files:**
- Modify: `src/main/kotlin/workflows/base/Check.kt`
- Modify: `src/main/kotlin/workflows/base/CreateTag.kt`
- Modify: `src/main/kotlin/workflows/base/Release.kt`
- Modify: `src/main/kotlin/workflows/base/Publish.kt`
- Modify: `src/main/kotlin/workflows/base/ManualCreateTag.kt`
- Modify: `src/main/kotlin/workflows/base/Labeler.kt`
- Modify: `src/main/kotlin/workflows/base/ConventionalCommitCheck.kt`

- [ ] **Step 1: Update Check.kt**

In `src/main/kotlin/workflows/base/Check.kt`, replace:
```kotlin
command = "\${{ inputs.check-command }}",
```
with:
```kotlin
command = CheckWorkflow.checkCommand.ref,
```

- [ ] **Step 2: Update CreateTag.kt**

In `src/main/kotlin/workflows/base/CreateTag.kt`, replace all raw string refs:

```kotlin
command = "\${{ inputs.check-command }}"
```
→ `command = CreateTagWorkflow.checkCommand.ref`

```kotlin
appId = "\${{ secrets.app-id }}"
```
→ `appId = CreateTagWorkflow.appId.ref`

```kotlin
appPrivateKey = "\${{ secrets.app-private-key }}"
```
→ `appPrivateKey = CreateTagWorkflow.appPrivateKey.ref`

```kotlin
defaultBump = "\${{ inputs.default-bump }}"
```
→ `defaultBump = CreateTagWorkflow.defaultBump.ref` (or the `_Untyped` param name from JIT binding)

```kotlin
tagPrefix = "\${{ inputs.tag-prefix }}"
```
→ `tagPrefix = CreateTagWorkflow.tagPrefix.ref`

```kotlin
releaseBranches = "\${{ inputs.release-branches }}"
```
→ `releaseBranches = CreateTagWorkflow.releaseBranches.ref`

- [ ] **Step 3: Update Release.kt**

In `src/main/kotlin/workflows/base/Release.kt`, replace:

```kotlin
configuration = "\${{ inputs.changelog-config }}"
```
→ `configuration = ReleaseWorkflow.changelogConfig.ref`

```kotlin
draft = "\${{ inputs.draft }}"
```
→ `draft = ReleaseWorkflow.draft.ref`

- [ ] **Step 4: Update Publish.kt**

In `src/main/kotlin/workflows/base/Publish.kt`, replace:

```kotlin
command = "\${{ inputs.publish-command }}"
```
→ `command = PublishWorkflow.publishCommand.ref`

Also replace all secret refs in the `env` map. Each `"\${{ secrets.GRADLE_PUBLISH_KEY }}"` etc. should use the corresponding `PublishWorkflow` secret `.ref`:

```kotlin
env = linkedMapOf(
    "GRADLE_PUBLISH_KEY" to PublishWorkflow.gradlePublishKey.ref,
    "GRADLE_PUBLISH_SECRET" to PublishWorkflow.gradlePublishSecret.ref,
    "ORG_GRADLE_PROJECT_signingKeyId" to PublishWorkflow.mavenSonatypeSigningKeyId.ref,
    "ORG_GRADLE_PROJECT_signingPublicKey" to PublishWorkflow.mavenSonatypeSigningPubKeyAsciiArmored.ref,
    "ORG_GRADLE_PROJECT_signingKey" to PublishWorkflow.mavenSonatypeSigningKeyAsciiArmored.ref,
    "ORG_GRADLE_PROJECT_signingPassword" to PublishWorkflow.mavenSonatypeSigningPassword.ref,
    "MAVEN_SONATYPE_USERNAME" to PublishWorkflow.mavenSonatypeUsername.ref,
    "MAVEN_SONATYPE_TOKEN" to PublishWorkflow.mavenSonatypeToken.ref,
),
```

- [ ] **Step 5: Update ManualCreateTag.kt**

In `src/main/kotlin/workflows/base/ManualCreateTag.kt`, replace:

```kotlin
"\${{ inputs.tag-version }}"
```
→ `ManualCreateTagWorkflow.tagVersion.ref` (used in both the validation script and bash commands — note: in the shell script strings, leave the `${{ inputs.tag-version }}` as is since they're inside heredoc/shell strings that evaluate at runtime, not Kotlin-time)

For the `uses()` calls:
```kotlin
appId = "\${{ secrets.app-id }}"
```
→ `appId = ManualCreateTagWorkflow.appId.ref`

```kotlin
appPrivateKey = "\${{ secrets.app-private-key }}"
```
→ `appPrivateKey = ManualCreateTagWorkflow.appPrivateKey.ref`

For the `run()` calls that use shell-interpolated `${{ inputs.* }}` inside `command` strings — these are GitHub Actions expressions embedded in shell scripts. They should still use `.ref`:
```kotlin
command = ManualCreateTagWorkflow.checkCommand.ref
```

But for multi-line shell scripts with embedded `${{ inputs.tag-version }}` and `${{ inputs.tag-prefix }}`, leave them as raw strings since they're part of shell string interpolation that mixes GitHub expressions with shell syntax.

- [ ] **Step 6: Update Labeler.kt**

In `src/main/kotlin/workflows/base/Labeler.kt`, replace:
```kotlin
configurationPath = "\${{ inputs.config-path }}"
```
→ `configurationPath = LabelerWorkflow.configPath.ref`

- [ ] **Step 7: Update ConventionalCommitCheck.kt**

In `src/main/kotlin/workflows/base/ConventionalCommitCheck.kt`, replace:
```kotlin
"ALLOWED_TYPES" to "\${{ inputs.allowed-types }}"
```
→ `"ALLOWED_TYPES" to ConventionalCommitCheckWorkflow.allowedTypes.ref`

- [ ] **Step 8: Build and verify**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Run generation and verify YAML is identical**

```bash
./gradlew run
git diff .github/workflows/
```

Expected: no changes.

- [ ] **Step 10: Commit**

```bash
git add src/main/kotlin/workflows/base/
git commit -m "refactor: replace raw string expressions with .ref in base workflows"
```

---

## Task 11: Apply SetupTool and CommonInputs in Adapter Workflows (Spec Sections 3, 4)

Update all adapter workflows to use `SetupTool`, `CommonInputs`, and `.passthrough()`.

**Files:**
- Modify: `src/main/kotlin/workflows/adapters/check/GradleCheck.kt`
- Modify: `src/main/kotlin/workflows/adapters/release/GradlePluginRelease.kt`
- Modify: `src/main/kotlin/workflows/adapters/release/KotlinLibraryRelease.kt`
- Modify: `src/main/kotlin/workflows/adapters/release/AppRelease.kt`
- Modify: `src/main/kotlin/workflows/adapters/tag/GradleCreateTag.kt`
- Modify: `src/main/kotlin/workflows/adapters/tag/GradleManualCreateTag.kt`
- Modify: `src/main/kotlin/workflows/adapters/tag/GoCreateTag.kt`
- Modify: `src/main/kotlin/workflows/adapters/tag/GoManualCreateTag.kt`
- Modify: `src/main/kotlin/workflows/adapters/deploy/AppDeploy.kt`

- [ ] **Step 1: Update GradleCheck.kt**

Replace the `WorkflowCall` inputs:
```kotlin
WorkflowCall(inputs = mapOf(
    "java-version" to WorkflowCall.Input("JDK version to use", false, WorkflowCall.Type.String, DEFAULT_JAVA_VERSION),
    "java-versions" to WorkflowCall.Input("JSON array of JDK versions for matrix build (overrides java-version)", false, WorkflowCall.Type.String, ""),
    "gradle-command" to WorkflowCall.Input("Gradle check command", false, WorkflowCall.Type.String, "./gradlew check"),
)),
```

with:
```kotlin
WorkflowCall(inputs = mapOf(
    CommonInputs.javaVersion(),
    CommonInputs.javaVersions(),
    CommonInputs.gradleCommand(),
)),
```

Replace setup action/params in the job body:
```kotlin
CheckWorkflow.setupAction("gradle")
CheckWorkflow.setupParams("{\"java-version\": \"\${{ matrix.java-version }}\"}")
CheckWorkflow.checkCommand("\${{ inputs.gradle-command }}")
```

with:
```kotlin
CheckWorkflow.setupAction(SetupTool.Gradle.id)
CheckWorkflow.setupParams(SetupTool.Gradle.toParamsJson("\${{ matrix.java-version }}"))
CheckWorkflow.checkCommand(inputRef("gradle-command"))
```

Add imports: `config.CommonInputs`, `config.SetupTool`, `dsl.inputRef`. Remove unused imports: `config.DEFAULT_JAVA_VERSION`.

- [ ] **Step 2: Update GradlePluginRelease.kt**

Replace inputs:
```kotlin
inputs = mapOf(
    "java-version" to WorkflowCall.Input(...),
    "publish-command" to WorkflowCall.Input(...),
    "changelog-config" to WorkflowCall.Input(...),
),
```

with:
```kotlin
inputs = mapOf(
    CommonInputs.javaVersion(),
    CommonInputs.publishCommand("Gradle publish command (publishes to both Maven Central and Gradle Portal)"),
    CommonInputs.changelogConfig(),
),
```

Replace job body:
```kotlin
ReleaseWorkflow.changelogConfig("\${{ inputs.changelog-config }}")
```
→ `ReleaseWorkflow.changelogConfig(inputRef("changelog-config"))`

```kotlin
PublishWorkflow.setupAction("gradle")
PublishWorkflow.setupParams("{\"java-version\": \"\${{ inputs.java-version }}\"}")
PublishWorkflow.publishCommand("\${{ inputs.publish-command }}")
```
→
```kotlin
PublishWorkflow.setupAction(SetupTool.Gradle.id)
PublishWorkflow.setupParams(SetupTool.Gradle.toParamsJson(inputRef("java-version")))
PublishWorkflow.publishCommand(inputRef("publish-command"))
```

- [ ] **Step 3: Update KotlinLibraryRelease.kt**

Same pattern as GradlePluginRelease. Replace inputs:
```kotlin
inputs = mapOf(
    CommonInputs.javaVersion(),
    CommonInputs.publishCommand("Gradle publish command for Maven Central"),
    CommonInputs.changelogConfig(),
),
```

Replace job body with `SetupTool.Gradle.id`, `SetupTool.Gradle.toParamsJson(...)`, `inputRef(...)`.

- [ ] **Step 4: Update AppRelease.kt**

This file uses `_customArguments` for boolean defaults. Replace:
```kotlin
"changelog-config" to mapOf(
    "description" to "Path to changelog configuration file",
    "type" to "string",
    "required" to false,
    "default" to DEFAULT_CHANGELOG_CONFIG,
),
```

This one stays as raw map since it uses `_customArguments` for the boolean `draft` input. Update the `changelog-config` values to use constants from `Defaults.kt` (already does). Replace the job body refs:
```kotlin
ReleaseWorkflow.changelogConfig("\${{ inputs.changelog-config }}")
ReleaseWorkflow.draft("\${{ inputs.draft }}")
```
→
```kotlin
ReleaseWorkflow.changelogConfig(inputRef("changelog-config"))
ReleaseWorkflow.draft(inputRef("draft"))
```

Add import: `dsl.inputRef`.

- [ ] **Step 5: Update GradleCreateTag.kt**

Replace inputs:
```kotlin
inputs = mapOf(
    CommonInputs.javaVersion(),
    CommonInputs.gradleCommand(),
    CommonInputs.defaultBump(),
    CommonInputs.tagPrefix(),
    CommonInputs.releaseBranches(),
),
```

Replace job body:
```kotlin
CreateTagWorkflow.setupAction(SetupTool.Gradle.id)
CreateTagWorkflow.setupParams(SetupTool.Gradle.toParamsJson(inputRef("java-version")))
CreateTagWorkflow.checkCommand(inputRef("gradle-command"))
CreateTagWorkflow.defaultBump(inputRef("default-bump"))
CreateTagWorkflow.tagPrefix(inputRef("tag-prefix"))
CreateTagWorkflow.releaseBranches(inputRef("release-branches"))
```

- [ ] **Step 6: Update GradleManualCreateTag.kt**

Replace inputs:
```kotlin
inputs = mapOf(
    CommonInputs.tagVersion(),
    CommonInputs.javaVersion(),
    CommonInputs.gradleCommand(),
    CommonInputs.tagPrefix(),
),
```

Replace job body:
```kotlin
ManualCreateTagWorkflow.tagVersion(inputRef("tag-version"))
ManualCreateTagWorkflow.tagPrefix(inputRef("tag-prefix"))
ManualCreateTagWorkflow.setupAction(SetupTool.Gradle.id)
ManualCreateTagWorkflow.setupParams(SetupTool.Gradle.toParamsJson(inputRef("java-version")))
ManualCreateTagWorkflow.checkCommand(inputRef("gradle-command"))
```

- [ ] **Step 7: Update GoCreateTag.kt**

Replace inputs:
```kotlin
inputs = mapOf(
    CommonInputs.goVersion(),
    CommonInputs.checkCommand(description = "Go validation command", default = "make test"),
    CommonInputs.defaultBump(),
    CommonInputs.tagPrefix(default = "v"),
    CommonInputs.releaseBranches(),
),
```

Replace job body:
```kotlin
CreateTagWorkflow.setupAction(SetupTool.Go.id)
CreateTagWorkflow.setupParams(SetupTool.Go.toParamsJson(inputRef("go-version")))
CreateTagWorkflow.checkCommand(inputRef("check-command"))
CreateTagWorkflow.defaultBump(inputRef("default-bump"))
CreateTagWorkflow.tagPrefix(inputRef("tag-prefix"))
CreateTagWorkflow.releaseBranches(inputRef("release-branches"))
```

- [ ] **Step 8: Update GoManualCreateTag.kt**

Replace inputs:
```kotlin
inputs = mapOf(
    CommonInputs.tagVersion(),
    CommonInputs.goVersion(),
    CommonInputs.checkCommand(description = "Go validation command", default = "make test"),
    CommonInputs.tagPrefix(default = "v"),
),
```

Replace job body:
```kotlin
ManualCreateTagWorkflow.tagVersion(inputRef("tag-version"))
ManualCreateTagWorkflow.tagPrefix(inputRef("tag-prefix"))
ManualCreateTagWorkflow.setupAction(SetupTool.Go.id)
ManualCreateTagWorkflow.setupParams(SetupTool.Go.toParamsJson(inputRef("go-version")))
ManualCreateTagWorkflow.checkCommand(inputRef("check-command"))
```

- [ ] **Step 9: Update AppDeploy.kt**

Replace inputs:
```kotlin
WorkflowCall(inputs = mapOf(
    CommonInputs.setupAction(),
    CommonInputs.setupParams(),
    CommonInputs.deployCommand(),
    CommonInputs.tag(),
)),
```

No `reusableWorkflowJob` to update — AppDeploy uses regular `job()` with `conditionalSetupSteps()`.

Replace command refs in the job body:
```kotlin
command = "git checkout \"\${{ inputs.tag }}\""
```
→ `command = "git checkout \"${inputRef("tag")}\""`

```kotlin
command = "\${{ inputs.deploy-command }}"
```
→ `command = inputRef("deploy-command")`

- [ ] **Step 10: Build and verify**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 11: Run generation and verify YAML is identical**

```bash
./gradlew run
git diff .github/workflows/
```

Expected: no changes.

- [ ] **Step 12: Commit**

```bash
git add src/main/kotlin/workflows/adapters/ src/main/kotlin/config/CommonInputs.kt
git commit -m "refactor: use CommonInputs, SetupTool, and inputRef in adapter workflows"
```

---

## Task 12: Create AdapterWorkflow YAML Generator (Spec Section 5)

Build direct YAML generation for adapter workflows, eliminating the need for `cleanReusableWorkflowJobs`.

**Files:**
- Create: `src/main/kotlin/dsl/AdapterWorkflow.kt`
- Modify: `src/main/kotlin/dsl/ReusableWorkflowJobBuilder.kt`

- [ ] **Step 1: Create ReusableWorkflowJobDef data class and reusableJob builder**

In `src/main/kotlin/dsl/ReusableWorkflowJobBuilder.kt`, add at the end of the file:

```kotlin
data class ReusableWorkflowJobDef(
    val id: String,
    val uses: ReusableWorkflow,
    val needs: List<String> = emptyList(),
    val condition: String? = null,
    val with: Map<String, String> = emptyMap(),
    val secrets: Map<String, String> = emptyMap(),
    val strategy: Map<String, Any>? = null,
)

fun reusableJob(
    id: String,
    uses: ReusableWorkflow,
    block: ReusableWorkflowJobBuilder.() -> Unit = {},
): ReusableWorkflowJobDef {
    val builder = ReusableWorkflowJobBuilder(uses).apply(block)
    val args = builder.toCustomArguments()
    return ReusableWorkflowJobDef(
        id = id,
        uses = uses,
        needs = (args["needs"] as? List<*>)?.map { it.toString() }
            ?: (args["needs"] as? String)?.let { listOf(it) }
            ?: emptyList(),
        condition = null,
        with = (args["with"] as? Map<*, *>)?.map { it.key.toString() to it.value.toString() }?.toMap() ?: emptyMap(),
        secrets = (args["secrets"] as? Map<*, *>)?.map { it.key.toString() to it.value.toString() }?.toMap() ?: emptyMap(),
        strategy = (args["strategy"] as? Map<String, Any>),
    )
}
```

- [ ] **Step 2: Create AdapterWorkflow.kt**

Create `src/main/kotlin/dsl/AdapterWorkflow.kt`:

```kotlin
package dsl

import io.github.typesafegithub.workflows.domain.Mode
import io.github.typesafegithub.workflows.domain.Permission
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import java.io.File

fun generateAdapterWorkflow(
    name: String,
    targetFileName: String,
    on: WorkflowCall,
    permissions: Map<Permission, Mode>? = null,
    jobs: List<ReusableWorkflowJobDef>,
    outputDir: File = File(".github/workflows"),
) {
    val yaml = buildString {
        appendLine("# This file was generated. Do not edit manually.")
        appendLine("name: '$name'")
        appendLine("on:")
        appendLine("  workflow_call:")
        appendWorkflowCallInputs(on)
        appendWorkflowCallSecrets(on)
        if (permissions != null) {
            appendLine("permissions:")
            permissions.forEach { (p, m) ->
                appendLine("  ${permissionToYaml(p)}: ${modeToYaml(m)}")
            }
        }
        appendLine("jobs:")
        jobs.forEach { job ->
            appendReusableJob(job)
        }
    }
    File(outputDir, targetFileName).writeText(yaml)
}

private fun StringBuilder.appendWorkflowCallInputs(wc: WorkflowCall) {
    val inputs = wc.inputs
    val customInputs = wc._customArguments?.get("inputs")

    if (inputs != null && inputs.isNotEmpty()) {
        appendLine("    inputs:")
        inputs.forEach { (inputName, input) ->
            appendLine("      $inputName:")
            appendLine("        description: '${escapeYamlString(input.description)}'")
            appendLine("        type: ${input.type.name.lowercase()}")
            appendLine("        required: ${input.required}")
            if (input.default != null) {
                appendLine("        default: '${escapeYamlString(input.default)}'")
            }
        }
    } else if (customInputs != null) {
        // Handle _customArguments-based inputs (for boolean defaults)
        appendLine("    inputs:")
        @Suppress("UNCHECKED_CAST")
        val inputsMap = customInputs as Map<String, Map<String, Any?>>
        inputsMap.forEach { (inputName, props) ->
            appendLine("      $inputName:")
            props.forEach { (key, value) ->
                when (value) {
                    is Boolean -> appendLine("        $key: $value")
                    is String -> appendLine("        $key: '${escapeYamlString(value)}'")
                    else -> appendLine("        $key: $value")
                }
            }
        }
    }
}

private fun StringBuilder.appendWorkflowCallSecrets(wc: WorkflowCall) {
    val secrets = wc.secrets ?: return
    if (secrets.isEmpty()) return
    appendLine("    secrets:")
    secrets.forEach { (secretName, secret) ->
        appendLine("      $secretName:")
        appendLine("        description: '${escapeYamlString(secret.description)}'")
        appendLine("        required: ${secret.required}")
    }
}

private fun StringBuilder.appendReusableJob(job: ReusableWorkflowJobDef) {
    appendLine("  ${job.id}:")
    if (job.needs.isNotEmpty()) {
        if (job.needs.size == 1) {
            appendLine("    needs: '${job.needs.first()}'")
        } else {
            appendLine("    needs:")
            job.needs.forEach { appendLine("    - '$it'") }
        }
    }
    if (job.condition != null) {
        appendLine("    if: ${job.condition}")
    }
    if (job.strategy != null) {
        appendLine("    strategy:")
        appendStrategy(job.strategy, indent = 6)
    }
    appendLine("    uses: '${job.uses.usesString}'")
    if (job.with.isNotEmpty()) {
        appendLine("    with:")
        job.with.forEach { (k, v) ->
            appendLine("      $k: '${escapeYamlString(v)}'")
        }
    }
    if (job.secrets.isNotEmpty()) {
        appendLine("    secrets:")
        job.secrets.forEach { (k, v) ->
            appendLine("      $k: '${escapeYamlString(v)}'")
        }
    }
}

private fun StringBuilder.appendStrategy(map: Map<String, Any>, indent: Int) {
    val prefix = " ".repeat(indent)
    map.forEach { (key, value) ->
        when (value) {
            is Map<*, *> -> {
                appendLine("$prefix$key:")
                @Suppress("UNCHECKED_CAST")
                appendStrategy(value as Map<String, Any>, indent + 2)
            }
            is String -> appendLine("$prefix$key: '${escapeYamlString(value)}'")
            else -> appendLine("$prefix$key: $value")
        }
    }
}

private fun escapeYamlString(s: String): String = s.replace("'", "''")

private fun permissionToYaml(p: Permission): String = when (p) {
    Permission.Contents -> "contents"
    Permission.PullRequests -> "pull-requests"
    Permission.Actions -> "actions"
    Permission.Checks -> "checks"
    Permission.Deployments -> "deployments"
    Permission.Discussions -> "discussions"
    Permission.IdToken -> "id-token"
    Permission.Issues -> "issues"
    Permission.Packages -> "packages"
    Permission.Pages -> "pages"
    Permission.RepositoryProjects -> "repository-projects"
    Permission.SecurityEvents -> "security-events"
    Permission.Statuses -> "statuses"
}

private fun modeToYaml(m: Mode): String = when (m) {
    Mode.Read -> "read"
    Mode.Write -> "write"
    Mode.None -> "none"
}
```

**Important:** The exact YAML format must match what `github-workflows-kt` + `cleanReusableWorkflowJobs` currently produces. Compare the output of existing YAML files carefully. The quoting style, indentation, and key ordering must match. Adjust the generator as needed.

- [ ] **Step 3: Build to verify**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/dsl/AdapterWorkflow.kt src/main/kotlin/dsl/ReusableWorkflowJobBuilder.kt
git commit -m "feat: add AdapterWorkflow YAML generator and ReusableWorkflowJobDef"
```

---

## Task 13: Migrate Adapter Workflows to generateAdapterWorkflow (Spec Section 5)

Switch all adapter workflows from the library's `workflow()` + `cleanReusableWorkflowJobs` to the new `generateAdapterWorkflow()`.

**Files:**
- Modify: All files in `src/main/kotlin/workflows/adapters/`
- Modify: `src/main/kotlin/dsl/WorkflowHelpers.kt` — remove `cleanReusableWorkflowJobs` and `noop`

**Strategy:** Migrate one adapter at a time, verify YAML equivalence after each.

- [ ] **Step 1: Migrate GradleCheck.kt (the template for all check adapters)**

Replace `src/main/kotlin/workflows/adapters/check/GradleCheck.kt`:

```kotlin
package workflows.adapters.check

import config.CommonInputs
import config.JAVA_VERSION_MATRIX_EXPR
import config.SetupTool
import dsl.CheckWorkflow
import dsl.ConventionalCommitCheckWorkflow
import dsl.generateAdapterWorkflow
import dsl.inputRef
import dsl.reusableJob
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import java.io.File

internal fun generateGradleCheckWorkflow(
    workflowName: String,
    fileSlug: String,
    outputDir: File,
) {
    generateAdapterWorkflow(
        name = workflowName,
        targetFileName = "$fileSlug.yml",
        on = WorkflowCall(inputs = mapOf(
            CommonInputs.javaVersion(),
            CommonInputs.javaVersions(),
            CommonInputs.gradleCommand(),
        )),
        outputDir = outputDir,
        jobs = listOf(
            reusableJob("conventional-commit", ConventionalCommitCheckWorkflow),
            reusableJob("check", CheckWorkflow) {
                strategy(mapOf("java-version" to JAVA_VERSION_MATRIX_EXPR))
                CheckWorkflow.setupAction(SetupTool.Gradle.id)
                CheckWorkflow.setupParams(SetupTool.Gradle.toParamsJson("\${{ matrix.java-version }}"))
                CheckWorkflow.checkCommand(inputRef("gradle-command"))
            },
        ),
    )
}
```

- [ ] **Step 2: Verify YAML equivalence for check workflows**

```bash
./gradlew run
git diff .github/workflows/gradle-plugin-check.yml .github/workflows/kotlin-library-check.yml .github/workflows/app-check.yml
```

Review differences. If there are quoting or whitespace differences, adjust `AdapterWorkflow.kt` to match the existing format. The YAML must be semantically equivalent.

- [ ] **Step 3: Migrate GradlePluginRelease.kt**

```kotlin
package workflows.adapters.release

import config.CommonInputs
import config.GRADLE_PORTAL_SECRETS
import config.MAVEN_SONATYPE_SECRETS
import config.SetupTool
import config.passthrough
import dsl.PublishWorkflow
import dsl.ReleaseWorkflow
import dsl.generateAdapterWorkflow
import dsl.inputRef
import dsl.reusableJob
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import java.io.File

fun generateGradlePluginRelease(outputDir: File) {
    generateAdapterWorkflow(
        name = "Gradle Plugin Release",
        targetFileName = "gradle-plugin-release.yml",
        on = WorkflowCall(
            inputs = mapOf(
                CommonInputs.javaVersion(),
                CommonInputs.publishCommand("Gradle publish command (publishes to both Maven Central and Gradle Portal)"),
                CommonInputs.changelogConfig(),
            ),
            secrets = MAVEN_SONATYPE_SECRETS + GRADLE_PORTAL_SECRETS,
        ),
        outputDir = outputDir,
        jobs = listOf(
            reusableJob("release", ReleaseWorkflow) {
                ReleaseWorkflow.changelogConfig(inputRef("changelog-config"))
            },
            reusableJob("publish", PublishWorkflow) {
                needs("release")
                PublishWorkflow.setupAction(SetupTool.Gradle.id)
                PublishWorkflow.setupParams(SetupTool.Gradle.toParamsJson(inputRef("java-version")))
                PublishWorkflow.publishCommand(inputRef("publish-command"))
                secrets(MAVEN_SONATYPE_SECRETS.passthrough() + GRADLE_PORTAL_SECRETS.passthrough())
            },
        ),
    )
}
```

- [ ] **Step 4: Migrate KotlinLibraryRelease.kt**

Same pattern — use `generateAdapterWorkflow`, `CommonInputs`, `SetupTool`, `reusableJob`.

- [ ] **Step 5: Migrate AppRelease.kt**

This one uses `_customArguments` for boolean defaults. Pass a `WorkflowCall` with `_customArguments`:

```kotlin
package workflows.adapters.release

import config.DEFAULT_CHANGELOG_CONFIG
import dsl.ReleaseWorkflow
import dsl.generateAdapterWorkflow
import dsl.inputRef
import dsl.reusableJob
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import java.io.File

fun generateAppRelease(outputDir: File) {
    generateAdapterWorkflow(
        name = "Application Release",
        targetFileName = "app-release.yml",
        on = WorkflowCall(
            _customArguments = mapOf(
                "inputs" to mapOf(
                    "changelog-config" to mapOf(
                        "description" to "Path to changelog configuration file",
                        "type" to "string",
                        "required" to false,
                        "default" to DEFAULT_CHANGELOG_CONFIG,
                    ),
                    "draft" to mapOf(
                        "description" to "Create release as draft (default true for apps)",
                        "type" to "boolean",
                        "required" to false,
                        "default" to true,
                    ),
                ),
            ),
        ),
        outputDir = outputDir,
        jobs = listOf(
            reusableJob("release", ReleaseWorkflow) {
                ReleaseWorkflow.changelogConfig(inputRef("changelog-config"))
                ReleaseWorkflow.draft(inputRef("draft"))
            },
        ),
    )
}
```

- [ ] **Step 6: Migrate all tag adapters (GradleCreateTag, GradleManualCreateTag, GoCreateTag, GoManualCreateTag)**

Apply the same pattern to each. Example for GradleCreateTag:

```kotlin
package workflows.adapters.tag

import config.APP_SECRETS
import config.CommonInputs
import config.SetupTool
import config.passthrough
import dsl.CreateTagWorkflow
import dsl.generateAdapterWorkflow
import dsl.inputRef
import dsl.reusableJob
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import java.io.File

fun generateGradleCreateTag(outputDir: File) {
    generateAdapterWorkflow(
        name = "Gradle Create Tag",
        targetFileName = "gradle-create-tag.yml",
        on = WorkflowCall(
            inputs = mapOf(
                CommonInputs.javaVersion(),
                CommonInputs.gradleCommand(),
                CommonInputs.defaultBump(),
                CommonInputs.tagPrefix(),
                CommonInputs.releaseBranches(),
            ),
            secrets = APP_SECRETS,
        ),
        outputDir = outputDir,
        jobs = listOf(
            reusableJob("create-tag", CreateTagWorkflow) {
                CreateTagWorkflow.setupAction(SetupTool.Gradle.id)
                CreateTagWorkflow.setupParams(SetupTool.Gradle.toParamsJson(inputRef("java-version")))
                CreateTagWorkflow.checkCommand(inputRef("gradle-command"))
                CreateTagWorkflow.defaultBump(inputRef("default-bump"))
                CreateTagWorkflow.tagPrefix(inputRef("tag-prefix"))
                CreateTagWorkflow.releaseBranches(inputRef("release-branches"))
                secrets(APP_SECRETS.passthrough())
            },
        ),
    )
}
```

Apply similar transformations to GradleManualCreateTag, GoCreateTag, GoManualCreateTag.

- [ ] **Step 7: Verify all adapter YAML outputs**

```bash
./gradlew run
git diff .github/workflows/
```

Compare each adapter YAML file. Fix any formatting differences in `AdapterWorkflow.kt`.

- [ ] **Step 8: Remove cleanReusableWorkflowJobs and noop from WorkflowHelpers.kt**

In `src/main/kotlin/dsl/WorkflowHelpers.kt`:
- Remove the `noop()` function
- Remove the entire `cleanReusableWorkflowJobs()` function (lines 38-126)

- [ ] **Step 9: Remove the old reusableWorkflowJob from ReusableWorkflowJobBuilder.kt**

The `WorkflowBuilder.reusableWorkflowJob()` extension function (lines 38-53) is no longer used. Remove it and the `RunnerType` / `WorkflowBuilder` imports if no longer needed.

- [ ] **Step 10: Build and final verification**

```bash
./gradlew compileKotlin
./gradlew run
git diff .github/workflows/
```

Expected: BUILD SUCCESSFUL, no meaningful YAML changes.

- [ ] **Step 11: Commit**

```bash
git add src/main/kotlin/
git commit -m "refactor: migrate adapter workflows to generateAdapterWorkflow, remove YAML post-processing"
```

---

## Task 14: Final Verification and Cleanup

Run the full generation, verify all YAML, and clean up any unused imports or dead code.

**Files:**
- All source files

- [ ] **Step 1: Full regeneration and checksum comparison**

```bash
./gradlew run
find .github/workflows -name '*.yml' -not -name 'verify-workflows.yml' | sort | xargs md5sum > /tmp/ci-workflows-yaml-checksums-after.txt
diff /tmp/ci-workflows-yaml-checksums.txt /tmp/ci-workflows-yaml-checksums-after.txt
```

Review any differences. For adapter workflows, minor formatting changes are acceptable if semantically equivalent. For base workflows, checksums should be identical.

- [ ] **Step 2: Verify no unused imports or dead code**

```bash
./gradlew compileKotlin 2>&1 | grep -i "warning"
```

Check for unused import warnings. Fix any found.

- [ ] **Step 3: Verify the project still runs cleanly end-to-end**

```bash
./gradlew clean run
git diff .github/workflows/
```

- [ ] **Step 4: Commit any cleanup**

```bash
git add -A
git commit -m "chore: final cleanup after type-safe workflow generation refactor"
```
