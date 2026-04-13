# GitHub Workflows KT Migration Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate all 20 GitHub workflow YAML files to Kotlin DSL using github-workflows-kt for type-safety and IDE autocomplete.

**Architecture:** Kotlin `.main.kts` scripts in `.github/workflow-src/` generate YAML into `.github/workflows/`. Shared code lives in `_shared.main.kts`. Composite actions remain YAML. A hand-written `verify-workflows.yml` ensures consistency.

**Tech Stack:** github-workflows-kt (latest), Kotlin scripting (.main.kts), Bash (generate.sh)

**Important limitation:** github-workflows-kt does NOT natively support `workflow_call` triggers, reusable workflow inputs/secrets/outputs, or calling reusable workflows via `uses:` at job level. All of these require `_customArguments` workarounds. This is the expected approach per the library's design.

---

## File Structure

### New files to create:
| File | Responsibility |
|------|---------------|
| `.github/workflow-src/_shared.main.kts` | Shared constants, enums, helper functions |
| `.github/workflow-src/generate.sh` | Runs all `.main.kts`, moves YAML to `../workflows/` |
| `.github/workflow-src/check.main.kts` | Base check workflow |
| `.github/workflow-src/create-tag.main.kts` | Base tag creation workflow |
| `.github/workflow-src/manual-create-tag.main.kts` | Base manual tag workflow |
| `.github/workflow-src/release.main.kts` | Base release workflow |
| `.github/workflow-src/publish.main.kts` | Base publish workflow |
| `.github/workflow-src/conventional-commit-check.main.kts` | PR title validation |
| `.github/workflow-src/labeler.main.kts` | PR labeler |
| `.github/workflow-src/app-check.main.kts` | App check adapter |
| `.github/workflow-src/app-release.main.kts` | App release adapter |
| `.github/workflow-src/app-deploy.main.kts` | App deploy adapter |
| `.github/workflow-src/gradle-create-tag.main.kts` | Gradle tag adapter |
| `.github/workflow-src/gradle-manual-create-tag.main.kts` | Gradle manual tag adapter |
| `.github/workflow-src/gradle-plugin-check.main.kts` | Gradle plugin check adapter |
| `.github/workflow-src/gradle-plugin-release.main.kts` | Gradle plugin release adapter |
| `.github/workflow-src/kotlin-library-check.main.kts` | Kotlin lib check adapter |
| `.github/workflow-src/kotlin-library-release.main.kts` | Kotlin lib release adapter |
| `.github/workflow-src/go-create-tag.main.kts` | Go tag adapter |
| `.github/workflow-src/go-manual-create-tag.main.kts` | Go manual tag adapter |
| `.github/workflows/verify-workflows.yml` | Hand-written CI consistency check |

### Files that will be regenerated (overwritten):
All 20 `.github/workflows/*.yml` files (except `verify-workflows.yml`)

### Files unchanged:
- `.github/actions/setup-gradle/action.yml`
- `.github/actions/setup-go/action.yml`
- `.github/actions/setup-python/action.yml`
- `.github/actions/create-app-token/action.yml`
- `.github/labeler.yml`

---

### Task 1: Infrastructure — `_shared.main.kts` and `generate.sh`

**Files:**
- Create: `.github/workflow-src/_shared.main.kts`
- Create: `.github/workflow-src/generate.sh`

- [ ] **Step 1: Create the `workflow-src` directory**

```bash
mkdir -p .github/workflow-src
```

- [ ] **Step 2: Create `_shared.main.kts`**

Create `.github/workflow-src/_shared.main.kts`:

```kotlin
#!/usr/bin/env kotlin

@file:Repository("https://repo.maven.apache.org/maven2/")
@file:DependsOn("io.github.typesafegithub:github-workflows-kt:3.7.0")

import io.github.typesafegithub.workflows.domain.RunnerType
import io.github.typesafegithub.workflows.dsl.JobBuilder

// ── Constants ──────────────────────────────────────────────────────────────────

val UBUNTU_LATEST = RunnerType.UbuntuLatest

val DEFAULT_JAVA_VERSION = "17"
val DEFAULT_GO_VERSION = "1.22"
val DEFAULT_PYTHON_VERSION = "3.12"
val DEFAULT_RELEASE_BRANCHES = "main,[0-9]+\\.x"
val DEFAULT_CHANGELOG_CONFIG = ".github/changelog-config.json"

val WORKFLOW_REF = "v2"
val WORKFLOW_BASE = "zenhelix/ci-workflows/.github/workflows"
val ACTION_BASE = "zenhelix/ci-workflows/.github/actions"

// ── Workflow Call Input Helpers ─────────────────────────────────────────────────

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

// ── Reusable Workflow Call Helpers ──────────────────────────────────────────────

fun reusableWorkflow(name: String) = "$WORKFLOW_BASE/$name@$WORKFLOW_REF"
fun localAction(name: String) = "$ACTION_BASE/$name@$WORKFLOW_REF"

// ── Setup Step Helpers ─────────────────────────────────────────────────────────

fun gradleSetupStep(
    javaVersionExpr: String = "fromJson(inputs.setup-params).java-version || '17'",
    fetchDepth: String = "1",
) = mapOf(
    "name" to "Setup Gradle",
    "if" to "inputs.setup-action == 'gradle'",
    "uses" to localAction("setup-gradle"),
    "with" to mapOf(
        "java-version" to "\${{ $javaVersionExpr }}",
        "fetch-depth" to fetchDepth,
    ),
)

fun goSetupStep(
    goVersionExpr: String = "fromJson(inputs.setup-params).go-version || '1.22'",
    fetchDepth: String = "1",
) = mapOf(
    "name" to "Setup Go",
    "if" to "inputs.setup-action == 'go'",
    "uses" to localAction("setup-go"),
    "with" to mapOf(
        "go-version" to "\${{ $goVersionExpr }}",
        "fetch-depth" to fetchDepth,
    ),
)

fun pythonSetupStep(
    pythonVersionExpr: String = "fromJson(inputs.setup-params).python-version || '3.12'",
    fetchDepth: String = "1",
) = mapOf(
    "name" to "Setup Python",
    "if" to "inputs.setup-action == 'python'",
    "uses" to localAction("setup-python"),
    "with" to mapOf(
        "python-version" to "\${{ $pythonVersionExpr }}",
        "fetch-depth" to fetchDepth,
    ),
)

fun conditionalSetupSteps(fetchDepth: String = "1") = listOf(
    gradleSetupStep(fetchDepth = fetchDepth),
    goSetupStep(fetchDepth = fetchDepth),
    pythonSetupStep(fetchDepth = fetchDepth),
)

// ── Common Inputs ──────────────────────────────────────────────────────────────

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
```

- [ ] **Step 3: Create `generate.sh`**

Create `.github/workflow-src/generate.sh`:

```bash
#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKFLOWS_DIR="$SCRIPT_DIR/../workflows"

cd "$SCRIPT_DIR"

echo "Generating workflows from Kotlin sources..."

for script in *.main.kts; do
    # Skip shared files (prefixed with _)
    [[ "$script" == _* ]] && continue

    echo "  Processing $script..."
    kotlin "$script"
done

# Move generated YAML files to workflows directory
for yaml in *.yaml; do
    [ -f "$yaml" ] || continue
    target_name="${yaml%.yaml}.yml"
    mv "$yaml" "$WORKFLOWS_DIR/$target_name"
    echo "  Moved $yaml -> workflows/$target_name"
done

echo "Done. Generated workflows are in .github/workflows/"
```

- [ ] **Step 4: Make generate.sh executable**

```bash
chmod +x .github/workflow-src/generate.sh
```

- [ ] **Step 5: Verify _shared.main.kts compiles**

```bash
cd .github/workflow-src && kotlin _shared.main.kts
```

Expected: Script compiles without errors (no output since it only defines values/functions).

- [ ] **Step 6: Commit**

```bash
git add .github/workflow-src/_shared.main.kts .github/workflow-src/generate.sh
git commit -m "ci: add workflow-src infrastructure with shared helpers and generate script"
```

---

### Task 2: Base workflow — `check.main.kts`

**Files:**
- Create: `.github/workflow-src/check.main.kts`
- Regenerated: `.github/workflows/check.yml`

- [ ] **Step 1: Create `check.main.kts`**

Create `.github/workflow-src/check.main.kts`:

```kotlin
#!/usr/bin/env kotlin
@file:Import("_shared.main.kts")

import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowDispatch
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig

workflow(
    name = "Check",
    on = listOf(),
    sourceFile = __FILE__,
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
        _customArguments = mapOf(
            "steps" to (conditionalSetupSteps() + listOf(
                mapOf(
                    "name" to "Run check",
                    "run" to "\${{ inputs.check-command }}",
                ),
            )),
        ),
    ) {}
}
```

- [ ] **Step 2: Generate and verify**

```bash
cd .github/workflow-src && kotlin check.main.kts
```

Expected: Creates `check.yaml` in `workflow-src/`.

- [ ] **Step 3: Move and compare with original**

```bash
cd .github/workflow-src && mv check.yaml ../workflows/check.yml
cd ../.. && git diff .github/workflows/check.yml
```

Expected: Diff shows only formatting differences (quoting, key order), not logic changes. If there are logic differences, fix the Kotlin source.

- [ ] **Step 4: Commit**

```bash
git add .github/workflow-src/check.main.kts .github/workflows/check.yml
git commit -m "ci: migrate check workflow to Kotlin DSL"
```

---

### Task 3: Base workflow — `conventional-commit-check.main.kts`

**Files:**
- Create: `.github/workflow-src/conventional-commit-check.main.kts`
- Regenerated: `.github/workflows/conventional-commit-check.yml`

- [ ] **Step 1: Create `conventional-commit-check.main.kts`**

Create `.github/workflow-src/conventional-commit-check.main.kts`:

```kotlin
#!/usr/bin/env kotlin
@file:Import("_shared.main.kts")

import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig

workflow(
    name = "Conventional Commit Check",
    on = listOf(),
    sourceFile = __FILE__,
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
            env = linkedMapOf(
                "PR_TITLE" to "\${{ github.event.pull_request.title }}",
                "ALLOWED_TYPES" to "\${{ inputs.allowed-types }}",
            ),
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
        )
    }
}
```

- [ ] **Step 2: Generate, move, and compare**

```bash
cd .github/workflow-src && kotlin conventional-commit-check.main.kts && mv conventional-commit-check.yaml ../workflows/conventional-commit-check.yml
cd ../.. && git diff .github/workflows/conventional-commit-check.yml
```

Expected: Only formatting differences.

- [ ] **Step 3: Commit**

```bash
git add .github/workflow-src/conventional-commit-check.main.kts .github/workflows/conventional-commit-check.yml
git commit -m "ci: migrate conventional-commit-check workflow to Kotlin DSL"
```

---

### Task 4: Base workflow — `release.main.kts`

**Files:**
- Create: `.github/workflow-src/release.main.kts`
- Regenerated: `.github/workflows/release.yml`

- [ ] **Step 1: Create `release.main.kts`**

Create `.github/workflow-src/release.main.kts`:

```kotlin
#!/usr/bin/env kotlin
@file:Import("_shared.main.kts")

@file:Repository("https://bindings.krzeminski.it")
@file:DependsOn("actions:checkout:v6")
@file:DependsOn("mikepenz:release-changelog-builder-action:v6")
@file:DependsOn("softprops:action-gh-release:v2")

import io.github.typesafegithub.workflows.actions.actions.Checkout
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.dsl.expressions.expr
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig

workflow(
    name = "Release",
    on = listOf(),
    sourceFile = __FILE__,
    consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
    _customArguments = mapOf(
        "on" to mapOf(
            "workflow_call" to mapOf(
                "inputs" to mapOf(
                    "changelog-config" to stringInput(
                        description = "Path to changelog configuration file",
                        default = DEFAULT_CHANGELOG_CONFIG,
                    ),
                    "draft" to booleanInput(
                        description = "Create release as draft",
                        default = false,
                    ),
                ),
            ),
        ),
        "permissions" to mapOf("contents" to "write"),
    ),
) {
    job(
        id = "release",
        name = "GitHub Release",
        runsOn = UbuntuLatest,
    ) {
        uses(
            name = "Check out",
            action = Checkout(fetchDepth = Checkout.FetchDepth.Unlimited),
        )
        uses(
            name = "Build Changelog",
            action = io.github.typesafegithub.workflows.actions.mikepenz.ReleaseChangelogBuilderAction(
                configuration = "\${{ inputs.changelog-config }}",
                toTag = expr { github.ref_name },
            ),
            _customArguments = mapOf("id" to "changelog"),
            env = linkedMapOf("GITHUB_TOKEN" to expr("secrets.GITHUB_TOKEN")),
        )
        uses(
            name = "Create Release",
            action = io.github.typesafegithub.workflows.actions.softprops.ActionGhRelease(
                body = "\${{ steps.changelog.outputs.changelog }}",
                name = expr { github.ref_name },
                tagName = expr { github.ref_name },
                draft = "\${{ inputs.draft }}",
            ),
            env = linkedMapOf("GITHUB_TOKEN" to expr("secrets.GITHUB_TOKEN")),
        )
    }
}
```

- [ ] **Step 2: Generate, move, and compare**

```bash
cd .github/workflow-src && kotlin release.main.kts && mv release.yaml ../workflows/release.yml
cd ../.. && git diff .github/workflows/release.yml
```

- [ ] **Step 3: Commit**

```bash
git add .github/workflow-src/release.main.kts .github/workflows/release.yml
git commit -m "ci: migrate release workflow to Kotlin DSL"
```

---

### Task 5: Base workflow — `publish.main.kts`

**Files:**
- Create: `.github/workflow-src/publish.main.kts`
- Regenerated: `.github/workflows/publish.yml`

- [ ] **Step 1: Create `publish.main.kts`**

Create `.github/workflow-src/publish.main.kts`:

```kotlin
#!/usr/bin/env kotlin
@file:Import("_shared.main.kts")

import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig

val PUBLISH_SECRETS = mapOf(
    "GRADLE_PUBLISH_KEY" to secretInput("Gradle Plugin Portal publish key", required = false),
    "GRADLE_PUBLISH_SECRET" to secretInput("Gradle Plugin Portal publish secret", required = false),
    "MAVEN_SONATYPE_SIGNING_KEY_ID" to secretInput("GPG signing key ID", required = false),
    "MAVEN_SONATYPE_SIGNING_PUB_KEY_ASCII_ARMORED" to secretInput("GPG signing public key (ASCII armored)", required = false),
    "MAVEN_SONATYPE_SIGNING_KEY_ASCII_ARMORED" to secretInput("GPG signing private key (ASCII armored)", required = false),
    "MAVEN_SONATYPE_SIGNING_PASSWORD" to secretInput("GPG signing key passphrase", required = false),
    "MAVEN_SONATYPE_USERNAME" to secretInput("Maven Central (Sonatype) username", required = false),
    "MAVEN_SONATYPE_TOKEN" to secretInput("Maven Central (Sonatype) token", required = false),
)

workflow(
    name = "Publish",
    on = listOf(),
    sourceFile = __FILE__,
    consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
    _customArguments = mapOf(
        "on" to mapOf(
            "workflow_call" to mapOf(
                "inputs" to mapOf(
                    SETUP_ACTION_INPUT,
                    SETUP_PARAMS_INPUT,
                    "publish-command" to stringInput(
                        description = "Command to run for publishing",
                        required = true,
                    ),
                ),
                "secrets" to PUBLISH_SECRETS,
            ),
        ),
        "permissions" to mapOf("contents" to "read"),
    ),
) {
    job(
        id = "publish",
        name = "Publish",
        runsOn = UbuntuLatest,
        _customArguments = mapOf(
            "steps" to (conditionalSetupSteps() + listOf(
                mapOf(
                    "name" to "Publish",
                    "env" to mapOf(
                        "GRADLE_PUBLISH_KEY" to "\${{ secrets.GRADLE_PUBLISH_KEY }}",
                        "GRADLE_PUBLISH_SECRET" to "\${{ secrets.GRADLE_PUBLISH_SECRET }}",
                        "ORG_GRADLE_PROJECT_signingKeyId" to "\${{ secrets.MAVEN_SONATYPE_SIGNING_KEY_ID }}",
                        "ORG_GRADLE_PROJECT_signingPublicKey" to "\${{ secrets.MAVEN_SONATYPE_SIGNING_PUB_KEY_ASCII_ARMORED }}",
                        "ORG_GRADLE_PROJECT_signingKey" to "\${{ secrets.MAVEN_SONATYPE_SIGNING_KEY_ASCII_ARMORED }}",
                        "ORG_GRADLE_PROJECT_signingPassword" to "\${{ secrets.MAVEN_SONATYPE_SIGNING_PASSWORD }}",
                        "MAVEN_SONATYPE_USERNAME" to "\${{ secrets.MAVEN_SONATYPE_USERNAME }}",
                        "MAVEN_SONATYPE_TOKEN" to "\${{ secrets.MAVEN_SONATYPE_TOKEN }}",
                    ),
                    "run" to "\${{ inputs.publish-command }}",
                ),
            )),
        ),
    ) {}
}
```

- [ ] **Step 2: Generate, move, and compare**

```bash
cd .github/workflow-src && kotlin publish.main.kts && mv publish.yaml ../workflows/publish.yml
cd ../.. && git diff .github/workflows/publish.yml
```

- [ ] **Step 3: Commit**

```bash
git add .github/workflow-src/publish.main.kts .github/workflows/publish.yml
git commit -m "ci: migrate publish workflow to Kotlin DSL"
```

---

### Task 6: Base workflow — `create-tag.main.kts`

**Files:**
- Create: `.github/workflow-src/create-tag.main.kts`
- Regenerated: `.github/workflows/create-tag.yml`

- [ ] **Step 1: Create `create-tag.main.kts`**

Create `.github/workflow-src/create-tag.main.kts`:

```kotlin
#!/usr/bin/env kotlin
@file:Import("_shared.main.kts")

import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig

workflow(
    name = "Create Tag",
    on = listOf(),
    sourceFile = __FILE__,
    consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
    _customArguments = mapOf(
        "on" to mapOf(
            "workflow_call" to mapOf(
                "inputs" to mapOf(
                    SETUP_ACTION_INPUT,
                    SETUP_PARAMS_INPUT,
                    CHECK_COMMAND_INPUT,
                    "default-bump" to stringInput(
                        description = "Default version bump type (major, minor, patch)",
                        default = "patch",
                    ),
                    "tag-prefix" to stringInput(
                        description = "Prefix for the tag (e.g. v)",
                        default = "",
                    ),
                    "release-branches" to stringInput(
                        description = "Comma-separated branch patterns for releases",
                        default = DEFAULT_RELEASE_BRANCHES,
                    ),
                ),
                "secrets" to mapOf(
                    APP_ID_SECRET,
                    APP_PRIVATE_KEY_SECRET,
                ),
            ),
        ),
        "permissions" to mapOf("contents" to "write"),
    ),
) {
    job(
        id = "create_tag",
        name = "Create Tag",
        runsOn = UbuntuLatest,
        _customArguments = mapOf(
            "steps" to (conditionalSetupSteps(fetchDepth = "0") + listOf(
                mapOf(
                    "name" to "Run validation",
                    "run" to "\${{ inputs.check-command }}",
                ),
                mapOf(
                    "name" to "Generate App Token",
                    "id" to "app-token",
                    "uses" to localAction("create-app-token"),
                    "with" to mapOf(
                        "app-id" to "\${{ secrets.app-id }}",
                        "app-private-key" to "\${{ secrets.app-private-key }}",
                    ),
                ),
                mapOf(
                    "name" to "Bump version and push tag",
                    "uses" to "mathieudutour/github-tag-action@v6.2",
                    "with" to mapOf(
                        "github_token" to "\${{ steps.app-token.outputs.token }}",
                        "default_bump" to "\${{ inputs.default-bump }}",
                        "tag_prefix" to "\${{ inputs.tag-prefix }}",
                        "release_branches" to "\${{ inputs.release-branches }}",
                    ),
                ),
            )),
        ),
    ) {}
}
```

- [ ] **Step 2: Generate, move, and compare**

```bash
cd .github/workflow-src && kotlin create-tag.main.kts && mv create-tag.yaml ../workflows/create-tag.yml
cd ../.. && git diff .github/workflows/create-tag.yml
```

- [ ] **Step 3: Commit**

```bash
git add .github/workflow-src/create-tag.main.kts .github/workflows/create-tag.yml
git commit -m "ci: migrate create-tag workflow to Kotlin DSL"
```

---

### Task 7: Base workflow — `manual-create-tag.main.kts`

**Files:**
- Create: `.github/workflow-src/manual-create-tag.main.kts`
- Regenerated: `.github/workflows/manual-create-tag.yml`

- [ ] **Step 1: Create `manual-create-tag.main.kts`**

Create `.github/workflow-src/manual-create-tag.main.kts`:

```kotlin
#!/usr/bin/env kotlin
@file:Import("_shared.main.kts")

import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig

workflow(
    name = "Manual Create Tag",
    on = listOf(),
    sourceFile = __FILE__,
    consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
    _customArguments = mapOf(
        "on" to mapOf(
            "workflow_call" to mapOf(
                "inputs" to mapOf(
                    "tag-version" to stringInput(
                        description = "Version to tag (e.g. 1.2.3)",
                        required = true,
                    ),
                    "tag-prefix" to stringInput(
                        description = "Prefix for the tag (e.g. v)",
                        default = "",
                    ),
                    SETUP_ACTION_INPUT,
                    SETUP_PARAMS_INPUT,
                    CHECK_COMMAND_INPUT,
                ),
                "secrets" to mapOf(
                    APP_ID_SECRET,
                    APP_PRIVATE_KEY_SECRET,
                ),
            ),
        ),
        "permissions" to mapOf("contents" to "write"),
    ),
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
        // Remaining steps use _customArguments because they reference local composite actions
    }

    // Override job with full custom steps
    _customArguments = mapOf(
        "on" to mapOf(
            "workflow_call" to mapOf(
                "inputs" to mapOf(
                    "tag-version" to stringInput(description = "Version to tag (e.g. 1.2.3)", required = true),
                    "tag-prefix" to stringInput(description = "Prefix for the tag (e.g. v)", default = ""),
                    SETUP_ACTION_INPUT,
                    SETUP_PARAMS_INPUT,
                    CHECK_COMMAND_INPUT,
                ),
                "secrets" to mapOf(APP_ID_SECRET, APP_PRIVATE_KEY_SECRET),
            ),
        ),
        "permissions" to mapOf("contents" to "write"),
    )
}
```

**Note:** This workflow has a complex mix of shell scripts and composite action calls. Due to the `_customArguments` limitation for steps that use local composite actions, the full implementation may need to use `_customArguments` for the entire job steps array, similar to `check.main.kts`. The exact approach should be determined during implementation based on what compiles and produces correct YAML.

**Alternative full-custom approach if the above doesn't work:**

```kotlin
#!/usr/bin/env kotlin
@file:Import("_shared.main.kts")

import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig

workflow(
    name = "Manual Create Tag",
    on = listOf(),
    sourceFile = __FILE__,
    consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
    _customArguments = mapOf(
        "on" to mapOf(
            "workflow_call" to mapOf(
                "inputs" to mapOf(
                    "tag-version" to stringInput(description = "Version to tag (e.g. 1.2.3)", required = true),
                    "tag-prefix" to stringInput(description = "Prefix for the tag (e.g. v)", default = ""),
                    SETUP_ACTION_INPUT,
                    SETUP_PARAMS_INPUT,
                    CHECK_COMMAND_INPUT,
                ),
                "secrets" to mapOf(APP_ID_SECRET, APP_PRIVATE_KEY_SECRET),
            ),
        ),
        "permissions" to mapOf("contents" to "write"),
    ),
) {
    job(
        id = "manual_tag",
        name = "Manual Tag",
        runsOn = UbuntuLatest,
        _customArguments = mapOf(
            "steps" to listOf(
                mapOf(
                    "name" to "Validate version format",
                    "run" to """
                        VERSION="${'$'}{{ inputs.tag-version }}"
                        if [[ ! "${'$'}VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9.]+)?${'$'} ]]; then
                          echo "::error::Version must be in semver format (e.g. 1.2.3 or 1.2.3-rc.1)"
                          exit 1
                        fi
                    """.trimIndent(),
                ),
            ) + conditionalSetupSteps(fetchDepth = "0") + listOf(
                mapOf(
                    "name" to "Run validation",
                    "run" to "\${{ inputs.check-command }}",
                ),
                mapOf(
                    "name" to "Generate App Token",
                    "id" to "app-token",
                    "uses" to localAction("create-app-token"),
                    "with" to mapOf(
                        "app-id" to "\${{ secrets.app-id }}",
                        "app-private-key" to "\${{ secrets.app-private-key }}",
                    ),
                ),
                mapOf(
                    "name" to "Create and push tag",
                    "env" to mapOf("GITHUB_TOKEN" to "\${{ steps.app-token.outputs.token }}"),
                    "run" to """
                        TAG="${'$'}{{ inputs.tag-prefix }}${'$'}{{ inputs.tag-version }}"
                        git config user.name "github-actions[bot]"
                        git config user.email "github-actions[bot]@users.noreply.github.com"
                        git tag -a "${'$'}TAG" -m "Release ${'$'}TAG"
                        git push origin "${'$'}TAG"
                        echo "::notice::Created tag ${'$'}TAG"
                    """.trimIndent(),
                ),
            ),
        ),
    ) {}
}
```

- [ ] **Step 2: Generate, move, and compare**

```bash
cd .github/workflow-src && kotlin manual-create-tag.main.kts && mv manual-create-tag.yaml ../workflows/manual-create-tag.yml
cd ../.. && git diff .github/workflows/manual-create-tag.yml
```

- [ ] **Step 3: Commit**

```bash
git add .github/workflow-src/manual-create-tag.main.kts .github/workflows/manual-create-tag.yml
git commit -m "ci: migrate manual-create-tag workflow to Kotlin DSL"
```

---

### Task 8: Base workflow — `labeler.main.kts`

**Files:**
- Create: `.github/workflow-src/labeler.main.kts`
- Regenerated: `.github/workflows/labeler.yml`

- [ ] **Step 1: Create `labeler.main.kts`**

Create `.github/workflow-src/labeler.main.kts`:

```kotlin
#!/usr/bin/env kotlin
@file:Import("_shared.main.kts")

@file:Repository("https://bindings.krzeminski.it")
@file:DependsOn("actions:labeler:v6")

import io.github.typesafegithub.workflows.actions.actions.Labeler
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.dsl.expressions.expr
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig

workflow(
    name = "PR Labeler",
    on = listOf(),
    sourceFile = __FILE__,
    consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
    _customArguments = mapOf(
        "on" to mapOf(
            "workflow_call" to mapOf(
                "inputs" to mapOf(
                    "config-path" to stringInput(
                        description = "Path to labeler configuration file",
                        default = ".github/labeler.yml",
                    ),
                ),
            ),
        ),
        "permissions" to mapOf(
            "contents" to "write",
            "pull-requests" to "write",
        ),
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
                repoToken = expr("secrets.GITHUB_TOKEN"),
                configurationPath = "\${{ inputs.config-path }}",
                syncLabels = true,
            ),
        )
    }
}
```

- [ ] **Step 2: Generate, move, and compare**

```bash
cd .github/workflow-src && kotlin labeler.main.kts && mv labeler.yaml ../workflows/labeler.yml
cd ../.. && git diff .github/workflows/labeler.yml
```

- [ ] **Step 3: Commit**

```bash
git add .github/workflow-src/labeler.main.kts .github/workflows/labeler.yml
git commit -m "ci: migrate labeler workflow to Kotlin DSL"
```

---

### Task 9: Adapter workflows — Gradle group (4 files)

**Files:**
- Create: `.github/workflow-src/gradle-create-tag.main.kts`
- Create: `.github/workflow-src/gradle-manual-create-tag.main.kts`
- Create: `.github/workflow-src/gradle-plugin-check.main.kts`
- Create: `.github/workflow-src/gradle-plugin-release.main.kts`
- Regenerated: corresponding `.github/workflows/*.yml`

- [ ] **Step 1: Create `gradle-create-tag.main.kts`**

Create `.github/workflow-src/gradle-create-tag.main.kts`:

```kotlin
#!/usr/bin/env kotlin
@file:Import("_shared.main.kts")

import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig

workflow(
    name = "Gradle Create Tag",
    on = listOf(),
    sourceFile = __FILE__,
    consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
    _customArguments = mapOf(
        "on" to mapOf(
            "workflow_call" to mapOf(
                "inputs" to mapOf(
                    "java-version" to stringInput(description = "JDK version to use", default = DEFAULT_JAVA_VERSION),
                    "gradle-command" to stringInput(description = "Gradle validation command", default = "./gradlew check"),
                    "default-bump" to stringInput(description = "Default version bump type (major, minor, patch)", default = "patch"),
                    "tag-prefix" to stringInput(description = "Prefix for the tag", default = ""),
                    "release-branches" to stringInput(description = "Comma-separated branch patterns for releases", default = DEFAULT_RELEASE_BRANCHES),
                ),
                "secrets" to mapOf(APP_ID_SECRET, APP_PRIVATE_KEY_SECRET),
            ),
        ),
    ),
) {
    job(
        id = "create-tag",
        runsOn = UbuntuLatest,
        _customArguments = mapOf(
            "uses" to reusableWorkflow("create-tag.yml"),
            "with" to mapOf(
                "setup-action" to "gradle",
                "setup-params" to """{"java-version": "${'$'}{{ inputs.java-version }}"}""",
                "check-command" to "\${{ inputs.gradle-command }}",
                "default-bump" to "\${{ inputs.default-bump }}",
                "tag-prefix" to "\${{ inputs.tag-prefix }}",
                "release-branches" to "\${{ inputs.release-branches }}",
            ),
            "secrets" to mapOf(
                "app-id" to "\${{ secrets.app-id }}",
                "app-private-key" to "\${{ secrets.app-private-key }}",
            ),
        ),
    ) {}
}
```

- [ ] **Step 2: Create `gradle-manual-create-tag.main.kts`**

Create `.github/workflow-src/gradle-manual-create-tag.main.kts`:

```kotlin
#!/usr/bin/env kotlin
@file:Import("_shared.main.kts")

import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig

workflow(
    name = "Gradle Manual Create Tag",
    on = listOf(),
    sourceFile = __FILE__,
    consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
    _customArguments = mapOf(
        "on" to mapOf(
            "workflow_call" to mapOf(
                "inputs" to mapOf(
                    "tag-version" to stringInput(description = "Version to tag (e.g. 1.2.3)", required = true),
                    "java-version" to stringInput(description = "JDK version to use", default = DEFAULT_JAVA_VERSION),
                    "gradle-command" to stringInput(description = "Gradle validation command", default = "./gradlew check"),
                    "tag-prefix" to stringInput(description = "Prefix for the tag", default = ""),
                ),
                "secrets" to mapOf(APP_ID_SECRET, APP_PRIVATE_KEY_SECRET),
            ),
        ),
    ),
) {
    job(
        id = "manual-tag",
        runsOn = UbuntuLatest,
        _customArguments = mapOf(
            "uses" to reusableWorkflow("manual-create-tag.yml"),
            "with" to mapOf(
                "tag-version" to "\${{ inputs.tag-version }}",
                "tag-prefix" to "\${{ inputs.tag-prefix }}",
                "setup-action" to "gradle",
                "setup-params" to """{"java-version": "${'$'}{{ inputs.java-version }}"}""",
                "check-command" to "\${{ inputs.gradle-command }}",
            ),
            "secrets" to mapOf(
                "app-id" to "\${{ secrets.app-id }}",
                "app-private-key" to "\${{ secrets.app-private-key }}",
            ),
        ),
    ) {}
}
```

- [ ] **Step 3: Create `gradle-plugin-check.main.kts`**

Create `.github/workflow-src/gradle-plugin-check.main.kts`:

```kotlin
#!/usr/bin/env kotlin
@file:Import("_shared.main.kts")

import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig

workflow(
    name = "Gradle Plugin Check",
    on = listOf(),
    sourceFile = __FILE__,
    consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
    _customArguments = mapOf(
        "on" to mapOf(
            "workflow_call" to mapOf(
                "inputs" to mapOf(
                    "java-version" to stringInput(description = "JDK version to use", default = DEFAULT_JAVA_VERSION),
                    "java-versions" to stringInput(description = "JSON array of JDK versions for matrix build (overrides java-version)", default = ""),
                    "gradle-command" to stringInput(description = "Gradle check command", default = "./gradlew check"),
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
    ) {}
    job(
        id = "check",
        runsOn = UbuntuLatest,
        _customArguments = mapOf(
            "strategy" to mapOf(
                "matrix" to mapOf(
                    "java-version" to "\${{ fromJson(inputs.java-versions || format('[\"\\{0}\"]', inputs.java-version)) }}",
                ),
            ),
            "uses" to reusableWorkflow("check.yml"),
            "with" to mapOf(
                "setup-action" to "gradle",
                "setup-params" to """{"java-version": "${'$'}{{ matrix.java-version }}"}""",
                "check-command" to "\${{ inputs.gradle-command }}",
            ),
        ),
    ) {}
}
```

- [ ] **Step 4: Create `gradle-plugin-release.main.kts`**

Create `.github/workflow-src/gradle-plugin-release.main.kts`:

```kotlin
#!/usr/bin/env kotlin
@file:Import("_shared.main.kts")

import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig

workflow(
    name = "Gradle Plugin Release",
    on = listOf(),
    sourceFile = __FILE__,
    consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
    _customArguments = mapOf(
        "on" to mapOf(
            "workflow_call" to mapOf(
                "inputs" to mapOf(
                    "java-version" to stringInput(description = "JDK version to use", default = DEFAULT_JAVA_VERSION),
                    "publish-command" to stringInput(
                        description = "Gradle publish command (publishes to both Maven Central and Gradle Portal)",
                        required = true,
                    ),
                    "changelog-config" to stringInput(description = "Path to changelog configuration file", default = DEFAULT_CHANGELOG_CONFIG),
                ),
                "secrets" to mapOf(
                    "MAVEN_SONATYPE_USERNAME" to secretInput("", required = true),
                    "MAVEN_SONATYPE_TOKEN" to secretInput("", required = true),
                    "MAVEN_SONATYPE_SIGNING_KEY_ID" to secretInput("", required = true),
                    "MAVEN_SONATYPE_SIGNING_PUB_KEY_ASCII_ARMORED" to secretInput("", required = true),
                    "MAVEN_SONATYPE_SIGNING_KEY_ASCII_ARMORED" to secretInput("", required = true),
                    "MAVEN_SONATYPE_SIGNING_PASSWORD" to secretInput("", required = true),
                    "GRADLE_PUBLISH_KEY" to secretInput("", required = true),
                    "GRADLE_PUBLISH_SECRET" to secretInput("", required = true),
                ),
            ),
        ),
    ),
) {
    job(
        id = "release",
        runsOn = UbuntuLatest,
        _customArguments = mapOf(
            "uses" to reusableWorkflow("release.yml"),
            "with" to mapOf(
                "changelog-config" to "\${{ inputs.changelog-config }}",
            ),
        ),
    ) {}
    job(
        id = "publish",
        runsOn = UbuntuLatest,
        _customArguments = mapOf(
            "needs" to "release",
            "uses" to reusableWorkflow("publish.yml"),
            "with" to mapOf(
                "setup-action" to "gradle",
                "setup-params" to """{"java-version": "${'$'}{{ inputs.java-version }}"}""",
                "publish-command" to "\${{ inputs.publish-command }}",
            ),
            "secrets" to mapOf(
                "MAVEN_SONATYPE_USERNAME" to "\${{ secrets.MAVEN_SONATYPE_USERNAME }}",
                "MAVEN_SONATYPE_TOKEN" to "\${{ secrets.MAVEN_SONATYPE_TOKEN }}",
                "MAVEN_SONATYPE_SIGNING_KEY_ID" to "\${{ secrets.MAVEN_SONATYPE_SIGNING_KEY_ID }}",
                "MAVEN_SONATYPE_SIGNING_PUB_KEY_ASCII_ARMORED" to "\${{ secrets.MAVEN_SONATYPE_SIGNING_PUB_KEY_ASCII_ARMORED }}",
                "MAVEN_SONATYPE_SIGNING_KEY_ASCII_ARMORED" to "\${{ secrets.MAVEN_SONATYPE_SIGNING_KEY_ASCII_ARMORED }}",
                "MAVEN_SONATYPE_SIGNING_PASSWORD" to "\${{ secrets.MAVEN_SONATYPE_SIGNING_PASSWORD }}",
                "GRADLE_PUBLISH_KEY" to "\${{ secrets.GRADLE_PUBLISH_KEY }}",
                "GRADLE_PUBLISH_SECRET" to "\${{ secrets.GRADLE_PUBLISH_SECRET }}",
            ),
        ),
    ) {}
}
```

- [ ] **Step 5: Generate all 4, move, and compare**

```bash
cd .github/workflow-src
for f in gradle-create-tag gradle-manual-create-tag gradle-plugin-check gradle-plugin-release; do
    kotlin "$f.main.kts" && mv "$f.yaml" "../workflows/$f.yml"
done
cd ../..
git diff .github/workflows/gradle-*.yml
```

- [ ] **Step 6: Commit**

```bash
git add .github/workflow-src/gradle-*.main.kts .github/workflows/gradle-*.yml
git commit -m "ci: migrate Gradle adapter workflows to Kotlin DSL"
```

---

### Task 10: Adapter workflows — Kotlin library group (2 files)

**Files:**
- Create: `.github/workflow-src/kotlin-library-check.main.kts`
- Create: `.github/workflow-src/kotlin-library-release.main.kts`
- Regenerated: corresponding `.github/workflows/*.yml`

- [ ] **Step 1: Create `kotlin-library-check.main.kts`**

Create `.github/workflow-src/kotlin-library-check.main.kts`:

```kotlin
#!/usr/bin/env kotlin
@file:Import("_shared.main.kts")

import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig

workflow(
    name = "Kotlin Library Check",
    on = listOf(),
    sourceFile = __FILE__,
    consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
    _customArguments = mapOf(
        "on" to mapOf(
            "workflow_call" to mapOf(
                "inputs" to mapOf(
                    "java-version" to stringInput(description = "JDK version to use", default = DEFAULT_JAVA_VERSION),
                    "java-versions" to stringInput(description = "JSON array of JDK versions for matrix build (overrides java-version)", default = ""),
                    "gradle-command" to stringInput(description = "Gradle check command", default = "./gradlew check"),
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
    ) {}
    job(
        id = "check",
        runsOn = UbuntuLatest,
        _customArguments = mapOf(
            "strategy" to mapOf(
                "matrix" to mapOf(
                    "java-version" to "\${{ fromJson(inputs.java-versions || format('[\"\\{0}\"]', inputs.java-version)) }}",
                ),
            ),
            "uses" to reusableWorkflow("check.yml"),
            "with" to mapOf(
                "setup-action" to "gradle",
                "setup-params" to """{"java-version": "${'$'}{{ matrix.java-version }}"}""",
                "check-command" to "\${{ inputs.gradle-command }}",
            ),
        ),
    ) {}
}
```

- [ ] **Step 2: Create `kotlin-library-release.main.kts`**

Create `.github/workflow-src/kotlin-library-release.main.kts`:

```kotlin
#!/usr/bin/env kotlin
@file:Import("_shared.main.kts")

import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig

val MAVEN_SECRETS = mapOf(
    "MAVEN_SONATYPE_USERNAME" to secretInput("", required = true),
    "MAVEN_SONATYPE_TOKEN" to secretInput("", required = true),
    "MAVEN_SONATYPE_SIGNING_KEY_ID" to secretInput("", required = true),
    "MAVEN_SONATYPE_SIGNING_PUB_KEY_ASCII_ARMORED" to secretInput("", required = true),
    "MAVEN_SONATYPE_SIGNING_KEY_ASCII_ARMORED" to secretInput("", required = true),
    "MAVEN_SONATYPE_SIGNING_PASSWORD" to secretInput("", required = true),
)

workflow(
    name = "Kotlin Library Release",
    on = listOf(),
    sourceFile = __FILE__,
    consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
    _customArguments = mapOf(
        "on" to mapOf(
            "workflow_call" to mapOf(
                "inputs" to mapOf(
                    "java-version" to stringInput(description = "JDK version to use", default = DEFAULT_JAVA_VERSION),
                    "publish-command" to stringInput(description = "Gradle publish command for Maven Central", required = true),
                    "changelog-config" to stringInput(description = "Path to changelog configuration file", default = DEFAULT_CHANGELOG_CONFIG),
                ),
                "secrets" to MAVEN_SECRETS,
            ),
        ),
    ),
) {
    job(
        id = "release",
        runsOn = UbuntuLatest,
        _customArguments = mapOf(
            "uses" to reusableWorkflow("release.yml"),
            "with" to mapOf(
                "changelog-config" to "\${{ inputs.changelog-config }}",
            ),
        ),
    ) {}
    job(
        id = "publish",
        runsOn = UbuntuLatest,
        _customArguments = mapOf(
            "needs" to "release",
            "uses" to reusableWorkflow("publish.yml"),
            "with" to mapOf(
                "setup-action" to "gradle",
                "setup-params" to """{"java-version": "${'$'}{{ inputs.java-version }}"}""",
                "publish-command" to "\${{ inputs.publish-command }}",
            ),
            "secrets" to mapOf(
                "MAVEN_SONATYPE_USERNAME" to "\${{ secrets.MAVEN_SONATYPE_USERNAME }}",
                "MAVEN_SONATYPE_TOKEN" to "\${{ secrets.MAVEN_SONATYPE_TOKEN }}",
                "MAVEN_SONATYPE_SIGNING_KEY_ID" to "\${{ secrets.MAVEN_SONATYPE_SIGNING_KEY_ID }}",
                "MAVEN_SONATYPE_SIGNING_PUB_KEY_ASCII_ARMORED" to "\${{ secrets.MAVEN_SONATYPE_SIGNING_PUB_KEY_ASCII_ARMORED }}",
                "MAVEN_SONATYPE_SIGNING_KEY_ASCII_ARMORED" to "\${{ secrets.MAVEN_SONATYPE_SIGNING_KEY_ASCII_ARMORED }}",
                "MAVEN_SONATYPE_SIGNING_PASSWORD" to "\${{ secrets.MAVEN_SONATYPE_SIGNING_PASSWORD }}",
            ),
        ),
    ) {}
}
```

- [ ] **Step 3: Generate, move, and compare**

```bash
cd .github/workflow-src
for f in kotlin-library-check kotlin-library-release; do
    kotlin "$f.main.kts" && mv "$f.yaml" "../workflows/$f.yml"
done
cd ../..
git diff .github/workflows/kotlin-library-*.yml
```

- [ ] **Step 4: Commit**

```bash
git add .github/workflow-src/kotlin-library-*.main.kts .github/workflows/kotlin-library-*.yml
git commit -m "ci: migrate Kotlin library adapter workflows to Kotlin DSL"
```

---

### Task 11: Adapter workflows — Go group (2 files)

**Files:**
- Create: `.github/workflow-src/go-create-tag.main.kts`
- Create: `.github/workflow-src/go-manual-create-tag.main.kts`
- Regenerated: corresponding `.github/workflows/*.yml`

- [ ] **Step 1: Create `go-create-tag.main.kts`**

Create `.github/workflow-src/go-create-tag.main.kts`:

```kotlin
#!/usr/bin/env kotlin
@file:Import("_shared.main.kts")

import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig

workflow(
    name = "Go Create Tag",
    on = listOf(),
    sourceFile = __FILE__,
    consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
    _customArguments = mapOf(
        "on" to mapOf(
            "workflow_call" to mapOf(
                "inputs" to mapOf(
                    "go-version" to stringInput(description = "Go version to use", default = DEFAULT_GO_VERSION),
                    "check-command" to stringInput(description = "Go validation command", default = "make test"),
                    "default-bump" to stringInput(description = "Default version bump type (major, minor, patch)", default = "patch"),
                    "tag-prefix" to stringInput(description = "Prefix for the tag", default = "v"),
                    "release-branches" to stringInput(description = "Comma-separated branch patterns for releases", default = DEFAULT_RELEASE_BRANCHES),
                ),
                "secrets" to mapOf(APP_ID_SECRET, APP_PRIVATE_KEY_SECRET),
            ),
        ),
    ),
) {
    job(
        id = "create-tag",
        runsOn = UbuntuLatest,
        _customArguments = mapOf(
            "uses" to reusableWorkflow("create-tag.yml"),
            "with" to mapOf(
                "setup-action" to "go",
                "setup-params" to """{"go-version": "${'$'}{{ inputs.go-version }}"}""",
                "check-command" to "\${{ inputs.check-command }}",
                "default-bump" to "\${{ inputs.default-bump }}",
                "tag-prefix" to "\${{ inputs.tag-prefix }}",
                "release-branches" to "\${{ inputs.release-branches }}",
            ),
            "secrets" to mapOf(
                "app-id" to "\${{ secrets.app-id }}",
                "app-private-key" to "\${{ secrets.app-private-key }}",
            ),
        ),
    ) {}
}
```

- [ ] **Step 2: Create `go-manual-create-tag.main.kts`**

Create `.github/workflow-src/go-manual-create-tag.main.kts`:

```kotlin
#!/usr/bin/env kotlin
@file:Import("_shared.main.kts")

import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig

workflow(
    name = "Go Manual Create Tag",
    on = listOf(),
    sourceFile = __FILE__,
    consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
    _customArguments = mapOf(
        "on" to mapOf(
            "workflow_call" to mapOf(
                "inputs" to mapOf(
                    "tag-version" to stringInput(description = "Version to tag (e.g. 1.2.3)", required = true),
                    "go-version" to stringInput(description = "Go version to use", default = DEFAULT_GO_VERSION),
                    "check-command" to stringInput(description = "Go validation command", default = "make test"),
                    "tag-prefix" to stringInput(description = "Prefix for the tag", default = "v"),
                ),
                "secrets" to mapOf(APP_ID_SECRET, APP_PRIVATE_KEY_SECRET),
            ),
        ),
    ),
) {
    job(
        id = "manual-tag",
        runsOn = UbuntuLatest,
        _customArguments = mapOf(
            "uses" to reusableWorkflow("manual-create-tag.yml"),
            "with" to mapOf(
                "tag-version" to "\${{ inputs.tag-version }}",
                "tag-prefix" to "\${{ inputs.tag-prefix }}",
                "setup-action" to "go",
                "setup-params" to """{"go-version": "${'$'}{{ inputs.go-version }}"}""",
                "check-command" to "\${{ inputs.check-command }}",
            ),
            "secrets" to mapOf(
                "app-id" to "\${{ secrets.app-id }}",
                "app-private-key" to "\${{ secrets.app-private-key }}",
            ),
        ),
    ) {}
}
```

- [ ] **Step 3: Generate, move, and compare**

```bash
cd .github/workflow-src
for f in go-create-tag go-manual-create-tag; do
    kotlin "$f.main.kts" && mv "$f.yaml" "../workflows/$f.yml"
done
cd ../..
git diff .github/workflows/go-*.yml
```

- [ ] **Step 4: Commit**

```bash
git add .github/workflow-src/go-*.main.kts .github/workflows/go-*.yml
git commit -m "ci: migrate Go adapter workflows to Kotlin DSL"
```

---

### Task 12: Adapter workflows — App group (3 files)

**Files:**
- Create: `.github/workflow-src/app-check.main.kts`
- Create: `.github/workflow-src/app-release.main.kts`
- Create: `.github/workflow-src/app-deploy.main.kts`
- Regenerated: corresponding `.github/workflows/*.yml`

- [ ] **Step 1: Create `app-check.main.kts`**

Create `.github/workflow-src/app-check.main.kts`:

```kotlin
#!/usr/bin/env kotlin
@file:Import("_shared.main.kts")

import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig

workflow(
    name = "Application Check",
    on = listOf(),
    sourceFile = __FILE__,
    consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
    _customArguments = mapOf(
        "on" to mapOf(
            "workflow_call" to mapOf(
                "inputs" to mapOf(
                    "java-version" to stringInput(description = "JDK version to use", default = DEFAULT_JAVA_VERSION),
                    "java-versions" to stringInput(description = "JSON array of JDK versions for matrix build (overrides java-version)", default = ""),
                    "gradle-command" to stringInput(description = "Gradle check command", default = "./gradlew check"),
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
    ) {}
    job(
        id = "check",
        runsOn = UbuntuLatest,
        _customArguments = mapOf(
            "strategy" to mapOf(
                "matrix" to mapOf(
                    "java-version" to "\${{ fromJson(inputs.java-versions || format('[\"\\{0}\"]', inputs.java-version)) }}",
                ),
            ),
            "uses" to reusableWorkflow("check.yml"),
            "with" to mapOf(
                "setup-action" to "gradle",
                "setup-params" to """{"java-version": "${'$'}{{ matrix.java-version }}"}""",
                "check-command" to "\${{ inputs.gradle-command }}",
            ),
        ),
    ) {}
}
```

- [ ] **Step 2: Create `app-release.main.kts`**

Create `.github/workflow-src/app-release.main.kts`:

```kotlin
#!/usr/bin/env kotlin
@file:Import("_shared.main.kts")

import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig

workflow(
    name = "Application Release",
    on = listOf(),
    sourceFile = __FILE__,
    consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
    _customArguments = mapOf(
        "on" to mapOf(
            "workflow_call" to mapOf(
                "inputs" to mapOf(
                    "changelog-config" to stringInput(description = "Path to changelog configuration file", default = DEFAULT_CHANGELOG_CONFIG),
                    "draft" to booleanInput(description = "Create release as draft (default true for apps)", default = true),
                ),
            ),
        ),
    ),
) {
    job(
        id = "release",
        runsOn = UbuntuLatest,
        _customArguments = mapOf(
            "uses" to reusableWorkflow("release.yml"),
            "with" to mapOf(
                "changelog-config" to "\${{ inputs.changelog-config }}",
                "draft" to "\${{ inputs.draft }}",
            ),
        ),
    ) {}
}
```

- [ ] **Step 3: Create `app-deploy.main.kts`**

Create `.github/workflow-src/app-deploy.main.kts`:

```kotlin
#!/usr/bin/env kotlin
@file:Import("_shared.main.kts")

import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig

workflow(
    name = "Application Deploy",
    on = listOf(),
    sourceFile = __FILE__,
    consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
    _customArguments = mapOf(
        "on" to mapOf(
            "workflow_call" to mapOf(
                "inputs" to mapOf(
                    SETUP_ACTION_INPUT,
                    SETUP_PARAMS_INPUT,
                    "deploy-command" to stringInput(description = "Command to run for deployment", required = true),
                    "tag" to stringInput(description = "Tag/version to deploy (checked out at this ref)", required = true),
                ),
            ),
        ),
        "permissions" to mapOf("contents" to "read"),
    ),
) {
    job(
        id = "deploy",
        name = "Deploy",
        runsOn = UbuntuLatest,
        _customArguments = mapOf(
            "steps" to (conditionalSetupSteps(fetchDepth = "0") + listOf(
                mapOf(
                    "name" to "Checkout tag",
                    "run" to """git checkout "${'$'}{{ inputs.tag }}"""",
                ),
                mapOf(
                    "name" to "Deploy",
                    "run" to "\${{ inputs.deploy-command }}",
                ),
            )),
        ),
    ) {}
}
```

- [ ] **Step 4: Generate all 3, move, and compare**

```bash
cd .github/workflow-src
for f in app-check app-release app-deploy; do
    kotlin "$f.main.kts" && mv "$f.yaml" "../workflows/$f.yml"
done
cd ../..
git diff .github/workflows/app-*.yml
```

- [ ] **Step 5: Commit**

```bash
git add .github/workflow-src/app-*.main.kts .github/workflows/app-*.yml
git commit -m "ci: migrate App adapter workflows to Kotlin DSL"
```

---

### Task 13: CI bootstrap — `verify-workflows.yml`

**Files:**
- Create: `.github/workflows/verify-workflows.yml` (hand-written YAML)

- [ ] **Step 1: Create `verify-workflows.yml`**

Create `.github/workflows/verify-workflows.yml`:

```yaml
name: 'Verify Workflows'

on:
  pull_request:
    paths:
      - '.github/workflow-src/**'
      - '.github/workflows/**'
  push:
    branches: ['main']
    paths:
      - '.github/workflow-src/**'
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

      - name: 'Install Kotlin'
        run: |
          curl -s https://get.sdkman.io | bash
          source "$HOME/.sdkman/bin/sdkman-init.sh"
          sdk install kotlin
          echo "$HOME/.sdkman/candidates/kotlin/current/bin" >> $GITHUB_PATH

      - name: 'Generate workflows'
        run: .github/workflow-src/generate.sh

      - name: 'Check for differences'
        run: |
          if ! git diff --exit-code .github/workflows/; then
            echo "::error::Generated workflow YAML files are out of date. Run .github/workflow-src/generate.sh locally and commit the results."
            exit 1
          fi
          echo "All generated workflows are up-to-date."
```

- [ ] **Step 2: Commit**

```bash
git add .github/workflows/verify-workflows.yml
git commit -m "ci: add verify-workflows CI check for Kotlin DSL consistency"
```

---

### Task 14: Full generation validation

**Files:** All `.github/workflow-src/*.main.kts` and `.github/workflows/*.yml`

- [ ] **Step 1: Run full generation**

```bash
cd .github/workflow-src && ./generate.sh
```

Expected: All scripts execute without errors, all YAML files generated and moved.

- [ ] **Step 2: Verify no functional differences**

```bash
git diff .github/workflows/
```

Review the diff. Acceptable: formatting differences (whitespace, quoting, key ordering). Unacceptable: changed logic, missing inputs/secrets, renamed jobs/steps.

- [ ] **Step 3: Fix any issues found**

If there are logic differences, fix the corresponding `.main.kts` file and regenerate. Repeat until diff shows only formatting changes.

- [ ] **Step 4: Final commit**

```bash
git add .github/workflow-src/ .github/workflows/
git commit -m "ci: complete github-workflows-kt migration — all 20 workflows"
```
