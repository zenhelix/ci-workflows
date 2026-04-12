# CI Workflows Design

## Problem

Multiple repositories in the zenhelix GitHub organization (gradle plugins, KMP projects, Maven libraries, monorepos) each require CI/CD pipelines with similar logic. Currently, each repo defines its own workflows independently, leading to duplication and inconsistency.

Additionally, GitHub organization-level settings (issue templates, PR templates, community health files) are configured per-repo instead of centrally.

## Solution

Two complementary components:

1. **ci-workflows** repository — reusable GitHub Actions workflows generated from Kotlin DSL (github-workflows-kt), used as composable building blocks across all repos
2. **zenhelix/.github** repository (public) — organization-wide default settings and community health files

## Architecture

### Approach: Composable Reusable Workflows

Each workflow is a single-purpose "brick." Projects combine the bricks they need via small caller YAML files.

- Reusable workflows are defined in `ci-workflows` using Kotlin DSL (github-workflows-kt) and generated into YAML
- Caller workflows in each repo are plain YAML (github-workflows-kt does not support job-level `uses:` for calling reusable workflows)
- Versioning via git tags (`@v1`, `@v1.2.0`) to avoid breaking all projects on every change

### ci-workflows Repository Structure

```
ci-workflows/
  .github/
    workflows/
      gradle-check.yml
      gradle-create-tag.yml
      release-github.yml
      release-publish-gradle.yml
      labeler.yml
      workflow.gradle-check.main.kts
      workflow.gradle-create-tag.main.kts
      workflow.release-github.main.kts
      workflow.release-publish-gradle.main.kts
      workflow.labeler.main.kts
    labeler.yml
    changelog-config.json
  README.md
  LICENSE
```

### Reusable Workflows (Bricks)

#### 1. gradle-check.yml

Build and test on pull requests.

**Trigger:** `workflow_call`

**Inputs:**
- `java-version` (string, default: `"17"`)
- `gradle-command` (string, default: `"./gradlew check"`)

**Steps:** checkout -> setup-java -> setup-gradle (cache read-only) -> run gradle command

**Actions:**
- `actions/checkout@v6`
- `actions/setup-java@v5`
- `gradle/actions/setup-gradle@v5`

#### 2. gradle-create-tag.yml

Build, bump semantic version, push tag.

**Trigger:** `workflow_call`

**Inputs:**
- `java-version` (string, default: `"17"`)
- `gradle-command` (string, default: `"./gradlew check"`)
- `default-bump` (string, default: `"patch"`) — major/minor/patch
- `tag-prefix` (string, default: `""`)
- `release-branches` (string, default: `"main,[0-9]+\\.x"`)

**Secrets:**
- `app-id` (required) — GitHub App ID for commit token
- `app-private-key` (required) — GitHub App private key

**Steps:** checkout (full history) -> setup-java -> setup-gradle -> run gradle command -> create GitHub App token -> bump version and push tag

**Actions:**
- `actions/checkout@v6`
- `actions/setup-java@v5`
- `gradle/actions/setup-gradle@v5`
- `actions/create-github-app-token@v2` (replaces peter-murray/workflow-application-token-action)
- `mathieudutour/github-tag-action@v6` (replaces anothrNick/github-tag-action)

#### 3. release-github.yml

Generate changelog and create GitHub Release.

**Trigger:** `workflow_call`

**Inputs:**
- `changelog-config` (string, default: `".github/changelog-config.json"`)

**Outputs:**
- `tag` — the release tag name (for downstream jobs)

**Steps:** checkout (full history) -> build changelog -> create GitHub release

**Actions:**
- `actions/checkout@v6`
- `mikepenz/release-changelog-builder-action@v6`
- `softprops/action-gh-release@v2`

#### 4. release-publish-gradle.yml

Universal Gradle publish step, parameterized per project.

**Trigger:** `workflow_call`

**Inputs:**
- `java-version` (string, default: `"17"`)
- `publish-command` (string, required) — e.g., `"./gradlew publishPlugins"`, `"./gradlew publish"`

**Secrets** (declared in `workflow_call`, passed via `secrets: inherit` from caller):
- `GRADLE_PUBLISH_KEY` (optional) -> env `GRADLE_PUBLISH_KEY`
- `GRADLE_PUBLISH_SECRET` (optional) -> env `GRADLE_PUBLISH_SECRET`
- `MAVEN_SONATYPE_SIGNING_KEY_ID` (optional) -> env `ORG_GRADLE_PROJECT_signingKeyId`
- `MAVEN_SONATYPE_SIGNING_PUB_KEY_ASCII_ARMORED` (optional) -> env `ORG_GRADLE_PROJECT_signingPublicKey`
- `MAVEN_SONATYPE_SIGNING_KEY_ASCII_ARMORED` (optional) -> env `ORG_GRADLE_PROJECT_signingKey`
- `MAVEN_SONATYPE_SIGNING_PASSWORD` (optional) -> env `ORG_GRADLE_PROJECT_signingPassword`

Secrets are optional because not all projects need all of them (e.g., a Maven library doesn't need Gradle Portal keys).

**Steps:** checkout -> setup-java -> setup-gradle -> run publish command with secrets as env vars

**Actions:**
- `actions/checkout@v6`
- `actions/setup-java@v5`
- `gradle/actions/setup-gradle@v5`

#### 5. labeler.yml

Auto-label PRs based on changed file paths.

**Trigger:** `workflow_call`

**Inputs:**
- `config-path` (string, default: `".github/labeler.yml"`)

**Steps:** run labeler action

**Actions:**
- `actions/labeler@v6`

### Caller Patterns

Each project has small YAML files calling the reusable workflows.

**Example: Gradle Plugin project (like maven-central-publish)**

```yaml
# .github/workflows/build.yml
name: Build
on: [pull_request]
jobs:
  build:
    uses: zenhelix/ci-workflows/.github/workflows/gradle-check.yml@v1

# .github/workflows/create-tag.yml
name: Create Tag
on:
  push:
    branches: [main, '[0-9]+.x']
jobs:
  tag:
    uses: zenhelix/ci-workflows/.github/workflows/gradle-create-tag.yml@v1
    secrets: inherit

# .github/workflows/release.yml
name: Release
on:
  push:
    tags: ['*']
jobs:
  github-release:
    uses: zenhelix/ci-workflows/.github/workflows/release-github.yml@v1
  publish:
    needs: github-release
    uses: zenhelix/ci-workflows/.github/workflows/release-publish-gradle.yml@v1
    with:
      publish-command: "./gradlew publishPlugins -Pversion=${{ github.ref_name }}"
    secrets: inherit

# .github/workflows/labeler.yml
name: PR Labeler
on: [pull_request]
jobs:
  label:
    uses: zenhelix/ci-workflows/.github/workflows/labeler.yml@v1
```

**Example: Maven library — only release.yml differs:**

```yaml
jobs:
  publish:
    needs: github-release
    uses: zenhelix/ci-workflows/.github/workflows/release-publish-gradle.yml@v1
    with:
      publish-command: "./gradlew publish -Pversion=${{ github.ref_name }}"
    secrets: inherit
```

### zenhelix/.github Repository

Public repository providing organization-wide defaults.

```
.github/
  profile/
    README.md                      # Organization profile page
  .github/
    ISSUE_TEMPLATE/
      bug_report.md                # Default bug report template
    PULL_REQUEST_TEMPLATE.md       # Default PR template
  CONTRIBUTING.md
  CODE_OF_CONDUCT.md
  LICENSE
```

These files act as fallbacks — if a specific repo has its own template, it takes priority.

**Important:** the `.github` repository must be **public** for community health files to work as org-wide defaults (unless on GitHub Enterprise).

### Secrets Strategy

All secrets are configured at the **GitHub organization level** (Settings -> Secrets and variables -> Actions) and inherited via `secrets: inherit`:

| Secret | Used by |
|---|---|
| `ZENHELIX_COMMITER_APP_ID` | gradle-create-tag |
| `ZENHELIX_COMMITER_APP_PRIVATE_KEY` | gradle-create-tag |
| `GRADLE_PUBLISH_KEY` | release-publish-gradle |
| `GRADLE_PUBLISH_SECRET` | release-publish-gradle |
| `MAVEN_SONATYPE_SIGNING_KEY_ID` | release-publish-gradle |
| `MAVEN_SONATYPE_SIGNING_PUB_KEY_ASCII_ARMORED` | release-publish-gradle |
| `MAVEN_SONATYPE_SIGNING_KEY_ASCII_ARMORED` | release-publish-gradle |
| `MAVEN_SONATYPE_SIGNING_PASSWORD` | release-publish-gradle |

Projects that don't need certain secrets simply don't call the corresponding workflow.

### Actions Updates

| Purpose | Old | New |
|---|---|---|
| GitHub App Token | `peter-murray/workflow-application-token-action@v4` | `actions/create-github-app-token@v2` |
| Version Tag | `anothrNick/github-tag-action@v1` | `mathieudutour/github-tag-action@v6` |
| All others | unchanged | unchanged |

### Versioning Strategy

ci-workflows uses git tags for stability:
- `@v1` — major version tag (updated on non-breaking changes)
- `@v1.2.0` — exact version for pinning
- `@main` — development/testing only

### Technology

- **github-workflows-kt** v3.7.0+ — Kotlin DSL for generating reusable workflow YAML
- Kotlin script files (`.main.kts`) as source of truth
- Generated YAML committed to repository
