package config

/**
 * Tag used in `zenhelix/ci-workflows/.github/workflows/<name>.yml@<WORKFLOW_REF>` refs.
 * Reusable-workflow refs are exempt from GitHub's sha_pinning_required policy, so a
 * mutable major-version tag is fine. Bumped from v2 → v4 for spec 6; breaking changes
 * will bump to v5.
 */
const val WORKFLOW_REF = "v4"
const val WORKFLOW_BASE = "zenhelix/ci-workflows/.github/workflows"

/** External form for reusable-workflow calls (adapter → base, and consumers → ci-workflows). */
fun reusableWorkflow(name: String) = "$WORKFLOW_BASE/$name@$WORKFLOW_REF"

/**
 * Local composite-action ref, resolved from the current repository commit at runtime.
 * GitHub's sha_pinning_required policy exempts `./`-prefixed refs, and using local form
 * avoids the self-ref chicken-and-egg on new releases.
 */
fun localAction(name: String) = "./.github/actions/$name"
