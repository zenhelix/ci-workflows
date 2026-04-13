package actions

import config.localAction
import io.github.typesafegithub.workflows.domain.actions.Action

class SetupAction(
    private val actionName: String,
    private val versionKey: String,
    private val version: String,
    private val fetchDepth: String? = null,
) : Action<Action.Outputs>() {
    override val usesString = localAction(actionName)
    override fun toYamlArguments() = linkedMapOf(
        versionKey to version,
    ).apply {
        if (fetchDepth != null) put("fetch-depth", fetchDepth)
    }

    override fun buildOutputObject(stepId: String) = Outputs(stepId)
}

class CreateAppTokenAction(
    private val appId: String,
    private val appPrivateKey: String,
) : Action<CreateAppTokenAction.CreateAppTokenOutputs>() {
    override val usesString = localAction("create-app-token")
    override fun toYamlArguments() = linkedMapOf(
        "app-id" to appId,
        "app-private-key" to appPrivateKey,
    )

    override fun buildOutputObject(stepId: String) = CreateAppTokenOutputs(stepId)

    class CreateAppTokenOutputs(stepId: String) : Outputs(stepId) {
        val token: String get() = get("token")
    }
}

class GithubTagAction(
    private val githubToken: String,
    private val defaultBump: String,
    private val tagPrefix: String,
    private val releaseBranches: String,
) : Action<Action.Outputs>() {
    override val usesString = "mathieudutour/github-tag-action@v6.2"
    override fun toYamlArguments() = linkedMapOf(
        "github_token" to githubToken,
        "default_bump" to defaultBump,
        "tag_prefix" to tagPrefix,
        "release_branches" to releaseBranches,
    )

    override fun buildOutputObject(stepId: String) = Outputs(stepId)
}

