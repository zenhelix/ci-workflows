# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Reusable GitHub Actions workflow generator for the Zenhelix ecosystem. Workflows are defined in Kotlin using the [github-workflows-kt](https://github.com/typesafegithub/github-workflows-kt) type-safe DSL and compiled to YAML.

## Build & Run Commands

```bash
./gradlew build          # Compile both modules
./gradlew run            # Generate all YAML workflows into .github/workflows/
./gradlew clean          # Clean build artifacts
```

There is no dedicated test suite. Correctness is validated through compilation and YAML generation.

## Architecture

### Three-Level Workflow Hierarchy

```
Level 3: Adapter workflows   — language-specific presets (gradle-check, kotlin-library-release, go-create-tag, ...)
Level 2: Base workflows       — generic, tool-agnostic (check, create-tag, publish, release, ...)
Level 1: Composite actions    — reusable setup steps (setup-gradle, setup-go, setup-python, create-app-token)
```

### Module Structure

- **`workflow-dsl/`** — DSL library for building reusable workflows
  - `core/` — `ReusableWorkflow`, `WorkflowInput`, `InputRegistry`, `MatrixDef`
  - `builder/` — `AdapterWorkflow`, `AdapterWorkflowBuilder`, `GeneratableWorkflow` interface
  - `yaml/` — YAML serialization via kaml (`AdapterWorkflowYaml`, `InputsYamlMapper`)
  - `capability/` — `SetupCapability` interface for setup-aware workflows

- **`src/main/kotlin/`** — Workflow definitions and generator
  - `generate/Generate.kt` — Entry point. Instantiates all workflows and writes YAML.
  - `config/` — Constants: `SetupTool` enum (Gradle/Go/Python), `Defaults` (Java 17, Go 1.22, Python 3.12), `Refs`, `ToolTagConfig`
  - `workflows/base/` — Base workflow objects (CheckWorkflow, CreateTagWorkflow, ReleaseWorkflow, etc.)
  - `workflows/adapters/` — Adapter objects with named properties (e.g., `GradleCheck.appCheck`, `CreateTagAdapters.gradle`)
  - `workflows/support/SetupSteps.kt` — Conditional setup step generation per tool
  - `actions/` — Action definitions (`SetupAction`, `GithubTagAction`, `CreateAppTokenAction`)

### Key Patterns

- **Base workflows** are Kotlin `object`s implementing `GeneratableWorkflow`. They define reusable workflow structure with typed inputs/secrets.
- **Adapter workflows** wrap base workflows with preset defaults for specific ecosystems (e.g., Gradle adapter pre-fills `setup-action: gradle` and exposes `java-version` instead of raw JSON).
- **`GeneratableWorkflow.generate(outputDir)`** writes the final YAML file.
- All workflows are registered in `Generate.kt`'s main list — add new workflows there.
- Kotlin context parameters (`-Xcontext-parameters`) are enabled in both modules.

### Generated Output

Running `./gradlew run` writes YAML files to `.github/workflows/`. These generated files are committed to the repo and consumed by other Zenhelix repositories via `uses: zenhelix/ci-workflows/.github/workflows/<name>@v1`.

## Why kaml Is Used Alongside github-workflows-kt

github-workflows-kt has two limitations that forced a custom YAML serialization layer:

1. **No job-level `uses` support.** GitHub Actions has two kinds of jobs: regular (`runs-on` + `steps`) and caller (`uses` + `with` + `secrets`). The library's `Job` class only models regular jobs — it has no `uses` property and always requires `runsOn`. Adapter workflows are entirely "caller" jobs, so they are serialized through kaml via `AdapterWorkflowYaml`, bypassing github-workflows-kt completely.

2. **Boolean defaults in `workflow_call` inputs are `String?`.** The `WorkflowCall.Input.default` field is typed as `String?`, so `default: true` cannot be expressed — it would serialize as `default: "true"`. For base workflows this is worked around with `_customArguments` (see `ReusableWorkflow.toWorkflowCallTrigger()`). For adapter workflows, kaml handles it natively via `YamlDefaultSerializer` which has `StringValue` and `BooleanValue` subtypes.

As a result, the project has a dual serialization path:
- **Base workflows** → github-workflows-kt's `workflow()` DSL (with `_customArguments` hacks for booleans)
- **Adapter workflows** → kaml-based custom serialization (`AdapterWorkflowYaml` + `InputsYamlMapper`)

## Key Dependencies

| Library | Purpose |
|---|---|
| `github-workflows-kt` 3.7.0 | Type-safe GitHub Actions DSL (base workflows) |
| `kaml` 0.104.0 | YAML serialization for adapter workflows (workaround for github-workflows-kt gaps) |
| `kotlinx-serialization-core` 1.11.0 | Kotlin serialization framework |
| JIT action bindings from `bindings.krzeminski.it` | Type-safe wrappers for GitHub Actions (checkout, labeler, etc.) |
