# SHA Pinning Policy

This document is the single source of truth for how the zenhelix organization enforces SHA pinning of GitHub Actions references. For history and rationale of individual decisions, see the linked specs at the end.

## Three enforcement layers

### Layer 1 — native (GitHub control-plane)

- `sha_pinning_required = true` is set on the organization-level `github_actions_organization_permissions.zenhelix` resource and on every non-template repository's `github_actions_repository_permissions.this` resource (14 repos total).
- Managed in `infra/organization.tf` and `infra/modules/repository/main.tf`.
- GitHub enforces the setting at workflow-run validation: any step-level `uses:` referencing a non-SHA ref causes the run to abort with `startup_failure` before any runner is allocated.
- Carve-outs (confirmed against `docs.github.com/en/actions/reference/security/secure-use` and the Aug 15 2025 GitHub Changelog):
  - Reusable-workflow refs `owner/repo/.github/workflows/<name>.yml@<ref>` — `<ref>` may be a tag.
  - Local refs `uses: ./.github/actions/<name>` — resolved from the current commit filesystem, never restricted.
- Verified live on GitHub Free plan on 2026-04-20 via Spec 7 smoke test (workflow run concluded `startup_failure`).

### Layer 2 — adapter (ci-workflows)

- Every `*-check` adapter contains a job named `sha-pin-check` that calls `sha-pinning-guard.yml@v4`. Current `*-check` adapters (all generated from `src/main/kotlin/workflows/adapters/check/GradleCheck.kt`): `app-check.yml`, `gradle-check.yml`, `gradle-plugin-check.yml`, `kotlin-library-check.yml`. When adding a new `*-check` adapter (e.g., `GoCheck.kt`), include the guard per "How to add a new adapter" below.
- The guard script (`.github/scripts/check-sha-pinning.py`) parses the caller repo's `.github/workflows/` and `.github/actions/` trees; fails the job on any `uses:` ref that is neither a 40-char SHA, a local `./` ref, nor a `zenhelix/ci-workflows/.github/workflows/*@<ref>` reusable-workflow ref.
- **Contract:** the adapter-level guard job MUST be named `sha-pin-check`. Renaming breaks the Layer 3 status-check string declared in `infra/locals.tf`. Verified by `verify-workflows.yml` (DSL-to-YAML diff check).

### Layer 3 — ruleset (infra)

- Per-repo branch rulesets include the guard's full status-check string in `required_checks`.
- The string is reported by GitHub as `<parent-job> / <called-reusable-workflow-job>`. Confirmed empirically via Spec 10 Phase B pilot run (zenhelix-ktlint-rules#11, 2026-04-21): **`check / sha-pin-check / SHA Pinning Guard`**.
- Declared in `infra/locals.tf` per repo. Applies to 9 repos whose `required_checks` list includes `"check"` (i.e., repos that use a `*-check` adapter).
- Effect: PR merges are blocked when the guard fails — independent of whether the workflow itself ran (e.g., if Layer 1 rejected the run, the status check never reports, and the ruleset treats the absence as a failure per the `required_status_checks` contract).

## Ref shapes

| Ref kind | Shape | SHA required? | Why |
|---|---|---|---|
| Step-level third-party action | `owner/repo@<40-char-SHA>  # <version>` | yes (Layer 1) | Native policy. The `# <version>` comment is metadata for humans + Renovate. |
| Internal composite action (`zenhelix/ci-workflows/.github/actions/<name>@...`) | `zenhelix/ci-workflows/.github/actions/<name>@<ACTIONS_SHA>` | yes (Layer 1) | `./` local refs don't work when a reusable workflow is called from another repo — `./` resolves against the caller's `$GITHUB_WORKSPACE`, not the ci-workflows tree. See `src/main/kotlin/config/Refs.kt:16-19` for the rationale. |
| Internal reusable workflow | `zenhelix/ci-workflows/.github/workflows/<name>.yml@v4` | no (exempt) | GitHub exempts reusable-workflow refs. The `v4` tag is protected by a tag ruleset in `infra` (non-fast-forward + delete blocked) to mitigate force-push attacks. |

## How to add a new adapter

1. Add the adapter to `src/main/kotlin/workflows/adapters/<kind>/<Name>.kt`.
2. If it is a `*-check` adapter, include the guard: add `ShaPinningGuardWorkflow.simpleJob("sha-pin-check")` inside the factory body before the main `CheckWorkflow.setupJob(...)` call. See `GradleCheck.kt` for the pattern.
3. Regenerate YAML via `./gradlew run`.
4. Release: bump `src/main/kotlin/config/Refs.kt::ACTIONS_SHA` (via Renovate PR) to the merge commit of this release, then tag `v4.N+1` and move the `v4` mutable tag.

## How to onboard a new consumer repo

1. Add the repo to `infra/locals.tf` under `locals.repositories`.
2. Set `required_checks = ["check", "<Layer-3-status-check-string>"]` if you want Layer 3 enforcement. The status-check string contains slashes and spaces; in HCL it is a plain string in double quotes — no escaping needed.
3. In the consumer repo's `.github/workflows/<name>.yml`, call the relevant adapter:
   ```yaml
   jobs:
     check:
       uses: zenhelix/ci-workflows/.github/workflows/gradle-check.yml@v4
   ```
4. Layer 2 (guard job) activates automatically — the adapter ships with `sha-pin-check`.
5. Apply `infra` — Layer 1 (`sha_pinning_required = true`) and Layer 3 (ruleset required check) are wired by the module.
6. Standalone consumer `sha-pinning-guard.yml@v4` jobs from Spec 6 §6.3.2.4 can optionally be kept as redundant belt-and-suspenders; pruning them is planned as a follow-up to Spec 10.

## Automation

- `src/main/kotlin/config/Refs.kt::ACTIONS_SHA` — bumped by Renovate customManager on v4 tag moves (`renovate.json`, Spec 10).
- JIT binding SHAs in `build.gradle.kts` — bumped by Renovate customManager (Spec 9).
- Composite-action internal `uses:` SHAs (`.github/actions/<name>/action.yml`) — bumped by Dependabot `package-ecosystem: github-actions` with per-action `directories:` list (`.github/dependabot.yml`, Spec 6 §6.2.3).

## Rollback

Every layer is independently revertable:

- **Layer 1:** revert the `sha_pinning_required = true` attribute in Terraform; `make apply`. 15 resources (1 org + 14 repos) flip back to `false` in one operation.
- **Layer 2:** revert the `ci-workflows` commit that added the adapter job; cut a new `v4.N+2` tag without the change; move `v4`.
- **Layer 3:** revert the `locals.tf` change in `infra`; `make apply`. Status-check string removed from `required_checks`.
- **Automation:** reject any bad Renovate PR; rule self-corrects next cycle.

No destructive operations; no state surgery; all rollbacks are single-commit reverts with a normal `make apply` or tag move.

## History

- Spec 6 (2026-04-20) — SHA-pinned all externals in `ci-workflows`; shipped `sha-pinning-guard.yml` as a reusable workflow; configured Dependabot for `.github/workflows` and `.github/actions/*`.
- Spec 7 (2026-04-20) — flipped `sha_pinning_required = true` on org + 14 repos; smoke test confirmed Free-plan enforcement via `startup_failure`.
- Spec 9 (2026-04-21) — replaced Dependabot with Renovate customManager for JIT bindings in `build.gradle.kts`.
- Spec 10 (2026-04-21) — this document; armed guard in adapters, ruleset required check, Renovate for `ACTIONS_SHA`.
- Spec 11 (2026-04-22) — brought `zenhelix/infra` to Layer 1 compliance (SHA-pinned workflow refs), armed Renovate for infra workflows, added standalone `sha-pin-check.yml`, closed the `gradle-bindings` label drift loop; archived the Spec 6 §6.3.2 step 4 audit finding (consumer-repo standalone guard was never implemented and is rendered unnecessary by Spec 10 adapter-level guard).

## v5 — Spec 13 Phase 1 (2026-04-26)

- **Fix `check.yml`:** added missing `actions/checkout` step at the top of the `build` job. Prior to this, every consumer of `check.yml@v4` (and adapter wrappers like `app-check.yml@v4`, `gradle-check.yml@v4`, etc.) failed `Run check` with exit 127 because gradlew was not present in the runner working directory.
- **Fix `conventional-commit-check.yml`:** gated the `check-title` job with `if: github.event_name == 'pull_request'`. On push events the job is now skipped instead of failing 0s due to null `pull_request.title`.
- **Fix `codeql-analysis.yml`:** removed explicit `./gradlew compileKotlin compileTestKotlin --continue` build step and removed the `build-command` input. Set `build-mode: 'autobuild'` on the `Initialize CodeQL` step instead — the action now drives Gradle's `assemble` under tracing, producing a populated database for both single-module and multi-module Kotlin/Java projects. Setup Gradle step retained so gradle is on PATH for autobuild.
- **Consumer impact:** all 9 Kotlin/Java consumer repos (dependanger, dependency-hub, gradle-extensions, gradle-magic-wands, kt-utils, maven-central-publish, spring-kt, zenhelix-app, zenhelix-ktlint-rules) must bump `@v4` → `@v5` to receive the fix. Bump is tracked separately as Tasks 1.10/1.11.

## v5 hotfix — Spec 13 Phase 1.5 concurrency cascade (2026-04-26)

Concurrency-group collision fix for three reusable workflows:
- `check.yml` group changed: `${{ github.workflow }}-${{ github.ref }}` → `check-yml-${{ github.ref }}`
- `conventional-commit-check.yml` group changed: `${{ github.workflow }}-${{ github.ref }}` → `conventional-commit-check-yml-${{ github.ref }}`
- `labeler.yml` group changed: `${{ github.workflow }}-${{ github.ref }}` → `labeler-yml-${{ github.ref }}`

**Why:** in workflow_call context, `${{ github.workflow }}` resolves to the *caller* workflow's name (not the called reusable's name). When a parent workflow like `Build & Tag` calls multiple reusables that all use `${{ github.workflow }}-${{ github.ref }}` as their concurrency group, all three reusables register the same group string (`Build & Tag-refs/heads/main`) and cancel each other under `cancel-in-progress: true`. Prior to this fix, `conventional-commit / Check PR Title` was chronically cancelled on every push to main, cascading via `needs:` to skip `create-tag` jobs in consumer repos.

**Verified on `maven-central-publish` PR #37:** `check / check (17) / Build` passed cleanly; conventional-commit cancellation no longer cascades.

**Tag move:** `v5` is force-moved to point at the hotfix commit. Consumers using `@v5` (floating ref) automatically pick up the fix on next CI run.

## v5 hotfix #2 — bump WORKFLOW_REF (2026-04-26)

`Refs.kt::WORKFLOW_REF` was still `"v4"`, so adapter workflows generated `uses: ...check.yml@v4` etc. internally even when consumers called the adapter at `@v5`. The earlier concurrency fix in v5 wasn't actually being executed — adapters at `@v5` were transitively running the OLD `@v4` reusables (which still had the colliding `${{ github.workflow }}-${{ github.ref }}` group).

**Fix:** `Refs.kt::WORKFLOW_REF` "v4" → "v5". Regenerated 11 adapter YAMLs (`gradle-plugin-check`, `gradle-plugin-release`, `kotlin-library-check`, `kotlin-library-release`, `app-check`, `app-release`, `app-deploy`, `gradle-create-tag`, `gradle-publish`, `go-create-tag`, `go-release`).

Tag move: `v5` force-moved again to point at this commit. Consumers using `@v5` (floating ref) automatically get adapters that internally reference `@v5` reusables (previously they were stuck on `@v4` reusables transitively).
