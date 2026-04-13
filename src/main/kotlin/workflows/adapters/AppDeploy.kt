package workflows.adapters

import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowDispatch
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import shared.SETUP_PARAMS_INPUT
import shared.conditionalSetupSteps
import shared.stringInput
import java.io.File

fun generateAppDeploy(outputDir: File) {
    workflow(
        name = "Application Deploy",
        on = listOf(WorkflowDispatch()),
        sourceFile = File(".github/workflow-src/app-deploy.main.kts"),
        targetFileName = "app-deploy.yml",
        consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
        _customArguments = mapOf(
            "on" to mapOf(
                "workflow_call" to mapOf(
                    "inputs" to mapOf(
                        "setup-action" to stringInput(
                            description = "Setup action to use: gradle, go, python",
                            required = true,
                        ),
                        SETUP_PARAMS_INPUT,
                        "deploy-command" to stringInput(
                            description = "Command to run for deployment",
                            required = true,
                        ),
                        "tag" to stringInput(
                            description = "Tag/version to deploy (checked out at this ref)",
                            required = true,
                        ),
                    ),
                ),
            ),
            "permissions" to mapOf("contents" to "read"),
        ),
    ) {
        job(
            id = "deploy",
            name = "Deploy",
            runsOn = UbuntuLatest,
        ) {
            conditionalSetupSteps(fetchDepth = "0")
            run(
                name = "Checkout tag",
                command = "git checkout \"\${{ inputs.tag }}\"",
            )
            run(
                name = "Deploy",
                command = "\${{ inputs.deploy-command }}",
            )
        }
    }
}
