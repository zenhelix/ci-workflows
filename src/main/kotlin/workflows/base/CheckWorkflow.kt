package workflows.base

import dsl.builder.AdapterWorkflowBuilder
import dsl.builder.SetupAwareJobBuilder
import dsl.builder.refInput
import dsl.capability.SetupCapability
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.dsl.WorkflowBuilder
import workflows.ProjectWorkflow
import workflows.support.conditionalSetupSteps

object CheckWorkflow : ProjectWorkflow("check.yml", "Check"), SetupCapability {
    override val setupAction = input("setup-action", SetupCapability.SETUP_ACTION_DESCRIPTION, required = true)
    override val setupParams = input("setup-params", SetupCapability.SETUP_PARAMS_DESCRIPTION, default = SetupCapability.SETUP_PARAMS_DEFAULT)
    val checkCommand = input("check-command", "Command to run for checking", required = true)

    class JobBuilder : SetupAwareJobBuilder(CheckWorkflow) {
        var checkCommand by refInput(CheckWorkflow.checkCommand)
    }

    context(builder: AdapterWorkflowBuilder)
    fun job(id: String, block: JobBuilder.() -> Unit = {}) = job(id, ::JobBuilder, block)

    override fun WorkflowBuilder.implementation() {
        job(id = "build", name = "Build", runsOn = UbuntuLatest) {
            conditionalSetupSteps()
            run(name = "Run check", command = checkCommand.ref.expression)
        }
    }
}
