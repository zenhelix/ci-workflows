# Kotlin Idioms Refactoring — Design Spec

## Goal

Make the Kotlin DSL code more concise and idiomatic without changing generated YAML output.

## Scope

Focus: Kotlin idioms and conciseness. No structural/architectural changes.

## Changes

### 1. Unify `input()` overloads (9 -> 5 methods)

**Problem:** `InputRegistry`, `ReusableWorkflow`, and `AdapterWorkflowBuilder` each have 3 identical `input()` overloads differing only by default type (none, String, Boolean).

**Solution:**
- `InputRegistry` gets a single core method with `default: InputDefault? = null`
- `InputDefault` companion gets `operator fun invoke(String)` and `operator fun invoke(Boolean)` factories
- `ReusableWorkflow` and `AdapterWorkflowBuilder` keep thin wrapper overloads (`default: String` and `default: Boolean`) that wrap into `InputDefault` — call-sites unchanged
- Type inference (`InputType.Boolean` vs `InputType.Text`) derived from `default` type inside `InputRegistry`

**Files:** `InputRegistry.kt`, `WorkflowInput.kt`, `ReusableWorkflow.kt`, `AdapterWorkflowBuilder.kt`

### 2. Extension property `expr` for WorkflowInput / WorkflowSecret

**Problem:** `.ref.expression` chain repeated ~25 times across codebase.

**Solution:** Add extension properties in `WorkflowInput.kt`:
```kotlin
val WorkflowInput.expr: String get() = ref.expression
val WorkflowSecret.expr: String get() = ref.expression
```

Replace all `.ref.expression` usages with `.expr`.

**Files:** `WorkflowInput.kt` + all workflow/adapter/action files (~25 call-sites)

### 3. Extract `setupJob()` / `simpleJob()` extensions (remove 8 boilerplate functions)

**Problem:** Every workflow object copy-pastes a `job()` function that just delegates to the parent with a specific builder factory.

**Solution:**
- `SetupCapability.kt` gets an inline extension function `setupJob()` for workflows implementing `SetupCapability`
- `ReusableWorkflow.kt` gets `simpleJob()` for plain workflows
- Remove all 8 per-object `job()` functions
- Adapter call-sites change from `Workflow.job(...)` to `Workflow.setupJob(...)` / `Workflow.simpleJob(...)`

**Files:** `SetupCapability.kt`, `ReusableWorkflow.kt`, all 8 base workflow files, all 6 adapter files

### 4a. Remove `setInput(InputRef)` overload

**Problem:** Two `setInput` methods do the same thing — one takes `String`, other takes `InputRef` (value class wrapping String).

**Solution:** Keep only `setInput(WorkflowInput, String)`. The single call-site (infix `from`) changes to use `.expr`.

**Files:** `ReusableWorkflowJobBuilder.kt`

### 4b. Remove List overload of `passthroughSecrets`

**Problem:** `passthroughSecrets(List)` and `passthroughSecrets(vararg)` — List version unnecessary.

**Solution:** Keep only vararg version. Single List call-site (`PublishWorkflow.mavenSecrets`) uses spread: `*mavenSecrets.toTypedArray()`.

**Files:** `ReusableWorkflowJobBuilder.kt`, `ReleaseAdapters.kt`

### 5. Functional chain in `collectSecretsFromJobs`

**Problem:** Imperative nested for-loops with `putIfAbsent`.

**Solution:** Replace with `flatMap` + `toMap()` + `takeIf`. Semantics preserved (in practice jobs don't have overlapping secrets).

**Files:** `AdapterWorkflow.kt`

### 6. Private scope for ecosystem adapter functions

**Problem:** `ecosystemCreateTag()` and `ecosystemManualCreateTag()` are top-level functions but only used inside their respective objects.

**Solution:** Move them as `private fun` inside the object bodies.

**Files:** `CreateTagAdapters.kt`, `ManualCreateTagAdapters.kt`

## Verification

- `./gradlew clean run` must succeed
- Diff generated YAML files against previous output — must be identical
- `./gradlew build` must compile without errors

## Out of Scope

- Structural changes (no new interfaces, no shared base classes for input registration)
- Test additions (no test suite exists)
- Documentation changes
