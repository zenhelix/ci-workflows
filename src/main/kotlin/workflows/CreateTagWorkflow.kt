package workflows

import actions.CreateAppTokenAction
import actions.GithubTagAction
import config.DEFAULT_RELEASE_BRANCHES
import dsl.AdapterWorkflowBuilder
import dsl.ReusableWorkflowJobBuilder
import dsl.SetupConfigurable
import dsl.stringInput
import dsl.refInput
import workflows.core.ProjectWorkflow
import workflows.helpers.conditionalSetupSteps
import io.github.typesafegithub.workflows.domain.Mode
import io.github.typesafegithub.workflows.domain.Permission
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import java.io.File

object CreateTagWorkflow : ProjectWorkflow("create-tag.yml") {

    val setupAction = input(
        "setup-action",
        description = SetupConfigurable.SETUP_ACTION_DESCRIPTION,
        required = true
    )
    val setupParams = input(
        "setup-params",
        description = SetupConfigurable.SETUP_PARAMS_DESCRIPTION,
        default = SetupConfigurable.SETUP_PARAMS_DEFAULT
    )
    val checkCommand = input(
        "check-command",
        description = "Validation command to run before tagging",
        required = true
    )
    val defaultBump = input(
        "default-bump",
        description = "Default version bump type (major, minor, patch)",
        default = "patch"
    )
    val tagPrefix = input(
        "tag-prefix",
        description = "Prefix for the tag (e.g. v)",
        default = ""
    )
    val releaseBranches = input(
        "release-branches",
        description = "Comma-separated branch patterns for releases",
        default = DEFAULT_RELEASE_BRANCHES
    )
    val appId = secret(
        "app-id",
        description = "GitHub App ID for generating commit token"
    )
    val appPrivateKey = secret(
        "app-private-key",
        description = "GitHub App private key for generating commit token"
    )

    class JobBuilder : ReusableWorkflowJobBuilder(CreateTagWorkflow), SetupConfigurable {
        var setupAction by stringInput(CreateTagWorkflow.setupAction)
        var setupParams by stringInput(CreateTagWorkflow.setupParams)
        var checkCommand by refInput(CreateTagWorkflow.checkCommand)
        var defaultBump by refInput(CreateTagWorkflow.defaultBump)
        var tagPrefix by refInput(CreateTagWorkflow.tagPrefix)
        var releaseBranches by refInput(CreateTagWorkflow.releaseBranches)

        override fun applySetup(action: String, params: String) {
            setupAction = action
            setupParams = params
        }
    }

    context(builder: AdapterWorkflowBuilder)
    fun job(id: String, block: JobBuilder.() -> Unit = {}) {
        builder.registerJob(buildJob(id, ::JobBuilder, block))
    }

    fun generate() {
        workflow(
            name = "Create Tag",
            on = listOf(
                WorkflowCall(
                    inputs = inputs,
                    secrets = secrets,
                ),
            ),
            sourceFile = File("src/main/kotlin/workflows/CreateTagWorkflow.kt"),
            targetFileName = "create-tag.yml",
            consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
            permissions = mapOf(Permission.Contents to Mode.Write),
        ) {
            job(
                id = "create_tag",
                name = "Create Tag",
                runsOn = UbuntuLatest,
            ) {
                conditionalSetupSteps(fetchDepth = "0")
                run(
                    name = "Run validation",
                    command = checkCommand.ref.expression,
                )
                uses(
                    name = "Generate App Token",
                    action = CreateAppTokenAction(
                        appId = appId.ref.expression,
                        appPrivateKey = appPrivateKey.ref.expression,
                    ),
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
}
