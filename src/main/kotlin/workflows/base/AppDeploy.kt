package workflows.base

import workflows.conditionalSetupSteps
import io.github.typesafegithub.workflows.domain.Mode
import io.github.typesafegithub.workflows.domain.Permission
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import java.io.File

fun generateAppDeploy(outputDir: File) {
    workflow(
        name = "Application Deploy",
        on = listOf(
            WorkflowCall(
                inputs = mapOf(
                    "setup-action" to WorkflowCall.Input(
                        "Setup action to use: gradle, go, python", true, WorkflowCall.Type.String
                    ),
                    "setup-params" to WorkflowCall.Input(
                        "JSON object with setup parameters (e.g. {\"java-version\": \"21\"})",
                        false, WorkflowCall.Type.String, "{}"
                    ),
                    "deploy-command" to WorkflowCall.Input(
                        "Command to run for deployment", true, WorkflowCall.Type.String
                    ),
                    "tag" to WorkflowCall.Input(
                        "Tag/version to deploy (checked out at this ref)", true, WorkflowCall.Type.String
                    ),
                )
            ),
        ),
        sourceFile = File(".github/workflow-src/app-deploy.main.kts"),
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
                command = "git checkout \"\${{ inputs.tag }}\"",
            )
            run(
                name = "Deploy",
                command = "\${{ inputs.deploy-command }}",
            )
        }
    }
}
