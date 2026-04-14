package workflows.base

import workflows.conditionalSetupSteps
import workflows.definitions.AppDeployWorkflow
import io.github.typesafegithub.workflows.domain.Mode
import io.github.typesafegithub.workflows.domain.Permission
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import java.io.File

fun generateAppDeploy() {
    workflow(
        name = "Application Deploy",
        on = listOf(
            WorkflowCall(inputs = AppDeployWorkflow.inputs),
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
                command = "git checkout \"${AppDeployWorkflow.tag.ref.expression}\"",
            )
            run(
                name = "Deploy",
                command = AppDeployWorkflow.deployCommand.ref.expression,
            )
        }
    }
}
