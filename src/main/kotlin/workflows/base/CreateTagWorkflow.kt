package workflows.base

import actions.CreateAppTokenAction
import actions.GithubTagAction
import config.DEFAULT_RELEASE_BRANCHES
import dsl.builder.AdapterWorkflowBuilder
import dsl.builder.SetupAwareJobBuilder
import dsl.builder.refInput
import dsl.capability.SetupCapability
import io.github.typesafegithub.workflows.domain.Mode
import io.github.typesafegithub.workflows.domain.Permission
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.dsl.WorkflowBuilder
import workflows.ProjectWorkflow
import workflows.support.conditionalSetupSteps

object CreateTagWorkflow : ProjectWorkflow(
    "create-tag.yml", "Create Tag",
    permissions = mapOf(Permission.Contents to Mode.Write),
), SetupCapability {
    override val setupAction = input("setup-action", SetupCapability.SETUP_ACTION_DESCRIPTION, required = true)
    override val setupParams = input("setup-params", SetupCapability.SETUP_PARAMS_DESCRIPTION, default = SetupCapability.SETUP_PARAMS_DEFAULT)
    val checkCommand = input("check-command", "Validation command to run before tagging", required = true)
    val defaultBump = input("default-bump", "Default version bump type (major, minor, patch)", default = "patch")
    val tagPrefix = input("tag-prefix", "Prefix for the tag (e.g. v)", default = "")
    val releaseBranches = input("release-branches", "Comma-separated branch patterns for releases", default = DEFAULT_RELEASE_BRANCHES)
    val appId = secret("app-id", "GitHub App ID for generating commit token")
    val appPrivateKey = secret("app-private-key", "GitHub App private key for generating commit token")

    class JobBuilder : SetupAwareJobBuilder(CreateTagWorkflow) {
        var checkCommand by refInput(CreateTagWorkflow.checkCommand)
        var defaultBump by refInput(CreateTagWorkflow.defaultBump)
        var tagPrefix by refInput(CreateTagWorkflow.tagPrefix)
        var releaseBranches by refInput(CreateTagWorkflow.releaseBranches)
    }

    context(builder: AdapterWorkflowBuilder)
    fun job(id: String, block: JobBuilder.() -> Unit = {}) = job(id, ::JobBuilder, block)

    override fun WorkflowBuilder.implementation() {
        job(id = "create_tag", name = "Create Tag", runsOn = UbuntuLatest) {
            conditionalSetupSteps(fetchDepth = "0")
            run(name = "Run validation", command = checkCommand.ref.expression)
            uses(
                name = "Generate App Token",
                action = CreateAppTokenAction(appId = appId.ref.expression, appPrivateKey = appPrivateKey.ref.expression),
                id = "app-token",
            )
            uses(
                name = "Bump version and push tag",
                action = GithubTagAction(
                    githubToken = "\${{ steps.app-token.outputs.token }}",
                    defaultBump = defaultBump.ref.expression,
                    tagPrefix = tagPrefix.ref.expression,
                    releaseBranches = releaseBranches.ref.expression,
                ),
            )
        }
    }
}
