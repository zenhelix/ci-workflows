package workflows

import actions.CreateAppTokenAction
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

object ManualCreateTagWorkflow : ProjectWorkflow("manual-create-tag.yml") {

    val tagVersion = input(
        "tag-version",
        description = "Version to tag (e.g. 1.2.3)",
        required = true
    )
    val tagPrefix = input(
        "tag-prefix",
        description = "Prefix for the tag (e.g. v)",
        default = ""
    )
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
    val appId = secret(
        "app-id",
        description = "GitHub App ID for generating commit token"
    )
    val appPrivateKey = secret(
        "app-private-key",
        description = "GitHub App private key for generating commit token"
    )

    class JobBuilder : ReusableWorkflowJobBuilder(ManualCreateTagWorkflow), SetupConfigurable {
        var tagVersion by refInput(ManualCreateTagWorkflow.tagVersion)
        var tagPrefix by refInput(ManualCreateTagWorkflow.tagPrefix)
        var setupAction by stringInput(ManualCreateTagWorkflow.setupAction)
        var setupParams by stringInput(ManualCreateTagWorkflow.setupParams)
        var checkCommand by refInput(ManualCreateTagWorkflow.checkCommand)

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
            name = "Manual Create Tag",
            on = listOf(
                WorkflowCall(
                    inputs = inputs,
                    secrets = secrets,
                ),
            ),
            sourceFile = File("src/main/kotlin/workflows/ManualCreateTagWorkflow.kt"),
            targetFileName = "manual-create-tag.yml",
            consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
            permissions = mapOf(Permission.Contents to Mode.Write),
        ) {
            job(
                id = "manual_tag",
                name = "Manual Tag",
                runsOn = UbuntuLatest,
            ) {
                run(
                    name = "Validate version format",
                    command = """
                        VERSION="${'$'}{{ inputs.tag-version }}"
                        if [[ ! "${'$'}VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9.]+)?${'$'} ]]; then
                          echo "::error::Version must be in semver format (e.g. 1.2.3 or 1.2.3-rc.1)"
                          exit 1
                        fi
                    """.trimIndent(),
                )
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
                run(
                    name = "Create and push tag",
                    command = """
                        TAG="${'$'}{{ inputs.tag-prefix }}${'$'}{{ inputs.tag-version }}"
                        git config user.name "github-actions[bot]"
                        git config user.email "github-actions[bot]@users.noreply.github.com"
                        git tag -a "${'$'}TAG" -m "Release ${'$'}TAG"
                        git push origin "${'$'}TAG"
                        echo "::notice::Created tag ${'$'}TAG"
                    """.trimIndent(),
                    env = linkedMapOf(
                        "GITHUB_TOKEN" to "\${{ steps.app-token.outputs.token }}",
                    ),
                )
            }
        }
    }
}
