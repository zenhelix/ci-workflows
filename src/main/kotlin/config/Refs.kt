package config

/**
 * Tag used in `zenhelix/ci-workflows/.github/workflows/<name>.yml@<WORKFLOW_REF>` refs.
 * Reusable-workflow refs are exempt from GitHub's sha_pinning_required policy, so a
 * mutable major-version tag is fine. Bumped from v2 → v4 for spec 6; breaking changes
 * will bump to v5.
 */
const val WORKFLOW_REF = "v5"
const val WORKFLOW_BASE = "zenhelix/ci-workflows/.github/workflows"

/**
 * Commit SHA of the current v4 release. Composite-action step refs (which ARE subject
 * to sha_pinning_required) are pinned to this. Bump to the previous release's SHA on
 * each release cycle, or to the current merge commit via a post-merge follow-up.
 *
 * NOTE: Using `./.github/actions/<name>` (local) form does NOT work when this repo's
 * reusable workflows are called by other repos — `./` resolves against the caller's
 * $GITHUB_WORKSPACE, not this repo's tree. Hence the fully-qualified SHA form below.
 */
const val ACTIONS_SHA = "63aedd23d8b568996497cf9210547388db43ab46" // v4
const val ACTIONS_BASE = "zenhelix/ci-workflows/.github/actions"

/** External form for reusable-workflow calls (adapter → base, and consumers → ci-workflows). */
fun reusableWorkflow(name: String) = "$WORKFLOW_BASE/$name@$WORKFLOW_REF"

/** Composite-action step ref — fully-qualified, SHA-pinned (policy-compliant). */
fun localAction(name: String) = "$ACTIONS_BASE/$name@$ACTIONS_SHA"
