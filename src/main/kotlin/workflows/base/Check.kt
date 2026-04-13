package workflows.base

import dsl.CheckWorkflow
import dsl.conditionalSetupSteps
import io.github.typesafegithub.workflows.domain.Mode
import io.github.typesafegithub.workflows.domain.Permission
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import java.io.File

fun generateCheck() {
    workflow(
        name = "Check",
        on = listOf(
            WorkflowCall(inputs = CheckWorkflow.inputs),
        ),
        sourceFile = File(".github/workflow-src/check.main.kts"),
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
                command = CheckWorkflow.checkCommand.ref,
            )
        }
    }
}
