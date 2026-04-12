# ci-workflows

Reusable GitHub Actions workflows for zenhelix projects. Each workflow is a composable building block — combine what you need in your project.

Workflows are defined in Kotlin DSL ([github-workflows-kt](https://github.com/typesafegithub/github-workflows-kt)) and generated into YAML.

## Workflows

### gradle-check

Build and test a Gradle project.

| Input | Default | Description |
|---|---|---|
| `java-version` | `17` | JDK version |
| `gradle-command` | `./gradlew check` | Gradle command to run |

**Required caller permissions:** `contents: read`

```yaml
# .github/workflows/build.yml
name: Build
on: [pull_request]
jobs:
  build:
    uses: zenhelix/ci-workflows/.github/workflows/gradle-check.yml@v1
```

### gradle-create-tag

Build, bump semantic version, and push a git tag.

| Input | Default | Description |
|---|---|---|
| `java-version` | `17` | JDK version |
| `gradle-command` | `./gradlew check` | Gradle command to run |
| `default-bump` | `patch` | Version bump type (major/minor/patch) |
| `tag-prefix` | `""` | Tag prefix (e.g. `v`) |
| `release-branches` | `main,[0-9]+\.x` | Branch patterns for releases |

| Secret | Required | Description |
|---|---|---|
| `app-id` | yes | GitHub App ID for commit token |
| `app-private-key` | yes | GitHub App private key |

**Required caller permissions:** `contents: write`

```yaml
# .github/workflows/create-tag.yml
name: Create Tag
on:
  push:
    branches: [main, '[0-9]+.x']
jobs:
  tag:
    uses: zenhelix/ci-workflows/.github/workflows/gradle-create-tag.yml@v1
    secrets:
      app-id: ${{ secrets.ZENHELIX_COMMITER_APP_ID }}
      app-private-key: ${{ secrets.ZENHELIX_COMMITER_APP_PRIVATE_KEY }}
```

### release-github

Generate changelog and create a GitHub Release.

| Input | Default | Description |
|---|---|---|
| `changelog-config` | `.github/changelog-config.json` | Path to changelog config |

**Required caller permissions:** `contents: write`

**Note:** The changelog config is read from the **calling repository**, not from ci-workflows. Copy `.github/changelog-config.json` from this repo into your project, or provide your own.

```yaml
# .github/workflows/release.yml (part 1)
name: Release
on:
  push:
    tags: ['*']
jobs:
  github-release:
    uses: zenhelix/ci-workflows/.github/workflows/release-github.yml@v1
```

### release-publish-gradle

Publish Gradle artifacts. Validates that at least one set of credentials is configured before publishing.

| Input | Default | Description |
|---|---|---|
| `java-version` | `17` | JDK version |
| `publish-command` | *required* | Gradle publish command |

| Secret | Required | Description |
|---|---|---|
| `GRADLE_PUBLISH_KEY` | no | Gradle Plugin Portal key |
| `GRADLE_PUBLISH_SECRET` | no | Gradle Plugin Portal secret |
| `MAVEN_SONATYPE_SIGNING_KEY_ID` | no | GPG signing key ID |
| `MAVEN_SONATYPE_SIGNING_PUB_KEY_ASCII_ARMORED` | no | GPG public key |
| `MAVEN_SONATYPE_SIGNING_KEY_ASCII_ARMORED` | no | GPG private key |
| `MAVEN_SONATYPE_SIGNING_PASSWORD` | no | GPG passphrase |

At least one credential set must be provided (Gradle Portal or Maven/Sonatype signing).

**Required caller permissions:** `contents: read`

```yaml
# .github/workflows/release.yml (part 2, combined with release-github)
  publish:
    needs: github-release
    uses: zenhelix/ci-workflows/.github/workflows/release-publish-gradle.yml@v1
    with:
      publish-command: "./gradlew publishPlugins -Pversion=${{ github.ref_name }}"
    secrets: inherit
```

### labeler

Auto-label PRs based on changed file paths.

| Input | Default | Description |
|---|---|---|
| `config-path` | `.github/labeler.yml` | Path to labeler config |

**Required caller permissions:** `contents: write`, `pull-requests: write`

**Note:** The labeler reads `.github/labeler.yml` from the **calling repository**. Copy `.github/labeler.yml` from this repo into your project, or create your own.

```yaml
# .github/workflows/labeler.yml
name: PR Labeler
on: [pull_request]
jobs:
  label:
    uses: zenhelix/ci-workflows/.github/workflows/labeler.yml@v1
```

## Full Example

Complete CI/CD setup for a Gradle plugin project:

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
    secrets:
      app-id: ${{ secrets.ZENHELIX_COMMITER_APP_ID }}
      app-private-key: ${{ secrets.ZENHELIX_COMMITER_APP_PRIVATE_KEY }}

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

## Required Files in Your Project

These files are read from **your repository**, not from ci-workflows:

| File | Used by | Template |
|---|---|---|
| `.github/changelog-config.json` | release-github | [changelog-config.json](.github/changelog-config.json) |
| `.github/labeler.yml` | labeler | [labeler.yml](.github/labeler.yml) |

Copy the templates from this repo and customize for your project.

## Secrets Setup

Configure these secrets at the **GitHub organization level** (Settings > Secrets and variables > Actions):

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

## Versioning

Reference workflows by tag for stability:

- `@v1` — latest compatible version (recommended)
- `@v1.0.0` — exact version
- `@main` — development only

## Extending

The CI/CD system uses a three-level architecture:

```
Level 3: Project workflows      — per-repo .github/workflows that call presets or base directly
Level 2: Preset workflows       — common configurations per project type (gradle-check, kotlin-library-release, …)
Level 1: Base workflows         — generic, tool-agnostic (check, create-tag, publish, release)
         Composite actions      — setup-gradle, setup-go, create-app-token
```

### Adding a new setup action

To support a new language or toolchain (e.g., `setup-rust`):

1. Create `.github/actions/setup-rust/action.yml` as a composite action that installs the toolchain and any caches.
2. In the base workflows (`check.yml`, `create-tag.yml`, `publish.yml`), add a new conditional step:
   ```yaml
   - if: inputs.setup-action == 'rust'
     uses: ./.github/actions/setup-rust
     with:
       setup-params: ${{ inputs.setup-params }}
   ```
3. Existing presets and project workflows remain untouched.

### Adding a new preset workflow

Presets wrap base workflows with sensible defaults for a specific project type:

1. Create `.github/workflows/<type>-check.yml` and/or `<type>-release.yml`.
2. Call the corresponding base workflow with pre-configured `setup-action` and `setup-params`:
   ```yaml
   jobs:
     check:
       uses: ./.github/workflows/check.yml
       with:
         setup-action: 'gradle'
         setup-params: '{"java-version":"21"}'
   ```
3. Expose named inputs (e.g., `java-version`) instead of raw JSON so callers get a clean interface.
4. Follow the naming convention: `<type>-check.yml`, `<type>-create-tag.yml`, `<type>-release.yml`.

### Adding a new project type end-to-end

1. **Infra** — add the value to the `project-type` custom property (in the `infra` repo).
2. **ci-workflows** — create preset workflows for the new type (see above).
3. **Template repo** (optional) — create `zenhelix/template-<type>` with `.github/workflows` that call the new presets.
4. **Rulesets** — update infra rulesets if the new type needs different branch-protection or status-check rules.

## Development

Workflows are defined as Kotlin scripts in `.github/workflows/workflow.*.main.kts`. To regenerate YAML after editing:

```bash
kotlin .github/workflows/workflow.<name>.main.kts
```

Requires Kotlin 2.1.0+.
