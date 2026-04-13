package shared

import io.github.typesafegithub.workflows.domain.actions.Action

class SetupGradleAction(
    private val javaVersion: String,
    private val fetchDepth: String? = null,
) : Action<Action.Outputs>() {
    override val usesString = localAction("setup-gradle")
    override fun toYamlArguments() = linkedMapOf(
        "java-version" to javaVersion,
    ).apply {
        if (fetchDepth != null) put("fetch-depth", fetchDepth)
    }
    override fun buildOutputObject(stepId: String) = Outputs(stepId)
}

class SetupGoAction(
    private val goVersion: String,
    private val fetchDepth: String? = null,
) : Action<Action.Outputs>() {
    override val usesString = localAction("setup-go")
    override fun toYamlArguments() = linkedMapOf(
        "go-version" to goVersion,
    ).apply {
        if (fetchDepth != null) put("fetch-depth", fetchDepth)
    }
    override fun buildOutputObject(stepId: String) = Outputs(stepId)
}

class SetupPythonAction(
    private val pythonVersion: String,
    private val fetchDepth: String? = null,
) : Action<Action.Outputs>() {
    override val usesString = localAction("setup-python")
    override fun toYamlArguments() = linkedMapOf(
        "python-version" to pythonVersion,
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

    class CreateAppTokenOutputs(stepId: String) : Action.Outputs(stepId) {
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

class CheckoutAction(
    private val fetchDepth: String? = null,
) : Action<Action.Outputs>() {
    override val usesString = "actions/checkout@v6"
    override fun toYamlArguments() = linkedMapOf<String, String>().apply {
        if (fetchDepth != null) put("fetch-depth", fetchDepth)
    }
    override fun buildOutputObject(stepId: String) = Outputs(stepId)
}

class ReleaseChangelogBuilderAction(
    private val configuration: String,
    private val toTag: String,
) : Action<ReleaseChangelogBuilderAction.ChangelogOutputs>() {
    override val usesString = "mikepenz/release-changelog-builder-action@v6"
    override fun toYamlArguments() = linkedMapOf(
        "configuration" to configuration,
        "toTag" to toTag,
    )
    override fun buildOutputObject(stepId: String) = ChangelogOutputs(stepId)

    class ChangelogOutputs(stepId: String) : Action.Outputs(stepId) {
        val changelog: String get() = get("changelog")
    }
}

class GhReleaseAction(
    private val body: String,
    private val name: String,
    private val tagName: String,
    private val draft: String,
) : Action<Action.Outputs>() {
    override val usesString = "softprops/action-gh-release@v2"
    override fun toYamlArguments() = linkedMapOf(
        "body" to body,
        "name" to name,
        "tag_name" to tagName,
        "draft" to draft,
    )
    override fun buildOutputObject(stepId: String) = Outputs(stepId)
}

class LabelerAction(
    private val repoToken: String,
    private val configurationPath: String,
    private val syncLabels: String,
) : Action<Action.Outputs>() {
    override val usesString = "actions/labeler@v6"
    override fun toYamlArguments() = linkedMapOf(
        "repo-token" to repoToken,
        "configuration-path" to configurationPath,
        "sync-labels" to syncLabels,
    )
    override fun buildOutputObject(stepId: String) = Outputs(stepId)
}
