package workflows.base

import io.github.typesafegithub.workflows.domain.Mode
import io.github.typesafegithub.workflows.domain.Permission
import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import actions.LabelerAction
import dsl.LabelerWorkflow
import java.io.File

fun generateLabeler() {
    workflow(
        name = "PR Labeler",
        on = listOf(
            WorkflowCall(inputs = LabelerWorkflow.inputs),
        ),
        sourceFile = File(".github/workflow-src/labeler.main.kts"),
        targetFileName = "labeler.yml",
        consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
        permissions = mapOf(
            Permission.Contents to Mode.Write,
            Permission.PullRequests to Mode.Write,
        ),
    ) {
        job(
            id = "label",
            name = "Label PR",
            runsOn = UbuntuLatest,
        ) {
            uses(
                name = "Label PR based on file paths",
                action = LabelerAction(
                    repoToken = "\${{ secrets.GITHUB_TOKEN }}",
                    configurationPath = "\${{ inputs.config-path }}",
                    syncLabels = "true",
                ),
            )
        }
    }
}
