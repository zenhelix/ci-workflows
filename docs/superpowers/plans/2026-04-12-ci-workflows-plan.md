# CI Workflows Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create reusable GitHub Actions workflows in ci-workflows repo (Kotlin DSL) and org-wide community health files in zenhelix/.github repo.

**Architecture:** Five reusable workflows as composable bricks, each generated from a `.main.kts` Kotlin script using github-workflows-kt. Each workflow uses `workflow_call` trigger with typed inputs/secrets. A separate `.github` repo provides org-wide default templates.

**Tech Stack:** github-workflows-kt 3.7.0, Kotlin Script (.main.kts), GitHub Actions, GitHub Reusable Workflows

---

## File Map

### ci-workflows repo (`/Users/dmitriimedakin/IdeaProjects/zenhelix/ci-workflows`)

| File | Responsibility |
|---|---|
| `.github/workflows/workflow.gradle-check.main.kts` | Kotlin DSL source for gradle-check reusable workflow |
| `.github/workflows/gradle-check.yml` | Generated YAML — build & test |
| `.github/workflows/workflow.gradle-create-tag.main.kts` | Kotlin DSL source for gradle-create-tag reusable workflow |
| `.github/workflows/gradle-create-tag.yml` | Generated YAML — build + version bump + tag push |
| `.github/workflows/workflow.release-github.main.kts` | Kotlin DSL source for release-github reusable workflow |
| `.github/workflows/release-github.yml` | Generated YAML — changelog + GitHub release |
| `.github/workflows/workflow.release-publish-gradle.main.kts` | Kotlin DSL source for release-publish-gradle reusable workflow |
| `.github/workflows/release-publish-gradle.yml` | Generated YAML — Gradle publish |
| `.github/workflows/workflow.labeler.main.kts` | Kotlin DSL source for labeler reusable workflow |
| `.github/workflows/labeler.yml` | Generated YAML — PR auto-labeling |
| `.github/changelog-config.json` | Default changelog categories config |
| `.github/labeler.yml` | Default labeler file-path rules |

### .github repo (`/Users/dmitriimedakin/IdeaProjects/zenhelix/.github`)

| File | Responsibility |
|---|---|
| `profile/README.md` | Organization profile page on GitHub |
| `.github/ISSUE_TEMPLATE/bug_report.md` | Default bug report issue template |
| `.github/PULL_REQUEST_TEMPLATE.md` | Default pull request template |
| `CONTRIBUTING.md` | Contributing guidelines |
| `CODE_OF_CONDUCT.md` | Code of conduct |

---

## Task 1: Create gradle-check reusable workflow

**Repo:** ci-workflows

**Files:**
- Create: `.github/workflows/workflow.gradle-check.main.kts`
- Create: `.github/workflows/gradle-check.yml` (generated)

- [ ] **Step 1: Write the Kotlin DSL script**

Create `.github/workflows/workflow.gradle-check.main.kts`:

```kotlin
#!/usr/bin/env kotlin

// github-workflows-kt
@file:Repository("https://repo.maven.apache.org/maven2/")
@file:DependsOn("io.github.typesafegithub:github-workflows-kt:3.7.0")
@file:Repository("https://bindings.krzeminski.it")

// Actions
@file:DependsOn("actions:checkout:v6")
@file:DependsOn("actions:setup-java:v5")
@file:DependsOn("gradle:actions__setup-gradle:v5")

import io.github.typesafegithub.workflows.actions.actions.Checkout
import io.github.typesafegithub.workflows.actions.actions.SetupJava
import io.github.typesafegithub.workflows.actions.actions.SetupJava.Distribution.Temurin
import io.github.typesafegithub.workflows.actions.gradle.ActionsSetupGradle
import io.github.typesafegithub.workflows.domain.Mode.Read
import io.github.typesafegithub.workflows.domain.Permission.Contents
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.dsl.expressions.expr
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig.Disabled

check(KotlinVersion.CURRENT.isAtLeast(2, 1, 0)) {
    "This script requires Kotlin 2.1.0 or later. Current: ${KotlinVersion.CURRENT}"
}

workflow(
    name = "Gradle Check",
    on = listOf(
        WorkflowCall(
            inputs = mapOf(
                "java-version" to WorkflowCall.Input(
                    description = "JDK version to use",
                    required = false,
                    type = WorkflowCall.Type.String,
                    default = "17"
                ),
                "gradle-command" to WorkflowCall.Input(
                    description = "Gradle command to run",
                    required = false,
                    type = WorkflowCall.Type.String,
                    default = "./gradlew check"
                )
            )
        )
    ),
    permissions = mapOf(Contents to Read),
    sourceFile = __FILE__,
    targetFileName = "gradle-check.yml",
    consistencyCheckJobConfig = Disabled
) {
    job(id = "check", name = "Check", runsOn = UbuntuLatest) {
        uses(name = "Check out", action = Checkout())
        uses(
            name = "Set up Java",
            action = SetupJava(
                javaVersion = expr { "inputs.java-version" },
                distribution = Temurin
            )
        )
        uses(
            name = "Setup Gradle",
            action = ActionsSetupGradle(cacheReadOnly = true, gradleHomeCacheCleanup = true)
        )
        run(name = "Check", command = expr { "inputs.gradle-command" })
    }
}
```

- [ ] **Step 2: Generate the YAML**

```bash
cd /Users/dmitriimedakin/IdeaProjects/zenhelix/ci-workflows
kotlin .github/workflows/workflow.gradle-check.main.kts
```

Expected: `gradle-check.yml` created in `.github/workflows/`

- [ ] **Step 3: Verify generated YAML**

Read `.github/workflows/gradle-check.yml` and verify it contains:
- `on: workflow_call` with `inputs` section
- `java-version` and `gradle-command` inputs with defaults
- Steps: checkout, setup-java, setup-gradle, run check

- [ ] **Step 4: Commit**

```bash
cd /Users/dmitriimedakin/IdeaProjects/zenhelix/ci-workflows
git add .github/workflows/workflow.gradle-check.main.kts .github/workflows/gradle-check.yml
git commit -m "feat: add gradle-check reusable workflow"
```

---

## Task 2: Create gradle-create-tag reusable workflow

**Repo:** ci-workflows

**Files:**
- Create: `.github/workflows/workflow.gradle-create-tag.main.kts`
- Create: `.github/workflows/gradle-create-tag.yml` (generated)

- [ ] **Step 1: Write the Kotlin DSL script**

Create `.github/workflows/workflow.gradle-create-tag.main.kts`:

```kotlin
#!/usr/bin/env kotlin

// github-workflows-kt
@file:Repository("https://repo.maven.apache.org/maven2/")
@file:DependsOn("io.github.typesafegithub:github-workflows-kt:3.7.0")
@file:Repository("https://bindings.krzeminski.it")

// Actions
@file:DependsOn("actions:checkout:v6")
@file:DependsOn("actions:setup-java:v5")
@file:DependsOn("gradle:actions__setup-gradle:v5")
@file:DependsOn("actions:create-github-app-token:v2")
@file:DependsOn("mathieudutour:github-tag-action:v6.2")

import io.github.typesafegithub.workflows.actions.actions.Checkout
import io.github.typesafegithub.workflows.actions.actions.Checkout.FetchDepth
import io.github.typesafegithub.workflows.actions.actions.CreateGithubAppToken
import io.github.typesafegithub.workflows.actions.actions.SetupJava
import io.github.typesafegithub.workflows.actions.actions.SetupJava.Distribution.Temurin
import io.github.typesafegithub.workflows.actions.gradle.ActionsSetupGradle
import io.github.typesafegithub.workflows.actions.mathieudutour.GithubTagAction_Untyped
import io.github.typesafegithub.workflows.domain.Mode.Write
import io.github.typesafegithub.workflows.domain.Permission.Contents
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.dsl.expressions.expr
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig.Disabled

check(KotlinVersion.CURRENT.isAtLeast(2, 1, 0)) {
    "This script requires Kotlin 2.1.0 or later. Current: ${KotlinVersion.CURRENT}"
}

workflow(
    name = "Gradle Create Tag",
    on = listOf(
        WorkflowCall(
            inputs = mapOf(
                "java-version" to WorkflowCall.Input(
                    description = "JDK version to use",
                    required = false,
                    type = WorkflowCall.Type.String,
                    default = "17"
                ),
                "gradle-command" to WorkflowCall.Input(
                    description = "Gradle command to run",
                    required = false,
                    type = WorkflowCall.Type.String,
                    default = "./gradlew check"
                ),
                "default-bump" to WorkflowCall.Input(
                    description = "Default version bump type (major, minor, patch)",
                    required = false,
                    type = WorkflowCall.Type.String,
                    default = "patch"
                ),
                "tag-prefix" to WorkflowCall.Input(
                    description = "Prefix for the tag (e.g. 'v')",
                    required = false,
                    type = WorkflowCall.Type.String,
                    default = ""
                ),
                "release-branches" to WorkflowCall.Input(
                    description = "Comma-separated list of branch patterns for releases",
                    required = false,
                    type = WorkflowCall.Type.String,
                    default = "main,[0-9]+\\.x"
                )
            ),
            secrets = mapOf(
                "app-id" to WorkflowCall.Secret(
                    description = "GitHub App ID for generating commit token",
                    required = true
                ),
                "app-private-key" to WorkflowCall.Secret(
                    description = "GitHub App private key for generating commit token",
                    required = true
                )
            )
        )
    ),
    permissions = mapOf(Contents to Write),
    sourceFile = __FILE__,
    targetFileName = "gradle-create-tag.yml",
    consistencyCheckJobConfig = Disabled
) {
    job(id = "create_tag", name = "Create Tag", runsOn = UbuntuLatest) {
        uses(name = "Check out", action = Checkout(fetchDepth = FetchDepth.Value(0)))
        uses(
            name = "Set up Java",
            action = SetupJava(
                javaVersion = expr { "inputs.java-version" },
                distribution = Temurin
            )
        )
        uses(
            name = "Setup Gradle",
            action = ActionsSetupGradle(gradleHomeCacheCleanup = true)
        )
        run(name = "Check", command = expr { "inputs.gradle-command" })
        val token = uses(
            name = "Generate App Token",
            action = CreateGithubAppToken(
                appId = expr { "secrets.app-id" },
                privateKey = expr { "secrets.app-private-key" }
            )
        )
        uses(
            name = "Bump version and push tag",
            action = GithubTagAction_Untyped(
                githubToken_Untyped = expr { token.outputs["token"] },
                defaultBump_Untyped = expr { "inputs.default-bump" },
                tagPrefix_Untyped = expr { "inputs.tag-prefix" },
                releaseBranches_Untyped = expr { "inputs.release-branches" }
            )
        )
    }
}
```

- [ ] **Step 2: Generate the YAML**

```bash
cd /Users/dmitriimedakin/IdeaProjects/zenhelix/ci-workflows
kotlin .github/workflows/workflow.gradle-create-tag.main.kts
```

Expected: `gradle-create-tag.yml` created in `.github/workflows/`

- [ ] **Step 3: Verify generated YAML**

Read `.github/workflows/gradle-create-tag.yml` and verify it contains:
- `on: workflow_call` with 5 inputs and 2 secrets
- Steps: checkout (depth 0), setup-java, setup-gradle, check, generate app token, bump version
- `actions/create-github-app-token@v2` instead of old peter-murray action
- `mathieudutour/github-tag-action@v6.2` instead of old anothrNick action

- [ ] **Step 4: Commit**

```bash
cd /Users/dmitriimedakin/IdeaProjects/zenhelix/ci-workflows
git add .github/workflows/workflow.gradle-create-tag.main.kts .github/workflows/gradle-create-tag.yml
git commit -m "feat: add gradle-create-tag reusable workflow"
```

---

## Task 3: Create release-github reusable workflow

**Repo:** ci-workflows

**Files:**
- Create: `.github/workflows/workflow.release-github.main.kts`
- Create: `.github/workflows/release-github.yml` (generated)
- Create: `.github/changelog-config.json`

- [ ] **Step 1: Create default changelog config**

Create `.github/changelog-config.json`:

```json
{
  "categories": [
    {
      "title": "## Breaking Changes",
      "labels": ["breaking-change"]
    },
    {
      "title": "## New Features",
      "labels": ["feature", "enhancement", "feat"]
    },
    {
      "title": "## Bug Fixes",
      "labels": ["bug", "fix"]
    },
    {
      "title": "## Build & CI",
      "labels": ["build", "ci"]
    },
    {
      "title": "## Documentation",
      "labels": ["documentation", "docs"]
    },
    {
      "title": "## Dependencies",
      "labels": ["dependencies"]
    },
    {
      "title": "## Other Changes",
      "labels": []
    }
  ],
  "ignore_labels": ["ignore-changelog"],
  "sort": {
    "order": "ASC",
    "on_property": "mergedAt"
  },
  "template": "#{{CHANGELOG}}\n\n**Full Changelog**: #{{RELEASE_DIFF}}",
  "pr_template": "- #{{TITLE}} (#{{NUMBER}}) @#{{AUTHOR}}",
  "empty_template": "No changes.",
  "label_extractor": [
    {
      "pattern": "^(build|ci|docs|feat|fix|perf|refactor|style|test)(\\(.*\\))?!?:",
      "target": "$1",
      "on_property": "title",
      "method": "match",
      "flags": "i"
    }
  ],
  "transformers": [
    {
      "pattern": "^(build|ci|docs|feat|fix|perf|refactor|style|test)(\\(.*\\))?!?:\\s*",
      "target": ""
    }
  ],
  "max_tags_to_fetch": 200,
  "max_pull_requests": 200,
  "max_back_track_time_days": 365,
  "exclude_merge_branches": [],
  "tag_resolver": {
    "method": "semver"
  },
  "base_branches": ["main"]
}
```

- [ ] **Step 2: Write the Kotlin DSL script**

Create `.github/workflows/workflow.release-github.main.kts`:

```kotlin
#!/usr/bin/env kotlin

// github-workflows-kt
@file:Repository("https://repo.maven.apache.org/maven2/")
@file:DependsOn("io.github.typesafegithub:github-workflows-kt:3.7.0")
@file:Repository("https://bindings.krzeminski.it")

// Actions
@file:DependsOn("actions:checkout:v6")
@file:DependsOn("softprops:action-gh-release:v2")
@file:DependsOn("mikepenz:release-changelog-builder-action:v6")

import io.github.typesafegithub.workflows.actions.actions.Checkout
import io.github.typesafegithub.workflows.actions.actions.Checkout.FetchDepth
import io.github.typesafegithub.workflows.actions.mikepenz.ReleaseChangelogBuilderAction_Untyped
import io.github.typesafegithub.workflows.actions.softprops.ActionGhRelease
import io.github.typesafegithub.workflows.domain.Mode.Write
import io.github.typesafegithub.workflows.domain.Permission.Contents
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.dsl.expressions.expr
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig.Disabled

check(KotlinVersion.CURRENT.isAtLeast(2, 1, 0)) {
    "This script requires Kotlin 2.1.0 or later. Current: ${KotlinVersion.CURRENT}"
}

workflow(
    name = "GitHub Release",
    on = listOf(
        WorkflowCall(
            inputs = mapOf(
                "changelog-config" to WorkflowCall.Input(
                    description = "Path to changelog configuration file",
                    required = false,
                    type = WorkflowCall.Type.String,
                    default = ".github/changelog-config.json"
                )
            )
        )
    ),
    permissions = mapOf(Contents to Write),
    sourceFile = __FILE__,
    targetFileName = "release-github.yml",
    consistencyCheckJobConfig = Disabled
) {
    job(id = "release", name = "GitHub Release", runsOn = UbuntuLatest) {
        uses(name = "Check out", action = Checkout(fetchDepth = FetchDepth.Value(0)))

        val tag = expr { github.ref_name }

        val changelogBuilder = uses(
            name = "Build Changelog",
            action = ReleaseChangelogBuilderAction_Untyped(
                configuration_Untyped = expr { "inputs.changelog-config" },
                toTag_Untyped = tag
            ),
            env = mapOf("GITHUB_TOKEN" to expr { secrets.GITHUB_TOKEN })
        )

        uses(
            name = "Create Release",
            action = ActionGhRelease(
                tagName = tag,
                name = tag,
                body = expr { changelogBuilder.outputs["changelog"] },
                draft = false
            ),
            env = mapOf("GITHUB_TOKEN" to expr { secrets.GITHUB_TOKEN })
        )
    }
}
```

- [ ] **Step 3: Generate the YAML**

```bash
cd /Users/dmitriimedakin/IdeaProjects/zenhelix/ci-workflows
kotlin .github/workflows/workflow.release-github.main.kts
```

Expected: `release-github.yml` created in `.github/workflows/`

- [ ] **Step 4: Verify generated YAML**

Read `.github/workflows/release-github.yml` and verify it contains:
- `on: workflow_call` with `changelog-config` input
- Steps: checkout (depth 0), build changelog, create release
- Uses `github.ref_name` for tag
- Uses `secrets.GITHUB_TOKEN` for auth

- [ ] **Step 5: Commit**

```bash
cd /Users/dmitriimedakin/IdeaProjects/zenhelix/ci-workflows
git add .github/changelog-config.json .github/workflows/workflow.release-github.main.kts .github/workflows/release-github.yml
git commit -m "feat: add release-github reusable workflow with changelog config"
```

---

## Task 4: Create release-publish-gradle reusable workflow

**Repo:** ci-workflows

**Files:**
- Create: `.github/workflows/workflow.release-publish-gradle.main.kts`
- Create: `.github/workflows/release-publish-gradle.yml` (generated)

- [ ] **Step 1: Write the Kotlin DSL script**

Create `.github/workflows/workflow.release-publish-gradle.main.kts`:

```kotlin
#!/usr/bin/env kotlin

// github-workflows-kt
@file:Repository("https://repo.maven.apache.org/maven2/")
@file:DependsOn("io.github.typesafegithub:github-workflows-kt:3.7.0")
@file:Repository("https://bindings.krzeminski.it")

// Actions
@file:DependsOn("actions:checkout:v6")
@file:DependsOn("actions:setup-java:v5")
@file:DependsOn("gradle:actions__setup-gradle:v5")

import io.github.typesafegithub.workflows.actions.actions.Checkout
import io.github.typesafegithub.workflows.actions.actions.SetupJava
import io.github.typesafegithub.workflows.actions.actions.SetupJava.Distribution.Temurin
import io.github.typesafegithub.workflows.actions.gradle.ActionsSetupGradle
import io.github.typesafegithub.workflows.domain.Mode.Read
import io.github.typesafegithub.workflows.domain.Permission.Contents
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.dsl.expressions.expr
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig.Disabled

check(KotlinVersion.CURRENT.isAtLeast(2, 1, 0)) {
    "This script requires Kotlin 2.1.0 or later. Current: ${KotlinVersion.CURRENT}"
}

workflow(
    name = "Gradle Publish",
    on = listOf(
        WorkflowCall(
            inputs = mapOf(
                "java-version" to WorkflowCall.Input(
                    description = "JDK version to use",
                    required = false,
                    type = WorkflowCall.Type.String,
                    default = "17"
                ),
                "publish-command" to WorkflowCall.Input(
                    description = "Gradle publish command (e.g. './gradlew publish', './gradlew publishPlugins')",
                    required = true,
                    type = WorkflowCall.Type.String
                )
            ),
            secrets = mapOf(
                "GRADLE_PUBLISH_KEY" to WorkflowCall.Secret(
                    description = "Gradle Plugin Portal publish key",
                    required = false
                ),
                "GRADLE_PUBLISH_SECRET" to WorkflowCall.Secret(
                    description = "Gradle Plugin Portal publish secret",
                    required = false
                ),
                "MAVEN_SONATYPE_SIGNING_KEY_ID" to WorkflowCall.Secret(
                    description = "GPG signing key ID",
                    required = false
                ),
                "MAVEN_SONATYPE_SIGNING_PUB_KEY_ASCII_ARMORED" to WorkflowCall.Secret(
                    description = "GPG signing public key (ASCII armored)",
                    required = false
                ),
                "MAVEN_SONATYPE_SIGNING_KEY_ASCII_ARMORED" to WorkflowCall.Secret(
                    description = "GPG signing private key (ASCII armored)",
                    required = false
                ),
                "MAVEN_SONATYPE_SIGNING_PASSWORD" to WorkflowCall.Secret(
                    description = "GPG signing key passphrase",
                    required = false
                )
            )
        )
    ),
    permissions = mapOf(Contents to Read),
    sourceFile = __FILE__,
    targetFileName = "release-publish-gradle.yml",
    consistencyCheckJobConfig = Disabled
) {
    job(id = "publish", name = "Publish", runsOn = UbuntuLatest) {
        uses(name = "Check out", action = Checkout())
        uses(
            name = "Set up Java",
            action = SetupJava(
                javaVersion = expr { "inputs.java-version" },
                distribution = Temurin
            )
        )
        uses(
            name = "Setup Gradle",
            action = ActionsSetupGradle(gradleHomeCacheCleanup = true)
        )
        run(
            name = "Publish",
            command = expr { "inputs.publish-command" },
            env = mapOf(
                "GRADLE_PUBLISH_KEY" to expr { "secrets.GRADLE_PUBLISH_KEY" },
                "GRADLE_PUBLISH_SECRET" to expr { "secrets.GRADLE_PUBLISH_SECRET" },
                "ORG_GRADLE_PROJECT_signingKeyId" to expr { "secrets.MAVEN_SONATYPE_SIGNING_KEY_ID" },
                "ORG_GRADLE_PROJECT_signingPublicKey" to expr { "secrets.MAVEN_SONATYPE_SIGNING_PUB_KEY_ASCII_ARMORED" },
                "ORG_GRADLE_PROJECT_signingKey" to expr { "secrets.MAVEN_SONATYPE_SIGNING_KEY_ASCII_ARMORED" },
                "ORG_GRADLE_PROJECT_signingPassword" to expr { "secrets.MAVEN_SONATYPE_SIGNING_PASSWORD" }
            )
        )
    }
}
```

- [ ] **Step 2: Generate the YAML**

```bash
cd /Users/dmitriimedakin/IdeaProjects/zenhelix/ci-workflows
kotlin .github/workflows/workflow.release-publish-gradle.main.kts
```

Expected: `release-publish-gradle.yml` created in `.github/workflows/`

- [ ] **Step 3: Verify generated YAML**

Read `.github/workflows/release-publish-gradle.yml` and verify it contains:
- `on: workflow_call` with 2 inputs and 6 secrets
- `publish-command` input marked as required
- All secrets marked as `required: false`
- Secrets mapped to environment variable names (especially `ORG_GRADLE_PROJECT_*` prefixes)

- [ ] **Step 4: Commit**

```bash
cd /Users/dmitriimedakin/IdeaProjects/zenhelix/ci-workflows
git add .github/workflows/workflow.release-publish-gradle.main.kts .github/workflows/release-publish-gradle.yml
git commit -m "feat: add release-publish-gradle reusable workflow"
```

---

## Task 5: Create labeler reusable workflow

**Repo:** ci-workflows

**Files:**
- Create: `.github/workflows/workflow.labeler.main.kts`
- Create: `.github/workflows/labeler.yml` (generated)
- Create: `.github/labeler.yml` (default config)

- [ ] **Step 1: Create default labeler config**

Create `.github/labeler.yml`:

```yaml
# Default labeler configuration for zenhelix projects
# Override per-repo by creating .github/labeler.yml in the project

# Source code
source:
  - changed-files:
      - any-glob-to-any-file:
          - 'src/main/**/*.kt'
          - 'src/main/**/*.java'

# Test changes
tests:
  - changed-files:
      - any-glob-to-any-file:
          - 'src/test/**/*'
          - 'src/functionalTest/**/*'

# Build configuration
build:
  - changed-files:
      - any-glob-to-any-file:
          - 'build.gradle.kts'
          - 'settings.gradle.kts'
          - 'gradle.properties'
          - 'gradle/**/*'

# CI/CD changes
ci:
  - changed-files:
      - any-glob-to-any-file:
          - '.github/**/*'

# Documentation
documentation:
  - changed-files:
      - any-glob-to-any-file:
          - '*.md'
          - '*.adoc'
          - 'docs/**/*'
          - 'LICENSE'

# Dependencies
dependencies:
  - changed-files:
      - any-glob-to-any-file:
          - 'build.gradle.kts'
          - 'gradle/libs.versions.toml'
```

- [ ] **Step 2: Write the Kotlin DSL script**

Create `.github/workflows/workflow.labeler.main.kts`:

```kotlin
#!/usr/bin/env kotlin

// github-workflows-kt
@file:Repository("https://repo.maven.apache.org/maven2/")
@file:DependsOn("io.github.typesafegithub:github-workflows-kt:3.7.0")
@file:Repository("https://bindings.krzeminski.it")

// Actions
@file:DependsOn("actions:labeler:v6")

import io.github.typesafegithub.workflows.actions.actions.Labeler
import io.github.typesafegithub.workflows.domain.Mode.Write
import io.github.typesafegithub.workflows.domain.Permission.Contents
import io.github.typesafegithub.workflows.domain.Permission.PullRequests
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.dsl.expressions.expr
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig.Disabled

check(KotlinVersion.CURRENT.isAtLeast(2, 1, 0)) {
    "This script requires Kotlin 2.1.0 or later. Current: ${KotlinVersion.CURRENT}"
}

workflow(
    name = "PR Labeler",
    on = listOf(
        WorkflowCall(
            inputs = mapOf(
                "config-path" to WorkflowCall.Input(
                    description = "Path to labeler configuration file",
                    required = false,
                    type = WorkflowCall.Type.String,
                    default = ".github/labeler.yml"
                )
            )
        )
    ),
    permissions = mapOf(
        Contents to Write,
        PullRequests to Write
    ),
    sourceFile = __FILE__,
    targetFileName = "labeler.yml",
    consistencyCheckJobConfig = Disabled
) {
    job(id = "label", name = "Label PR", runsOn = UbuntuLatest) {
        uses(
            name = "Label PR based on file paths",
            action = Labeler(
                repoToken = expr { secrets.GITHUB_TOKEN },
                syncLabels = true
            )
        )
    }
}
```

Note: the `config-path` input is defined but the `Labeler` action reads `.github/labeler.yml` from the calling repo by default. If the calling repo has its own `.github/labeler.yml`, it takes priority. The input is available for non-standard config locations.

- [ ] **Step 3: Generate the YAML**

```bash
cd /Users/dmitriimedakin/IdeaProjects/zenhelix/ci-workflows
kotlin .github/workflows/workflow.labeler.main.kts
```

Expected: `labeler.yml` created in `.github/workflows/`

- [ ] **Step 4: Verify generated YAML**

Read `.github/workflows/labeler.yml` and verify it contains:
- `on: workflow_call` with `config-path` input
- Permissions: contents write, pull-requests write
- Step using `actions/labeler@v6`

- [ ] **Step 5: Commit**

```bash
cd /Users/dmitriimedakin/IdeaProjects/zenhelix/ci-workflows
git add .github/labeler.yml .github/workflows/workflow.labeler.main.kts .github/workflows/labeler.yml
git commit -m "feat: add labeler reusable workflow with default config"
```

---

## Task 6: Set up zenhelix/.github repository

**Repo:** .github (`/Users/dmitriimedakin/IdeaProjects/zenhelix/.github`)

**Files:**
- Create: `profile/README.md`
- Create: `.github/ISSUE_TEMPLATE/bug_report.md`
- Create: `.github/PULL_REQUEST_TEMPLATE.md`
- Create: `CONTRIBUTING.md`
- Create: `CODE_OF_CONDUCT.md`

- [ ] **Step 1: Create organization profile README**

Create `profile/README.md`:

```markdown
# ZenHelix

Open-source Gradle plugins and Kotlin libraries.

## Projects

| Project | Description |
|---|---|
| [gradle-magic-wands](https://github.com/zenhelix/gradle-magic-wands) | Gradle plugins |
| [maven-central-publish](https://github.com/zenhelix/maven-central-publish) | Maven Central publishing Gradle plugin |
| [zenhelix-ktlint-rules](https://github.com/zenhelix/zenhelix-ktlint-rules) | KtLint rules |
```

- [ ] **Step 2: Create default bug report template**

Create `.github/ISSUE_TEMPLATE/bug_report.md`:

```markdown
---
name: Bug Report
about: Report a bug
title: ''
labels: bug
assignees: ''
---

## Description

A clear description of the bug.

## Steps to Reproduce

1. ...
2. ...
3. ...

## Expected Behavior

What you expected to happen.

## Actual Behavior

What actually happened.

## Environment

- OS: 
- JDK version: 
- Gradle version: 
- Plugin/library version: 
```

- [ ] **Step 3: Create default PR template**

Create `.github/PULL_REQUEST_TEMPLATE.md`:

```markdown
## Summary

Brief description of the changes.

## Changes

- ...

## Checklist

- [ ] Tests pass locally
- [ ] Code follows project conventions
- [ ] Documentation updated (if applicable)
```

- [ ] **Step 4: Create CONTRIBUTING.md**

Create `CONTRIBUTING.md`:

```markdown
# Contributing to ZenHelix

Thank you for your interest in contributing!

## Getting Started

1. Fork the repository
2. Create a feature branch from `main`
3. Make your changes
4. Run tests: `./gradlew check`
5. Submit a pull request

## Conventions

- Follow [Conventional Commits](https://www.conventionalcommits.org/) for commit messages
- Format: `<type>: <description>` (types: feat, fix, refactor, docs, test, chore, perf, ci)
- Write tests for new features and bug fixes
- Keep pull requests focused — one feature or fix per PR

## Reporting Issues

Use the issue templates provided. Include reproduction steps and environment details.
```

- [ ] **Step 5: Create CODE_OF_CONDUCT.md**

Create `CODE_OF_CONDUCT.md`:

```markdown
# Code of Conduct

## Our Pledge

We pledge to make participation in our projects a harassment-free experience for everyone.

## Our Standards

Examples of behavior that contributes to a positive environment:

- Being respectful of differing viewpoints
- Giving and gracefully accepting constructive feedback
- Focusing on what is best for the community

Examples of unacceptable behavior:

- Trolling, insulting/derogatory comments, personal or political attacks
- Harassment in any form
- Publishing others' private information without permission

## Enforcement

Instances of unacceptable behavior may be reported to the project maintainers. All complaints will be reviewed and investigated.

## Attribution

This Code of Conduct is adapted from the [Contributor Covenant](https://www.contributor-covenant.org/), version 2.1.
```

- [ ] **Step 6: Commit**

```bash
cd /Users/dmitriimedakin/IdeaProjects/zenhelix/.github
git add profile/README.md .github/ISSUE_TEMPLATE/bug_report.md .github/PULL_REQUEST_TEMPLATE.md CONTRIBUTING.md CODE_OF_CONDUCT.md
git commit -m "feat: add org-wide community health files and profile"
```

---

## Task 7: Verify and tag ci-workflows

**Repo:** ci-workflows

- [ ] **Step 1: Verify all files are present**

```bash
cd /Users/dmitriimedakin/IdeaProjects/zenhelix/ci-workflows
ls -la .github/workflows/*.yml
ls -la .github/workflows/*.main.kts
ls -la .github/changelog-config.json
ls -la .github/labeler.yml
```

Expected files:
- 5 `.yml` files: gradle-check, gradle-create-tag, release-github, release-publish-gradle, labeler
- 5 `.main.kts` files: one per workflow
- `changelog-config.json` and `labeler.yml` configs

- [ ] **Step 2: Verify each YAML has `workflow_call` trigger**

```bash
cd /Users/dmitriimedakin/IdeaProjects/zenhelix/ci-workflows
grep -l "workflow_call" .github/workflows/*.yml
```

Expected: all 5 YAML files listed

- [ ] **Step 3: Push and tag**

```bash
cd /Users/dmitriimedakin/IdeaProjects/zenhelix/ci-workflows
git push origin feature/init
```

After merging to main:

```bash
git tag v1.0.0
git tag v1
git push origin v1.0.0 v1
```

Note: tagging happens after merge to main. During development on `feature/init`, callers can reference `@feature/init` for testing.

- [ ] **Step 4: Push .github repo**

```bash
cd /Users/dmitriimedakin/IdeaProjects/zenhelix/.github
git push origin main
```
