package workflows.base

import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowDispatch
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import shared.*
import java.io.File

fun generateLabeler(outputDir: File) {
    workflow(
        name = "PR Labeler",
        on = listOf(WorkflowDispatch()),
        sourceFile = File(".github/workflow-src/labeler.main.kts"),
        targetFileName = "labeler.yml",
        consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
        _customArguments = mapOf(
            "on" to mapOf(
                "workflow_call" to mapOf(
                    "inputs" to mapOf(
                        "config-path" to stringInput(
                            description = "Path to labeler configuration file",
                            default = ".github/labeler.yml",
                        ),
                    ),
                ),
            ),
            "permissions" to mapOf(
                "contents" to "write",
                "pull-requests" to "write",
            ),
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
