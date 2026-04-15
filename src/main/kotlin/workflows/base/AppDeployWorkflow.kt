package workflows.base

import dsl.builder.AdapterWorkflowBuilder
import dsl.builder.SetupAwareJobBuilder
import dsl.capability.SetupCapability
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.dsl.WorkflowBuilder
import workflows.ProjectWorkflow
import workflows.support.conditionalSetupSteps

object AppDeployWorkflow : ProjectWorkflow("app-deploy.yml", "Application Deploy"), SetupCapability {
    override val setupAction = input("setup-action", SetupCapability.SETUP_ACTION_DESCRIPTION, required = true)
    override val setupParams = input("setup-params", SetupCapability.SETUP_PARAMS_DESCRIPTION, default = SetupCapability.SETUP_PARAMS_DEFAULT)
    val deployCommand = input("deploy-command", "Command to run for deployment", required = true)
    val tag = input("tag", "Tag/version to deploy (checked out at this ref)", required = true)

    context(builder: AdapterWorkflowBuilder)
    fun job(id: String, block: SetupAwareJobBuilder<AppDeployWorkflow>.() -> Unit = {}) =
        job(id, { SetupAwareJobBuilder(this@AppDeployWorkflow) }, block)

    override fun WorkflowBuilder.implementation() {
        job(id = "deploy", name = "Deploy", runsOn = UbuntuLatest) {
            conditionalSetupSteps(fetchDepth = "0")
            run(name = "Checkout tag", command = "git checkout \"${tag.ref.expression}\"")
            run(name = "Deploy", command = deployCommand.ref.expression)
        }
    }
}
