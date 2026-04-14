package workflows.base

import dsl.builder.AdapterWorkflowBuilder
import dsl.builder.ReusableWorkflowJobBuilder
import dsl.builder.refInput
import dsl.builder.stringInput
import dsl.capability.SetupCapability
import dsl.capability.SetupCapableJobBuilder
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.dsl.WorkflowBuilder
import workflows.ProjectWorkflow
import workflows.support.conditionalSetupSteps

object CheckWorkflow : ProjectWorkflow("check.yml", "Check"), SetupCapability {
    override val setupAction = input("setup-action", SetupCapability.SETUP_ACTION_DESCRIPTION, required = true)
    override val setupParams = input("setup-params", SetupCapability.SETUP_PARAMS_DESCRIPTION, default = SetupCapability.SETUP_PARAMS_DEFAULT)
    val checkCommand = input("check-command", "Command to run for checking", required = true)

    class JobBuilder : ReusableWorkflowJobBuilder(CheckWorkflow), SetupCapableJobBuilder {
        override var setupAction by stringInput(CheckWorkflow.setupAction)
        override var setupParams by stringInput(CheckWorkflow.setupParams)
        var checkCommand by refInput(CheckWorkflow.checkCommand)
    }

    context(builder: AdapterWorkflowBuilder)
    fun job(id: String, block: JobBuilder.() -> Unit = {}) {
        builder.registerJob(buildJob(id, ::JobBuilder, block))
    }

    override fun WorkflowBuilder.implementation() {
        job(id = "build", name = "Build", runsOn = UbuntuLatest) {
            conditionalSetupSteps()
            run(name = "Run check", command = checkCommand.ref.expression)
        }
    }
}
