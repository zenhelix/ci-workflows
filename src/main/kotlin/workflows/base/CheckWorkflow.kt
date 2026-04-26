package workflows.base

import dsl.capability.SetupCapability
import io.github.typesafegithub.workflows.actions.actions.Checkout_Untyped
import io.github.typesafegithub.workflows.domain.Concurrency
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.dsl.WorkflowBuilder
import workflows.ProjectWorkflow
import dsl.core.expr
import workflows.support.conditionalSetupSteps

object CheckWorkflow : ProjectWorkflow(
    "check.yml", "Check",
    concurrency = Concurrency(
        group = "\${{ github.workflow }}-\${{ github.ref }}",
        cancelInProgress = true,
    ),
), SetupCapability {
    override val setupAction = input("setup-action", SetupCapability.SETUP_ACTION_DESCRIPTION, required = true)
    override val setupParams = input("setup-params", SetupCapability.SETUP_PARAMS_DESCRIPTION, default = SetupCapability.SETUP_PARAMS_DEFAULT)
    val checkCommand = input("check-command", "Command to run for checking", required = true)

    override fun WorkflowBuilder.implementation() {
        job(id = "build", name = "Build", runsOn = UbuntuLatest, timeoutMinutes = 30) {
            uses(name = "Checkout", action = Checkout_Untyped())
            conditionalSetupSteps()
            run(name = "Run check", command = checkCommand.expr)
        }
    }
}
