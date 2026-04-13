package workflows.adapters

import io.github.typesafegithub.workflows.domain.RunnerType.UbuntuLatest
import io.github.typesafegithub.workflows.domain.triggers.WorkflowDispatch
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import shared.*
import java.io.File

fun generateAppRelease(outputDir: File) {
    val targetFile = "app-release.yml"

    workflow(
        name = "Application Release",
        on = listOf(WorkflowDispatch()),
        sourceFile = File(".github/workflow-src/app-release.main.kts"),
        targetFileName = targetFile,
        consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
        _customArguments = mapOf(
            "on" to mapOf(
                "workflow_call" to mapOf(
                    "inputs" to mapOf(
                        "changelog-config" to stringInput(
                            description = "Path to changelog configuration file",
                            default = DEFAULT_CHANGELOG_CONFIG,
                        ),
                        "draft" to booleanInput(
                            description = "Create release as draft (default true for apps)",
                            default = true,
                        ),
                    ),
                ),
            ),
        ),
    ) {
        job(
            id = "release",
            runsOn = UbuntuLatest,
            _customArguments = mapOf(
                "uses" to reusableWorkflow("release.yml"),
                "with" to mapOf(
                    "changelog-config" to "\${{ inputs.changelog-config }}",
                    "draft" to "\${{ inputs.draft }}",
                ),
            ),
        ) {
            noop()
        }
    }

    cleanReusableWorkflowJobs(File(outputDir, targetFile))
}
