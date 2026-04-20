package actions

import io.github.typesafegithub.workflows.domain.actions.Action

class GithubTagAction(
    private val githubToken: String,
    private val defaultBump: String,
    private val tagPrefix: String,
    private val releaseBranches: String,
) : Action<Action.Outputs>() {
    // mathieudutour/github-tag-action — not available in bindings.krzeminski.it registry,
    // so this action is hand-rolled and the SHA pin lives inline. Bump via Dependabot
    // (ci-worflows/.github/dependabot.yml watches .github/workflows/*.yml, which contains
    // the regenerated SHA ref — Dependabot can propose bumps from the YAML comment below).
    override val usesString = "mathieudutour/github-tag-action@${GITHUB_TAG_ACTION_SHA}"

    companion object {
        private const val GITHUB_TAG_ACTION_SHA = "a22cf08638b34d5badda920f9daf6e72c477b07b" // v6.2
    }
    override fun toYamlArguments() = linkedMapOf(
        "github_token" to githubToken,
        "default_bump" to defaultBump,
        "tag_prefix" to tagPrefix,
        "release_branches" to releaseBranches,
    )

    override fun buildOutputObject(stepId: String) = Outputs(stepId)
}
