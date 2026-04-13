# Migration from .main.kts to Gradle Project

## Goal

Replace standalone `.main.kts` scripts with a Gradle project for full IDE support (autocomplete, refactoring, navigation, compile-time errors).

## Decisions

| Decision | Choice |
|----------|--------|
| Project location | Repository root |
| Run command | `./gradlew run` (application plugin) |
| Old workflow-src | Delete entirely |
| Kotlin file structure | By layers: `shared/`, `workflows/base/`, `workflows/adapters/` |
| CI verification | `./gradlew run` + `git diff --exit-code` |
| github-workflows-kt version | 3.7.0 (unchanged) |
| Kotlin version | 2.3.20 |
| Gradle wrapper source | Copy from `/Users/dmitriimedakin/IdeaProjects/orient/backend-api-copy` |

## Project Structure

```
/
  build.gradle.kts
  settings.gradle.kts
  gradle/wrapper/
  gradlew, gradlew.bat
  .gitignore                          # add .gradle/, build/
  src/main/kotlin/
    shared/
      Constants.kt                    # WORKFLOW_REF, DEFAULT_JAVA_VERSION, etc.
      Inputs.kt                       # stringInput(), booleanInput(), secretInput(), common input vals
      Actions.kt                      # SetupGradleAction, CheckoutAction, etc.
      PostProcessing.kt               # cleanReusableWorkflowJobs()
      DslHelpers.kt                   # conditionalSetupSteps(), noop()
    workflows/
      base/
        Check.kt                      # generateCheck()
        ConventionalCommitCheck.kt     # generateConventionalCommitCheck()
        CreateTag.kt                   # generateCreateTag()
        ManualCreateTag.kt             # generateManualCreateTag()
        Release.kt                     # generateRelease()
        Publish.kt                     # generatePublish()
        Labeler.kt                     # generateLabeler()
      adapters/
        AppCheck.kt                    # generateAppCheck()
        AppRelease.kt                  # generateAppRelease()
        AppDeploy.kt                   # generateAppDeploy()
        GradleCreateTag.kt            # generateGradleCreateTag()
        GradleManualCreateTag.kt       # generateGradleManualCreateTag()
        GradlePluginCheck.kt           # generateGradlePluginCheck()
        GradlePluginRelease.kt         # generateGradlePluginRelease()
        KotlinLibraryCheck.kt          # generateKotlinLibraryCheck()
        KotlinLibraryRelease.kt        # generateKotlinLibraryRelease()
        GoCreateTag.kt                # generateGoCreateTag()
        GoManualCreateTag.kt           # generateGoManualCreateTag()
    Generate.kt                        # main() — calls all generate* functions
  .github/
    workflows/                         # generated YAML (unchanged output)
    actions/                           # unchanged
    labeler.yml                        # unchanged
```

## Gradle Configuration

### settings.gradle.kts

```kotlin
rootProject.name = "ci-workflows"
```

### build.gradle.kts

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

## Generation

### Generate.kt

Entry point. Calls all workflow generator functions with output directory:

```kotlin
fun main() {
    val workflowsDir = File(".github/workflows")

    // Base
    generateCheck(workflowsDir)
    generateConventionalCommitCheck(workflowsDir)
    generateCreateTag(workflowsDir)
    generateManualCreateTag(workflowsDir)
    generateRelease(workflowsDir)
    generatePublish(workflowsDir)
    generateLabeler(workflowsDir)

    // Adapters
    generateAppCheck(workflowsDir)
    generateAppRelease(workflowsDir)
    generateAppDeploy(workflowsDir)
    generateGradleCreateTag(workflowsDir)
    generateGradleManualCreateTag(workflowsDir)
    generateGradlePluginCheck(workflowsDir)
    generateGradlePluginRelease(workflowsDir)
    generateKotlinLibraryCheck(workflowsDir)
    generateKotlinLibraryRelease(workflowsDir)
    generateGoCreateTag(workflowsDir)
    generateGoManualCreateTag(workflowsDir)
}
```

### Workflow Function Pattern

Each workflow is a top-level function:

```kotlin
fun generateCheck(outputDir: File) {
    workflow(
        name = "Check",
        on = listOf(WorkflowDispatch()),
        targetFileName = outputDir.resolve("check.yml").toString(),
        consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
        _customArguments = mapOf(/* workflow_call inputs */),
    ) {
        // jobs
    }
}
```

## CI Verification

Updated `verify-workflows.yml` (hand-written):

```yaml
name: 'Verify Workflows'
on:
  pull_request:
    paths: ['src/**', 'build.gradle.kts', '.github/workflows/**']
  push:
    branches: ['main']
    paths: ['src/**', 'build.gradle.kts', '.github/workflows/**']
permissions:
  contents: read
jobs:
  verify:
    name: Verify generated workflows are up-to-date
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v6
      - uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: '17'
      - uses: gradle/actions/setup-gradle@v5
      - run: ./gradlew run
      - run: |
          if ! git diff --exit-code .github/workflows/; then
            echo "::error::Generated workflows are out of date. Run ./gradlew run and commit."
            exit 1
          fi
```

## What Gets Deleted

- `.github/workflow-src/` — entire directory (19 `.main.kts` files + `generate.sh`)

## What Stays Unchanged

- `.github/workflows/*.yml` — generated YAML must be byte-identical after migration
- `.github/actions/` — composite actions
- `.github/labeler.yml` — labeler config

## Success Criteria

Generated YAML after migration must be **byte-identical** to current output. No formatting differences allowed — same Kotlin code, same library version, same output.
