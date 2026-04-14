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

object CheckWorkflow : ProjectWorkflow("check.yml") {

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
        description = "Command to run for checking",
        required = true
    )

    class JobBuilder : ReusableWorkflowJobBuilder(CheckWorkflow), SetupConfigurable {
        var setupAction by stringInput(CheckWorkflow.setupAction)
        var setupParams by stringInput(CheckWorkflow.setupParams)
        var checkCommand by refInput(CheckWorkflow.checkCommand)

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
            name = "Check",
            on = listOf(
                WorkflowCall(inputs = inputs),
            ),
            sourceFile = File("src/main/kotlin/workflows/CheckWorkflow.kt"),
            targetFileName = "check.yml",
            consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
            permissions = mapOf(Permission.Contents to Mode.Read),
        ) {
            job(
                id = "build",
                name = "Build",
                runsOn = UbuntuLatest,
            ) {
                conditionalSetupSteps()
                run(
                    name = "Run check",
                    command = checkCommand.ref.expression,
                )
            }
        }
    }
}
