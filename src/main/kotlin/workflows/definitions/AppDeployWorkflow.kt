package workflows.definitions

import dsl.AdapterWorkflowBuilder
import dsl.ReusableWorkflowJobBuilder
import dsl.SetupConfigurable
import dsl.stringInput
import dsl.refInput
import workflows.core.ProjectWorkflow

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
}
