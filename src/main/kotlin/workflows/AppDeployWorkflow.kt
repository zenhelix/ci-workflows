package workflows

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

object AppDeployWorkflow : ProjectWorkflow("app-deploy.yml") {

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
    val deployCommand = input(
        "deploy-command",
        description = "Command to run for deployment",
        required = true
    )
    val tag = input(
        "tag",
        description = "Tag/version to deploy (checked out at this ref)",
        required = true
    )

    class JobBuilder : ReusableWorkflowJobBuilder(AppDeployWorkflow), SetupConfigurable {
        var setupAction by stringInput(AppDeployWorkflow.setupAction)
        var setupParams by stringInput(AppDeployWorkflow.setupParams)
        var deployCommand by refInput(AppDeployWorkflow.deployCommand)
        var tag by refInput(AppDeployWorkflow.tag)

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
            name = "Application Deploy",
            on = listOf(
                WorkflowCall(inputs = inputs),
            ),
            sourceFile = File("src/main/kotlin/workflows/AppDeployWorkflow.kt"),
            targetFileName = "app-deploy.yml",
            consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
            permissions = mapOf(Permission.Contents to Mode.Read),
        ) {
            job(
                id = "deploy",
                name = "Deploy",
                runsOn = UbuntuLatest,
            ) {
                conditionalSetupSteps(fetchDepth = "0")
                run(
                    name = "Checkout tag",
                    command = "git checkout \"${tag.ref.expression}\"",
                )
                run(
                    name = "Deploy",
                    command = deployCommand.ref.expression,
                )
            }
        }
    }
}
